package dev.hololith.probe;

import com.google.gson.GsonBuilder;
import dev.hololith.*;
import dev.hololith.client.HololithControls;
import dev.hololith.compat.ShipCoordinates;
import dev.hololith.survey.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value="hololith_probe",dist=Dist.CLIENT)
public final class HololithProbe {
    private final long started=System.nanoTime();
    private final List<Map<String,Object>> checks=new CopyOnWriteArrayList<>();
    private CompletableFuture<Void> work=CompletableFuture.completedFuture(null);
    private final boolean resume=Boolean.getBoolean("hololith.probe.resume");
    private final boolean minimal=Boolean.getBoolean("hololith.probe.minimal");
    private final boolean contraptions=Boolean.getBoolean("hololith.probe.contraptions");
    private final boolean airship=Boolean.getBoolean("hololith.probe.airship");
    private PublicAirshipFixture airshipFixture;
    private ContraptionFixture contraptionFixture;
    private final boolean showcase=Boolean.getBoolean("hololith.showcase");
    private ShowcaseFixture showcaseFixture;
    private boolean worldOpened;
    private List<String> loadedMods=List.of();
    private int ticks,phase,phaseTick,cx,cz;
    private BlockPos table,sensor,edited;
    private UUID playerId,sensorId;
    private long revision;
    private boolean finished;
    private UUID movingId;
    private volatile int movingTick=-1;
    private int movingMeshFrames;
    private boolean movingMeshSeen,movingMeshLost;
    private final Set<Integer> movingCenters=new HashSet<>();
    private boolean movingAcquired,movingLost,movingMarkersLost;
    private ShipFixture shipFixture;
    private BlockPos originalTable,shipSensor;
    private UUID shipSensorId;
    private final ReferenceChecks references=new ReferenceChecks(this::check);
    private BlockPos interactiveReference;
    private CompoundTag removedReference;
    private net.minecraft.world.level.block.state.BlockState removedReferenceState;
    private final List<BlockPos> darkRoom=new ArrayList<>();
    private Object intensityMesh;
    private QolChecks qolChecks;
    public HololithProbe(){
        try {
            Path game=Path.of(System.getProperty("user.dir")).toRealPath();
            String allowed=System.getProperty("hololith.probe.allowedRoot", "");
            if(allowed.isBlank()||!game.equals(Path.of(allowed).toRealPath())||!Files.exists(game.resolve(".hololith-disposable")))throw new IllegalStateException("Probe requires an explicitly allowed disposable directory and .hololith-disposable marker");
            Path file=game.resolve("saves/hololith-validation/level.dat");
            if(airship){
                if(Files.exists(game.resolve("saves/hololith-airship")))throw new IllegalStateException("Fresh airship probe refuses an existing world; archive it first");
            }else if(minimal&&!resume){
                if(Files.exists(file))throw new IllegalStateException("Fresh minimal probe refuses an existing world; archive it first");
            }else{
                var nbt=NbtIo.readCompressed(file,NbtAccounter.unlimitedHeap());
                if(nbt.getCompound("Data").getInt("DataVersion")>3955)throw new IllegalStateException("Refuse world downgrade");
                nbt.getCompound("Data").putBoolean("confirmedExperimentalSettings",true);NbtIo.writeCompressed(nbt,file);
            }
        }catch(Exception e){throw new RuntimeException(e);}
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderFrameEvent.Post event)->{
            if(movingTick<10||table==null||Minecraft.getInstance().level==null)return;
            try{Object mesh=clientMesh();if(mesh!=null){movingMeshSeen=true;movingMeshFrames++;}else if(movingMeshSeen)movingMeshLost=true;}
            catch(Exception error){movingMeshLost=true;}
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Pre e)->{
            if(movingTick<0)return;
            var level=e.getServer().overworld();var t=(HololithTableEntity)level.getBlockEntity(table);
            SurveyStore.get(e.getServer()).heartbeat(movingId,level.dimension().location().toString(),cx+4*((movingTick/5)%3),cz);
            if(t.snapshot()!=null){movingAcquired=true;movingCenters.add(t.snapshot().centerX);}else if(movingAcquired)movingLost=true;
            if(movingTick>=20&&t.snapshot()!=null&&t.markers().size()!=2)movingMarkersLost=true;
            if(++movingTick==160){
                check("moving target adopts completed surveys",movingCenters.size()>=3&&movingCenters.stream().mapToInt(Integer::intValue).max().orElse(cx)>=cx+8);
                check("moving updates retain last projection",movingAcquired&&!movingLost);movingTick=-1;
                check("rapid survey updates keep table-linked marker frame current",!movingMarkersLost);
            }
        });
    }
    private void check(String text,boolean passed){checks.add(Map.of("test",text,"passed",passed));System.out.println("HOLOLITH CHECK "+passed+": "+text);}
    private void server(Consumer<ServerPlayer> action){var s=Minecraft.getInstance().getSingleplayerServer();work=CompletableFuture.runAsync(()->action.accept(Objects.requireNonNull(s.getPlayerList().getPlayer(playerId))),s);}
    private void next(){phase++;phaseTick=ticks;}
    private void use(BlockPos pos){var mc=Minecraft.getInstance();mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos).add(0,.5,0),Direction.UP,pos,false));}
    private void screenshot(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),c->System.out.println(c.getString()));}
    private HololithTableEntity clientTable(){return (HololithTableEntity)Minecraft.getInstance().level.getBlockEntity(table);}
    private Object clientMesh()throws Exception{
        var field=dev.hololith.client.HololithRenderer.class.getDeclaredField("CACHE");field.setAccessible(true);var entry=((Map<?,?>)field.get(null)).get(clientTable());
        if(entry==null)return null;var mesh=entry.getClass().getDeclaredField("mesh");mesh.setAccessible(true);return mesh.get(entry);
    }
    private void near(ServerPlayer p,BlockPos pos){p.connection.teleport(pos.getX()+.5,pos.getY()+1,pos.getZ()+2,180,40);p.getAbilities().flying=true;p.onUpdateAbilities();}
    private void hand(ServerPlayer p,ItemStack stack){p.setItemInHand(InteractionHand.MAIN_HAND,stack);p.inventoryMenu.broadcastChanges();}
    private void restoreReference(ServerPlayer p){
        var at=ReferenceChecks.buried(cx,cz).getFirst();p.serverLevel().setBlockAndUpdate(at,removedReferenceState);
        var restored=BlockEntity.loadStatic(at,removedReferenceState,removedReference,p.registryAccess());p.serverLevel().setBlockEntity(Objects.requireNonNull(restored));restored.onLoad();
    }
    private void camera(ServerPlayer p){
        Vec3 base=ShipCoordinates.toWorld(p.serverLevel(),table).orElseThrow();
        double x=base.x-.5+4.3,y=base.y-.5+3.0,z=base.z-.5+5.1;
        Vec3 d=base.add(0,1.3,0).subtract(new Vec3(x,y+p.getEyeHeight(),z));
        p.connection.teleport(x,y,z,(float)Math.toDegrees(Math.atan2(d.z,d.x))-90,(float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))));
        p.getAbilities().flying=true;p.onUpdateAbilities();
    }
    private void openMinimalWorld(Minecraft mc){
        if(worldOpened||mc.level!=null||!(mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen)||mc.getOverlay()!=null)return;
        worldOpened=true;
        var flows=mc.createWorldOpenFlows();
        if(resume){
            flows.openWorld("hololith-validation",()->{check("minimal saved world opens",false);finish();});
        }else{
            flows.createFreshLevel("hololith-validation",
                new net.minecraft.world.level.LevelSettings("Hololith minimal validation",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,
                    new net.minecraft.world.level.GameRules(),net.minecraft.world.level.WorldDataConfiguration.DEFAULT),
                new net.minecraft.world.level.levelgen.WorldOptions(121121L,false,false),
                registries->registries.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                    .getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions(),
                new net.minecraft.client.gui.screens.TitleScreen());
        }
    }
    private void tick(ClientTickEvent.Post event){
        if(finished)return;var mc=Minecraft.getInstance();
        if(!contraptions&&!showcase&&!airship&&System.nanoTime()-started>360_000_000_000L){check("probe completed before timeout",false);finish();return;}
        try {
            if(airship){if(airshipFixture==null)airshipFixture=new PublicAirshipFixture();airshipFixture.tick();return;}
            if(minimal)openMinimalWorld(mc);
            if(showcase){if(showcaseFixture==null)showcaseFixture=new ShowcaseFixture();showcaseFixture.tick();return;}
            if(contraptions){if(contraptionFixture==null)contraptionFixture=new ContraptionFixture(resume);contraptionFixture.tick();return;}
            if(mc.level==null||mc.player==null)return;ticks++;
            if(work.isCompletedExceptionally())work.join();if(!work.isDone()||ticks<80)return;
            if(phase==56){if(qolChecks.tick())finish();return;}
            if(ticks-phaseTick<25)return;
            switch(phase){
                case 0->{
                    mc.options.fov().set(55);playerId=mc.player.getUUID();check(minimal?"minimal Create client and integrated server loaded":"full pack client and integrated server loaded",mc.getSingleplayerServer()!=null);
                    loadedMods=net.neoforged.fml.ModList.get().getMods().stream().map(mod->mod.getModId()).sorted().toList();
                    if(minimal){
                        check("minimal run contains only Create, Hololith and their bundled dependencies",Set.of("minecraft","neoforge","create","flywheel","ponder","registrate","hololith","hololith_probe").containsAll(loadedMods));
                        check("optional Sable integration is absent",!net.neoforged.fml.ModList.get().isLoaded("sable"));
                    }
                    server(p->{
                        var level=p.serverLevel();var spawn=level.getSharedSpawnPos();cx=Math.floorDiv(spawn.getX()+64,4)*4;cz=Math.floorDiv(spawn.getZ()+64,4)*4;
                        table=new BlockPos(cx+23,225,cz);sensor=new BlockPos(cx,226,cz);edited=new BlockPos(cx+3,250,cz+3);
                        if(resume){
                            var t=(HololithTableEntity)level.getBlockEntity(table);check("table survives real process restart",t!=null);check("table cached survey survives real process restart",t!=null&&t.snapshot()!=null&&t.snapshot().knownColumns()>0);
                            var s=(SurveySensorEntity)level.getBlockEntity(sensor);check("sensor identity persisted",s!=null&&t!=null&&s.sensorId().equals(t.sensorId()));
                            check("shared region cache persisted",t!=null&&t.snapshot()!=null&&SurveyStore.get(p.server).find(t.snapshot().key())!=null);
                            references.verifyRestart(p,table,cx,cz);
                            p.setGameMode(GameType.CREATIVE);camera(p);return;
                        }
                        buildTerrain(p);
                        for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)level.setBlockAndUpdate(table.offset(x,-1,z),Blocks.STONE_BRICKS.defaultBlockState());
                        level.setBlockAndUpdate(sensor,Hololith.SENSOR.get().defaultBlockState());
                        p.setGameMode(GameType.CREATIVE);p.getInventory().clearContent();p.getInventory().selected=0;p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));p.setItemInHand(InteractionHand.MAIN_HAND,Hololith.TABLE_ITEM.toStack());p.inventoryMenu.broadcastChanges();
                        p.connection.teleport(table.getX()+.5,table.getY()+1,table.getZ()+3,180,35);
                        check(minimal?"ordinary coordinate fallback works without optional ship API":"stationary Sable transform returns actual position",ShipCoordinates.toWorld(level,sensor).orElseThrow().distanceToSqr(Vec3.atCenterOf(sensor))<.0001);
                    });if(resume){phase=40;phaseTick=ticks;}else next();
                }
                case 1->{use(table.below());next();}
                case 2->{
                    server(p->{var level=p.serverLevel();int parts=0,entities=0;for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){var at=table.offset(x,0,z);if(level.getBlockState(at).is(Hololith.TABLE.get()))parts++;if(level.getBlockEntity(at) instanceof HololithTableEntity)entities++;}
                        check("client item placement creates nine table tiles",parts==9);check("exactly one table block entity",entities==1);
                        var t=(HololithTableEntity)level.getBlockEntity(table);t.configure(32,0,0,1,0,level.dimension().location().toString(),cx,cz);
                        p.setItemInHand(InteractionHand.MAIN_HAND,Hololith.CARD.toStack());p.inventoryMenu.broadcastChanges();near(p,sensor);
                    });next();
                }
                case 3->{use(sensor);next();}
                case 4->{
                    check("card records sensor through client item use",mc.player.getMainHandItem().getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().hasUUID("SensorId"));
                    server(p->{sensorId=((SurveySensorEntity)p.serverLevel().getBlockEntity(sensor)).sensorId();p.connection.teleport(table.getX()+.5,table.getY()+1,table.getZ()+3,180,35);});next();
                }
                case 5->{use(table);next();}
                case 6->{
                    if(ticks-phaseTick<180)return;
                    var t=clientTable();check("card links table by sensor UUID",t!=null&&sensorId.equals(t.sensorId()));check("terrain snapshot synchronized to client",t!=null&&t.snapshot()!=null);
                    server(p->{var b=(HololithTableEntity)p.serverLevel().getBlockEntity(table);var s=Objects.requireNonNull(b.snapshot());
                        check("32 area contains 1024 known columns",s.size==32&&s.knownColumns()==1024);int water=0,leaves=0,min=4096,max=-4096;for(int i=0;i<s.flags.length;i++){if((s.flags[i]&2)!=0)water++;if((s.flags[i]&4)!=0)leaves++;min=Math.min(min,s.surfaceY[i]);max=Math.max(max,s.surfaceY[i]);}
                        check("real height relief",max-min>=8);check("water distinguished",water>0);check("raised canopies distinguished",leaves>0);
                        var decoded=SurveySnapshot.load(s.save(),p.registryAccess());check("state palette and snapshot round trip",decoded!=null&&decoded.sameTerrain(s));
                        var restored=(HololithTableEntity)BlockEntity.loadStatic(table,b.getBlockState(),b.saveWithFullMetadata(p.registryAccess()),p.registryAccess());check("table NBT retains link and survey",restored!=null&&sensorId.equals(restored.sensorId())&&restored.snapshot().sameTerrain(s));
                        references.setup(p,table,sensorId,cx,cz);
                        camera(p);p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);p.inventoryMenu.broadcastChanges();
                    });mc.options.hideGui=true;next();
                }
                case 7->{RenderComparison.run(clientTable().snapshot(),this::check);check("four reference markers reach actual client",clientTable().markers().size()==4);screenshot("hololith-terrain.png");mc.options.hideGui=false;server(p->{references.verifyBuried(p,table,cx,cz);p.connection.teleport(table.getX()+.5,table.getY()+1,table.getZ()+3,180,35);});next();}
                case 8->{use(table);next();}
                case 9->{
                    check("actual instrument panel opens over server packet",mc.screen instanceof HololithControls);screenshot("hololith-controls.png");var screen=Objects.requireNonNull(mc.screen);
                    screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().startsWith("Area:")).map(c->(Button)c).findFirst().orElseThrow().onPress();
                    screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().startsWith("Rotation:")).map(c->(Button)c).findFirst().orElseThrow().onPress();
                    screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().startsWith("Apply")).map(c->(Button)c).findFirst().orElseThrow().onPress();next();
                }
                case 10->{
                    if(ticks-phaseTick<180)return;
                    RenderComparison.run(clientTable().snapshot(),this::check);
                    server(p->{var t=(HololithTableEntity)p.serverLevel().getBlockEntity(table);check("panel changes authoritative area and rotation",t.span()==64&&t.rotation()==1);check("64 area full-resolution survey",t.snapshot()!=null&&t.snapshot().span==64&&t.snapshot().size==64);revision=t.snapshot().revision;p.serverLevel().setBlockAndUpdate(edited,Blocks.GOLD_BLOCK.defaultBlockState());});next();
                }
                case 11->{
                    if(ticks-phaseTick<180)return;
                    server(p->{var t=(HololithTableEntity)p.serverLevel().getBlockEntity(table);var s=t.snapshot();int index=(edited.getZ()-s.centerZ+s.span/2)*s.size+edited.getX()-s.centerX+s.span/2;
                        check("terrain edit refreshes height material and revision",s.revision>revision&&s.surfaceY[index]==250&&s.surfaceStates[index].is(Blocks.GOLD_BLOCK));p.serverLevel().removeBlock(edited,false);
                        var far=SurveyKey.at(p.serverLevel().dimension().location().toString(),20_000_000,20_000_000,128);check("remote region starts unloaded",p.serverLevel().getChunkSource().getChunkNow(1_250_000,1_250_000)==null);SurveyManager.get(p.server).request(far);
                        t.configure(128,1,0,1,1,t.targetDimension(),cx,cz);
                    });next();
                }
                case 12->{
                    if(ticks-phaseTick<180)return;
                    server(p->{var t=(HololithTableEntity)p.serverLevel().getBlockEntity(table);check("128 area caps grid to 64 squared",t.snapshot()!=null&&t.snapshot().span==128&&t.snapshot().size==64);
                        check("survey does not force-load remote chunks",p.serverLevel().getChunkSource().getChunkNow(1_250_000,1_250_000)==null);
                        var far=SurveyKey.at(p.serverLevel().dimension().location().toString(),20_000_000,20_000_000,128);check("unloaded target has no invented cache",SurveyStore.get(p.server).find(far)==null);
                        t.configure(32,0,0,1,1,t.targetDimension(),cx,cz);camera(p);p.server.saveEverything(false,true,true);
                    });next();
                }
                case 13->{
                    if(ticks-phaseTick<140)return;
                    server(p->{movingId=UUID.randomUUID();CompoundTag card=new CompoundTag();card.putString("Dimension",p.serverLevel().dimension().location().toString());card.putInt("X",cx);card.putInt("Z",cz);card.putUUID("SensorId",movingId);((HololithTableEntity)p.serverLevel().getBlockEntity(table)).bind(card);movingTick=0;});next();
                }
                case 14->{
                    if(ticks-phaseTick<190)return;
                    check("rapid survey changes retain a rendered GPU mesh",movingMeshSeen&&movingMeshFrames>20&&!movingMeshLost);
                    server(p->{CompoundTag card=new CompoundTag();card.putString("Dimension",p.serverLevel().dimension().location().toString());card.putInt("X",cx);card.putInt("Z",cz);card.putUUID("SensorId",sensorId);((HololithTableEntity)p.serverLevel().getBlockEntity(table)).bind(card);});next();
                }
                case 15->{
                    if(ticks-phaseTick<140)return;
                    if(minimal){phase=19;phaseTick=ticks;return;}
                    server(p->{try{
                        var level=p.serverLevel();originalTable=table;shipSensor=table.below();level.setBlockAndUpdate(shipSensor,Hololith.SENSOR.get().defaultBlockState());shipSensorId=((SurveySensorEntity)level.getBlockEntity(shipSensor)).sensorId();
                        CompoundTag card=new CompoundTag();card.putUUID("SensorId",shipSensorId);card.putString("Dimension",level.dimension().location().toString());card.putInt("X",shipSensor.getX());card.putInt("Z",shipSensor.getZ());((HololithTableEntity)level.getBlockEntity(table)).bind(card);
                        var blocks=new ArrayList<BlockPos>();for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)blocks.add(table.offset(x,0,z));blocks.add(shipSensor);
                        references.addShipReferences(level,table,shipSensorId,blocks);
                        shipFixture=new ShipFixture(level,table,blocks);table=shipFixture.inPlot(originalTable);
                        check("Sable assembly retains all nine table tiles",blocks.subList(0,9).stream().allMatch(b->level.getBlockState(shipFixture.inPlot(b)).is(Hololith.TABLE.get())));
                        check("sensor UUID survives engine assembly",((SurveySensorEntity)level.getBlockEntity(shipFixture.inPlot(shipSensor))).sensorId().equals(shipSensorId));
                        check("assembled sensor maps to world position",ShipCoordinates.toWorld(level,shipFixture.inPlot(shipSensor)).orElseThrow().distanceToSqr(Vec3.atCenterOf(shipSensor))<.001);camera(p);
                    }catch(Exception e){throw new RuntimeException(e);}});next();
                }
                case 16->{
                    if(ticks-phaseTick<100)return;check("assembled snapshot reaches client sublevel",clientTable()!=null&&clientTable().snapshot()!=null);
                    check("assembled reference frame reaches ship client",clientTable().markers().stream().filter(m->references.shipIds.containsKey(m.id())).count()==4);
                    server(p->{try{var t=(HololithTableEntity)p.serverLevel().getBlockEntity(table);check("assembled table and sensor continue ticking",t.snapshot()!=null&&t.snapshot().centerX==Math.floorDiv(shipSensor.getX(),4)*4);references.verifyShip(p,table,shipFixture,0,0,0,"assembled");shipFixture.move(8,0,-4,0);camera(p);}catch(Exception e){throw new RuntimeException(e);}});next();
                }
                case 17->{
                    if(ticks-phaseTick<100)return;check("moved table retains synchronized survey",clientTable()!=null&&clientTable().snapshot()!=null);screenshot("hololith-ship.png");
                    server(p->{try{
                        var at=ShipCoordinates.toWorld(p.serverLevel(),shipFixture.inPlot(shipSensor)).orElseThrow();check("moving Sable sensor resolves translated world position",at.distanceToSqr(Vec3.atCenterOf(shipSensor).add(8,0,-4))<.001);
                        var t=(HololithTableEntity)p.serverLevel().getBlockEntity(table);check("moving ship survey follows translated ground",t.snapshot()!=null&&t.snapshot().centerX==Math.floorDiv(shipSensor.getX()+8,4)*4&&t.snapshot().centerZ==Math.floorDiv(shipSensor.getZ()-4,4)*4);
                        references.verifyShip(p,table,shipFixture,8,-4,0,"translated");
                        final double yaw=Math.PI/2;shipFixture.move(8,0,-4,yaw);
                        var delta=new org.joml.Quaterniond(shipFixture.startRotation).rotateY(yaw).mul(new org.joml.Quaterniond(shipFixture.startRotation).invert());
                        for(var original:List.of(shipSensor,originalTable.east())){
                            var expected=new org.joml.Vector3d(original.getX()+.5,original.getY()+.5,original.getZ()+.5).sub(shipFixture.startPosition);delta.transform(expected).add(shipFixture.startPosition).add(8,0,-4);
                            var actual=ShipCoordinates.toWorld(p.serverLevel(),shipFixture.inPlot(original)).orElseThrow();
                            check(original.equals(shipSensor)?"yawed sensor resolves world position":"off-axis table tile rotates around ship COM",actual.distanceToSqr(new Vec3(expected.x,expected.y,expected.z))<.001);
                        }camera(p);
                    }catch(Exception e){throw new RuntimeException(e);}});next();
                }
                case 18->{
                    if(ticks-phaseTick<100)return;check("yawed ship retains client projection",clientTable()!=null&&clientTable().snapshot()!=null);screenshot("hololith-ship-yaw.png");
                    check("yawed reference frame reaches ship client",clientTable().markers().stream().filter(m->references.shipIds.containsKey(m.id())).count()==4);
                    server(p->{try{
                        references.verifyShip(p,table,shipFixture,8,-4,Math.PI/2,"yawed");
                        shipFixture.returnBlocks();shipFixture=null;table=originalTable;check("engine disassembly restores table and survey",p.serverLevel().getBlockEntity(table) instanceof HololithTableEntity restored&&restored.snapshot()!=null);
                        CompoundTag card=new CompoundTag();card.putUUID("SensorId",sensorId);card.putString("Dimension",p.serverLevel().dimension().location().toString());card.putInt("X",cx);card.putInt("Z",cz);((HololithTableEntity)p.serverLevel().getBlockEntity(table)).bind(card);camera(p);
                    }catch(Exception e){throw new RuntimeException(e);}});next();
                }
                case 19->{
                    if(ticks-phaseTick<140)return;
                    server(p->{var beacon=sensor.east(8);p.serverLevel().setBlockAndUpdate(beacon,Hololith.BEACON.get().defaultBlockState());p.setItemInHand(InteractionHand.MAIN_HAND,Hololith.CARD.toStack());p.inventoryMenu.broadcastChanges();p.connection.teleport(beacon.getX()+.5,beacon.getY()+1,beacon.getZ()+2,180,40);});next();
                }
                case 20->{use(sensor.east(8));next();}
                case 21->{check("card records remote beacon",mc.player.getMainHandItem().getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().hasUUID("SensorId"));server(p->p.connection.teleport(table.getX()+.5,table.getY()+1,table.getZ()+3,180,35));next();}
                case 22->{use(table);next();}
                case 23->{
                    if(ticks-phaseTick<140)return;
                    server(p->{var t=(HololithTableEntity)p.serverLevel().getBlockEntity(table);var beacon=(SurveySensorEntity)p.serverLevel().getBlockEntity(sensor.east(8));check("remote beacon link acquires its terrain",beacon.sensorId().equals(t.sensorId())&&t.snapshot()!=null&&t.snapshot().centerX==cx+8);t.configure(32,0,0,1,2,p.serverLevel().dimension().location().toString(),cx,cz);});next();
                }
                case 24->{
                    if(ticks-phaseTick<140)return;
                    server(p->{var t=(HololithTableEntity)p.serverLevel().getBlockEntity(table);check("coordinate mode acquires selected region",t.mode()==2&&t.snapshot()!=null&&t.snapshot().centerX==cx&&t.snapshot().centerZ==cz);CompoundTag card=new CompoundTag();card.putUUID("SensorId",sensorId);card.putString("Dimension",p.serverLevel().dimension().location().toString());card.putInt("X",cx);card.putInt("Z",cz);t.bind(card);p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);p.inventoryMenu.broadcastChanges();camera(p);});next();
                }
                case 25->{
                    if(ticks-phaseTick<140)return;
                    server(p->{interactiveReference=new BlockPos(cx+8,260,cz+8);p.serverLevel().setBlockAndUpdate(interactiveReference,Hololith.MARKER.get().defaultBlockState());for(int x=-1;x<=1;x++)for(int z=1;z<=3;z++)p.serverLevel().setBlockAndUpdate(interactiveReference.offset(x,-1,z),Blocks.STONE_BRICKS.defaultBlockState());hand(p,Hololith.CARD.toStack());near(p,sensor);});next();
                }
                case 26->{use(sensor);next();}
                case 27->{server(p->near(p,interactiveReference));next();}
                case 28->{use(interactiveReference);next();}
                case 29->{
                    check("sensor-card reference appears on client",clientTable().markers().size()==5);
                    server(p->{var marker=(ReferenceMarkerEntity)p.serverLevel().getBlockEntity(interactiveReference);check("actual card item links reference to sensor",sensorId.equals(marker.targetId())&&marker.targetKind()==1);hand(p,new ItemStack(Items.RED_DYE));});next();
                }
                case 30->{use(interactiveReference);next();}
                case 31->{
                    check("dye color reaches client block state",mc.level.getBlockState(interactiveReference).getValue(ReferenceMarkerBlock.COLOR)==DyeColor.RED);
                    mc.options.keyShift.setDown(true);
                    server(p->{check("actual dye item changes reference color",p.serverLevel().getBlockState(interactiveReference).getValue(ReferenceMarkerBlock.COLOR)==DyeColor.RED);hand(p,Hololith.CARD.toStack());near(p,table);});next();
                }
                case 32->{use(table);next();}
                case 33->{
                    var tag=mc.player.getMainHandItem().getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();check("sneak-card records table identity without stale sensor link",tag.hasUUID("TableId")&&!tag.hasUUID("SensorId"));
                    mc.options.keyShift.setDown(false);server(p->near(p,interactiveReference));next();
                }
                case 34->{use(interactiveReference);next();}
                case 35->{
                    server(p->{
                        var marker=(ReferenceMarkerEntity)p.serverLevel().getBlockEntity(interactiveReference);var t=(HololithTableEntity)p.serverLevel().getBlockEntity(table);
                        check("actual card relinks reference directly to table",marker.targetKind()==0&&t.tableId().equals(marker.targetId()));
                        var s=t.snapshot();int index=(interactiveReference.getZ()-s.centerZ+s.span/2)*s.size+interactiveReference.getX()-s.centerX+s.span/2;
                        check("high reference block does not lift sampled ground",s.surfaceY[index]<interactiveReference.getY()-10);
                        hand(p,net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("create:wrench")).getDefaultInstance());
                    });mc.options.keyShift.setDown(true);next();
                }
                case 36->{System.out.println("REFERENCE WRENCH client="+mc.player.position()+" shift="+mc.player.isShiftKeyDown()+" hand="+mc.player.getMainHandItem()+" reference="+interactiveReference);use(interactiveReference);next();}
                case 37->{
                    mc.options.keyShift.setDown(false);check("unlink sends empty removal to actual client",clientTable().markers().size()==4);
                    server(p->{
                        check("sneak-wrench clears reference target",((ReferenceMarkerEntity)p.serverLevel().getBlockEntity(interactiveReference)).targetId()==null);
                        var at=ReferenceChecks.buried(cx,cz).getFirst();var marker=(ReferenceMarkerEntity)p.serverLevel().getBlockEntity(at);
                        removedReference=marker.saveWithFullMetadata(p.registryAccess());removedReferenceState=marker.getBlockState();p.serverLevel().removeBlock(at,false);hand(p,ItemStack.EMPTY);camera(p);
                        for(int x=-1;x<=1;x++)for(int z=1;z<=3;z++)p.serverLevel().removeBlock(interactiveReference.offset(x,-1,z),false);
                    });next();
                }
                case 38->{if(ticks-phaseTick<60)return;check("destroyed reference disappears from projection",clientTable().markers().size()==3);server(this::restoreReference);next();}
                case 39->{if(ticks-phaseTick<60)return;check("reference NBT restores its link and color",clientTable().markers().size()==4);phase=50;phaseTick=ticks;}
                case 40->{if(ticks-phaseTick<100)return;check("persisted terrain synced after reconnect",clientTable()!=null&&clientTable().snapshot()!=null);screenshot("hololith-restart.png");qolChecks=new QolChecks(table,this::check);phase=56;phaseTick=ticks;}
                case 50->{server(p->p.serverLevel().removeBlockEntity(ReferenceChecks.buried(cx,cz).getFirst()));next();}
                case 51->{if(ticks-phaseTick<40)return;check("unavailable reference has no ghost marker",clientTable().markers().size()==3);server(this::restoreReference);next();}
                case 52->{
                    if(ticks-phaseTick<40)return;check("reference returns after block entity reload",clientTable().markers().size()==4);mc.options.hideGui=true;
                    server(p->{for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++)for(int y=0;y<=7;y++)if(Math.abs(x)==6||Math.abs(z)==6||y==7){var at=table.offset(x,y,z);if(p.serverLevel().getBlockState(at).isAir()){p.serverLevel().setBlockAndUpdate(at,Blocks.STONE_BRICKS.defaultBlockState());darkRoom.add(at);}}p.serverLevel().setDayTime(18000);camera(p);});next();
                }
                case 53->{if(ticks-phaseTick<100)return;screenshot("hololith-dark-interior.png");intensityMesh=clientMesh();dev.hololith.client.HololithClientConfig.HOLOGRAM_INTENSITY.set(1.6);next();}
                case 54->{
                    screenshot("hololith-intensity-high.png");check("intensity changes reuse the existing GPU terrain mesh",intensityMesh!=null&&intensityMesh==clientMesh());check("client intensity setting applies at runtime",Math.abs(dev.hololith.client.HololithClientConfig.intensity()-1.6)<.001);
                    dev.hololith.client.HololithClientConfig.HOLOGRAM_INTENSITY.set(1.0);server(p->{for(var at:darkRoom)p.serverLevel().removeBlock(at,false);p.serverLevel().setDayTime(6000);camera(p);});next();
                }
                case 55->{if(ticks-phaseTick<100)return;screenshot("hololith-markers-daylight.png");qolChecks=new QolChecks(table,this::check);phase=56;phaseTick=ticks;}
            }
        }catch(Throwable e){checks.add(Map.of("test","probe exception","passed",false,"error",e.toString()));e.printStackTrace();finish();}
    }
    private void buildTerrain(ServerPlayer player){
        var level=player.serverLevel();
        for(int x=-16;x<16;x++)for(int z=-16;z<16;z++){
            int hill=(int)(10*Math.exp(-((x+8)*(x+8)+(z+5)*(z+5))/65.0));int h=203+hill;boolean river=Math.abs(z-(int)(3*Math.sin(x*.25)))<=1;if(river)h=201;
            for(int y=196;y<=h;y++)level.setBlock(new BlockPos(cx+x,y,cz+z),(y==h?(river?Blocks.SAND:Blocks.GRASS_BLOCK):Blocks.STONE).defaultBlockState(),2);
            if(river)level.setBlock(new BlockPos(cx+x,202,cz+z),Blocks.WATER.defaultBlockState(),2);
        }
        for(int tx:new int[]{-11,-5,3,10}){
            int tz=10,h=level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,cx+tx,cz+tz);
            for(int y=0;y<5;y++)level.setBlock(new BlockPos(cx+tx,h+y,cz+tz),Blocks.OAK_LOG.defaultBlockState(),2);
            for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=3;y<=5;y++)if(Math.abs(x)+Math.abs(z)<4)level.setBlock(new BlockPos(cx+tx+x,h+y,cz+tz+z),Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true),2);
        }
        for(int x=5;x<=11;x++)for(int z=-12;z<=-7;z++)for(int y=204;y<=208;y++)if(x==5||x==11||z==-12||z==-7||y==208)level.setBlock(new BlockPos(cx+x,y,cz+z),(y==208?Blocks.COPPER_BLOCK:Blocks.STONE_BRICKS).defaultBlockState(),2);
    }
    private void finish(){
        if(shipFixture!=null){var s=Minecraft.getInstance().getSingleplayerServer();if(s!=null)CompletableFuture.runAsync(()->{try{shipFixture.returnBlocks();shipFixture=null;}catch(Exception e){checks.add(Map.of("test","ship cleanup","passed",false,"error",e.toString()));}},s).join();}
        if(finished)return;finished=true;var mc=Minecraft.getInstance();var report=new LinkedHashMap<String,Object>();report.put("timestamp",Instant.now().toString());report.put("mode",resume?"resume":"fresh");
        report.put("version",net.neoforged.fml.ModList.get().getModContainerById("hololith").orElseThrow().getModInfo().getVersion().toString());
        report.put("profile",minimal?"minimal":"full-pack");report.put("loadedMods",loadedMods);
        report.put("scope",minimal?"Graphical NeoForge development client and integrated server with only Create, Hololith and bundled dependencies, offline dev identity and a fresh vanilla flat disposable world. Normal controls, terrain surveys, markers and rendering are exercised; optional Sable ship phases are skipped. No authenticated multiplayer acceptance.":"Graphical NeoForge development client and integrated server with installed pack, offline dev identity and copied disposable world. No authenticated multiplayer or airborne flight acceptance.");
        report.put("skippedChecks",minimal?List.of("Sable assembly, translation, yaw and disassembly: optional mod absent"):List.of());
        report.put("phase",phase);report.put("worldTicks",ticks);report.put("fpsAtFinish",mc.getFps());report.put("checks",checks);report.put("total",checks.size());report.put("failures",checks.stream().filter(c->Boolean.FALSE.equals(c.get("passed"))).count());
        try{Files.createDirectories(Path.of("validation"));Files.writeString(Path.of("validation/hololith-"+(resume?"resume":"fresh")+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception e){throw new RuntimeException(e);}
        Screenshot.grab(mc.gameDirectory,"hololith-final.png",mc.getMainRenderTarget(),c->mc.execute(mc::stop));
    }
}
