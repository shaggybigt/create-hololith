package dev.hololith;
import dev.hololith.compat.ShipCoordinates;
import dev.hololith.survey.ReferenceRegistry;
import dev.hololith.survey.MarkerAnchor;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
public final class ReferenceMarkerEntity extends BlockEntity {
    private UUID markerId=UUID.randomUUID(),targetId;
    private int targetKind;
    private java.util.function.Supplier<java.util.Optional<net.minecraft.world.phys.Vec3>> mountedPosition;
    private Runnable mountedChanged;
    private AbstractContraptionEntity mountedEntity;
    private BlockPos mountedLocalPos;
    public ReferenceMarkerEntity(BlockPos pos,BlockState state){super(Hololith.MARKER_ENTITY.get(),pos,state);}
    public UUID markerId(){return markerId;}
    public UUID targetId(){return targetId;}
    public int targetKind(){return targetKind;}
    public void setMountedCallbacks(java.util.function.Supplier<java.util.Optional<net.minecraft.world.phys.Vec3>> position,Runnable changed){mountedPosition=position;mountedChanged=changed;}
    public void setMountedAnchor(AbstractContraptionEntity entity,BlockPos localPos){mountedEntity=entity;mountedLocalPos=localPos.immutable();}
    public MarkerAnchor anchor(){
        if(level==null)return null;
        if(mountedEntity!=null){
            if(mountedEntity.isRemoved()||mountedEntity.level()!=level)return null;
            return new MarkerAnchor(mountedLocalPos,mountedEntity.getId(),mountedEntity.getUUID(),ShipCoordinates.shipIdentity(level,mountedEntity.position()));
        }
        return MarkerAnchor.world(worldPosition,ShipCoordinates.shipIdentity(level,net.minecraft.world.phys.Vec3.atCenterOf(worldPosition)));
    }
    public boolean bind(CompoundTag card){
        UUID target=card.hasUUID("TableId")?card.getUUID("TableId"):card.hasUUID("SensorId")?card.getUUID("SensorId"):null;
        if(target==null)return false;
        unregister();targetId=target;targetKind=card.hasUUID("TableId")?0:1;changed();announce();return true;
    }
    public void unlink(){unregister();targetId=null;changed();}
    private void unregister(){if(level instanceof ServerLevel s)ReferenceRegistry.get(s.getServer()).remove(this);}
    private void announce(){
        if(targetId==null||!(level instanceof ServerLevel s))return;
        (mountedPosition==null?ShipCoordinates.toWorld(level,worldPosition):mountedPosition.get()).ifPresent(v->ReferenceRegistry.get(s.getServer()).update(s.getServer(),this,level.dimension().location().toString(),v.x,v.z));
    }
    public static void tick(Level level,BlockPos pos,BlockState state,ReferenceMarkerEntity marker){if((level.getGameTime()+pos.asLong())%5==0)marker.announce();}
    @Override public void onLoad(){super.onLoad();if(level!=null&&!level.isClientSide){setChanged();announce();}}
    @Override public void setRemoved(){unregister();super.setRemoved();}
    public void changed(){if(mountedChanged!=null){mountedChanged.run();return;}setChanged();if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.saveAdditional(tag,lookup);tag.putUUID("MarkerId",markerId);if(targetId!=null)tag.putUUID("TargetId",targetId);tag.putInt("TargetKind",targetKind);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.loadAdditional(tag,lookup);if(tag.hasUUID("MarkerId"))markerId=tag.getUUID("MarkerId");targetId=tag.hasUUID("TargetId")?tag.getUUID("TargetId"):null;targetKind=tag.getInt("TargetKind")==1?1:0;}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup){return saveWithoutMetadata(lookup);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
