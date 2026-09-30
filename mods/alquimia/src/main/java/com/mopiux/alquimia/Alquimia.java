package com.mopiux.alquimia;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(Alquimia.MOD_ID)
public final class Alquimia {
    public static final String MOD_ID = "alquimia";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Alquimia() {
        LOGGER.info("Cargando {}", MOD_ID);
    }
}
