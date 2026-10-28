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
    /** A recorded physical instrument identity is required; coordinates are descriptive only. */
    public static boolean isSensorCard(CompoundTag card){return card!=null&&card.hasUUID("SensorId")&&!card.contains("TableId");}
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
        if(be instanceof SurveySensorEntity sensor){
            if(!level.isClientSide){
                var world=ShipCoordinates.toWorld(level,pos);if(world.isEmpty()){SurveyFeedback.tell(player,"message.hololith.position_unavailable");return InteractionResult.FAIL;}
                SurveyFeedback.recordLocation(player,context.getItemInHand(),sensor,world.get());
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if(player.isShiftKeyDown()){
            if(!level.isClientSide)SurveyFeedback.tell(player,"message.hololith.sensor_required");
            return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
        CompoundTag t=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if(t.hasUUID("TableId"))lines.add(Component.translatable("tooltip.hololith.card.table",SurveyFeedback.shortId(t.getUUID("TableId"))).withStyle(net.minecraft.ChatFormatting.AQUA));
        else if(t.hasUUID("SensorId"))lines.add(Component.translatable("tooltip.hololith.card.sensor",SurveyFeedback.shortId(t.getUUID("SensorId"))).withStyle(net.minecraft.ChatFormatting.AQUA));
        else lines.add(Component.translatable(t.contains("Dimension")?"tooltip.hololith.card.legacy_coordinates":"tooltip.hololith.card.blank")
                .withStyle(t.contains("Dimension")?net.minecraft.ChatFormatting.GOLD:net.minecraft.ChatFormatting.AQUA));
        if(isSensorCard(t)&&t.contains("Dimension")){
            lines.add(Component.translatable("tooltip.hololith.card.recorded_position",t.getInt("X"),t.getInt("Z")).withStyle(net.minecraft.ChatFormatting.GRAY));
            lines.add(Component.literal(t.getString("Dimension")).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
    }
}
