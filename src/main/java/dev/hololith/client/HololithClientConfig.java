package dev.hololith.client;
import net.neoforged.neoforge.common.ModConfigSpec;
/** Local display settings; no terrain scans or mesh rebuilds are triggered. */
public final class HololithClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue HOLOGRAM_INTENSITY;
    static {
        var builder=new ModConfigSpec.Builder();
        HOLOGRAM_INTENSITY=builder.comment("Local projection brightness including terrain and exposed-edge glow. No shader pack required.").defineInRange("hologramIntensity",1.0,0.4,1.8);
        SPEC=builder.build();
    }
    private HololithClientConfig(){}
    public static float intensity(){return HOLOGRAM_INTENSITY.get().floatValue();}
    /** Live local preview; the instrument screen saves once when it closes. */
    public static void previewIntensity(double value){
        if(Double.isFinite(value))HOLOGRAM_INTENSITY.set(Math.clamp(Math.round(value*20)/20.0,0.4,1.8));
    }
    public static void save(){HOLOGRAM_INTENSITY.save();}
}
