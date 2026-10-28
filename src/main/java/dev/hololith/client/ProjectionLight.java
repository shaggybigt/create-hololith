package dev.hololith.client;

/** A bounded, continuous variation in light output; never changes geometry. */
public final class ProjectionLight {
    private ProjectionLight() {}
    public static float modulation(double ticks){
        // At 20 TPS, these overlapping waves are approximately 0.5 and 1.5 Hz.
        // Their combined depth is only 3.6%; no sudden dark frames or flashes.
        return (float)(1-.012*(1+Math.sin(ticks*.47))-.006*(1+Math.sin(ticks*.17+1.3)));
    }
}
