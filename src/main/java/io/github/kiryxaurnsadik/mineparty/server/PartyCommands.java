package io.github.kiryxaurnsadik.mineparty.server;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.github.kiryxaurnsadik.mineparty.party.Limits;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server commands. They also work for players without the mod, so a party can be run from chat:
 * /party create [name], /party invite <player> [n], /party accept|decline <id>, /party leave [n], /pc [n] <text>.
 * {@code n} is the party number 1..3 in join order.
 */
public final class PartyCommands {
    private PartyCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, Supplier<PartyManager> manager) {
        dispatcher.register(Commands.literal("party")
                .then(Commands.literal("create")
                        .executes(ctx -> run(ctx, p -> manager.get().create(p, "")))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> run(ctx, p -> manager.get().create(p, StringArgumentType.getString(ctx, "name"))))))
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> invite(ctx, manager.get(), 1))
                                .then(Commands.argument("n", IntegerArgumentType.integer(1, Limits.MAX_PARTIES_PER_PLAYER))
                                        .executes(ctx -> invite(ctx, manager.get(), IntegerArgumentType.getInteger(ctx, "n"))))))
                .then(Commands.literal("accept")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> run(ctx, p -> manager.get().accept(p, parseId(ctx))))))
                .then(Commands.literal("decline")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> run(ctx, p -> manager.get().decline(p, parseId(ctx))))))
                .then(Commands.literal("leave")
                        .executes(ctx -> byIndex(ctx, manager.get(), 1, (p, id) -> manager.get().leave(p, id)))
                        .then(Commands.argument("n", IntegerArgumentType.integer(1, Limits.MAX_PARTIES_PER_PLAYER))
                                .executes(ctx -> byIndex(ctx, manager.get(), IntegerArgumentType.getInteger(ctx, "n"),
                                        (p, id) -> manager.get().leave(p, id))))));

        dispatcher.register(Commands.literal("pc")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> partyChat(ctx, manager.get()))));
    }

    private interface PlayerAction {
        void run(ServerPlayer player);
    }

    private interface PartyAction {
        void run(ServerPlayer player, UUID party);
    }

    private static int run(CommandContext<CommandSourceStack> ctx, PlayerAction action) throws CommandSyntaxException {
        action.run(ctx.getSource().getPlayerOrException());
        return 1;
    }

    private static UUID parseId(CommandContext<CommandSourceStack> ctx) {
        try {
            return UUID.fromString(StringArgumentType.getString(ctx, "id"));
        } catch (IllegalArgumentException e) {
            return new UUID(0, 0);
        }
    }

    private static int byIndex(CommandContext<CommandSourceStack> ctx, PartyManager manager, int n, PartyAction action)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        List<UUID> ids = manager.partyIdsOf(player.getUUID());
        if (n > ids.size()) {
            ctx.getSource().sendFailure(Component.literal(ids.isEmpty()
                    ? "Вы не состоите в отряде. /party create"
                    : "Нет отряда номер " + n));
            return 0;
        }
        action.run(player, ids.get(n - 1));
        return 1;
    }

    private static int invite(CommandContext<CommandSourceStack> ctx, PartyManager manager, int n) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        return byIndex(ctx, manager, n, (p, id) -> manager.invite(p, id, target.getUUID()));
    }

    private static int partyChat(CommandContext<CommandSourceStack> ctx, PartyManager manager) throws CommandSyntaxException {
        String message = StringArgumentType.getString(ctx, "message");
        int n = 1;
        if (message.length() > 2 && Character.isDigit(message.charAt(0)) && message.charAt(1) == ' ') {
            n = message.charAt(0) - '0';
            message = message.substring(2);
        }
        String text = message;
        if (n < 1 || n > Limits.MAX_PARTIES_PER_PLAYER) {
            n = 1;
        }
        return byIndex(ctx, manager, n, (p, id) -> manager.chat(p, id, text));
    }
}
