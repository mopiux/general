package com.mopiux.lenguaperdida;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(LenguaPerdida.MOD_ID)
public final class LenguaPerdida {
    public static final String MOD_ID = "lenguaperdida";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LenguaPerdida() {
        LOGGER.info("Cargando {}", MOD_ID);
    }
}
