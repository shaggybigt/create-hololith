package dev.hololith;
import dev.hololith.compat.ShipCoordinates;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;

public final class SurveyCardItem extends Item {
    public SurveyCardItem(Properties properties){super(properties);}
    @Override public InteractionResult useOn(UseOnContext context){
        var level=context.getLevel();var pos=context.getClickedPos();var player=context.getPlayer();if(player==null)return InteractionResult.PASS;
        var state=level.getBlockState(pos);var be=level.getBlockEntity(pos);
        if(!player.mayBuild()||player.isSpectator()||!level.mayInteract(player,pos))return InteractionResult.FAIL;
        if(be instanceof ReferenceMarkerEntity marker){
            if(!level.isClientSide){
                SurveyFeedback.markerCard(player,context.getItemInHand(),marker,ShipCoordinates.toWorld(level,pos).orElse(null));
            }return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if(state.is(Hololith.TABLE.get())){
            if(!level.isClientSide&&level.getBlockEntity(HololithTableBlock.anchor(pos,state)) instanceof HololithTableEntity table){
                SurveyFeedback.tableCard(player,context.getItemInHand(),table,ShipCoordinates.toWorld(level,table.getBlockPos()).orElse(null));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if(be instanceof SurveySensorEntity||player.isShiftKeyDown()){
            if(!level.isClientSide){
                var world=ShipCoordinates.toWorld(level,pos);if(world.isEmpty()){SurveyFeedback.tell(player,"message.hololith.position_unavailable");return InteractionResult.FAIL;}
                SurveyFeedback.recordLocation(player,context.getItemInHand(),be instanceof SurveySensorEntity sensor?sensor:null,world.get());
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
        CompoundTag t=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if(t.hasUUID("TableId"))lines.add(Component.translatable("tooltip.hololith.card.table",SurveyFeedback.shortId(t.getUUID("TableId"))).withStyle(net.minecraft.ChatFormatting.AQUA));
        else if(t.hasUUID("SensorId"))lines.add(Component.translatable("tooltip.hololith.card.sensor",SurveyFeedback.shortId(t.getUUID("SensorId"))).withStyle(net.minecraft.ChatFormatting.AQUA));
        else lines.add(Component.translatable(t.contains("Dimension")?"tooltip.hololith.card.coordinates":"tooltip.hololith.card.blank").withStyle(net.minecraft.ChatFormatting.AQUA));
        if(t.contains("Dimension")){
            lines.add(Component.translatable(t.hasUUID("SensorId")?"tooltip.hololith.card.recorded_position":"tooltip.hololith.card.position",t.getInt("X"),t.getInt("Z")).withStyle(net.minecraft.ChatFormatting.GRAY));
            lines.add(Component.literal(t.getString("Dimension")).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
    }
}
