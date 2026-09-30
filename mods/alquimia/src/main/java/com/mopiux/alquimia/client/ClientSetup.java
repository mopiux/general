package com.mopiux.alquimia.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mopiux.alquimia.Alquimia;
import com.mopiux.alquimia.alchemy.AlchemyData;
import com.mopiux.alquimia.alchemy.AlchemyIngredient;
import com.mopiux.alquimia.alchemy.Grinding;
import com.mopiux.alquimia.config.AlquimiaConfig;
import com.mopiux.alquimia.config.ConfigScreen;
import com.mopiux.alquimia.registry.ModBlockEntities;
import com.mopiux.alquimia.registry.ModItems;
import com.mopiux.alquimia.registry.ModMenus;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Registro de todo lo que es solo del cliente: pantallas, renderizadores, colores, teclas. */
public final class ClientSetup {
    private static final String CATEGORY = "key.categories.alquimia";
    public static final KeyMapping KEY_STIR = key("stir", GLFW.GLFW_KEY_SPACE);
    public static final KeyMapping KEY_DILUTE = key("dilute", GLFW.GLFW_KEY_D);
    public static final KeyMapping KEY_FIX = key("fix", GLFW.GLFW_KEY_S);
    public static final KeyMapping KEY_EMPOWER = key("empower", GLFW.GLFW_KEY_A);
    public static final KeyMapping KEY_PROLONG = key("prolong", GLFW.GLFW_KEY_M);
    public static final KeyMapping KEY_BOTTLE = key("bottle", GLFW.GLFW_KEY_B);
    public static final KeyMapping KEY_CENTER = key("center", GLFW.GLFW_KEY_C);

    private ClientSetup() {
    }

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.alquimia." + name, KeyConflictContext.GUI, InputConstants.Type.KEYSYM, code, CATEGORY);
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientSetup::clientSetup);
        modBus.addListener(ClientSetup::registerRenderers);
        modBus.addListener(ClientSetup::registerItemColors);
        modBus.addListener(ClientSetup::registerKeys);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onTooltip);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onLogout);
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new ConfigScreen(parent, Alquimia.MOD_ID,
                        Component.translatable("alquimia.config.title"), List.of(
                        new ConfigScreen.Section(Component.translatable("alquimia.config.section.client"), AlquimiaConfig.CLIENT_SPEC, false),
                        new ConfigScreen.Section(Component.translatable("alquimia.config.section.common"), AlquimiaConfig.COMMON_SPEC, false)))));
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ModMenus.CAULDRON.get(), CauldronScreen::new));
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.ALCHEMICAL_CAULDRON.get(), CauldronRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.MORTAR.get(), MortarRenderer::new);
    }

    private static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, layer) -> layer == 0 ? PotionUtils.getColor(stack) : -1,
                ModItems.ALCHEMICAL_POTION.get(), ModItems.ALCHEMICAL_SPLASH_POTION.get(), ModItems.ALCHEMICAL_LINGERING_POTION.get());
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        for (KeyMapping k : new KeyMapping[]{KEY_STIR, KEY_DILUTE, KEY_FIX, KEY_EMPOWER, KEY_PROLONG, KEY_BOTTLE, KEY_CENTER}) {
            event.register(k);
        }
    }

    private static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        AlchemyIngredient ing = AlchemyData.get(true).find(stack);
        if (ing == null) return;
        List<Component> lines = ingredientLines(ing, Grinding.pathFraction(stack));
        if (!ing.isTransform()) {
            lines.add(Grinding.get(stack) >= 1f
                    ? Component.translatable("tooltip.alquimia.ground").withStyle(ChatFormatting.GREEN)
                    : Component.translatable("tooltip.alquimia.unground").withStyle(ChatFormatting.DARK_GRAY));
        }
        event.getToolTip().addAll(1, lines);
    }

    /** Líneas que describen un ingrediente (tooltip del ítem y del grimorio). */
    public static List<Component> ingredientLines(AlchemyIngredient ing, float fraction) {
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable("tooltip.alquimia.ingredient").withStyle(ChatFormatting.DARK_PURPLE));
        switch (ing.transform()) {
            case ROTATE -> out.add(Component.translatable("tooltip.alquimia.rotate", Math.round(ing.amount())).withStyle(ChatFormatting.GRAY));
            case SCALE -> out.add(Component.translatable(ing.amount() < 1 ? "tooltip.alquimia.shrink" : "tooltip.alquimia.stretch",
                    Math.round(ing.amount() * 100)).withStyle(ChatFormatting.GRAY));
            case MIRROR -> out.add(Component.translatable("tooltip.alquimia.mirror").withStyle(ChatFormatting.GRAY));
            default -> out.add(Component.translatable("tooltip.alquimia.path", String.format("%.0f", ing.length() * fraction))
                    .withStyle(ChatFormatting.GRAY));
        }
        return out;
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientAccess.reset();
        AlchemyData.setClient(AlchemyData.EMPTY);
    }
}
