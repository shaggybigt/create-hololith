package dev.hololith.survey;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

/** Pure guards shared by render resolution and tests. Authority always stays with the server frame. */
public final class MarkerRenderPosition {
    private MarkerRenderPosition(){}
    public static boolean linked(UUID markerId,UUID actualId,UUID target,int kind,UUID tableId,UUID sensorId,int mode){
        return markerId!=null&&markerId.equals(actualId)&&target!=null
            &&(kind==0?target.equals(tableId):kind==1&&mode==1&&target.equals(sensorId));
    }
    public static Vec3 choose(MarkerPoint authoritative,Optional<Vec3> local){
        if(local.isPresent()){
            Vec3 p=local.get();
            if(Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z)
                &&Math.abs(p.x)<=30_000_000D&&Math.abs(p.z)<=30_000_000D)return p;
        }
        return new Vec3(authoritative.x(),0,authoritative.z());
    }
}
