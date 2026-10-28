package dev.hololith;

import dev.hololith.survey.SurveyStore;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;

/** Shared feedback and card actions for world blocks and captured Create instruments. */
public final class SurveyFeedback {
    private SurveyFeedback() {}
    public static String shortId(java.util.UUID id){return id.toString().substring(0,8);}
    public static void tell(Player player,String key,Object... args){player.displayClientMessage(Component.translatable(key,args),true);}
    public static void success(Player player,Vec3 position,String key,Object... args){
        tell(player,key,args);
        if(player instanceof ServerPlayer server){
            server.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.BLOCKS,.18f,1.4f);
            if(position!=null)server.serverLevel().sendParticles(server,ParticleTypes.END_ROD,false,
                position.x,position.y+.3,position.z,4,.12,.08,.12,0);
        }
    }
    public static void dial(Player player,HololithTableEntity table,boolean scale){
        if(scale)tell(player,"message.hololith.area",table.span(),table.span());
        else tell(player,"message.hololith.rotation",table.rotation()*90);
        if(player instanceof ServerPlayer server)server.playNotifySound(SoundEvents.LEVER_CLICK,SoundSource.BLOCKS,.25f,1.2f);
    }
    public static void configured(Player player,HololithTableEntity table,Vec3 world){
        String source=table.mode()==1?"message.hololith.source_sensor":"message.hololith.source_table";
        success(player,world,"message.hololith.configured",Component.translatable(source),table.span());
    }
    private static void record(Player player,ItemStack stack,CompoundTag tag){
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));player.getInventory().setChanged();
    }
    public static void tableCard(Player player,ItemStack stack,HololithTableEntity table,Vec3 world){
        CompoundTag card=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if(player.isShiftKeyDown()){
            CompoundTag target=new CompoundTag();target.putUUID("TableId",table.tableId());record(player,stack,target);
            success(player,world,"message.hololith.table_recorded",shortId(table.tableId()));
        }else if(card.hasUUID("TableId"))tell(player,"message.hololith.table_card_hint");
        else if(!SurveyCardItem.isSensorCard(card))tell(player,card.isEmpty()?"message.hololith.card_empty":"message.hololith.sensor_required");
        else if(table.allowControl()){
            if(table.bind(card))success(player,world,"message.hololith.table_linked_sensor",shortId(card.getUUID("SensorId")));
            else tell(player,"message.hololith.sensor_required");
        }
    }
    public static void markerCard(Player player,ItemStack stack,ReferenceMarkerEntity marker,Vec3 world){
        if(marker.bind(stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag()))
            success(player,world,"message.hololith.reference_linked",targetKind(marker),shortId(marker.targetId()));
        else tell(player,"message.hololith.reference_card_hint");
    }
    public static void recordLocation(Player player,ItemStack stack,SurveySensorEntity sensor,Vec3 world){
        if(sensor==null){tell(player,"message.hololith.sensor_required");return;}
        CompoundTag tag=new CompoundTag();tag.putString("Dimension",player.level().dimension().location().toString());
        tag.putInt("X",(int)Math.floor(world.x));tag.putInt("Z",(int)Math.floor(world.z));
        tag.putUUID("SensorId",sensor.sensorId());
        if(player instanceof ServerPlayer server)SurveyStore.get(server.serverLevel().getServer()).heartbeat(sensor.sensorId(),tag.getString("Dimension"),tag.getInt("X"),tag.getInt("Z"));
        record(player,stack,tag);
        success(player,world,"message.hololith.sensor_recorded",shortId(sensor.sensorId()));
    }
    public static void unlinkMarker(Player player,ReferenceMarkerEntity marker,Vec3 world){
        if(marker.targetId()==null){tell(player,"message.hololith.reference_already_unlinked");return;}
        marker.unlink();success(player,world,"message.hololith.reference_unlinked");
    }
    public static void inspectMarker(Player player,ReferenceMarkerEntity marker){
        if(marker.targetId()==null)tell(player,"message.hololith.reference_unlinked_hint");
        else tell(player,"message.hololith.reference_status",targetKind(marker),shortId(marker.targetId()),
            colorName(marker.getBlockState().getValue(ReferenceMarkerBlock.COLOR)));
    }
    public static Component colorName(net.minecraft.world.item.DyeColor color){return Component.translatable("color.hololith."+color.getName());}
    private static Component targetKind(ReferenceMarkerEntity marker){return Component.translatable(marker.targetKind()==0?"message.hololith.target_table":"message.hololith.target_sensor");}
}
