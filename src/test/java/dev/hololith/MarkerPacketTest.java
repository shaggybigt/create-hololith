package dev.hololith;
import dev.hololith.survey.*;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MarkerPacketTest {
    private RegistryFriendlyByteBuf buffer(){return new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);}
    @Test void fullMarkerPacketRoundTripsIdentityLocationAndColors(){
        var points=new ArrayList<MarkerPoint>();for(int i=0;i<64;i++)points.add(new MarkerPoint(new UUID(0,i+1),-10+i*.125,20.5,0x123456+i));
        var message=new HololithNetwork.Markers(new BlockPos(100,72,-20),new UUID(3,4),new SurveyKey("minecraft:overworld",-4,20,128),List.copyOf(points));var b=buffer();
        try{HololithNetwork.Markers.CODEC.encode(b,message);assertTrue(b.readableBytes()<4096);assertEquals(message,HololithNetwork.Markers.CODEC.decode(b));assertEquals(0,b.readableBytes());}finally{b.release();}
    }
    @Test void countLimitIsCheckedBeforeReadingMarkerElements(){
        for(int count:new int[]{-1,65,Integer.MAX_VALUE}){var b=buffer();try{b.writeBlockPos(BlockPos.ZERO);b.writeUUID(new UUID(0,1));b.writeBoolean(false);b.writeVarInt(count);assertThrows(DecoderException.class,()->HololithNetwork.Markers.CODEC.decode(b));}finally{b.release();}}
    }
    @Test void nonfiniteCoordinatesCannotReachTheRenderer(){
        for(double x:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}){var b=buffer();try{HololithNetwork.Markers.CODEC.encode(b,new HololithNetwork.Markers(BlockPos.ZERO,new UUID(0,1),null,List.of(new MarkerPoint(new UUID(0,2),x,0,0xFFFFFF))));assertThrows(DecoderException.class,()->HololithNetwork.Markers.CODEC.decode(b));}finally{b.release();}}
    }
    @Test void emptyUpdatePreservesIdentityAndClearsTheMarkerSet(){
        var b=buffer();var message=new HololithNetwork.Markers(BlockPos.ZERO,new UUID(3,4),null,List.of());try{HololithNetwork.Markers.CODEC.encode(b,message);assertEquals(message,HololithNetwork.Markers.CODEC.decode(b));}finally{b.release();}
    }
    @Test void initialChunkTagIsBoundedAndDropsInvalidCoordinates(){
        var points=new ArrayList<MarkerPoint>();for(int i=0;i<65;i++)points.add(new MarkerPoint(new UUID(0,i+1),i,0,0xFE123456));var encoded=MarkerPoint.save(points);assertEquals(64,encoded.size());encoded.getCompound(0).putDouble("X",Double.NaN);var decoded=MarkerPoint.load(encoded);assertEquals(63,decoded.size());assertEquals(0x123456,decoded.getFirst().color());assertThrows(UnsupportedOperationException.class,()->decoded.clear());
    }
}
