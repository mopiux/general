package com.mopiux.alquimia.block;

import com.mopiux.alquimia.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Guarda el ingrediente que se está moliendo y el progreso de la molienda. */
public class MortarBlockEntity extends BlockEntity {
    public static final int MAX_ITEMS = 16;
    public static final int GRIND_STEPS = 6;

    private ItemStack stack = ItemStack.EMPTY;
    private int progress;

    public MortarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MORTAR.get(), pos, state);
    }

    public ItemStack stack() {
        return stack;
    }

    public int progress() {
        return progress;
    }

    public void setStack(ItemStack s) {
        stack = s;
        progress = 0;
        changed();
    }

    public void setProgress(int p) {
        progress = p;
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!stack.isEmpty()) tag.put("item", stack.save(new CompoundTag()));
        tag.putInt("progress", progress);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        stack = tag.contains("item") ? ItemStack.of(tag.getCompound("item")) : ItemStack.EMPTY;
        progress = tag.getInt("progress");
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
