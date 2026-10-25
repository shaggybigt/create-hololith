package dev.hololith.survey;
import java.util.*;
import net.minecraft.nbt.*;
/** A live reference position in world space. Elevation deliberately does not travel. */
public record MarkerPoint(UUID id,double x,double z,int color) {
    public MarkerPoint { color&=0xFFFFFF; }
    public static final int LIMIT=64;
    public static boolean inside(SurveySnapshot s,double x,double z){
        if(s==null||!Double.isFinite(x)||!Double.isFinite(z))return false;
        double dx=x-(s.centerX-s.span/2.0),dz=z-(s.centerZ-s.span/2.0);
        if(dx<0||dz<0||dx>=s.span||dz>=s.span)return false;
        int i=(int)(dz*s.size/s.span)*s.size+(int)(dx*s.size/s.span);
        return (s.flags[i]&1)!=0;
    }
    public static ListTag save(List<MarkerPoint> points){
        ListTag list=new ListTag();for(var p:points.stream().limit(LIMIT).toList()){
            CompoundTag t=new CompoundTag();t.putUUID("Id",p.id);t.putDouble("X",p.x);t.putDouble("Z",p.z);t.putInt("Color",p.color);list.add(t);
        }return list;
    }
    public static List<MarkerPoint> load(ListTag list){
        var result=new ArrayList<MarkerPoint>();
        for(int i=0;i<Math.min(LIMIT,list.size());i++){var t=list.getCompound(i);double x=t.getDouble("X"),z=t.getDouble("Z");
            if(t.hasUUID("Id")&&Double.isFinite(x)&&Double.isFinite(z))result.add(new MarkerPoint(t.getUUID("Id"),x,z,t.getInt("Color")&0xFFFFFF));
        }return List.copyOf(result);
    }
}
