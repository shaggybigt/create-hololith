package dev.hololith;
import dev.hololith.survey.*;
import java.util.*;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ReferenceGeometryTest {
    private SurveySnapshot flat(int span,int x,int z){
        var key=new SurveyKey("minecraft:overworld",x,z,span);int n=key.size()*key.size();
        int[] ground=new int[n],surface=new int[n];byte[] flags=new byte[n];Arrays.fill(ground,64);Arrays.fill(surface,64);Arrays.fill(flags,(byte)1);
        return new SurveySnapshot(key,1000,1,ground,surface,new int[n],new int[n],new BlockState[n],new BlockState[n],flags,n);
    }
    @Test void negativeBoundsAreHalfOpenAndUseBlockCenters(){
        var s=flat(32,-4,-20);var g=SurveyGeometry.at(s);
        assertTrue(g.position(-20,-36).isPresent());assertTrue(g.position(12-1e-6,-4-1e-6).isPresent());assertTrue(g.position(-20-1e-6,-36).isEmpty());
        assertTrue(g.position(12,-20).isEmpty());assertTrue(g.position(-4,-4).isEmpty());
        var p=g.position(-19.5,-35.5).orElseThrow();assertEquals(-1.1140625,p.x(),1e-6);assertEquals(-1.1140625,p.z(),1e-6);assertEquals(.106875,p.y(),1e-6);
        var center=g.position(-4,-20).orElseThrow();assertEquals(0,center.x());assertEquals(0,center.z());
    }
    @Test void unknownAndNonfiniteLocationsNeverProduceMarkers(){
        var s=flat(32,0,0);s.flags[16*32+16]=0;s.groundY[16*32+16]=-4096;s.surfaceY[16*32+16]=4095;var g=SurveyGeometry.at(s);
        assertTrue(g.position(.5,.5).isEmpty());assertEquals(.106875,g.position(1.5,.5).orElseThrow().y(),1e-6);
        assertTrue(g.position(Double.NaN,0).isEmpty());assertTrue(g.position(0,Double.POSITIVE_INFINITY).isEmpty());assertFalse(MarkerPoint.inside(null,0,0));
        Arrays.fill(s.flags,(byte)0);assertTrue(SurveyGeometry.at(s).position(0,0).isEmpty());
    }
    @Test void waterCanopyAndMountainsUseDisplayedSurfaceElevation(){
        var s=flat(32,0,0);int water=16*32+16,tree=water+1,mountain=water+2;
        s.flags[water]=3;s.groundY[water]=60;s.surfaceY[water]=64;s.flags[tree]=5;s.surfaceY[tree]=72;s.groundY[mountain]=164;s.surfaceY[mountain]=164;
        var g=SurveyGeometry.at(s);double wy=g.position(.5,.5).orElseThrow().y(),ty=g.position(1.5,.5).orElseThrow().y(),my=g.position(2.5,.5).orElseThrow().y();
        assertEquals(g.height(65),wy,1e-6);assertEquals(g.height(73),ty,1e-6);assertTrue(wy<ty&&ty<my);assertEquals(.75,my,1e-6);
        var point=new MarkerPoint(new UUID(0,1),.5,.5,0x00FFFF);assertEquals(wy,g.position(point.x(),point.z()).orElseThrow().y(),1e-6);
    }
    @Test void largeSurveyMapsBothWorldBlocksToOneSample(){
        var s=flat(128,0,0);s.flags[0]=0;var g=SurveyGeometry.at(s);assertTrue(g.position(-63.5,-63.5).isEmpty());assertTrue(g.position(-62.5,-63.5).isEmpty());assertTrue(g.position(-61.5,-63.5).isPresent());assertTrue(g.position(64,-63.5).isEmpty());
    }
    @Test void retargetingUsesCompletedSnapshotNotUnpublishedCoordinates(){
        var previous=flat(32,0,0);var next=flat(32,32,0);assertTrue(SurveyGeometry.at(previous).position(.5,.5).isPresent());assertTrue(SurveyGeometry.at(next).position(.5,.5).isEmpty());assertTrue(SurveyGeometry.at(previous).position(32.5,.5).isEmpty());assertTrue(SurveyGeometry.at(next).position(32.5,.5).isPresent());
    }
}
