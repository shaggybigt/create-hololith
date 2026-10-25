package dev.hololith.compat;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import dev.hololith.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;

/** Routes Create's moving-block interactions to Hololith's mounted state. */
public final class ContraptionInteractions extends MovingInteractionBehaviour {
    public static void register() {
        var interactions = new ContraptionInteractions();
        REGISTRY.register(Hololith.TABLE.get(), interactions);
        REGISTRY.register(Hololith.SENSOR.get(), interactions);
        REGISTRY.register(Hololith.BEACON.get(), interactions);
        REGISTRY.register(Hololith.MARKER.get(), interactions);
    }

    @Override
    public boolean handlePlayerInteraction(Player player, InteractionHand hand, BlockPos localPos,
            AbstractContraptionEntity entity) {
        if (hand == null || player.isSpectator() || !player.mayBuild() || entity.getContraption() == null) return false;
        var info = entity.getContraption().getBlocks().get(localPos);
        if (info == null) return false;
        boolean tableBlock = info.state().is(Hololith.TABLE.get());
        boolean markerBlock = info.state().is(Hololith.MARKER.get());
        boolean sensorBlock = info.state().is(Hololith.SENSOR.get()) || info.state().is(Hololith.BEACON.get());
        ItemStack stack = player.getItemInHand(hand);
        if (!tableBlock && !markerBlock && !(sensorBlock && stack.is(Hololith.CARD.get()))) return false;
        // Create sends the interaction to the server only when the client accepts it.
        if (entity.level().isClientSide) return true;
        if (!(player instanceof ServerPlayer serverPlayer) || player.level() != entity.level()) return false;
        BlockPos target = tableBlock ? HololithTableBlock.anchor(localPos, info.state()) : localPos;
        var world = ContraptionSurveys.worldPosition(entity, target);
        if (world.isEmpty() || world.get().distanceToSqr(player.position()) > 64
                || !entity.level().mayInteract(player, BlockPos.containing(world.get()))) return false;
        if (tableBlock) {
            HololithTableEntity table = ContraptionSurveys.table(entity, target);
            if (table == null) return false;
            if (stack.is(Hololith.CARD.get())) SurveyFeedback.tableCard(serverPlayer, stack, table,world.get());
            else if (isWrench(stack)) { if (table.allowControl()) {table.adjustDial(player.isShiftKeyDown());SurveyFeedback.dial(player,table,player.isShiftKeyDown());} }
            else ContraptionPackets.open(serverPlayer, entity, target);
            return true;
        }
        if (sensorBlock) {
            var sensor = ContraptionSurveys.sensor(entity, target);
            if (sensor == null) return false;
            SurveyFeedback.recordLocation(player,stack,sensor,world.get());
            return true;
        }
        ReferenceMarkerEntity marker = ContraptionSurveys.marker(entity, target);
        if (marker == null) return false;
        if (stack.is(Hololith.CARD.get())) {
            SurveyFeedback.markerCard(player,stack,marker,world.get());
        } else if (stack.getItem() instanceof DyeItem dye) {
            if (marker.getBlockState().getValue(ReferenceMarkerBlock.COLOR) != dye.getDyeColor()) {
                marker.setBlockState(marker.getBlockState().setValue(ReferenceMarkerBlock.COLOR, dye.getDyeColor()));
                marker.changed();
                stack.consume(1, player);
                SurveyFeedback.success(player,world.get(),"message.hololith.reference_color",SurveyFeedback.colorName(dye.getDyeColor()));
            }
        } else if (isWrench(stack) && player.isShiftKeyDown()) {
            SurveyFeedback.unlinkMarker(player,marker,world.get());
        } else {
            SurveyFeedback.inspectMarker(player,marker);
        }
        return true;
    }

    private static boolean isWrench(ItemStack stack) { return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals("create:wrench"); }
}
