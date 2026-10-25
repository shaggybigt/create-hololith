package dev.hololith.survey;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** Optional server-issued locator. It never grants membership or supplies a pin color. */
public record MarkerAnchor(BlockPos pos, int entityId, UUID entityUuid, UUID shipUuid) {
    public MarkerAnchor {
        pos=pos.immutable();
        if(entityId < -1 || (entityId == -1) != (entityUuid == null))
            throw new IllegalArgumentException("Invalid marker entity identity");
    }
    public static MarkerAnchor world(BlockPos pos,UUID ship){return new MarkerAnchor(pos,-1,null,ship);}
    public boolean mounted(){return entityId>=0;}
    public CompoundTag save(){
        CompoundTag tag=new CompoundTag();tag.putLong("Pos",pos.asLong());
        if(mounted()){tag.putInt("Entity",entityId);tag.putUUID("EntityId",entityUuid);}
        if(shipUuid!=null)tag.putUUID("ShipId",shipUuid);return tag;
    }
    public static MarkerAnchor load(CompoundTag tag){
        if(!tag.contains("Pos",net.minecraft.nbt.Tag.TAG_LONG))return null;
        boolean mounted=tag.contains("Entity");
        if(mounted&&(!tag.hasUUID("EntityId")||tag.getInt("Entity")<0))return null;
        if(tag.contains("ShipId")&&!tag.hasUUID("ShipId"))return null;
        return new MarkerAnchor(BlockPos.of(tag.getLong("Pos")),mounted?tag.getInt("Entity"):-1,
            mounted?tag.getUUID("EntityId"):null,tag.hasUUID("ShipId")?tag.getUUID("ShipId"):null);
    }
    public static void write(RegistryFriendlyByteBuf buffer,MarkerAnchor anchor){
        buffer.writeByte(anchor==null?0:anchor.mounted()?2:1);if(anchor==null)return;
        buffer.writeBlockPos(anchor.pos);
        if(anchor.mounted()){buffer.writeVarInt(anchor.entityId);buffer.writeUUID(anchor.entityUuid);}
        buffer.writeBoolean(anchor.shipUuid!=null);if(anchor.shipUuid!=null)buffer.writeUUID(anchor.shipUuid);
    }
    public static MarkerAnchor read(RegistryFriendlyByteBuf buffer){
        int kind=buffer.readUnsignedByte();if(kind==0)return null;
        if(kind!=1&&kind!=2)throw new io.netty.handler.codec.DecoderException("Invalid marker anchor kind");
        BlockPos pos=buffer.readBlockPos();int entity=kind==2?buffer.readVarInt():-1;
        if(kind==2&&entity<0)throw new io.netty.handler.codec.DecoderException("Invalid marker entity id");
        UUID identity=kind==2?buffer.readUUID():null;
        return new MarkerAnchor(pos,entity,identity,buffer.readBoolean()?buffer.readUUID():null);
    }
}
