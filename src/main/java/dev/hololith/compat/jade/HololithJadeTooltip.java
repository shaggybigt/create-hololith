package dev.hololith.compat.jade;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Loaded through Jade's client registration; the server provider has no dependency on this enum. */
public enum HololithJadeTooltip implements IBlockComponentProvider {
    INSTANCE;

    @Override public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag serverData = accessor.getServerData();
        if (!serverData.contains(HololithJadeSnapshot.DATA_KEY, Tag.TAG_COMPOUND)) {
            if (!accessor.isServerConnected())
                tooltip.add(Component.translatable("jade.hololith.server_required").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.addAll(describe(serverData.getCompound(HololithJadeSnapshot.DATA_KEY), accessor.showDetails()));
    }

    /** Exposed for the optional Jade gameplay probe; this method has no side effects. */
    public static List<Component> describe(CompoundTag data, boolean details) {
        List<Component> lines = new ArrayList<>();
        if (!data.getBoolean("Available")) {
            lines.add(Component.translatable("jade.hololith.unavailable").withStyle(ChatFormatting.GRAY));
            return lines;
        }
        switch (data.getString("Kind")) {
            case "table" -> {
                lines.add(Component.translatable("jade.hololith.power", Component.translatable(data.getString("PowerStatus")))
                        .withStyle(data.getBoolean("Projecting") ? ChatFormatting.GREEN : ChatFormatting.GOLD));
                lines.add(Component.translatable("jade.hololith.speed", number(data.getFloat("ActualRpm")), data.getInt("RequiredRpm")));
                lines.add(Component.translatable("jade.hololith.area", data.getInt("Span"), data.getInt("Span")));
                lines.add(Component.translatable("jade.hololith.survey", data.getString("Status")));
                Component source = switch (data.getInt("Mode")) {
                    case 1 -> data.contains("Sensor")
                            ? Component.translatable("jade.hololith.source_sensor", data.getString("Sensor"))
                            : Component.translatable("jade.hololith.source_missing");
                    default -> Component.translatable("message.hololith.source_table");
                };
                lines.add(Component.translatable("jade.hololith.source", source));
                if (details) {
                    lines.add(Component.translatable("jade.hololith.stress", number(data.getFloat("StressUsage")), number(data.getFloat("StressImpact"))));
                    lines.add(Component.translatable("jade.hololith.display", data.getInt("Magnification"), data.getInt("Relief")));
                    lines.add(Component.translatable("jade.hololith.table_id", data.getString("Table")).withStyle(ChatFormatting.GRAY));
                }
            }
            case "sensor", "beacon" -> {
                String key = data.getString("Kind").equals("beacon") ? "jade.hololith.beacon_id" : "jade.hololith.sensor_id";
                lines.add(Component.translatable(key, data.getString("Sensor")));
                lines.add(Component.translatable("jade.hololith.sensor_hint").withStyle(ChatFormatting.GRAY));
            }
            case "marker" -> {
                lines.add(Component.translatable("jade.hololith.color", Component.translatable("color.hololith." + data.getString("Color"))));
                if (data.getBoolean("Linked")) {
                    Component kind = Component.translatable(data.getInt("TargetKind") == 0
                            ? "message.hololith.target_table" : "message.hololith.target_sensor");
                    lines.add(Component.translatable("jade.hololith.linked", kind, data.getString("Target")));
                } else lines.add(Component.translatable("jade.hololith.unlinked").withStyle(ChatFormatting.GOLD));
            }
            default -> { }
        }
        return List.copyOf(lines);
    }

    private static String number(float value) {
        return value == Math.rint(value) ? String.format(Locale.ROOT, "%.0f", value) : String.format(Locale.ROOT, "%.1f", value);
    }

    @Override public ResourceLocation getUid() {
        return HololithJadePlugin.INSTRUMENT;
    }
}
