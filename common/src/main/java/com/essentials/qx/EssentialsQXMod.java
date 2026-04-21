package com.essentials.qx;

import com.essentials.qx.dump.DumpCommand;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Main EssentialsQX mod class
 */
public final class EssentialsQXMod {
    public static final String MOD_ID = "essentialsqx";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ConfigManager CONFIG;
    private static Path CONFIG_DIR;

    /**
     * Initialize mod. Call from NeoForge entrypoint.
     * @param configDir Path to config directory (e.g. FMLPaths.CONFIGDIR.get())
     */
    public static void init(Path configDir) {
        CONFIG_DIR = configDir;
        // Load configuration
        loadConfig();

        // Initialize managers
        SpawnManager.init(null); // Server instance will be passed during server start
        HomeManager.init();
        RTPManager.init(null); // Server instance will be passed during server start
        TPAManager.init(null); // Server instance will be passed during server start
        PlayerStatesManager.init(null); // Server instance will be passed during server start
        TimeManager.init();
        HelpManager.init();

        LOGGER.info("EssentialsQX initialized");
    }

    /** Called when server starts - initialize managers with world data */
    public static void onServerStarted(net.minecraft.server.MinecraftServer server) {
        SpawnManager.init(server);
        RTPManager.init(server);
        TPAManager.init(server);
        PlayerStatesManager.init(server);

        if (!CONFIG.debug) {
            return;
        }

        LOGGER.info("=== SERVER WORLD INFORMATION ===");

        // Get information about the save directory
        try {
            var worldPath = server.getWorldPath(LevelResource.ROOT);
            LOGGER.info("Server world folder: {}", worldPath.normalize());
        } catch (Exception e) {
            LOGGER.warn("Failed to get server world folder path: {}", e.getMessage());
        }

        // Get information about all worlds
        var levelKeys = server.levelKeys();
        LOGGER.info("Number of dimensions: {}", levelKeys.size());

        for (var levelKey : levelKeys) {
            var level = server.getLevel(levelKey);
            if (level != null) {
                LOGGER.info("Dimension: {}", levelKey.location());

                try {
                    var dimensionPath = server.getWorldPath(LevelResource.ROOT).resolve(levelKey.location().getPath());
                    LOGGER.info("  Dimension folder: {}", dimensionPath.normalize());
                } catch (Exception e) {
                    LOGGER.warn("Failed to get dimension path for {}: {}", levelKey.location(), e.getMessage());
                }
            }
        }

        try {
            var overworld = server.overworld();
            if (overworld != null) {
                showWorldBorderInfo(server, overworld);
            }
        } catch (Exception e) {
            LOGGER.debug("Could not access overworld for world border info: {}", e.getMessage());
        }

        LOGGER.info("===================================");
    }

    /** Called when server stops - save all data */
    public static void onServerStopping(net.minecraft.server.MinecraftServer server) {
        HomeManager.saveAllHomes(server);
        RTPManager.saveAllRTPData(server);
        PlayerStatesManager.saveAllStates(server);
        TimeManager.clear();
        LOGGER.info("Server stopping - saved all player data");
    }

    /** Called when player joins */
    public static void onPlayerJoin(net.minecraft.server.level.ServerPlayer player) {
        PlayerStatesManager.applySavedStates(player);
        LOGGER.debug("Player {} joined - applied saved states", player.getName().getString());
    }

    /** Called when player quits */
    public static void onPlayerQuit(net.minecraft.server.level.ServerPlayer player) {
        HomeManager.savePlayerHomes(player.getUUID(), player.getServer());
        RTPManager.savePlayerUsedRTPs(player.getUUID(), player.getServer());
        PlayerStatesManager.savePlayerState(player.getUUID(), player.getServer());
        LOGGER.debug("Player {} disconnected - saved their home, RTP and state data", player.getName().getString());
    }

    /** Called every server tick */
    public static void onServerTick(net.minecraft.server.MinecraftServer server) {
        TeleportManager.onServerTick(server);
        TPAManager.processExpiredRequests();
    }

    public static ConfigManager getConfig() {
        return CONFIG;
    }

    private static void loadConfig() {
        try {
            Path configFile = CONFIG_DIR.resolve("essentialsqx.json");

            if (Files.exists(configFile)) {
                // Load existing configuration
                String json = Files.readString(configFile);
                CONFIG = GSON.fromJson(json, ConfigManager.class);
                LOGGER.info("Configuration loaded from: {}", configFile);
                // Validate and update configuration with missing defaults
                validateAndUpdateConfig();
            } else {
                // Create default configuration
                CONFIG = new ConfigManager();
                saveConfig();
                LOGGER.info("Default configuration created: {}", configFile);
            }
        } catch (Exception e) {
            LOGGER.error("Error loading configuration, using default configuration: {}", e.getMessage());
            CONFIG = new ConfigManager();
        }
    }

    private static void saveConfig() {
        try {
            Files.createDirectories(CONFIG_DIR);
            Path configFile = CONFIG_DIR.resolve("essentialsqx.json");

            String json = GSON.toJson(CONFIG);
            Files.writeString(configFile, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            LOGGER.info("Configuration saved to: {}", configFile);
        } catch (IOException e) {
            LOGGER.error("Error saving configuration: {}", e.getMessage());
        }
    }

    /**
     * Validates configuration and adds missing keys with default values
     * Since GSON doesn't touch fields not present in JSON, we need to ensure
     * all fields have proper default values by re-serializing the config
     */
    private static void validateAndUpdateConfig() {
        // Always ensure the configuration has all default values
        // by re-creating it with defaults and merging with loaded values
        ConfigManager defaultConfig = new ConfigManager();

        // Check if any new fields are missing by comparing with defaults
        // For new installations or when new config fields are added

        // For simplicity, we'll always save the full configuration to ensure
        // all fields are present in the JSON file
        String currentJson = GSON.toJson(CONFIG);
        String defaultJson = GSON.toJson(defaultConfig);

        // If the current config doesn't have all the fields that default has,
        // we need to update it
        if (!currentJson.equals(defaultJson)) {
            // Create a merged configuration
            ConfigManager mergedConfig = new ConfigManager();

            // Copy all loaded values to merged config
            mergedConfig.debug = CONFIG.debug;
            mergedConfig.teleportDelaySeconds = CONFIG.teleportDelaySeconds;
            mergedConfig.allowMovementDuringTeleport = CONFIG.allowMovementDuringTeleport;
            mergedConfig.allowDamageDuringTeleport = CONFIG.allowDamageDuringTeleport;
            mergedConfig.teleportCooldownSeconds = CONFIG.teleportCooldownSeconds;
            mergedConfig.allowOperatorsBypassTeleportDelay = CONFIG.allowOperatorsBypassTeleportDelay;
            mergedConfig.allowOperatorsBypassTeleportCooldown = CONFIG.allowOperatorsBypassTeleportCooldown;
            mergedConfig.language = CONFIG.language;
            mergedConfig.maxHomesPerPlayer = CONFIG.maxHomesPerPlayer;
            mergedConfig.allowedHomeDimensions = CONFIG.allowedHomeDimensions != null ?
                CONFIG.allowedHomeDimensions : defaultConfig.allowedHomeDimensions;
            mergedConfig.allowOperatorsBypassHomeLimit = CONFIG.allowOperatorsBypassHomeLimit;
            mergedConfig.rtpPointsCount = CONFIG.rtpPointsCount;
            mergedConfig.rtpAllowedDimensions = CONFIG.rtpAllowedDimensions != null ?
                CONFIG.rtpAllowedDimensions : defaultConfig.rtpAllowedDimensions;
            mergedConfig.tpaRequestTimeoutSeconds = CONFIG.tpaRequestTimeoutSeconds;
            mergedConfig.dayDurationTicks = CONFIG.dayDurationTicks > 0 ? CONFIG.dayDurationTicks : defaultConfig.dayDurationTicks;
            mergedConfig.nightDurationTicks = CONFIG.nightDurationTicks > 0 ? CONFIG.nightDurationTicks : defaultConfig.nightDurationTicks;
            mergedConfig.dumpEnabled = CONFIG.dumpEnabled;
            mergedConfig.dumpRequireOp = CONFIG.dumpRequireOp;
            mergedConfig.dumpAllowConsole = CONFIG.dumpAllowConsole;
            mergedConfig.dumpMaxItems = CONFIG.dumpMaxItems > 0 ? CONFIG.dumpMaxItems : defaultConfig.dumpMaxItems;

            CONFIG = mergedConfig;
            saveConfig();
            LOGGER.info("Configuration updated with missing default values");
        }
    }

    /**
     * Shows world border information for the overworld.
     */
    private static void showWorldBorderInfo(MinecraftServer server, net.minecraft.server.level.ServerLevel overworld) {
        try {
            var worldBorder = overworld.getWorldBorder();
            int maxWorldSize = -1;

            // Try to access max-world-size (server properties)
            try {
                // Get server properties using reflection (this is necessary)
                var propertiesMethod = server.getClass().getMethod("getProperties");
                var properties = propertiesMethod.invoke(server);
                if (properties != null) {
                    // Try to get maxWorldSize from properties object
                    var maxWorldSizeField = properties.getClass().getField("maxWorldSize");
                    maxWorldSize = (Integer) maxWorldSizeField.get(properties);
                    LOGGER.info("Successfully read max-world-size: {} from server properties", maxWorldSize);
                }
            } catch (Exception e) {
                LOGGER.debug("Could not access server properties: {}", e.getMessage());

                // Fallback: try reflection on server object directly for maxWorldSize
                try {
                    var maxWorldSizeField = server.getClass().getDeclaredField("maxWorldSize");
                    maxWorldSizeField.setAccessible(true);
                    maxWorldSize = (Integer) maxWorldSizeField.get(server);
                    LOGGER.info("Successfully read max-world-size: {} from server object (fallback)", maxWorldSize);
                } catch (Exception fallbackE) {
                    LOGGER.warn("Could not read max-world-size from server config or properties: {}", fallbackE.getMessage());
                }
            }

            LOGGER.info("World Border Information:");
            LOGGER.info("  Center: X={}, Z={}", worldBorder.getCenterX(), worldBorder.getCenterZ());
            if (maxWorldSize > 0) {
                LOGGER.info("  Current Max World Size: {} blocks radius", maxWorldSize);
            } else {
                LOGGER.info("  Current Max World Size: Information not available");
            }
            LOGGER.info("  Current Border Size: {} blocks diameter", Math.round(worldBorder.getSize()));
            LOGGER.info("  Border Status: Active and configured");

        } catch (Exception e) {
            LOGGER.debug("Could not get world border information: {}", e.getMessage());
        }
    }

    /** Brigadier argument helper - avoids Commands.argument() API differences across loader versions */
    private static <T> RequiredArgumentBuilder<CommandSourceStack, T> arg(String name, ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    /** Register commands - call from RegisterCommandsEvent */
    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
        // /spawn command
        dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("spawn")
            .executes(EssentialsQXMod::executeSpawnCommand));

        // /setspawn command (operator only)
        dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("setspawn")
            .requires(source -> source.hasPermission(2)) // Operator permission level 2
            .executes(EssentialsQXMod::executeSetSpawnCommand));

        // /resetspawn command (operator only)
        dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("resetspawn")
            .requires(source -> source.hasPermission(2)) // Operator permission level 2
            .executes(EssentialsQXMod::executeResetSpawnCommand));

        // Home system commands - single tree per command
        dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("sethome")
            .then(arg("name", StringArgumentType.word())
                .executes(ctx -> executeSetHomeCommand(ctx, ctx.getArgument("name", String.class)))));

        dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("home")
            .then(arg("name", StringArgumentType.word())
                .executes(ctx -> executeHomeCommand(ctx, ctx.getArgument("name", String.class)))
                .then(arg("player", StringArgumentType.word())
                    .requires(source -> source.hasPermission(2))
                    .executes(ctx -> executeAdminHomeCommand(ctx,
                        ctx.getArgument("player", String.class),
                        ctx.getArgument("name", String.class))))));

        dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("homes")
            .executes(EssentialsQXMod::executeHomesCommand)
            .then(arg("player", StringArgumentType.word())
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> executeAdminHomesCommand(ctx, ctx.getArgument("player", String.class)))));

        dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("delhome")
            .then(arg("name", StringArgumentType.word())
                .executes(ctx -> executeDelHomeCommand(ctx, ctx.getArgument("name", String.class)))
                .then(arg("player", StringArgumentType.word())
                    .requires(source -> source.hasPermission(2))
                    .executes(ctx -> executeAdminDelHomeCommand(ctx,
                        ctx.getArgument("player", String.class),
                        ctx.getArgument("name", String.class))))));

        dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("renamehome")
            .then(arg("oldName", StringArgumentType.word())
                .then(arg("newName", StringArgumentType.word())
                    .executes(ctx -> executeRenameHomeCommand(ctx,
                        ctx.getArgument("oldName", String.class),
                        ctx.getArgument("newName", String.class))))));

        // RTP - single tree (status, create, reset)
        dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("rtp")
            .executes(EssentialsQXMod::executeRTPCommand)
            .then(LiteralArgumentBuilder.<CommandSourceStack>literal("status")
                .executes(EssentialsQXMod::executeRTPStatusCommand))
            .then(LiteralArgumentBuilder.<CommandSourceStack>literal("create")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> executeRTPUpdateCommand(ctx, EssentialsQXMod.getConfig().rtpPointsCount))
                .then(arg("count", IntegerArgumentType.integer(1))
                    .executes(ctx -> executeRTPUpdateCommand(ctx, IntegerArgumentType.getInteger(ctx, "count")))))
            .then(LiteralArgumentBuilder.<CommandSourceStack>literal("reset")
                .requires(source -> source.hasPermission(2))
                .executes(EssentialsQXMod::executeRTPResetCommand)));

        dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("tpr")
            .executes(EssentialsQXMod::executeRTPCommand)
            .then(LiteralArgumentBuilder.<CommandSourceStack>literal("status")
                .executes(EssentialsQXMod::executeRTPStatusCommand))
            .then(LiteralArgumentBuilder.<CommandSourceStack>literal("create")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> executeRTPUpdateCommand(ctx, EssentialsQXMod.getConfig().rtpPointsCount))
                .then(arg("count", IntegerArgumentType.integer(1))
                    .executes(ctx -> executeRTPUpdateCommand(ctx, IntegerArgumentType.getInteger(ctx, "count")))))
            .then(LiteralArgumentBuilder.<CommandSourceStack>literal("reset")
                .requires(source -> source.hasPermission(2))
                .executes(EssentialsQXMod::executeRTPResetCommand)));

        // TPA: StringArgumentType + getPlayerByName (compatible across loaders, avoids EntityArgument differences)
        dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("tpa")
            .then(arg("player", StringArgumentType.word())
                .executes(ctx -> {
                    String name = ctx.getArgument("player", String.class);
                    net.minecraft.server.level.ServerPlayer target = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
                    if (target != null) {
                        return executeTPACommand(ctx, target);
                    }
                    ctx.getSource().sendFailure(Component.literal("Player not found: " + name));
                    return 0;
                })));

        dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("tpaccept")
            .executes(EssentialsQXMod::executeTPAcceptCommand));

        dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("tpcancel")
            .executes(EssentialsQXMod::executeTPCancelCommand));

        dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("qxhelp")
            .executes(EssentialsQXMod::executeQxHelpCommand));

        dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("god")
            .requires(source -> source.hasPermission(2))
            .executes(EssentialsQXMod::executeGodCommand));

        dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("fly")
            .requires(source -> source.hasPermission(2))
            .executes(EssentialsQXMod::executeFlyCommand));

        dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("qxdump")
            .then(LiteralArgumentBuilder.<CommandSourceStack>literal("list")
                .executes(DumpCommand::executeList))
            .then(LiteralArgumentBuilder.<CommandSourceStack>literal("minecraft")
                .executes(DumpCommand::executeMinecraft))
            .then(arg("modid", StringArgumentType.word())
                .executes(ctx -> DumpCommand.executeMod(ctx, StringArgumentType.getString(ctx, "modid")))));
    }

    private static int executeSpawnCommand(CommandContext<CommandSourceStack> context) {
        net.minecraft.commands.CommandSourceStack source = context.getSource();
        net.minecraft.server.level.ServerPlayer player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal(LocalizationManager.getMessage("command.only_players")));
            return 0;
        }

        SpawnManager.SpawnData spawnData = SpawnManager.getSpawnData();
        if (spawnData == null || !spawnData.isValid()) {
            source.sendFailure(Component.literal(LocalizationManager.getMessage("spawn.not_set")));
            return 0;
        }

        // Check if player already has an active teleport
        if (TeleportManager.hasActiveTeleport(player.getUUID())) {
            source.sendFailure(Component.literal(LocalizationManager.getMessage("teleport.already_active")));
            return 0;
        }

        try {
            // Get the target dimension
            var targetDimension = spawnData.getDimensionKey();
            var targetLevel = source.getServer().getLevel(targetDimension);
            if (targetLevel == null) {
                source.sendFailure(Component.literal(LocalizationManager.getMessage("teleport.dimension_unavailable")));
                return 0;
            }

            // Find safe spawn location (adjust if needed)
            var originalSpawnPos = new net.minecraft.core.BlockPos((int) spawnData.x, (int) spawnData.y, (int) spawnData.z);
            var safeSpawnPos = SafetyManager.findSafeTeleportLocation(targetLevel, originalSpawnPos);

            if (safeSpawnPos == null) {
                source.sendFailure(Component.literal(LocalizationManager.getMessage("teleport.no_safe_location")));
                LOGGER.error("Failed to find safe spawn location for player {} near {}", player.getName().getString(), originalSpawnPos);
                return 0;
            }

            // Check if we adjusted the spawn position
            boolean positionAdjusted = !safeSpawnPos.equals(originalSpawnPos);

            // Create teleport action
            Runnable teleportAction = () -> {
                try {
                    // Check if player is still online
                    if (player.isRemoved()) {
                        return;
                    }

                    // Double-check safety at teleport time
                    var currentSafePos = SafetyManager.findSafeTeleportLocation(targetLevel, safeSpawnPos);
                    if (currentSafePos != null) {
                        var currentFinalPos = new net.minecraft.world.phys.Vec3(currentSafePos.getX() + 0.5, currentSafePos.getY(), currentSafePos.getZ() + 0.5);
                        player.teleportTo(targetLevel, currentFinalPos.x, currentFinalPos.y, currentFinalPos.z, spawnData.yaw, spawnData.pitch);

                        if (positionAdjusted) {
                            LOGGER.info("Player {} teleported to adjusted spawn position at {},{},{} (original was unsafe) in {}",
                                player.getName().getString(), currentFinalPos.x, currentFinalPos.y, currentFinalPos.z, targetDimension.location());
                        } else {
                            LOGGER.info("Player {} teleported to spawn point at {},{},{} in {}",
                                player.getName().getString(), currentFinalPos.x, currentFinalPos.y, currentFinalPos.z, targetDimension.location());
                        }
                    } else {
                        player.sendSystemMessage(Component.literal("§cTELEPORT_DESTINATION_UNSAFE_PLACEHOLDER"));
                        LOGGER.warn("Teleport destination became unsafe for player {}", player.getName().getString());
                    }
                } catch (Exception e) {
                    player.sendSystemMessage(Component.literal("§cTELEPORT_FAILED_PLACEHOLDER"));
                    LOGGER.error("Error during teleport completion for player {}: {}", player.getName().getString(), e.getMessage());
                }
            };

            // Start teleport countdown
            TeleportManager.startTeleportCountdown(player, teleportAction);

        } catch (Exception e) {
            source.sendFailure(Component.literal(LocalizationManager.getMessage("teleport.start_failed")));
            LOGGER.error("Error starting teleport countdown for player {}: {}", player.getName().getString(), e.getMessage());
        }

        return 1;
    }

    private static int executeSetSpawnCommand(CommandContext<CommandSourceStack> context) {
        net.minecraft.commands.CommandSourceStack source = context.getSource();
        net.minecraft.server.level.ServerPlayer player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal(LocalizationManager.getMessage("command.only_players")));
            return 0;
        }

        try {
            // Validate spawn location using managers
            if (!SpawnManager.isValidSpawnLocation(player)) {
                return 0; // Error messages are sent by the validation methods
            }

            // Get player's current position and rotation
            var pos = player.position();
            float yaw = player.getYRot();
            float pitch = player.getXRot();

            // Create new spawn data
            var newSpawnData = new SpawnManager.SpawnData();
            newSpawnData.x = pos.x;
            newSpawnData.y = pos.y;
            newSpawnData.z = pos.z;
            newSpawnData.yaw = yaw;
            newSpawnData.pitch = pitch;
            newSpawnData.dimension = player.level().dimension().location().toString();

            // Update and save spawn data
            SpawnManager.setSpawnData(newSpawnData, source.getServer());

            source.sendSuccess(() -> Component.literal(LocalizationManager.getMessage("spawn.set_success")), true);
            LOGGER.info("Player {} set new spawn point at {}, {}, {} in {} (yaw: {}, pitch: {})",
                player.getName().getString(), pos.x, pos.y, pos.z,
                newSpawnData.dimension, yaw, pitch);

        } catch (Exception e) {
            source.sendFailure(Component.literal(LocalizationManager.getMessage("spawn.set_failed")));
            LOGGER.error("Error setting spawn point for player {}: {}", player.getName().getString(), e.getMessage());
        }

        return 1;
    }

    private static int executeResetSpawnCommand(CommandContext<CommandSourceStack> context) {
        net.minecraft.commands.CommandSourceStack source = context.getSource();

        try {
            // Reset spawn data to defaults
            var defaultSpawnData = new SpawnManager.SpawnData();
            SpawnManager.setSpawnData(defaultSpawnData, source.getServer());

            source.sendSuccess(() -> Component.literal(LocalizationManager.getMessage("spawn.reset_success")), true);
            LOGGER.info("Spawn point reset to default by player");

        } catch (Exception e) {
            source.sendFailure(Component.literal(LocalizationManager.getMessage("spawn.reset_failed")));
            LOGGER.error("Error resetting spawn point: {}", e.getMessage());
        }

        return 1;
    }

    // Home system commands implementation

    private static int executeSetHomeCommand(CommandContext<CommandSourceStack> context, String homeName) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal(LocalizationManager.getMessage("command.only_players")));
            return 0;
        }

        HomeManager.setHome(player, homeName);
        return 1;
    }

    private static int executeHomeCommand(CommandContext<CommandSourceStack> context, String homeName) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal(LocalizationManager.getMessage("command.only_players")));
            return 0;
        }

        HomeManager.teleportToHome(player, homeName);
        return 1;
    }

    private static int executeHomesCommand(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal(LocalizationManager.getMessage("command.only_players")));
            return 0;
        }

        HomeManager.listHomes(player);
        return 1;
    }

    private static int executeDelHomeCommand(CommandContext<CommandSourceStack> context, String homeName) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal(LocalizationManager.getMessage("command.only_players")));
            return 0;
        }

        // For now, implement without confirmation as requested
        // In a real implementation, you might want to add confirmation
        HomeManager.deleteHome(player, homeName);
        return 1;
    }

    private static int executeRenameHomeCommand(CommandContext<CommandSourceStack> context, String oldName, String newName) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal(LocalizationManager.getMessage("command.only_players")));
            return 0;
        }

        HomeManager.renameHome(player, oldName, newName);
        return 1;
    }

    // Admin commands

    private static int executeAdminHomesCommand(CommandContext<CommandSourceStack> context, String targetPlayerName) {
        CommandSourceStack source = context.getSource();
        ServerPlayer admin = source.getPlayer();

        if (admin == null) {
            source.sendFailure(Component.literal(LocalizationManager.getMessage("command.only_players")));
            return 0;
        }

        HomeManager.listPlayerHomes(admin, targetPlayerName);
        return 1;
    }

    private static int executeAdminDelHomeCommand(CommandContext<CommandSourceStack> context, String targetPlayerName, String homeName) {
        CommandSourceStack source = context.getSource();
        ServerPlayer admin = source.getPlayer();

        if (admin == null) {
            source.sendFailure(Component.literal(LocalizationManager.getMessage("command.only_players")));
            return 0;
        }

        // For now, implement without confirmation as requested
        HomeManager.deletePlayerHome(admin, targetPlayerName, homeName);
        return 1;
    }

    private static int executeAdminHomeCommand(CommandContext<CommandSourceStack> context, String targetPlayerName, String homeName) {
        CommandSourceStack source = context.getSource();
        ServerPlayer admin = source.getPlayer();

        if (admin == null) {
            source.sendFailure(Component.literal(LocalizationManager.getMessage("command.only_players")));
            return 0;
        }

        HomeManager.teleportToPlayerHome(admin, targetPlayerName, homeName);
        return 1;
    }

    // QxHelp command implementation

    private static int executeQxHelpCommand(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal(LocalizationManager.getMessage("command.only_players")));
            return 0;
        }

        try {
            String json = HelpManager.buildHelpJson(player.getServer());
            HelpManager.sendToPlayer(player, new HelpManager.QxHelpPayload(json));
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c" + (e.getMessage() != null ? e.getMessage() : "Error")));
            LOGGER.error("Failed to build help data", e);
            return 0;
        }

        return 1;
    }

    // RTP commands implementation

    private static int executeRTPStatusCommand(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int count = RTPManager.getRTPPointsCount();
        source.sendSuccess(() -> Component.literal(
            LocalizationManager.getMessage("rtp.status", count)
        ), false);
        return 1;
    }

    private static int executeRTPCommand(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal(LocalizationManager.getMessage("command.only_players")));
            return 0;
        }

        RTPManager.randomTeleport(player);
        return 1;
    }

    private static int executeRTPUpdateCommand(CommandContext<CommandSourceStack> context, int count) {
        CommandSourceStack source = context.getSource();
        ServerPlayer admin = source.getPlayer();

        if (admin == null) {
            source.sendFailure(Component.literal(LocalizationManager.getMessage("command.only_players")));
            return 0;
        }

        // Notify admin and start creation
        admin.sendSystemMessage(Component.literal(LocalizationManager.getMessage("rtp.create_confirm")));

        // Run creation asynchronously to avoid blocking
        new Thread(() -> {
            try {
                RTPManager.updateRTPPoints(admin.getServer(), count);
            } catch (Exception e) {
                admin.sendSystemMessage(Component.literal("§c" + LocalizationManager.getMessage("rtp.create_failed") + ": " + e.getMessage()));
                LOGGER.error("RTP update failed", e);
            }
        }, "RTP-Update-Thread").start();

        return 1;
    }

    private static int executeRTPResetCommand(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        try {
            RTPManager.resetAllRTPData(source.getServer());
            source.sendSuccess(() -> Component.literal(
                LocalizationManager.getMessage("rtp.reset_success")
            ), true);
            LOGGER.info("RTP data reset by operator");
        } catch (Exception e) {
            source.sendFailure(Component.literal(
                LocalizationManager.getMessage("rtp.reset_failed")
            ));
            LOGGER.error("Failed to reset RTP data", e);
            return 0;
        }

        return 1;
    }

    // TPA commands implementation

    private static int executeTPACommand(CommandContext<CommandSourceStack> context,
                                        ServerPlayer target) {
        CommandSourceStack source = context.getSource();
        ServerPlayer requester = source.getPlayer();

        if (requester == null) {
            source.sendFailure(Component.literal(LocalizationManager.getMessage("command.only_players")));
            return 0;
        }

        if (requester.equals(target)) {
            requester.sendSystemMessage(Component.literal(
                LocalizationManager.getMessage("tpa.cannot_teleport_to_self")
            ));
            return 0;
        }

        TPAManager.sendTPARequest(requester, target);
        return 1;
    }

    private static int executeTPAcceptCommand(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal(LocalizationManager.getMessage("command.only_players")));
            return 0;
        }

        TPAManager.acceptTPARequest(player);
        return 1;
    }

    private static int executeTPCancelCommand(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal(LocalizationManager.getMessage("command.only_players")));
            return 0;
        }

        TPAManager.cancelTPARequest(player);
        return 1;
    }

    // God and Fly commands implementation

    private static int executeGodCommand(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal(LocalizationManager.getMessage("command.only_players")));
            return 0;
        }

        PlayerStatesManager.toggleGodMode(player);
        return 1;
    }

    private static int executeFlyCommand(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal(LocalizationManager.getMessage("command.only_players")));
            return 0;
        }

        PlayerStatesManager.toggleFlyMode(player);
        return 1;
    }

}