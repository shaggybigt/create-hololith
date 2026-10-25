package dev.hololith.client.ponder;
import dev.hololith.Hololith;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
@EventBusSubscriber(modid=Hololith.ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class HololithPonderPlugin implements PonderPlugin {
    @SubscribeEvent public static void setup(FMLClientSetupEvent event){event.enqueueWork(()->PonderIndex.addPlugin(new HololithPonderPlugin()));}
    @Override public String getModId(){return Hololith.ID;}
    @Override public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper){
        helper.forComponents(id("surveying_table")).addStoryBoard("survey_workshop",HololithScenes::table);
        helper.forComponents(id("surveying_table"),id("local_sensor"),id("remote_beacon"),id("survey_card")).addStoryBoard("survey_workshop",HololithScenes::sources);
        helper.forComponents(id("reference_marker"),id("survey_card"),id("surveying_table")).addStoryBoard("survey_workshop",HololithScenes::markers);
    }
    private static ResourceLocation id(String path){return Hololith.id(path);}
}
