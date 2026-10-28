package dev.hololith.probe;

import com.google.gson.*;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.bearing.*;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.mounted.*;
import com.simibubi.create.content.contraptions.sync.ContraptionInteractionPacket;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.hololith.*;
import dev.hololith.client.*;
import dev.hololith.compat.ContraptionSurveys;
import dev.hololith.survey.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Opt-in, disposable-world tests using Create's real assembly and entity lifecycle. */
final class ContraptionFixture {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final BlockPos BEARING=new BlockPos(512,199,0);
    private static final BlockPos TABLE=new BlockPos(2,3,0),SENSOR=new BlockPos(4,0,1);
    private static final List<BlockPos> MARKERS=List.of(new BlockPos(0,0,-2),new BlockPos(4,0,-2),new BlockPos(4,0,2),new BlockPos(0,0,2));
    private static final DyeColor[] COLORS={DyeColor.RED,DyeColor.LIME,DyeColor.BLUE,DyeColor.YELLOW};
    private final boolean resume;
    private final long started=System.nanoTime();
    private final List<Map<String,Object>> checks=new CopyOnWriteArrayList<>();
    private CompletableFuture<Void> work=CompletableFuture.completedFuture(null);
    private int ticks,phase,phaseTick,driveTicks;
    private boolean done;
    private Rig bearing,active,cartRig;
    private AbstractMinecart cart;
    private Vec3 cartStart;private BlockPos restoredCart;
    private int interpolatedMarkerFrames;
    private UUID expectedTable,expectedSensor;
    private List<UUID> expectedMarkers=List.of();
    ContraptionFixture(boolean resume){
        this.resume=resume;
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Pre event)->{
            if(driveTicks>0&&cart!=null&&cart.isAlive()){cart.setDeltaMovement(.16,0,0);if(--driveTicks==0)cart.setDeltaMovement(Vec3.ZERO);}
        });
    }
    private void check(String name,boolean passed){
        checks.add(Map.of("test",name,"passed",passed));System.out.println("HOLOLITH CONTRAPTION "+passed+": "+name);
    }
    private void server(Consumer<ServerPlayer> action){
        var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();UUID id=mc.player.getUUID();
        work=CompletableFuture.runAsync(()->action.accept(Objects.requireNonNull(server.getPlayerList().getPlayer(id))),server);
    }
    private void next(){phase++;phaseTick=ticks;}
    private boolean waited(int count){return ticks-phaseTick>=count;}
    void tick(){
        if(done)return;var mc=Minecraft.getInstance();
        try{
            if(System.nanoTime()-started>360_000_000_000L)throw new IllegalStateException("Contraption fixture exceeded six minutes");
            if(mc.level==null||mc.player==null)return;ticks++;
            if(phase==10)sampleMovingMarkers();
            if(work.isCompletedExceptionally())work.join();if(!work.isDone()||ticks<80||!waited(25))return;
            if(resume){resumeTick();return;}
            switch(phase){
                case 0->{
                    server(p->{
                        p.setGameMode(GameType.CREATIVE);p.getInventory().selected=0;p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));
                        p.serverLevel().setDayTime(6000);p.serverLevel().setWeatherParameters(6000,0,false,false);
                        for(int x=29;x<=37;x++)for(int z=-3;z<=3;z++)p.serverLevel().getChunk(x,z);
                        var level=p.serverLevel();
                        for(int x=-20;x<40;x++)for(int z=-20;z<20;z++)level.setBlock(BEARING.offset(x,-19,z),(z>=-1&&z<=1?Blocks.BLUE_CONCRETE:Blocks.GRASS_BLOCK).defaultBlockState(),2);
                        level.setBlockAndUpdate(BEARING,AllBlocks.MECHANICAL_BEARING.getDefaultState().setValue(BlockStateProperties.FACING,Direction.UP));
                        bearing=platform(p,BEARING.above(),0);active=bearing;camera(p);
                    });next();
                }
                case 1->{
                    if(!waited(140))return;
                    server(p->{var before=(HololithTableEntity)p.serverLevel().getBlockEntity(bearing.tableWorld());
                        rememberSurvey(bearing,before,"stationary bearing fixture");
                        var controller=controller(p);controller.getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE).setValue(IControlContraption.RotationMode.ROTATE_NEVER_PLACE.ordinal());controller.assemble();bearing.entity=Objects.requireNonNull(controller.getMovedContraption(),"Bearing did not assemble");bearing.entityId=bearing.entity.getId();
                        check("bearing captures all nine table tiles with only center explicitly glued",tableCount(bearing.entity)==9);
                        check("bearing captures the formerly working real motor and shaft",capturedDrive(bearing));
                        check("assembly removes original table blocks",p.serverLevel().getBlockState(bearing.tableWorld()).isAir());camera(p);
                    });next();
                }
                case 2->{
                    if(!waited(140))return;verifyClient("bearing assembled");
                    server(p->{verifyServer(p,active,"bearing assembled");controller(p).setAngle(90);controller(p).sendData();camera(p);});next();
                }
                case 3->{
                    if(!waited(140))return;verifyClient("bearing rotated");shot("contraption-bearing.png");
                    server(p->{verifyServer(p,active,"bearing rotated");check("bearing applies a quarter turn",Math.abs(((ControlledContraptionEntity)active.entity).getAngle(1)-90)<.01);near(p,active.entity.toGlobalVector(Vec3.atCenterOf(active.markers.getFirst()),1));hand(p,new ItemStack(Items.MAGENTA_DYE));});phase=100;phaseTick=ticks;
                }
                case 100->{interact(active.markers.getFirst());next();}
                case 101->{
                    var info=clientEntity().getContraption().getBlocks().get(active.markers.getFirst());
                    check("mounted dye interaction updates client block color",info.state().getValue(ReferenceMarkerBlock.COLOR)==DyeColor.MAGENTA);
                    server(p->{var marker=ContraptionSurveys.marker(active.entity,active.markers.getFirst());check("mounted dye interaction changes authoritative color",marker.getBlockState().getValue(ReferenceMarkerBlock.COLOR)==DyeColor.MAGENTA);marker.unlink();near(p,active.entity.toGlobalVector(Vec3.atCenterOf(active.sensor),1));hand(p,Hololith.CARD.toStack());});next();
                }
                case 102->{interact(active.sensor);next();}
                case 103->{
                    var card=mc.player.getMainHandItem().getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
                    check("mounted sensor card records persistent sensor identity",card.hasUUID("SensorId")&&card.getUUID("SensorId").equals(active.sensorId));
                    server(p->near(p,active.entity.toGlobalVector(Vec3.atCenterOf(active.markers.getFirst()),1)));next();
                }
                case 104->{interact(active.markers.getFirst());next();}
                case 105->{
                    check("mounted card relink restores fourth marker on powered stationary observer",clientObserver()!=null&&clientObserver().markers().size()==4);
                    server(p->{var marker=ContraptionSurveys.marker(active.entity,active.markers.getFirst());check("mounted card links authoritative reference to sensor",active.sensorId.equals(marker.targetId())&&marker.targetKind()==1);near(p,active.entity.toGlobalVector(Vec3.atCenterOf(active.table),1));hand(p,ItemStack.EMPTY);});phase=4;phaseTick=ticks;
                }
                case 4->{interact(active.table);next();}
                case 5->{
                    check("Create interaction packet opens mounted instrument panel",mc.screen instanceof HololithControls);
                    if(mc.screen instanceof HololithControls controls){for(var child:controls.children())if(child instanceof Button button&&button.getMessage().getString().startsWith("Area:"))button.onPress();for(var child:controls.children())if(child instanceof Button button&&button.getMessage().getString().startsWith("Rotation:"))button.onPress();for(var child:controls.children())if(child instanceof Button button&&button.getMessage().getString().startsWith("Apply"))button.onPress();}
                    next();
                }
                case 6->{
                    if(!waited(160))return;
                    active.cachedKey=null;active.expectedSpan=64;active.expectedRotation=1;
                    verifyClient("mounted controls");
                    server(p->{var table=ContraptionSurveys.table(active.entity,active.table);check("mounted controls change authoritative scale and rotation",table.span()==64&&table.rotation()==1);check("retargeting an unpowered captured table waits without scanning",table.snapshot()==null&&!table.isProjecting());controller(p).setAngle(180);controller(p).sendData();camera(p);});next();
                }
                case 7->{
                    if(!waited(140))return;verifyClient("bearing half turn");
                    server(p->{verifyServer(p,active,"bearing half turn");controller(p).setAngle(0);controller(p).sendData();});phase=70;phaseTick=ticks;
                }
                case 70->{
                    if(!waited(80))return;
                    server(p->{controller(p).disassemble();verifyRestored(p,bearing,bearing.tableWorld(),"bearing settings trial disassembly");motor(p,bearing.tableWorld(),0);camera(p);});next();
                }
                case 71->{
                    if(!waited(60))return;
                    server(p->{var table=worldTable(p,bearing.tableWorld());check("disassembled retargeted table waits for a real shaft source",!table.isProjecting()&&table.snapshot()==null);motor(p,bearing.tableWorld(),16);});next();
                }
                case 72->{
                    if(!waited(160))return;
                    server(p->{var table=worldTable(p,bearing.tableWorld());rememberSurvey(bearing,table,"reconnected bearing fixture");check("reconnected bearing table applies mounted scale and rotation",table.span()==64&&table.rotation()==1);controller(p).assemble();bearing.entity=Objects.requireNonNull(controller(p).getMovedContraption());bearing.entityId=bearing.entity.getId();controller(p).setAngle(180);controller(p).sendData();camera(p);});next();
                }
                case 73->{
                    if(!waited(140))return;verifyClient("bearing recaptured after real reconnection");
                    server(p->{verifyServer(p,active,"bearing recaptured after real reconnection");var level=p.serverLevel();BlockPos assembler=BEARING.east(24);
                        for(int x=0;x<=25;x++){level.setBlockAndUpdate(assembler.offset(x,-1,0),Blocks.STONE.defaultBlockState());if(x>0)level.setBlockAndUpdate(assembler.east(x),Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE,RailShape.EAST_WEST));}
                        level.setBlockAndUpdate(assembler.below(),Blocks.REDSTONE_BLOCK.defaultBlockState());
                        level.setBlockAndUpdate(assembler,AllBlocks.CART_ASSEMBLER.getDefaultState().setValue(CartAssemblerBlock.RAIL_SHAPE,RailShape.EAST_WEST).setValue(CartAssemblerBlock.RAIL_TYPE,CartAssembleRailType.REGULAR).setValue(CartAssemblerBlock.POWERED,true));
                        cartRig=platform(p,assembler,1);active=cartRig;camera(p);
                    });phase=8;phaseTick=ticks;
                }
                case 8->{
                    if(!waited(140))return;
                    server(p->{var level=p.serverLevel();rememberSurvey(active,worldTable(p,active.tableWorld()),"stationary cart fixture");cart=Objects.requireNonNull(EntityType.MINECART.create(level));cart.setPos(active.anchor.getX()+.5,active.anchor.getY()+.0625,active.anchor.getZ()+.5);level.addFreshEntity(cart);
                        ((CartAssemblerBlockEntity)level.getBlockEntity(active.anchor)).tryAssemble(cart);
                        active.entity=(AbstractContraptionEntity)cart.getFirstPassenger();if(active.entity==null)throw new IllegalStateException("Cart assembler did not capture platform");
                        active.entityId=active.entity.getId();check("cart captures all nine table tiles",tableCount(active.entity)==9);check("cart captures the formerly working real motor and shaft",capturedDrive(active));camera(p);
                    });next();
                }
                case 9->{
                    if(!waited(140))return;verifyClient("cart assembled");server(p->{verifyServer(p,active,"cart assembled");cartStart=cart.position();driveTicks=70;});next();
                }
                case 10->{
                    if(!waited(160))return;
                    check("moving cart marker positions interpolate between server ticks",interpolatedMarkerFrames>=3);
                    server(p->{check("minecart physically translates on rail",cart.position().distanceTo(cartStart)>4);verifyServer(p,active,"cart translated");camera(p);});next();
                }
                case 11->{
                    if(!waited(60))return;verifyClient("cart translated");shot("contraption-cart.png");
                    server(p->{restoredCart=BlockPos.containing(active.entity.toGlobalVector(Vec3.atCenterOf(active.table),1));active.entity.disassemble();verifyRestored(p,active,restoredCart,"cart disassembly");motor(p,restoredCart,0);});phase=110;phaseTick=ticks;
                }
                case 110->{
                    if(!waited(60))return;
                    server(p->{var table=worldTable(p,restoredCart);check("disassembled cart table retains cache while source is stopped",!table.isProjecting()&&cached(active,table));motor(p,restoredCart,16);});next();
                }
                case 111->{
                    if(!waited(140))return;
                    server(p->{var table=worldTable(p,restoredCart);check("cart disassembly reconnects genuine power and projection",powered(table)&&table.snapshot()!=null&&table.markers().size()==4);active=bearing;camera(p);});phase=12;phaseTick=ticks;
                }
                case 12->{
                    if(!waited(100))return;verifyClient("bearing after cart test");
                    server(p->{expectedTable=bearing.tableId;expectedSensor=bearing.sensorId;expectedMarkers=List.copyOf(bearing.markerIds);p.server.saveEverything(false,true,true);});next();
                }
                case 13->{writeResume();shot("contraption-before-restart.png");finish();}
            }
        }catch(Throwable error){error.printStackTrace();checks.add(Map.of("test","fixture exception","passed",false,"error",error.toString()));finish();}
    }
    private void resumeTick()throws Exception{
        switch(phase){
            case 0->{
                JsonObject saved=JSON.fromJson(Files.readString(Path.of("validation/contraption-fixture.json")),JsonObject.class);
                expectedTable=UUID.fromString(saved.get("tableId").getAsString());expectedSensor=UUID.fromString(saved.get("sensorId").getAsString());
                expectedMarkers=new ArrayList<>();for(var id:saved.getAsJsonArray("markers"))expectedMarkers.add(UUID.fromString(id.getAsString()));
                server(p->{bearing=new Rig(BEARING.above(),0);bearing.tableId=expectedTable;bearing.sensorId=expectedSensor;bearing.markerIds.addAll(expectedMarkers);bearing.expectedSpan=64;bearing.expectedRotation=1;
                    JsonObject key=saved.getAsJsonObject("cachedKey");bearing.cachedKey=new SurveyKey(key.get("dimension").getAsString(),key.get("x").getAsInt(),key.get("z").getAsInt(),key.get("span").getAsInt());bearing.cachedRevision=saved.get("cachedRevision").getAsLong();
                    active=bearing;near(p,Vec3.atCenterOf(BEARING).add(3,3,5));});next();
            }
            case 1->{
                if(!waited(180))return;
                server(p->{bearing.entity=Objects.requireNonNull(controller(p).getMovedContraption(),"Saved bearing did not recover entity");bearing.entityId=bearing.entity.getId();verifyServer(p,bearing,"fresh JVM mounted reload");
                    var table=ContraptionSurveys.table(bearing.entity,bearing.table);check("saved mounted control settings survive JVM restart",table.span()==64&&table.rotation()==1);
                    var marker=ContraptionSurveys.marker(bearing.entity,bearing.markers.getFirst());check("mounted dye and link survive JVM restart",marker.getBlockState().getValue(ReferenceMarkerBlock.COLOR)==DyeColor.MAGENTA&&expectedSensor.equals(marker.targetId()));camera(p);});next();
            }
            case 2->{
                if(!waited(100))return;verifyClient("fresh JVM mounted reload");shot("contraption-restart.png");
                server(p->{controller(p).setAngle(0);controller(p).sendData();});next();
            }
            case 3->{
                if(!waited(80))return;
                server(p->{controller(p).disassemble();verifyRestored(p,bearing,bearing.tableWorld(),"bearing disassembly");motor(p,bearing.tableWorld(),0);near(p,Vec3.atCenterOf(bearing.tableWorld()).add(3,4,5));});next();
            }
            case 4->{
                if(!waited(60))return;
                server(p->{var table=worldTable(p,bearing.tableWorld());check("reloaded disassembled table retains survey while genuinely unpowered",!table.isProjecting()&&cached(bearing,table));motor(p,bearing.tableWorld(),16);});next();
            }
            case 5->{
                if(!waited(140))return;
                var table=Minecraft.getInstance().level.getBlockEntity(bearing.tableWorld());
                check("restored world table visibly resumes terrain and four markers",table instanceof HololithTableEntity t&&t.isProjecting()&&t.snapshot()!=null&&t.markers().size()==4);
                server(p->{var restored=worldTable(p,bearing.tableWorld());check("reloaded bearing disassembly reconnects a real Create source and network",powered(restored));check("reconnected table keeps saved scale rotation and sensor link",restored.span()==64&&restored.rotation()==1&&expectedSensor.equals(restored.sensorId()));});next();
            }
            case 6->{shot("contraption-disassembled.png");finish();}
        }
    }
    private Rig platform(ServerPlayer p,BlockPos anchor,int deck){
        var level=p.serverLevel();Rig rig=new Rig(anchor,deck);
        for(int x=0;x<=4;x++)for(int z=-2;z<=2;z++)level.setBlockAndUpdate(anchor.offset(x,deck,z),Blocks.ANDESITE.defaultBlockState());
        for(int part=0;part<9;part++)level.setBlockAndUpdate(rig.tableWorld().offset(part%3-1,0,part/3-1),Hololith.TABLE.get().defaultBlockState().setValue(HololithTableBlock.PART,part));
        level.setBlockAndUpdate(anchor.offset(rig.sensor),Hololith.SENSOR.get().defaultBlockState());
        var sensor=(SurveySensorEntity)level.getBlockEntity(anchor.offset(rig.sensor));rig.sensorId=sensor.sensorId();
        var table=(HololithTableEntity)level.getBlockEntity(rig.tableWorld());rig.tableId=table.tableId();
        CompoundTag card=new CompoundTag();card.putUUID("SensorId",rig.sensorId);card.putString("Dimension",level.dimension().location().toString());card.putInt("X",anchor.getX()+rig.sensor.getX());card.putInt("Z",anchor.getZ()+rig.sensor.getZ());
        table.bind(card);table.configure(32,0,0,1,1,level.dimension().location().toString(),anchor.getX(),anchor.getZ());
        addDrive(p,rig.tableWorld());
        for(int i=0;i<4;i++){var pos=anchor.offset(rig.markers.get(i));level.setBlockAndUpdate(pos,Hololith.MARKER.get().defaultBlockState().setValue(ReferenceMarkerBlock.COLOR,COLORS[i]));var marker=(ReferenceMarkerEntity)level.getBlockEntity(pos);marker.bind(card);rig.markerIds.add(marker.markerId());}
        // The deck and its one center-to-table attachment are glued; outer table tiles are not.
        level.addFreshEntity(new SuperGlueEntity(level,SuperGlueEntity.span(anchor.offset(0,deck,-2),anchor.offset(4,deck,2))));
        level.addFreshEntity(new SuperGlueEntity(level,SuperGlueEntity.span(rig.tableWorld().below(3),rig.tableWorld())));
        rig.observer=anchor.offset(-10,deck+3,0);
        for(int part=0;part<9;part++)level.setBlockAndUpdate(rig.observer.offset(part%3-1,0,part/3-1),Hololith.TABLE.get().defaultBlockState().setValue(HololithTableBlock.PART,part));
        var observer=(HololithTableEntity)level.getBlockEntity(rig.observer);observer.bind(card);observer.configure(32,0,0,1,1,level.dimension().location().toString(),anchor.getX(),anchor.getZ());
        addDrive(p,rig.observer);
        return rig;
    }
    private HololithTableEntity worldTable(ServerPlayer player,BlockPos pos){
        if(player.serverLevel().getBlockEntity(pos) instanceof HololithTableEntity table)return table;
        throw new IllegalStateException("Expected real table at "+pos);
    }
    private void addDrive(ServerPlayer player,BlockPos table){
        var level=player.serverLevel();
        level.setBlockAndUpdate(table.below(),AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS,Direction.Axis.Y));
        level.setBlockAndUpdate(table.below(2),AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.UP));
        motor(player,table,16);
    }
    private void motor(ServerPlayer player,BlockPos table,int rpm){
        if(!(player.serverLevel().getBlockEntity(table.below(2)) instanceof CreativeMotorBlockEntity motor))throw new IllegalStateException("Expected genuine fixture motor beneath "+table);
        motor.generatedSpeed.setValue(rpm);
    }
    private static boolean powered(HololithTableEntity table){
        return table!=null&&table.hasSource()&&table.hasNetwork()&&table.isProjecting()&&Math.abs(Math.abs(table.getSpeed())-16)<.01;
    }
    private static boolean capturedDrive(Rig rig){
        var blocks=rig.entity.getContraption().getBlocks();
        return blocks.containsKey(rig.table.below())&&blocks.get(rig.table.below()).state().is(AllBlocks.SHAFT.get())
            &&blocks.containsKey(rig.table.below(2))&&blocks.get(rig.table.below(2)).state().is(AllBlocks.CREATIVE_MOTOR.get());
    }
    private void rememberSurvey(Rig rig,HololithTableEntity table,String label){
        check(label+" receives genuine motor and shaft power",powered(table));
        check(label+" acquires actual loaded terrain",table.snapshot()!=null&&table.snapshot().knownColumns()>0);
        if(table.snapshot()==null)throw new IllegalStateException(label+" has no terrain to test capture persistence");
        rig.cachedKey=table.snapshot().key();rig.cachedRevision=table.snapshot().revision;
    }
    private static boolean cached(Rig rig,HololithTableEntity table){
        return rig.cachedKey==null?table.snapshot()==null:table.snapshot()!=null&&rig.cachedKey.equals(table.snapshot().key())&&rig.cachedRevision==table.snapshot().revision;
    }
    private MechanicalBearingBlockEntity controller(ServerPlayer p){return (MechanicalBearingBlockEntity)p.serverLevel().getBlockEntity(BEARING);}
    private static long tableCount(AbstractContraptionEntity entity){return entity.getContraption().getBlocks().values().stream().filter(b->b.state().is(Hololith.TABLE.get())).count();}
    private void verifyServer(ServerPlayer p,Rig rig,String label){
        var table=ContraptionSurveys.table(rig.entity,rig.table);var sensor=ContraptionSurveys.sensor(rig.entity,rig.sensor);
        check(label+" retains table and sensor identities",table!=null&&sensor!=null&&rig.tableId.equals(table.tableId())&&rig.sensorId.equals(sensor.sensorId()));
        if(table==null||sensor==null)return;
        var world=rig.entity.toGlobalVector(Vec3.atCenterOf(rig.sensor),1);var target=SurveyStore.get(p.server).sensor(rig.sensorId);
        check(label+" sensor follows engine world position",target!=null&&target.online()&&target.x()==(int)Math.floor(world.x)&&target.z()==(int)Math.floor(world.z));
        check(label+" captured table has no source network speed or projection",!table.hasSource()&&!table.hasNetwork()&&Math.abs(table.getSpeed())<.01&&!table.isProjecting());
        check(label+" captured table preserves completed cache without surveying",cached(rig,table));
        check(label+" captured table preserves settings and sensor link",table.span()==rig.expectedSpan&&table.rotation()==rig.expectedRotation&&rig.sensorId.equals(table.sensorId()));
        var observer=(HololithTableEntity)p.serverLevel().getBlockEntity(rig.observer);
        check(label+" stationary observer uses a real source and network",powered(observer));
        check(label+" powered stationary table follows moving sensor",observer!=null&&observer.snapshot()!=null&&Math.abs(observer.snapshot().centerX-Math.floorDiv((int)Math.floor(world.x),4)*4)<=4&&Math.abs(observer.snapshot().centerZ-Math.floorDiv((int)Math.floor(world.z),4)*4)<=4);
        boolean markers=observer!=null&&observer.markers().size()==4;
        if(observer!=null)for(int i=0;i<4;i++){var id=rig.markerIds.get(i);var expected=rig.entity.toGlobalVector(Vec3.atCenterOf(rig.markers.get(i)),1);var point=observer.markers().stream().filter(m->m.id().equals(id)).findFirst();markers&=point.isPresent()&&Math.abs(point.get().x()-expected.x)<.01&&Math.abs(point.get().z()-expected.z)<.01;}
        check(label+" powered observer markers match all four engine transforms",markers);
        check(label+" captured block data preserves the expected survey cache",rig.entity.getContraption().getBlocks().get(rig.table).nbt().contains("Survey")== (rig.cachedKey!=null));
    }
    private void verifyRestored(ServerPlayer p,Rig rig,BlockPos center,String label){
        var table=p.serverLevel().getBlockEntity(center);check(label+" restores identity settings link and expected cache",table instanceof HololithTableEntity t&&t.tableId().equals(rig.tableId)&&rig.sensorId.equals(t.sensorId())&&t.span()==rig.expectedSpan&&t.rotation()==rig.expectedRotation&&cached(rig,t));
        boolean parts=true;for(int part=0;part<9;part++){var state=p.serverLevel().getBlockState(center.offset(part%3-1,0,part/3-1));parts&=state.is(Hololith.TABLE.get())&&state.getValue(HololithTableBlock.PART)==part;}check(label+" restores intact nine-tile footprint",parts);
    }
    private AbstractContraptionEntity clientEntity(){return Minecraft.getInstance().level.getEntity(active.entityId) instanceof AbstractContraptionEntity entity?entity:null;}
    private HololithTableEntity clientTable(){var entity=clientEntity();if(entity==null)return null;var be=entity.getContraption().getOrCreateClientContraptionLazy().getBlockEntity(active.table);return be instanceof HololithTableEntity table?table:null;}
    private HololithTableEntity clientObserver(){var level=Minecraft.getInstance().level;return level!=null&&level.getBlockEntity(active.observer) instanceof HololithTableEntity table?table:null;}
    private void sampleMovingMarkers(){
        var table=clientObserver();if(table==null||!table.isProjecting()||table.snapshot()==null)return;
        for(var marker:table.markers()){
            if(marker.anchor()==null||!marker.anchor().mounted())continue;
            Vec3 before=MarkerPositionResolver.position(table,marker,0),after=MarkerPositionResolver.position(table,marker,1);
            Vec3 middle=MarkerPositionResolver.position(table,marker,.5f);
            if(before.distanceTo(after)>.001&&middle.distanceTo(before.add(after).scale(.5))<.02){interpolatedMarkerFrames++;break;}
        }
    }
    private void verifyClient(String label)throws Exception{
        var captured=clientTable();
        check(label+" virtual client table remains dark without captured kinetic power",captured!=null&&!captured.isProjecting()&&Math.abs(captured.getSpeed())<.01);
        check(label+" virtual client table preserves identity settings link and expected cache",captured!=null&&active.tableId.equals(captured.tableId())&&active.sensorId.equals(captured.sensorId())&&captured.span()==active.expectedSpan&&captured.rotation()==active.expectedRotation&&cached(active,captured));
        check(label+" dark virtual table never builds a terrain GPU mesh",mesh(captured)==null);
        var table=clientObserver();check(label+" powered stationary client receives survey and four markers",table!=null&&table.isProjecting()&&table.snapshot()!=null&&table.markers().size()==4);
        if(table!=null&&!table.markers().isEmpty()){
            check(label+" marker sources identify this tracked contraption",table.markers().stream().allMatch(point->point.anchor()!=null&&point.anchor().mounted()&&point.anchor().entityUuid().equals(clientEntity().getUUID())));
            var point=table.markers().getFirst();var anchor=point.anchor();
            if(anchor!=null&&anchor.mounted()){
                var invalid=new MarkerPoint(point.id(),point.x(),point.z(),point.color(),new MarkerAnchor(anchor.pos(),anchor.entityId(),UUID.randomUUID(),anchor.shipUuid()));
                Vec3 fallback=MarkerPositionResolver.position(table,invalid,.5f);
                check(label+" stale contraption identity uses authoritative marker fallback",fallback.x==point.x()&&fallback.z==point.z());
            }
        }
        check(label+" powered stationary observer builds renderer GPU mesh",mesh(table)!=null);
    }
    private static Object mesh(HololithTableEntity table)throws ReflectiveOperationException{
        if(table==null)return null;
        var field=HololithRenderer.class.getDeclaredField("CACHE");field.setAccessible(true);var entry=((Map<?,?>)field.get(null)).get(table);
        if(entry==null)return null;var value=entry.getClass().getDeclaredField("mesh");value.setAccessible(true);return value.get(entry);
    }
    private void interact(BlockPos pos){var entity=clientEntity();if(entity==null)throw new IllegalStateException("Client contraption missing");CatnipServices.NETWORK.sendToServer(new ContraptionInteractionPacket(entity,InteractionHand.MAIN_HAND,pos,Direction.UP));}
    private void hand(ServerPlayer p,ItemStack stack){p.setItemInHand(InteractionHand.MAIN_HAND,stack);p.inventoryMenu.broadcastChanges();}
    private void near(ServerPlayer p,Vec3 target){p.connection.teleport(target.x,target.y+1,target.z+3,180,35);p.getAbilities().flying=true;p.onUpdateAbilities();}
    private void camera(ServerPlayer p){
        // The working display is the genuinely powered stationary observer. The
        // captured instrument remains intentionally dark on ordinary contraptions.
        Vec3 focus=Vec3.atCenterOf(active.observer);
        Vec3 eye=focus.add(5,4,6),delta=focus.add(0,1.3,0).subtract(eye);
        p.connection.teleport(eye.x,eye.y-p.getEyeHeight(),eye.z,(float)Math.toDegrees(Math.atan2(delta.z,delta.x))-90,(float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z))));p.getAbilities().flying=true;p.onUpdateAbilities();
    }
    private void shot(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,"hololith-"+name,mc.getMainRenderTarget(),c->System.out.println(c.getString()));}
    private void writeResume()throws Exception{
        var saved=new LinkedHashMap<String,Object>();saved.put("tableId",expectedTable.toString());saved.put("sensorId",expectedSensor.toString());saved.put("markers",expectedMarkers.stream().map(UUID::toString).toList());saved.put("cachedKey",bearing.cachedKey);saved.put("cachedRevision",bearing.cachedRevision);
        Files.createDirectories(Path.of("validation"));Files.writeString(Path.of("validation/contraption-fixture.json"),JSON.toJson(saved));
    }
    private void finish(){
        if(done)return;done=true;driveTicks=0;var report=new LinkedHashMap<String,Object>();report.put("mode",resume?"resume":"fresh");report.put("phase",phase);report.put("checks",checks);report.put("total",checks.size());report.put("failures",checks.stream().filter(c->Boolean.FALSE.equals(c.get("passed"))).count());
        report.put("timestamp",java.time.Instant.now().toString());
        report.put("version",net.neoforged.fml.ModList.get().getModContainerById("hololith").orElseThrow().getModInfo().getVersion().toString());
        report.put("profile","contraptions");
        report.put("loadedMods",net.neoforged.fml.ModList.get().getMods().stream().map(m->m.getModId()).sorted().toList());
        report.put("scope","Real Create bearing/cart assembly, controlled quarter-turns, test-driven minecart motion, client interaction packets and separate JVM persistence. Before assembly, both captured instruments and stationary observers receive actual Create motor/shaft power. Captured tables, including their captured motor and shaft, must stay dark with no kinetic network while sensors and markers continue feeding the powered stationary observers. Disassembly and genuine source reconnection restore projection; mounted retargeting intentionally invalidates the old survey until power returns. Creative Motors are test supplies. No powered-flight, train, natural chunk-unload or player glue acceptance.");
        try{Files.createDirectories(Path.of("validation"));Files.writeString(Path.of("validation/hololith-"+(resume?"resume":"fresh")+".json"),JSON.toJson(report));}catch(Exception e){e.printStackTrace();}
        var mc=Minecraft.getInstance();mc.options.keyShift.setDown(false);mc.execute(mc::stop);
    }
    private static final class Rig {
        final BlockPos anchor,table,sensor;
        final List<BlockPos> markers;
        final List<UUID> markerIds=new ArrayList<>();
        BlockPos observer;UUID tableId,sensorId;AbstractContraptionEntity entity;int entityId,expectedSpan=32,expectedRotation;SurveyKey cachedKey;long cachedRevision;
        Rig(BlockPos anchor,int deck){this.anchor=anchor;table=TABLE.above(deck);sensor=SENSOR.above(deck);markers=MARKERS.stream().map(p->p.above(deck)).toList();observer=anchor.offset(-10,deck+3,0);}
        BlockPos tableWorld(){return anchor.offset(table);}
    }
}
