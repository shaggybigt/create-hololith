package dev.hololith.survey;

import dev.hololith.HololithConfig;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class SurveyStore extends SavedData {
    private final LinkedHashMap<String,SurveySnapshot> regions=new LinkedHashMap<>(16,.75f,true);
    private final LinkedHashMap<UUID,SensorTarget> sensors=new LinkedHashMap<>();
    public record SensorTarget(String dimension,int x,int z,long seenAt) {
        public boolean online(){return System.currentTimeMillis()-seenAt<5000;}
    }
    public static SurveyStore get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(SurveyStore::new,SurveyStore::load),"hololith_surveys");}
    public SurveySnapshot find(SurveyKey key){return regions.get(key.storageKey());}
    public void put(SurveySnapshot snapshot){
        SurveySnapshot previous=regions.put(snapshot.key().storageKey(),snapshot);
        boolean changed=previous==null||previous.revision!=snapshot.revision;
        while(regions.size()>HololithConfig.CACHE_ENTRIES.get()){regions.remove(regions.keySet().iterator().next());changed=true;}
        if(changed)setDirty();
    }
    public SensorTarget sensor(UUID id){return sensors.get(id);}
    public void heartbeat(UUID id,String dimension,int x,int z){
        SensorTarget old=sensors.put(id,new SensorTarget(dimension,x,z,System.currentTimeMillis()));
        if(old==null||old.x()!=x||old.z()!=z||!old.dimension().equals(dimension)||System.currentTimeMillis()-old.seenAt()>30000)setDirty();
        while(sensors.size()>1024){sensors.remove(sensors.keySet().iterator().next());setDirty();}
    }
    public void offline(UUID id){SensorTarget old=sensors.get(id);if(old!=null){sensors.put(id,new SensorTarget(old.dimension(),old.x(),old.z(),0));setDirty();}}
    public static SurveyStore load(CompoundTag tag,HolderLookup.Provider lookup){
        SurveyStore result=new SurveyStore();ListTag regions=tag.getList("Regions",Tag.TAG_COMPOUND);
        for(int i=0;i<Math.min(256,regions.size());i++){SurveySnapshot s=SurveySnapshot.load(regions.getCompound(i),lookup);if(s!=null)result.regions.put(s.key().storageKey(),s);}
        ListTag sensors=tag.getList("Sensors",Tag.TAG_COMPOUND);
        for(int i=0;i<Math.min(1024,sensors.size());i++){CompoundTag t=sensors.getCompound(i);if(t.hasUUID("Id"))result.sensors.put(t.getUUID("Id"),new SensorTarget(t.getString("Dimension"),t.getInt("X"),t.getInt("Z"),0));}
        return result;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup){
        tag.putInt("Format",1);ListTag r=new ListTag();regions.values().forEach(s->r.add(s.save()));tag.put("Regions",r);
        ListTag s=new ListTag();sensors.forEach((id,target)->{CompoundTag t=new CompoundTag();t.putUUID("Id",id);t.putString("Dimension",target.dimension());t.putInt("X",target.x());t.putInt("Z",target.z());s.add(t);});tag.put("Sensors",s);return tag;
    }
}
