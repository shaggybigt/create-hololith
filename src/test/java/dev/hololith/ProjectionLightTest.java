package dev.hololith;

import dev.hololith.client.ProjectionLight;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectionLightTest {
    @Test void shimmerStaysSubtleAndContinuousAcrossFrames(){
        float previous=ProjectionLight.modulation(0);
        float minimum=previous,maximum=previous;
        for(int frame=1;frame<36000;frame++){
            float value=ProjectionLight.modulation(frame/3.0);
            assertTrue(value>=.964f&&value<=1f);
            assertTrue(Math.abs(value-previous)<.003f,"No flashes between 60 FPS samples");
            minimum=Math.min(minimum,value);maximum=Math.max(maximum,value);previous=value;
        }
        assertTrue(maximum-minimum>.02f);
    }
}
