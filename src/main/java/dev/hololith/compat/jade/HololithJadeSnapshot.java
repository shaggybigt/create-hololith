package dev.hololith.compat.jade;

import dev.hololith.Hololith;
import dev.hololith.HololithTableBlock;
import dev.hololith.HololithTableEntity;
import dev.hololith.ReferenceMarkerBlock;
import dev.hololith.ReferenceMarkerEntity;
import dev.hololith.SurveyFeedback;
import dev.hololith.SurveySensorEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Small read-only instrument view. This class deliberately has no Jade dependency. */
public final class HololithJadeSnapshot {
    public static final String DATA_KEY = "HololithInstrument";

    private HololithJadeSnapshot() {}

    /** Never requests a chunk, scans terrain, changes an instrument or copies survey NBT. */
    public static CompoundTag capture(Level level, BlockPos pos, BlockState state, BlockEntity direct) {
        CompoundTag data = new CompoundTag();
        if (state.is(Hololith.TABLE.get())) {
            data.putString("Kind", "table");
            HololithTableEntity table = tableAt(level, pos, state, direct);
            if (table == null) return data;
            data.putBoolean("Available", true);
            data.putString("Table", SurveyFeedback.shortId(table.tableId()));
            data.putInt("Span", table.span());
            data.putInt("Magnification", Math.round(table.magnification() * 100));
            data.putInt("Relief", Math.round(table.verticalScale()));
            data.putString("Status", bounded(table.status(), 192));
            data.putInt("Mode", table.mode());
            if (table.sensorId() != null) data.putString("Sensor", SurveyFeedback.shortId(table.sensorId()));
            data.putBoolean("Projecting", table.isProjecting());
            data.putString("PowerStatus", bounded(table.powerStatusKey(), 128));
            data.putInt("RequiredRpm", table.requiredRpm());
            data.putFloat("ActualRpm", finite(table.actualRpm()));
            data.putFloat("StressImpact", finite(table.stressImpact()));
            data.putFloat("StressUsage", finite(table.stressUsage()));
        } else if (state.is(Hololith.SENSOR.get()) || state.is(Hololith.BEACON.get())) {
            data.putString("Kind", state.is(Hololith.BEACON.get()) ? "beacon" : "sensor");
            if (direct instanceof SurveySensorEntity sensor) {
                data.putBoolean("Available", true);
                data.putString("Sensor", SurveyFeedback.shortId(sensor.sensorId()));
            }
        } else if (state.is(Hololith.MARKER.get())) {
            data.putString("Kind", "marker");
            data.putString("Color", state.getValue(ReferenceMarkerBlock.COLOR).getName());
            if (direct instanceof ReferenceMarkerEntity marker) {
                data.putBoolean("Available", true);
                data.putBoolean("Linked", marker.targetId() != null);
                if (marker.targetId() != null) {
                    data.putString("Target", SurveyFeedback.shortId(marker.targetId()));
                    data.putInt("TargetKind", marker.targetKind());
                }
            }
        }
        return data;
    }

    private static HololithTableEntity tableAt(Level level, BlockPos pos, BlockState state, BlockEntity direct) {
        // A virtual-world accessor may already supply the correct captured center BE.
        if (state.getValue(HololithTableBlock.PART) == 4 && direct instanceof HololithTableEntity table) return table;
        BlockPos center = HololithTableBlock.anchor(pos, state);
        if (!level.hasChunkAt(center)) return null;
        BlockState anchor = level.getBlockState(center);
        if (!anchor.is(Hololith.TABLE.get()) || anchor.getValue(HololithTableBlock.PART) != 4
                || anchor.getValue(HololithTableBlock.FACING) != state.getValue(HololithTableBlock.FACING)) return null;
        return level.getBlockEntity(center) instanceof HololithTableEntity table ? table : null;
    }

    private static String bounded(String value, int maximum) {
        if (value == null) return "";
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    private static float finite(float value) {
        return Float.isFinite(value) ? Math.max(0, value) : 0;
    }
}
