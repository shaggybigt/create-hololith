package dev.hololith.compat;

import dev.hololith.Hololith;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

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
    /** Identity of the containing plot, never a search across ships or chunks. */
    public static UUID shipIdentity(Level level,Vec3 position){
        ShipBridge bridge=ShipBridge.INSTANCE;
        if(bridge.failure!=null||bridge.containing==null||!finite(position))return null;
        try{
            Object ship=bridge.containing.invoke(bridge.instance,level,BlockPos.containing(position));
            return ship==null||(boolean)bridge.removed.invoke(ship)?null:(UUID)bridge.identity.invoke(ship);
        }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){return null;}
    }
    /** Uses the very same interpolated pose as Sable's renderer; no extrapolation. */
    public static Optional<Vec3> toRenderWorld(Level level,Vec3 position,UUID expectedShip,float partial){
        if(!finite(position)||!Float.isFinite(partial)||partial<0||partial>1)return Optional.empty();
        ShipBridge bridge=ShipBridge.INSTANCE;
        if(bridge.failure!=null)return Optional.empty();
        if(bridge.containing==null)return expectedShip==null&&playable(position)?Optional.of(position):Optional.empty();
        try{
            Object ship=bridge.containing.invoke(bridge.instance,level,BlockPos.containing(position));
            if(ship==null)return expectedShip==null&&playable(position)?Optional.of(position):Optional.empty();
            if(expectedShip==null||(boolean)bridge.removed.invoke(ship)||!expectedShip.equals(bridge.identity.invoke(ship)))return Optional.empty();
            RenderBridge render=RenderBridge.INSTANCE;
            if(render.clientType==null||!render.clientType.isInstance(ship))return Optional.empty();
            Object pose=render.pose.invoke(ship,partial);
            Vec3 result=(Vec3)render.transform.invoke(pose,position);
            return playable(result)?Optional.of(result):Optional.empty();
        }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){return Optional.empty();}
    }
    /** Sable renders entity origins with the full pose, but rotates their geometry without scaling it. */
    public static Optional<Vec3> toRenderWorld(Level level,BlockPos sourcePosition,Vec3 origin,Vec3 relative,UUID expectedShip,float partial){
        if(sourcePosition==null||!finite(origin)||!finite(relative)||!Float.isFinite(partial)||partial<0||partial>1)return Optional.empty();
        ShipBridge bridge=ShipBridge.INSTANCE;
        if(bridge.failure!=null)return Optional.empty();
        Vec3 ordinary=origin.add(relative);
        if(bridge.containing==null)return expectedShip==null&&playable(ordinary)?Optional.of(ordinary):Optional.empty();
        try{
            Object ship=bridge.containing.invoke(bridge.instance,level,sourcePosition);
            if(ship==null)return expectedShip==null&&playable(ordinary)?Optional.of(ordinary):Optional.empty();
            if(expectedShip==null||(boolean)bridge.removed.invoke(ship)||!expectedShip.equals(bridge.identity.invoke(ship)))return Optional.empty();
            RenderBridge render=RenderBridge.INSTANCE;
            if(render.clientType==null||!render.clientType.isInstance(ship))return Optional.empty();
            Object pose=render.pose.invoke(ship,partial);
            Vec3 transformedOrigin=(Vec3)render.transform.invoke(pose,origin);
            return composeRenderedEntity(transformedOrigin,relative,(Quaterniondc)render.orientation.invoke(pose));
        }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){return Optional.empty();}
    }
    static Optional<Vec3> composeRenderedEntity(Vec3 origin,Vec3 relative,Quaterniondc orientation){
        if(!finite(origin)||!finite(relative)||orientation==null)return Optional.empty();
        Vector3d offset=orientation.transform(new Vector3d(relative.x,relative.y,relative.z));
        Vec3 result=origin.add(offset.x,offset.y,offset.z);
        return playable(result)?Optional.of(result):Optional.empty();
    }
    private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
    private static boolean playable(Vec3 v){return finite(v)&&Math.abs(v.x)<=30_000_000D&&Math.abs(v.z)<=30_000_000D;}
    private static final class ShipBridge {
        static final ShipBridge INSTANCE=new ShipBridge();
        Object instance;Method containing,identity,removed;Throwable failure;
        ShipBridge(){
            try{
                Class<?> companion=Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
                instance=companion.getField("INSTANCE").get(null);
                containing=companion.getMethod("getContaining",Level.class,net.minecraft.core.Vec3i.class);
                identity=Class.forName("dev.ryanhcode.sable.companion.SubLevelAccess").getMethod("getUniqueId");
                removed=Class.forName("dev.ryanhcode.sable.sublevel.SubLevel").getMethod("isRemoved");
            }catch(ClassNotFoundException absent){if(instance!=null)failure=absent;}
            catch(ReflectiveOperationException|RuntimeException|LinkageError error){failure=error;}
        }
    }
    /** Loaded only by the client-side render entry point, not by server identity lookups. */
    private static final class RenderBridge {
        static final RenderBridge INSTANCE=new RenderBridge();
        Class<?> clientType;Method pose,transform,orientation;
        RenderBridge(){try{
            clientType=Class.forName("dev.ryanhcode.sable.companion.ClientSubLevelAccess");
            pose=clientType.getMethod("renderPose",float.class);
            transform=Class.forName("dev.ryanhcode.sable.companion.math.Pose3dc").getMethod("transformPosition",Vec3.class);
            orientation=Class.forName("dev.ryanhcode.sable.companion.math.Pose3dc").getMethod("orientation");
        }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){clientType=null;}}
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
