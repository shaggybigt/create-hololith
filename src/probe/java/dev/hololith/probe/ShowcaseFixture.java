package dev.hololith.probe;

import com.google.gson.GsonBuilder;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.mounted.*;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.hololith.*;
import dev.hololith.client.*;
import dev.hololith.survey.SurveyKey;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Four-minute actual-gameplay recording. This fixture never enters the player JAR. */
final class ShowcaseFixture {
    private static final BlockPos TABLE=new BlockPos(24,103,0),SENSOR=new BlockPos(0,82,0),BEACON=new BlockPos(-26,96,-20);
    private static final BlockPos ASSEMBLER=new BlockPos(0,90,25),CART_SENSOR=new BlockPos(2,1,0);
    private static final List<BlockPos> PINS=List.of(new BlockPos(-6,82,-6),new BlockPos(6,82,-6),new BlockPos(6,82,6),new BlockPos(-6,82,6));
    private static final DyeColor[] COLORS={DyeColor.CYAN,DyeColor.ORANGE,DyeColor.LIME,DyeColor.MAGENTA};
    private static final int FPS=30,SECONDS=240;
    private record Cue(int second,String label,Runnable action){}
    private final List<Cue> cues=new ArrayList<>();
    private final List<Map<String,Object>> evidence=new CopyOnWriteArrayList<>(),actions=new CopyOnWriteArrayList<>();
    private final Set<SurveyKey> movingSurveys=new HashSet<>();
    private final Set<UUID> cartMarkerIds=new HashSet<>();
    private final long boot=System.nanoTime();
    private CompletableFuture<Void> work=CompletableFuture.completedFuture(null);
    private long recordingStart;
    private int ticks,frames,repeatedFrames,cueIndex,cartId;
    private boolean built,done,successful,cinematic=true;
    private volatile boolean moving;
    private AbstractMinecart cart;
    private AbstractContraptionEntity contraption;
    private Vec3 previousCart,cameraPosition;
    private double cartTravel;
    private int movingPowerSamples,movingMarkerSamples;
    private UUID localId,beaconId,cartSensorId;
    private CompoundTag cachedBeforePower;
    private PonderUI ponder;
    private Process encoder;
    private OutputStream video;
    private byte[] rgb;

    ShowcaseFixture(){
        var game=Minecraft.getInstance().gameDirectory.toPath();
        if(!Files.exists(game.resolve(".hololith-showcase")))throw new IllegalStateException("Showcase requires its dedicated disposable staging marker");
        NeoForge.EVENT_BUS.addListener(this::capture);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event)->{
            if(!moving||cart==null||!cart.isAlive())return;
            Vec3 position=cart.position();if(previousCart!=null)cartTravel+=position.distanceTo(previousCart);previousCart=position;
            if(event.getServer().overworld().getGameTime()%5!=0)return;
            var block=event.getServer().overworld().getBlockEntity(TABLE);
            if(block instanceof HololithTableEntity table){
                if(powered(table))movingPowerSamples++;
                if(table.snapshot()!=null)movingSurveys.add(table.snapshot().key());
                if(hasCartMarkers(table))movingMarkerSamples++;
            }
        });
        plan();
    }
    private void server(Consumer<ServerPlayer> action){
        var mc=Minecraft.getInstance();var server=Objects.requireNonNull(mc.getSingleplayerServer());UUID player=Objects.requireNonNull(mc.player).getUUID();
        work=CompletableFuture.runAsync(()->action.accept(Objects.requireNonNull(server.getPlayerList().getPlayer(player))),server);
    }
    private double seconds(){return recordingStart==0?0:(System.nanoTime()-recordingStart)/1e9;}
    private void cue(int second,String label,Runnable action){cues.add(new Cue(second,label,action));}
    private void require(String name,boolean passed){
        evidence.add(Map.of("check",name,"passed",passed,"second",seconds()));
        if(!passed)throw new IllegalStateException(name);
    }
    private static boolean powered(HololithTableEntity table){return table.hasSource()&&table.hasNetwork()&&table.isProjecting()&&Math.abs(Math.abs(table.getSpeed())-32)<.01;}
    private boolean hasCartMarkers(HololithTableEntity table){
        return cartMarkerIds.size()==4&&table.markers().stream().map(point->point.id()).collect(java.util.stream.Collectors.toSet()).containsAll(cartMarkerIds);
    }
    private HololithTableEntity table(ServerPlayer player){return (HololithTableEntity)Objects.requireNonNull(player.serverLevel().getBlockEntity(TABLE));}
    private HololithTableEntity clientTable(){return Minecraft.getInstance().level.getBlockEntity(TABLE) instanceof HololithTableEntity table?table:null;}
    private void hand(ServerPlayer player,ItemStack stack){player.setItemInHand(InteractionHand.MAIN_HAND,stack);player.inventoryMenu.broadcastChanges();}
    private void interaction(BlockPos pos,ItemStack stack){
        cinematic=false;var mc=Minecraft.getInstance();mc.options.hideGui=false;
        server(player->{hand(player,stack);near(player,pos);});
    }
    private void beauty(){
        var mc=Minecraft.getInstance();if(mc.screen!=null)mc.screen.onClose();mc.options.hideGui=true;cinematic=true;cameraPosition=null;
        server(player->hand(player,ItemStack.EMPTY));
    }
    private void use(BlockPos pos){
        var mc=Minecraft.getInstance();mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(pos).add(0,.5,0),Direction.UP,pos,false));mc.player.swing(InteractionHand.MAIN_HAND);
    }
    private void near(ServerPlayer player,BlockPos pos){look(player,Vec3.atCenterOf(pos).add(0,2.2,3.1),Vec3.atCenterOf(pos).add(0,.25,0));}
    private void look(ServerPlayer player,Vec3 eye,Vec3 focus){
        Vec3 delta=focus.subtract(eye);player.connection.teleport(eye.x,eye.y-player.getEyeHeight(),eye.z,
            (float)Math.toDegrees(Math.atan2(delta.z,delta.x))-90,(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));
        player.getAbilities().mayfly=true;player.getAbilities().flying=true;player.setDeltaMovement(Vec3.ZERO);player.onUpdateAbilities();
    }
    private void press(String prefix){
        var screen=Minecraft.getInstance().screen;
        if(!(screen instanceof HololithControls))throw new IllegalStateException("Instrument screen missing at "+seconds());
        screen.children().stream().filter(child->child instanceof Button button&&button.getMessage().getString().startsWith(prefix))
            .map(child->(Button)child).findFirst().orElseThrow(()->new IllegalStateException("Missing control: "+prefix)).onPress();
    }
    private void brightness(double value){
        var screen=Minecraft.getInstance().screen;
        if(!(screen instanceof HololithControls))throw new IllegalStateException("Brightness screen missing");
        var slider=screen.children().stream().filter(child->child instanceof AbstractSliderButton).map(child->(AbstractSliderButton)child).findFirst().orElseThrow();
        slider.onClick(slider.getX()+4+(slider.getWidth()-8)*value,slider.getY()+10);
    }
    private void recordedCard(UUID expected,String label){
        server(player->{CompoundTag card=player.getMainHandItem().getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
            require(label,SurveyCardItem.isSensorCard(card)&&expected.equals(card.getUUID("SensorId")));});
    }
    private void linked(UUID expected,String label){server(player->require(label,expected.equals(table(player).sensorId())&&table(player).mode()==1));}
    private void setting(int span,int zoom,int rotation,String label){
        server(player->{var table=table(player);require(label,powered(table)&&table.span()==span&&table.zoom()==zoom&&table.rotation()==rotation&&table.snapshot()!=null&&table.snapshot().span==span);
            require(label+" applies actual network stress",Math.abs(table.getOrCreateNetwork().getActualStressOf(table)-table.stressImpact()*32)<.01);});
    }
    private void motor(ServerPlayer player,int rpm){
        var source=(CreativeMotorBlockEntity)Objects.requireNonNull(player.serverLevel().getBlockEntity(TABLE.below(2)));
        source.generatedSpeed.setValue(rpm); // Real Create generator setting; never set table or network speed.
    }

    private void plan(){
        cue(19,"stop actual kinetic source",()->server(player->{cachedBeforePower=table(player).snapshot().save();motor(player,0);}));
        cue(22,"verify stopped projection and retained cache",()->server(player->require("stopping the real source blacks out the projection and preserves terrain",!table(player).isProjecting()&&table(player).getSpeed()==0&&cachedBeforePower.equals(table(player).snapshot().save()))));
        cue(25,"set source below minimum",()->server(player->motor(player,8)));
        cue(28,"verify minimum speed requirement",()->server(player->require("8 RPM reaches the shaft but cannot project",Math.abs(table(player).getSpeed())==8&&!table(player).isProjecting())));
        cue(31,"restore normal source",()->server(player->motor(player,32)));
        cue(34,"verify real power recovery",()->server(player->require("32 RPM restores the real survey projection",powered(table(player))&&table(player).snapshot()!=null)));
        cue(35,"open scale controls",()->interaction(TABLE,ItemStack.EMPTY));cue(36,"use table",()->use(TABLE));
        cue(39,"select 64 blocks",()->press("Area:"));cue(40,"rotate map",()->press("Rotation:"));cue(42,"apply 64-block survey",()->press("Apply"));cue(43,"view larger survey",this::beauty);
        cue(47,"verify 64-block map",()->setting(64,0,1,"normal controls produce a powered 64-block rotated survey"));
        cue(48,"open larger survey controls",()->interaction(TABLE,ItemStack.EMPTY));cue(49,"use table",()->use(TABLE));
        cue(52,"select 128 blocks",()->press("Area:"));cue(53,"increase projection size",()->press("Projection:"));cue(54,"apply 128-block survey",()->press("Apply"));cue(55,"view broad terrain",this::beauty);
        cue(61,"verify 128-block map",()->setting(128,1,1,"normal controls produce a powered 128-block 125-percent survey"));
        cue(71,"open projection controls",()->interaction(TABLE,ItemStack.EMPTY));cue(72,"use table",()->use(TABLE));
        cue(75,"cycle survey area",()->press("Area:"));cue(76,"enlarge projection to 150 percent",()->press("Projection:"));cue(77,"select 64-block detail",()->press("Area:"));cue(78,"apply detailed view",()->press("Apply"));cue(79,"view enlarged terrain",this::beauty);
        cue(84,"verify enlarged map stress",()->setting(64,2,1,"normal controls produce a powered 64-block 150-percent survey"));
        cue(90,"prepare blank card at local sensor",()->interaction(SENSOR,Hololith.CARD.toStack()));cue(92,"record local sensor",()->use(SENSOR));cue(95,"verify physical sensor card",()->recordedCard(localId,"normal item use records the local sensor UUID"));
        cue(96,"bring sensor card to table",()->server(player->near(player,TABLE)));cue(98,"link sensor card",()->use(TABLE));cue(102,"verify local link",()->linked(localId,"normal card interaction links the placed local sensor"));cue(103,"view linked local terrain",this::beauty);
        cue(114,"prepare card at remote beacon",()->interaction(BEACON,Hololith.CARD.toStack()));cue(116,"record physical beacon",()->use(BEACON));cue(120,"verify beacon card",()->recordedCard(beaconId,"normal item use records the placed beacon UUID"));
        cue(121,"bring beacon card to table",()->server(player->near(player,TABLE)));cue(123,"apply beacon card",()->use(TABLE));cue(127,"verify remote survey",()->server(player->{var table=table(player);require("placed remote beacon supplies the live terrain region",beaconId.equals(table.sensorId())&&powered(table)&&table.snapshot()!=null&&Math.abs(table.snapshot().centerX-BEACON.getX())<=4&&Math.abs(table.snapshot().centerZ-BEACON.getZ())<=4);}));cue(130,"view beacon terrain",this::beauty);
        cue(136,"record local sensor for reference markers",()->interaction(SENSOR,Hololith.CARD.toStack()));cue(138,"record sensor",()->use(SENSOR));cue(140,"return source card to table",()->server(player->near(player,TABLE)));cue(142,"restore local sensor survey",()->use(TABLE));
        cue(143,"bring card to first reference block",()->server(player->near(player,PINS.getFirst())));cue(144,"link reference to sensor",()->use(PINS.getFirst()));cue(146,"select red dye",()->server(player->hand(player,new ItemStack(Items.RED_DYE))));cue(147,"dye reference block",()->use(PINS.getFirst()));
        cue(149,"verify linked dye marker",()->server(player->{var marker=(ReferenceMarkerEntity)player.serverLevel().getBlockEntity(PINS.getFirst());var table=table(player);require("normal card and dye use creates the red linked reference",marker!=null&&localId.equals(marker.targetId())&&marker.targetKind()==1&&marker.getBlockState().getValue(ReferenceMarkerBlock.COLOR)==DyeColor.RED);require("four actual reference pins appear on the terrain",table.markers().size()==4&&table.markers().stream().anyMatch(point->point.id().equals(marker.markerId())&&point.color()==(DyeColor.RED.getTextureDiffuseColor()&0xffffff)));}));cue(150,"view colored terrain references",this::beauty);
        cue(160,"open brightness controls at dusk",()->{cinematic=false;Minecraft.getInstance().options.hideGui=false;server(player->{hand(player,ItemStack.EMPTY);near(player,TABLE);player.serverLevel().setDayTime(13000);});});cue(162,"use brightness panel",()->use(TABLE));cue(165,"lower brightness",()->brightness(.28));cue(167,"raise brightness",()->brightness(.86));
        cue(169,"disable subtle flicker",()->press("Subtle hologram flicker:"));cue(170,"verify flicker off",()->require("real flicker control switches off",!HololithClientConfig.HOLOGRAM_FLICKER.get()));cue(171,"enable subtle flicker",()->press("Subtle hologram flicker:"));
        cue(173,"verify local appearance settings",()->require("real brightness and flicker controls update local settings",HololithClientConfig.intensity()>1.5&&HololithClientConfig.HOLOGRAM_FLICKER.get()));cue(174,"view luminous evening projection",this::beauty);
        cue(182,"open actual Create Ponder",()->{cinematic=false;Minecraft.getInstance().options.hideGui=false;ponder=PonderUI.of(Hololith.TABLE_ITEM.toStack());require("Create returns the real Hololith Ponder interface",ponder!=null);Minecraft.getInstance().setScreen(ponder);ponder.setComfyReadingEnabled(false);});
        cue(185,"advance actual Ponder tutorial",()->ponder.seekToTime(180));cue(190,"verify Ponder scene",()->require("actual Hololith tutorial is displayed",Minecraft.getInstance().screen==ponder&&ponder.getActiveScene().getId().getNamespace().equals("hololith")&&ponder.getActiveScene().getCurrentTime()>=180));
        cue(193,"return to world",()->{if(Minecraft.getInstance().screen!=null)Minecraft.getInstance().screen.onClose();});cue(194,"prepare moving survey controls",()->interaction(TABLE,ItemStack.EMPTY));cue(195,"open table controls",()->use(TABLE));cue(197,"cycle area",()->press("Area:"));cue(198,"select 32-block moving survey",()->press("Area:"));cue(199,"restore 100-percent projection",()->press("Projection:"));cue(200,"apply moving survey settings",()->press("Apply"));
        cue(201,"take card to cart sensor",()->interaction(ASSEMBLER.offset(CART_SENSOR),Hololith.CARD.toStack()));cue(202,"record cart sensor",()->use(ASSEMBLER.offset(CART_SENSOR)));cue(204,"verify card and return to table",()->server(player->{var card=player.getMainHandItem().getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();require("normal card records the cart-mounted sensor",SurveyCardItem.isSensorCard(card)&&cartSensorId.equals(card.getUUID("SensorId")));near(player,TABLE);}));
        cue(206,"link moving source to powered table",()->use(TABLE));cue(208,"assemble real Create sensor cart",()->server(this::assembleCart));cue(209,"view moving source",this::beauty);
        cue(210,"push cart onto powered rails",()->server(player->{require("moving demonstration uses an actual linked physical source",cartSensorId.equals(table(player).sensorId())&&table(player).mode()==1);cart.setDeltaMovement(.12,0,0);previousCart=cart.position();moving=true;}));
        cue(230,"verify movement and stationary projection",()->server(player->{require("powered rails move the actual assembled sensor cart",cartTravel>16&&contraption!=null&&contraption.isAlive());require("stationary table stays genuinely powered during the moving survey",movingPowerSamples>=50&&powered(table(player)));require("moving sensor changes the actual terrain survey",movingSurveys.size()>=3);require("all four cart references remain visible in the moving survey",movingMarkerSamples>=35&&hasCartMarkers(table(player)));require("ordinary contraption demo carries sensors and markers, not an unpowered projection",contraption.getContraption().getBlocks().values().stream().noneMatch(block->block.state().is(Hololith.TABLE.get())));}));
        cue(234,"save reusable showcase world",()->server(player->player.server.saveEverything(false,true,true)));
    }

    void tick(){
        if(done)return;var mc=Minecraft.getInstance();
        try{
            if(System.nanoTime()-boot>720_000_000_000L)throw new IllegalStateException("Showcase exceeded its twelve-minute bound");
            if(mc.level==null||mc.player==null)return;ticks++;
            if(work.isCompletedExceptionally())work.join();if(!work.isDone())return;
            mc.options.keyAttack.setDown(false);mc.options.keyUse.setDown(false);mc.mouseHandler.releaseMouse();
            if(!built&&ticks>60){built=true;mc.options.fov().set(50);mc.options.hideGui=true;mc.options.bobView().set(false);HololithClientConfig.previewIntensity(1);HololithClientConfig.HOLOGRAM_FLICKER.set(true);server(this::build);return;}
            if(!built)return;
            if(recordingStart==0){
                cinematicCamera(0);
                var table=clientTable();
                if(table!=null&&table.isProjecting()&&table.snapshot()!=null&&table.snapshot().knownColumns()==1024&&table.markers().size()==4&&ticks>300){
                    require("real powered 32-block terrain and four references acquired before recording",true);
                    startEncoder(mc);recordingStart=System.nanoTime();
                }return;
            }
            double now=seconds();
            if(cueIndex<cues.size()&&now>=cues.get(cueIndex).second()){
                Cue cue=cues.get(cueIndex++);
                if(now-cue.second()>3)throw new IllegalStateException("Recording action fell behind its caption by over three seconds: "+cue.label());
                actions.add(Map.of("plannedSecond",cue.second(),"actualSecond",now,"action",cue.label()));cue.action().run();
            }
            if(cinematic&&mc.screen==null)cinematicCamera(now);
            if(now>=SECONDS){require("all planned recording interactions completed",cueIndex==cues.size());successful=true;finish();}
        }catch(Throwable failure){failure.printStackTrace();evidence.add(Map.of("check","showcase completed","passed",false,"error",failure.toString()));finish();}
    }
    private void cinematicCamera(double second){
        var mc=Minecraft.getInstance();Vec3 focus=Vec3.atCenterOf(TABLE).add(0,1.45,0);double distance=second<8?9:6,angle=.5+second*.012,height=3.1;
        if(second>=18&&second<35){focus=Vec3.atCenterOf(TABLE).add(0,-.2,0);distance=6.8;angle=1.05;height=1.8;}
        if(second>=210&&second<219&&mc.level.getEntity(cartId) instanceof AbstractContraptionEntity entity){focus=entity.toGlobalVector(Vec3.atCenterOf(CART_SENSOR),1).add(0,.5,0);distance=8;height=4;angle=.7;}
        Vec3 eye=focus.add(Math.sin(angle)*distance,height,Math.cos(angle)*distance);
        // A deliberately operated spectator-style creative camera, never ship pose or terrain substitution.
        if(cameraPosition==null||cameraPosition.distanceTo(eye)>18)cameraPosition=eye;else cameraPosition=cameraPosition.lerp(eye,.16);
        Vec3 delta=focus.subtract(cameraPosition);mc.player.setPos(cameraPosition.x,cameraPosition.y-mc.player.getEyeHeight(),cameraPosition.z);
        mc.player.setYRot((float)Math.toDegrees(Math.atan2(delta.z,delta.x))-90);mc.player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));
        mc.player.getAbilities().mayfly=true;mc.player.getAbilities().flying=true;mc.player.setDeltaMovement(Vec3.ZERO);
    }
    private void placeTable(ServerLevel level,BlockPos pos){for(int part=0;part<9;part++)level.setBlockAndUpdate(pos.offset(part%3-1,0,part/3-1),Hololith.TABLE.get().defaultBlockState().setValue(HololithTableBlock.PART,part));}
    private CompoundTag card(UUID id,BlockPos pos){var card=new CompoundTag();card.putUUID("SensorId",id);card.putString("Dimension","minecraft:overworld");card.putInt("X",pos.getX());card.putInt("Z",pos.getZ());return card;}
    private void build(ServerPlayer player){
        var level=player.serverLevel();player.setGameMode(GameType.CREATIVE);player.getInventory().clearContent();player.getInventory().selected=0;
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,player.server);level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false,player.server);level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
        for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++)level.getChunk(x,z);
        for(int x=-96;x<=96;x++)for(int z=-96;z<=96;z++){
            double hill=13*Math.exp(-((x+20)*(x+20)+(z+18)*(z+18))/180.0)+7*Math.exp(-((x-30)*(x-30)+(z-22)*(z-22))/160.0);
            int top=80+(int)hill;boolean river=Math.abs(z-3*Math.sin(x*.12))<2;if(river)top=78;
            for(int y=75;y<=top;y++)level.setBlock(new BlockPos(x,y,z),(y==top?(river?Blocks.SAND:Blocks.GRASS_BLOCK):y>top-3?Blocks.DIRT:Blocks.STONE).defaultBlockState(),2);
            if(river)for(int y=top+1;y<=80;y++)level.setBlock(new BlockPos(x,y,z),Blocks.WATER.defaultBlockState(),2);
        }
        for(int x:new int[]{-30,-22,-12,-5,5,15,28,36})for(int z:new int[]{-30,13,34}){
            int top=level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,x,z);
            for(int y=0;y<5;y++)level.setBlock(new BlockPos(x,top+y,z),Blocks.SPRUCE_LOG.defaultBlockState(),2);
            for(int y=2;y<7;y++)for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)if(Math.abs(dx)+Math.abs(dz)<(y<5?4:2))level.setBlock(new BlockPos(x+dx,top+y,z+dz),Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true),2);
        }
        for(int x=4;x<=11;x++)for(int z=-13;z<=-6;z++)for(int y=81;y<=87;y++)if(x==4||x==11||z==-13||z==-6||y==87)level.setBlock(new BlockPos(x,y,z),(y==87?Blocks.WAXED_CUT_COPPER:y==84&&(x+z)%3==0?Blocks.GLASS:Blocks.STONE_BRICKS).defaultBlockState(),2);
        for(int x=-7;x<=7;x++)for(int z=-6;z<=6;z++)level.setBlockAndUpdate(TABLE.offset(x,-3,z),(Math.abs(x)==7||Math.abs(z)==6?Blocks.WAXED_CUT_COPPER:Blocks.POLISHED_ANDESITE).defaultBlockState());
        for(int x:new int[]{-7,7})for(int z:new int[]{-6,6}){for(int y=-2;y<=1;y++)level.setBlockAndUpdate(TABLE.offset(x,y,z),Blocks.POLISHED_DEEPSLATE_WALL.defaultBlockState());level.setBlockAndUpdate(TABLE.offset(x,2,z),Blocks.SEA_LANTERN.defaultBlockState());}
        placeTable(level,TABLE);level.setBlockAndUpdate(TABLE.below(),AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS,Direction.Axis.Y));level.setBlockAndUpdate(TABLE.below(2),AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.UP));motor(player,32);
        level.setBlockAndUpdate(SENSOR.below(),Blocks.WAXED_CUT_COPPER.defaultBlockState());level.setBlockAndUpdate(SENSOR,Hololith.SENSOR.get().defaultBlockState());localId=((SurveySensorEntity)level.getBlockEntity(SENSOR)).sensorId();
        for(int y=91;y<BEACON.getY();y++)level.setBlockAndUpdate(new BlockPos(BEACON.getX(),y,BEACON.getZ()),Blocks.WAXED_CUT_COPPER.defaultBlockState());level.setBlockAndUpdate(BEACON,Hololith.BEACON.get().defaultBlockState());beaconId=((SurveySensorEntity)level.getBlockEntity(BEACON)).sensorId();
        var table=table(player);table.bind(card(localId,SENSOR));table.configure(32,0,0,1,1,"minecraft:overworld",0,0);
        for(int index=0;index<4;index++){
            BlockPos pos=PINS.get(index);level.setBlockAndUpdate(pos.below(),Blocks.POLISHED_ANDESITE.defaultBlockState());level.setBlockAndUpdate(pos,Hololith.MARKER.get().defaultBlockState().setValue(ReferenceMarkerBlock.COLOR,COLORS[index]));
            var marker=(ReferenceMarkerEntity)level.getBlockEntity(pos);var target=new CompoundTag();target.putUUID("TableId",table.tableId());marker.bind(target);
        }
        buildRailLoop(level);
        for(int x=0;x<=4;x++)for(int z=-2;z<=2;z++)level.setBlockAndUpdate(ASSEMBLER.offset(x,1,z),Blocks.WAXED_CUT_COPPER.defaultBlockState());
        level.setBlockAndUpdate(ASSEMBLER.offset(CART_SENSOR),Hololith.SENSOR.get().defaultBlockState());cartSensorId=((SurveySensorEntity)level.getBlockEntity(ASSEMBLER.offset(CART_SENSOR))).sensorId();
        for(int index=0;index<4;index++){
            BlockPos pos=ASSEMBLER.offset(index%2*4,1,index/2*4-2);level.setBlockAndUpdate(pos,Hololith.MARKER.get().defaultBlockState().setValue(ReferenceMarkerBlock.COLOR,COLORS[index]));var marker=(ReferenceMarkerEntity)level.getBlockEntity(pos);marker.bind(card(cartSensorId,ASSEMBLER.offset(CART_SENSOR)));cartMarkerIds.add(marker.markerId());
        }
        level.addFreshEntity(new SuperGlueEntity(level,SuperGlueEntity.span(ASSEMBLER.offset(0,1,-2),ASSEMBLER.offset(4,1,2))));
        look(player,Vec3.atCenterOf(TABLE).add(5,5,8),Vec3.atCenterOf(TABLE).add(0,1.4,0));player.server.saveEverything(false,true,true);
    }
    private void buildRailLoop(ServerLevel level){
        // The second pass fixes the intended rail shapes after all neighboring
        // rails exist; initial placement's automatic shape selection is provisional.
        for(int pass=0;pass<2;pass++)for(int x=-8;x<=16;x++)for(int z=25;z<=41;z++)if(x==-8||x==16||z==25||z==41){
            BlockPos pos=new BlockPos(x,ASSEMBLER.getY(),z);level.setBlockAndUpdate(pos.below(),Blocks.REDSTONE_BLOCK.defaultBlockState());
            RailShape shape=z==25||z==41?RailShape.EAST_WEST:RailShape.NORTH_SOUTH;
            if(x==-8&&z==25)shape=RailShape.SOUTH_EAST;if(x==16&&z==25)shape=RailShape.SOUTH_WEST;if(x==16&&z==41)shape=RailShape.NORTH_WEST;if(x==-8&&z==41)shape=RailShape.NORTH_EAST;
            boolean corner=(x==-8||x==16)&&(z==25||z==41);
            level.setBlock(pos,corner?Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE,shape):Blocks.POWERED_RAIL.defaultBlockState().setValue(PoweredRailBlock.SHAPE,shape).setValue(PoweredRailBlock.POWERED,true),2);
        }
        level.setBlockAndUpdate(ASSEMBLER,AllBlocks.CART_ASSEMBLER.getDefaultState().setValue(CartAssemblerBlock.RAIL_SHAPE,RailShape.EAST_WEST).setValue(CartAssemblerBlock.RAIL_TYPE,CartAssembleRailType.REGULAR).setValue(CartAssemblerBlock.POWERED,true));
    }
    private void assembleCart(ServerPlayer player){
        cart=Objects.requireNonNull(EntityType.MINECART.create(player.serverLevel()));cart.setPos(ASSEMBLER.getX()+.5,ASSEMBLER.getY()+.0625,ASSEMBLER.getZ()+.5);player.serverLevel().addFreshEntity(cart);
        ((CartAssemblerBlockEntity)player.serverLevel().getBlockEntity(ASSEMBLER)).tryAssemble(cart);contraption=(AbstractContraptionEntity)Objects.requireNonNull(cart.getFirstPassenger());cartId=contraption.getId();
        require("real Create assembler captures the sensor and four reference blocks",contraption.getContraption().getBlocks().values().stream().filter(block->block.state().is(Hololith.SENSOR.get())).count()==1&&contraption.getContraption().getBlocks().values().stream().filter(block->block.state().is(Hololith.MARKER.get())).count()==4);
        player.serverLevel().setDayTime(6000);
    }
    private void startEncoder(Minecraft mc)throws IOException{
        String ffmpeg=System.getProperty("hololith.showcase.ffmpeg","");if(ffmpeg.isBlank()||!Files.isRegularFile(Path.of(ffmpeg)))throw new IOException("Supply a trusted local FFmpeg binary");
        Files.createDirectories(Path.of("showcase"));int width=mc.getMainRenderTarget().width,height=mc.getMainRenderTarget().height;rgb=new byte[width*height*3];
        encoder=new ProcessBuilder(ffmpeg,"-hide_banner","-loglevel","warning","-y","-f","rawvideo","-pixel_format","rgb24","-video_size",width+"x"+height,"-framerate",Integer.toString(FPS),"-i","pipe:0","-an","-c:v","libx264","-preset","veryfast","-crf","18","-pix_fmt","yuv420p","-movflags","+faststart","showcase/gameplay-raw.mp4").redirectError(Path.of("showcase/encoder.log").toFile()).start();
        video=new BufferedOutputStream(encoder.getOutputStream(),rgb.length);
    }
    private void capture(RenderFrameEvent.Post event){
        if(done||recordingStart==0||video==null)return;int due=Math.min(SECONDS*FPS,(int)(seconds()*FPS));if(due<=frames)return;
        try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){
            int[] pixels=image.getPixelsRGBA();if(pixels.length*3!=rgb.length)throw new IOException("Window resized during capture");
            for(int index=0,byteIndex=0;index<pixels.length;index++){int color=pixels[index];rgb[byteIndex++]=(byte)color;rgb[byteIndex++]=(byte)(color>>>8);rgb[byteIndex++]=(byte)(color>>>16);}
            if(due-frames>1)repeatedFrames+=due-frames-1;
            while(frames<due){video.write(rgb);frames++;}
        }catch(Exception failure){failure.printStackTrace();evidence.add(Map.of("check","frame capture","passed",false,"error",failure.toString()));finish();}
    }
    private void finish(){
        if(done)return;done=true;moving=false;
        try{
            if(video!=null){
                // At successful completion at most one render interval remains.
                // A failed run is kept short; never pad a failed demonstration to four minutes.
                if(successful)while(frames<SECONDS*FPS){video.write(rgb);frames++;}
                video.close();if(!encoder.waitFor(30,TimeUnit.SECONDS)||encoder.exitValue()!=0)throw new IOException("Encoder failed");
            }
        }catch(Exception failure){successful=false;failure.printStackTrace();evidence.add(Map.of("check","encoder completed","passed",false,"error",failure.toString()));}
        try{
            var report=new LinkedHashMap<String,Object>();report.put("capture","Actual Minecraft framebuffer at 30 fps. Authored creative terrain and camera, normal item-use packets, actual Hololith controls and actual Create Ponder. No generated gameplay.");
            report.put("version",net.neoforged.fml.ModList.get().getModContainerById("hololith").orElseThrow().getModInfo().getVersion().toString());report.put("profile","showcase");report.put("completed",successful);report.put("frames",frames);report.put("repeatedFrames",repeatedFrames);report.put("durationSeconds",frames/(double)FPS);report.put("plannedSeconds",SECONDS);report.put("checks",evidence);report.put("actions",actions);report.put("cartTravelBlocks",cartTravel);report.put("movingSurveyRegions",movingSurveys.size());report.put("movingPowerSamples",movingPowerSamples);report.put("movingMarkerSamples",movingMarkerSamples);
            report.put("scope","Create-only creative showcase. Real Creative Motor at 32 RPM supplies the stationary table through a shaft. Normal generator settings demonstrate power loss and recovery. A real cart assembler carries only a sensor and reference blocks on powered rails, given one initial push; no continuous velocity or pose injection. No moving-table power or Aeronautics-flight claim. Ponder is visibly presented as an in-game tutorial. Silent footage.");report.put("timestamp",java.time.Instant.now().toString());Files.createDirectories(Path.of("showcase"));Files.writeString(Path.of("showcase/capture.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
        }catch(Exception failure){failure.printStackTrace();}
        Minecraft.getInstance().execute(Minecraft.getInstance()::stop);
    }
}
