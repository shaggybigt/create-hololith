package dev.hololith.survey;
public record SurveyKey(String dimension,int x,int z,int span) {
    public static int validSpan(int span){return span==32||span==64||span==128?span:64;}
    public static SurveyKey at(String dimension,int x,int z,int span){return new SurveyKey(dimension,Math.floorDiv(x,4)*4,Math.floorDiv(z,4)*4,validSpan(span));}
    public int size(){return Math.min(span,64);}
    public int step(){return span/size();}
    public int sampleX(int index){return x-span/2+(index%size())*step();}
    public int sampleZ(int index){return z-span/2+(index/size())*step();}
    public String storageKey(){return dimension+"|"+x+"|"+z+"|"+span;}
}
