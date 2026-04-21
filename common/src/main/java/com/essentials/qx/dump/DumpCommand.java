package com.essentials.qx.dump;

import com.essentials.qx.ConfigManager;
import com.essentials.qx.EssentialsQXMod;
import com.mojang.brigadier.context.CommandContext;
import dev.architectury.platform.Platform;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * /qxdump minecraft | /qxdump &lt;modid&gt; | /qxdump list — CSV dumps under world/essentialsqx/dumps/
 */
public final class DumpCommand {
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private DumpCommand() {}

    private static Component msg(String text) {
        return Component.literal("[EssentialsQX] " + text);
    }

    public static int executeList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ConfigManager cfg = EssentialsQXMod.getConfig();
        if (!cfg.dumpEnabled) {
            source.sendFailure(msg("Команда отключена в конфиге."));
            return 0;
        }
        if (!checkPermission(source, cfg)) {
            source.sendFailure(msg("У вас нет прав"));
            return 0;
        }
        MinecraftServer server = source.getServer();
        server.execute(() -> {
            Map<String, Integer> modStats = new HashMap<>();
            int totalItems = 0;
            for (Item item : BuiltInRegistries.ITEM) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                if (id == null) {
                    continue;
                }
                String ns = id.getNamespace();
                modStats.merge(ns, 1, Integer::sum);
                totalItems++;
            }
            TreeMap<String, Integer> sorted = new TreeMap<>(modStats);
            source.sendSuccess(() -> msg("=== Моды с предметами ==="), false);
            for (Map.Entry<String, Integer> e : sorted.entrySet()) {
                String line = "- " + e.getKey() + " - " + e.getValue() + " предметов";
                source.sendSuccess(() -> msg(line), false);
            }
            String summary = "Всего: " + sorted.size() + " модов, " + totalItems + " предметов";
            source.sendSuccess(() -> msg(summary), false);
        });
        return 1;
    }

    public static int executeMinecraft(CommandContext<CommandSourceStack> ctx) {
        return executeDump(ctx, "minecraft", true);
    }

    public static int executeMod(CommandContext<CommandSourceStack> ctx, String modid) {
        modid = modid.toLowerCase(Locale.ROOT).trim();
        return executeDump(ctx, modid, false);
    }

    private static int executeDump(CommandContext<CommandSourceStack> ctx, String modid, boolean vanillaOnly) {
        CommandSourceStack source = ctx.getSource();
        ConfigManager cfg = EssentialsQXMod.getConfig();
        if (!cfg.dumpEnabled) {
            source.sendFailure(msg("Команда отключена в конфиге."));
            return 0;
        }
        if (!checkPermission(source, cfg)) {
            source.sendFailure(msg("У вас нет прав"));
            return 0;
        }
        MinecraftServer server = source.getServer();
        server.execute(() -> {
            if (!vanillaOnly && !Platform.isModLoaded(modid)) {
                source.sendFailure(msg("Мод '" + modid + "' не найден"));
                return;
            }
            List<Row> rows = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                if (id == null) {
                    continue;
                }
                if (vanillaOnly) {
                    if (!id.getNamespace().equals("minecraft")) {
                        continue;
                    }
                } else {
                    if (!id.getNamespace().equals(modid)) {
                        continue;
                    }
                }
                String name = localizedName(item);
                rows.add(new Row(id.toString(), name));
            }
            if (rows.isEmpty()) {
                source.sendFailure(msg("В моде '" + modid + "' нет предметов"));
                return;
            }
            int max = Math.max(1, cfg.dumpMaxItems);
            boolean truncated = rows.size() > max;
            List<Row> out = truncated ? rows.subList(0, max) : rows;
            int count = out.size();
            Path dumpDir;
            try {
                dumpDir = server.getWorldPath(LevelResource.ROOT).resolve("essentialsqx").resolve("dumps");
                Files.createDirectories(dumpDir);
            } catch (IOException e) {
                EssentialsQXMod.LOGGER.error("qxdump: create dirs", e);
                source.sendFailure(msg("Не удалось создать папку"));
                return;
            }
            String ts = LocalDateTime.now().format(FILE_TS);
            String fileName = "qxdump_" + safeFileSegment(modid) + "_" + ts + ".csv";
            Path file = dumpDir.resolve(fileName);
            try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                w.write("id,name");
                w.newLine();
                for (Row r : out) {
                    w.write(csvEscapeId(r.id));
                    w.write(',');
                    w.write(csvEscapeName(r.name));
                    w.newLine();
                }
            } catch (IOException e) {
                EssentialsQXMod.LOGGER.error("qxdump: write file", e);
                source.sendFailure(msg("Ошибка сохранения файла"));
                return;
            }
            String rel = "world/essentialsqx/dumps/" + fileName;
            if (vanillaOnly) {
                source.sendSuccess(
                    () -> msg("Сбор ванильных предметов... (" + count + " предметов)"),
                    false
                );
            } else {
                source.sendSuccess(
                    () -> msg("Сбор предметов мода '" + modid + "'... (" + count + " предметов)"),
                    false
                );
            }
            if (truncated) {
                source.sendSuccess(
                    () -> msg("Ограничено конфигом dumpMaxItems=" + max + " из " + rows.size()),
                    false
                );
            }
            source.sendSuccess(
                () -> msg("Файл сохранён: " + rel),
                false
            );
        });
        return 1;
    }

    private static boolean checkPermission(CommandSourceStack source, ConfigManager cfg) {
        if (!cfg.dumpAllowConsole && source.getEntity() == null) {
            return false;
        }
        return !cfg.dumpRequireOp || source.hasPermission(2);
    }

    private static String localizedName(Item item) {
        try {
            return new ItemStack(item).getHoverName().getString();
        } catch (Exception e) {
            return item.toString();
        }
    }

    private static String safeFileSegment(String modid) {
        return modid.replaceAll("[^a-z0-9_.-]", "_");
    }

    private static String csvEscapeId(String id) {
        if (id.indexOf(',') >= 0 || id.indexOf('"') >= 0 || id.indexOf('\n') >= 0 || id.indexOf('\r') >= 0) {
            return '"' + id.replace("\"", "\"\"") + '"';
        }
        return id;
    }

    private static String csvEscapeName(String name) {
        if (name == null) {
            return "";
        }
        if (name.indexOf(',') >= 0 || name.indexOf('"') >= 0 || name.indexOf('\n') >= 0 || name.indexOf('\r') >= 0) {
            return '"' + name.replace("\"", "\"\"") + '"';
        }
        return name;
    }

    private record Row(String id, String name) {}
}
