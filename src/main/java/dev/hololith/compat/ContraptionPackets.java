package dev.hololith.compat;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import dev.hololith.*;
import dev.hololith.survey.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Entity-tracking transport; ordinary chunk packets cannot address captured blocks. */
public final class ContraptionPackets {
    private ContraptionPackets() {}
    public record State(int entityId,UUID entityUuid,BlockPos pos,CompoundTag tag) implements CustomPacketPayload {
        public static final Type<State> TYPE=new Type<>(Hololith.id("contraption_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=StreamCodec.of((buffer,p)->{
            buffer.writeVarInt(p.entityId);buffer.writeUUID(p.entityUuid);buffer.writeBlockPos(p.pos);buffer.writeNbt(p.tag);
        },buffer->{
            int id=buffer.readVarInt();UUID uuid=buffer.readUUID();BlockPos pos=buffer.readBlockPos();CompoundTag tag=buffer.readNbt();
            if(tag==null)throw new io.netty.handler.codec.DecoderException("Missing captured survey state");
            return new State(id,uuid,pos,tag);
        });
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Markers(int entityId,UUID entityUuid,HololithNetwork.Markers frame) implements CustomPacketPayload {
        public static final Type<Markers> TYPE=new Type<>(Hololith.id("contraption_markers"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Markers> CODEC=StreamCodec.of((buffer,p)->{
            buffer.writeVarInt(p.entityId);buffer.writeUUID(p.entityUuid);HololithNetwork.Markers.CODEC.encode(buffer,p.frame);
        },buffer->new Markers(buffer.readVarInt(),buffer.readUUID(),HololithNetwork.Markers.CODEC.decode(buffer)));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Open(int entityId,UUID entityUuid,BlockPos pos) implements CustomPacketPayload {
        public static final Type<Open> TYPE=new Type<>(Hololith.id("contraption_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Open> CODEC=StreamCodec.of((buffer,p)->{
            buffer.writeVarInt(p.entityId);buffer.writeUUID(p.entityUuid);buffer.writeBlockPos(p.pos);
        },buffer->new Open(buffer.readVarInt(),buffer.readUUID(),buffer.readBlockPos()));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Configure(int entityId,UUID entityUuid,HololithNetwork.Configure settings) implements CustomPacketPayload {
        public static final Type<Configure> TYPE=new Type<>(Hololith.id("contraption_configure"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Configure> CODEC=StreamCodec.of((buffer,p)->{
            buffer.writeVarInt(p.entityId);buffer.writeUUID(p.entityUuid);HololithNetwork.Configure.CODEC.encode(buffer,p.settings);
        },buffer->new Configure(buffer.readVarInt(),buffer.readUUID(),HololithNetwork.Configure.CODEC.decode(buffer)));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }

    public static void register(RegisterPayloadHandlersEvent event){
        var registrar=event.registrar("5");
        registrar.playToClient(State.TYPE,State.CODEC,(packet,context)->context.enqueueWork(()->dev.hololith.client.ContraptionClient.accept(packet.entityId,packet.entityUuid,packet.pos,packet.tag)));
        registrar.playToClient(Markers.TYPE,Markers.CODEC,(packet,context)->context.enqueueWork(()->{
            var frame=packet.frame;dev.hololith.client.ContraptionClient.acceptMarkers(packet.entityId,packet.entityUuid,frame.pos(),frame.tableId(),frame.key(),frame.points());
        }));
        registrar.playToClient(Open.TYPE,Open.CODEC,(packet,context)->context.enqueueWork(()->dev.hololith.client.ContraptionClient.open(packet.entityId,packet.entityUuid,packet.pos)));
        registrar.playToServer(Configure.TYPE,Configure.CODEC,(packet,context)->context.enqueueWork(()->{
            if(context.player() instanceof ServerPlayer player)apply(player,packet);
        }));
    }

    public static void sync(AbstractContraptionEntity entity,BlockPos localPos,CompoundTag tag){
        if(entity==null||entity.level().isClientSide||entity.isRemoved()||tag==null)return;
        PacketDistributor.sendToPlayersTrackingEntity(entity,new State(entity.getId(),entity.getUUID(),localPos,tag));
    }
    public static void markers(AbstractContraptionEntity entity,BlockPos localPos,UUID tableId,SurveyKey key,List<MarkerPoint> points){
        if(entity==null||entity.level().isClientSide||entity.isRemoved())return;
        PacketDistributor.sendToPlayersTrackingEntity(entity,new Markers(entity.getId(),entity.getUUID(),new HololithNetwork.Markers(localPos,tableId,key,points)));
    }
    public static void track(ServerPlayer player,AbstractContraptionEntity entity){
        if(entity.getContraption()==null||entity.isRemoved())return;
        for(var actor:entity.getContraption().getActors()){
            CompoundTag tag=ContraptionSurveys.stateTag(actor.right);
            if(tag!=null)PacketDistributor.sendToPlayer(player,new State(entity.getId(),entity.getUUID(),actor.left.pos(),tag));
        }
    }

    public static boolean canUse(ServerPlayer player,AbstractContraptionEntity entity,BlockPos localPos){
        if(entity==null||entity.isRemoved()||entity.level()!=player.level()||player.isSpectator()||!player.mayBuild())return false;
        var position=ContraptionSurveys.worldPosition(entity,localPos);
        return position.isPresent()&&position.get().distanceToSqr(player.position())<=64
            &&player.serverLevel().mayInteract(player,BlockPos.containing(position.get()));
    }
    public static void open(ServerPlayer player,AbstractContraptionEntity entity,BlockPos localPos){
        if(!canUse(player,entity,localPos)||ContraptionSurveys.table(entity,localPos)==null)return;
        CompoundTag tag=ContraptionSurveys.stateTag(ContraptionSurveys.context(entity,localPos));
        if(tag==null)return;
        PacketDistributor.sendToPlayer(player,new State(entity.getId(),entity.getUUID(),localPos,tag));
        PacketDistributor.sendToPlayer(player,new Open(entity.getId(),entity.getUUID(),localPos));
    }
    public static void configure(int entityId,UUID entityUuid,HololithNetwork.Configure settings){
        PacketDistributor.sendToServer(new Configure(entityId,entityUuid,settings));
    }
    private static void apply(ServerPlayer player,Configure packet){
        var settings=packet.settings;
        if(!HololithNetwork.valid(settings))return;
        if(!(player.serverLevel().getEntity(packet.entityId) instanceof AbstractContraptionEntity entity)||!entity.getUUID().equals(packet.entityUuid))return;
        if(!canUse(player,entity,settings.pos()))return;
        HololithTableEntity table=ContraptionSurveys.table(entity,settings.pos());
        if(table==null||(settings.mode()==1&&table.sensorId()==null)||!table.allowControl())return;
        table.configure(settings.span(),settings.rotation(),settings.zoom(),settings.relief(),settings.mode(),settings.dimension(),settings.x(),settings.z());
        SurveyFeedback.configured(player,table,ContraptionSurveys.worldPosition(entity,settings.pos()).orElse(null));
    }
}
