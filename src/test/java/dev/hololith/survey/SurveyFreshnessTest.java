package dev.hololith.survey;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SurveyFreshnessTest {
    @Test void missingCompletionNeverProvesLiveness(){
        assertFalse(SurveyManager.recentlyChecked(null,1000,100));
    }
    @Test void completionRemainsRecentThroughRefreshIntervalAndGrace(){
        assertTrue(SurveyManager.recentlyChecked(1000,1000,100));
        assertTrue(SurveyManager.recentlyChecked(1000,1100,100));
        assertTrue(SurveyManager.recentlyChecked(1000,1140,100));
        assertFalse(SurveyManager.recentlyChecked(1000,1141,100));
    }
    @Test void returningToAnOldSurveyRequiresAnotherCompletedScan(){
        assertFalse(SurveyManager.recentlyChecked(1000,5000,100));
        assertTrue(SurveyManager.recentlyChecked(5000,5000,100));
    }
    @Test void configuredRefreshCadenceControlsTheFreshnessWindow(){
        assertTrue(SurveyManager.recentlyChecked(1000,1080,40));
        assertFalse(SurveyManager.recentlyChecked(1000,1081,40));
        assertTrue(SurveyManager.recentlyChecked(1000,2240,1200));
        assertFalse(SurveyManager.recentlyChecked(1000,2241,1200));
    }
    @Test void backwardsOrOverflowingTicksCannotMakeStaleDataLive(){
        assertFalse(SurveyManager.recentlyChecked(1000,999,100));
        assertFalse(SurveyManager.recentlyChecked(Integer.MAX_VALUE,Integer.MIN_VALUE,100));
        assertFalse(SurveyManager.recentlyChecked(Integer.MIN_VALUE,Integer.MAX_VALUE,100));
    }
}
