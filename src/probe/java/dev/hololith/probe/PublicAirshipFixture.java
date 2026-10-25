package dev.hololith.probe;

import com.google.gson.GsonBuilder;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.schematics.SchematicItem;
import com.simibubi.create.content.schematics.packet.SchematicPlacePacket;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour;
import dev.hololith.*;
import dev.hololith.client.HololithRenderer;
import dev.hololith.client.MarkerPositionResolver;
import dev.hololith.compat.ShipCoordinates;
import dev.hololith.survey.*;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Quaterniondc;

/** Opt-in local asset acceptance test. Never part of the player JAR. No ship teleports or forces. */
final class PublicAirshipFixture {
    private static final String WORLD="hololith-airship", FILE="air_ship_v10-hololith-test.nbt", OWNER="hololith-airship-probe";
    private static final String SCHEMATIC_SHA256="f2d16fb6b5343cd872f0d02ec9d247cb11a48d21ea6be950963a627f297beaa2";
    private static final BlockPos CONSOLE=new BlockPos(13,2,23), ASSEMBLER=new BlockPos(12,1,7), TABLE=new BlockPos(12,1,18), SENSOR=new BlockPos(12,-1,18);
    private static final BlockPos FORWARD_LEVER=new BlockPos(12,2,23), REVERSE_LEVER=new BlockPos(11,2,23);
    private static final List<BlockPos> BEARINGS=List.of(new BlockPos(4,2,16),new BlockPos(21,2,16));
    private static final List<BlockPos> MARKERS=List.of(new BlockPos(8,1,4),new BlockPos(17,1,4),new BlockPos(8,1,20),new BlockPos(17,1,20));
    private static final DyeColor[] COLORS={DyeColor.CYAN,DyeColor.ORANGE,DyeColor.LIME,DyeColor.MAGENTA};
    private static final int SPACE=32,SHIFT=340,W=87,S=83,A=65,D=68;
    private final List<Map<String,Object>> checks=new CopyOnWriteArrayList<>(), samples=new CopyOnWriteArrayList<>(), events=new CopyOnWriteArrayList<>();
    private final Queue<String> screenshots=new ConcurrentLinkedQueue<>();
    private final long boot=System.nanoTime();
    private final Path game=Minecraft.getInstance().gameDirectory.toPath();
    private final Set<UUID> fixtureShips=new HashSet<>();
    private CompletableFuture<Void> work=CompletableFuture.completedFuture(null);
    private volatile Rig active;
    private volatile boolean started,finished;
    private boolean worldOpened,reported;
    private int clientTicks;
    private UUID playerId;
    private CompoundTag schematic;
    private String schematicHash="";
    private boolean baselinePassed;
    private double baselineTravel,baselineRise;
    private String limitation="";

    PublicAirshipFixture(){
        if(!Boolean.getBoolean("hololith.probe.airship")||!Files.exists(game.resolve(".hololith-airship")))throw new IllegalStateException("Public airship fixture requires its dedicated staging marker");
        NeoForge.EVENT_BUS.addListener(this::serverTick);
        NeoForge.EVENT_BUS.addListener(this::renderCheck);
    }
    void tick(){
        if(reported)return;Minecraft mc=Minecraft.getInstance();
        try{
            if(System.nanoTime()-boot>720_000_000_000L)throw new IllegalStateException("Airship acceptance exceeded its twelve-minute wall-clock limit");
            if(!worldOpened&&mc.level==null&&mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen&&mc.getOverlay()==null){
                if(Files.exists(game.resolve("saves/"+WORLD+"/level.dat")))throw new IllegalStateException("Refuse an existing world; start a newly archived airship trial");
                worldOpened=true;
                mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings("Hololith public airship acceptance",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                    new net.minecraft.world.level.levelgen.WorldOptions(121121L,false,false),registries->registries.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions(),new net.minecraft.client.gui.screens.TitleScreen());
            }
            if(mc.level==null||mc.player==null)return;clientTicks++;
            if(work.isCompletedExceptionally())work.join();if(!work.isDone())return;
            if(!started&&clientTicks>80){
                playerId=mc.player.getUUID();mc.options.fov().set(65);mc.options.bobView().set(false);
                var server=Objects.requireNonNull(mc.getSingleplayerServer());
                work=CompletableFuture.runAsync(()->{try{initialize(server.getPlayerList().getPlayer(playerId));started=true;}catch(Exception e){throw new RuntimeException(e);}},server);return;
            }
            String shot=screenshots.poll();if(shot!=null)Screenshot.grab(mc.gameDirectory,"hololith-airship-"+shot+".png",mc.getMainRenderTarget(),c->System.out.println(c.getString()));
            Rig rig=active;
            if(rig!=null&&rig.instrumented&&rig.offset!=null){
                var be=mc.level.getBlockEntity(rig.plot(TABLE));
                if(be instanceof HololithTableEntity table&&table.snapshot()!=null){
                    rig.clientSurvey=true;rig.clientPins|=table.markers().size()==4;
                    var field=HololithRenderer.class.getDeclaredField("CACHE");field.setAccessible(true);Object entry=((Map<?,?>)field.get(null)).get(table);
                    if(entry!=null){var mesh=entry.getClass().getDeclaredField("mesh");mesh.setAccessible(true);rig.clientMesh|=mesh.get(entry)!=null;}
                }
            }
            if(finished)finish();
        }catch(Throwable failure){failure.printStackTrace();check("airship harness completed without exception",false);limitation=failure.toString();finished=true;finish();}
    }
    private void initialize(ServerPlayer p)throws Exception{
        Objects.requireNonNull(p);var level=p.serverLevel();p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false,level.getServer());level.setDayTime(6000);
        Path source=game.resolve("schematics/"+FILE);schematic=NbtIo.readCompressed(source,NbtAccounter.create(16*1024*1024));
        schematicHash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source)));
        require("test schematic matches the reviewed sanitized Airship V10 asset",SCHEMATIC_SHA256.equals(schematicHash));
        ListTag entities=schematic.getList("entities",Tag.TAG_COMPOUND);
        require("test schematic contains exactly two glue entities and no mobs",entities.size()==2&&entities.stream().allMatch(t->((CompoundTag)t).getCompound("nbt").getString("id").equals("create:super_glue")));
        require("test schematic has expected dimensions and no embedded sublevels",schematic.getList("size",Tag.TAG_INT).size()==3&&!schematic.contains("sub_levels")&&schematic.getList("blocks",Tag.TAG_COMPOUND).size()==2121);
        require("full pack includes Aeronautics and Sable",net.neoforged.fml.ModList.get().isLoaded("aeronautics")&&net.neoforged.fml.ModList.get().isLoaded("sable"));
        require("fresh world has no preexisting Sable ships",ships(level).isEmpty());
        begin(p,false);
    }
    private void begin(ServerPlayer p,boolean instrumented)throws Exception{
        ServerLevel level=p.serverLevel();int x=instrumented?96:0;level.getChunk(x>>4,0);
        int ground=level.getHeight(Heightmap.Types.WORLD_SURFACE,x+12,12);BlockPos origin=new BlockPos(x,ground+3,0);
        Rig rig=new Rig(instrumented,origin);active=rig;
        for(int cx=-2;cx<=3;cx++)for(int cz=-2;cz<=3;cz++)level.getChunk((x>>4)+cx,cz);
        for(var pos:BlockPos.betweenClosed(origin.offset(-1,-1,-1),origin.offset(26,24,27)))if(!level.getBlockState(pos).isAir())throw new IllegalStateException("Airship import destination is occupied: "+pos);
        ItemStack blueprint=SchematicItem.create(level,FILE,OWNER);blueprint.set(AllDataComponents.SCHEMATIC_DEPLOYED,true);blueprint.set(AllDataComponents.SCHEMATIC_ANCHOR,origin);
        new SchematicPlacePacket(blueprint).handle(p);
        ListTag palette=schematic.getList("palette",Tag.TAG_COMPOUND),blocks=schematic.getList("blocks",Tag.TAG_COMPOUND);
        int matches=0;
        for(int i=0;i<blocks.size();i++){
            CompoundTag b=blocks.getCompound(i);ListTag a=b.getList("pos",Tag.TAG_INT);BlockPos local=new BlockPos(a.getInt(0),a.getInt(1),a.getInt(2));
            ResourceLocation name=ResourceLocation.parse(palette.getCompound(b.getInt("state")).getString("Name"));
            rig.payload.put(local,name);if(BuiltInRegistries.BLOCK.getKey(level.getBlockState(origin.offset(local)).getBlock()).equals(name))matches++;
        }
        require(rig.name()+" imported all 2121 blocks through Create",matches==2121);
        List<SuperGlueEntity> glues=level.getEntitiesOfClass(SuperGlueEntity.class,new AABB(Vec3.atLowerCornerOf(origin),Vec3.atLowerCornerOf(origin.offset(25,23,26))).inflate(1));
        require(rig.name()+" retained original propeller glue",glues.size()==2);
        rig.placedEntities.addAll(glues.stream().map(SuperGlueEntity::getUUID).toList());
        prepareFuel(p,rig);
        CompoundTag console=Objects.requireNonNull(level.getBlockEntity(origin.offset(CONSOLE))).saveWithoutMetadata(level.registryAccess());
        Set<Integer> keys=new HashSet<>();for(Tag key:console.getList("Keys",Tag.TAG_COMPOUND))keys.add(((CompoundTag)key).getInt("GLFWKey"));
        require(rig.name()+" preserves original six typewriter bindings",keys.equals(Set.of(SPACE,SHIFT,W,S,A,D)));
        var assembler=BuiltInRegistries.BLOCK.get(ResourceLocation.parse("simulated:physics_assembler"));
        if(assembler==Blocks.AIR)throw new IllegalStateException("Installed physics assembler missing");
        level.setBlockAndUpdate(origin.offset(ASSEMBLER),assembler.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE,AttachFace.FLOOR));
        rig.payload.put(ASSEMBLER,ResourceLocation.parse("simulated:physics_assembler"));
        if(instrumented)instrument(level,rig);
        prepareHoneyGlue(p,rig);
        camera(p,Vec3.atCenterOf(origin.offset(12,8,12)).add(32,14,34),Vec3.atCenterOf(origin.offset(12,8,12)));
        event(rig,"placed",Map.of("origin",List.of(origin.getX(),origin.getY(),origin.getZ())));
    }
    private void prepareHoneyGlue(ServerPlayer p,Rig rig)throws Exception{
        // The download contains rotor Super Glue, but no Honey Glue for hull assembly.
        // Apply the same legal Honey Glue selection in both trials through its normal
        // server packet handler, including its held-item and configured-size checks.
        ServerLevel level=p.serverLevel();BlockPos from=rig.origin.offset(0,-1,0),to=rig.origin.offset(24,22,25);
        AABB bounds=AABB.encapsulatingFullBlocks(from,to);ResourceLocation honeyId=ResourceLocation.parse("simulated:honey_glue");
        var before=level.getEntities((net.minecraft.world.entity.Entity)null,bounds,e->BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).equals(honeyId));
        require(rig.name()+" imported craft has no existing Honey Glue",before.isEmpty());
        Item item=BuiltInRegistries.ITEM.get(honeyId);if(item==Items.AIR)throw new IllegalStateException("Installed Honey Glue item missing");
        ItemStack previous=p.getMainHandItem().copy();
        try{
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item));p.inventoryMenu.broadcastChanges();
            Class<?> contextType=Class.forName("foundry.veil.api.network.handler.PacketContext");
            Object context=java.lang.reflect.Proxy.newProxyInstance(contextType.getClassLoader(),new Class<?>[]{contextType},(proxy,method,args)->switch(method.getName()){
                case "player"->p;case "level"->level;case "toString"->"Hololith disposable fixture normal server packet context";
                default->throw new UnsupportedOperationException("Unexpected Honey Glue context method: "+method.getName());
            });
            Class<?> packetType=Class.forName("dev.simulated_team.simulated.network.packets.honey_glue.HoneyGlueSpawnPacket");
            Object packet=packetType.getConstructor(BlockPos.class,BlockPos.class).newInstance(from,to);
            packetType.getMethod("handle",contextType).invoke(packet,context);
        }finally{p.setItemInHand(InteractionHand.MAIN_HAND,previous);p.inventoryMenu.broadcastChanges();}
        var added=level.getEntities((net.minecraft.world.entity.Entity)null,bounds,e->BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).equals(honeyId));
        require(rig.name()+" normal Honey Glue selection attaches the complete craft volume",added.size()==1&&added.getFirst().getBoundingBox().equals(bounds));
        rig.placedEntities.add(added.getFirst().getUUID());
        event(rig,"required Honey Glue assembly preparation",Map.of("localFromInclusive",List.of(0,-1,0),"localToInclusive",List.of(24,22,25),"glueUuid",added.getFirst().getUUID().toString(),"path","HoneyGlueSpawnPacket.handle with held Honey Glue and ordinary size validation","originalRotorSuperGluePreserved",true,"baselineScope","creator layout with required assembly and fuel preparation"));
    }
    private void prepareFuel(ServerPlayer p,Rig rig)throws Exception{
        // Create intentionally strips portable-engine runtime fuel through PartialSafeNBT.
        // Restore the source build's creative fuel using the engine's normal item interaction.
        ServerLevel level=p.serverLevel();ItemStack previous=p.getMainHandItem().copy();int fueled=0;
        try{
            for(var e:rig.payload.entrySet())if(e.getValue().getPath().endsWith("portable_engine")){
                BlockPos pos=rig.origin.offset(e.getKey());CompoundTag before=tag(level,pos),after=before;List<String> results=new ArrayList<>();
                FuelTarget target=fuelTarget(level,p,pos);
                event(rig,"portable engine import state",Map.of("localPosition",List.of(e.getKey().getX(),e.getKey().getY(),e.getKey().getZ()),"state",engineState(level,pos),"oldTopCenterHitsValueControl",hitsValueControl(level,pos,Vec3.atCenterOf(pos).add(0,.5,0))));
                for(int clicks=0;clicks<2&&!(after.getInt("BurnTime")>=51840000&&after.getBoolean("SuperHeated"));clicks++){
                    ItemStack cake=AllItems.CREATIVE_BLAZE_CAKE.asStack();p.setItemInHand(InteractionHand.MAIN_HAND,cake);p.inventoryMenu.broadcastChanges();
                    camera(p,target.eye(),target.hit().getLocation());
                    Map<String,Object> clickBefore=engineState(level,pos);
                    String result=p.gameMode.useItemOn(p,level,cake,InteractionHand.MAIN_HAND,target.hit()).toString();results.add(result);
                    after=tag(level,pos);
                    Vec3 hit=target.hit().getLocation().subtract(Vec3.atLowerCornerOf(pos));
                    event(rig,"portable engine fuel click",Map.of("localPosition",List.of(e.getKey().getX(),e.getKey().getY(),e.getKey().getZ()),"click",clicks+1,"item",BuiltInRegistries.ITEM.getKey(cake.getItem()).toString(),"hitLocal",List.of(hit.x,hit.y,hit.z),"face",target.hit().getDirection().toString(),"hitsValueControl",hitsValueControl(level,pos,target.hit().getLocation()),"result",result,"before",clickBefore,"after",engineState(level,pos)));
                }
                event(rig,"portable engine fuel preparation result",Map.of("localPosition",List.of(e.getKey().getX(),e.getKey().getY(),e.getKey().getZ()),"beforeBurnTime",before.getInt("BurnTime"),"beforeSuperHeated",before.getBoolean("SuperHeated"),"interactionResults",results,"afterBurnTime",after.getInt("BurnTime"),"afterSuperHeated",after.getBoolean("SuperHeated")));
                BlockEntity engine=requireBe(level,pos);
                if(after.getInt("BurnTime")>=51840000&&after.getBoolean("SuperHeated")&&(boolean)call(engine,"isCurrentFuelInfinite",new Class<?>[]{})&&(boolean)call(engine,"isSuperHeated",new Class<?>[]{}))fueled++;
            }
        }finally{p.setItemInHand(InteractionHand.MAIN_HAND,previous);p.inventoryMenu.broadcastChanges();}
        require(rig.name()+" six engines fueled and superheated through normal item interaction",fueled==6);
    }
    private record FuelTarget(Vec3 eye,BlockHitResult hit){}
    private static FuelTarget fuelTarget(ServerLevel level,ServerPlayer p,BlockPos pos){
        // Top-center is the rotation-direction value box. Use a genuine world ray to a
        // lower engine surface so Create's value-settings event cannot consume the cake.
        for(Direction side:Direction.Plane.HORIZONTAL){
            Vec3 eye=Vec3.atCenterOf(pos).add(side.getStepX()*2.4,1.62,side.getStepZ()*2.4);
            Vec3 aim=Vec3.atLowerCornerOf(pos).add(.5,.125,.5);
            BlockHitResult hit=level.clip(new ClipContext(eye,aim,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,p));
            if(hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(pos)&&!hitsValueControl(level,pos,hit.getLocation()))return new FuelTarget(eye,hit);
        }
        throw new IllegalStateException("No unobstructed engine fuel surface outside Create's value control at "+pos);
    }
    private static boolean hitsValueControl(ServerLevel level,BlockPos pos,Vec3 point){
        if(!(level.getBlockEntity(pos) instanceof SmartBlockEntity be))return false;
        for(var behaviour:be.getAllBehaviours())if(behaviour instanceof ValueSettingsBehaviour value&&value.isActive()&&value.testHit(point))return true;
        return false;
    }
    private static Map<String,Object> engineState(ServerLevel level,BlockPos pos)throws Exception{
        BlockEntity be=requireBe(level,pos);CompoundTag n=be.saveWithoutMetadata(level.registryAccess());Map<String,Object> out=new LinkedHashMap<>();
        out.put("block",BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).toString());out.put("blockClass",be.getBlockState().getBlock().getClass().getName());
        out.put("blockEntityType",String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType())));out.put("blockEntityClass",be.getClass().getName());out.put("clientSide",level.isClientSide);
        out.put("burnTimeGetter",call(be,"getCurrentBurnTime",new Class<?>[]{}));out.put("superHeatedGetter",call(be,"isSuperHeated",new Class<?>[]{}));out.put("infiniteGetter",call(be,"isCurrentFuelInfinite",new Class<?>[]{}));
        out.put("burnTimeTagType",n.contains("BurnTime")?n.get("BurnTime").getId():-1);out.put("superHeatedTagType",n.contains("SuperHeated")?n.get("SuperHeated").getId():-1);
        out.put("nbt",n.toString());return out;
    }
    private void instrument(ServerLevel level,Rig rig){
        for(int part=0;part<9;part++){BlockPos pos=rig.origin.offset(TABLE).offset(part%3-1,0,part/3-1);if(!level.getBlockState(pos).isAir())throw new IllegalStateException("Table would replace original machinery");level.setBlockAndUpdate(pos,Hololith.TABLE.get().defaultBlockState().setValue(HololithTableBlock.PART,part));}
        level.setBlockAndUpdate(rig.origin.offset(SENSOR),Hololith.SENSOR.get().defaultBlockState());var sensor=(SurveySensorEntity)level.getBlockEntity(rig.origin.offset(SENSOR));rig.sensorId=sensor.sensorId();
        CompoundTag card=new CompoundTag();card.putUUID("SensorId",rig.sensorId);card.putString("Dimension",level.dimension().location().toString());card.putInt("X",rig.origin.getX()+SENSOR.getX());card.putInt("Z",rig.origin.getZ()+SENSOR.getZ());
        var table=(HololithTableEntity)level.getBlockEntity(rig.origin.offset(TABLE));rig.tableId=table.tableId();table.configure(128,0,0,1,1,level.dimension().location().toString(),0,0);table.bind(card);
        for(int i=0;i<MARKERS.size();i++){
            BlockPos pos=rig.origin.offset(MARKERS.get(i));if(!level.getBlockState(pos).isAir())throw new IllegalStateException("Marker would replace original machinery");
            level.setBlockAndUpdate(pos,Hololith.MARKER.get().defaultBlockState().setValue(ReferenceMarkerBlock.COLOR,COLORS[i]));var marker=(ReferenceMarkerEntity)level.getBlockEntity(pos);marker.bind(card);rig.markerIds.add(marker.markerId());
        }
        level.addFreshEntity(new SuperGlueEntity(level,SuperGlueEntity.span(rig.origin.offset(TABLE).offset(-1,-1,-1),rig.origin.offset(TABLE).offset(1,0,1))));
        level.addFreshEntity(new SuperGlueEntity(level,SuperGlueEntity.span(rig.origin.offset(SENSOR),rig.origin.offset(SENSOR).above())));
    }
    private void serverTick(ServerTickEvent.Post event){
        if(!started||finished||!work.isDone())return;ServerPlayer p=event.getServer().getPlayerList().getPlayer(playerId);if(p==null)return;
        try{
            Rig rig=active;if(rig==null)return;rig.age++;
            if(rig.age==40)screenshots.add(rig.name()+"-imported");
            if(rig.age==60)assemble(p,rig);
            if(rig.ship==null)return;
            if(rig.age==100){
                event(rig,"drivetrain before powered startup",Map.of("machines",drivetrain(p.serverLevel(),rig)));
            }
            if(rig.age<140)return;
            Vec3 console=world(rig,CONSOLE);camera(p,console.add(0,1.3,-1.8),rig.instrumented?world(rig,TABLE).add(0,1.1,0):console.add(0,0,-14));
            Object keyboard=requireBe(p.serverLevel(),rig.plot(CONSOLE));
            if(!(boolean)call(keyboard,"checkUser",new Class<?>[]{UUID.class},playerId)){
                if(!(boolean)call(keyboard,"checkAndStartUsing",new Class<?>[]{UUID.class},playerId))throw new IllegalStateException("Could not acquire original typewriter controls");
                rig.pressed.clear();
            }
            int flight=rig.age-140;Vec3 position=world(rig,CONSOLE);
            if(rig.start==null){rig.start=position;rig.previous=position;event(rig,"flight begins",Map.of());}
            Vec3 velocity=position.subtract(rig.previous);rig.previous=position;
            rig.verticalVelocity=velocity.y;rig.speedWindow.addLast(Math.hypot(velocity.x,velocity.z));while(rig.speedWindow.size()>20)rig.speedWindow.removeFirst();
            double rise=position.y-rig.start.y,travel=Math.hypot(position.x-rig.start.x,position.z-rig.start.z);
            rig.maxRise=Math.max(rig.maxRise,rise);rig.maxTravel=Math.max(rig.maxTravel,travel);
            if(paused(p.serverLevel()))throw new IllegalStateException("Physics was paused during flight test");
            if(rig.proofTick<0&&flight>=100&&rise>2&&travel>8&&rig.maxPoweredPropellers==2)rig.proofTick=flight;
            int turning=rig.proofTick<0?Integer.MAX_VALUE:rig.proofTick+20;
            int braking=rig.proofTick<0?700:rig.proofTick+100;
            // Balloon heat persists after a key release. Use the measured ascent rate to
            // reduce the real lift accumulator early, rather than holding lift for a timer.
            int lift=tag(p.serverLevel(),rig.plot(new BlockPos(17,9,9))).getInt("OutputSignal");
            rig.currentSpeedSignal=tag(p.serverLevel(),rig.plot(new BlockPos(17,9,13))).getInt("OutputSignal");
            rig.requestedLift=(int)Math.max(0,Math.min(4,Math.round(4+(8-rise)*.06-velocity.y*14)));
            key(rig,keyboard,SPACE,lift<rig.requestedLift);key(rig,keyboard,SHIFT,lift>rig.requestedLift);
            // Begin modest propulsion after actual lift, well before the altitude bound.
            // Five is below the creator's maximum eleven; all input uses original keys.
            rig.requestedSpeed=flight<braking&&rise>.5?5:0;
            if(rise>.5&&flight<braking&&!rig.forwardSelected)selectForward(p,rig,true);
            key(rig,keyboard,W,rig.requestedSpeed>0&&rig.currentSpeedSignal<rig.requestedSpeed);
            key(rig,keyboard,D,flight>=turning&&flight<turning+60);
            key(rig,keyboard,S,rig.currentSpeedSignal>5||flight>=braking&&flight<braking+160);
            if(flight>=braking&&rig.currentSpeedSignal==0&&rig.forwardSelected)selectForward(p,rig,false);
            for(BlockPos bearing:BEARINGS){
                Object be=requireBe(p.serverLevel(),rig.plot(bearing));boolean running=(boolean)call(be,"isRunning",new Class<?>[]{});
                if(running&&rig.observedRunning.add(bearing)){event(rig,"propeller running observed",propellerState(p.serverLevel(),rig,bearing,true));if(rig.observedRunning.size()==2)screenshots.add(rig.name()+"-powered-startup");}
                if(!running&&Math.abs(((Number)call(be,"getSpeed",new Class<?>[]{})).doubleValue())>.01&&rig.requestedStart.add(bearing)){
                    event(rig,"propeller before normal powered startup request",propellerState(p.serverLevel(),rig,bearing,true));
                    // This is the action performed by an ordinary empty-hand bearing click.
                    // MechanicalBearing discards an assembly request while its input RPM is zero.
                    call(be,"setAssembleNextTick",new Class<?>[]{boolean.class},true);
                }
            }
            if(flight==100||flight==200||flight==400||flight==braking||flight==braking+60||flight==braking+180)event(rig,"drivetrain flight checkpoint",Map.of("flightTick",flight,"machines",drivetrain(p.serverLevel(),rig)));
            if(flight%5==0)sample(p,rig,flight,position,rise,travel);
            Vec3 forward=world(rig,new BlockPos(13,2,5)).subtract(position);double yaw=Math.atan2(forward.x,forward.z);
            if(flight==turning)rig.beforeTurn=yaw;
            if(flight==turning+80){rig.turn=Math.abs(Math.atan2(Math.sin(yaw-rig.beforeTurn),Math.cos(yaw-rig.beforeTurn)));rig.steeringComplete=true;}
            if(flight==braking)rig.beforeBrakeSpeed=rig.speedWindow.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            if(flight==braking+180){rig.finalSpeedSignal=rig.currentSpeedSignal;rig.afterBrakeSpeed=rig.speedWindow.stream().mapToDouble(Double::doubleValue).average().orElse(0);rig.brakingComplete=true;}
            if(flight==200||flight==500||flight==650)screenshots.add(rig.name()+"-flight-"+flight);
            boolean bounded=Math.abs(rise)>100||travel>220;
            if(bounded)event(rig,"bounded observation ended",Map.of("rise",rise,"travel",travel));
            if(flight>=960||rig.brakingComplete||bounded){evaluate(p,rig);cleanup(p,rig);if(!rig.instrumented&&baselinePassed)begin(p,true);else finished=true;}
        }catch(Throwable failure){failure.printStackTrace();limitation=failure.toString();Rig rig=active;check((rig==null?"baseline":rig.name())+" completed bounded flight",false);try{if(rig!=null)cleanup(p,rig);}catch(Exception cleanup){cleanup.printStackTrace();}finished=true;}
    }
    private void selectForward(ServerPlayer p,Rig rig,boolean enabled)throws Exception{
        ServerLevel level=p.serverLevel();BlockPos pos=rig.plot(FORWARD_LEVER),reverse=rig.plot(REVERSE_LEVER);
        require(rig.name()+" original reverse selector remains off",level.getBlockState(reverse).is(Blocks.LEVER)&&!level.getBlockState(reverse).getValue(BlockStateProperties.POWERED));
        var before=level.getBlockState(pos);if(!before.is(Blocks.LEVER))throw new IllegalStateException("Original forward selector missing");
        if(before.getValue(BlockStateProperties.POWERED)==enabled){rig.forwardSelected=enabled;return;}
        ItemStack previous=p.getMainHandItem().copy();String result;
        try{
            p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);p.inventoryMenu.broadcastChanges();
            result=p.gameMode.useItemOn(p,level,ItemStack.EMPTY,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos).add(0,-.25,0),Direction.UP,pos,false)).toString();
        }finally{p.setItemInHand(InteractionHand.MAIN_HAND,previous);p.inventoryMenu.broadcastChanges();}
        require(rig.name()+" original forward lever "+(enabled?"engages propulsion":"returns drivetrain to neutral"),level.getBlockState(pos).is(Blocks.LEVER)&&level.getBlockState(pos).getValue(BlockStateProperties.POWERED)==enabled);
        rig.forwardSelected=enabled;
        event(rig,"original forward selector used",Map.of("enabled",enabled,"result",result,"localPosition",List.of(12,2,23),"reverseEnabled",false,"purpose",enabled?"engage original direction selector":"neutral after original throttle decrease","machines",drivetrain(level,rig)));
    }
    private static List<Map<String,Object>> drivetrain(ServerLevel level,Rig rig)throws Exception{
        List<Map<String,Object>> result=new ArrayList<>();
        for(var entry:rig.payload.entrySet()){
            String path=entry.getValue().getPath();if(!(path.endsWith("portable_engine")||Set.of("analog_transmission","directional_gearshift","clutch","propeller_bearing").contains(path)))continue;
            BlockPos local=entry.getKey();BlockEntity be=requireBe(level,rig.plot(local));Map<String,Object> state=new LinkedHashMap<>();state.put("localPosition",List.of(local.getX(),local.getY(),local.getZ()));state.put("blockState",be.getBlockState().toString());state.put("class",be.getClass().getName());state.put("nbt",be.saveWithoutMetadata(level.registryAccess()).toString());
            state.put("rpm",call(be,"getSpeed",new Class<?>[]{}));state.put("theoreticalRpm",call(be,"getTheoreticalSpeed",new Class<?>[]{}));state.put("overstressed",call(be,"isOverStressed",new Class<?>[]{}));
            if(path.equals("propeller_bearing"))state.putAll(propellerState(level,rig,local,false));result.add(state);
        }
        return result;
    }
    private static Map<String,Object> propellerState(ServerLevel level,Rig rig,BlockPos local,boolean includeNbt)throws Exception{
        BlockEntity be=requireBe(level,rig.plot(local));Map<String,Object> state=new LinkedHashMap<>();state.put("localPosition",List.of(local.getX(),local.getY(),local.getZ()));state.put("rpm",call(be,"getSpeed",new Class<?>[]{}));state.put("rotationSpeed",call(be,"getRotationSpeed",new Class<?>[]{}));state.put("running",call(be,"isRunning",new Class<?>[]{}));state.put("active",call(be,"isActive",new Class<?>[]{}));state.put("sailPower",be.getClass().getField("totalSailPower").get(be));state.put("thrust",call(be,"getThrust",new Class<?>[]{}));
        Object moved=call(be,"getMovedContraption",new Class<?>[]{});state.put("contraptionPresent",moved!=null);
        if(moved instanceof com.simibubi.create.content.contraptions.AbstractContraptionEntity entity){state.put("contraptionBlocks",entity.getContraption().getBlocks().size());state.put("contraptionAlive",entity.isAlive());state.put("contraptionStalled",entity.isStalled());}
        if(includeNbt)state.put("nbt",be.saveWithoutMetadata(level.registryAccess()).toString());return state;
    }
    private void assemble(ServerPlayer p,Rig rig)throws Exception{
        ServerLevel level=p.serverLevel();require(rig.name()+" physics is unpaused before normal assembly",!paused(level));
        Set<UUID> before=new HashSet<>();for(Object ship:ships(level))before.add(shipId(ship));
        call(requireBe(level,rig.origin.offset(ASSEMBLER)),"assembleOrDisassemble",new Class<?>[]{});
        List<Object> created=new ArrayList<>();for(Object ship:ships(level))if(!before.contains(shipId(ship)))created.add(ship);
        require(rig.name()+" normal Physics Assembler creates one hull",created.size()==1);rig.ship=created.getFirst();rig.shipId=shipId(rig.ship);fixtureShips.add(rig.shipId);
        Object pose=call(rig.ship,"logicalPose",new Class<?>[]{});
        Vec3 local=(Vec3)call(pose,"transformPositionInverse",new Class<?>[]{Vec3.class},Vec3.atCenterOf(rig.origin.offset(ASSEMBLER)));
        rig.offset=BlockPos.containing(local).subtract(rig.origin.offset(ASSEMBLER));
        int retained=0,sourceRemaining=0;List<Map<String,Object>> missing=new ArrayList<>();
        for(var block:rig.payload.entrySet()){
            ResourceLocation actual=BuiltInRegistries.BLOCK.getKey(level.getBlockState(rig.plot(block.getKey())).getBlock());
            ResourceLocation source=BuiltInRegistries.BLOCK.getKey(level.getBlockState(rig.origin.offset(block.getKey())).getBlock());
            if(!source.equals(ResourceLocation.withDefaultNamespace("air")))sourceRemaining++;
            if(actual.equals(block.getValue()))retained++;
            else if(missing.size()<48)missing.add(Map.of("localPosition",List.of(block.getKey().getX(),block.getKey().getY(),block.getKey().getZ()),"expected",block.getValue().toString(),"actualPlotBlock",actual.toString(),"remainingSourceBlock",source.toString()));
        }
        event(rig,"normal assembly capture inventory",Map.of("expected",rig.payload.size(),"retained",retained,"sourceBlocksRemaining",sourceRemaining,"plotOffset",List.of(rig.offset.getX(),rig.offset.getY(),rig.offset.getZ()),"missingFirst48",missing));
        require(rig.name()+" normal assembly retained complete original hull and machinery",retained==rig.payload.size());
        if(rig.instrumented){
            var table=(HololithTableEntity)level.getBlockEntity(rig.plot(TABLE));var sensor=(SurveySensorEntity)level.getBlockEntity(rig.plot(SENSOR));
            require("instrumented assembly preserves table and sensor UUID",table!=null&&sensor!=null&&rig.tableId.equals(table.tableId())&&rig.sensorId.equals(sensor.sensorId()));
            require("instrumented assembly preserves all nine table tiles",java.util.stream.IntStream.range(0,9).allMatch(part->level.getBlockState(rig.plot(TABLE).offset(part%3-1,0,part/3-1)).is(Hololith.TABLE.get())));
        }
        event(rig,"assembled normally",Map.of("ship",rig.shipId.toString(),"retained",retained));
    }
    private void sample(ServerPlayer p,Rig rig,int flight,Vec3 position,double rise,double travel)throws Exception{
        ServerLevel level=p.serverLevel();Map<String,Object> row=new LinkedHashMap<>();row.put("run",rig.name());row.put("tick",flight);row.put("position",List.of(position.x,position.y,position.z));row.put("rise",rise);row.put("horizontalTravel",travel);
        Vec3 front=world(rig,new BlockPos(13,2,5)).subtract(position);double yaw=Math.atan2(front.x,front.z);row.put("yaw",yaw);
        int thrust=0;List<Double> thrusts=new ArrayList<>();for(BlockPos b:BEARINGS){Object be=requireBe(level,rig.plot(b));double force=((Number)call(be,"getThrust",new Class<?>[]{})).doubleValue();thrusts.add(force);if((boolean)call(be,"isActive",new Class<?>[]{})&&Math.abs(force)>.01)thrust++;}rig.maxPoweredPropellers=Math.max(rig.maxPoweredPropellers,thrust);row.put("propellerThrust",thrusts);
        List<Map<String,Object>> propellers=new ArrayList<>();for(BlockPos b:BEARINGS)propellers.add(propellerState(level,rig,b,false));row.put("propellers",propellers);row.put("forwardSelected",rig.forwardSelected);
        int lift=tag(level,rig.plot(new BlockPos(17,9,9))).getInt("OutputSignal"),speed=tag(level,rig.plot(new BlockPos(17,9,13))).getInt("OutputSignal");row.put("liftAccumulator",lift);row.put("speedAccumulator",speed);rig.currentSpeedSignal=speed;rig.maxLiftSignal=Math.max(rig.maxLiftSignal,lift);rig.maxSpeedSignal=Math.max(rig.maxSpeedSignal,speed);
        row.put("burnerSignal",tag(level,rig.plot(new BlockPos(12,15,10))).getInt("SignalStrength"));row.put("heldKeys",List.copyOf(rig.pressed));row.put("pilotRequestedLift",rig.requestedLift);row.put("pilotRequestedSpeed",rig.requestedSpeed);row.put("verticalBlocksPerTick",rig.verticalVelocity);row.put("horizontalBlocksPerTick",rig.speedWindow.peekLast());
        if(rig.instrumented){
            var table=(HololithTableEntity)level.getBlockEntity(rig.plot(TABLE));var sensor=(SurveySensorEntity)level.getBlockEntity(rig.plot(SENSOR));
            if(table==null||sensor==null||!rig.tableId.equals(table.tableId())||!rig.sensorId.equals(sensor.sensorId()))throw new IllegalStateException("Instrument identity lost during flight");
            Vec3 sensorWorld=world(rig,SENSOR);var heartbeat=SurveyStore.get(p.server).sensor(rig.sensorId);row.put("sensorWorld",List.of(sensorWorld.x,sensorWorld.z));
            rig.sensorTransformError=Math.max(rig.sensorTransformError,sensorWorld.distanceTo(ShipCoordinates.toWorld(level,rig.plot(SENSOR)).orElseThrow()));
            if(heartbeat!=null){rig.sensorHeard|=heartbeat.online();row.put("sensorReported",List.of(heartbeat.x(),heartbeat.z()));}
            Map<UUID,Vec3> current=new HashMap<>();for(int i=0;i<4;i++)current.put(rig.markerIds.get(i),world(rig,MARKERS.get(i)));rig.history.addLast(current);while(rig.history.size()>4)rig.history.removeFirst();
            var snapshot=table.snapshot();if(snapshot!=null){rig.surveyCenters.add(snapshot.centerX+","+snapshot.centerZ);row.put("survey",List.of(snapshot.centerX,snapshot.centerZ));row.put("known",snapshot.knownColumns());}
            row.put("status",table.status());row.put("markerCount",table.markers().size());
            if(table.markers().size()==4){
                rig.markerFrames++;for(var marker:table.markers()){
                    Vec3 now=current.get(marker.id());if(now==null)throw new IllegalStateException("Unexpected marker identity");
                    double error=Math.hypot(marker.x()-now.x,marker.z()-now.z);Vec3 oldest=rig.history.getFirst().get(marker.id());double motion=Math.hypot(now.x-oldest.x,now.z-oldest.z);
                    rig.maxMarkerError=Math.max(rig.maxMarkerError,error);if(rig.history.size()==4&&error>motion+.15)rig.badMarkerFrames++;
                }
            }
        }
        samples.add(row);
    }
    private void evaluate(ServerPlayer p,Rig rig)throws Exception{
        boolean flight=rig.maxLiftSignal>0&&rig.maxSpeedSignal>0&&rig.maxPoweredPropellers==2&&rig.maxRise>2&&rig.maxTravel>8;
        check(rig.name()+" original controls increase lift and propulsion accumulators",rig.maxLiftSignal>0&&rig.maxSpeedSignal>0);
        check(rig.name()+" two real propellers generate thrust",rig.maxPoweredPropellers==2);
        check(rig.name()+" balloon rises at least two blocks under unpaused physics",rig.maxRise>2);
        check(rig.name()+" powered horizontal flight exceeds eight blocks",rig.maxTravel>8);
        boolean steering=rig.steeringComplete&&rig.turn>.02,throttleReduction=rig.brakingComplete&&rig.finalSpeedSignal<rig.maxSpeedSignal;
        boolean decelerated=rig.brakingComplete&&rig.beforeBrakeSpeed>.02&&rig.afterBrakeSpeed<rig.beforeBrakeSpeed*.9;
        check(rig.name()+" completed original steering input changes heading",steering);
        check(rig.name()+" completed original decrease input reduces throttle",throttleReduction);
        check(rig.name()+" measured horizontal speed decreases after throttle reduction and neutral",decelerated);
        check(rig.name()+" respects creator speed limit of eleven",rig.maxSpeedSignal<=11);
        if(!rig.instrumented){baselinePassed=flight&&steering&&throttleReduction&&decelerated&&rig.maxSpeedSignal<=11;baselineTravel=rig.maxTravel;baselineRise=rig.maxRise;if(!baselinePassed)limitation="Prepared creator-layout baseline did not pass complete flight, steering and deceleration acceptance; instrumented acceptance skipped. No failure attributed to Hololith.";}
        else{
            check("moving ship sensor transform agrees with engine pose",rig.sensorTransformError<.001&&rig.sensorHeard);
            check("moving ship survey changes center during actual flight",rig.surveyCenters.size()>=2);
            check("four reference markers remain within measured update latency",rig.markerFrames>=20&&rig.badMarkerFrames==0);
            check("client receives moving ship survey and four reference pins",rig.clientSurvey&&rig.clientPins);
            check("actual ship table renderer builds GPU mesh",rig.clientMesh);
            check("live marker resolver matches independently composed Sable render pose",rig.renderSamples>=40&&rig.renderErrors==0&&rig.maxRenderError<.001);
            check("moving rendered pins advance beyond stale authoritative samples",rig.renderCorrections>=4);
            event(rig,"baseline comparison",Map.of("baselineRise",baselineRise,"instrumentedRise",rig.maxRise,"baselineTravel",baselineTravel,"instrumentedTravel",rig.maxTravel,"maxMarkerWorldError",rig.maxMarkerError));
        }
        Map<String,Object> summary=new LinkedHashMap<>();summary.put("rise",rig.maxRise);summary.put("travel",rig.maxTravel);summary.put("turnRadians",rig.turn);
        summary.put("steeringComplete",rig.steeringComplete);summary.put("brakingComplete",rig.brakingComplete);summary.put("renderSamples",rig.renderSamples);summary.put("renderErrors",rig.renderErrors);summary.put("renderCorrections",rig.renderCorrections);summary.put("maxRenderError",rig.maxRenderError);summary.put("sensorTransformError",rig.sensorTransformError);summary.put("markerFrames",rig.markerFrames);summary.put("badMarkerFrames",rig.badMarkerFrames);summary.put("maxMarkerWorldError",rig.maxMarkerError);summary.put("surveyCenterCount",rig.surveyCenters.size());
        summary.put("beforeReductionHorizontalBlocksPerTick",rig.beforeBrakeSpeed);summary.put("afterReductionHorizontalBlocksPerTick",rig.afterBrakeSpeed);summary.put("speedMeasurementWindowTicks",20);
        event(rig,"flight complete",summary);
    }
    private void renderCheck(RenderFrameEvent.Post event){
        Rig rig=active;var mc=Minecraft.getInstance();
        if(finished||rig==null||!rig.instrumented||rig.offset==null||mc.level==null||rig.age<180)return;
        try{
            if(!(mc.level.getBlockEntity(rig.plot(TABLE)) instanceof HololithTableEntity table)||table.snapshot()==null)return;
            Object container=Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer").getMethod("getContainer",Level.class).invoke(null,mc.level);
            Object ship=call(container,"getSubLevel",new Class<?>[]{UUID.class},rig.shipId);if(ship==null)return;
            float partial=mc.getTimer().getGameTimeDeltaPartialTick(true);Object pose=call(ship,"renderPose",new Class<?>[]{float.class},partial);
            Vector3dc center=(Vector3dc)call(pose,"rotationPoint",new Class<?>[]{}),scale=(Vector3dc)call(pose,"scale",new Class<?>[]{}),position=(Vector3dc)call(pose,"position",new Class<?>[]{});
            Quaterniondc orientation=(Quaterniondc)call(pose,"orientation",new Class<?>[]{});
            for(var marker:table.markers()){
                int index=rig.markerIds.indexOf(marker.id());if(index<0)continue;BlockPos block=rig.plot(MARKERS.get(index));
                // Independent composition from the engine's render-pose components; never call
                // ShipCoordinates or Pose3dc.transformPosition to obtain the expected value.
                Vector3d expected=new Vector3d(block.getX()+.5,block.getY()+.5,block.getZ()+.5).sub(center).mul(scale).rotate(orientation).add(position);
                Vec3 resolved=MarkerPositionResolver.position(table,marker,partial);double error=Math.hypot(resolved.x-expected.x,resolved.z-expected.z);
                rig.renderSamples++;rig.maxRenderError=Math.max(rig.maxRenderError,error);if(error>.001)rig.renderErrors++;
                if(Math.hypot(resolved.x-marker.x(),resolved.z-marker.z())>.01)rig.renderCorrections++;
            }
        }catch(Exception failure){rig.renderErrors++;if(!rig.renderFailureLogged){rig.renderFailureLogged=true;failure.printStackTrace();}}
    }
    private void key(Rig rig,Object keyboard,int key,boolean pressed)throws Exception{
        if(rig.pressed.contains(key)==pressed)return;
        Class<?> entry=Class.forName("dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterEntries$KeyboardEntry");
        call(keyboard,"onKeyInteraction",new Class<?>[]{UUID.class,entry,int.class,boolean.class},playerId,null,key,pressed);
        if(pressed)rig.pressed.add(key);else rig.pressed.remove(key);event(rig,"typewriter input",Map.of("key",key,"pressed",pressed));
    }
    private void cleanup(ServerPlayer p,Rig rig)throws Exception{
        if(rig.ship==null)return;Object keyboard=p.serverLevel().getBlockEntity(rig.plot(CONSOLE));if(keyboard!=null){for(int key:List.copyOf(rig.pressed))key(rig,keyboard,key,false);call(keyboard,"disconnectUser",new Class<?>[]{});}
        if(!fixtureShips.remove(rig.shipId))throw new IllegalStateException("Refuse to remove a non-fixture ship");
        require("cleanup targets recorded "+rig.name()+" ship UUID",rig.shipId.equals(shipId(rig.ship)));
        camera(p,Vec3.atCenterOf(rig.origin).add(-20,25,-20),Vec3.atCenterOf(rig.origin));
        Object container=container(p.serverLevel());Class<?> type=Class.forName("dev.ryanhcode.sable.sublevel.SubLevel"),reason=Class.forName("dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason");
        call(container,"removeSubLevel",new Class<?>[]{type,reason},rig.ship,reason.getField("REMOVED").get(null));
        event(rig,"fixture ship removed",Map.of("ship",rig.shipId.toString()));
    }
    private static BlockEntity requireBe(ServerLevel level,BlockPos pos){return Objects.requireNonNull(level.getBlockEntity(pos),"Missing machine at "+pos);}
    private static CompoundTag tag(ServerLevel level,BlockPos pos){return requireBe(level,pos).saveWithoutMetadata(level.registryAccess());}
    private static Object call(Object target,String name,Class<?>[] types,Object...args)throws Exception{return target.getClass().getMethod(name,types).invoke(target,args);}
    private static Object container(ServerLevel level)throws Exception{return Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer").getMethod("getContainer",Level.class).invoke(null,level);}
    private static List<?> ships(ServerLevel level)throws Exception{return (List<?>)call(container(level),"getAllSubLevels",new Class<?>[]{});}
    private static UUID shipId(Object ship)throws Exception{return (UUID)call(ship,"getUniqueId",new Class<?>[]{});}
    private static boolean paused(ServerLevel level)throws Exception{Object physics=Class.forName("dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem").getMethod("require",Level.class).invoke(null,level);return (boolean)call(physics,"getPaused",new Class<?>[]{});}
    private static Vec3 world(Rig rig,BlockPos local)throws Exception{return (Vec3)call(call(rig.ship,"logicalPose",new Class<?>[]{}),"transformPosition",new Class<?>[]{Vec3.class},Vec3.atCenterOf(rig.plot(local)));}
    private static void camera(ServerPlayer p,Vec3 eye,Vec3 target){Vec3 delta=target.subtract(eye);p.connection.teleport(eye.x,eye.y-p.getEyeHeight(),eye.z,(float)Math.toDegrees(Math.atan2(delta.z,delta.x))-90,(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));p.getAbilities().flying=true;p.onUpdateAbilities();}
    private void check(String text,boolean passed){checks.add(Map.of("test",text,"passed",passed));System.out.println("HOLOLITH AIRSHIP CHECK "+passed+": "+text);}
    private void require(String text,boolean passed){check(text,passed);if(!passed)throw new IllegalStateException(text);}
    private void event(Rig rig,String label,Map<String,?> details){Map<String,Object> e=new LinkedHashMap<>();e.put("run",rig.name());e.put("tick",rig.age);e.put("event",label);e.putAll(details);events.add(e);}
    private void finish(){
        if(reported)return;reported=true;Map<String,Object> report=new LinkedHashMap<>();report.put("timestamp",java.time.Instant.now().toString());report.put("version",net.neoforged.fml.ModList.get().getModContainerById("hololith").orElseThrow().getModInfo().getVersion().toString());
        report.put("profile","public-airship");report.put("source","https://createmod.com/schematics/airship-v10");report.put("author","Patrik");report.put("schematic",FILE);report.put("schematicSha256",schematicHash);report.put("baselinePassed",baselinePassed);report.put("checks",checks);report.put("total",checks.size());report.put("failures",checks.stream().filter(c->Boolean.FALSE.equals(c.get("passed"))).count());report.put("events",events);report.put("samples",samples);report.put("limitation",limitation);
        report.put("scope","Exact reviewed sanitized Airship V10 asset only: creator layout with required assembly and fuel preparation. Fresh disposable flat world, actual Create placement and normal Physics Assembler, unpaused Sable physics and original typewriter controller callbacks. Both trials add the same Honey Glue selection via its normal server handler; original rotor Super Glue is preserved. Creative observer camera follows the craft; no ship pose/velocity/force injection and no passenger or authenticated multiplayer acceptance. Create strips runtime engine fuel; both trials attempt identical Creative Blaze Cake item interactions to reproduce the source build's infinite superheated fuel, with per-click states recorded and verified before assembly. Public build is not distributed with this harness.");
        report.put("loadedMods",net.neoforged.fml.ModList.get().getMods().stream().map(m->m.getModId()).sorted().toList());
        try{Files.createDirectories(game.resolve("validation"));Files.writeString(game.resolve("validation/airship.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception e){e.printStackTrace();}
        Minecraft.getInstance().execute(Minecraft.getInstance()::stop);
    }
    private static final class Rig {
        final boolean instrumented;final BlockPos origin;final Map<BlockPos,ResourceLocation> payload=new LinkedHashMap<>();final Set<Integer> pressed=new TreeSet<>();final Set<String> surveyCenters=new HashSet<>();final List<UUID> placedEntities=new ArrayList<>(),markerIds=new ArrayList<>();final Deque<Map<UUID,Vec3>> history=new ArrayDeque<>();
        final Deque<Double> speedWindow=new ArrayDeque<>();
        final Set<BlockPos> requestedStart=new HashSet<>(),observedRunning=new HashSet<>();
        Object ship;UUID shipId,tableId,sensorId;BlockPos offset;Vec3 start,previous;int age,maxLiftSignal,maxSpeedSignal,currentSpeedSignal,finalSpeedSignal,maxPoweredPropellers,markerFrames,badMarkerFrames,requestedLift,requestedSpeed,proofTick=-1;double maxRise,maxTravel,beforeTurn,turn,sensorTransformError,maxMarkerError,verticalVelocity,beforeBrakeSpeed,afterBrakeSpeed;boolean sensorHeard,steeringComplete,brakingComplete,forwardSelected;volatile boolean clientSurvey,clientPins,clientMesh,renderFailureLogged;volatile int renderSamples,renderErrors,renderCorrections;volatile double maxRenderError;
        Rig(boolean instrumented,BlockPos origin){this.instrumented=instrumented;this.origin=origin;}
        String name(){return instrumented?"instrumented":"baseline";}BlockPos plot(BlockPos local){return origin.offset(local).offset(offset);}
    }
}
