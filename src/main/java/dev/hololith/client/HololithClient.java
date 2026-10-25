package dev.hololith.client;
import dev.hololith.Hololith;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
@EventBusSubscriber(modid="hololith",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class HololithClient {
    private HololithClient(){}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){event.registerBlockEntityRenderer(Hololith.TABLE_ENTITY.get(),HololithRenderer::new);}
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event){
        event.registerReloadListener(new SimplePreparableReloadListener<Boolean>(){
            @Override protected Boolean prepare(ResourceManager manager,ProfilerFiller profiler){return true;}
            @Override protected void apply(Boolean ignored,ResourceManager manager,ProfilerFiller profiler){HololithRenderer.clear();}
        });
    }
    @EventBusSubscriber(modid="hololith",value=Dist.CLIENT)
    public static final class RuntimeEvents {
        @SubscribeEvent public static void tick(ClientTickEvent.Post event){HololithRenderer.tick();}
        @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){HololithRenderer.clear();}
    }
}
