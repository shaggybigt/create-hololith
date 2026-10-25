package dev.hololith.survey;

/** Presentation only: a cached snapshot never proves its source is currently live. */
public final class SurveyStatus {
    private SurveyStatus() {}

    public static String describe(SurveySnapshot displayed,SurveyKey target,boolean sourceOnline,
            boolean checkedThisSession,boolean pending,boolean dimensionAvailable){
        if(!dimensionAvailable)return retained("Dimension unavailable",displayed);
        if(!sourceOnline)return retained("Sensor offline",displayed);
        if(displayed==null)return pending?"Surveying · waiting for loaded terrain":"Unavailable · visit the target to load terrain";
        if(!displayed.key().equals(target))return retained("Updating location",displayed);
        int total=displayed.size*displayed.size,known=displayed.knownColumns();
        if(known==0)return "Unavailable · no surface terrain";
        if(!checkedThisSession)return "Cached survey · checking terrain"+incomplete(known,total);
        if(displayed.loadedColumns==0)return "Cached survey · chunks unloaded"+incomplete(known,total);
        if(known<total)return "Incomplete survey · "+known+"/"+total+" known; "+displayed.loadedColumns+" loaded";
        if(displayed.loadedColumns<total)return "Live + cached terrain · "+displayed.loadedColumns+"/"+total+" loaded";
        return "Live survey";
    }

    public static String retained(String reason,SurveySnapshot displayed){
        if(displayed==null)return reason+" · no saved survey";
        int total=displayed.size*displayed.size,known=displayed.knownColumns();
        return reason+" · cached survey"+incomplete(known,total);
    }

    private static String incomplete(int known,int total){return known<total?" ("+known+"/"+total+" known)":"";}
}
