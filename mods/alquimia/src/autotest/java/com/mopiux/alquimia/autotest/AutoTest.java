package com.mopiux.alquimia.autotest;

import com.mopiux.alquimia.Alquimia;
import com.mopiux.alquimia.alchemy.AlchemyData;
import com.mopiux.alquimia.alchemy.AlchemyIngredient;
import com.mopiux.alquimia.alchemy.AlchemyKnowledge;
import com.mopiux.alquimia.alchemy.AlchemyMap;
import com.mopiux.alquimia.alchemy.BrewState;
import com.mopiux.alquimia.alchemy.Grinding;
import com.mopiux.alquimia.alchemy.PlayerKnowledge;
import com.mopiux.alquimia.block.AlchemicalCauldronBlockEntity;
import com.mopiux.alquimia.block.MortarBlockEntity;
import com.mopiux.alquimia.client.ClientSetup;
import com.mopiux.alquimia.client.GrimoireScreen;
import com.mopiux.alquimia.item.AlchemicalPotionItem;
import com.mopiux.alquimia.network.Network;
import com.mopiux.alquimia.registry.ModBlocks;
import com.mopiux.alquimia.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Prueba automática del cliente (solo en desarrollo, con {@code -Dalquimia.autotest=true}):
 * crea un mundo, arma un pequeño laboratorio con todos los bloques e ítems del mod, abre las
 * pantallas y guarda capturas en {@code run/screenshots}. Luego cierra el juego.
 */
@Mod.EventBusSubscriber(modid = Alquimia.MOD_ID, value = Dist.CLIENT)
public final class AutoTest {
    private static final boolean ENABLED = Boolean.getBoolean("alquimia.autotest");
    private static int step;
    private static int wait;
    private static int stepTicks;
    private static volatile BlockPos origin;
    private static int shots;

    private AutoTest() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        stepTicks++;
        if (stepTicks > 20 * 240) {
            Alquimia.LOGGER.error("[AutoTest] Tiempo agotado en el paso {}", step);
            mc.stop();
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        try {
            run(mc);
        } catch (Throwable t) {
            Alquimia.LOGGER.error("[AutoTest] Falló en el paso " + step, t);
            mc.stop();
        }
    }

    private static void next(int ticksToWait) {
        step++;
        wait = ticksToWait;
        stepTicks = 0;
        Alquimia.LOGGER.info("[AutoTest] paso {}", step);
    }

    private static void run(Minecraft mc) throws Exception {
        switch (step) {
            case 0 -> {
                if (mc.getOverlay() != null) return;
                if (!(mc.screen instanceof TitleScreen) && !(mc.screen instanceof AccessibilityOnboardingScreen)) return;
                mc.options.pauseOnLostFocus = false;
                mc.options.onboardAccessibility = false;
                mc.options.tutorialStep = TutorialSteps.NONE;
                mc.options.skipMultiplayerWarning = true;
                mc.options.guiScale().set(3);
                mc.resizeDisplay();
                GameRules rules = new GameRules();
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                LevelSettings settings = new LevelSettings("alquimia_autotest", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                        true, rules, WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("alquimia_autotest", settings,
                        new WorldOptions(20240601L, false, false), WorldPresets::createNormalWorldDimensions);
                next(0);
            }
            case 1 -> {
                if (mc.level == null || mc.player == null || mc.screen != null) return;
                next(80);
            }
            case 2 -> {
                onServer(mc, AutoTest::buildScene);
                next(100);
            }
            case 3 -> {
                mc.options.hideGui = true;
                next(15);
            }
            case 4 -> {
                shot(mc, "01_laboratorio");
                onServer(mc, sp -> {
                    sp.teleportTo(origin.getX() + 0.5, origin.getY(), origin.getZ() - 1.2);
                    sp.setYRot(180f);
                    sp.setXRot(8f);
                    sp.setYHeadRot(180f);
                });
                next(40);
            }
            case 5 -> {
                shot(mc, "02_menas_y_bloques");
                mc.options.hideGui = false;
                onServer(mc, AutoTest::prepareBrew);
                next(30);
            }
            case 6 -> {
                onServer(mc, sp -> {
                    BlockPos p = origin.offset(-2, 0, 1);
                    if (sp.level().getBlockEntity(p) instanceof AlchemicalCauldronBlockEntity be) {
                        NetworkHooks.openScreen(sp, be, p);
                    }
                });
                next(60);
            }
            case 7 -> {
                // Pasar el ratón sobre la pata de conejo del inventario para ver la vista previa del camino
                hoverSlotWithItem(mc, Items.RABBIT_FOOT);
                next(20);
            }
            case 8 -> {
                shot(mc, "03_caldero");
                onServer(mc, sp -> {
                    PlayerKnowledge k = AlchemyKnowledge.get(sp.server).of(sp.getUUID());
                    for (AlchemyMap m : AlchemyData.server().maps()) {
                        k.fog(m.id()).set(0, m.fogSize() * m.fogSize());
                        for (AlchemyMap.Zone z : m.zones()) k.discover(m.id(), z.effect());
                    }
                    for (AlchemyIngredient i : AlchemyData.server().ingredients()) k.learnIngredient(i.id());
                    Network.sendKnowledge(sp);
                });
                setMouse(mc, 5, 5);
                next(30);
            }
            case 9 -> {
                shot(mc, "04_mapa_revelado");
                mc.player.closeContainer();
                mc.setScreen(new GrimoireScreen(mc.level.dimension().location()));
                setMouse(mc, 5, 5);
                next(30);
            }
            case 10 -> {
                shot(mc, "05_grimorio_esencias");
                pressButton(mc, "gui.alquimia.grimoire.ingredients");
                next(10);
            }
            case 11 -> {
                if (mc.screen != null) {
                    int left = (mc.screen.width - 300) / 2, top = (mc.screen.height - 190) / 2;
                    setMouse(mc, left + 158 + 9 + 21 * 2, top + 40 + 9 + 21);
                }
                next(20);
            }
            case 12 -> {
                shot(mc, "06_grimorio_ingredientes");
                mc.setScreen(ClientSetup.createConfigScreen(null));
                setMouse(mc, 5, 5);
                next(30);
            }
            case 13 -> {
                shot(mc, "07_configuracion");
                mc.setScreen(null);
                onServer(mc, sp -> {
                    BlockPos chest = origin.offset(3, 0, 1);
                    if (sp.level().getBlockEntity(chest) instanceof ChestBlockEntity c) sp.openMenu(c);
                });
                next(40);
            }
            case 14 -> {
                hoverSlotWithItem(mc, ModItems.ALCHEMICAL_POTION.get());
                next(20);
            }
            case 15 -> {
                shot(mc, "08_objetos");
                Alquimia.LOGGER.info("[AutoTest] Listo: {} capturas", shots);
                mc.stop();
                next(1000);
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ escena
    private static void buildScene(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        level.setDayTime(6000);
        level.setWeatherParameters(20000, 0, false, false);
        origin = new BlockPos(sp.getBlockX(), 150, sp.getBlockZ());
        BlockPos o = origin;
        // limpiar y piso
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                for (int y = -1; y <= 6; y++) level.setBlock(o.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                BlockState floor = (Math.abs(x) == 7 || Math.abs(z) == 7) ? Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState()
                        : ((x + z) % 2 == 0 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.DARK_OAK_PLANKS.defaultBlockState());
                level.setBlock(o.offset(x, -1, z), floor, 2);
            }
        }
        // pared del fondo con las menas junto a su roca base
        BlockState[][] wall = {
                {Blocks.STONE.defaultBlockState(), ModBlocks.SALT_ORE.get().defaultBlockState(), Blocks.STONE.defaultBlockState(),
                        ModBlocks.CINNABAR_ORE.get().defaultBlockState(), Blocks.STONE.defaultBlockState()},
                {Blocks.DEEPSLATE.defaultBlockState(), ModBlocks.DEEPSLATE_SALT_ORE.get().defaultBlockState(), Blocks.DEEPSLATE.defaultBlockState(),
                        ModBlocks.DEEPSLATE_CINNABAR_ORE.get().defaultBlockState(), Blocks.DEEPSLATE.defaultBlockState()},
                {Blocks.NETHERRACK.defaultBlockState(), ModBlocks.NETHER_SULFUR_ORE.get().defaultBlockState(), Blocks.NETHERRACK.defaultBlockState(),
                        ModBlocks.NETHER_SULFUR_ORE.get().defaultBlockState(), Blocks.NETHERRACK.defaultBlockState()},
        };
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 5; col++) {
                level.setBlock(o.offset(col - 2, 2 - row, -4), wall[row][col], 2);
            }
        }
        level.setBlock(o.offset(-4, 0, -4), ModBlocks.SALT_BLOCK.get().defaultBlockState(), 2);
        level.setBlock(o.offset(-4, 1, -4), ModBlocks.CINNABAR_BLOCK.get().defaultBlockState(), 2);
        level.setBlock(o.offset(-4, 2, -4), ModBlocks.SULFUR_BLOCK.get().defaultBlockState(), 2);
        for (int y = 0; y < 3; y++) {
            level.setBlock(o.offset(4, y, -4), Blocks.BOOKSHELF.defaultBlockState(), 2);
            level.setBlock(o.offset(-3, y, -4), Blocks.BOOKSHELF.defaultBlockState(), 2);
            level.setBlock(o.offset(3, y, -4), Blocks.BOOKSHELF.defaultBlockState(), 2);
        }
        // caldero sobre una fogata, mortero y cofre
        BlockPos cauldron = o.offset(-2, 0, 1);
        level.setBlock(cauldron.below(), Blocks.CAMPFIRE.defaultBlockState(), 3);
        level.setBlock(cauldron, ModBlocks.ALCHEMICAL_CAULDRON.get().defaultBlockState(), 3);
        level.setBlock(o.offset(0, 0, 1), ModBlocks.MORTAR.get().defaultBlockState(), 3);
        if (level.getBlockEntity(o.offset(0, 0, 1)) instanceof MortarBlockEntity m) {
            ItemStack sugar = new ItemStack(Items.SUGAR, 8);
            m.setStack(sugar);
        }
        level.setBlock(o.offset(1, 0, 1), Blocks.LANTERN.defaultBlockState(), 3);
        BlockPos chest = o.offset(3, 0, 1);
        level.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH), 3);
        if (level.getBlockEntity(chest) instanceof ChestBlockEntity c) {
            List<ItemStack> items = List.of(
                    new ItemStack(ModItems.ALCHEMICAL_CAULDRON.get()), new ItemStack(ModItems.MORTAR.get()),
                    new ItemStack(ModItems.GRIMOIRE.get()), new ItemStack(ModItems.SALT.get(), 32),
                    new ItemStack(ModItems.CINNABAR.get(), 12), new ItemStack(ModItems.QUICKSILVER.get(), 6),
                    new ItemStack(ModItems.SULFUR.get(), 20), new ItemStack(ModItems.SALT_ORE.get(), 4),
                    new ItemStack(ModItems.DEEPSLATE_SALT_ORE.get(), 3), new ItemStack(ModItems.CINNABAR_ORE.get(), 2),
                    new ItemStack(ModItems.DEEPSLATE_CINNABAR_ORE.get(), 2), new ItemStack(ModItems.NETHER_SULFUR_ORE.get(), 5),
                    new ItemStack(ModItems.SALT_BLOCK.get()), new ItemStack(ModItems.CINNABAR_BLOCK.get()),
                    new ItemStack(ModItems.SULFUR_BLOCK.get()),
                    AlchemicalPotionItem.create(ModItems.ALCHEMICAL_POTION.get(), List.of(
                            new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 5400, 1),
                            new MobEffectInstance(MobEffects.JUMP, 3600, 0),
                            new MobEffectInstance(MobEffects.NIGHT_VISION, 3600, 0))),
                    AlchemicalPotionItem.create(ModItems.ALCHEMICAL_SPLASH_POTION.get(), List.of(
                            new MobEffectInstance(MobEffects.REGENERATION, 900, 1))),
                    AlchemicalPotionItem.create(ModItems.ALCHEMICAL_LINGERING_POTION.get(), List.of(
                            new MobEffectInstance(MobEffects.POISON, 900, 0),
                            new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 900, 1))),
                    AlchemicalPotionItem.create(ModItems.ALCHEMICAL_POTION.get(), List.of(
                            new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 3600, 0))));
            for (int i = 0; i < items.size(); i++) c.setItem(i, items.get(i));
        }
        // marcos con ítems en la pared
        Item[] framed = {ModItems.SALT.get(), ModItems.QUICKSILVER.get(), ModItems.SULFUR.get(), ModItems.GRIMOIRE.get()};
        for (int i = 0; i < framed.length; i++) {
            BlockPos support = o.offset(-1 + i, 3, -4);
            level.setBlock(support, Blocks.SPRUCE_PLANKS.defaultBlockState(), 2);
            ItemFrame frame = new ItemFrame(level, support.south(), Direction.SOUTH);
            frame.setItem(new ItemStack(framed[i]));
            level.addFreshEntity(frame);
        }
        // jugador mirando el laboratorio
        sp.getInventory().clearContent();
        ItemStack groundSugar = new ItemStack(Items.SUGAR, 16);
        Grinding.set(groundSugar, 1f);
        sp.getInventory().add(new ItemStack(Items.RABBIT_FOOT, 4));
        sp.getInventory().add(groundSugar);
        sp.getInventory().add(new ItemStack(Items.BLAZE_POWDER, 6));
        sp.getInventory().add(new ItemStack(Items.FERMENTED_SPIDER_EYE, 2));
        sp.getInventory().add(new ItemStack(Items.GLOWSTONE_DUST, 12));
        sp.getInventory().add(new ItemStack(ModItems.SULFUR.get(), 8));
        sp.getInventory().add(new ItemStack(ModItems.QUICKSILVER.get(), 4));
        sp.getInventory().add(new ItemStack(Items.POTION, 1));
        sp.getInventory().add(new ItemStack(ModItems.GRIMOIRE.get()));
        sp.teleportTo(o.getX() + 0.5, o.getY(), o.getZ() + 6.5);
        sp.setYRot(180f);
        sp.setXRot(18f);
        sp.setYHeadRot(180f);
    }

    private static void prepareBrew(ServerPlayer sp) {
        BlockPos p = origin.offset(-2, 0, 1);
        if (!(sp.level().getBlockEntity(p) instanceof AlchemicalCauldronBlockEntity be)) return;
        AlchemyData data = AlchemyData.server();
        AlchemyMap map = be.map();
        if (map == null) return;
        be.addWater(3);
        AlchemyIngredient sugar = data.find(new ItemStack(Items.SUGAR));
        AlchemyIngredient blaze = data.find(new ItemStack(Items.BLAZE_POWDER));
        AlchemyIngredient wheat = data.find(new ItemStack(Items.WHEAT_SEEDS));
        PlayerKnowledge k = AlchemyKnowledge.get(sp.server).of(sp.getUUID());
        BrewState.PointVisitor explore = (x, y) -> {
            k.reveal(map, x, y, PlayerKnowledge.REVEAL_RADIUS);
            AlchemyMap.Zone z = map.zoneAt(x, y);
            if (z != null) k.discover(map.id(), z.effect());
            return true;
        };
        if (sugar != null) be.applyIngredient(sugar, 1f);
        be.brew().advance(29f, 0.5f, explore);
        AlchemyMap.Zone speed = map.zoneAt(be.brew().x, be.brew().y);
        if (speed != null) be.brew().fixed.add(new BrewState.FixedEssence(speed.effect(), 1, speed.duration(), false));
        if (blaze != null) be.applyIngredient(blaze, 1f);
        if (wheat != null) be.applyIngredient(wheat, 1f);
        be.brew().advance(22f, 0.5f, explore);
        be.brew().ingredientsUsed = 3;
        be.items().setStackInSlot(AlchemicalCauldronBlockEntity.SLOT_REAGENT, new ItemStack(ModItems.SALT.get(), 12));
        be.items().setStackInSlot(AlchemicalCauldronBlockEntity.SLOT_BOTTLES, new ItemStack(Items.GLASS_BOTTLE, 3));
        be.markBrewChanged();
        AlchemyKnowledge.get(sp.server).setDirty();
        Network.sendKnowledge(sp);
    }

    // ------------------------------------------------------------------ utilidades
    private interface ServerAction {
        void run(ServerPlayer sp) throws Exception;
    }

    private static void onServer(Minecraft mc, ServerAction action) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) return;
        java.util.UUID id = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(id);
            if (sp == null) return;
            try {
                action.run(sp);
            } catch (Exception e) {
                Alquimia.LOGGER.error("[AutoTest] Error en el servidor", e);
            }
        });
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(),
                msg -> Alquimia.LOGGER.info("[AutoTest] Captura {}: {}", name, msg.getString()));
        shots++;
    }

    /** Mueve el ratón a coordenadas de la interfaz (escaladas). */
    private static void setMouse(Minecraft mc, double guiX, double guiY) {
        try {
            double scale = mc.getWindow().getGuiScale();
            MouseHandler mh = mc.mouseHandler;
            Field fx = MouseHandler.class.getDeclaredField("xpos");
            Field fy = MouseHandler.class.getDeclaredField("ypos");
            fx.setAccessible(true);
            fy.setAccessible(true);
            fx.setDouble(mh, guiX * scale);
            fy.setDouble(mh, guiY * scale);
        } catch (ReflectiveOperationException e) {
            Alquimia.LOGGER.warn("[AutoTest] No se pudo mover el ratón: {}", e.toString());
        }
    }

    private static void hoverSlotWithItem(Minecraft mc, Item item) {
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) return;
        for (Slot slot : screen.getMenu().slots) {
            if (slot.getItem().is(item)) {
                setMouse(mc, screen.getGuiLeft() + slot.x + 8, screen.getGuiTop() + slot.y + 8);
                return;
            }
        }
    }

    private static void pressButton(Minecraft mc, String translationKey) {
        if (mc.screen == null) return;
        for (var child : mc.screen.children()) {
            if (child instanceof Button b && b.getMessage().getContents() instanceof TranslatableContents tc
                    && tc.getKey().equals(translationKey)) {
                b.onPress();
                return;
            }
        }
    }
}
