package dev.hololith.power;

/** Shared survival balance and pure power rules; no client or world dependencies. */
public final class ProjectionPower {
    public static final int MINIMUM_RPM=16;
    private ProjectionPower() {}
    public enum State {
        UNPOWERED("unpowered"), TOO_SLOW("too_slow"), OVERSTRESSED("overstressed"),
        MOUNTED("mounted"), RUNNING("running");
        private final String name;
        State(String name){this.name=name;}
        public String key(){return "power.hololith."+name;}
        public static State read(String value){
            for(State state:values())if(state.name().equals(value))return state;
            return UNPOWERED;
        }
    }
    public static float impact(int span,int zoom){
        float base=span==32?4:span==128?16:8;
        float magnification=1+Math.clamp(zoom,0,2)*.25f;
        return base*magnification*magnification;
    }
    public static State state(boolean mounted,boolean connected,boolean overstressed,float speed){
        if(mounted)return State.MOUNTED;
        if(!connected||!Float.isFinite(speed))return State.UNPOWERED;
        if(overstressed)return State.OVERSTRESSED;
        if(Math.abs(speed)<.001f)return State.UNPOWERED;
        return Math.abs(speed)<MINIMUM_RPM?State.TOO_SLOW:State.RUNNING;
    }
}
