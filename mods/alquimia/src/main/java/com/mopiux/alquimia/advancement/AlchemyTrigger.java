package com.mopiux.alquimia.advancement;

import com.google.gson.JsonObject;
import com.mopiux.alquimia.Alquimia;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;

/**
 * Criterio de logros genérico del mod: {@code {"trigger": "alquimia:alchemy", "conditions": {"event": "bottle", "min": 2}}}.
 * Eventos: grind, fix, empower, prolong, bottle, discover, explode.
 */
public final class AlchemyTrigger extends SimpleCriterionTrigger<AlchemyTrigger.Instance> {
    public static final ResourceLocation ID = new ResourceLocation(Alquimia.MOD_ID, "alchemy");
    public static final AlchemyTrigger INSTANCE = new AlchemyTrigger();

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    protected Instance createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext ctx) {
        return new Instance(player, GsonHelper.getAsString(json, "event"), GsonHelper.getAsInt(json, "min", 1));
    }

    public void trigger(ServerPlayer player, String event, int value) {
        trigger(player, inst -> inst.matches(event, value));
    }

    public static final class Instance extends AbstractCriterionTriggerInstance {
        private final String event;
        private final int min;

        public Instance(ContextAwarePredicate player, String event, int min) {
            super(ID, player);
            this.event = event;
            this.min = min;
        }

        boolean matches(String e, int value) {
            return event.equals(e) && value >= min;
        }

        @Override
        public JsonObject serializeToJson(SerializationContext ctx) {
            JsonObject json = super.serializeToJson(ctx);
            json.addProperty("event", event);
            json.addProperty("min", min);
            return json;
        }
    }
}
