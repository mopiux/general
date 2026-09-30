package com.mopiux.alquimia.block;

import com.mopiux.alquimia.advancement.AlchemyTrigger;
import com.mopiux.alquimia.alchemy.AlchemyData;
import com.mopiux.alquimia.alchemy.AlchemyIngredient;
import com.mopiux.alquimia.alchemy.AlchemyKnowledge;
import com.mopiux.alquimia.alchemy.AlchemyMap;
import com.mopiux.alquimia.alchemy.BrewState;
import com.mopiux.alquimia.alchemy.Grinding;
import com.mopiux.alquimia.alchemy.PlayerKnowledge;
import com.mopiux.alquimia.config.AlquimiaConfig;
import com.mopiux.alquimia.item.AlchemicalPotionItem;
import com.mopiux.alquimia.menu.CauldronMenu;
import com.mopiux.alquimia.network.Network;
import com.mopiux.alquimia.registry.ModBlockEntities;
import com.mopiux.alquimia.registry.ModItems;
import com.mopiux.alquimia.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Lógica del caldero alquímico. Toda la simulación ocurre en el servidor: los clientes solo
 * reciben el estado para dibujarlo.
 */
public class AlchemicalCauldronBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SLOT_INGREDIENT = 0;
    public static final int SLOT_REAGENT = 1;
    public static final int SLOT_BOTTLES = 2;
    public static final int SLOT_OUTPUT = 3;
    public static final int OUTPUTS = 3;
    public static final int SIZE = SLOT_OUTPUT + OUTPUTS;
    public static final int WATER_COLOR = 0x3F76E4;
    private static final int FEED_INTERVAL = 8;

    private final ItemStackHandler items = new ItemStackHandler(SIZE) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return AlchemicalCauldronBlockEntity.this.isValid(slot, stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot >= SLOT_OUTPUT ? 1 : 64;
        }
    };
    private final LazyOptional<IItemHandler> automation = LazyOptional.of(() -> new AutomationHandler(items));

    private final BrewState brew = new BrewState();
    private int heat;
    private int stateVersion;
    @Nullable
    private UUID stirrer;
    private int feedCooldown;
    private int stirSoundCooldown;
    private final Set<UUID> knowledgeDirty = new HashSet<>();

    // --- Datos visuales (sincronizados al cliente para dibujar el líquido y las partículas)
    private int visualWater = -1;
    private int visualHeat = -1;
    private int visualColor = WATER_COLOR;
    private boolean visualStirring;

    public AlchemicalCauldronBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ALCHEMICAL_CAULDRON.get(), pos, state);
    }

    // ------------------------------------------------------------------ accesos
    public ItemStackHandler items() {
        return items;
    }

    public BrewState brew() {
        return brew;
    }

    public int heat() {
        return heat;
    }

    public int stateVersion() {
        return stateVersion;
    }

    public boolean isStirring() {
        return stirrer != null;
    }

    @Nullable
    public UUID stirrer() {
        return stirrer;
    }

    public int visualWater() {
        return Math.max(0, visualWater);
    }

    public int visualHeat() {
        return Math.max(0, visualHeat);
    }

    public int visualColor() {
        return visualColor;
    }

    public boolean visualStirring() {
        return visualStirring;
    }

    @Nullable
    public AlchemyMap map() {
        if (level == null) return null;
        return AlchemyData.get(level).mapFor(level.dimension().location());
    }

    private boolean isValid(int slot, ItemStack stack) {
        return isValidFor(level == null || level.isClientSide, slot, stack);
    }

    /** Qué puede ir en cada ranura (se usa en servidor y en la predicción del cliente). */
    public static boolean isValidFor(boolean clientSide, int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_INGREDIENT -> AlchemyData.get(clientSide).find(stack) != null;
            case SLOT_REAGENT -> isReagent(stack);
            case SLOT_BOTTLES -> stack.is(Items.GLASS_BOTTLE);
            default -> false;
        };
    }

    public static boolean isReagent(ItemStack stack) {
        return stack.is(ModItems.SALT.get()) || stack.is(ModItems.SULFUR.get()) || stack.is(ModItems.QUICKSILVER.get())
                || isWaterBottle(stack);
    }

    public static boolean isWaterBottle(ItemStack stack) {
        return stack.is(Items.POTION) && PotionUtils.getPotion(stack) == Potions.WATER;
    }

    // ------------------------------------------------------------------ tick
    public static void serverTick(Level level, BlockPos pos, BlockState state, AlchemicalCauldronBlockEntity be) {
        ServerLevel sl = (ServerLevel) level;
        if (level.getGameTime() % 10 == 0 || be.visualHeat < 0) be.heat = computeHeat(level, pos);
        be.tickFeed(sl);
        be.tickStir(sl);
        if (!be.knowledgeDirty.isEmpty() && level.getGameTime() % 5 == 0) be.flushKnowledge(sl);
        be.syncVisuals();
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, AlchemicalCauldronBlockEntity be) {
        if (be.visualWater <= 0 || be.visualHeat <= 0) return;
        if (!AlquimiaConfig.CLIENT_SPEC.isLoaded() || !AlquimiaConfig.BUBBLE_PARTICLES.get()) return;
        RandomSource r = level.random;
        double surface = pos.getY() + 0.35 + be.visualWater * 0.18;
        float cr = ((be.visualColor >> 16) & 0xFF) / 255f;
        float cg = ((be.visualColor >> 8) & 0xFF) / 255f;
        float cb = (be.visualColor & 0xFF) / 255f;
        int chances = be.visualStirring ? 3 : 1;
        for (int i = 0; i < chances; i++) {
            if (r.nextFloat() < 0.25f * be.visualHeat) {
                double x = pos.getX() + 0.25 + r.nextDouble() * 0.5;
                double z = pos.getZ() + 0.25 + r.nextDouble() * 0.5;
                level.addParticle(ParticleTypes.BUBBLE_POP, x, surface, z, 0, 0.01, 0);
            }
        }
        if (be.visualStirring && r.nextFloat() < 0.5f) {
            double a = (level.getGameTime() % 40) / 40.0 * Math.PI * 2;
            level.addParticle(ParticleTypes.ENTITY_EFFECT, pos.getX() + 0.5 + Math.cos(a) * 0.3, surface + 0.05,
                    pos.getZ() + 0.5 + Math.sin(a) * 0.3, cr, cg, cb);
        }
    }

    public static int computeHeat(Level level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState state = level.getBlockState(below);
        boolean lit = !state.hasProperty(BlockStateProperties.LIT) || state.getValue(BlockStateProperties.LIT);
        if (state.is(ModTags.STRONG_HEAT_SOURCES)) return lit ? 2 : 0;
        if (state.is(ModTags.HEAT_SOURCES)) return lit ? 1 : 0;
        if (level.getFluidState(below).is(FluidTags.LAVA)) return 2;
        return 0;
    }

    private void tickFeed(ServerLevel level) {
        if (feedCooldown > 0) {
            feedCooldown--;
            return;
        }
        ItemStack in = items.getStackInSlot(SLOT_INGREDIENT);
        if (in.isEmpty() || brew.water <= 0) return;
        AlchemyIngredient ing = AlchemyData.server().find(in);
        if (ing == null) return;
        float fraction = Grinding.pathFraction(in);
        ItemStack one = items.extractItem(SLOT_INGREDIENT, 1, false);
        if (one.isEmpty()) return;
        applyIngredient(ing, fraction);
        brew.ingredientsUsed++;
        feedCooldown = FEED_INTERVAL;
        for (ServerPlayer p : viewers(level)) {
            if (knowledge(p).learnIngredient(ing.id())) knowledgeDirty.add(p.getUUID());
        }
        double cx = worldPosition.getX() + 0.5, cz = worldPosition.getZ() + 0.5;
        double cy = worldPosition.getY() + 0.4 + brew.water * 0.18;
        level.playSound(null, worldPosition, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.35f, 1.3f + level.random.nextFloat() * 0.3f);
        level.sendParticles(ParticleTypes.SPLASH, cx, cy, cz, 10, 0.15, 0.02, 0.15, 0.1);
        markBrewChanged();
    }

    public void applyIngredient(AlchemyIngredient ing, float fraction) {
        switch (ing.transform()) {
            case NONE -> brew.append(ing.segments(fraction));
            case ROTATE -> brew.rotatePending(ing.amount());
            case SCALE -> brew.scalePending(ing.amount());
            case MIRROR -> brew.mirrorPending();
        }
    }

    private void tickStir(ServerLevel level) {
        if (stirrer == null) return;
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(stirrer);
        if (p == null || !isViewing(p)) {
            stopStir();
            return;
        }
        if (brew.pending.isEmpty()) {
            stopStir();
            return;
        }
        if (AlquimiaConfig.REQUIRE_HEAT.get() && heat <= 0) {
            stopStir();
            p.displayClientMessage(Component.translatable("message.alquimia.needs_heat"), true);
            return;
        }
        AlchemyMap map = map();
        if (map == null) {
            stopStir();
            return;
        }
        float speed = 0.5f * (heat >= 2 ? 1.6f : 1f) * AlquimiaConfig.STIR_SPEED.get().floatValue();
        PlayerKnowledge k = knowledge(p);
        boolean[] changed = {false};
        boolean[] hitHazard = {false};
        boolean[] hitEdge = {false};
        float r = map.radius();
        brew.advance(speed, 0.5f, (x, y) -> {
            if (x * x + y * y > r * r) {
                hitEdge[0] = true;
                return false;
            }
            if (k.reveal(map, x, y, PlayerKnowledge.REVEAL_RADIUS)) changed[0] = true;
            AlchemyMap.Zone z = map.zoneAt(x, y);
            if (z != null && k.discover(map.id(), z.effect())) {
                changed[0] = true;
                onDiscover(p, z, k);
            }
            if (map.hazardAt(x, y) != null) {
                hitHazard[0] = true;
                return false;
            }
            return true;
        });
        if (changed[0]) knowledgeDirty.add(p.getUUID());
        if (hitEdge[0]) {
            float d = Mth.sqrt(brew.x * brew.x + brew.y * brew.y);
            if (d > r) {
                brew.x = brew.x / d * (r - 0.5f);
                brew.y = brew.y / d * (r - 0.5f);
            }
            brew.pending.clear();
            stopStir();
            p.displayClientMessage(Component.translatable("message.alquimia.edge"), true);
        }
        if (hitHazard[0]) {
            ruin(level, p);
            return;
        }
        if (--stirSoundCooldown <= 0) {
            stirSoundCooldown = 12;
            level.playSound(null, worldPosition, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.BLOCKS, 0.5f,
                    0.9f + level.random.nextFloat() * 0.2f);
        }
        markBrewChanged();
    }

    private void onDiscover(ServerPlayer p, AlchemyMap.Zone z, PlayerKnowledge k) {
        MobEffect effect = z.mobEffect();
        if (effect != null) {
            p.displayClientMessage(Component.translatable("message.alquimia.discovered", effect.getDisplayName()), true);
        }
        p.level().playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.5f, 1.6f);
        AlchemyTrigger.INSTANCE.trigger(p, "discover", k.totalDiscovered());
    }

    /** La mezcla tocó una zona de peligro. */
    private void ruin(ServerLevel level, @Nullable ServerPlayer cause) {
        brew.reset();
        stopStir();
        markBrewChanged();
        double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 1.0, z = worldPosition.getZ() + 0.5;
        if (AlquimiaConfig.HAZARDS_EXPLODE.get()) {
            level.explode(null, x, y, z, 1.6f,
                    AlquimiaConfig.EXPLOSIONS_BREAK_BLOCKS.get() ? Level.ExplosionInteraction.BLOCK : Level.ExplosionInteraction.NONE);
        } else {
            level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1f, 0.7f);
        }
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 20, 0.3, 0.3, 0.3, 0.02);
        if (cause != null) {
            cause.displayClientMessage(Component.translatable("message.alquimia.exploded"), true);
            AlchemyTrigger.INSTANCE.trigger(cause, "explode", 1);
        }
    }

    // ------------------------------------------------------------------ acciones del jugador
    public void handleAction(ServerPlayer player, CauldronAction action) {
        if (level == null || level.isClientSide) return;
        switch (action) {
            case STIR_START -> startStir(player);
            case STIR_STOP -> {
                if (player.getUUID().equals(stirrer)) stopStir();
            }
            case STIR_TOGGLE -> {
                if (player.getUUID().equals(stirrer)) stopStir();
                else startStir(player);
            }
            case DILUTE -> dilute(player);
            case FIX -> fix(player);
            case EMPOWER -> empower(player);
            case PROLONG -> prolong(player);
            case BOTTLE -> bottle(player);
            case EMPTY -> empty(player);
        }
    }

    private void startStir(ServerPlayer p) {
        if (brew.water <= 0) {
            fail(p, "no_water");
            return;
        }
        if (brew.pending.isEmpty()) {
            fail(p, "nothing_to_stir");
            return;
        }
        if (AlquimiaConfig.REQUIRE_HEAT.get() && heat <= 0) {
            fail(p, "needs_heat");
            return;
        }
        stirrer = p.getUUID();
        markBrewChanged();
    }

    private void stopStir() {
        if (stirrer != null) {
            stirrer = null;
            markBrewChanged();
        }
    }

    private void dilute(ServerPlayer p) {
        ItemStack reagent = items.getStackInSlot(SLOT_REAGENT);
        if (!isWaterBottle(reagent)) {
            fail(p, "dilute.no_water");
            return;
        }
        if (brew.water >= BrewState.MAX_WATER && brew.x == 0 && brew.y == 0) {
            fail(p, "dilute.nothing");
            return;
        }
        items.extractItem(SLOT_REAGENT, 1, false);
        ItemStack rest = items.insertItem(SLOT_BOTTLES, new ItemStack(Items.GLASS_BOTTLE), false);
        if (!rest.isEmpty() && !p.getInventory().add(rest)) p.drop(rest, false);
        brew.pullTowardOrigin(BrewState.DILUTE_AMOUNT);
        if (brew.water < BrewState.MAX_WATER) brew.water++;
        exploreAt(p);
        level.playSound(null, worldPosition, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1f, 1f);
        markBrewChanged();
    }

    private void fix(ServerPlayer p) {
        AlchemyMap map = map();
        if (map == null) return;
        if (!items.getStackInSlot(SLOT_REAGENT).is(ModItems.SALT.get())) {
            fail(p, "fix.no_salt");
            return;
        }
        AlchemyMap.Zone z = map.zoneAt(brew.x, brew.y);
        if (z == null) {
            fail(p, "fix.no_zone");
            return;
        }
        if (brew.hasFixed(z.effect())) {
            fail(p, "fix.already");
            return;
        }
        int max = AlquimiaConfig.MAX_EFFECTS.get();
        if (brew.fixed.size() >= max) {
            p.displayClientMessage(Component.translatable("message.alquimia.fix.full", max), true);
            return;
        }
        MobEffect effect = z.mobEffect();
        if (effect == null) {
            fail(p, "fix.unknown");
            return;
        }
        int tier = z.tierAt(brew.x, brew.y);
        int amp = Math.max(0, Math.min(tier - 1, Math.min(z.maxAmplifier(), AlquimiaConfig.MAX_AMPLIFIER.get())));
        brew.fixed.add(new BrewState.FixedEssence(z.effect(), amp, z.duration(), false));
        items.extractItem(SLOT_REAGENT, 1, false);
        exploreAt(p);
        level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.2f, 0.8f + tier * 0.15f);
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.ENCHANT, worldPosition.getX() + 0.5, worldPosition.getY() + 1.1,
                    worldPosition.getZ() + 0.5, 30, 0.3, 0.3, 0.3, 0.5);
        }
        p.displayClientMessage(Component.translatable("message.alquimia.fixed",
                AlchemicalPotionItem.effectName(new MobEffectInstance(effect, 20, amp))), true);
        AlchemyTrigger.INSTANCE.trigger(p, "fix", brew.fixed.size());
        markBrewChanged();
    }

    private void empower(ServerPlayer p) {
        if (!items.getStackInSlot(SLOT_REAGENT).is(ModItems.SULFUR.get())) {
            fail(p, "empower.no_sulfur");
            return;
        }
        if (brew.fixed.isEmpty()) {
            fail(p, "no_essence");
            return;
        }
        int last = brew.fixed.size() - 1;
        BrewState.FixedEssence f = brew.fixed.get(last);
        if (f.empowered()) {
            fail(p, "empower.already");
            return;
        }
        int amp = Math.min(f.amplifier() + 1, AlquimiaConfig.MAX_AMPLIFIER.get());
        if (amp <= f.amplifier()) {
            fail(p, "empower.max");
            return;
        }
        brew.fixed.set(last, new BrewState.FixedEssence(f.effect(), amp, Math.max(20, f.duration() / 2), true));
        items.extractItem(SLOT_REAGENT, 1, false);
        level.playSound(null, worldPosition, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.8f, 1.2f);
        AlchemyTrigger.INSTANCE.trigger(p, "empower", 1);
        markBrewChanged();
    }

    private void prolong(ServerPlayer p) {
        if (!items.getStackInSlot(SLOT_REAGENT).is(ModItems.QUICKSILVER.get())) {
            fail(p, "prolong.no_quicksilver");
            return;
        }
        if (brew.fixed.isEmpty()) {
            fail(p, "no_essence");
            return;
        }
        if (brew.prolongs >= BrewState.MAX_PROLONGS) {
            fail(p, "prolong.max");
            return;
        }
        for (int i = 0; i < brew.fixed.size(); i++) {
            BrewState.FixedEssence f = brew.fixed.get(i);
            brew.fixed.set(i, new BrewState.FixedEssence(f.effect(), f.amplifier(), f.duration() * 3 / 2, f.empowered()));
        }
        brew.prolongs++;
        items.extractItem(SLOT_REAGENT, 1, false);
        level.playSound(null, worldPosition, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1f, 1f);
        AlchemyTrigger.INSTANCE.trigger(p, "prolong", brew.prolongs);
        markBrewChanged();
    }

    private void bottle(ServerPlayer p) {
        if (brew.fixed.isEmpty()) {
            fail(p, "no_essence");
            return;
        }
        if (!items.getStackInSlot(SLOT_BOTTLES).is(Items.GLASS_BOTTLE)) {
            fail(p, "bottle.no_bottles");
            return;
        }
        List<MobEffectInstance> effects = buildEffects();
        if (effects.isEmpty()) {
            fail(p, "fix.unknown");
            return;
        }
        int made = 0;
        for (int i = 0; i < OUTPUTS && brew.water > 0; i++) {
            int slot = SLOT_OUTPUT + i;
            if (!items.getStackInSlot(slot).isEmpty()) continue;
            if (items.extractItem(SLOT_BOTTLES, 1, false).isEmpty()) break;
            items.setStackInSlot(slot, AlchemicalPotionItem.create(ModItems.ALCHEMICAL_POTION.get(), effects));
            brew.water--;
            made++;
        }
        if (made == 0) {
            fail(p, "bottle.full");
            return;
        }
        level.playSound(null, worldPosition, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1f, 1f);
        AlchemyTrigger.INSTANCE.trigger(p, "bottle", effects.size());
        if (brew.water <= 0) {
            brew.reset();
            stopStir();
        }
        markBrewChanged();
    }

    private void empty(ServerPlayer p) {
        if (brew.water <= 0 && brew.isPristine()) return;
        brew.reset();
        stopStir();
        level.playSound(null, worldPosition, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1f, 0.9f);
        markBrewChanged();
    }

    private void fail(ServerPlayer p, String key) {
        p.displayClientMessage(Component.translatable("message.alquimia." + key), true);
        level.playSound(null, worldPosition, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 0.4f, 1.4f);
    }

    public List<MobEffectInstance> buildEffects() {
        double mult = AlquimiaConfig.DURATION_MULTIPLIER.get();
        List<MobEffectInstance> list = new ArrayList<>();
        for (BrewState.FixedEssence f : brew.fixed) {
            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(f.effect());
            if (effect == null) continue;
            int duration = effect.isInstantenous() ? 1 : Math.max(20, (int) Math.round(f.duration() * mult));
            list.add(new MobEffectInstance(effect, duration, f.amplifier()));
        }
        return list;
    }

    /** Revela y descubre alrededor de la posición actual (tras diluir o fijar). */
    private void exploreAt(ServerPlayer p) {
        AlchemyMap map = map();
        if (map == null) return;
        PlayerKnowledge k = knowledge(p);
        boolean changed = k.reveal(map, brew.x, brew.y, PlayerKnowledge.REVEAL_RADIUS);
        AlchemyMap.Zone z = map.zoneAt(brew.x, brew.y);
        if (z != null && k.discover(map.id(), z.effect())) {
            changed = true;
            onDiscover(p, z, k);
        }
        if (changed) knowledgeDirty.add(p.getUUID());
    }

    // ------------------------------------------------------------------ agua (interacción en el mundo)
    /** Agrega agua desde un balde o una botella. Si ya hay una mezcla, el agua la diluye. */
    public boolean addWater(int amount) {
        if (brew.water >= BrewState.MAX_WATER) return false;
        boolean active = !brew.isPristine();
        int before = brew.water;
        brew.water = Math.min(BrewState.MAX_WATER, brew.water + amount);
        if (active) brew.pullTowardOrigin(BrewState.DILUTE_AMOUNT * (brew.water - before));
        markBrewChanged();
        return true;
    }

    /** Recupera el agua con un balde (solo si no hay mezcla en curso). */
    public boolean takeWater() {
        if (brew.water < BrewState.MAX_WATER || !brew.isPristine()) return false;
        brew.reset();
        markBrewChanged();
        return true;
    }

    // ------------------------------------------------------------------ jugadores / conocimiento
    public boolean isViewing(Player p) {
        AbstractContainerMenu menu = p.containerMenu;
        return menu instanceof CauldronMenu m && m.blockPos().equals(worldPosition);
    }

    public List<ServerPlayer> viewers(ServerLevel level) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.players()) if (isViewing(p)) out.add(p);
        return out;
    }

    private PlayerKnowledge knowledge(ServerPlayer p) {
        AlchemyKnowledge all = AlchemyKnowledge.get(p.server);
        all.setDirty();
        return all.of(p.getUUID());
    }

    private void flushKnowledge(ServerLevel level) {
        for (UUID id : knowledgeDirty) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null) Network.sendKnowledge(p);
        }
        knowledgeDirty.clear();
    }

    public void markBrewChanged() {
        stateVersion++;
        setChanged();
    }

    // ------------------------------------------------------------------ sincronización visual
    private int liquidColor() {
        if (!brew.fixed.isEmpty()) {
            List<MobEffectInstance> effects = buildEffects();
            if (!effects.isEmpty()) return PotionUtils.getColor(effects);
        }
        AlchemyMap map = map();
        if (map != null) {
            AlchemyMap.Zone z = map.zoneAt(brew.x, brew.y);
            if (z != null && z.mobEffect() != null) return z.mobEffect().getColor();
        }
        return WATER_COLOR;
    }

    private void syncVisuals() {
        if (level == null) return;
        int color = brew.water > 0 ? liquidColor() : WATER_COLOR;
        boolean stirring = stirrer != null;
        if (color != visualColor || brew.water != visualWater || heat != visualHeat || stirring != visualStirring) {
            visualColor = color;
            visualWater = brew.water;
            visualHeat = heat;
            visualStirring = stirring;
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
            level.updateNeighbourForOutputSignal(worldPosition, state.getBlock());
        }
    }

    private CompoundTag visualTag() {
        CompoundTag t = new CompoundTag();
        t.putInt("water", brew.water);
        t.putInt("heat", heat);
        t.putInt("color", visualColor);
        t.putBoolean("stirring", stirrer != null);
        return t;
    }

    private void readVisual(CompoundTag t) {
        visualWater = t.getInt("water");
        visualHeat = t.getInt("heat");
        visualColor = t.contains("color") ? t.getInt("color") : WATER_COLOR;
        visualStirring = t.getBoolean("stirring");
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag t = new CompoundTag();
        t.put("visual", visualTag());
        return t;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        readVisual(tag.getCompound("visual"));
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) readVisual(tag.getCompound("visual"));
    }

    // ------------------------------------------------------------------ guardado
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("items", items.serializeNBT());
        tag.put("brew", brew.save());
        tag.putInt("heat", heat);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("items")) items.deserializeNBT(tag.getCompound("items"));
        if (tag.contains("brew")) brew.load(tag.getCompound("brew"));
        heat = tag.getInt("heat");
        if (tag.contains("visual")) readVisual(tag.getCompound("visual"));
    }

    public void dropContents() {
        if (level == null) return;
        for (int i = 0; i < items.getSlots(); i++) {
            ItemStack s = items.getStackInSlot(i);
            if (!s.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, s);
            }
        }
    }

    public int comparatorSignal() {
        if (brew.water <= 0) return 0;
        return Math.min(15, brew.water * 3 + brew.fixed.size() * 2);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        stirrer = null;
    }

    // ------------------------------------------------------------------ capacidades (tolvas, tuberías)
    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) return automation.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        automation.invalidate();
    }

    // ------------------------------------------------------------------ menú
    @Override
    public Component getDisplayName() {
        return Component.translatable("container.alquimia.alchemical_cauldron");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new CauldronMenu(id, inventory, this);
    }

    /**
     * Acceso para automatización: se puede insertar ingredientes, reactivos y frascos, y solo se
     * pueden extraer los elixires terminados.
     */
    private static final class AutomationHandler implements IItemHandler {
        private final ItemStackHandler inner;

        AutomationHandler(ItemStackHandler inner) {
            this.inner = inner;
        }

        @Override
        public int getSlots() {
            return inner.getSlots();
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return inner.getStackInSlot(slot);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (slot >= SLOT_OUTPUT) return stack;
            return inner.insertItem(slot, stack, simulate);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < SLOT_OUTPUT) return ItemStack.EMPTY;
            return inner.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return inner.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return slot < SLOT_OUTPUT && inner.isItemValid(slot, stack);
        }
    }
}
