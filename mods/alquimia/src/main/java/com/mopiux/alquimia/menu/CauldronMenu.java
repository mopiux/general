package com.mopiux.alquimia.menu;

import com.mopiux.alquimia.alchemy.AlchemyData;
import com.mopiux.alquimia.alchemy.BrewState;
import com.mopiux.alquimia.block.AlchemicalCauldronBlockEntity;
import com.mopiux.alquimia.network.Network;
import com.mopiux.alquimia.network.SyncBrewPacket;
import com.mopiux.alquimia.registry.ModBlocks;
import com.mopiux.alquimia.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.mopiux.alquimia.block.AlchemicalCauldronBlockEntity.*;

/**
 * Menú del caldero. En el servidor está conectado al bloque; en el cliente guarda una copia del
 * estado de la mezcla que llega por red ({@link SyncBrewPacket}).
 */
public class CauldronMenu extends AbstractContainerMenu {
    // Posiciones (en píxeles dentro de la pantalla) — las usa también CauldronScreen.
    public static final int SLOTS_Y = 19;
    public static final int INGREDIENT_X = 191;
    public static final int REAGENT_X = 213;
    public static final int BOTTLES_X = 235;
    public static final int OUTPUT_X = 283;
    public static final int INVENTORY_X = 191;
    public static final int INVENTORY_Y = 107;
    public static final int HOTBAR_Y = 165;

    private static final int MACHINE_SLOTS = SIZE;

    private final BlockPos pos;
    private final ContainerLevelAccess access;
    private final Player player;
    @Nullable
    private final AlchemicalCauldronBlockEntity blockEntity;
    private int lastSentVersion = Integer.MIN_VALUE;

    // --- Estado en el cliente
    private BrewState clientBrew = new BrewState();
    private int clientHeat;
    private boolean clientStirring;
    private boolean clientStirringByMe;
    private int clientVersion;

    public static CauldronMenu fromNetwork(int id, Inventory inventory, FriendlyByteBuf buf) {
        ItemStackHandler predicted = new ItemStackHandler(SIZE) {
            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                return AlchemicalCauldronBlockEntity.isValidFor(true, slot, stack);
            }

            @Override
            public int getSlotLimit(int slot) {
                return slot >= SLOT_OUTPUT ? 1 : 64;
            }
        };
        return new CauldronMenu(id, inventory, buf.readBlockPos(), predicted, null);
    }

    public CauldronMenu(int id, Inventory inventory, AlchemicalCauldronBlockEntity be) {
        this(id, inventory, be.getBlockPos(), be.items(), be);
    }

    private CauldronMenu(int id, Inventory inventory, BlockPos pos, IItemHandler handler, @Nullable AlchemicalCauldronBlockEntity be) {
        super(ModMenus.CAULDRON.get(), id);
        this.pos = pos;
        this.player = inventory.player;
        this.blockEntity = be;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);

        addSlot(new FilteredSlot(handler, SLOT_INGREDIENT, INGREDIENT_X, SLOTS_Y));
        addSlot(new FilteredSlot(handler, SLOT_REAGENT, REAGENT_X, SLOTS_Y));
        addSlot(new FilteredSlot(handler, SLOT_BOTTLES, BOTTLES_X, SLOTS_Y));
        for (int i = 0; i < OUTPUTS; i++) {
            addSlot(new OutputSlot(handler, SLOT_OUTPUT + i, OUTPUT_X + i * 18, SLOTS_Y));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, HOTBAR_Y));
        }
    }

    public BlockPos blockPos() {
        return pos;
    }

    // ------------------------------------------------------------------ estado cliente
    public BrewState brew() {
        return clientBrew;
    }

    public int heat() {
        return clientHeat;
    }

    public boolean isStirring() {
        return clientStirring;
    }

    public boolean isStirringByMe() {
        return clientStirringByMe;
    }

    public int clientVersion() {
        return clientVersion;
    }

    public void applySync(SyncBrewPacket packet) {
        BrewState b = new BrewState();
        b.load(packet.brew());
        this.clientBrew = b;
        this.clientHeat = packet.heat();
        this.clientStirring = packet.stirring();
        this.clientStirringByMe = packet.stirringByMe();
        this.clientVersion++;
    }

    // ------------------------------------------------------------------ servidor
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (blockEntity != null && player instanceof ServerPlayer sp && blockEntity.stateVersion() != lastSentVersion) {
            lastSentVersion = blockEntity.stateVersion();
            BrewState copy = blockEntity.brew().copy();
            copy.trimTrail(240);
            boolean byMe = sp.getUUID().equals(blockEntity.stirrer());
            Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                    new SyncBrewPacket(containerId, copy.save(), blockEntity.heat(), blockEntity.isStirring(), byMe));
        }
    }

    @Nullable
    public AlchemicalCauldronBlockEntity blockEntity() {
        return blockEntity;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.ALCHEMICAL_CAULDRON.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int invStart = MACHINE_SLOTS;
        int invEnd = slots.size();
        if (index < MACHINE_SLOTS) {
            if (!moveItemStackTo(stack, invStart, invEnd, true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            if (AlchemyData.get(player.level()).find(stack) != null) {
                moved = moveItemStackTo(stack, SLOT_INGREDIENT, SLOT_INGREDIENT + 1, false);
            }
            if (!moved && isReagent(stack)) {
                moved = moveItemStackTo(stack, SLOT_REAGENT, SLOT_REAGENT + 1, false);
            }
            if (!moved && stack.is(Items.GLASS_BOTTLE)) {
                moved = moveItemStackTo(stack, SLOT_BOTTLES, SLOT_BOTTLES + 1, false);
            }
            if (!moved) {
                // Mover entre inventario principal y barra rápida
                int hotbarStart = invEnd - 9;
                if (index < hotbarStart) {
                    if (!moveItemStackTo(stack, hotbarStart, invEnd, false)) return ItemStack.EMPTY;
                } else if (!moveItemStackTo(stack, invStart, hotbarStart, false)) {
                    return ItemStack.EMPTY;
                }
            }
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }

    // ------------------------------------------------------------------ ranuras
    private static class FilteredSlot extends SlotItemHandler {
        FilteredSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            return getItemHandler().isItemValid(getSlotIndex(), stack);
        }
    }

    private static class OutputSlot extends SlotItemHandler {
        OutputSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            return false;
        }
    }
}
