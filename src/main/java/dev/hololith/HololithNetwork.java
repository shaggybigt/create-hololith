package dev.hololith;

import dev.hololith.compat.ShipCoordinates;
import dev.hololith.survey.*;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class HololithNetwork {
    public record Open(BlockPos pos) implements CustomPacketPayload {
        public static final Type<Open> TYPE=new Type<>(Hololith.id("open"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Open> CODEC=StreamCodec.of((b,p)->b.writeBlockPos(p.pos),b->new Open(b.readBlockPos()));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Configure(BlockPos pos,int span,int rotation,int zoom,int relief,int mode,String dimension,int x,int z) implements CustomPacketPayload {
        public static final Type<Configure> TYPE=new Type<>(Hololith.id("configure"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Configure> CODEC=StreamCodec.of((b,p)->{
            b.writeBlockPos(p.pos);b.writeVarInt(p.span);b.writeVarInt(p.rotation);b.writeVarInt(p.zoom);b.writeVarInt(p.relief);b.writeVarInt(p.mode);b.writeUtf(p.dimension,128);b.writeInt(p.x);b.writeInt(p.z);
        },b->new Configure(b.readBlockPos(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readUtf(128),b.readInt(),b.readInt()));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent event){
        var r=event.registrar("3");
        dev.hololith.compat.ContraptionPackets.register(event);
        r.playToClient(Markers.TYPE,Markers.CODEC,(p,c)->c.enqueueWork(()->{if(c.player().level().getBlockEntity(p.pos) instanceof HololithTableEntity table)table.acceptMarkers(p.tableId,p.key,p.points);}));
        r.playToClient(Open.TYPE,Open.CODEC,(p,c)->c.enqueueWork(()->dev.hololith.client.HololithControls.open(p.pos())));
        r.playToServer(Configure.TYPE,Configure.CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)apply(player,p);}));
    }
    public static void open(ServerPlayer player,HololithTableEntity table){
        player.connection.send(ClientboundBlockEntityDataPacket.create(table));PacketDistributor.sendToPlayer(player,new Open(table.getBlockPos()));
    }
    public record Markers(BlockPos pos,UUID tableId,SurveyKey key,List<MarkerPoint> points) implements CustomPacketPayload {
        public static final Type<Markers> TYPE=new Type<>(Hololith.id("markers"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Markers> CODEC=StreamCodec.of((b,p)->{
            b.writeBlockPos(p.pos);b.writeUUID(p.tableId);b.writeBoolean(p.key!=null);
            if(p.key!=null){b.writeUtf(p.key.dimension(),128);b.writeInt(p.key.x());b.writeInt(p.key.z());b.writeVarInt(p.key.span());}
            b.writeVarInt(p.points.size());for(var point:p.points){b.writeUUID(point.id());b.writeDouble(point.x());b.writeDouble(point.z());b.writeInt(point.color());}
        },b->{
            var pos=b.readBlockPos();var id=b.readUUID();SurveyKey key=null;
            if(b.readBoolean()){String dim=b.readUtf(128);int x=b.readInt(),z=b.readInt(),span=b.readVarInt();if(span!=32&&span!=64&&span!=128)throw new io.netty.handler.codec.DecoderException("Invalid marker survey span");key=new SurveyKey(dim,x,z,span);}
            int count=b.readVarInt();if(count<0||count>MarkerPoint.LIMIT)throw new io.netty.handler.codec.DecoderException("Too many reference markers");
            var points=new ArrayList<MarkerPoint>(count);for(int i=0;i<count;i++){var marker=b.readUUID();double x=b.readDouble(),z=b.readDouble();int color=b.readInt();if(!Double.isFinite(x)||!Double.isFinite(z))throw new io.netty.handler.codec.DecoderException("Invalid reference position");points.add(new MarkerPoint(marker,x,z,color&0xFFFFFF));}
            return new Markers(pos,id,key,List.copyOf(points));
        });
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public static void markers(ServerLevel level,HololithTableEntity table,SurveyKey key,List<MarkerPoint> points){
        PacketDistributor.sendToPlayersTrackingChunk(level,new ChunkPos(table.getBlockPos()),new Markers(table.getBlockPos(),table.tableId(),key,points));
    }
    public static boolean valid(Configure p){
        return (p.span==32||p.span==64||p.span==128)&&p.rotation>=0&&p.rotation<4&&p.zoom>=0&&p.zoom<=2&&p.relief>=1&&p.relief<=2&&p.mode>=0&&p.mode<=2&&Math.abs((long)p.x)<=29_999_872&&Math.abs((long)p.z)<=29_999_872&&ResourceLocation.tryParse(p.dimension)!=null;
    }
    private static void apply(ServerPlayer player,Configure p){
        if(!valid(p)||player.isSpectator()||!player.mayBuild())return;
        var level=player.serverLevel();var world=ShipCoordinates.toWorld(level,p.pos);if(world.isEmpty()||world.get().distanceToSqr(player.position())>64)return;
        if(!level.hasChunkAt(p.pos)||!level.mayInteract(player,p.pos))return;
        if(level.getBlockEntity(p.pos) instanceof HololithTableEntity table){
            if(p.mode==1&&table.sensorId()==null)return;
            if(!table.allowControl())return;
            table.configure(p.span,p.rotation,p.zoom,p.relief,p.mode,p.dimension,p.x,p.z);
            SurveyFeedback.configured(player,table,world.get());
        }
    }
}
