package com.mopiux.alquimia.alchemy;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mopiux.alquimia.network.Network;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.BitSet;
import java.util.Collection;
import java.util.List;

/**
 * {@code /alquimia revelar|reveal [jugadores]} revela todos los mapas (útil en creativo o para
 * quien prefiera no explorar) y {@code /alquimia olvidar|forget [jugadores]} borra lo aprendido.
 */
public final class AlchemyCommand {
    private AlchemyCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("alquimia").requires(s -> s.hasPermission(2));
        for (String name : List.of("revelar", "reveal")) {
            root.then(Commands.literal(name)
                    .executes(ctx -> reveal(ctx, List.of(ctx.getSource().getPlayerOrException())))
                    .then(Commands.argument("players", EntityArgument.players())
                            .executes(ctx -> reveal(ctx, EntityArgument.getPlayers(ctx, "players")))));
        }
        for (String name : List.of("olvidar", "forget")) {
            root.then(Commands.literal(name)
                    .executes(ctx -> forget(ctx, List.of(ctx.getSource().getPlayerOrException())))
                    .then(Commands.argument("players", EntityArgument.players())
                            .executes(ctx -> forget(ctx, EntityArgument.getPlayers(ctx, "players")))));
        }
        dispatcher.register(root);
    }

    private static int reveal(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) throws CommandSyntaxException {
        AlchemyData data = AlchemyData.server();
        for (ServerPlayer p : players) {
            AlchemyKnowledge all = AlchemyKnowledge.get(p.server);
            PlayerKnowledge k = all.of(p.getUUID());
            for (AlchemyMap map : data.maps()) {
                BitSet fog = k.fog(map.id());
                fog.set(0, map.fogSize() * map.fogSize());
                for (AlchemyMap.Zone z : map.zones()) k.discover(map.id(), z.effect());
            }
            for (AlchemyIngredient i : data.ingredients()) k.learnIngredient(i.id());
            all.setDirty();
            Network.sendKnowledge(p);
        }
        int n = players.size();
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.alquimia.reveal", n), true);
        return n;
    }

    private static int forget(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) throws CommandSyntaxException {
        for (ServerPlayer p : players) {
            AlchemyKnowledge all = AlchemyKnowledge.get(p.server);
            all.of(p.getUUID()).copyFrom(new PlayerKnowledge());
            all.setDirty();
            Network.sendKnowledge(p);
        }
        int n = players.size();
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.alquimia.forget", n), true);
        return n;
    }
}
