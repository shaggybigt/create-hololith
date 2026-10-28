package dev.hololith;

import dev.hololith.survey.SurveyKey;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SurveyContractTest {
    @Test void negativeCoordinatesAlignWithoutDrift(){
        var key=SurveyKey.at("minecraft:overworld",-1,-17,64);
        assertEquals(-4,key.x());assertEquals(-20,key.z());
        assertEquals(-36,key.sampleX(0));assertEquals(-52,key.sampleZ(0));
        assertEquals(27,key.sampleX(4095));assertEquals(11,key.sampleZ(4095));
    }
    @Test void largestSurveyCapsNetworkAndMeshResolution(){
        var key=SurveyKey.at("minecraft:overworld",0,0,128);
        assertEquals(64,key.size());assertEquals(2,key.step());
        assertEquals(-64,key.sampleX(0));assertEquals(62,key.sampleX(4095));
        assertEquals(-64,key.sampleZ(0));assertEquals(62,key.sampleZ(4095));
    }
    @Test void rejectMalformedControlsBeforeWorldLookup(){
        assertTrue(HololithNetwork.valid(packet(64,0,0,"minecraft:overworld")));
        assertFalse(HololithNetwork.valid(packet(4096,0,0,"minecraft:overworld")));
        assertFalse(HololithNetwork.valid(packet(64,Integer.MIN_VALUE,0,"minecraft:overworld")));
        assertFalse(HololithNetwork.valid(packet(64,0,0,"Minecraft:Overworld")));
        assertFalse(HololithNetwork.valid(new HololithNetwork.Configure(BlockPos.ZERO,64,4,0,1,0,"minecraft:overworld",0,0)));
        assertFalse(HololithNetwork.valid(new HololithNetwork.Configure(BlockPos.ZERO,64,0,0,1,9,"minecraft:overworld",0,0)));
    }
    @Test void onlyPhysicalSourceModesAreAcceptedBeforeWorldLookup(){
        assertTrue(HololithNetwork.valid(new HololithNetwork.Configure(BlockPos.ZERO,32,0,0,1,0,"minecraft:overworld",0,0)));
        assertTrue(HololithNetwork.valid(new HololithNetwork.Configure(BlockPos.ZERO,32,0,0,1,1,"minecraft:overworld",0,0)));
        assertFalse(HololithNetwork.valid(new HololithNetwork.Configure(BlockPos.ZERO,32,0,0,1,2,"minecraft:overworld",0,0)));
        assertFalse(HololithNetwork.valid(new HololithNetwork.Configure(BlockPos.ZERO,128,3,2,2,2,"minecraft:the_nether",4096,-4096)));
        assertFalse(HololithNetwork.valid(new HololithNetwork.Configure(BlockPos.ZERO,64,0,0,1,-1,"minecraft:overworld",0,0)));
    }
    private HololithNetwork.Configure packet(int span,int x,int z,String dimension){return new HololithNetwork.Configure(BlockPos.ZERO,span,0,0,1,0,dimension,x,z);}
}
