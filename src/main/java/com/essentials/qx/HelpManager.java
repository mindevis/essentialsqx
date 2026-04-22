package com.essentials.qx;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.function.Consumer;

/**
 * Collects commands from all mods and provides data for QxHelpScreen.
 */
public final class HelpManager {

    private static final Gson GSON = new GsonBuilder().create();
    private static Consumer<String> clientOpenScreenCallback;
    private static PayloadSender payloadSender;

    /** Set by NeoForge to send qxhelp payload to player */
    public static void setPayloadSender(PayloadSender sender) {
        payloadSender = sender;
    }

    /** Send qxhelp payload to player. Called from /qxhelp command. */
    public static void sendToPlayer(ServerPlayer player, QxHelpPayload payload) {
        if (payloadSender != null) {
            payloadSender.sendToPlayer(player, payload);
        }
    }

    @FunctionalInterface
    public interface PayloadSender {
        void sendToPlayer(ServerPlayer player, QxHelpPayload payload);
    }

    public static final CustomPacketPayload.Type<QxHelpPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EssentialsQXMod.MOD_ID, "qxhelp"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QxHelpPayload> STREAM_CODEC =
        StreamCodec.of(HelpManager::write, HelpManager::read);

    /** Called from mod init. Networking is registered by NeoForge. */
    public static void init() {
    }

    public static void setClientOpenScreenCallback(Consumer<String> callback) {
        clientOpenScreenCallback = callback;
    }

    public static void onClientReceive(String json) {
        if (clientOpenScreenCallback != null) {
            clientOpenScreenCallback.accept(json);
        }
    }

    private static void write(RegistryFriendlyByteBuf buf, QxHelpPayload payload) {
        buf.writeUtf(payload.json(), 262144);
    }

    private static QxHelpPayload read(RegistryFriendlyByteBuf buf) {
        return new QxHelpPayload(buf.readUtf(262144));
    }

    private static final Map<String, String> VANILLA_COMMANDS_RU = new LinkedHashMap<>();
    static {
        VANILLA_COMMANDS_RU.put("advancement", "Управление достижениями");
        VANILLA_COMMANDS_RU.put("attribute", "Запрос и изменение атрибутов сущностей");
        VANILLA_COMMANDS_RU.put("ban", "Добавить игрока в бан-лист");
        VANILLA_COMMANDS_RU.put("ban-ip", "Забанить IP-адрес");
        VANILLA_COMMANDS_RU.put("banlist", "Показать бан-лист");
        VANILLA_COMMANDS_RU.put("bossbar", "Создание и изменение полос боссов");
        VANILLA_COMMANDS_RU.put("clear", "Очистить инвентарь игрока");
        VANILLA_COMMANDS_RU.put("clone", "Копирование блоков из одной области в другую");
        VANILLA_COMMANDS_RU.put("damage", "Нанести урон сущностям");
        VANILLA_COMMANDS_RU.put("data", "Работа с NBT-данными блоков и сущностей");
        VANILLA_COMMANDS_RU.put("datapack", "Управление загруженными датапаками");
        VANILLA_COMMANDS_RU.put("debug", "Запуск или остановка отладочной сессии");
        VANILLA_COMMANDS_RU.put("defaultgamemode", "Установить режим игры по умолчанию");
        VANILLA_COMMANDS_RU.put("deop", "Снять статус оператора с игрока");
        VANILLA_COMMANDS_RU.put("difficulty", "Установить уровень сложности");
        VANILLA_COMMANDS_RU.put("effect", "Добавить или снять эффекты");
        VANILLA_COMMANDS_RU.put("enchant", "Добавить зачарование на предмет");
        VANILLA_COMMANDS_RU.put("execute", "Выполнить другую команду");
        VANILLA_COMMANDS_RU.put("experience", "Добавить или убрать опыт");
        VANILLA_COMMANDS_RU.put("xp", "Добавить или убрать опыт");
        VANILLA_COMMANDS_RU.put("fill", "Заполнить область блоками");
        VANILLA_COMMANDS_RU.put("fillbiome", "Заполнить область биомом");
        VANILLA_COMMANDS_RU.put("forceload", "Принудительная загрузка чанков");
        VANILLA_COMMANDS_RU.put("function", "Выполнить функцию");
        VANILLA_COMMANDS_RU.put("gamemode", "Установить режим игры");
        VANILLA_COMMANDS_RU.put("gamerule", "Установить или запросить правило игры");
        VANILLA_COMMANDS_RU.put("give", "Выдать предмет игроку");
        VANILLA_COMMANDS_RU.put("help", "Справка по командам");
        VANILLA_COMMANDS_RU.put("item", "Управление предметами в инвентаре");
        VANILLA_COMMANDS_RU.put("jfr", "Профилирование JFR");
        VANILLA_COMMANDS_RU.put("kick", "Кикнуть игрока с сервера");
        VANILLA_COMMANDS_RU.put("kill", "Убить сущности");
        VANILLA_COMMANDS_RU.put("list", "Список игроков на сервере");
        VANILLA_COMMANDS_RU.put("locate", "Найти структуру, биом или достопримечательность");
        VANILLA_COMMANDS_RU.put("loot", "Выдать предметы из таблиц добычи");
        VANILLA_COMMANDS_RU.put("me", "Сообщение от имени игрока");
        VANILLA_COMMANDS_RU.put("msg", "Личное сообщение игроку");
        VANILLA_COMMANDS_RU.put("op", "Выдать статус оператора");
        VANILLA_COMMANDS_RU.put("pardon", "Разбанить игрока");
        VANILLA_COMMANDS_RU.put("pardon-ip", "Разбанить IP-адрес");
        VANILLA_COMMANDS_RU.put("particle", "Создать частицы");
        VANILLA_COMMANDS_RU.put("place", "Разместить структуру или feature");
        VANILLA_COMMANDS_RU.put("playsound", "Воспроизвести звук");
        VANILLA_COMMANDS_RU.put("publish", "Открыть мир для локальной сети");
        VANILLA_COMMANDS_RU.put("recipe", "Выдать или отозвать рецепты");
        VANILLA_COMMANDS_RU.put("reload", "Перезагрузить датапаки");
        VANILLA_COMMANDS_RU.put("ride", "Посадить сущность на другую");
        VANILLA_COMMANDS_RU.put("save-all", "Сохранить мир");
        VANILLA_COMMANDS_RU.put("save-off", "Отключить автосохранение");
        VANILLA_COMMANDS_RU.put("save-on", "Включить автосохранение");
        VANILLA_COMMANDS_RU.put("say", "Сообщение всем игрокам");
        VANILLA_COMMANDS_RU.put("schedule", "Отложить выполнение функции");
        VANILLA_COMMANDS_RU.put("scoreboard", "Управление таблицей счёта");
        VANILLA_COMMANDS_RU.put("seed", "Показать сид мира");
        VANILLA_COMMANDS_RU.put("setblock", "Установить блок");
        VANILLA_COMMANDS_RU.put("setworldspawn", "Установить точку спавна мира");
        VANILLA_COMMANDS_RU.put("spawnpoint", "Установить точку спавна игрока");
        VANILLA_COMMANDS_RU.put("spectate", "Переключить наблюдение за сущностью");
        VANILLA_COMMANDS_RU.put("spreadplayers", "Телепортировать сущности в случайные места");
        VANILLA_COMMANDS_RU.put("stop", "Остановить сервер");
        VANILLA_COMMANDS_RU.put("stopsound", "Остановить звук");
        VANILLA_COMMANDS_RU.put("summon", "Призвать сущность");
        VANILLA_COMMANDS_RU.put("tag", "Управление тегами сущностей");
        VANILLA_COMMANDS_RU.put("team", "Управление командами");
        VANILLA_COMMANDS_RU.put("teammsg", "Сообщение команде");
        VANILLA_COMMANDS_RU.put("teleport", "Телепортация");
        VANILLA_COMMANDS_RU.put("tp", "Телепортация");
        VANILLA_COMMANDS_RU.put("tell", "Личное сообщение игроку");
        VANILLA_COMMANDS_RU.put("tellraw", "Сообщение в формате JSON");
        VANILLA_COMMANDS_RU.put("time", "Управление игровым временем");
        VANILLA_COMMANDS_RU.put("title", "Заголовки на экране");
        VANILLA_COMMANDS_RU.put("trigger", "Триггер для таблицы счёта");
        VANILLA_COMMANDS_RU.put("weather", "Управление погодой");
        VANILLA_COMMANDS_RU.put("whitelist", "Управление белым списком");
        VANILLA_COMMANDS_RU.put("worldborder", "Управление границей мира");
    }

    /**
     * Build help data JSON from server commands.
     * Groups: Базовые команды (with RU descriptions), then mods (no descriptions).
     */
    public static String buildHelpJson(MinecraftServer server) {
        CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
        CommandSourceStack source = server.createCommandSourceStack();

        Map<String, List<CommandEntry>> byGroup = new LinkedHashMap<>();

        // 1. Базовые команды (vanilla) — с описаниями на русском
        List<CommandEntry> basicList = new ArrayList<>();
        for (CommandNode<CommandSourceStack> node : dispatcher.getRoot().getChildren()) {
            if (node instanceof LiteralCommandNode<CommandSourceStack> literal) {
                String root = literal.getLiteral().toLowerCase(Locale.ROOT);
                String desc = VANILLA_COMMANDS_RU.get(root);
                if (desc != null) {
                    collectBasicCommand(dispatcher, literal, source, basicList);
                }
            }
        }
        if (!basicList.isEmpty()) {
            byGroup.put("Базовые команды", basicList);
        }

        // 2. EssentialsQX — без описаний
        List<CommandEntry> eqxList = getEssentialsQXCommandsNoDesc();
        if (!eqxList.isEmpty()) byGroup.put("EssentialsQX", eqxList);

        // 3. Остальные моды — без описаний
        Set<String> essentialsqxRoots = Set.of("spawn", "setspawn", "resetspawn", "sethome", "home", "homes",
            "delhome", "renamehome", "rtp", "tpr", "tpa", "tpaccept", "tpcancel", "god", "fly", "qxhelp");
        Map<String, List<CommandEntry>> otherByMod = new LinkedHashMap<>();

        for (CommandNode<CommandSourceStack> node : dispatcher.getRoot().getChildren()) {
            if (node instanceof LiteralCommandNode<CommandSourceStack> literal) {
                String root = literal.getLiteral().toLowerCase(Locale.ROOT);
                if (essentialsqxRoots.contains(root) || VANILLA_COMMANDS_RU.containsKey(root)) continue;
                collectModCommand(dispatcher, literal, source, otherByMod);
            }
        }

        for (Map.Entry<String, List<CommandEntry>> e : otherByMod.entrySet()) {
            if (!e.getValue().isEmpty()) {
                byGroup.put(e.getKey(), e.getValue());
            }
        }

        return GSON.toJson(byGroup);
    }

    private static void collectBasicCommand(CommandDispatcher<CommandSourceStack> dispatcher,
            LiteralCommandNode<CommandSourceStack> node, CommandSourceStack source,
            List<CommandEntry> out) {
        String root = node.getLiteral().toLowerCase(Locale.ROOT);
        String desc = VANILLA_COMMANDS_RU.get(root);
        if (desc == null) return;
        try {
            String[] usageArr = dispatcher.getAllUsage(node, source, false);
            if (usageArr != null && usageArr.length > 0) {
                for (String u : usageArr) {
                    String fullCmd = u.startsWith("/") ? u : "/" + u.trim();
                    out.add(new CommandEntry(fullCmd, desc));
                }
            } else {
                out.add(new CommandEntry("/" + node.getLiteral(), desc));
            }
        } catch (Exception ignored) {
            out.add(new CommandEntry("/" + node.getLiteral(), desc));
        }
    }

    private static void collectModCommand(CommandDispatcher<CommandSourceStack> dispatcher,
            LiteralCommandNode<CommandSourceStack> node, CommandSourceStack source,
            Map<String, List<CommandEntry>> out) {
        String modName = inferModName(node.getLiteral());
        try {
            String[] usageArr = dispatcher.getAllUsage(node, source, false);
            if (usageArr != null && usageArr.length > 0) {
                for (String u : usageArr) {
                    String fullCmd = u.startsWith("/") ? u : "/" + u.trim();
                    out.computeIfAbsent(modName, k -> new ArrayList<>()).add(new CommandEntry(fullCmd, ""));
                }
            } else {
                out.computeIfAbsent(modName, k -> new ArrayList<>()).add(new CommandEntry("/" + node.getLiteral(), ""));
            }
        } catch (Exception ignored) {
            out.computeIfAbsent(modName, k -> new ArrayList<>()).add(new CommandEntry("/" + node.getLiteral(), ""));
        }
    }

    private static String inferModName(String rootCommand) {
        String lower = rootCommand.toLowerCase(Locale.ROOT);
        if (lower.startsWith("trove") || lower.equals("journal")) return "TroveQX";
        if (lower.startsWith("vein")) return "VeinMiningQX";
        if (lower.startsWith("falling") || lower.equals("tree")) return "FallingTreesQX";
        return "Other";
    }

    private static List<CommandEntry> getEssentialsQXCommandsNoDesc() {
        List<CommandEntry> list = new ArrayList<>();
        list.add(new CommandEntry("/spawn", ""));
        list.add(new CommandEntry("/setspawn", ""));
        list.add(new CommandEntry("/resetspawn", ""));
        list.add(new CommandEntry("/sethome <имя>", ""));
        list.add(new CommandEntry("/home <имя>", ""));
        list.add(new CommandEntry("/homes", ""));
        list.add(new CommandEntry("/delhome <имя>", ""));
        list.add(new CommandEntry("/renamehome <старое> <новое>", ""));
        list.add(new CommandEntry("/rtp", ""));
        list.add(new CommandEntry("/tpr", ""));
        list.add(new CommandEntry("/rtp status", ""));
        list.add(new CommandEntry("/rtp create [кол-во]", ""));
        list.add(new CommandEntry("/rtp reset", ""));
        list.add(new CommandEntry("/tpr reset", ""));
        list.add(new CommandEntry("/tpa <игрок>", ""));
        list.add(new CommandEntry("/tpaccept", ""));
        list.add(new CommandEntry("/tpcancel", ""));
        list.add(new CommandEntry("/god", ""));
        list.add(new CommandEntry("/fly", ""));
        list.add(new CommandEntry("/qxhelp", ""));
        return list;
    }

    public record CommandEntry(String command, String description) {}
    public record QxHelpPayload(String json) implements CustomPacketPayload {
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
