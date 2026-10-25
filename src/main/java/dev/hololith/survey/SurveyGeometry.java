package dev.hololith.survey;
import java.util.Optional;
/** Shared terrain/marker projection math, relative to the center of the instrument. */
public final class SurveyGeometry {
    public static final float WIDTH=2.3f;
    private final SurveySnapshot snapshot;
    private final int minY;
    private final float step;
    private SurveyGeometry(SurveySnapshot snapshot,int minY,float step){this.snapshot=snapshot;this.minY=minY;this.step=step;}
    public static SurveyGeometry at(SurveySnapshot s){
        int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE;
        for(int i=0;i<s.flags.length;i++)if((s.flags[i]&1)!=0){min=Math.min(min,s.groundY[i]);max=Math.max(max,Math.max(s.groundY[i],s.surfaceY[i])+1);}
        if(min==Integer.MAX_VALUE)return new SurveyGeometry(s,0,0);
        return new SurveyGeometry(s,min,Math.min(WIDTH/s.span,.715f/Math.max(1,max-min)));
    }
    public float height(int y){return .035f+Math.max(0,y-minY)*step;}
    public float step(){return step;}
    public Optional<Position> position(double x,double z){
        if(!MarkerPoint.inside(snapshot,x,z))return Optional.empty();
        int ix=(int)((x-(snapshot.centerX-snapshot.span/2.0))*snapshot.size/snapshot.span);
        int iz=(int)((z-(snapshot.centerZ-snapshot.span/2.0))*snapshot.size/snapshot.span);
        int i=iz*snapshot.size+ix;
        int y=(snapshot.flags[i]&6)!=0?Math.max(snapshot.groundY[i],snapshot.surfaceY[i]):snapshot.groundY[i];
        return Optional.of(new Position(WIDTH*(x-snapshot.centerX)/snapshot.span,height(y+1),WIDTH*(z-snapshot.centerZ)/snapshot.span));
    }
    public record Position(double x,double y,double z){}
}
