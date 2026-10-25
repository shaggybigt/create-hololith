package dev.hololith.survey;

/** One outstanding region per table. Finish useful work before following the latest target. */
public final class SurveyFollower {
    /** Lightweight position/poll cadence; terrain work remains globally budgeted by SurveyManager. */
    public static final int POLL_TICKS=5;
    public interface Source {
        SurveySnapshot cached(SurveyKey key);
        SurveySnapshot request(SurveyKey key);
        boolean isPending(SurveyKey key);
    }
    public record Update(SurveySnapshot snapshot,boolean pending) {}
    private SurveyKey active;

    public void reset(){active=null;}

    public Update poll(SurveyKey target,Source source){
        SurveySnapshot completed=null;
        if(active!=null&&!active.equals(target)){
            if(source.isPending(active)){
                // Keep a slow scan alive rather than canceling it on every movement.
                source.request(active);
                SurveySnapshot current=source.cached(target);
                // Null means retain the table's last display; an old cache must not roll it back.
                return new Update(current,true);
            }
            // Consume the completed region WITHOUT scheduling another scan of the old location.
            completed=source.cached(active);
            active=null;
        }
        // The same poll starts the newest region. No additional polling interval is lost.
        SurveySnapshot current=source.request(target);
        active=source.isPending(target)?target:null;
        return new Update(current!=null?current:completed,active!=null);
    }
}
