package dev.hololith;
import dev.hololith.survey.SurveySnapshot;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SnapshotValidationTest {
    @Test void malformedOrFutureSnapshotsAreNotRendered(){
        var tag=new CompoundTag();tag.putInt("Format",2);tag.putInt("Span",64);
        assertNull(SurveySnapshot.load(tag,null));
        tag.putInt("Format",1);tag.putInt("Span",65536);
        assertNull(SurveySnapshot.load(tag,null));
        tag.putInt("Span",64);tag.putIntArray("GroundY",new int[16]);
        assertNull(SurveySnapshot.load(tag,null));
    }
}
