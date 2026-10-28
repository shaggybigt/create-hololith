package dev.hololith.probe;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.hololith.Hololith;
import dev.hololith.HololithTableBlock;
import dev.hololith.HololithTableEntity;
import dev.hololith.ReferenceMarkerBlock;
import dev.hololith.ReferenceMarkerEntity;
import dev.hololith.SurveyFeedback;
import dev.hololith.SurveySensorEntity;
import dev.hololith.compat.jade.HololithJadeSnapshot;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/** Real recipe, placement, loot and optional Jade acceptance in a bounded disposable fixture. */
final class IntegrationChecks {
    private final BlockPos tablePos, markerPos, sensorPos, beaconPos, driverPos;
    private final BiConsumer<String, Boolean> check;
    private final boolean jade=ModList.get().isLoaded("jade");
    private CompletableFuture<Void> work=CompletableFuture.completedFuture(null);
    private UUID playerId,expectedMarkerTarget;
    private Vec3 oldPosition;
    private float oldYaw,oldPitch;
    private ItemStack oldHand;
    private boolean oldHideGui,oldFlying,done;
    private int stage,ticks,phaseTick,hudStableTicks,cameraAttempts,lastCameraAttempt;

    IntegrationChecks(BlockPos clearRegion,BiConsumer<String,Boolean> check){
        tablePos=clearRegion.immutable();markerPos=tablePos.east(3);sensorPos=tablePos.west(3);
        beaconPos=tablePos.north(3);driverPos=tablePos.below(2);this.check=check;
    }

    private void record(String text,boolean passed){check.accept(text,passed);}
    private void next(){stage++;phaseTick=ticks;hudStableTicks=0;cameraAttempts=0;lastCameraAttempt=ticks;}
    private boolean waited(int delay){return ticks-phaseTick>=delay;}
    private void server(Consumer<ServerPlayer> action){
        var server=Objects.requireNonNull(Minecraft.getInstance().getSingleplayerServer());
        work=CompletableFuture.runAsync(()->action.accept(Objects.requireNonNull(server.getPlayerList().getPlayer(playerId))),server);
    }
    private HololithTableEntity table(ServerPlayer player){
        return (HololithTableEntity)Objects.requireNonNull(player.serverLevel().getBlockEntity(tablePos));
    }
    private CompoundTag snapshot(ServerPlayer player,BlockPos pos){
        var level=player.serverLevel();
        return HololithJadeSnapshot.capture(level,pos,level.getBlockState(pos),level.getBlockEntity(pos));
    }
    private static ItemStack item(String id){
        return new ItemStack(BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id))
                .orElseThrow(()->new IllegalArgumentException("Missing recipe ingredient "+id)));
    }
    private CraftingInput pattern(String[] rows,Map<Character,String> ingredients){
        List<ItemStack> input=new ArrayList<>();
        for(String row:rows)for(char symbol:row.toCharArray())input.add(symbol==' '?ItemStack.EMPTY:item(ingredients.get(symbol)));
        return CraftingInput.of(rows[0].length(),rows.length,input);
    }
    private ItemStack craft(ServerPlayer player,String name,CraftingInput input,int count,String result){
        var level=player.serverLevel();var manager=level.getRecipeManager();
        var match=manager.getRecipeFor(RecipeType.CRAFTING,input,level);
        record("loaded recipe matches real ingredients: "+name,match.isPresent()&&match.get().id().equals(Hololith.id(name)));
        if(match.isEmpty())return ItemStack.EMPTY;
        ItemStack crafted=match.get().value().assemble(input,level.registryAccess());
        record("loaded recipe crafts expected result: "+name,crafted.is(item(result).getItem())&&crafted.getCount()==count);
        return crafted;
    }
    private void recipes(ServerPlayer player){
        var manager=player.serverLevel().getRecipeManager();
        List<String> expected=new ArrayList<>(List.of("surveying_table","local_sensor","remote_beacon","survey_card","reference_marker"));
        for(DyeColor color:DyeColor.values())expected.add("reference_marker_"+color.getName());
        record("all 21 Hololith recipes resolve in the live RecipeManager",expected.size()==21
                &&expected.stream().allMatch(id->manager.byKey(Hololith.id(id)).isPresent())
                &&manager.getRecipes().stream().filter(recipe->recipe.id().getNamespace().equals("hololith")).count()==21);
        craft(player,"surveying_table",pattern(new String[]{"BLB","AMA","CSC"},Map.of(
                'B',"create:brass_ingot",'L',"minecraft:amethyst_shard",'A',"create:andesite_casing",
                'M',"create:precision_mechanism",'C',"create:copper_sheet",'S',"create:shaft")),1,"hololith:surveying_table");
        craft(player,"local_sensor",pattern(new String[]{" A ","CEC"," G "},Map.of(
                'A',"create:andesite_alloy",'C',"minecraft:copper_ingot",'E',"create:electron_tube",'G',"minecraft:glass")),1,"hololith:local_sensor");
        craft(player,"remote_beacon",pattern(new String[]{" T ","GSG","ABA"},Map.of(
                'T',"create:transmitter",'G',"minecraft:glass",'S',"hololith:local_sensor",'A',"create:andesite_alloy",'B',"create:brass_ingot")),1,"hololith:remote_beacon");
        craft(player,"reference_marker",pattern(new String[]{" B ","ARA"," C "},Map.of(
                'B',"create:brass_nugget",'A',"minecraft:amethyst_shard",'R',"minecraft:redstone",'C',"create:copper_sheet")),4,"hololith:reference_marker");
        craft(player,"survey_card",CraftingInput.of(3,1,List.of(item("minecraft:paper"),item("create:brass_nugget"),item("minecraft:redstone"))),1,"hololith:survey_card");
        // A negative case proves the RecipeManager is evaluating ingredients, not just IDs.
        record("missing mechanism prevents crafting a table",manager.getRecipeFor(RecipeType.CRAFTING,
                pattern(new String[]{"BLB","A A","CSC"},Map.of('B',"create:brass_ingot",'L',"minecraft:amethyst_shard",
                        'A',"create:andesite_casing",'C',"create:copper_sheet",'S',"create:shaft")),player.serverLevel()).isEmpty());
    }
    private boolean place(ServerPlayer player,ItemStack stack,DyeColor color){
        var level=player.serverLevel();level.removeBlock(markerPos,false);
        player.connection.teleport(markerPos.getX()+.5,markerPos.getY()+1,markerPos.getZ()+2.5,180,45);
        player.setItemInHand(InteractionHand.MAIN_HAND,stack.copy());
        var result=player.gameMode.useItemOn(player,level,player.getMainHandItem(),InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(markerPos.below()).add(0,.5,0),Direction.UP,markerPos.below(),false));
        var state=level.getBlockState(markerPos);
        return result.consumesAction()&&state.is(Hololith.MARKER.get())&&state.getValue(ReferenceMarkerBlock.COLOR)==color;
    }
    private void markers(ServerPlayer player){
        var level=player.serverLevel();var table=table(player);
        UUID sensor=((SurveySensorEntity)Objects.requireNonNull(level.getBlockEntity(sensorPos))).sensorId();
        for(DyeColor color:DyeColor.values()){
            ItemStack crafted=craft(player,"reference_marker_"+color.getName(),CraftingInput.of(2,1,
                    List.of(item("hololith:reference_marker"),item("minecraft:"+color.getName()+"_dye"))),1,"hololith:reference_marker");
            record("crafted "+color.getName()+" marker carries its block-state component",crafted.getOrDefault(
                    DataComponents.BLOCK_STATE,BlockItemStateProperties.EMPTY).get(ReferenceMarkerBlock.COLOR)==color);
            boolean placed=place(player,crafted,color);
            record("normal item interaction places crafted "+color.getName()+" marker color",placed);
            if(!placed)continue;
            var drops=Block.getDrops(level.getBlockState(markerPos),level,markerPos,level.getBlockEntity(markerPos),player,ItemStack.EMPTY);
            ItemStack drop=drops.stream().filter(stack->stack.is(Hololith.MARKER_ITEM.get())).findFirst().orElse(ItemStack.EMPTY);
            record("loaded loot table retains "+color.getName()+" marker color",drop.getOrDefault(
                    DataComponents.BLOCK_STATE,BlockItemStateProperties.EMPTY).get(ReferenceMarkerBlock.COLOR)==color);
            record("normal item interaction restores dropped "+color.getName()+" marker color",place(player,drop,color));
            var marker=(ReferenceMarkerEntity)Objects.requireNonNull(level.getBlockEntity(markerPos));
            CompoundTag data=snapshot(player,markerPos);
            record("Jade snapshot identifies unlinked "+color.getName()+" marker",data.getBoolean("Available")
                    &&data.getString("Kind").equals("marker")&&data.getString("Color").equals(color.getName())&&!data.getBoolean("Linked"));
            CompoundTag card=new CompoundTag();card.putUUID("TableId",table.tableId());marker.bind(card);data=snapshot(player,markerPos);
            record("Jade snapshot identifies table-linked "+color.getName()+" marker",data.getBoolean("Linked")
                    &&data.getInt("TargetKind")==0&&data.getString("Target").equals(SurveyFeedback.shortId(table.tableId())));
            card=new CompoundTag();card.putUUID("SensorId",sensor);marker.bind(card);data=snapshot(player,markerPos);
            record("Jade snapshot identifies sensor-linked "+color.getName()+" marker",data.getBoolean("Linked")
                    &&data.getInt("TargetKind")==1&&data.getString("Target").equals(SurveyFeedback.shortId(sensor)));
            marker.unlink();data=snapshot(player,markerPos);
            record("Jade snapshot clears the "+color.getName()+" marker link",!data.getBoolean("Linked")&&!data.contains("Target"));
            compact(data,"marker "+color.getName());
        }
        ItemStack display=item("hololith:reference_marker");
        display.set(DataComponents.BLOCK_STATE,BlockItemStateProperties.EMPTY.with(ReferenceMarkerBlock.COLOR,DyeColor.RED));
        if(place(player,display,DyeColor.RED)){
            expectedMarkerTarget=table.tableId();
            CompoundTag card=new CompoundTag();card.putUUID("TableId",table.tableId());
            ((ReferenceMarkerEntity)Objects.requireNonNull(level.getBlockEntity(markerPos))).bind(card);
        }
    }
    private void sources(ServerPlayer player){
        var level=player.serverLevel();var table=table(player);
        for(BlockPos pos:List.of(sensorPos,beaconPos)){
            var sensor=(SurveySensorEntity)Objects.requireNonNull(level.getBlockEntity(pos));var data=snapshot(player,pos);
            record("Jade snapshot identifies "+(pos.equals(beaconPos)?"remote beacon":"local sensor"),data.getBoolean("Available")
                    &&data.getString("Kind").equals(pos.equals(beaconPos)?"beacon":"sensor")
                    &&data.getString("Sensor").equals(SurveyFeedback.shortId(sensor.sensorId())));
            CompoundTag card=new CompoundTag();card.putUUID("SensorId",sensor.sensorId());
            card.putString("Dimension",level.dimension().location().toString());card.putInt("X",pos.getX());card.putInt("Z",pos.getZ());
            table.bind(card);var linked=snapshot(player,tablePos);
            record("Jade table source matches linked "+data.getString("Kind"),linked.getInt("Mode")==1&&linked.getString("Sensor").equals(data.getString("Sensor")));
            compact(data,data.getString("Kind"));
        }
        UUID linked=table.sensorId();table.configure(32,0,0,1,2,"minecraft:overworld",-123,456);
        record("unsupported coordinate configuration preserves the physical source",table.mode()==1&&Objects.equals(table.sensorId(),linked));
        table.configure(32,0,0,1,0,"minecraft:overworld",tablePos.getX(),tablePos.getZ());
        var local=snapshot(player,tablePos);
        record("Jade table source reports local physical mode without target coordinates",local.getInt("Mode")==0&&!local.contains("X")&&!local.contains("Z"));
    }
    private void compact(CompoundTag data,String label){
        try{
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();NbtIo.write(data,new DataOutputStream(bytes));
            boolean scalar=data.getAllKeys().stream().map(data::get).allMatch(tag->tag!=null&&tag.getId()!=Tag.TAG_COMPOUND
                    &&tag.getId()!=Tag.TAG_LIST&&tag.getId()!=Tag.TAG_BYTE_ARRAY&&tag.getId()!=Tag.TAG_INT_ARRAY&&tag.getId()!=Tag.TAG_LONG_ARRAY);
            record("Jade "+label+" payload is bounded scalar data without terrain",bytes.size()<2048&&data.size()<24&&scalar
                    &&!data.contains("Survey")&&!data.contains("Palette")&&!data.contains("Voxels"));
        }catch(Exception error){throw new RuntimeException(error);}
    }
    private void tableSnapshots(ServerPlayer player,boolean powered){
        CompoundTag center=snapshot(player,tablePos);var table=table(player);
        record("Jade snapshot reports real "+(powered?"running":"unpowered")+" shaft state",center.getBoolean("Available")
                &&center.getBoolean("Projecting")==powered&&center.getString("PowerStatus").equals(powered?"power.hololith.running":"power.hololith.unpowered")
                &&center.getInt("RequiredRpm")==16&&Math.abs(center.getFloat("ActualRpm")-(powered?16:0))<.01f);
        record("Jade snapshot reports the active map and source",center.getInt("Span")==32&&center.getInt("Magnification")==100
                &&center.getInt("Mode")==0&&center.getString("Status").equals(table.status()));
        boolean identical=true;
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)identical&=center.equals(snapshot(player,tablePos.offset(x,0,z)));
        record("Jade resolves center plus eight table tiles while "+(powered?"running":"unpowered"),identical);
        compact(center,powered?"running table":"unpowered table");
        if(jade)registrationServer(player);
    }
    private void motor(ServerPlayer player,int rpm){
        var level=player.serverLevel();
        if(!(level.getBlockEntity(driverPos) instanceof CreativeMotorBlockEntity)){
            level.setBlockAndUpdate(driverPos,AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.UP));
        }
        ((CreativeMotorBlockEntity)Objects.requireNonNull(level.getBlockEntity(driverPos))).generatedSpeed.setValue(rpm);
    }
    private void camera(ServerPlayer player,BlockPos target){
        Vec3 position=Vec3.atCenterOf(target).add(0,1.1,3.2);
        Vec3 aim=Vec3.atCenterOf(target).add(0,target.equals(markerPos)?-.25:.45,0);
        Vec3 delta=aim.subtract(position.add(0,player.getEyeHeight(),0));
        float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
        float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z)));
        // Placement teleports can leave client momentum or disable flight on landing.
        // Use a real stable observer pose; never replace Minecraft/Jade's ray result.
        player.getAbilities().flying=true;player.onUpdateAbilities();
        player.setDeltaMovement(Vec3.ZERO);player.fallDistance=0;player.setOnGround(false);
        player.connection.teleport(position.x,position.y,position.z,yaw,pitch);
        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
    }
    private void screenshot(String name){
        var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),text->System.out.println(text.getString()));
    }
    private boolean rayTargetsInstrument(boolean marker){
        var mc=Minecraft.getInstance();
        if(mc.level==null||!(mc.hitResult instanceof BlockHitResult hit)||hit.getType()!=HitResult.Type.BLOCK)return false;
        BlockPos pos=hit.getBlockPos();
        if(marker)return pos.equals(markerPos)&&mc.level.getBlockState(pos).is(Hololith.MARKER.get());
        var state=mc.level.getBlockState(pos);
        return state.is(Hololith.TABLE.get())&&HololithTableBlock.anchor(pos,state).equals(tablePos);
    }
    /** Require a stable real ray and HUD response, with bounded observer-camera recovery. */
    private boolean awaitOverlay(boolean marker,boolean powered,int minimumTicks,int maximumTicks){
        boolean target=rayTargetsInstrument(marker);
        if(target&&overlay(marker,powered))hudStableTicks++;else hudStableTicks=0;
        if(!target&&waited(minimumTicks)&&!waited(maximumTicks-20)&&cameraAttempts<3&&ticks-lastCameraAttempt>=30){
            cameraAttempts++;lastCameraAttempt=ticks;
            server(player->camera(player,marker?markerPos:tablePos));
        }
        if(!waited(minimumTicks))return false;
        if(hudStableTicks>=8)return true;
        if(!waited(maximumTicks))return false;
        var mc=Minecraft.getInstance();
        System.out.println("HOLOLITH JADE: HUD deadline at stage "+stage+", ray="+mc.hitResult
                +", eye="+(mc.player==null?null:mc.player.getEyePosition())+", stable="+hudStableTicks+", camera retries="+cameraAttempts);
        return true;
    }
    private boolean overlayReady(boolean marker,boolean powered){return hudStableTicks>=8&&rayTargetsInstrument(marker)&&overlay(marker,powered);}

    // No Jade type occurs in this class's signatures or constant-pool class refs.
    // Reflection is only reached after ModList confirms the optional mod is loaded.
    private void registrationServer(ServerPlayer player){
        try{
            Class<?> type=Class.forName("snownee.jade.impl.WailaCommonRegistration");
            Object registration=type.getMethod("instance").invoke(null);
            for(BlockPos pos:List.of(tablePos,tablePos.east(),sensorPos,beaconPos,markerPos)){
                var providers=(List<?>)type.getMethod("getBlockNBTProviders",Block.class,BlockEntity.class).invoke(registration,
                        player.serverLevel().getBlockState(pos).getBlock(),player.serverLevel().getBlockEntity(pos));
                record("Jade registered authoritative provider for "+pos.subtract(tablePos),providers.stream()
                        .filter(provider->provider.getClass().getName().equals("dev.hololith.compat.jade.HololithJadeServerData")).count()==1);
            }
        }catch(ReflectiveOperationException error){throw new RuntimeException("Jade common registration inspection failed",error);}
    }
    private void registrationClient(){
        try{
            Class<?> type=Class.forName("snownee.jade.impl.WailaClientRegistration");Object registration=type.getMethod("instance").invoke(null);
            for(Block block:List.of(Hololith.TABLE.get(),Hololith.SENSOR.get(),Hololith.BEACON.get(),Hololith.MARKER.get())){
                var providers=(List<?>)type.getMethod("getBlockProviders",Block.class,Predicate.class)
                        .invoke(registration,block,(Predicate<Object>)provider->true);
                record("Jade registered tooltip provider for "+BuiltInRegistries.BLOCK.getKey(block),providers.stream()
                        .filter(provider->provider.getClass().getName().equals("dev.hololith.compat.jade.HololithJadeTooltip")).count()==1);
            }
        }catch(ReflectiveOperationException error){throw new RuntimeException("Jade client registration inspection failed",error);}
    }
    private boolean overlay(boolean marker,boolean powered){
        try{
            Class<?> center=Class.forName("snownee.jade.impl.ObjectDataCenter");Object accessor=center.getMethod("get").invoke(null);
            if(accessor==null)return false;
            Class<?> blockAccessor=Class.forName("snownee.jade.api.BlockAccessor");if(!blockAccessor.isInstance(accessor))return false;
            BlockPos pos=(BlockPos)blockAccessor.getMethod("getPosition").invoke(accessor);
            var state=Objects.requireNonNull(Minecraft.getInstance().level).getBlockState(pos);
            if(marker?!pos.equals(markerPos):!state.is(Hololith.TABLE.get())||!HololithTableBlock.anchor(pos,state).equals(tablePos))return false;
            CompoundTag envelope=(CompoundTag)center.getMethod("getServerData").invoke(null);
            if(envelope==null||!envelope.contains(HololithJadeSnapshot.DATA_KEY,Tag.TAG_COMPOUND))return false;
            CompoundTag data=envelope.getCompound(HololithJadeSnapshot.DATA_KEY);
            if(marker?(!data.getBoolean("Available")||!data.getString("Kind").equals("marker")
                    ||!data.getString("Color").equals("red")||!data.getBoolean("Linked")||data.getInt("TargetKind")!=0
                    ||expectedMarkerTarget==null||!data.getString("Target").equals(SurveyFeedback.shortId(expectedMarkerTarget))):
                    data.getBoolean("Projecting")!=powered||!data.getString("PowerStatus").equals(powered?"power.hololith.running":"power.hololith.unpowered"))return false;
            Class<?> handler=Class.forName("snownee.jade.overlay.WailaTickHandler");Object instance=handler.getMethod("instance").invoke(null);
            Object box=handler.getField("rootElement").get(instance);if(box==null)return false;
            Object tooltip=box.getClass().getMethod("getTooltip").invoke(box);
            String message=(String)tooltip.getClass().getMethod("getMessage",ResourceLocation.class).invoke(tooltip,Hololith.id("instrument"));
            if(message==null)return false;
            String expected=marker?Component.translatable("jade.hololith.color",Component.translatable("color.hololith.red")).getString():
                    Component.translatable("jade.hololith.power",Component.translatable(powered?"power.hololith.running":"power.hololith.unpowered")).getString();
            if(marker&&!message.contains(Component.translatable("jade.hololith.linked",
                    Component.translatable("message.hololith.target_table"),SurveyFeedback.shortId(expectedMarkerTarget)).getString()))return false;
            return message.contains(expected)&&Class.forName("snownee.jade.overlay.OverlayRenderer").getField("shown").getBoolean(null);
        }catch(ReflectiveOperationException error){throw new RuntimeException("Jade real overlay inspection failed",error);}
    }
    private void restore(ServerPlayer player){
        if(player.serverLevel().getBlockEntity(driverPos) instanceof CreativeMotorBlockEntity)motor(player,0);
        if(oldHand!=null)player.setItemInHand(InteractionHand.MAIN_HAND,oldHand);
        player.getAbilities().flying=oldFlying;player.onUpdateAbilities();
        if(oldPosition!=null)player.connection.teleport(oldPosition.x,oldPosition.y,oldPosition.z,oldYaw,oldPitch);
    }
    boolean tick(){
        if(done)return true;if(!work.isDone())return false;ticks++;
        var mc=Minecraft.getInstance();
        try{
            work.join();if(ticks>1000)throw new IllegalStateException("Integration fixture timed out at stage "+stage);
            // HUD acceptance observes the real world, never a stale instrument screen.
            if(stage<=3){if(mc.screen!=null)mc.screen.onClose();mc.mouseHandler.releaseMouse();}
            switch(stage){
                case 0->{
                    playerId=Objects.requireNonNull(mc.player).getUUID();oldHideGui=mc.options.hideGui;mc.options.hideGui=false;
                    if(jade)registrationClient();
                    server(player->{
                        oldPosition=player.position();oldYaw=player.getYRot();oldPitch=player.getXRot();oldHand=player.getMainHandItem().copy();
                        oldFlying=player.getAbilities().flying;player.getAbilities().flying=true;player.onUpdateAbilities();
                        var level=player.serverLevel();
                        for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++){
                            level.setBlockAndUpdate(tablePos.offset(x,-3,z),Blocks.STONE.defaultBlockState());
                            for(int y=-2;y<=4;y++)level.setBlockAndUpdate(tablePos.offset(x,y,z),y==-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
                        }
                        var state=Hololith.TABLE.get().defaultBlockState();level.setBlockAndUpdate(tablePos,state);
                        Hololith.TABLE.get().setPlacedBy(level,tablePos,state,player,Hololith.TABLE_ITEM.toStack());
                        level.setBlockAndUpdate(tablePos.below(),AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS,Direction.Axis.Y));
                        level.setBlockAndUpdate(sensorPos,Hololith.SENSOR.get().defaultBlockState());
                        level.setBlockAndUpdate(beaconPos,Hololith.BEACON.get().defaultBlockState());
                        recipes(player);sources(player);markers(player);camera(player,tablePos);
                    });next();
                }
                case 1->{
                    if(jade?!awaitOverlay(false,false,40,180):!waited(40))return false;
                    if(jade){record("real Jade overlay displays unpowered table data",overlayReady(false,false));screenshot("hololith-jade-unpowered.png");}
                    server(player->{tableSnapshots(player,false);motor(player,16);});next();
                }
                case 2->{
                    if(jade?!awaitOverlay(false,true,100,220):!waited(100))return false;
                    if(jade){record("real Jade overlay receives running shaft state",overlayReady(false,true));screenshot("hololith-jade-powered.png");}
                    server(player->{tableSnapshots(player,true);camera(player,markerPos);});next();
                }
                case 3->{
                    if(jade?!awaitOverlay(true,true,40,180):!waited(40))return false;
                    if(jade){record("real Jade overlay displays linked red marker",overlayReady(true,true));screenshot("hololith-jade-reference.png");}
                    else record("recipe and instrument integration fixture runs with Jade absent",true);
                    server(this::restore);next();
                }
                case 4->{mc.options.hideGui=oldHideGui;done=true;}
                default->throw new IllegalStateException("Unexpected integration stage "+stage);
            }
        }catch(Throwable failure){
            failure.printStackTrace();record("recipe and Jade fixture completes without error (stage "+stage+")",false);
            mc.options.hideGui=oldHideGui;done=true;try{server(this::restore);}catch(Throwable cleanup){cleanup.printStackTrace();}
        }
        return done;
    }
}
