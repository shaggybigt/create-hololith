package dev.hololith.compat.jade;

import dev.hololith.Hololith;
import dev.hololith.HololithTableBlock;
import dev.hololith.ReferenceMarkerBlock;
import dev.hololith.SurveySensorBlock;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/** Jade discovers this class only when installed; Hololith's entrypoint never loads it. */
@WailaPlugin
public final class HololithJadePlugin implements IWailaPlugin {
    static final ResourceLocation INSTRUMENT = Hololith.id("instrument");

    @Override public void register(IWailaCommonRegistration registration) {
        // Register by block class so the eight table tiles without a BE also work.
        registration.registerBlockDataProvider(HololithJadeServerData.INSTANCE, HololithTableBlock.class);
        registration.registerBlockDataProvider(HololithJadeServerData.INSTANCE, SurveySensorBlock.class);
        registration.registerBlockDataProvider(HololithJadeServerData.INSTANCE, ReferenceMarkerBlock.class);
    }

    @Override public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(HololithJadeTooltip.INSTANCE, HololithTableBlock.class);
        registration.registerBlockComponent(HololithJadeTooltip.INSTANCE, SurveySensorBlock.class);
        registration.registerBlockComponent(HololithJadeTooltip.INSTANCE, ReferenceMarkerBlock.class);
        registration.markAsServerFeature(INSTRUMENT);
    }
}
