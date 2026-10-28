package dev.hololith;

import dev.hololith.power.ProjectionPower;
import dev.hololith.power.ProjectionPower.State;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectionPowerTest {
    @Test void capturedSpeedNeverPowersAnAssembledActor(){
        assertEquals(State.MOUNTED,ProjectionPower.state(true,true,false,256));
    }
    @Test void absentNetworkOrMalformedSpeedCannotAuthorizeProjection(){
        for(float speed:new float[]{0,16,-16,256,Float.NaN,Float.POSITIVE_INFINITY})
            assertEquals(State.UNPOWERED,ProjectionPower.state(false,false,false,speed));
        assertEquals(State.UNPOWERED,ProjectionPower.state(false,true,false,Float.NaN));
    }
    @Test void powerNeedsMinimumSpeedInEitherDirection(){
        assertEquals(State.UNPOWERED,ProjectionPower.state(false,true,false,0));
        for(float speed:new float[]{8,-8,15.99f,-15.99f})
            assertEquals(State.TOO_SLOW,ProjectionPower.state(false,true,false,speed));
        for(float speed:new float[]{16,-16,32,-256})
            assertEquals(State.RUNNING,ProjectionPower.state(false,true,false,speed));
    }
    @Test void overloadAlwaysStopsAnOtherwiseFastConnectedTable(){
        assertEquals(State.OVERSTRESSED,ProjectionPower.state(false,true,true,256));
        assertEquals(State.OVERSTRESSED,ProjectionPower.state(false,true,true,0));
    }
    @Test void surveyAndProjectionAreaIncreaseActualLoad(){
        assertEquals(4,ProjectionPower.impact(32,0));
        assertEquals(8,ProjectionPower.impact(64,0));
        assertEquals(16,ProjectionPower.impact(128,0));
        assertEquals(6.25f,ProjectionPower.impact(32,1));
        assertEquals(36,ProjectionPower.impact(128,2));
        assertEquals(576,ProjectionPower.impact(128,2)*ProjectionPower.MINIMUM_RPM);
    }
    @Test void unknownSavedPowerStatesFailClosed(){
        assertEquals(State.UNPOWERED,State.read("invalid"));
        assertEquals(State.UNPOWERED,State.read(""));
        assertEquals(State.RUNNING,State.read("RUNNING"));
    }
}
