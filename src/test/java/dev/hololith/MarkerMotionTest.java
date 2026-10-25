package dev.hololith;

import dev.hololith.compat.ContraptionPackets;
import dev.hololith.compat.ShipCoordinates;
import dev.hololith.survey.*;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MarkerMotionTest {
    private static final UUID MARKER=new UUID(1,2),TABLE=new UUID(3,4),SENSOR=new UUID(5,6),ENTITY=new UUID(7,8),SHIP=new UUID(9,10);
    private RegistryFriendlyByteBuf buffer(){return new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);}
    private SurveySnapshot survey(){
        int n=32*32;byte[] flags=new byte[n];Arrays.fill(flags,(byte)1);
        return new SurveySnapshot(new SurveyKey("minecraft:overworld",0,0,32),0,1,new int[n],new int[n],new int[n],new int[n],new BlockState[n],new BlockState[n],flags,n);
    }
    @Test void movingSourcesRoundTripAllIdentitiesThroughBothTransports(){
        var points=new ArrayList<MarkerPoint>();
        for(int i=0;i<64;i++)points.add(new MarkerPoint(new UUID(1,i),i*.125,1,0xff123456,
            i%2==0?MarkerAnchor.world(new BlockPos(10000+i,80,20000),SHIP):new MarkerAnchor(new BlockPos(i,2,4),42,ENTITY,SHIP)));
        var message=new ContraptionPackets.Markers(7,new UUID(11,12),new HololithNetwork.Markers(BlockPos.ZERO,TABLE,new SurveyKey("minecraft:overworld",0,0,64),points));
        var b=buffer();try{
            ContraptionPackets.Markers.CODEC.encode(b,message);
            assertTrue(b.readableBytes()<8192,"Even 64 fully identified moving sources remain a small packet");
            assertEquals(message,ContraptionPackets.Markers.CODEC.decode(b));assertEquals(0,b.readableBytes());
        }finally{b.release();}
        assertEquals(points,MarkerPoint.load(MarkerPoint.save(points)));
    }
    @Test void oldSavedPointsAndMalformedLocatorsRetainAuthoritativeFallback(){
        MarkerPoint old=new MarkerPoint(MARKER,2.5,-3.5,0xabcdef);
        ListTag saved=MarkerPoint.save(List.of(old));assertEquals(old,MarkerPoint.load(saved).getFirst());
        CompoundTag invalid=new CompoundTag();invalid.putLong("Pos",BlockPos.ZERO.asLong());invalid.putInt("Entity",8);
        saved.getCompound(0).put("Source",invalid);
        assertEquals(old,MarkerPoint.load(saved).getFirst(),"A missing entity UUID must disable interpolation, not discard the pin");
        invalid.putUUID("EntityId",ENTITY);invalid.putInt("Entity",-3);
        assertEquals(old,MarkerPoint.load(saved).getFirst());
    }
    @Test void invalidWireLocatorsAreRejectedBeforeClientLookup(){
        var b=buffer();try{b.writeByte(3);assertThrows(DecoderException.class,()->MarkerAnchor.read(b));}finally{b.release();}
        var input=buffer();try{input.writeByte(2);input.writeBlockPos(BlockPos.ZERO);input.writeVarInt(-1);
            assertThrows(DecoderException.class,()->MarkerAnchor.read(input));}finally{input.release();}
    }
    @Test void sourceIdentityAndLinkMustBothMatchTheServerApprovedMarker(){
        assertTrue(MarkerRenderPosition.linked(MARKER,MARKER,TABLE,0,TABLE,null,0));
        assertTrue(MarkerRenderPosition.linked(MARKER,MARKER,SENSOR,1,TABLE,SENSOR,1));
        assertFalse(MarkerRenderPosition.linked(MARKER,UUID.randomUUID(),TABLE,0,TABLE,null,0));
        assertFalse(MarkerRenderPosition.linked(MARKER,MARKER,UUID.randomUUID(),0,TABLE,null,0));
        assertFalse(MarkerRenderPosition.linked(MARKER,MARKER,SENSOR,1,TABLE,SENSOR,0));
        assertFalse(MarkerRenderPosition.linked(MARKER,MARKER,TABLE,2,TABLE,SENSOR,1));
        assertFalse(MarkerRenderPosition.linked(MARKER,MARKER,null,0,TABLE,null,0));
    }
    @Test void newerLocalFramesCannotRevealUnknownCellsOrEscapeTheCachedArea(){
        var s=survey();var point=new MarkerPoint(MARKER,.5,.5,0x123456);Vec3 fallback=new Vec3(.5,0,.5);
        Vec3 local=new Vec3(3.25,90,4.75);assertEquals(local,MarkerRenderPosition.choose(point,Optional.of(local)));
        assertEquals(fallback,MarkerRenderPosition.choose(point,Optional.empty()));
        for(Vec3 invalid:List.of(new Vec3(0,Double.NaN,0),new Vec3(Double.POSITIVE_INFINITY,0,0)))
            assertEquals(fallback,MarkerRenderPosition.choose(point,Optional.of(invalid)));
        for(Vec3 outside:List.of(new Vec3(16,0,0),new Vec3(-16.01,0,0))){
            Vec3 resolved=MarkerRenderPosition.choose(point,Optional.of(outside));
            assertEquals(outside,resolved,"A tracked marker leaving the map must not freeze at its old edge position");
            assertTrue(SurveyGeometry.at(s).position(resolved.x,resolved.z).isEmpty());
        }
        s.flags[20*32+19]=0;
        Vec3 unknown=MarkerRenderPosition.choose(point,Optional.of(local));
        assertEquals(local,unknown);assertTrue(SurveyGeometry.at(s).position(unknown.x,unknown.z).isEmpty());
        assertEquals(0x123456,point.color());assertEquals(.5,point.x(),"Interpolation must not overwrite authoritative coordinates");
    }
    @Test void missingOptionalShipOrBadPartialTicksFallBackWithoutTouchingALevel(){
        Vec3 p=new Vec3(1,2,3);
        assertTrue(ShipCoordinates.toRenderWorld(null,p,SHIP,.5f).isEmpty());
        for(float partial:new float[]{Float.NaN,Float.POSITIVE_INFINITY,-.01f,1.01f})
            assertTrue(ShipCoordinates.toRenderWorld(null,p,null,partial).isEmpty());
        if(!net.neoforged.fml.ModList.get().isLoaded("sable"))assertEquals(Optional.of(p),ShipCoordinates.toRenderWorld(null,p,null,.5f));
    }
}
