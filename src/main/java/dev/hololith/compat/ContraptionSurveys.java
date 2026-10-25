package dev.hololith.compat;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import dev.hololith.*;
import dev.hololith.survey.SurveyStore;
import dev.hololith.survey.SurveyFollower;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Server-side captured state. Runtime BEs are never installed or ticked in real chunks. */
public final class ContraptionSurveys implements MovementBehaviour {
    private static final ContraptionSurveys BEHAVIOUR=new ContraptionSurveys();
    private ContraptionSurveys() {}

    public static void register(){
        MovementBehaviour.REGISTRY.register(Hololith.TABLE.get(),BEHAVIOUR);
        MovementBehaviour.REGISTRY.register(Hololith.SENSOR.get(),BEHAVIOUR);
        MovementBehaviour.REGISTRY.register(Hololith.BEACON.get(),BEHAVIOUR);
        MovementBehaviour.REGISTRY.register(Hololith.MARKER.get(),BEHAVIOUR);
        // Permit Create assembly without permitting vanilla pistons to shear the table.
        BlockMovementChecks.registerMovementAllowedCheck((state,level,pos)->state.is(Hololith.TABLE.get())?BlockMovementChecks.CheckResult.SUCCESS:BlockMovementChecks.CheckResult.PASS);
        BlockMovementChecks.registerAttachedCheck((state,level,pos,direction)->{
            if(!state.is(Hololith.TABLE.get()))return BlockMovementChecks.CheckResult.PASS;
            BlockPos adjacent=pos.relative(direction);var neighbor=level.getBlockState(adjacent);
            return neighbor.is(Hololith.TABLE.get())&&HololithTableBlock.anchor(pos,state).equals(HololithTableBlock.anchor(adjacent,neighbor))
                ?BlockMovementChecks.CheckResult.SUCCESS:BlockMovementChecks.CheckResult.PASS;
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.StartTracking event)->{
            if(event.getEntity() instanceof ServerPlayer player&&event.getTarget() instanceof AbstractContraptionEntity entity)ContraptionPackets.track(player,entity);
        });
    }

    /** Instruments remain available when Create's generic actor controls are toggled. */
    @Override public ItemStack canBeDisabledVia(MovementContext context){return null;}

    public static MovementContext context(AbstractContraptionEntity entity,BlockPos localPos){
        if(entity==null||entity.getContraption()==null)return null;
        var actor=entity.getContraption().getActorAt(localPos);
        return actor==null?null:actor.right;
    }

    public static Optional<Vec3> worldPosition(AbstractContraptionEntity entity,BlockPos localPos){
        if(entity==null||entity.isRemoved()||entity.getContraption()==null)return Optional.empty();
        Vec3 position=entity.toGlobalVector(Vec3.atCenterOf(localPos),1);
        // A regular Create contraption may itself be mounted within a Sable plot.
        return ShipCoordinates.toWorld(entity.level(),position);
    }

    public static HololithTableEntity table(AbstractContraptionEntity entity,BlockPos localPos){
        Runtime state=runtime(context(entity,localPos));return state!=null&&state.blockEntity instanceof HololithTableEntity table?table:null;
    }
    public static SurveySensorEntity sensor(AbstractContraptionEntity entity,BlockPos localPos){
        Runtime state=runtime(context(entity,localPos));return state!=null&&state.blockEntity instanceof SurveySensorEntity sensor?sensor:null;
    }
    public static ReferenceMarkerEntity marker(AbstractContraptionEntity entity,BlockPos localPos){
        Runtime state=runtime(context(entity,localPos));return state!=null&&state.blockEntity instanceof ReferenceMarkerEntity marker?marker:null;
    }

    private static Runtime runtime(MovementContext context){
        if(context==null||!(context.world instanceof ServerLevel level)||context.contraption.entity==null)return null;
        if(context.temporaryData instanceof Runtime runtime)return runtime.blockEntity.isRemoved()?null:runtime;
        var state=context.state;
        BlockEntity blockEntity;
        if(state.is(Hololith.TABLE.get())){
            if(state.getValue(HololithTableBlock.PART)!=4)return null;
            blockEntity=new HololithTableEntity(context.localPos,state);
        }else if(state.is(Hololith.SENSOR.get())||state.is(Hololith.BEACON.get()))blockEntity=new SurveySensorEntity(context.localPos,state);
        else if(state.is(Hololith.MARKER.get()))blockEntity=new ReferenceMarkerEntity(context.localPos,state);
        else return null;
        if(context.blockEntityData!=null)blockEntity.loadWithComponents(context.blockEntityData,level.registryAccess());
        blockEntity.setLevel(level);
        Runtime runtime=new Runtime(blockEntity);context.temporaryData=runtime;
        if(blockEntity instanceof HololithTableEntity table){
            table.setMountedCallbacks(()->changed(context),
                (key,points)->ContraptionPackets.markers(context.contraption.entity,context.localPos,table.tableId(),key,points));
        }else if(blockEntity instanceof ReferenceMarkerEntity marker){
            marker.setMountedCallbacks(()->worldPosition(context.contraption.entity,context.localPos),()->changed(context));
            marker.setMountedAnchor(context.contraption.entity,context.localPos);
        }
        // Normalize legacy/missing tags once, before Create builds a save or spawn packet.
        persist(context,runtime);
        return runtime;
    }

    @Override public void tick(MovementContext context){
        Runtime runtime=runtime(context);if(runtime==null)return;
        var entity=context.contraption.entity;var level=(ServerLevel)context.world;
        if(entity.isRemoved())return;
        if(!runtime.initialSync){runtime.initialSync=true;ContraptionPackets.sync(entity,context.localPos,stateTag(context));}
        long clock=level.getGameTime()+context.localPos.asLong();
        if(runtime.blockEntity instanceof SurveySensorEntity sensor){
            if(clock%SurveyFollower.POLL_TICKS==0)worldPosition(entity,context.localPos).ifPresent(position->SurveyStore.get(level.getServer()).heartbeat(sensor.sensorId(),level.dimension().location().toString(),(int)Math.floor(position.x),(int)Math.floor(position.z)));
        }else if(runtime.blockEntity instanceof HololithTableEntity table){
            Optional<Vec3> position=table.mode()==0&&clock%SurveyFollower.POLL_TICKS==0?worldPosition(entity,context.localPos):Optional.empty();
            HololithTableEntity.tickAt(level,context.localPos,context.state,table,position);
        }else if(runtime.blockEntity instanceof ReferenceMarkerEntity marker){
            ReferenceMarkerEntity.tick(level,context.localPos,context.state,marker);
        }
    }

    public static void changed(AbstractContraptionEntity entity,BlockPos localPos){
        MovementContext context=context(entity,localPos);if(context!=null)changed(context);
    }
    private static void changed(MovementContext context){
        Runtime runtime=runtime(context);if(runtime==null)return;
        persist(context,runtime);
        ContraptionPackets.sync(context.contraption.entity,context.localPos,stateTag(context));
    }

    static CompoundTag stateTag(MovementContext context){
        Runtime runtime=runtime(context);if(runtime==null)return null;
        return runtime.blockEntity instanceof HololithTableEntity table?table.getUpdateTag(context.world.registryAccess()):runtime.blockEntity.saveWithFullMetadata(context.world.registryAccess());
    }

    private static void persist(MovementContext context,Runtime runtime){
        CompoundTag fresh=runtime.blockEntity.saveWithFullMetadata(context.world.registryAccess());
        fresh.remove("x");fresh.remove("y");fresh.remove("z");
        // writeBlocksCompound runs before writeExtraData and retains this tag by reference.
        // Use the current map entry even if another Create operation replaced the block info.
        var current=context.contraption.getBlocks().get(context.localPos);
        CompoundTag captured=current==null?context.blockEntityData:current.nbt();
        if(captured==null)captured=new CompoundTag();
        else for(String key:List.copyOf(captured.getAllKeys()))captured.remove(key);
        captured.merge(fresh);
        boolean changedState=context.state!=runtime.blockEntity.getBlockState();
        context.state=runtime.blockEntity.getBlockState();context.blockEntityData=captured;
        var info=new StructureBlockInfo(context.localPos,context.state,captured);
        var actor=context.contraption.getActorAt(context.localPos);
        if(actor!=null)actor.setLeft(info);
        if(changedState)context.contraption.entity.setBlock(context.localPos,info);
        else context.contraption.getBlocks().put(context.localPos,info);
    }

    @Override public void writeExtraData(MovementContext context){
        if(context!=null&&context.temporaryData instanceof Runtime runtime)persist(context,runtime);
    }
    @Override public void stopMoving(MovementContext context){
        if(context==null||!(context.temporaryData instanceof Runtime runtime))return;
        persist(context,runtime);runtime.blockEntity.setRemoved();context.temporaryData=null;
    }
    private static final class Runtime {
        final BlockEntity blockEntity;
        boolean initialSync;
        Runtime(BlockEntity blockEntity){this.blockEntity=blockEntity;}
    }
}
