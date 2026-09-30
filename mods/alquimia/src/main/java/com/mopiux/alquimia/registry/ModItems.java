package com.mopiux.alquimia.registry;

import com.mopiux.alquimia.Alquimia;
import com.mopiux.alquimia.item.AlchemicalPotionItem;
import com.mopiux.alquimia.item.AlchemicalThrowableItem;
import com.mopiux.alquimia.item.GrimoireItem;
import com.mopiux.alquimia.item.TooltipItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Alquimia.MOD_ID);

    // --- Reactivos (tria prima)
    public static final RegistryObject<Item> SALT = ITEMS.register("salt",
            () -> new TooltipItem(new Item.Properties(), "item.alquimia.salt.desc"));
    public static final RegistryObject<Item> CINNABAR = ITEMS.register("cinnabar",
            () -> new TooltipItem(new Item.Properties(), "item.alquimia.cinnabar.desc"));
    public static final RegistryObject<Item> QUICKSILVER = ITEMS.register("quicksilver",
            () -> new TooltipItem(new Item.Properties().rarity(Rarity.UNCOMMON), "item.alquimia.quicksilver.desc"));
    public static final RegistryObject<Item> SULFUR = ITEMS.register("sulfur",
            () -> new TooltipItem(new Item.Properties(), "item.alquimia.sulfur.desc"));

    // --- Pociones alquímicas
    public static final RegistryObject<Item> ALCHEMICAL_POTION = ITEMS.register("alchemical_potion",
            () -> new AlchemicalPotionItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> ALCHEMICAL_SPLASH_POTION = ITEMS.register("alchemical_splash_potion",
            () -> new AlchemicalThrowableItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON), false));
    public static final RegistryObject<Item> ALCHEMICAL_LINGERING_POTION = ITEMS.register("alchemical_lingering_potion",
            () -> new AlchemicalThrowableItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON), true));

    public static final RegistryObject<Item> GRIMOIRE = ITEMS.register("grimoire",
            () -> new GrimoireItem(new Item.Properties().stacksTo(1)));

    // --- Bloques
    public static final RegistryObject<Item> ALCHEMICAL_CAULDRON = block(ModBlocks.ALCHEMICAL_CAULDRON);
    public static final RegistryObject<Item> MORTAR = block(ModBlocks.MORTAR);
    public static final RegistryObject<Item> SALT_ORE = block(ModBlocks.SALT_ORE);
    public static final RegistryObject<Item> DEEPSLATE_SALT_ORE = block(ModBlocks.DEEPSLATE_SALT_ORE);
    public static final RegistryObject<Item> CINNABAR_ORE = block(ModBlocks.CINNABAR_ORE);
    public static final RegistryObject<Item> DEEPSLATE_CINNABAR_ORE = block(ModBlocks.DEEPSLATE_CINNABAR_ORE);
    public static final RegistryObject<Item> NETHER_SULFUR_ORE = block(ModBlocks.NETHER_SULFUR_ORE);
    public static final RegistryObject<Item> SALT_BLOCK = block(ModBlocks.SALT_BLOCK);
    public static final RegistryObject<Item> CINNABAR_BLOCK = block(ModBlocks.CINNABAR_BLOCK);
    public static final RegistryObject<Item> SULFUR_BLOCK = block(ModBlocks.SULFUR_BLOCK);

    private static RegistryObject<Item> block(RegistryObject<? extends Block> block) {
        Supplier<Item> s = () -> new BlockItem(block.get(), new Item.Properties());
        return ITEMS.register(block.getId().getPath(), s);
    }

    private ModItems() {
    }
}
