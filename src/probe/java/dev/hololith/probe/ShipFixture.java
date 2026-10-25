package dev.hololith.probe;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import org.joml.*;
/** Test-only adapter to the installed Sable API. */
final class ShipFixture {
    final Object ship,physics,handle;
    final Class<?> shipType,physicsType,handleType;
    final ServerLevel level;
    final BlockPos sourceAnchor,plotAnchor;
    final List<BlockPos> originalBlocks,plotBlocks;
    final boolean wasPaused;
    final Vector3d startPosition;
    final Quaterniond startRotation;
    ShipFixture(ServerLevel level,BlockPos anchor,List<BlockPos> blocks) throws Exception {
        this.level=level;sourceAnchor=anchor;originalBlocks=List.copyOf(blocks);
        shipType=Class.forName("dev.ryanhcode.sable.sublevel.ServerSubLevel");physicsType=Class.forName("dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem");handleType=Class.forName("dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle");
        physics=physicsType.getMethod("require",Level.class).invoke(null,level);wasPaused=(boolean)physicsType.getMethod("getPaused").invoke(physics);physicsType.getMethod("setPaused",boolean.class).invoke(physics,true);
        try {
            Class<?> bb=Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3i"),bbi=Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3ic");
            Object bounds=bb.getConstructor(int.class,int.class,int.class,int.class,int.class,int.class).newInstance(blocks.stream().mapToInt(BlockPos::getX).min().orElseThrow(),blocks.stream().mapToInt(BlockPos::getY).min().orElseThrow(),blocks.stream().mapToInt(BlockPos::getZ).min().orElseThrow(),blocks.stream().mapToInt(BlockPos::getX).max().orElseThrow(),blocks.stream().mapToInt(BlockPos::getY).max().orElseThrow(),blocks.stream().mapToInt(BlockPos::getZ).max().orElseThrow());
            Class<?> helper=Class.forName("dev.ryanhcode.sable.api.SubLevelAssemblyHelper");ship=helper.getMethod("assembleBlocks",ServerLevel.class,BlockPos.class,Iterable.class,bbi).invoke(null,level,anchor,blocks,bounds);
            Object plot=shipType.getMethod("getPlot").invoke(ship);plotAnchor=(BlockPos)Class.forName("dev.ryanhcode.sable.sublevel.plot.LevelPlot").getMethod("getCenterBlock").invoke(plot);
            plotBlocks=blocks.stream().map(this::inPlot).toList();handle=handleType.getMethod("of",shipType).invoke(null,ship);Object pose=shipType.getMethod("logicalPose").invoke(ship);
            startPosition=new Vector3d((Vector3dc)pose.getClass().getMethod("position").invoke(pose));startRotation=new Quaterniond((Quaterniondc)pose.getClass().getMethod("orientation").invoke(pose));
        }catch(Exception e){restorePhysics();throw e;}
    }
    BlockPos inPlot(BlockPos original){return original.offset(plotAnchor.getX()-sourceAnchor.getX(),plotAnchor.getY()-sourceAnchor.getY(),plotAnchor.getZ()-sourceAnchor.getZ());}
    void move(double x,double y,double z,double yaw) throws Exception {
        handleType.getMethod("teleport",Vector3dc.class,Quaterniondc.class).invoke(handle,new Vector3d(startPosition).add(x,y,z),new Quaterniond(startRotation).rotateY(yaw));physicsType.getMethod("updatePose",shipType).invoke(physics,ship);shipType.getMethod("forceUpdateGlobalBounds").invoke(ship);
    }
    void restorePhysics() throws Exception {physicsType.getMethod("setPaused",boolean.class).invoke(physics,wasPaused);}
    void returnBlocks() throws Exception {
        try {
            for(var p:originalBlocks)if(!level.getBlockState(p).isAir())throw new IllegalStateException("Fixture destination changed: "+p);
            Class<?> helper=Class.forName("dev.ryanhcode.sable.api.SubLevelAssemblyHelper"),transformType=Class.forName("dev.ryanhcode.sable.api.SubLevelAssemblyHelper$AssemblyTransform");
            Object transform=transformType.getConstructor(BlockPos.class,BlockPos.class,int.class,Rotation.class,ServerLevel.class).newInstance(plotAnchor,sourceAnchor,0,Rotation.NONE,level);helper.getMethod("moveBlocks",ServerLevel.class,transformType,Iterable.class).invoke(null,level,transform,plotBlocks);
            Class<?> containerType=Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");Object container=containerType.getMethod("getContainer",Level.class).invoke(null,level);
            if(!(boolean)shipType.getMethod("isRemoved").invoke(ship)){
                Class<?> reason=Class.forName("dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason");containerType.getMethod("removeSubLevel",Class.forName("dev.ryanhcode.sable.sublevel.SubLevel"),reason).invoke(container,ship,reason.getField("REMOVED").get(null));
            }
            containerType.getMethod("processSubLevelRemovals").invoke(container);
        }finally{restorePhysics();}
    }
}
