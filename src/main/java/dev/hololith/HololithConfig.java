package dev.hololith;
import net.neoforged.neoforge.common.ModConfigSpec;
public final class HololithConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue COLUMNS_PER_TICK, REFRESH_TICKS, CACHE_ENTRIES;
    static {
        var b=new ModConfigSpec.Builder();
        COLUMNS_PER_TICK=b.comment("Global loaded-column scan budget shared fairly by all tables.").defineInRange("columnsPerTick",512,32,4096);
        REFRESH_TICKS=b.comment("Minimum interval between scans of a stationary target (20 ticks = 1 second).").defineInRange("refreshTicks",100,40,1200);
        CACHE_ENTRIES=b.comment("Maximum saved regions per server; least recently used are evicted. A table also retains its own last survey.").defineInRange("cachedRegions",64,8,256);
        SPEC=b.build();
    }
}
