package dev.hololith;
import dev.hololith.compat.ShipCoordinates;
import dev.hololith.survey.SurveyStore;
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
    public static void tick(Level level,BlockPos pos,BlockState state,SurveySensorEntity sensor){
        if((level.getGameTime()+pos.asLong())%20!=0)return;
        ShipCoordinates.toWorld(level,pos).ifPresent(v->SurveyStore.get(((ServerLevel)level).getServer()).heartbeat(sensor.sensorId,level.dimension().location().toString(),(int)Math.floor(v.x),(int)Math.floor(v.z)));
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.saveAdditional(tag,lookup);tag.putUUID("SensorId",sensorId);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.loadAdditional(tag,lookup);if(tag.hasUUID("SensorId"))sensorId=tag.getUUID("SensorId");}
}
