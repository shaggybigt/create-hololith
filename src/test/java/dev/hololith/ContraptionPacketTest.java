package dev.hololith;

import dev.hololith.compat.ContraptionPackets;
import dev.hololith.survey.*;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContraptionPacketTest {
    private RegistryFriendlyByteBuf buffer(){return new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);}
    @Test void mountedControlKeepsEntityIdentitySeparateFromLocalBlockPosition(){
        var settings=new HololithNetwork.Configure(new BlockPos(-12,4,8),128,3,2,2,1,"minecraft:overworld",-120,800);
        var original=new ContraptionPackets.Configure(42,new UUID(5,8),settings);
        var b=buffer();try{ContraptionPackets.Configure.CODEC.encode(b,original);var received=ContraptionPackets.Configure.CODEC.decode(b);
            assertEquals(original,received);assertTrue(HololithNetwork.valid(received.settings()));assertEquals(0,b.readableBytes());
        }finally{b.release();}
    }
    @Test void mountedMarkersRetainAllIdentitiesAndStaySmall(){
        var points=new ArrayList<MarkerPoint>();for(int i=0;i<64;i++)points.add(new MarkerPoint(new UUID(2,i),-8+i*.125,32.5,0x123456+i));
        var original=new ContraptionPackets.Markers(100,new UUID(8,9),new HololithNetwork.Markers(new BlockPos(2,5,-6),new UUID(3,7),new SurveyKey("minecraft:overworld",-8,32,64),List.copyOf(points)));
        var b=buffer();try{ContraptionPackets.Markers.CODEC.encode(b,original);assertTrue(b.readableBytes()<4096);assertEquals(original,ContraptionPackets.Markers.CODEC.decode(b));assertEquals(0,b.readableBytes());}finally{b.release();}
    }
    @Test void mountedMarkerWrapperPreservesCountGuard(){
        var b=buffer();try{b.writeVarInt(1);b.writeUUID(new UUID(1,2));b.writeBlockPos(BlockPos.ZERO);b.writeUUID(new UUID(3,4));b.writeBoolean(false);b.writeVarInt(65);
            assertThrows(DecoderException.class,()->ContraptionPackets.Markers.CODEC.decode(b));
        }finally{b.release();}
    }
    @Test void mountedStateRoundTripsSavedIdentityWithoutWorldPositionGuessing(){
        var tag=new CompoundTag();tag.putUUID("TableId",new UUID(1,7));tag.putInt("Span",64);tag.putString("Status","Cached terrain");
        var original=new ContraptionPackets.State(41,new UUID(2,9),new BlockPos(-5,2,8),tag);
        var b=buffer();try{ContraptionPackets.State.CODEC.encode(b,original);assertEquals(original,ContraptionPackets.State.CODEC.decode(b));assertEquals(0,b.readableBytes());}finally{b.release();}
    }
    @Test void nullStateIsRejectedBeforeClientDelivery(){
        var b=buffer();try{b.writeVarInt(1);b.writeUUID(new UUID(1,2));b.writeBlockPos(BlockPos.ZERO);b.writeNbt(null);
            assertThrows(DecoderException.class,()->ContraptionPackets.State.CODEC.decode(b));
        }finally{b.release();}
    }
}
