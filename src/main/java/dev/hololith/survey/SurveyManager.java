package dev.hololith.survey;

import dev.hololith.HololithConfig;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/** Server-thread-only round robin queue. Never requests/generates a chunk. */
public final class SurveyManager {
    private static final Map<MinecraftServer,SurveyManager> INSTANCES=new WeakHashMap<>();
    public static SurveyManager get(MinecraftServer server){return INSTANCES.computeIfAbsent(server,SurveyManager::new);}
    public static void clear(MinecraftServer server){INSTANCES.remove(server);}
    private final MinecraftServer server;
    private final ArrayDeque<Job> queue=new ArrayDeque<>();
    private final Map<SurveyKey,Job> pending=new HashMap<>();
    private final LinkedHashMap<SurveyKey,Integer> checked=new LinkedHashMap<>(16,.75f,true);
    private SurveyManager(MinecraftServer server){this.server=server;}
    public SurveySnapshot request(SurveyKey key){
        SurveyStore store=SurveyStore.get(server);SurveySnapshot previous=store.find(key);Job job=pending.get(key);
        if(job!=null){job.lastRequested=server.getTickCount();return previous;}
        if(queue.size()>=128||server.getTickCount()-checked.getOrDefault(key,-100000)<HololithConfig.REFRESH_TICKS.get())return previous;
        var location=ResourceLocation.tryParse(key.dimension());if(location==null)return previous;
        ServerLevel level=server.getLevel(ResourceKey.create(Registries.DIMENSION,location));if(level==null)return previous;
        job=new Job(key,level,previous,server.getTickCount());pending.put(key,job);queue.add(job);return previous;
    }
    public int pendingJobs(){return pending.size();}
    public boolean isPending(SurveyKey key){return pending.containsKey(key);}
    /** Loaded-column counts prove liveness only while the last completed scan remains recent. */
    public boolean hasChecked(SurveyKey key){return recentlyChecked(checked.get(key),server.getTickCount(),HololithConfig.REFRESH_TICKS.get());}
    static boolean recentlyChecked(Integer completedTick,int currentTick,int refreshTicks){
        if(completedTick==null)return false;
        long age=(long)currentTick-completedTick;
        return age>=0&&age<=(long)refreshTicks+40;
    }
    public void tick(){
        long deadline=System.nanoTime()+2_000_000L;int left=HololithConfig.COLUMNS_PER_TICK.get();
        while(left>0&&!queue.isEmpty()&&System.nanoTime()<deadline){
            Job job=queue.remove();
            if(server.getTickCount()-job.lastRequested>200){pending.remove(job.key);continue;}
            int batch=Math.min(32,left);int used=0;
            while(used<batch&&job.cursor<job.flags.length){job.sample(job.cursor++);used++;}
            left-=used;
            if(job.cursor==job.flags.length){
                pending.remove(job.key);checked.put(job.key,server.getTickCount());
                while(checked.size()>512)checked.remove(checked.keySet().iterator().next());
                SurveySnapshot s=job.finish();if(s.knownColumns()>0)SurveyStore.get(server).put(s);
            }else queue.add(job);
        }
    }
    private static final class Job {
        final SurveyKey key;final ServerLevel level;final SurveySnapshot previous;
        final int[] groundY,surfaceY,groundColors,surfaceColors;final BlockState[] groundStates,surfaceStates;final byte[] flags;
        int cursor,loaded,lastRequested;
        Job(SurveyKey key,ServerLevel level,SurveySnapshot previous,int tick){
            this.key=key;this.level=level;this.previous=previous;lastRequested=tick;int n=key.size()*key.size();
            groundY=previous==null?new int[n]:previous.groundY.clone();surfaceY=previous==null?new int[n]:previous.surfaceY.clone();groundColors=previous==null?new int[n]:previous.groundColors.clone();surfaceColors=previous==null?new int[n]:previous.surfaceColors.clone();
            groundStates=previous==null?new BlockState[n]:previous.groundStates.clone();surfaceStates=previous==null?new BlockState[n]:previous.surfaceStates.clone();flags=previous==null?new byte[n]:previous.flags.clone();
        }
        void sample(int i){
            int x=key.sampleX(i),z=key.sampleZ(i);
            // getChunkNow returns null for absent chunks. All following state reads stay in this chunk.
            LevelChunk chunk=level.getChunkSource().getChunkNow(Math.floorDiv(x,16),Math.floorDiv(z,16));if(chunk==null)return;
            loaded++;
            int y=chunk.getHeight(Heightmap.Types.WORLD_SURFACE,x&15,z&15);
            if(y<level.getMinBuildHeight()){flags[i]=0;return;}
            BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos(x,y,z);BlockState top=chunk.getBlockState(pos);
            // The instrument itself must not become the ground beneath its reference pin.
            // Only reference-topped columns need this bounded, same-loaded-chunk descent.
            if(top.is(dev.hololith.Hololith.MARKER.get())){
                do{pos.setY(--y);top=chunk.getBlockState(pos);}while(y>level.getMinBuildHeight()&&(top.isAir()||top.is(dev.hololith.Hololith.MARKER.get())));
                if(top.isAir()||top.is(dev.hololith.Hololith.MARKER.get())){flags[i]=0;return;}
            }
            boolean water=top.getFluidState().is(net.minecraft.tags.FluidTags.WATER);boolean canopy=top.is(BlockTags.LEAVES);
            int ground=y;BlockState bottom=top;
            if(water||canopy){
                for(int depth=0;depth<32&&ground>level.getMinBuildHeight();depth++){
                    ground--;pos.setY(ground);bottom=chunk.getBlockState(pos);
                    if(water?bottom.getFluidState().isEmpty()&&!bottom.isAir():!bottom.isAir()&&!bottom.is(BlockTags.LEAVES)&&!bottom.is(BlockTags.LOGS))break;
                }
                if(bottom.isAir()||bottom.is(BlockTags.LEAVES)||bottom.is(BlockTags.LOGS)) {ground=y-3;bottom=Blocks.DIRT.defaultBlockState();}
            }
            groundY[i]=ground;surfaceY[i]=y;groundStates[i]=bottom;surfaceStates[i]=top;
            pos.setY(ground);groundColors[i]=color(chunk,bottom,pos,false);
            pos.setY(y);surfaceColors[i]=color(chunk,top,pos,water);
            flags[i]=(byte)(1|(water?2:0)|(canopy?4:0));
        }
        int color(LevelChunk chunk,BlockState state,BlockPos pos,boolean water){
            var biome=chunk.getNoiseBiome(Math.floorDiv(pos.getX(),4),Math.floorDiv(pos.getY(),4),Math.floorDiv(pos.getZ(),4)).value();
            if(water)return biome.getWaterColor();
            if(state.is(BlockTags.LEAVES))return biome.getFoliageColor();
            if(state.is(Blocks.GRASS_BLOCK)||state.is(Blocks.SHORT_GRASS)||state.is(Blocks.TALL_GRASS)||state.is(Blocks.FERN))return biome.getGrassColor(pos.getX(),pos.getZ());
            int c=state.getMapColor(level,pos).col;return c==0?0xaaa9a2:c;
        }
        SurveySnapshot finish(){
            long revision=previous==null?1:previous.revision+1;
            SurveySnapshot s=new SurveySnapshot(key,System.currentTimeMillis(),revision,groundY,surfaceY,groundColors,surfaceColors,groundStates,surfaceStates,flags,loaded);
            if(s.sameTerrain(previous))return new SurveySnapshot(key,previous.capturedAt,previous.revision,groundY,surfaceY,groundColors,surfaceColors,groundStates,surfaceStates,flags,loaded);
            return s;
        }
    }
}
