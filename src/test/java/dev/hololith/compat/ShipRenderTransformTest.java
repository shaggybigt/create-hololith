package dev.hololith.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ShipRenderTransformTest {
    @Test void entityGeometryIsRotatedAboutTheAlreadyTransformedOriginWithoutAnotherScale(){
        // A scale-two ship already moved the entity origin from x=4 to x=8.
        // Its renderer keeps a two-block entity offset two blocks long, then rotates it.
        Vec3 origin=new Vec3(8,30,12),relative=new Vec3(2,0,0);
        var result=ShipCoordinates.composeRenderedEntity(origin,relative,new Quaterniond().rotationY(Math.PI/2)).orElseThrow();
        assertEquals(8,result.x,1e-9);assertEquals(30,result.y,1e-9);assertEquals(10,result.z,1e-9);
        assertEquals(2,result.distanceTo(origin),1e-9);
    }
    @Test void invalidEntityRenderTransformsCannotLeakIntoProjection(){
        assertTrue(ShipCoordinates.composeRenderedEntity(Vec3.ZERO,new Vec3(Double.NaN,0,0),new Quaterniond()).isEmpty());
        assertTrue(ShipCoordinates.composeRenderedEntity(Vec3.ZERO,new Vec3(1,0,0),new Quaterniond(Double.NaN,0,0,1)).isEmpty());
        assertTrue(ShipCoordinates.composeRenderedEntity(new Vec3(30_000_000,0,0),new Vec3(1,0,0),new Quaterniond()).isEmpty());
        assertTrue(ShipCoordinates.toRenderWorld(null,BlockPos.ZERO,Vec3.ZERO,Vec3.ZERO,UUID.randomUUID(),Float.NaN).isEmpty());
    }
}
