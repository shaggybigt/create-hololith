package dev.hololith.client;

import dev.hololith.Hololith;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid=Hololith.ID,value=Dist.CLIENT)
public final class HololithTooltips {
    private HololithTooltips() {}
    @SubscribeEvent public static void tooltip(ItemTooltipEvent event){
        var stack=event.getItemStack();String kind;
        if(stack.is(Hololith.TABLE_ITEM.get()))kind="table";
        else if(stack.is(Hololith.SENSOR_ITEM.get()))kind="sensor";
        else if(stack.is(Hololith.BEACON_ITEM.get()))kind="beacon";
        else if(stack.is(Hololith.MARKER_ITEM.get()))kind="marker";
        else if(stack.is(Hololith.CARD.get()))kind="card";
        else return;
        var lines=event.getToolTip();
        if(!kind.equals("card"))lines.add(Component.translatable("tooltip.hololith."+kind).withStyle(ChatFormatting.GRAY));
        if(kind.equals("marker")){
            var state=stack.getOrDefault(net.minecraft.core.component.DataComponents.BLOCK_STATE,net.minecraft.world.item.component.BlockItemStateProperties.EMPTY);
            String color=state.properties().getOrDefault("color","cyan");
            lines.add(Component.translatable("jade.hololith.color",Component.translatable("color.hololith."+color)).withStyle(ChatFormatting.GRAY));
        }
        if(Screen.hasShiftDown()){
            for(int i=1;i<=3;i++)lines.add(Component.translatable("tooltip.hololith."+kind+"."+i).withStyle(ChatFormatting.GRAY));
        }else lines.add(Component.translatable("tooltip.hololith.controls").withStyle(ChatFormatting.DARK_GRAY));
    }
}
