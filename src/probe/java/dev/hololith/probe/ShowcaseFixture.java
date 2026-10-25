package dev.hololith.probe;

import com.google.gson.GsonBuilder;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.mounted.*;
import dev.hololith.*;
import dev.hololith.client.*;
import dev.hololith.compat.ContraptionSurveys;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Opt-in actual-gameplay camera/recording rig. Never shipped in the runtime mod. */
final class ShowcaseFixture {
    private static final BlockPos TABLE=new BlockPos(24,101,0), SENSOR=new BlockPos(0,81,0);
    private static final BlockPos ASSEMBLER=new BlockPos(-8,100,25), CART_TABLE=new BlockPos(2,2,0), CART_SENSOR=new BlockPos(0,1,0);
    private static final List<BlockPos> PINS=List.of(new BlockPos(-6,82,-6),new BlockPos(6,82,-6),new BlockPos(6,82,6),new BlockPos(-6,82,6));
    private static final DyeColor[] COLORS={DyeColor.CYAN,DyeColor.ORANGE,DyeColor.LIME,DyeColor.MAGENTA};
    private static final int FPS=30, SECONDS=80;
    private final Set<Integer> events=new HashSet<>();
    private final long boot=System.nanoTime();
    private CompletableFuture<Void> work=CompletableFuture.completedFuture(null);
    private long recordingStart;
    private int ticks,frames,repeatedFrames,cartId;
    private boolean built,done;
    private AbstractMinecart cart;
    private AbstractContraptionEntity contraption;
    private volatile boolean drive;
    private Process encoder;
    private OutputStream video;
    private byte[] rgb;
    private final List<Map<String,Object>> evidence=new ArrayList<>();
    ShowcaseFixture(){
        NeoForge.EVENT_BUS.addListener(this::capture);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Pre e)->{
            if(cart!=null&&cart.isAlive())cart.setDeltaMovement(drive?new Vec3(.055,0,0):Vec3.ZERO);
        });
    }
    private void server(Consumer<ServerPlayer> action){var mc=Minecraft.getInstance();var s=mc.getSingleplayerServer();UUID id=mc.player.getUUID();work=CompletableFuture.runAsync(()->action.accept(Objects.requireNonNull(s.getPlayerList().getPlayer(id))),s);}
    private double seconds(){return recordingStart==0?0:(System.nanoTime()-recordingStart)/1e9;}
    private boolean at(int second){return seconds()>=second&&events.add(second);}
    private void hand(ServerPlayer p,ItemStack stack){p.setItemInHand(InteractionHand.MAIN_HAND,stack);p.inventoryMenu.broadcastChanges();}
    private void use(BlockPos pos){var mc=Minecraft.getInstance();mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos).add(0,.5,0),Direction.UP,pos,false));mc.player.swing(InteractionHand.MAIN_HAND);}
    private void near(ServerPlayer p,BlockPos pos){look(p,Vec3.atCenterOf(pos).add(0,2,3.2),Vec3.atCenterOf(pos));}
    private void look(ServerPlayer p,Vec3 eye,Vec3 focus){Vec3 d=focus.subtract(eye);p.connection.teleport(eye.x,eye.y-p.getEyeHeight(),eye.z,(float)Math.toDegrees(Math.atan2(d.z,d.x))-90,(float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))));p.getAbilities().flying=true;p.onUpdateAbilities();}
    private void table(net.minecraft.server.level.ServerLevel level,BlockPos pos){for(int part=0;part<9;part++)level.setBlockAndUpdate(pos.offset(part%3-1,0,part/3-1),Hololith.TABLE.get().defaultBlockState().setValue(HololithTableBlock.PART,part));}
    private CompoundTag card(UUID id,int x,int z){var n=new CompoundTag();n.putUUID("SensorId",id);n.putString("Dimension","minecraft:overworld");n.putInt("X",x);n.putInt("Z",z);return n;}
    void tick(){
        if(done)return;var mc=Minecraft.getInstance();
        try{
            if(System.nanoTime()-boot>600_000_000_000L)throw new IllegalStateException("Showcase timed out");
            if(mc.level==null||mc.player==null)return;ticks++;
            if(work.isCompletedExceptionally())work.join();if(!work.isDone())return;
            if(!built&&ticks>60){built=true;mc.options.fov().set(50);mc.options.hideGui=true;mc.options.bobView().set(false);server(this::build);return;}
            if(!built)return;
            if(recordingStart==0){
                var t=mc.level.getBlockEntity(TABLE);
                if(t instanceof HololithTableEntity h&&h.snapshot()!=null&&h.snapshot().knownColumns()==1024&&ticks>300){
                    startEncoder(mc);recordingStart=System.nanoTime();
                    evidence.add(Map.of("check","real 32x32 survey acquired before recording","passed",true));
                }return;
            }
            double s=seconds();
            // Normal item-use packets and the actual instrument screen are recorded.
            if(at(17)){mc.options.hideGui=false;server(p->{hand(p,ItemStack.EMPTY);near(p,TABLE);});}
            if(at(18))use(TABLE);
            if(at(20))press("Area:");
            if(at(21))press("Rotation:");
            if(at(23))press("Apply");
            if(at(24))mc.options.hideGui=true;
            if(at(29)){mc.options.hideGui=false;server(p->{hand(p,Hololith.CARD.toStack());near(p,SENSOR);});}
            if(at(30))use(SENSOR);
            if(at(33))server(p->near(p,TABLE));
            if(at(34))use(TABLE);
            if(at(37)){mc.options.hideGui=true;server(p->hand(p,ItemStack.EMPTY));}
            if(at(41)){mc.options.hideGui=false;server(p->{hand(p,Hololith.CARD.toStack());near(p,SENSOR);});}
            if(at(42))use(SENSOR);
            if(at(43))server(p->near(p,PINS.getFirst()));
            if(at(44))use(PINS.getFirst());
            if(at(45))server(p->hand(p,new ItemStack(Items.RED_DYE)));
            if(at(46))use(PINS.getFirst());
            if(at(48)){mc.options.hideGui=true;server(p->hand(p,ItemStack.EMPTY));}
            if(at(52)){mc.options.hideGui=false;server(p->{near(p,TABLE);p.serverLevel().setDayTime(13000);});}
            if(at(53))use(TABLE);
            if(at(55)){
                if(mc.screen instanceof HololithControls screen){
                    var slider=screen.children().stream().filter(c->c instanceof AbstractSliderButton).map(c->(AbstractSliderButton)c).findFirst().orElseThrow();
                    slider.onClick(slider.getX()+slider.getWidth()*.88,slider.getY()+10);
                    evidence.add(Map.of("check","actual brightness slider operated","passed",true));
                }else throw new IllegalStateException("Brightness panel did not open");
            }
            if(at(57)){if(mc.screen!=null)mc.screen.onClose();mc.options.hideGui=true;}
            if(at(60)){server(p->{p.serverLevel().setDayTime(6000);assembleCart(p);});}
            if(at(63))drive=true;
            if(at(76)){drive=false;server(p->p.server.saveEverything(false,true,true));}
            if(s>=SECONDS){finish();return;}
            if(mc.screen==null&&mc.options.hideGui){
                Vec3 focus;double angle;
                if(s>=61&&mc.level.getEntity(cartId) instanceof AbstractContraptionEntity c){focus=c.toGlobalVector(Vec3.atCenterOf(CART_TABLE),1).add(0,1.0,0);angle=.75+(s-61)*.015;}
                else {focus=Vec3.atCenterOf(TABLE).add(0,1.1,0);angle=.55+s*.018;}
                double distance=s<8?8.5:s<17?5.1:s>=61?8:5.7;
                Vec3 eye=focus.add(Math.sin(angle)*distance,s<8?4.5:3.0,Math.cos(angle)*distance);
                // Actual creative-flight player camera; normal client movement sync follows it.
                Vec3 delta=focus.subtract(eye);mc.player.setPos(eye.x,eye.y-mc.player.getEyeHeight(),eye.z);mc.player.setYRot((float)Math.toDegrees(Math.atan2(delta.z,delta.x))-90);mc.player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));mc.player.getAbilities().flying=true;mc.player.setDeltaMovement(Vec3.ZERO);
            }
        }catch(Throwable e){e.printStackTrace();evidence.add(Map.of("check","showcase completed","passed",false,"error",e.toString()));finish();}
    }
    private void press(String starts){var mc=Minecraft.getInstance();if(!(mc.screen instanceof HololithControls))throw new IllegalStateException("Instrument screen missing at "+seconds());mc.screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().startsWith(starts)).map(c->(Button)c).findFirst().orElseThrow().onPress();}
    private void build(ServerPlayer p){
        var level=p.serverLevel();p.setGameMode(GameType.CREATIVE);p.getInventory().clearContent();p.getInventory().selected=0;
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,p.server);level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_WEATHER_CYCLE).set(false,p.server);level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
        for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)level.getChunk(x,z);
        for(int x=-48;x<=48;x++)for(int z=-48;z<=48;z++){
            double hill=13*Math.exp(-((x+20)*(x+20)+(z+18)*(z+18))/180.0)+7*Math.exp(-((x-30)*(x-30)+(z-22)*(z-22))/160.0);
            int h=80+(int)hill;boolean river=Math.abs(z-3*Math.sin(x*.12))<2.0;
            if(river)h=78;
            for(int y=75;y<=h;y++)level.setBlock(new BlockPos(x,y,z),(y==h?(river?Blocks.SAND:Blocks.GRASS_BLOCK):y>h-3?Blocks.DIRT:Blocks.STONE).defaultBlockState(),2);
            if(river)for(int y=h+1;y<=80;y++)level.setBlock(new BlockPos(x,y,z),Blocks.WATER.defaultBlockState(),2);
        }
        for(int tx:new int[]{-30,-22,-12,-5,5,15,28,36})for(int tz:new int[]{-30,13,34}){
            int h=level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,tx,tz);
            for(int y=0;y<5;y++)level.setBlock(new BlockPos(tx,h+y,tz),Blocks.SPRUCE_LOG.defaultBlockState(),2);
            for(int y=2;y<7;y++)for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)if(Math.abs(x)+Math.abs(z)<(y<5?4:2))level.setBlock(new BlockPos(tx+x,h+y,tz+z),Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true),2);
        }
        for(int x=4;x<=11;x++)for(int z=-13;z<=-6;z++)for(int y=81;y<=87;y++)if(x==4||x==11||z==-13||z==-6||y==87)level.setBlock(new BlockPos(x,y,z),(y==87?Blocks.WAXED_CUT_COPPER:y==84&&((x+z)%3==0)?Blocks.GLASS:Blocks.STONE_BRICKS).defaultBlockState(),2);
        // Brass/copper observatory balcony, deliberately outside the initial 32-block survey.
        for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)level.setBlockAndUpdate(TABLE.offset(x,-1,z),(Math.abs(x)==5||Math.abs(z)==5?Blocks.WAXED_CUT_COPPER:Blocks.POLISHED_ANDESITE).defaultBlockState());
        for(int x:new int[]{-5,5})for(int z:new int[]{-5,5}){
            for(int y=0;y<4;y++)level.setBlockAndUpdate(TABLE.offset(x,y,z),Blocks.POLISHED_DEEPSLATE_WALL.defaultBlockState());
            level.setBlockAndUpdate(TABLE.offset(x,4,z),Blocks.SEA_LANTERN.defaultBlockState());
        }
        table(level,TABLE);level.setBlockAndUpdate(SENSOR.below(),Blocks.WAXED_CUT_COPPER.defaultBlockState());level.setBlockAndUpdate(SENSOR.east(3).below(),Blocks.WAXED_CUT_COPPER.defaultBlockState());level.setBlockAndUpdate(SENSOR,Hololith.SENSOR.get().defaultBlockState());
        level.setBlockAndUpdate(SENSOR.east(3),Hololith.BEACON.get().defaultBlockState());
        var t=(HololithTableEntity)level.getBlockEntity(TABLE);t.configure(32,0,0,1,2,"minecraft:overworld",0,0);
        var source=(SurveySensorEntity)level.getBlockEntity(SENSOR);CompoundTag link=card(source.sensorId(),0,0);
        for(int i=0;i<4;i++){level.setBlockAndUpdate(PINS.get(i).below(),Blocks.POLISHED_ANDESITE.defaultBlockState());level.setBlockAndUpdate(PINS.get(i),Hololith.MARKER.get().defaultBlockState().setValue(ReferenceMarkerBlock.COLOR,COLORS[i]));var marker=(ReferenceMarkerEntity)level.getBlockEntity(PINS.get(i));var direct=new CompoundTag();direct.putUUID("TableId",t.tableId());marker.bind(direct);}
        for(int x=-5;x<40;x++){level.setBlockAndUpdate(ASSEMBLER.offset(x,-1,0),Blocks.SMOOTH_STONE.defaultBlockState());level.setBlockAndUpdate(ASSEMBLER.east(x),Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE,RailShape.EAST_WEST));}
        level.setBlockAndUpdate(ASSEMBLER.below(),Blocks.REDSTONE_BLOCK.defaultBlockState());level.setBlockAndUpdate(ASSEMBLER,AllBlocks.CART_ASSEMBLER.getDefaultState().setValue(CartAssemblerBlock.RAIL_SHAPE,RailShape.EAST_WEST).setValue(CartAssemblerBlock.RAIL_TYPE,CartAssembleRailType.REGULAR).setValue(CartAssemblerBlock.POWERED,true));
        for(int x=0;x<=4;x++)for(int z=-2;z<=2;z++)level.setBlockAndUpdate(ASSEMBLER.offset(x,1,z),Blocks.WAXED_CUT_COPPER.defaultBlockState());
        table(level,ASSEMBLER.offset(CART_TABLE));level.setBlockAndUpdate(ASSEMBLER.offset(CART_SENSOR),Hololith.SENSOR.get().defaultBlockState());
        var cs=(SurveySensorEntity)level.getBlockEntity(ASSEMBLER.offset(CART_SENSOR));var ct=(HololithTableEntity)level.getBlockEntity(ASSEMBLER.offset(CART_TABLE));ct.bind(card(cs.sensorId(),-8,25));ct.configure(32,0,0,1,1,"minecraft:overworld",-8,25);
        for(int i=0;i<4;i++){BlockPos at=ASSEMBLER.offset(i%2*4,1,i/2*4-2);level.setBlockAndUpdate(at,Hololith.MARKER.get().defaultBlockState().setValue(ReferenceMarkerBlock.COLOR,COLORS[i]));((ReferenceMarkerEntity)level.getBlockEntity(at)).bind(card(cs.sensorId(),-8,25));}
        level.addFreshEntity(new SuperGlueEntity(level,SuperGlueEntity.span(ASSEMBLER.offset(0,1,-2),ASSEMBLER.offset(4,1,2))));level.addFreshEntity(new SuperGlueEntity(level,SuperGlueEntity.span(ASSEMBLER.offset(CART_TABLE).below(),ASSEMBLER.offset(CART_TABLE))));
        look(p,Vec3.atCenterOf(TABLE).add(6,5,8),Vec3.atCenterOf(TABLE).add(0,1,0));p.server.saveEverything(false,true,true);
    }
    private void assembleCart(ServerPlayer p){
        cart=Objects.requireNonNull(EntityType.MINECART.create(p.serverLevel()));cart.setPos(ASSEMBLER.getX()+.5,ASSEMBLER.getY()+.0625,ASSEMBLER.getZ()+.5);p.serverLevel().addFreshEntity(cart);((CartAssemblerBlockEntity)p.serverLevel().getBlockEntity(ASSEMBLER)).tryAssemble(cart);contraption=(AbstractContraptionEntity)Objects.requireNonNull(cart.getFirstPassenger());cartId=contraption.getId();
        evidence.add(Map.of("check","real Create cart assembled","passed",true));
    }
    private void startEncoder(Minecraft mc)throws IOException{
        String ffmpeg=System.getProperty("hololith.showcase.ffmpeg","");if(ffmpeg.isBlank()||!Files.isRegularFile(Path.of(ffmpeg)))throw new IOException("Pass -PffmpegPath with a trusted local FFmpeg binary");
        Files.createDirectories(Path.of("showcase"));int w=mc.getMainRenderTarget().width,h=mc.getMainRenderTarget().height;rgb=new byte[w*h*3];
        encoder=new ProcessBuilder(ffmpeg,"-hide_banner","-loglevel","warning","-y","-f","rawvideo","-pixel_format","rgb24","-video_size",w+"x"+h,"-framerate",Integer.toString(FPS),"-i","pipe:0","-an","-c:v","libx264","-preset","veryfast","-crf","18","-pix_fmt","yuv420p","-movflags","+faststart","showcase/gameplay-raw.mp4").redirectError(Path.of("showcase/encoder.log").toFile()).start();
        video=new BufferedOutputStream(encoder.getOutputStream(),rgb.length);
    }
    private void capture(RenderFrameEvent.Post event){
        if(done||recordingStart==0||video==null)return;int due=Math.min(SECONDS*FPS,(int)(seconds()*FPS));if(due<=frames)return;
        try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){
            int[] pixels=image.getPixelsRGBA();if(pixels.length*3!=rgb.length)throw new IOException("Window resized during capture");
            for(int i=0,j=0;i<pixels.length;i++){int color=pixels[i];rgb[j++]=(byte)color;rgb[j++]=(byte)(color>>>8);rgb[j++]=(byte)(color>>>16);}
            if(due-frames>1)repeatedFrames+=due-frames-1;
            while(frames<due){video.write(rgb);frames++;}
        }catch(Exception e){e.printStackTrace();evidence.add(Map.of("check","frame capture","passed",false,"error",e.toString()));finish();}
    }
    private void finish(){
        if(done)return;done=true;drive=false;
        try{
            if(video!=null){while(frames<SECONDS*FPS){video.write(rgb);frames++;}video.close();if(!encoder.waitFor(30,TimeUnit.SECONDS)||encoder.exitValue()!=0)throw new IOException("Encoder failed");}
            var report=new LinkedHashMap<String,Object>();report.put("capture","Actual Minecraft framebuffer, 30 fps. Scripted camera and normal interaction packets in an original creative demonstration world; no generated gameplay.");report.put("frames",frames);report.put("repeatedFrames",repeatedFrames);report.put("durationSeconds",SECONDS);report.put("checks",evidence);report.put("timestamp",java.time.Instant.now().toString());Files.createDirectories(Path.of("showcase"));Files.writeString(Path.of("showcase/capture.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
        }catch(Exception e){e.printStackTrace();}
        Minecraft.getInstance().execute(Minecraft.getInstance()::stop);
    }
}
