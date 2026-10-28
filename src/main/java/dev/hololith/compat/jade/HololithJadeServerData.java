package dev.hololith.compat.jade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/** Jade requests this compact view only for the instrument the player is looking at. */
public enum HololithJadeServerData implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    @Override public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        CompoundTag snapshot = HololithJadeSnapshot.capture(accessor.getLevel(), accessor.getPosition(),
                accessor.getBlockState(), accessor.getBlockEntity());
        if (!snapshot.isEmpty()) data.put(HololithJadeSnapshot.DATA_KEY, snapshot);
    }

    @Override public ResourceLocation getUid() {
        return HololithJadePlugin.INSTRUMENT;
    }
}
