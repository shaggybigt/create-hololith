package dev.hololith.probe;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.crank.HandCrankBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.hololith.Hololith;
import dev.hololith.HololithTableEntity;
import dev.hololith.client.HololithRenderer;
import dev.hololith.survey.SurveyKey;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Real shaft-network acceptance in the already-authorized disposable probe world.
 * Call tick() on the client thread until it returns true. The supplied position is
 * the center of a separate 7x7 fixture, not the main survey rig.
 */
final class KineticChecks {
    private final BlockPos tablePos, shaftPos, driverPos;
    private final BiConsumer<String, Boolean> check;
    private CompletableFuture<Void> work=CompletableFuture.completedFuture(null);
    private int stage,ticks,phaseTick,stableRenderFrames,lastCameraRetry=-100,cameraRetries;
    private volatile boolean cranking,holdingCamera,done;
    private UUID playerId,tableId;
    private Vec3 oldPosition;
    private float oldYaw,oldPitch;
    private ItemStack oldHand;
    private boolean oldHideGui,oldFlying,oldMayFly,captureActive,captureMouseWasGrabbed;
    private long cachedRevision;
    private SurveyKey cachedKey;

    KineticChecks(BlockPos fixtureOrigin,BiConsumer<String,Boolean> check){
        tablePos=fixtureOrigin.immutable();shaftPos=tablePos.below();driverPos=tablePos.below(2);
        this.check=check;
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Post event)->{
            if(done)return;
            if(Minecraft.getInstance().screen==null&&cameraInPosition())stableRenderFrames++;else stableRenderFrames=0;
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event)->{
            if(done||!cranking&&!holdingCamera)return;
            try{
                ServerPlayer player=event.getServer().getPlayerList().getPlayer(playerId);
                if(player==null)return;
                if(holdingCamera){
                    if(!player.getAbilities().mayfly||!player.getAbilities().flying){
                        player.getAbilities().mayfly=true;player.getAbilities().flying=true;player.onUpdateAbilities();
                    }
                    player.setDeltaMovement(Vec3.ZERO);
                }
                if(!cranking||event.getServer().overworld().getGameTime()%5!=0
                        ||!(player.serverLevel().getBlockEntity(driverPos) instanceof HandCrankBlockEntity))return;
                // Use the ordinary Create block interaction, including the crank's own
                // finite turning interval and genuine generating-network calculation.
                player.gameMode.useItemOn(player,player.serverLevel(),player.getMainHandItem(),
                        InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(driverPos),
                                Direction.DOWN,driverPos,false));
                // Create also nudges the player's view while cranking. Restore the
                // observer camera after that genuine interaction, keeping the
                // powered, stopped and overloaded captures directly comparable.
                camera(player);
            }catch(Throwable failure){
                cranking=false;holdingCamera=false;failure.printStackTrace();
                record("kinetic fixture camera and normal crank interaction complete without error",false);
            }
        });
    }

    private void record(String text,boolean passed){check.accept(text,passed);}
    private void next(){endCapture();holdingCamera=false;lastCameraRetry=-100;cameraRetries=0;stage++;phaseTick=ticks;}
    private boolean waited(int delay){return ticks-phaseTick>=delay;}
    private void server(Consumer<ServerPlayer> action){
        var server=Objects.requireNonNull(Minecraft.getInstance().getSingleplayerServer());
        work=CompletableFuture.runAsync(()->action.accept(Objects.requireNonNull(server.getPlayerList().getPlayer(playerId))),server);
    }
    private HololithTableEntity table(ServerPlayer player){
        if(player.serverLevel().getBlockEntity(tablePos) instanceof HololithTableEntity table)return table;
        throw new IllegalStateException("Kinetic fixture table missing at "+tablePos);
    }
    private HololithTableEntity clientTable(){
        var level=Minecraft.getInstance().level;
        return level!=null&&level.getBlockEntity(tablePos) instanceof HololithTableEntity table?table:null;
    }
    private void configure(HololithTableEntity table,int span,int zoom){
        table.configure(span,0,zoom,1,0,"minecraft:overworld",tablePos.getX(),tablePos.getZ());
    }
    private void motor(ServerPlayer player,int rpm){
        var level=player.serverLevel();
        if(!(level.getBlockEntity(driverPos) instanceof CreativeMotorBlockEntity)){
            level.removeBlock(driverPos,false);
            level.setBlockAndUpdate(driverPos,AllBlocks.CREATIVE_MOTOR.getDefaultState()
                    .setValue(BlockStateProperties.FACING,Direction.UP));
        }
        var motor=(CreativeMotorBlockEntity)Objects.requireNonNull(level.getBlockEntity(driverPos));
        // This is the motor's normal setting and generated-rotation path. No speed
        // is assigned directly to the Hololith, its shaft, or its network.
        motor.generatedSpeed.setValue(rpm);
    }
    private static boolean near(float a,float b){return Math.abs(a-b)<.01f;}
    private float networkDemand(HololithTableEntity table){
        return table.hasNetwork()?table.getOrCreateNetwork().getActualStressOf(table):0;
    }
    private void demand(ServerPlayer player,float expectedImpact,float expectedRpm,String label){
        HololithTableEntity table=table(player);
        record(label+" uses the expected stress multiplier",near(table.stressImpact(),expectedImpact));
        record(label+" changes actual Create network stress",table.hasNetwork()
                &&near(networkDemand(table),expectedImpact*expectedRpm));
        record(label+" reports its live stress demand",near(table.stressUsage(),expectedImpact*expectedRpm));
    }
    private void clientPower(boolean expected,String label){
        var table=clientTable();
        record(label+" reaches the client projection gate",table!=null&&table.isProjecting()==expected);
    }
    private static Object mesh(HololithTableEntity table)throws ReflectiveOperationException{
        if(table==null)return null;
        Field cache=HololithRenderer.class.getDeclaredField("CACHE");cache.setAccessible(true);
        Object entry=((Map<?,?>)cache.get(null)).get(table);if(entry==null)return null;
        Field value=entry.getClass().getDeclaredField("mesh");value.setAccessible(true);return value.get(entry);
    }
    private void screenshot(String name){
        var mc=Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),text->System.out.println(text.getString()));
    }
    private Vec3 cameraEye(){return Vec3.atCenterOf(tablePos).add(4,3.5,5);}
    private boolean cameraInPosition(){
        var player=Minecraft.getInstance().player;
        if(player==null||!player.getAbilities().flying)return false;
        Vec3 delta=Vec3.atCenterOf(tablePos).add(0,1.3,0).subtract(player.getEyePosition()).normalize();
        return player.getEyePosition().distanceToSqr(cameraEye())<.04
                &&player.getLookAngle().dot(delta)>.999;
    }
    private boolean cameraReady(){return stableRenderFrames>=8&&cameraInPosition();}
    private boolean awaitCamera(int minimumWait,int deadline,String label){
        if(!waited(minimumWait))return false;
        var mc=Minecraft.getInstance();
        if(!captureActive){captureActive=true;captureMouseWasGrabbed=mc.mouseHandler.isMouseGrabbed();stableRenderFrames=0;}
        if(mc.screen!=null){
            System.out.println("HOLOLITH KINETIC CAMERA "+label+": closing unexpected "+mc.screen.getClass().getSimpleName());
            mc.setScreen(null);stableRenderFrames=0;
        }
        // Only capture phases take over the observer view. Release the cursor so
        // desktop mouse use cannot rotate it or reopen a table control screen.
        mc.options.keyAttack.setDown(false);mc.options.keyUse.setDown(false);mc.mouseHandler.releaseMouse();
        holdingCamera=true;
        if(cameraReady())return true;
        if(waited(deadline)){
            cameraDiagnostic(label+" failed to converge");
            return true; // The unchanged framing assertion below reports the failure.
        }
        if(ticks-lastCameraRetry>=20){
            lastCameraRetry=ticks;cameraRetries++;stableRenderFrames=0;
            // Reposition the real observer through its server connection, then
            // require fresh rendered frames. Never accept a stale screenshot merely
            // because enough time elapsed after an earlier camera placement.
            server(this::camera);
        }
        return false;
    }
    private void endCapture(){
        if(!captureActive)return;captureActive=false;
        var mc=Minecraft.getInstance();
        if(captureMouseWasGrabbed&&mc.screen==null&&mc.isWindowActive())mc.mouseHandler.grabMouse();
    }
    private void cameraDiagnostic(String label){
        var player=Minecraft.getInstance().player;
        if(player==null){System.out.println("HOLOLITH KINETIC CAMERA "+label+": client player unavailable");return;}
        Vec3 eye=player.getEyePosition(),target=Vec3.atCenterOf(tablePos).add(0,1.3,0);
        System.out.println("HOLOLITH KINETIC CAMERA "+label+": stage="+stage+", phaseTicks="+(ticks-phaseTick)
                +", retries="+cameraRetries+", stableFrames="+stableRenderFrames+", actualEye="+eye
                +", expectedEye="+cameraEye()+", distance="+eye.distanceTo(cameraEye())
                +", lookDot="+player.getLookAngle().dot(target.subtract(eye).normalize())
                +", yaw="+player.getYRot()+", pitch="+player.getXRot()+", flying="+player.getAbilities().flying
                +", mayfly="+player.getAbilities().mayfly+", velocity="+player.getDeltaMovement()
                +", screen="+(Minecraft.getInstance().screen==null?"none":Minecraft.getInstance().screen.getClass().getSimpleName()));
    }
    private void camera(ServerPlayer player){
        Vec3 camera=cameraEye();
        Vec3 delta=Vec3.atCenterOf(tablePos).add(0,1.3,0).subtract(camera);
        float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
        float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z)));
        player.getAbilities().mayfly=true;player.getAbilities().flying=true;player.onUpdateAbilities();
        player.setDeltaMovement(Vec3.ZERO);
        player.connection.teleport(camera.x,camera.y-player.getEyeHeight(),camera.z,yaw,pitch);
    }
    private void restore(ServerPlayer player){
        cranking=false;holdingCamera=false;
        if(player.serverLevel().getBlockEntity(driverPos) instanceof CreativeMotorBlockEntity)motor(player,0);
        if(oldHand!=null)player.setItemInHand(InteractionHand.MAIN_HAND,oldHand);
        if(oldPosition!=null)player.connection.teleport(oldPosition.x,oldPosition.y,oldPosition.z,oldYaw,oldPitch);
        player.setDeltaMovement(Vec3.ZERO);
        player.getAbilities().mayfly=oldMayFly;player.getAbilities().flying=oldFlying;player.onUpdateAbilities();
    }

    boolean tick(){
        if(done)return true;
        if(!work.isDone())return false;
        ticks++;
        var mc=Minecraft.getInstance();
        try{
            work.join();
            if(ticks>2600)throw new IllegalStateException("Kinetic fixture timed out at stage "+stage);
            switch(stage){
                case 0->{
                    if(mc.player==null||mc.level==null)throw new IllegalStateException("No client world for kinetic fixture");
                    playerId=mc.player.getUUID();oldHideGui=mc.options.hideGui;mc.options.hideGui=true;
                    server(player->{
                        oldPosition=player.position();oldYaw=player.getYRot();oldPitch=player.getXRot();
                        oldFlying=player.getAbilities().flying;oldMayFly=player.getAbilities().mayfly;
                        oldHand=player.getMainHandItem().copy();player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
                        var level=player.serverLevel();
                        // Fixture callers choose an unused region of a marked disposable
                        // world. Keep its construction and chunk requests tightly bounded.
                        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){
                            level.setBlockAndUpdate(tablePos.offset(x,-3,z),Blocks.STONE.defaultBlockState());
                            for(int y=-2;y<=3;y++)level.setBlockAndUpdate(tablePos.offset(x,y,z),Blocks.AIR.defaultBlockState());
                        }
                        var state=Hololith.TABLE.get().defaultBlockState();
                        level.setBlockAndUpdate(tablePos,state);
                        Hololith.TABLE.get().setPlacedBy(level,tablePos,state,player,Hololith.TABLE_ITEM.toStack());
                        level.setBlockAndUpdate(shaftPos,AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS,Direction.Axis.Y));
                        configure(table(player),32,0);tableId=table(player).tableId();camera(player);
                    });next();
                }
                case 1->{
                    if(!waited(40))return false;
                    clientPower(false,"unpowered table");
                    server(player->{
                        var table=table(player);
                        record("unpowered real table does not project",!table.isProjecting()&&near(table.getSpeed(),0));
                        record("unpowered new table does not start a terrain survey",table.snapshot()==null);
                        int tiles=0,entities=0;
                        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
                            BlockPos pos=tablePos.offset(x,0,z);
                            if(player.serverLevel().getBlockState(pos).is(Hololith.TABLE.get()))tiles++;
                            if(player.serverLevel().getBlockEntity(pos)!=null)entities++;
                        }
                        record("powered table remains one nine-tile instrument with one block entity",tiles==9&&entities==1);
                        motor(player,8);
                    });next();
                }
                case 2->{
                    if(!waited(50))return false;
                    clientPower(false,"below-minimum shaft speed");
                    server(player->{
                        var table=table(player);
                        record("real motor and shaft reach the table at 8 RPM",near(Math.abs(table.getSpeed()),8));
                        record("8 RPM is insufficient for projection",!table.isProjecting()&&near(table.requiredRpm(),16));
                        record("slow table does not start a terrain survey",table.snapshot()==null);
                        motor(player,16);
                    });next();
                }
                case 3->{
                    if(!awaitCamera(140,360,"powered capture"))return false;
                    var client=clientTable();
                    if((client==null||client.snapshot()==null)&&!waited(360))return false;
                    record("powered projection capture has a stationary flying camera and rendered frames",cameraReady());
                    clientPower(true,"minimum operating speed");
                    record("powered real-world table builds a GPU terrain mesh",mesh(client)!=null);
                    screenshot("hololith-kinetic-powered.png");
                    server(player->{
                        var table=table(player);
                        record("16 RPM starts projection through the real shaft",near(Math.abs(table.getSpeed()),16)&&table.isProjecting());
                        record("powered table captures actual loaded terrain",table.snapshot()!=null&&table.snapshot().knownColumns()>0);
                        demand(player,4,16,"32-block survey");
                        if(table.snapshot()!=null){cachedRevision=table.snapshot().revision;cachedKey=table.snapshot().key();}
                        motor(player,0);
                    });next();
                }
                case 4->{
                    if(!awaitCamera(60,180,"stopped capture"))return false;
                    record("stopped projection capture retains the same camera framing",cameraReady());
                    clientPower(false,"stopped shaft");
                    screenshot("hololith-kinetic-stopped.png");
                    server(player->{
                        var table=table(player);
                        record("stopping the source stops projection",!table.isProjecting()&&near(table.getSpeed(),0));
                        record("power loss retains the completed terrain and its revision",cachedKey!=null&&table.snapshot()!=null
                                &&cachedKey.equals(table.snapshot().key())&&cachedRevision==table.snapshot().revision);
                        var saved=table.saveWithFullMetadata(player.serverLevel().registryAccess());
                        record("unpowered table keeps its terrain in persistent NBT",saved.contains("Survey"));
                        motor(player,-16);
                    });next();
                }
                case 5->{
                    if(!waited(70))return false;
                    clientPower(true,"reverse shaft");
                    server(player->{
                        var table=table(player);
                        record("reverse rotation supplies valid kinetic power",table.getSpeed()<0&&near(Math.abs(table.getSpeed()),16)&&table.isProjecting());
                        record("power cycling preserves table identity",table.tableId().equals(tableId));
                        configure(table,64,0);
                    });next();
                }
                case 6->{
                    if(!waited(35))return false;
                    server(player->{demand(player,8,16,"64-block survey");configure(table(player),128,0);});next();
                }
                case 7->{
                    if(!waited(35))return false;
                    server(player->{demand(player,16,16,"128-block survey");configure(table(player),128,2);});next();
                }
                case 8->{
                    if(!waited(35))return false;
                    server(player->{
                        demand(player,36,16,"150-percent 128-block projection");
                        record("a sufficiently strong network supports the enlarged projection",table(player).isProjecting());
                        configure(table(player),32,0);
                    });next();
                }
                case 9->{
                    if(!waited(35))return false;
                    server(player->{
                        demand(player,4,16,"reduced projection");
                        var level=player.serverLevel();level.removeBlock(driverPos,false);
                        level.setBlockAndUpdate(driverPos,AllBlocks.HAND_CRANK.getDefaultState().setValue(BlockStateProperties.FACING,Direction.DOWN));
                        record("finite-capacity source is an actual Create hand crank",level.getBlockEntity(driverPos) instanceof HandCrankBlockEntity);
                        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
                        cranking=true;
                    });next();
                }
                case 10->{
                    if(!waited(60))return false;
                    clientPower(true,"hand-cranked small projection");
                    server(player->{
                        var table=table(player);
                        record("normal crank interactions generate real 32 RPM power",near(Math.abs(table.getSpeed()),32)&&table.isProjecting());
                        demand(player,4,32,"hand-cranked 32-block survey");
                        float capacity=table.hasNetwork()?table.getOrCreateNetwork().calculateCapacity():0;
                        record("hand crank provides bounded capacity between small and large demand",capacity>=128&&capacity<1152);
                        configure(table,128,2);
                    });next();
                }
                case 11->{
                    if(!awaitCamera(50,180,"overstressed capture"))return false;
                    record("overstressed projection capture retains the same camera framing",cameraReady());
                    clientPower(false,"overstressed projection");
                    screenshot("hololith-kinetic-overstressed.png");
                    server(player->{
                        var table=table(player);
                        record("enlarging the map overstresses the actual finite network",table.isOverStressed()
                                &&near(Math.abs(table.getTheoreticalSpeed()),32)&&near(table.getSpeed(),0));
                        record("overstress stops projection",!table.isProjecting());
                        record("network demand exceeds its real available capacity",table.hasNetwork()
                                &&networkDemand(table)>table.getOrCreateNetwork().calculateCapacity());
                        configure(table,32,0);
                    });next();
                }
                case 12->{
                    if(!waited(60))return false;
                    clientPower(true,"recovered finite-capacity projection");
                    server(player->{
                        var table=table(player);
                        record("reducing map demand restores an overstressed network without replacing blocks",
                                !table.isOverStressed()&&near(Math.abs(table.getSpeed()),32)&&table.isProjecting());
                        demand(player,4,32,"recovered hand-cranked survey");
                        cranking=false;
                    });next();
                }
                case 13->{
                    if(!waited(60))return false;
                    clientPower(false,"released crank");
                    server(player->{
                        var table=table(player);
                        record("releasing the crank naturally stops generation and projection",
                                near(table.getSpeed(),0)&&!table.isProjecting());
                        record("power controls do not change the linked instrument identity",table.tableId().equals(tableId));
                        restore(player);
                    });next();
                }
                case 14->{mc.options.hideGui=oldHideGui;done=true;}
                default->throw new IllegalStateException("Unexpected kinetic stage "+stage);
            }
        }catch(Throwable failure){
            failure.printStackTrace();record("kinetic network fixture completes without error (stage "+stage+")",false);
            endCapture();cranking=false;holdingCamera=false;done=true;mc.options.hideGui=oldHideGui;
            try{server(this::restore);}catch(Throwable cleanup){cleanup.printStackTrace();}
        }
        return done;
    }
}
