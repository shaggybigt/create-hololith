package dev.hololith.survey;
import dev.hololith.*;
import java.util.*;
import net.minecraft.server.MinecraftServer;
/** Loaded block entities announce themselves. No world searches, chunk loading or sight tests. */
public final class ReferenceRegistry {
    private static final Map<MinecraftServer,ReferenceRegistry> SERVERS=new WeakHashMap<>();
    private final Map<Link,Map<UUID,Entry>> groups=new HashMap<>();
    private final Map<UUID,Link> membership=new HashMap<>();
    private record Link(int kind,UUID target){}
    private record Entry(ReferenceMarkerEntity owner,String dimension,MarkerPoint point,int seen){}
    public static ReferenceRegistry get(MinecraftServer server){return SERVERS.computeIfAbsent(server,s->new ReferenceRegistry());}
    public static void clear(MinecraftServer server){SERVERS.remove(server);}
    public void update(MinecraftServer server,ReferenceMarkerEntity marker,String dimension,double x,double z){
        if(marker.targetId()==null)return;
        var link=new Link(marker.targetKind(),marker.targetId());var old=membership.put(marker.markerId(),link);
        if(old!=null&&!old.equals(link))removeFrom(old,marker.markerId());
        // Sub-pixel physics noise should not produce new packets for an otherwise resting body.
        x=Math.rint(x*256)/256;z=Math.rint(z*256)/256;
        var point=new MarkerPoint(marker.markerId(),x,z,marker.getBlockState().getValue(ReferenceMarkerBlock.COLOR).getTextureDiffuseColor());
        groups.computeIfAbsent(link,k->new HashMap<>()).put(marker.markerId(),new Entry(marker,dimension,point,server.getTickCount()));
    }
    private void removeFrom(Link link,UUID id){var group=groups.get(link);if(group!=null){group.remove(id);if(group.isEmpty())groups.remove(link);}}
    public void remove(ReferenceMarkerEntity marker){
        var link=membership.get(marker.markerId());var group=link==null?null:groups.get(link);var e=group==null?null:group.get(marker.markerId());
        // A removed source BE must not unregister the replacement created during ship assembly.
        if(e!=null&&e.owner==marker){removeFrom(link,marker.markerId());membership.remove(marker.markerId());}
    }
    public void tick(MinecraftServer server){
        if(server.getTickCount()%20!=0)return;
        var it=groups.entrySet().iterator();
        while(it.hasNext()){var group=it.next();group.getValue().entrySet().removeIf(e->{boolean expired=server.getTickCount()-e.getValue().seen>20||e.getValue().owner.isRemoved();if(expired)membership.remove(e.getKey());return expired;});if(group.getValue().isEmpty())it.remove();}
    }
    public List<MarkerPoint> forTable(MinecraftServer server,HololithTableEntity table){
        var s=table.snapshot();if(s==null)return List.of();
        var points=new TreeMap<UUID,MarkerPoint>();collect(server,new Link(0,table.tableId()),s,points);
        if(table.mode()==1&&table.sensorId()!=null)collect(server,new Link(1,table.sensorId()),s,points);
        return points.values().stream().limit(MarkerPoint.LIMIT).toList();
    }
    private void collect(MinecraftServer server,Link link,SurveySnapshot s,Map<UUID,MarkerPoint> points){
        var group=groups.get(link);if(group==null)return;
        for(var e:group.values())if(server.getTickCount()-e.seen<=20&&!e.owner.isRemoved()&&e.dimension.equals(s.dimension)&&MarkerPoint.inside(s,e.point.x(),e.point.z()))points.put(e.point.id(),e.point);
    }
}
