package dev.hololith;

import dev.hololith.survey.SurveyKey;
import dev.hololith.survey.SurveySnapshot;
import dev.hololith.survey.SurveyStatus;
import java.util.Arrays;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SurveyStatusTest {
    private static final SurveyKey TARGET=new SurveyKey("minecraft:overworld",0,0,32);
    private SurveySnapshot snapshot(int known,int loaded){
        int n=TARGET.size()*TARGET.size();byte[] flags=new byte[n];Arrays.fill(flags,0,known,(byte)1);
        return new SurveySnapshot(TARGET,1000,1,new int[n],new int[n],new int[n],new int[n],
            new BlockState[n],new BlockState[n],flags,loaded);
    }
    private String status(SurveySnapshot survey,boolean online,boolean checked,boolean pending){
        return SurveyStatus.describe(survey,TARGET,online,checked,pending,true);
    }
    @Test void completeOnlineVerifiedTerrainIsLiveEvenDuringNextRefresh(){
        assertEquals("Live survey",status(snapshot(1024,1024),true,true,false));
        assertEquals("Live survey",status(snapshot(1024,1024),true,true,true));
    }
    @Test void historicalLoadedCountDoesNotProveLivenessAfterRestart(){
        String text=status(snapshot(1024,1024),true,false,true);
        assertTrue(text.startsWith("Cached survey"));assertFalse(text.contains("Live"));
        assertTrue(text.contains("checking terrain"));
    }
    @Test void offlineSourcePreservesIncompleteCacheWithoutClaimingLive(){
        String text=status(snapshot(256,256),false,true,false);
        assertTrue(text.startsWith("Sensor offline"));assertTrue(text.contains("cached survey"));
        assertTrue(text.contains("256/1024 known"));assertFalse(text.contains("Live"));
        assertEquals("Sensor offline · no saved survey",status(null,false,true,false));
    }
    @Test void unknownColumnsDifferFromKnownButCurrentlyUnloadedColumns(){
        String incomplete=status(snapshot(512,256),true,true,false);
        assertTrue(incomplete.startsWith("Incomplete survey"));assertTrue(incomplete.contains("512/1024 known"));
        assertTrue(incomplete.contains("256 loaded"));
        String mixed=status(snapshot(1024,256),true,true,false);
        assertTrue(mixed.startsWith("Live + cached terrain"));assertTrue(mixed.contains("256/1024 loaded"));
        assertFalse(mixed.contains("Incomplete"));
    }
    @Test void fullyUnloadedTerrainRetainsKnownCoverageAsCache(){
        String complete=status(snapshot(1024,0),true,true,false);
        String partial=status(snapshot(128,0),true,true,false);
        assertTrue(complete.startsWith("Cached survey"));assertTrue(complete.contains("chunks unloaded"));
        assertFalse(complete.contains("Live"));assertTrue(partial.contains("128/1024 known"));
    }
    @Test void noSnapshotDistinguishesWorkInProgressFromUnavailableTerrain(){
        assertTrue(status(null,true,false,true).startsWith("Surveying"));
        assertTrue(status(null,true,true,false).startsWith("Unavailable"));
        assertEquals("Unavailable · no surface terrain",status(snapshot(0,1024),true,true,false));
    }
    @Test void unavailableDimensionAndPositionDoNotDiscardTheSavedSurvey(){
        var saved=snapshot(256,256);
        String text=SurveyStatus.describe(saved,TARGET,true,true,false,false);
        assertTrue(text.startsWith("Dimension unavailable"));assertTrue(text.contains("256/1024 known"));assertFalse(text.contains("Live"));
        assertEquals("Position unavailable · no saved survey",SurveyStatus.retained("Position unavailable",null));
        assertTrue(SurveyStatus.retained("Position unavailable",saved).contains("cached survey"));
    }
    @Test void movingTargetNeverLabelsPreviousLocationAsLive(){
        var next=new SurveyKey("minecraft:overworld",32,0,32);
        String text=SurveyStatus.describe(snapshot(1024,1024),next,true,true,true,true);
        assertTrue(text.startsWith("Updating location"));assertTrue(text.contains("cached survey"));assertFalse(text.contains("Live"));
    }
}
