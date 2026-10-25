package dev.hololith.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.hololith.*;
import dev.hololith.compat.ShipCoordinates;
import dev.hololith.survey.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** One table render's temporary resolver. No persistent entity references, network traffic or chunk searches. */
public final class MarkerPositionResolver {
    private final HololithTableEntity table;
    private final ClientLevel level;
    private final float partial;
    private final Map<UUID,CreateFrame> frames=new HashMap<>();
    public MarkerPositionResolver(HololithTableEntity table,float partial){
        this.table=table;this.partial=partial;this.level=Minecraft.getInstance().level;
    }
    public static Vec3 position(HololithTableEntity table,MarkerPoint point,float partial){return new MarkerPositionResolver(table,partial).position(point);}
    public Vec3 position(MarkerPoint point){return MarkerRenderPosition.choose(point,local(point));}
    private Optional<Vec3> local(MarkerPoint point){
        MarkerAnchor anchor=point.anchor();
        if(anchor==null||level==null||table.snapshot()==null||table.getLevel() instanceof net.createmod.ponder.api.level.PonderLevel
            ||!level.dimension().location().toString().equals(table.snapshot().dimension)
            ||!Float.isFinite(partial)||partial<0||partial>1)return Optional.empty();
        try{
            if(anchor.mounted())return mounted(point,anchor);
            // Client getBlockEntity returns null for unavailable chunks; it does not request terrain.
            if(!(level.getBlockEntity(anchor.pos()) instanceof ReferenceMarkerEntity marker)
                ||marker.isRemoved()||!linked(point,marker.markerId(),marker.targetId(),marker.targetKind()))return Optional.empty();
            return ShipCoordinates.toRenderWorld(level,Vec3.atCenterOf(anchor.pos()),anchor.shipUuid(),partial);
        }catch(RuntimeException|LinkageError unavailable){return Optional.empty();}
    }
    private Optional<Vec3> mounted(MarkerPoint point,MarkerAnchor anchor){
        if(!(level.getEntity(anchor.entityId()) instanceof AbstractContraptionEntity entity)||entity.isRemoved()
            ||!entity.getUUID().equals(anchor.entityUuid())||entity.getContraption()==null)return Optional.empty();
        var info=entity.getContraption().getBlocks().get(anchor.pos());
        if(info==null||!info.state().is(Hololith.MARKER.get())||!matchesTag(point,info.nbt()))return Optional.empty();
        // Use Create's render transform, including minecart/passenger offsets, rather than its logical anchor.
        CreateFrame frame=frames.computeIfAbsent(entity.getUUID(),ignored->{
            PoseStack pose=new PoseStack();entity.applyLocalTransforms(pose,partial);
            boolean visual=VisualizationManager.supportsVisualization(level);
            Vec3 position=visual&&entity.isPrevPosInvalid()?entity.position():new Vec3(
                Mth.lerp(partial,visual?entity.xo:entity.xOld,entity.getX()),
                Mth.lerp(partial,visual?entity.yo:entity.yOld,entity.getY()),
                Mth.lerp(partial,visual?entity.zo:entity.zOld,entity.getZ()));
            return new CreateFrame(new Matrix4f(pose.last().pose()),position);
        });
        Vector3f v=frame.transform.transformPosition(new Vector3f(anchor.pos().getX()+.5f,anchor.pos().getY()+.5f,anchor.pos().getZ()+.5f));
        return ShipCoordinates.toRenderWorld(level,entity.blockPosition(),frame.origin,new Vec3(v.x,v.y,v.z),anchor.shipUuid(),partial);
    }
    private boolean matchesTag(MarkerPoint point,CompoundTag tag){
        return tag!=null&&tag.hasUUID("MarkerId")&&tag.hasUUID("TargetId")
            &&linked(point,tag.getUUID("MarkerId"),tag.getUUID("TargetId"),tag.getInt("TargetKind"));
    }
    private boolean linked(MarkerPoint point,UUID id,UUID target,int kind){
        return MarkerRenderPosition.linked(point.id(),id,target,kind,table.tableId(),table.sensorId(),table.mode());
    }
    private record CreateFrame(Matrix4f transform,Vec3 origin){}
}
