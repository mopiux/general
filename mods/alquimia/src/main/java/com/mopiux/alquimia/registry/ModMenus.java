package com.mopiux.alquimia.registry;

import com.mopiux.alquimia.Alquimia;
import com.mopiux.alquimia.menu.CauldronMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, Alquimia.MOD_ID);

    public static final RegistryObject<MenuType<CauldronMenu>> CAULDRON = MENUS.register("alchemical_cauldron",
            () -> IForgeMenuType.create(CauldronMenu::fromNetwork));

    private ModMenus() {
    }
}
