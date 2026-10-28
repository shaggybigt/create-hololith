package dev.hololith;
import dev.hololith.compat.ShipCoordinates;
import dev.hololith.survey.SurveyStore;
import dev.hololith.survey.SurveyFollower;
import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
public final class SurveySensorEntity extends BlockEntity {
    private UUID sensorId=UUID.randomUUID();
    public SurveySensorEntity(BlockPos pos,BlockState state){super(Hololith.SENSOR_ENTITY.get(),pos,state);}
    public UUID sensorId(){return sensorId;}
    @Override public void setRemoved(){
        // Destruction, chunk unload and captured-actor teardown stop authorizing
        // scans immediately. Assembly/reload restores liveness on the next heartbeat.
        if(level instanceof ServerLevel server)SurveyStore.get(server.getServer()).offline(sensorId);
        super.setRemoved();
    }
    public static void tick(Level level,BlockPos pos,BlockState state,SurveySensorEntity sensor){
        if(!(level instanceof ServerLevel)||(level.getGameTime()+pos.asLong())%SurveyFollower.POLL_TICKS!=0)return;
        ShipCoordinates.toWorld(level,pos).ifPresent(v->SurveyStore.get(((ServerLevel)level).getServer()).heartbeat(sensor.sensorId,level.dimension().location().toString(),(int)Math.floor(v.x),(int)Math.floor(v.z)));
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.saveAdditional(tag,lookup);tag.putUUID("SensorId",sensorId);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.loadAdditional(tag,lookup);if(tag.hasUUID("SensorId"))sensorId=tag.getUUID("SensorId");}
}
