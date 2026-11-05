package dev.elementalcircuits.server;

import static net.minecraft.commands.Commands.*;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.mana.ManaField;
import dev.elementalcircuits.network.CircuitNetwork;
import dev.elementalcircuits.server.mana.PlayerMana;
import dev.elementalcircuits.server.mana.WorldManaField;

import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigDecimal;
import java.util.*;

/** Operator-only, server-thread commands. No text packet grants authority. */
public final class AdminCommands {
    private AdminCommands() {}

    private interface Action {
        int run() throws Exception;
    }

    private static int guarded(Action action)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        try {
            return action.run();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw e;
        } catch (Exception e) {
            throw new SimpleCommandExceptionType(
                            Component.literal(
                                    e.getMessage() == null
                                            ? dev.elementalcircuits.text.Texts.get(
                                                    "text.elementalcircuits.server._admin_commands.001")
                                            : e.getMessage()))
                    .create();
        }
    }

    private static void say(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message), true);
    }

    private static long quantity(String value) {
        try {
            return new BigDecimal(value).multiply(BigDecimal.valueOf(Fixed.ONE)).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server._admin_commands.002"));
        }
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = literal("elementalcircuits").requires(s -> s.hasPermission(2));
        var mana = literal("mana");
        mana.then(
                literal("get")
                        .then(
                                argument("players", EntityArgument.players())
                                        .executes(
                                                c ->
                                                        guarded(
                                                                () -> {
                                                                    var players =
                                                                            EntityArgument
                                                                                    .getPlayers(
                                                                                            c,
                                                                                            "players");
                                                                    for (var p : players) {
                                                                        var pool =
                                                                                PlayerMana.get(p);
                                                                        say(
                                                                                c.getSource(),
                                                                                p
                                                                                                .getScoreboardName()
                                                                                        + "："
                                                                                        + Fixed
                                                                                                .display(
                                                                                                        pool
                                                                                                                .available())
                                                                                        + " / "
                                                                                        + Fixed
                                                                                                .display(
                                                                                                        pool
                                                                                                                .capacity())
                                                                                        + dev
                                                                                                .elementalcircuits
                                                                                                .text
                                                                                                .Texts
                                                                                                .get(
                                                                                                        "text.elementalcircuits.server._admin_commands.003")
                                                                                        + Fixed
                                                                                                .display(
                                                                                                        pool
                                                                                                                .regeneration())
                                                                                        + dev
                                                                                                .elementalcircuits
                                                                                                .text
                                                                                                .Texts
                                                                                                .get(
                                                                                                        "text.elementalcircuits.server._admin_commands.004")
                                                                                        + pool
                                                                                                .recoveryDelay()
                                                                                        + dev
                                                                                                .elementalcircuits
                                                                                                .text
                                                                                                .Texts
                                                                                                .get(
                                                                                                        "text.elementalcircuits.server._admin_commands.005"));
                                                                    }
                                                                    return players.size();
                                                                }))));
        for (String operation : List.of("set", "add", "capacity", "regen", "delay"))
            mana.then(
                    literal(operation)
                            .then(
                                    argument("players", EntityArgument.players())
                                            .then(
                                                    argument("value", StringArgumentType.word())
                                                            .executes(
                                                                    c ->
                                                                            guarded(
                                                                                    () ->
                                                                                            edit(
                                                                                                    c
                                                                                                            .getSource(),
                                                                                                    EntityArgument
                                                                                                            .getPlayers(
                                                                                                                    c,
                                                                                                                    "players"),
                                                                                                    operation,
                                                                                                    StringArgumentType
                                                                                                            .getString(
                                                                                                                    c,
                                                                                                                    "value")))))));
        for (String operation : List.of("refill", "resetRecovery"))
            mana.then(
                    literal(operation)
                            .then(
                                    argument("players", EntityArgument.players())
                                            .executes(
                                                    c ->
                                                            guarded(
                                                                    () ->
                                                                            edit(
                                                                                    c.getSource(),
                                                                                    EntityArgument
                                                                                            .getPlayers(
                                                                                                    c,
                                                                                                    "players"),
                                                                                    operation,
                                                                                    "0")))));
        root.then(mana);
        var config =
                literal("config")
                        .executes(
                                c -> {
                                    say(
                                            c.getSource(),
                                            dev.elementalcircuits.text.Texts.get(
                                                            "text.elementalcircuits.server._admin_commands.006")
                                                    + String.join(
                                                            ", ", ServerSettings.VALUES.keySet()));
                                    return 1;
                                });
        config.then(
                literal("get")
                        .then(
                                argument("key", StringArgumentType.word())
                                        .suggests(
                                                (c, b) ->
                                                        SharedSuggestionProvider.suggest(
                                                                ServerSettings.VALUES.keySet(), b))
                                        .executes(
                                                c ->
                                                        guarded(
                                                                () -> {
                                                                    String key =
                                                                            StringArgumentType
                                                                                    .getString(
                                                                                            c,
                                                                                            "key");
                                                                    var setting =
                                                                            ServerSettings.VALUES
                                                                                    .get(key);
                                                                    if (setting == null)
                                                                        throw new IllegalArgumentException(
                                                                                dev
                                                                                        .elementalcircuits
                                                                                        .text.Texts
                                                                                        .get(
                                                                                                "text.elementalcircuits.server._admin_commands.007"));
                                                                    say(
                                                                            c.getSource(),
                                                                            key
                                                                                    + " = "
                                                                                    + ServerSettings
                                                                                            .get(
                                                                                                    key)
                                                                                    + dev
                                                                                            .elementalcircuits
                                                                                            .text
                                                                                            .Texts
                                                                                            .get(
                                                                                                    "text.elementalcircuits.server._admin_commands.008")
                                                                                    + setting.min()
                                                                                    + "–"
                                                                                    + setting.max()
                                                                                    + "；"
                                                                                    + setting
                                                                                            .description());
                                                                    return 1;
                                                                }))));
        config.then(
                literal("set")
                        .then(
                                argument("key", StringArgumentType.word())
                                        .suggests(
                                                (c, b) ->
                                                        SharedSuggestionProvider.suggest(
                                                                ServerSettings.VALUES.keySet(), b))
                                        .then(
                                                argument("value", StringArgumentType.word())
                                                        .executes(
                                                                c ->
                                                                        guarded(
                                                                                () -> {
                                                                                    String key =
                                                                                            StringArgumentType
                                                                                                    .getString(
                                                                                                            c,
                                                                                                            "key");
                                                                                    ServerSettings
                                                                                            .set(
                                                                                                    key,
                                                                                                    Double
                                                                                                            .parseDouble(
                                                                                                                    StringArgumentType
                                                                                                                            .getString(
                                                                                                                                    c,
                                                                                                                                    "value")));
                                                                                    for (var p :
                                                                                            c.getSource()
                                                                                                    .getServer()
                                                                                                    .getPlayerList()
                                                                                                    .getPlayers())
                                                                                        CircuitNetwork
                                                                                                .syncPolicy(
                                                                                                        p);
                                                                                    say(
                                                                                            c
                                                                                                    .getSource(),
                                                                                            dev
                                                                                                            .elementalcircuits
                                                                                                            .text
                                                                                                            .Texts
                                                                                                            .get(
                                                                                                                    "text.elementalcircuits.server._admin_commands.009")
                                                                                                    + key
                                                                                                    + " = "
                                                                                                    + ServerSettings
                                                                                                            .get(
                                                                                                                    key)
                                                                                                    + dev
                                                                                                            .elementalcircuits
                                                                                                            .text
                                                                                                            .Texts
                                                                                                            .get(
                                                                                                                    "text.elementalcircuits.server._admin_commands.010")
                                                                                                    + " /ec field"
                                                                                                    + " applyConfig。");
                                                                                    return 1;
                                                                                })))));
        config.then(
                literal("reset")
                        .then(
                                argument("key", StringArgumentType.word())
                                        .suggests(
                                                (c, b) ->
                                                        SharedSuggestionProvider.suggest(
                                                                ServerSettings.VALUES.keySet(), b))
                                        .executes(
                                                c ->
                                                        guarded(
                                                                () -> {
                                                                    String key =
                                                                            StringArgumentType
                                                                                    .getString(
                                                                                            c,
                                                                                            "key");
                                                                    var setting =
                                                                            ServerSettings.VALUES
                                                                                    .get(key);
                                                                    if (setting == null)
                                                                        throw new IllegalArgumentException(
                                                                                dev
                                                                                        .elementalcircuits
                                                                                        .text.Texts
                                                                                        .get(
                                                                                                "text.elementalcircuits.server._admin_commands.007"));
                                                                    ServerSettings.set(
                                                                            key, setting.initial());
                                                                    for (var p :
                                                                            c.getSource()
                                                                                    .getServer()
                                                                                    .getPlayerList()
                                                                                    .getPlayers())
                                                                        CircuitNetwork.syncPolicy(
                                                                                p);
                                                                    say(
                                                                            c.getSource(),
                                                                            dev.elementalcircuits
                                                                                            .text
                                                                                            .Texts
                                                                                            .get(
                                                                                                    "text.elementalcircuits.server._admin_commands.011")
                                                                                    + key);
                                                                    return 1;
                                                                }))));
        root.then(config);
        var field = literal("field");
        field.then(
                literal("applyConfig")
                        .executes(
                                c ->
                                        guarded(
                                                () -> {
                                                    WorldManaField.get(c.getSource().getLevel())
                                                            .applySettings();
                                                    say(
                                                            c.getSource(),
                                                            dev.elementalcircuits.text.Texts.get(
                                                                    "text.elementalcircuits.server._admin_commands.012"));
                                                    return 1;
                                                })));
        field.then(
                literal("get")
                        .then(
                                argument("pos", BlockPosArgument.blockPos())
                                        .executes(
                                                c ->
                                                        guarded(
                                                                () -> {
                                                                    var level =
                                                                            c.getSource()
                                                                                    .getLevel();
                                                                    var pos =
                                                                            BlockPosArgument
                                                                                    .getLoadedBlockPos(
                                                                                            c,
                                                                                            "pos");
                                                                    var data =
                                                                            WorldManaField.get(
                                                                                    level);
                                                                    var f = data.field();
                                                                    var cell =
                                                                            ManaField.Cell.at(
                                                                                    pos.getX(),
                                                                                    pos.getY(),
                                                                                    pos.getZ());
                                                                    var s = f.snapshot();
                                                                    say(
                                                                            c.getSource(),
                                                                            dev.elementalcircuits
                                                                                            .text
                                                                                            .Texts
                                                                                            .get(
                                                                                                    "text.elementalcircuits.server._admin_commands.013")
                                                                                    + cell
                                                                                    + "："
                                                                                    + Fixed.display(
                                                                                            f
                                                                                                    .amount(
                                                                                                            cell))
                                                                                    + " / "
                                                                                    + Fixed.display(
                                                                                            f.rules()
                                                                                                    .capacity())
                                                                                    + dev
                                                                                            .elementalcircuits
                                                                                            .text
                                                                                            .Texts
                                                                                            .get(
                                                                                                    "text.elementalcircuits.server._admin_commands.014")
                                                                                    + f.cells()
                                                                                            .size()
                                                                                    + dev
                                                                                            .elementalcircuits
                                                                                            .text
                                                                                            .Texts
                                                                                            .get(
                                                                                                    "text.elementalcircuits.server._admin_commands.015")
                                                                                    + Fixed.display(
                                                                                            s
                                                                                                    .ambientCreated())
                                                                                    + " / "
                                                                                    + Fixed.display(
                                                                                            s
                                                                                                    .injected())
                                                                                    + " / "
                                                                                    + Fixed.display(
                                                                                            s
                                                                                                    .extracted())
                                                                                    + " / "
                                                                                    + Fixed.display(
                                                                                            s
                                                                                                    .dissipated()));
                                                                    return 1;
                                                                }))));
        for (String op : List.of("set", "add"))
            field.then(
                    literal(op)
                            .then(
                                    argument("pos", BlockPosArgument.blockPos())
                                            .then(
                                                    argument("value", StringArgumentType.word())
                                                            .executes(
                                                                    c ->
                                                                            guarded(
                                                                                    () -> {
                                                                                        var level =
                                                                                                c.getSource()
                                                                                                        .getLevel();
                                                                                        var pos =
                                                                                                BlockPosArgument
                                                                                                        .getLoadedBlockPos(
                                                                                                                c,
                                                                                                                "pos");
                                                                                        var data =
                                                                                                WorldManaField
                                                                                                        .get(
                                                                                                                level);
                                                                                        var f =
                                                                                                data
                                                                                                        .field();
                                                                                        var cell =
                                                                                                ManaField
                                                                                                        .Cell
                                                                                                        .at(
                                                                                                                pos
                                                                                                                        .getX(),
                                                                                                                pos
                                                                                                                        .getY(),
                                                                                                                pos
                                                                                                                        .getZ());
                                                                                        if (!f.cells()
                                                                                                .contains(
                                                                                                        cell))
                                                                                            throw new IllegalArgumentException(
                                                                                                    dev
                                                                                                            .elementalcircuits
                                                                                                            .text
                                                                                                            .Texts
                                                                                                            .get(
                                                                                                                    "text.elementalcircuits.server._admin_commands.016"));
                                                                                        long
                                                                                                value =
                                                                                                        quantity(
                                                                                                                StringArgumentType
                                                                                                                        .getString(
                                                                                                                                c,
                                                                                                                                "value")),
                                                                                                before =
                                                                                                        f
                                                                                                                .amount(
                                                                                                                        cell),
                                                                                                after =
                                                                                                        op
                                                                                                                        .equals(
                                                                                                                                "add")
                                                                                                                ? Math
                                                                                                                        .addExact(
                                                                                                                                before,
                                                                                                                                value)
                                                                                                                : value;
                                                                                        if (after
                                                                                                        < 0
                                                                                                || after
                                                                                                        > f.rules()
                                                                                                                .capacity())
                                                                                            throw new IllegalArgumentException(
                                                                                                    dev
                                                                                                            .elementalcircuits
                                                                                                            .text
                                                                                                            .Texts
                                                                                                            .get(
                                                                                                                    "text.elementalcircuits.server._admin_commands.017"));
                                                                                        if (after
                                                                                                                > before
                                                                                                        && f
                                                                                                                        .inject(
                                                                                                                                cell,
                                                                                                                                after
                                                                                                                                        - before)
                                                                                                                != after
                                                                                                                        - before
                                                                                                || after
                                                                                                                < before
                                                                                                        && !f
                                                                                                                .debit(
                                                                                                                        cell,
                                                                                                                        before
                                                                                                                                - after))
                                                                                            throw new IllegalArgumentException(
                                                                                                    dev
                                                                                                            .elementalcircuits
                                                                                                            .text
                                                                                                            .Texts
                                                                                                            .get(
                                                                                                                    "text.elementalcircuits.server._admin_commands.018"));
                                                                                        data
                                                                                                .setDirty();
                                                                                        say(
                                                                                                c
                                                                                                        .getSource(),
                                                                                                dev
                                                                                                                .elementalcircuits
                                                                                                                .text
                                                                                                                .Texts
                                                                                                                .get(
                                                                                                                        "text.elementalcircuits.server._admin_commands.019")
                                                                                                        + " "
                                                                                                        + Fixed
                                                                                                                .display(
                                                                                                                        after)
                                                                                                        + dev
                                                                                                                .elementalcircuits
                                                                                                                .text
                                                                                                                .Texts
                                                                                                                .get(
                                                                                                                        "text.elementalcircuits.server._admin_commands.020"));
                                                                                        return 1;
                                                                                    })))));
        root.then(field);
        var node = dispatcher.register(root);
        dispatcher.register(literal("ec").requires(s -> s.hasPermission(2)).redirect(node));
    }

    private static int edit(
            CommandSourceStack source,
            Collection<ServerPlayer> players,
            String operation,
            String text) {
        long value =
                operation.equals("delay") ? new BigDecimal(text).intValueExact() : quantity(text);
        // Preflight the entire selector before changing anyone.
        for (var p : players) {
            var pool = PlayerMana.get(p);
            switch (operation) {
                case "set" -> {
                    Fixed.checked(value);
                    if (value > pool.capacity())
                        throw new IllegalArgumentException(
                                dev.elementalcircuits.text.Texts.get(
                                                "text.elementalcircuits.server._admin_commands.021")
                                        + p.getScoreboardName()
                                        + dev.elementalcircuits.text.Texts.get(
                                                "text.elementalcircuits.server._admin_commands.022"));
                }
                case "add" -> {
                    long next = Math.addExact(pool.available(), value);
                    if (next < 0 || next > pool.capacity())
                        throw new IllegalArgumentException(
                                dev.elementalcircuits.text.Texts.get(
                                                "text.elementalcircuits.server._admin_commands.023")
                                        + p.getScoreboardName()
                                        + dev.elementalcircuits.text.Texts.get(
                                                "text.elementalcircuits.server._admin_commands.022"));
                }
                case "capacity" -> {
                    Fixed.checked(value);
                    if (value == 0)
                        throw new IllegalArgumentException(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server._admin_commands.024"));
                }
                case "regen" -> Fixed.checked(value);
                case "delay" -> {
                    if (value < 0 || value > Integer.MAX_VALUE - 1)
                        throw new IllegalArgumentException(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server._admin_commands.025"));
                }
            }
        }
        for (var p : players) {
            var pool = PlayerMana.get(p);
            switch (operation) {
                case "set" -> pool.setAmount(value);
                case "add" -> pool.setAmount(pool.available() + value);
                case "capacity" -> pool.setCapacity(value);
                case "refill" -> pool.setAmount(pool.capacity());
                case "regen" -> PlayerMana.recovery(p, value, null, false);
                case "delay" -> PlayerMana.recovery(p, null, (int) value, false);
                case "resetRecovery" -> PlayerMana.recovery(p, null, null, true);
            }
            PlayerMana.sync(p);
        }
        say(
                source,
                dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server._admin_commands.026")
                        + players.size()
                        + dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server._admin_commands.027")
                        + operation
                        + " "
                        + text);
        return players.size();
    }
}
