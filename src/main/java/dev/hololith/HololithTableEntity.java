package dev.hololith;

import dev.hololith.compat.ShipCoordinates;
import dev.hololith.survey.*;
import dev.hololith.power.ProjectionPower;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class HololithTableEntity extends KineticBlockEntity {
    private SurveySnapshot snapshot;
    private int span=64,rotation,zoom,relief=1,mode;
    private UUID sensorId;
    private UUID tableId=UUID.randomUUID();
    private List<MarkerPoint> markerPoints=List.of();
    private SurveyKey markerKey;
    private String targetDimension="minecraft:overworld",status="Waiting for survey";
    private int targetX,targetZ;
    private final SurveyFollower follower=new SurveyFollower();
    private long lastControlAt=-100;
    private Runnable mountedStateChanged;
    private java.util.function.BiConsumer<SurveyKey,List<MarkerPoint>> mountedMarkersChanged;
    private boolean mounted;
    private ProjectionPower.State powerState=ProjectionPower.State.UNPOWERED;
    public HololithTableEntity(BlockPos pos,BlockState state){super(Hololith.TABLE_ENTITY.get(),pos,state);}
    public SurveySnapshot snapshot(){return snapshot;}
    public int rotation(){return rotation;}
    public float magnification(){return 1+zoom*.25f;}
    public float verticalScale(){return relief;}
    public int span(){return span;}
    public int mode(){return mode;}
    public int zoom(){return zoom;}
    public String targetDimension(){return targetDimension;}
    public int targetX(){return targetX;}
    public int targetZ(){return targetZ;}
    public String status(){return status;}
    public UUID sensorId(){return sensorId;}
    public UUID tableId(){return tableId;}
    public boolean isProjecting(){return powerState==ProjectionPower.State.RUNNING&&Math.abs(getSpeed())>=requiredRpm();}
    public String powerStatusKey(){return powerState.key();}
    public int requiredRpm(){return ProjectionPower.MINIMUM_RPM;}
    public float actualRpm(){return mounted?0:Math.abs(getTheoreticalSpeed());}
    public float stressImpact(){return ProjectionPower.impact(span,zoom);}
    public float stressUsage(){return stressImpact()*actualRpm();}
    @Override public float calculateStressApplied(){lastStressApplied=stressImpact();return lastStressApplied;}
    private void refreshStress(){
        if(level!=null&&!level.isClientSide&&!mounted&&hasNetwork())getOrCreateNetwork().updateStressFor(this,calculateStressApplied());
    }
    private void updatePower(){
        var next=ProjectionPower.state(mounted,hasSource()&&hasNetwork(),isOverStressed(),getTheoreticalSpeed());
        String paused=snapshot==null?"Waiting for power":"Saved survey · projection off";
        if(next!=powerState||(next!=ProjectionPower.State.RUNNING&&!paused.equals(status))){
            powerState=next;if(next!=ProjectionPower.State.RUNNING)status=paused;changed();
        }
    }
    @Override public void tick(){
        // Captured ordinary Create actors are ticked by their MovementBehaviour.
        // They must never attach a kinetic network at their fictitious local position.
        if(mounted)return;
        super.tick();
        if(level!=null&&!level.isClientSide)tick(level,worldPosition,getBlockState(),this);
    }
    @Override public void remove(){if(!mounted)super.remove();}
    @Override public boolean addToGoggleTooltip(List<Component> tooltip,boolean sneaking){
        super.addToGoggleTooltip(tooltip,sneaking);
        tooltip.add(Component.translatable(powerStatusKey()).withStyle(isProjecting()?ChatFormatting.AQUA:ChatFormatting.GOLD));
        tooltip.add(Component.translatable("power.hololith.requirement",requiredRpm()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("power.hololith.impact",stressImpact()).withStyle(ChatFormatting.GRAY));
        return true;
    }
    /** Runtime-only transport for Create's captured block entities. Never saved in NBT. */
    public void setMountedCallbacks(Runnable stateChanged,java.util.function.BiConsumer<SurveyKey,List<MarkerPoint>> markersChanged){
        mounted=true;clearKineticInformation();speed=0;powerState=ProjectionPower.State.MOUNTED;
        mountedStateChanged=stateChanged;mountedMarkersChanged=markersChanged;
    }
    public List<MarkerPoint> markers(){return snapshot!=null&&snapshot.key().equals(markerKey)?markerPoints:List.of();}
    public void acceptMarkers(UUID identity,SurveyKey key,List<MarkerPoint> points){if(tableId.equals(identity)){markerKey=key;markerPoints=List.copyOf(points);}}
    private void refreshMarkers(){
        if(!(level instanceof ServerLevel serverLevel))return;
        SurveyKey key=snapshot==null?null:snapshot.key();
        List<MarkerPoint> points=ReferenceRegistry.get(serverLevel.getServer()).forTable(serverLevel.getServer(),this);
        if(!Objects.equals(markerKey,key)||!markerPoints.equals(points)){
            markerKey=key;markerPoints=points;
            if(mountedMarkersChanged!=null)mountedMarkersChanged.accept(key,points);else HololithNetwork.markers(serverLevel,this,key,points);
        }
    }
    @Override public void onLoad(){super.onLoad();if(level!=null&&!level.isClientSide)setChanged();}
    public boolean allowControl(){if(level==null||level.getGameTime()-lastControlAt<5)return false;lastControlAt=level.getGameTime();return true;}
    public boolean bind(CompoundTag card){
        if(!SurveyCardItem.isSensorCard(card))return false;
        UUID next=card.getUUID("SensorId");
        if(mode==1&&next.equals(sensorId))return true;
        sensorId=next;mode=1;
        // Card coordinates never select a target. Only server-side sensor heartbeats do.
        snapshot=null;markerPoints=List.of();markerKey=null;follower.reset();status="Acquiring survey";changed();return true;
    }
    public void adjustDial(boolean scale){if(scale)span=span==32?64:span==64?128:32;else rotation=(rotation+1)%4;if(scale){snapshot=null;follower.reset();refreshStress();}changed();}
    public void configure(int span,int rotation,int zoom,int relief,int mode,String dimension,int x,int z){
        if((mode!=0&&mode!=1)||(mode==1&&sensorId==null))return;
        if(this.span==span&&this.rotation==rotation&&this.zoom==zoom&&this.relief==relief&&this.mode==mode)return;
        boolean retarget=this.span!=span||this.mode!=mode;
        this.span=SurveyKey.validSpan(span);this.rotation=Math.floorMod(rotation,4);this.zoom=Math.clamp(zoom,0,2);this.relief=Math.clamp(relief,1,2);this.mode=mode;
        if(retarget){snapshot=null;follower.reset();status="Acquiring survey";}refreshStress();changed();
    }
    public static void tick(Level level,BlockPos pos,BlockState state,HololithTableEntity table){
        Optional<net.minecraft.world.phys.Vec3> location=table.mode==0&&(level.getGameTime()+pos.asLong())%SurveyFollower.POLL_TICKS==0?ShipCoordinates.toWorld(level,pos):Optional.empty();
        tickAt(level,pos,state,table,location);
    }
    /** Uses an authoritative transformed position supplied by a moving platform. */
    public static void tickAt(Level level,BlockPos pos,BlockState state,HololithTableEntity table,Optional<net.minecraft.world.phys.Vec3> location){
        if(!(level instanceof ServerLevel))return;
        table.updatePower();
        // Keep the completed survey and cache, but do not scan or project without power.
        if(!table.isProjecting())return;
        boolean markerDue=(level.getGameTime()+pos.asLong())%5==0;
        if((level.getGameTime()+pos.asLong())%SurveyFollower.POLL_TICKS!=0){if(markerDue)table.refreshMarkers();return;}
        var server=((ServerLevel)level).getServer();SurveyStore store=SurveyStore.get(server);
        String dimension;int x,z;boolean online=true;
        if(table.mode==0){
            if(location.isEmpty()){if(markerDue)table.refreshMarkers();table.updateStatus(SurveyStatus.retained("Position unavailable",table.snapshot));return;}
            dimension=level.dimension().location().toString();x=(int)Math.floor(location.get().x);z=(int)Math.floor(location.get().z);
        }else if(table.mode==1){
            var sensor=table.sensorId==null?null:store.sensor(table.sensorId);online=sensor!=null&&sensor.online();
            if(!online){
                // Do not request even loaded remote terrain from stale coordinates.
                // An already queued shared scan may finish for other users, but this
                // offline table retains its own previously displayed snapshot only.
                table.follower.reset();
                table.updateStatus(SurveyStatus.retained("Sensor offline",table.snapshot));
                if(markerDue)table.refreshMarkers();
                return;
            }
            dimension=sensor.dimension();x=sensor.x();z=sensor.z();
        }else{
            table.follower.reset();table.updateStatus(SurveyStatus.retained("Unsupported survey source",table.snapshot));return;
        }
        SurveyKey key=SurveyKey.at(dimension,x,z,table.span);
        // Re-seed the shared cache after eviction/restart from this table's own persistent survey.
        if(table.snapshot!=null&&table.snapshot.key().equals(key)&&store.find(key)==null)store.put(table.snapshot);
        SurveyManager manager=SurveyManager.get(server);
        SurveyFollower.Update update=table.follower.poll(key,manager);
        SurveySnapshot next=update.snapshot();
        boolean changed=false;
        if(next!=null){
            changed=table.snapshot==null||!table.snapshot.key().equals(next.key())||table.snapshot.revision!=next.revision;
            table.snapshot=next;
        }
        SurveySnapshot displayed=table.snapshot;
        var dimensionId=net.minecraft.resources.ResourceLocation.tryParse(key.dimension());
        boolean dimensionAvailable=dimensionId!=null&&server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,dimensionId))!=null;
        String text=SurveyStatus.describe(displayed,key,online,manager.hasChecked(key),update.pending(),dimensionAvailable);
        if(!text.equals(table.status)){table.status=text;changed=true;}
        // Publish positions for the newly adopted region, including in its full update tag.
        if(markerDue)table.refreshMarkers();
        if(changed)table.changed();
    }
    private void updateStatus(String text){if(!status.equals(text)){status=text;changed();}}
    public void changed(){if(mountedStateChanged!=null){mountedStateChanged.run();return;}setChanged();if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);}
    private void writeSettings(CompoundTag tag){
        tag.putInt("Span",span);tag.putInt("Rotation",rotation);tag.putInt("Zoom",zoom);tag.putInt("Relief",relief);tag.putInt("Mode",mode);if(sensorId!=null)tag.putUUID("SensorId",sensorId);tag.putString("Dimension",targetDimension);tag.putInt("X",targetX);tag.putInt("Z",targetZ);tag.putString("Status",status);
    }
    @Override protected void write(CompoundTag tag,HolderLookup.Provider lookup,boolean clientPacket){super.write(tag,lookup,clientPacket);tag.putUUID("TableId",tableId);writeSettings(tag);tag.putString("HololithPower",powerState.name());if(snapshot!=null)tag.put("Survey",snapshot.save());}
    @Override protected void read(CompoundTag tag,HolderLookup.Provider lookup,boolean clientPacket){
        if(tag.hasUUID("TableId"))tableId=tag.getUUID("TableId");
        if(tag.contains("Markers")){
            markerPoints=MarkerPoint.load(tag.getList("Markers",net.minecraft.nbt.Tag.TAG_COMPOUND));
            markerKey=tag.contains("MarkerDimension")?new SurveyKey(tag.getString("MarkerDimension"),tag.getInt("MarkerX"),tag.getInt("MarkerZ"),tag.getInt("MarkerSpan")):null;
        }
        super.read(tag,lookup,clientPacket);span=SurveyKey.validSpan(tag.getInt("Span"));rotation=Math.floorMod(tag.getInt("Rotation"),4);zoom=Math.clamp(tag.getInt("Zoom"),0,2);relief=Math.clamp(tag.getInt("Relief"),1,2);
        int savedMode=tag.getInt("Mode");boolean legacyTarget=savedMode!=0&&savedMode!=1;mode=savedMode==1?1:0;
        sensorId=tag.hasUUID("SensorId")?tag.getUUID("SensorId"):null;targetDimension=tag.getString("Dimension");targetX=tag.getInt("X");targetZ=tag.getInt("Z");status=tag.getString("Status");snapshot=!legacyTarget&&tag.contains("Survey")?SurveySnapshot.load(tag.getCompound("Survey"),lookup):null;
        follower.reset();
        if(legacyTarget){
            // A legacy arbitrary target must not flash above the now-local table.
            snapshot=null;markerPoints=List.of();markerKey=null;status="Acquiring local survey";
            targetDimension=level==null?"minecraft:overworld":level.dimension().location().toString();
            targetX=worldPosition.getX();targetZ=worldPosition.getZ();
        }
        // Stored RPM/state is not an authorization to operate after world reload.
        powerState=clientPacket||(level!=null&&level.isClientSide)?ProjectionPower.State.read(tag.getString("HololithPower")):ProjectionPower.State.UNPOWERED;
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup){
        CompoundTag tag=saveWithoutMetadata(lookup);tag.put("Markers",MarkerPoint.save(markerPoints));
        if(markerKey!=null){tag.putString("MarkerDimension",markerKey.dimension());tag.putInt("MarkerX",markerKey.x());tag.putInt("MarkerZ",markerKey.z());tag.putInt("MarkerSpan",markerKey.span());}
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
