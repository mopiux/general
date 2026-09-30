package com.mopiux.alquimia.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Configuración del mod.
 * <ul>
 *     <li>COMMON: reglas de juego y generación de mundo (las usa el servidor).</li>
 *     <li>CLIENT: accesibilidad y preferencias visuales de cada jugador.</li>
 * </ul>
 * Todas las opciones se pueden cambiar desde el menú Mods → Alquimia Cartográfica → Config.
 */
public final class AlquimiaConfig {
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final ForgeConfigSpec CLIENT_SPEC;

    // --- Reglas de alquimia
    public static final ForgeConfigSpec.IntValue MAX_EFFECTS;
    public static final ForgeConfigSpec.IntValue MAX_AMPLIFIER;
    public static final ForgeConfigSpec.DoubleValue STIR_SPEED;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_HEAT;
    public static final ForgeConfigSpec.BooleanValue HAZARDS_EXPLODE;
    public static final ForgeConfigSpec.BooleanValue EXPLOSIONS_BREAK_BLOCKS;
    public static final ForgeConfigSpec.DoubleValue DURATION_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue SHARED_DISCOVERIES;

    // --- Mundo
    public static final ForgeConfigSpec.IntValue SALT_VEINS;
    public static final ForgeConfigSpec.IntValue CINNABAR_VEINS;
    public static final ForgeConfigSpec.IntValue SULFUR_VEINS;

    // --- Cliente
    public static final ForgeConfigSpec.BooleanValue HOLD_TO_STIR;
    public static final ForgeConfigSpec.BooleanValue HIGH_CONTRAST;
    public static final ForgeConfigSpec.BooleanValue SHOW_ZONE_LABELS;
    public static final ForgeConfigSpec.BooleanValue REDUCE_MOTION;
    public static final ForgeConfigSpec.BooleanValue BUBBLE_PARTICLES;
    public static final ForgeConfigSpec.DoubleValue DEFAULT_ZOOM;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Reglas de la alquimia").push("alquimia");
        MAX_EFFECTS = b.comment("Cantidad máxima de esencias que se pueden fijar en una misma poción.")
                .translation("alquimia.config.alquimia.max_effects")
                .defineInRange("max_effects", 3, 1, 6);
        MAX_AMPLIFIER = b.comment("Nivel máximo de potencia (0 = nivel I, 1 = nivel II...).")
                .translation("alquimia.config.alquimia.max_amplifier")
                .defineInRange("max_amplifier", 2, 0, 4);
        STIR_SPEED = b.comment("Multiplicador de la velocidad al remover.")
                .translation("alquimia.config.alquimia.stir_speed")
                .defineInRange("stir_speed", 1.0, 0.1, 5.0);
        REQUIRE_HEAT = b.comment("Si es verdadero, el caldero necesita una fuente de calor debajo para poder remover.")
                .translation("alquimia.config.alquimia.require_heat")
                .define("require_heat", true);
        HAZARDS_EXPLODE = b.comment("Si es verdadero, tocar una zona de peligro del mapa hace explotar la mezcla.")
                .translation("alquimia.config.alquimia.hazards_explode")
                .define("hazards_explode", true);
        EXPLOSIONS_BREAK_BLOCKS = b.comment("Si es verdadero, las explosiones del caldero rompen bloques.")
                .translation("alquimia.config.alquimia.explosions_break_blocks")
                .define("explosions_break_blocks", false);
        DURATION_MULTIPLIER = b.comment("Multiplicador global de la duración de las pociones alquímicas.")
                .translation("alquimia.config.alquimia.duration_multiplier")
                .defineInRange("duration_multiplier", 1.0, 0.1, 10.0);
        SHARED_DISCOVERIES = b.comment("Si es verdadero, todos los jugadores comparten el mapa explorado y las esencias descubiertas.")
                .translation("alquimia.config.alquimia.shared_discoveries")
                .define("shared_discoveries", false);
        b.pop();

        b.comment("Generación de minerales (vetas por chunk; 0 desactiva el mineral).").push("mundo");
        SALT_VEINS = b.translation("alquimia.config.mundo.salt_veins").defineInRange("salt_veins", 8, 0, 64);
        CINNABAR_VEINS = b.translation("alquimia.config.mundo.cinnabar_veins").defineInRange("cinnabar_veins", 5, 0, 64);
        SULFUR_VEINS = b.translation("alquimia.config.mundo.sulfur_veins").defineInRange("sulfur_veins", 10, 0, 64);
        b.pop();
        COMMON_SPEC = b.build();

        ForgeConfigSpec.Builder c = new ForgeConfigSpec.Builder();
        c.comment("Accesibilidad y preferencias visuales").push("accesibilidad");
        HOLD_TO_STIR = c.comment("Verdadero: mantener apretado para remover. Falso: un clic empieza y otro clic detiene.")
                .translation("alquimia.config.accesibilidad.hold_to_stir")
                .define("hold_to_stir", true);
        HIGH_CONTRAST = c.comment("Mapa de alto contraste (fondo oscuro, líneas gruesas).")
                .translation("alquimia.config.accesibilidad.high_contrast")
                .define("high_contrast", false);
        SHOW_ZONE_LABELS = c.comment("Mostrar el nombre de las esencias descubiertas sobre el mapa.")
                .translation("alquimia.config.accesibilidad.show_zone_labels")
                .define("show_zone_labels", true);
        REDUCE_MOTION = c.comment("Reduce animaciones: la cámara del mapa no se desliza y no hay destellos.")
                .translation("alquimia.config.accesibilidad.reduce_motion")
                .define("reduce_motion", false);
        BUBBLE_PARTICLES = c.comment("Partículas de burbujas sobre el caldero.")
                .translation("alquimia.config.accesibilidad.bubble_particles")
                .define("bubble_particles", true);
        DEFAULT_ZOOM = c.comment("Zoom inicial del mapa alquímico.")
                .translation("alquimia.config.accesibilidad.default_zoom")
                .defineInRange("default_zoom", 1.0, 0.5, 3.0);
        c.pop();
        CLIENT_SPEC = c.build();
    }

    private AlquimiaConfig() {
    }

    public static void register(ModLoadingContext ctx) {
        ctx.registerConfig(ModConfig.Type.COMMON, COMMON_SPEC);
        ctx.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC);
    }

    /** Valor de vetas por nombre (lo usa el modificador de colocación de minerales). */
    public static int veinsFor(String key) {
        if (!COMMON_SPEC.isLoaded()) return 0;
        return switch (key) {
            case "salt" -> SALT_VEINS.get();
            case "cinnabar" -> CINNABAR_VEINS.get();
            case "sulfur" -> SULFUR_VEINS.get();
            default -> 0;
        };
    }
}
