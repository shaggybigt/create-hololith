package dev.hololith;

import com.mojang.logging.LogUtils;
import dev.hololith.survey.SurveyManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.registries.*;
import org.slf4j.Logger;

@Mod(Hololith.ID)
public final class Hololith {
    public static final String ID="hololith";
    public static final Logger LOGGER=LogUtils.getLogger();
    public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(ID);
    public static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(ID);
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,ID);
    public static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,ID);
    public static final DeferredBlock<HololithTableBlock> TABLE=BLOCKS.register("surveying_table",()->new HololithTableBlock(BlockBehaviour.Properties.of().strength(3.5f).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion().lightLevel(s->3)));
    public static final DeferredBlock<SurveySensorBlock> SENSOR=BLOCKS.register("local_sensor",()->new SurveySensorBlock(BlockBehaviour.Properties.of().strength(2.5f).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion().lightLevel(s->3)));
    public static final DeferredBlock<SurveySensorBlock> BEACON=BLOCKS.register("remote_beacon",()->new SurveySensorBlock(BlockBehaviour.Properties.of().strength(2.5f).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion().lightLevel(s->6)));
    public static final DeferredBlock<ReferenceMarkerBlock> MARKER=BLOCKS.register("reference_marker",()->new ReferenceMarkerBlock(BlockBehaviour.Properties.of().strength(1.5f).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion().lightLevel(s->5)));
    public static final DeferredItem<BlockItem> MARKER_ITEM=ITEMS.registerSimpleBlockItem(MARKER);
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<ReferenceMarkerEntity>> MARKER_ENTITY=ENTITIES.register("reference_marker",()->BlockEntityType.Builder.of(ReferenceMarkerEntity::new,MARKER.get()).build(null));
    public static final DeferredItem<BlockItem> TABLE_ITEM=ITEMS.registerSimpleBlockItem(TABLE);
    public static final DeferredItem<BlockItem> SENSOR_ITEM=ITEMS.registerSimpleBlockItem(SENSOR);
    public static final DeferredItem<BlockItem> BEACON_ITEM=ITEMS.registerSimpleBlockItem(BEACON);
    public static final DeferredItem<SurveyCardItem> CARD=ITEMS.register("survey_card",()->new SurveyCardItem(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<HololithTableEntity>> TABLE_ENTITY=ENTITIES.register("surveying_table",()->BlockEntityType.Builder.of(HololithTableEntity::new,TABLE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<SurveySensorEntity>> SENSOR_ENTITY=ENTITIES.register("survey_sensor",()->BlockEntityType.Builder.of(SurveySensorEntity::new,SENSOR.get(),BEACON.get()).build(null));
    static {
        TABS.register("instruments",()->CreativeModeTab.builder().title(Component.literal("Create: Hololith")).icon(()->TABLE_ITEM.toStack()).displayItems((p,o)->{o.accept(TABLE_ITEM);o.accept(SENSOR_ITEM);o.accept(BEACON_ITEM);o.accept(CARD);o.accept(MARKER_ITEM);}).build());
    }
    public Hololith(IEventBus bus, ModContainer container) {
        BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); TABS.register(bus);
        container.registerConfig(ModConfig.Type.SERVER,HololithConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT,dev.hololith.client.HololithClientConfig.SPEC);
        bus.addListener(HololithNetwork::register);
        bus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent e)->e.enqueueWork(()->{
            dev.hololith.compat.ContraptionSurveys.register();
            dev.hololith.compat.ContraptionInteractions.register();
        }));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e)->{SurveyManager.get(e.getServer()).tick();dev.hololith.survey.ReferenceRegistry.get(e.getServer()).tick(e.getServer());});
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e)->{SurveyManager.clear(e.getServer());dev.hololith.survey.ReferenceRegistry.clear(e.getServer());});
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock e)->{
            var player=e.getEntity();var level=e.getLevel();var state=level.getBlockState(e.getPos());
            if(!player.isShiftKeyDown()||player.isSpectator()||!player.mayBuild()||(!state.is(TABLE.get())&&!state.is(MARKER.get()))||!net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(e.getItemStack().getItem()).toString().equals("create:wrench"))return;
            if(!level.mayInteract(player,e.getPos()))return;
            var center=state.is(TABLE.get())?HololithTableBlock.anchor(e.getPos(),state):e.getPos();var world=dev.hololith.compat.ShipCoordinates.toWorld(level,center);
            if(world.isEmpty()||world.get().distanceToSqr(player.position())>64)return;
            if(!level.isClientSide&&level.getBlockEntity(center) instanceof HololithTableEntity table&&table.allowControl()){table.adjustDial(true);SurveyFeedback.dial(player,table,true);}
            if(!level.isClientSide&&level.getBlockEntity(center) instanceof ReferenceMarkerEntity marker)SurveyFeedback.unlinkMarker(player,marker,world.get());
            e.setCanceled(true);e.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        });
    }
    public static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath(ID,path);}
}
