package com.mopiux.planetoides;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(Planetoides.MOD_ID)
public final class Planetoides {
    public static final String MOD_ID = "planetoides";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Planetoides() {
        LOGGER.info("Cargando {}", MOD_ID);
    }
}
