package dev.hololith.probe;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Actual Create source blocks for marked disposable fixtures; never in the player JAR. */
final class ProbePower {
    static void supply(ServerLevel level,BlockPos table,int rpm){
        level.setBlockAndUpdate(table.below(),AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS,Direction.Axis.Y));
        level.setBlockAndUpdate(table.below(2),AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.UP));
        ((CreativeMotorBlockEntity)level.getBlockEntity(table.below(2))).generatedSpeed.setValue(rpm);
    }
}
