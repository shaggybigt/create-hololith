package dev.hololith.client;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import dev.hololith.Hololith;
import dev.hololith.HololithTableBlock;
import dev.hololith.HololithTableEntity;
import dev.hololith.survey.MarkerPoint;
import dev.hololith.survey.SurveyKey;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Applies mounted state to Create's existing virtual block entities. */
@EventBusSubscriber(modid=Hololith.ID,value=Dist.CLIENT)
public final class ContraptionClient {
    private static final int MAX_PENDING=256,PENDING_TICKS=200;
    private static final Map<Target,Pending> PENDING=new LinkedHashMap<>();
    private static ClientLevel observedLevel;
    private static long ticks;
    private ContraptionClient(){}

    public static void accept(int entityId,UUID entityUuid,BlockPos pos,CompoundTag tag){
        observeLevel();Target target=new Target(entityId,entityUuid,pos.immutable());Pending pending=PENDING.get(target);
        if(pending!=null||!applyState(target,tag)){pending=pending(target);pending.state=tag.copy();pending.markers=null;}
    }
    public static void acceptMarkers(int entityId,UUID entityUuid,BlockPos pos,UUID tableId,SurveyKey key,List<MarkerPoint> points){
        observeLevel();Target target=new Target(entityId,entityUuid,pos.immutable());MarkerUpdate update=new MarkerUpdate(tableId,key,List.copyOf(points));
        if(PENDING.containsKey(target)||!applyMarkers(target,update))pending(target).markers=update;
    }
    public static void open(int entityId,UUID entityUuid,BlockPos pos){
        observeLevel();Target target=new Target(entityId,entityUuid,pos.immutable());
        if(PENDING.containsKey(target)||!openPanel(target))pending(target).open=true;
    }
    private static Pending pending(Target target){
        Pending existing=PENDING.get(target);if(existing!=null){existing.expires=ticks+PENDING_TICKS;return existing;}
        if(PENDING.size()>=MAX_PENDING){var oldest=PENDING.keySet().iterator();oldest.next();oldest.remove();}
        Pending created=new Pending();created.expires=ticks+PENDING_TICKS;PENDING.put(target,created);return created;
    }
    private static AbstractContraptionEntity entity(Target target){
        if(observedLevel==null)return null;
        return observedLevel.getEntity(target.entityId) instanceof AbstractContraptionEntity entity&&entity.getUUID().equals(target.entityUuid)?entity:null;
    }
    private static boolean stale(Target target){
        var current=observedLevel==null?null:observedLevel.getEntity(target.entityId);
        return current!=null&&(!(current instanceof AbstractContraptionEntity)||!current.getUUID().equals(target.entityUuid));
    }
    private static BlockEntity blockEntity(Target target){
        AbstractContraptionEntity entity=entity(target);
        if(entity==null||entity.getContraption()==null||!entity.getContraption().getBlocks().containsKey(target.pos))return null;
        return entity.getContraption().getOrCreateClientContraptionLazy().getBlockEntity(target.pos);
    }
    private static boolean applyState(Target target,CompoundTag tag){
        if(stale(target))return true;
        AbstractContraptionEntity entity=entity(target);if(entity==null||entity.getContraption()==null)return false;
        var contraption=entity.getContraption();StructureBlockInfo info=contraption.getBlocks().get(target.pos);if(info==null)return false;
        String identity=info.state().is(Hololith.TABLE.get())&&info.state().getValue(HololithTableBlock.PART)==4?"TableId":
            info.state().is(Hololith.MARKER.get())?"MarkerId":
            info.state().is(Hololith.SENSOR.get())||info.state().is(Hololith.BEACON.get())?"SensorId":null;
        if(identity==null||!tag.hasUUID(identity)||info.nbt()!=null&&info.nbt().hasUUID(identity)&&!info.nbt().getUUID(identity).equals(tag.getUUID(identity)))return true;
        CompoundTag stored=tag.copy();
        StructureBlockInfo updated=new StructureBlockInfo(info.pos(),info.state(),stored);
        contraption.getBlocks().put(target.pos,updated);
        var actor=contraption.getActorAt(target.pos);if(actor!=null){actor.setLeft(updated);if(actor.getRight()!=null)actor.getRight().blockEntityData=stored;}
        BlockEntity blockEntity=blockEntity(target);if(blockEntity==null)return false;
        blockEntity.handleUpdateTag(stored,observedLevel.registryAccess());return true;
    }
    private static boolean applyMarkers(Target target,MarkerUpdate update){
        if(stale(target))return true;
        BlockEntity blockEntity=blockEntity(target);if(blockEntity==null)return false;
        if(!(blockEntity instanceof HololithTableEntity table)||!table.tableId().equals(update.tableId))return true;
        table.acceptMarkers(update.tableId,update.key,update.points);
        var info=entity(target).getContraption().getBlocks().get(target.pos);CompoundTag stored=info.nbt();
        if(stored!=null){
            stored.put("Markers",MarkerPoint.save(update.points));
            if(update.key!=null){stored.putString("MarkerDimension",update.key.dimension());stored.putInt("MarkerX",update.key.x());stored.putInt("MarkerZ",update.key.z());stored.putInt("MarkerSpan",update.key.span());}
            else{stored.remove("MarkerDimension");stored.remove("MarkerX");stored.remove("MarkerZ");stored.remove("MarkerSpan");}
        }
        return true;
    }
    private static boolean openPanel(Target target){
        if(stale(target))return true;
        BlockEntity blockEntity=blockEntity(target);if(blockEntity==null)return false;
        if(blockEntity instanceof HololithTableEntity table)HololithControls.open(table,target.entityId);return true;
    }
    private static void observeLevel(){
        ClientLevel current=Minecraft.getInstance().level;if(observedLevel!=current){PENDING.clear();observedLevel=current;}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        observeLevel();ticks++;var iterator=PENDING.entrySet().iterator();
        while(iterator.hasNext()){
            var entry=iterator.next();Target target=entry.getKey();Pending pending=entry.getValue();
            if(ticks>=pending.expires||stale(target)){iterator.remove();continue;}
            if(pending.state!=null){if(!applyState(target,pending.state))continue;pending.state=null;}
            if(pending.markers!=null){if(!applyMarkers(target,pending.markers))continue;pending.markers=null;}
            if(pending.open&&!openPanel(target))continue;iterator.remove();
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){PENDING.clear();observedLevel=null;}
    private record Target(int entityId,UUID entityUuid,BlockPos pos){}
    private record MarkerUpdate(UUID tableId,SurveyKey key,List<MarkerPoint> points){}
    private static final class Pending{private CompoundTag state;private MarkerUpdate markers;private boolean open;private long expires;}
}
