package dev.hololith.compat;

import dev.hololith.Hololith;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/** Optional Sable integration. Renderer vertices stay in the supplied local pose stack. */
public final class ShipCoordinates {
    private static final AtomicBoolean WARNED = new AtomicBoolean();
    private ShipCoordinates() {}
    /** Empty means conversion failed: retain cached terrain, never scan plot coordinates. */
    public static Optional<Vec3> toWorld(Level level, BlockPos pos) {
        return toWorld(level,Vec3.atCenterOf(pos));
    }
    /** Compose a Create actor's transform with an optional enclosing Sable sublevel. */
    public static Optional<Vec3> toWorld(Level level, Vec3 center) {
        Bridge bridge = LazyBridge.INSTANCE;
        if (bridge.failure != null) { warnOnce(bridge.failure); return Optional.empty(); }
        try {
            Vec3 result = bridge.method == null ? center : (Vec3) bridge.method.invoke(bridge.instance, level, center);
            if (result == null || !Double.isFinite(result.x) || !Double.isFinite(result.y)
                    || !Double.isFinite(result.z) || Math.abs(result.x) > 30_000_000D || Math.abs(result.z) > 30_000_000D) {
                warnOnce(new IllegalStateException("Survey position outside the playable world")); return Optional.empty();
            }
            return Optional.of(result);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warnOnce(error); return Optional.empty();
        }
    }
    private static void warnOnce(Throwable error) {
        if (WARNED.compareAndSet(false, true)) Hololith.LOGGER.warn("Hololith could not resolve a survey position. Keeping cached terrain instead of scanning unconverted ship coordinates.", error);
    }
    private static final class LazyBridge {
        private static final Bridge INSTANCE = findBridge();
        private static Bridge findBridge() {
            try {
                Class<?> companion = Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
                return new Bridge(companion.getField("INSTANCE").get(null), companion.getMethod("projectOutOfSubLevel", Level.class, Vec3.class), null);
            } catch (ClassNotFoundException absent) { return new Bridge(null, null, null); }
            catch (ReflectiveOperationException | RuntimeException | LinkageError error) { return new Bridge(null, null, error); }
        }
    }
    private record Bridge(Object instance, Method method, Throwable failure) {}
}
