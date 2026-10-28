package dev.hololith;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SurveyCardContractTest {
    @Test void legacyCoordinatesCannotAuthorizeASurvey(){
        CompoundTag card=new CompoundTag();
        card.putString("Dimension","minecraft:overworld");card.putInt("X",4096);card.putInt("Z",-4096);
        assertFalse(SurveyCardItem.isSensorCard(card));
        assertFalse(SurveyCardItem.isSensorCard(new CompoundTag()));
        assertFalse(SurveyCardItem.isSensorCard(null));
    }
    @Test void aPhysicalSourceIdentityDoesNotNeedCoordinateFallback(){
        CompoundTag card=new CompoundTag();card.putUUID("SensorId",UUID.randomUUID());
        assertTrue(SurveyCardItem.isSensorCard(card));
        card.putString("Dimension","invalid informational text");card.putInt("X",Integer.MAX_VALUE);card.putInt("Z",Integer.MIN_VALUE);
        assertTrue(SurveyCardItem.isSensorCard(card)); // The server resolves this UUID; these fields are never targets.
    }
    @Test void malformedAndAmbiguousIdentitiesAreRejected(){
        CompoundTag card=new CompoundTag();card.putString("SensorId",UUID.randomUUID().toString());
        assertFalse(SurveyCardItem.isSensorCard(card));
        card.putIntArray("SensorId",new int[]{1,2,3});assertFalse(SurveyCardItem.isSensorCard(card));
        card.putUUID("SensorId",UUID.randomUUID());card.putUUID("TableId",UUID.randomUUID());
        assertFalse(SurveyCardItem.isSensorCard(card));
    }
}
