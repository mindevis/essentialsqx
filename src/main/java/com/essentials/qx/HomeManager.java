package com.essentials.qx;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Manages player homes system
 */
public class HomeManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("EssentialsQX-Home");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern HOME_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");

    // Cache for loaded player homes
    private static final Map<UUID, Map<String, HomeData>> PLAYER_HOMES = new ConcurrentHashMap<>();

    /**
     * Initialize home manager
     */
    public static void init() {
        // Homes are loaded on-demand when players join
    }

    /**
     * Validate home name format
     */
    public static boolean isValidHomeName(String name) {
        return name != null && !name.isEmpty() && HOME_NAME_PATTERN.matcher(name).matches();
    }

    /**
     * Check if dimension is allowed for home setting
     */
    public static boolean isDimensionAllowed(ResourceKey<Level> dimension) {
        List<String> allowedDimensions = EssentialsQXMod.getConfig().allowedHomeDimensions;
        if (allowedDimensions == null || allowedDimensions.isEmpty()) {
            // If no restrictions, allow all
            return true;
        }

        String dimName = dimension.location().toString();
        return allowedDimensions.contains(dimName);
    }

    /**
     * Check if player can set more homes
     */
    public static boolean canSetMoreHomes(UUID playerId, MinecraftServer server) {
        Map<String, HomeData> homes = getPlayerHomes(playerId, server);
        int maxHomes = EssentialsQXMod.getConfig().maxHomesPerPlayer;

        // Operators always bypass home limit
        if (server != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null && player.hasPermissions(2)) {
                return true;
            }
        }

        return homes.size() < maxHomes;
    }

    /**
     * Set a home for player
     */
    public static boolean setHome(ServerPlayer player, String homeName) {
        UUID playerId = player.getUUID();
        MinecraftServer server = player.getServer();

        // Validate home name
        if (!isValidHomeName(homeName)) {
            player.sendSystemMessage(Component.literal(
                LocalizationManager.getMessage("home.invalid_name")
            ));
            return false;
        }

        // Check dimension restrictions
        if (!isDimensionAllowed(player.level().dimension())) {
            player.sendSystemMessage(Component.literal(
                LocalizationManager.getMessage("home.dimension_not_allowed")
            ));
            return false;
        }

        // Check home limit
        if (!canSetMoreHomes(playerId, server)) {
            player.sendSystemMessage(Component.literal(
                LocalizationManager.getMessage("home.limit_reached", EssentialsQXMod.getConfig().maxHomesPerPlayer)
            ));
            return false;
        }

        // Check if home already exists
        Map<String, HomeData> homes = getPlayerHomes(playerId, server);
        if (homes.containsKey(homeName)) {
            player.sendSystemMessage(Component.literal(
                LocalizationManager.getMessage("home.already_exists", homeName)
            ));
            return false;
        }

        // Validate safety
        if (!SafetyManager.isSafeSpawnLocation(player)) {
            player.sendSystemMessage(Component.literal(
                LocalizationManager.getMessage("home.location_unsafe")
            ));
            return false;
        }

        // Create home data
        HomeData homeData = new HomeData(
            player.getX(), player.getY(), player.getZ(),
            player.getYRot(), player.getXRot(),
            player.level().dimension().location().toString()
        );

        // Save home
        homes.put(homeName, homeData);
        savePlayerHomesToFile(playerId, server);

        player.sendSystemMessage(Component.literal(
            LocalizationManager.getMessage("home.set_success", homeName)
        ));

        LOGGER.info("Player {} set home '{}' at {}, {}, {} in {}",
            player.getName().getString(), homeName,
            homeData.x, homeData.y, homeData.z, homeData.dimension);

        return true;
    }

    /**
     * Rename a home
     */
    public static boolean renameHome(ServerPlayer player, String oldName, String newName) {
        UUID playerId = player.getUUID();
        MinecraftServer server = player.getServer();
        Map<String, HomeData> homes = getPlayerHomes(playerId, server);

        // Check if old home exists
        if (!homes.containsKey(oldName)) {
            player.sendSystemMessage(Component.literal(
                LocalizationManager.getMessage("home.not_found", oldName)
            ));
            return false;
        }

        // Validate new name
        if (!isValidHomeName(newName)) {
            player.sendSystemMessage(Component.literal(
                LocalizationManager.getMessage("home.invalid_name")
            ));
            return false;
        }

        // Check if new name already exists
        if (homes.containsKey(newName)) {
            player.sendSystemMessage(Component.literal(
                LocalizationManager.getMessage("home.already_exists", newName)
            ));
            return false;
        }

        // Rename home
        HomeData homeData = homes.remove(oldName);
        homes.put(newName, homeData);
        savePlayerHomesToFile(playerId, server);

        player.sendSystemMessage(Component.literal(
            LocalizationManager.getMessage("home.rename_success", oldName, newName)
        ));

        LOGGER.info("Player {} renamed home '{}' to '{}'",
            player.getName().getString(), oldName, newName);

        return true;
    }

    /**
     * Delete a home
     */
    public static boolean deleteHome(ServerPlayer player, String homeName) {
        UUID playerId = player.getUUID();
        MinecraftServer server = player.getServer();
        Map<String, HomeData> homes = getPlayerHomes(playerId, server);

        if (!homes.containsKey(homeName)) {
            player.sendSystemMessage(Component.literal(
                LocalizationManager.getMessage("home.not_found", homeName)
            ));
            return false;
        }

        homes.remove(homeName);
        savePlayerHomesToFile(playerId, server);

        player.sendSystemMessage(Component.literal(
            LocalizationManager.getMessage("home.delete_success", homeName)
        ));

        LOGGER.info("Player {} deleted home '{}'", player.getName().getString(), homeName);

        return true;
    }

    /**
     * Teleport to home
     */
    public static boolean teleportToHome(ServerPlayer player, String homeName) {
        UUID playerId = player.getUUID();
        Map<String, HomeData> homes = getPlayerHomes(playerId, player.getServer());

        if (!homes.containsKey(homeName)) {
            player.sendSystemMessage(Component.literal(
                LocalizationManager.getMessage("home.not_found", homeName)
            ));
            return false;
        }

        HomeData homeData = homes.get(homeName);

        try {
            // Get target dimension
            ResourceKey<Level> targetDimension = ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                ResourceLocation.parse(homeData.dimension)
            );

            ServerLevel targetLevel = player.getServer().getLevel(targetDimension);
            if (targetLevel == null) {
                player.sendSystemMessage(Component.literal(
                    LocalizationManager.getMessage("home.dimension_unavailable")
                ));
                return false;
            }

            // Find safe teleport location
            BlockPos originalPos = new BlockPos((int) homeData.x, (int) homeData.y, (int) homeData.z);
            BlockPos safePos = SafetyManager.findSafeTeleportLocation(targetLevel, originalPos);

            if (safePos == null) {
                player.sendSystemMessage(Component.literal(
                    LocalizationManager.getMessage("home.destination_unsafe")
                ));
                return false;
            }

            // Create teleport action
            Runnable teleportAction = () -> {
                try {
                    var finalPos = new net.minecraft.world.phys.Vec3(
                        safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5
                    );
                    player.teleportTo(targetLevel, finalPos.x, finalPos.y, finalPos.z,
                        homeData.yaw, homeData.pitch);

                    player.sendSystemMessage(Component.literal(
                        LocalizationManager.getMessage("home.teleport_success", homeName)
                    ));

                    LOGGER.info("Player {} teleported to home '{}' at {}, {}, {} in {}",
                        player.getName().getString(), homeName,
                        finalPos.x, finalPos.y, finalPos.z, homeData.dimension);

                } catch (Exception e) {
                    player.sendSystemMessage(Component.literal(
                        LocalizationManager.getMessage("home.teleport_failed")
                    ));
                    LOGGER.error("Error teleporting player {} to home: {}",
                        player.getName().getString(), e.getMessage());
                }
            };

            // Start teleport countdown
            TeleportManager.startTeleportCountdown(player, teleportAction);

        } catch (Exception e) {
            player.sendSystemMessage(Component.literal(
                LocalizationManager.getMessage("home.teleport_failed")
            ));
            LOGGER.error("Error preparing teleport for player {} to home: {}",
                player.getName().getString(), e.getMessage());
            return false;
        }

        return true;
    }

    /**
     * List player homes
     */
    public static void listHomes(ServerPlayer player) {
        UUID playerId = player.getUUID();
        MinecraftServer server = player.getServer();
        Map<String, HomeData> homes = getPlayerHomes(playerId, server);

        if (homes.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                LocalizationManager.getMessage("home.no_homes")
            ));
            return;
        }

        player.sendSystemMessage(Component.literal(
            LocalizationManager.getMessage("home.list_header", homes.size())
        ));

        for (Map.Entry<String, HomeData> entry : homes.entrySet()) {
            HomeData home = entry.getValue();
            player.sendSystemMessage(Component.literal(
                LocalizationManager.getMessage("home.list_entry",
                    entry.getKey(), (int)home.x, (int)home.y, (int)home.z, home.dimension)
            ));
        }
    }

    /**
     * Admin: List player homes
     */
    public static void listPlayerHomes(ServerPlayer admin, String targetPlayerName) {
        MinecraftServer server = admin.getServer();
        ServerPlayer targetPlayer = findPlayerByName(server, targetPlayerName);
        if (targetPlayer == null) {
            admin.sendSystemMessage(Component.literal(
                LocalizationManager.getMessage("home.player_not_found", targetPlayerName)
            ));
            return;
        }

        UUID targetId = targetPlayer.getUUID();
        Map<String, HomeData> homes = getPlayerHomes(targetId, server);

        if (homes.isEmpty()) {
            admin.sendSystemMessage(Component.literal(
                LocalizationManager.getMessage("home.admin_no_homes", targetPlayerName)
            ));
            return;
        }

        admin.sendSystemMessage(Component.literal(
            LocalizationManager.getMessage("home.admin_list_header", targetPlayerName, homes.size())
        ));

        for (Map.Entry<String, HomeData> entry : homes.entrySet()) {
            HomeData home = entry.getValue();
            admin.sendSystemMessage(Component.literal(
                LocalizationManager.getMessage("home.admin_list_entry",
                    entry.getKey(), (int)home.x, (int)home.y, (int)home.z, home.dimension)
            ));
        }
    }

    /**
     * Admin: Delete player home
     */
    public static void deletePlayerHome(ServerPlayer admin, String targetPlayerName, String homeName) {
        MinecraftServer server = admin.getServer();
        ServerPlayer targetPlayer = findPlayerByName(server, targetPlayerName);
        if (targetPlayer == null) {
            admin.sendSystemMessage(Component.literal(
                LocalizationManager.getMessage("home.player_not_found", targetPlayerName)
            ));
            return;
        }

        UUID targetId = targetPlayer.getUUID();
        Map<String, HomeData> homes = getPlayerHomes(targetId, server);

        if (!homes.containsKey(homeName)) {
            admin.sendSystemMessage(Component.literal(
                LocalizationManager.getMessage("home.admin_home_not_found", homeName, targetPlayerName)
            ));
            return;
        }

        homes.remove(homeName);
        savePlayerHomesToFile(targetId, server);

        admin.sendSystemMessage(Component.literal(
            LocalizationManager.getMessage("home.admin_delete_success", homeName, targetPlayerName)
        ));

        // Notify target player
        targetPlayer.sendSystemMessage(Component.literal(
            LocalizationManager.getMessage("home.admin_home_deleted", homeName, admin.getName().getString())
        ));

        LOGGER.info("Admin {} deleted home '{}' for player {}",
            admin.getName().getString(), homeName, targetPlayerName);
    }

    /**
     * Admin: Teleport to player home
     */
    public static void teleportToPlayerHome(ServerPlayer admin, String targetPlayerName, String homeName) {
        ServerPlayer targetPlayer = findPlayerByName(admin.getServer(), targetPlayerName);
        if (targetPlayer == null) {
            admin.sendSystemMessage(Component.literal(
                LocalizationManager.getMessage("home.player_not_found", targetPlayerName)
            ));
            return;
        }

        UUID targetId = targetPlayer.getUUID();
        Map<String, HomeData> homes = getPlayerHomes(targetId, admin.getServer());

        if (!homes.containsKey(homeName)) {
            admin.sendSystemMessage(Component.literal(
                LocalizationManager.getMessage("home.admin_home_not_found", homeName, targetPlayerName)
            ));
            return;
        }

        HomeData homeData = homes.get(homeName);

        try {
            ResourceKey<Level> targetDimension = ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                ResourceLocation.parse(homeData.dimension)
            );

            ServerLevel targetLevel = admin.getServer().getLevel(targetDimension);
            if (targetLevel == null) {
                admin.sendSystemMessage(Component.literal(
                    LocalizationManager.getMessage("home.dimension_unavailable")
                ));
                return;
            }

            BlockPos originalPos = new BlockPos((int) homeData.x, (int) homeData.y, (int) homeData.z);
            BlockPos safePos = SafetyManager.findSafeTeleportLocation(targetLevel, originalPos);

            if (safePos == null) {
                admin.sendSystemMessage(Component.literal(
                    LocalizationManager.getMessage("home.destination_unsafe")
                ));
                return;
            }

            Runnable teleportAction = () -> {
                try {
                    var finalPos = new net.minecraft.world.phys.Vec3(
                        safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5
                    );
                    admin.teleportTo(targetLevel, finalPos.x, finalPos.y, finalPos.z,
                        homeData.yaw, homeData.pitch);

                    admin.sendSystemMessage(Component.literal(
                        LocalizationManager.getMessage("home.admin_teleport_success", homeName, targetPlayerName)
                    ));

                    LOGGER.info("Admin {} teleported to home '{}' of player {} at {}, {}, {} in {}",
                        admin.getName().getString(), homeName, targetPlayerName,
                        finalPos.x, finalPos.y, finalPos.z, homeData.dimension);

                } catch (Exception e) {
                    admin.sendSystemMessage(Component.literal(
                        LocalizationManager.getMessage("home.teleport_failed")
                    ));
                    LOGGER.error("Error teleporting admin {} to player home: {}",
                        admin.getName().getString(), e.getMessage());
                }
            };

            TeleportManager.startTeleportCountdown(admin, teleportAction);

        } catch (Exception e) {
            admin.sendSystemMessage(Component.literal(
                LocalizationManager.getMessage("home.teleport_failed")
            ));
            LOGGER.error("Error preparing teleport for admin {} to player home: {}",
                admin.getName().getString(), e.getMessage());
        }
    }

    /**
     * Get player homes (load if not cached)
     */
    private static Map<String, HomeData> getPlayerHomes(UUID playerId, MinecraftServer server) {
        return PLAYER_HOMES.computeIfAbsent(playerId, id -> loadPlayerHomes(id, server));
    }

    /**
     * Load player homes from file
     */
    private static Map<String, HomeData> loadPlayerHomes(UUID playerId, MinecraftServer server) {
        try {
            Path homesFile = getPlayerHomesFile(playerId, server);
            if (Files.exists(homesFile)) {
                String json = Files.readString(homesFile);
                Type type = new TypeToken<Map<String, HomeData>>(){}.getType();
                Map<String, HomeData> homes = GSON.fromJson(json, type);
                return homes != null ? homes : new HashMap<>();
            }
        } catch (Exception e) {
            LOGGER.error("Error loading homes for player {}: {}", playerId, e.getMessage());
        }
        return new HashMap<>();
    }

    /**
     * Save player homes to file (internal method)
     */
    private static void savePlayerHomesToFile(UUID playerId, MinecraftServer server) {
        try {
            Map<String, HomeData> homes = PLAYER_HOMES.get(playerId);
            if (homes != null) {
                Path homesFile = getPlayerHomesFile(playerId, server);
                Files.createDirectories(homesFile.getParent());
                String json = GSON.toJson(homes);
                Files.writeString(homesFile, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
        } catch (Exception e) {
            LOGGER.error("Error saving homes for player {}: {}", playerId, e.getMessage());
        }
    }

    /**
     * Get player homes file path
     */
    private static Path getPlayerHomesFile(UUID playerId, MinecraftServer server) {
        if (server == null) return null;

        Path worldPath = server.getWorldPath(LevelResource.ROOT);
        Path essentialsqxFolder = worldPath.resolve(EssentialsQXMod.MOD_ID);
        Path usersFolder = essentialsqxFolder.resolve("users");
        Path playerFolder = usersFolder.resolve(playerId.toString());

        return playerFolder.resolve("homes.json");
    }

    /**
     * Get server instance
     */
    private static MinecraftServer getServer() {
        // This method should be called from command context where server is available
        // For now, return null - server should be passed as parameter
        return null;
    }

    /**
     * Find player by name
     */
    private static ServerPlayer findPlayerByName(MinecraftServer server, String playerName) {
        return server.getPlayerList().getPlayerByName(playerName);
    }

    /**
     * Save homes for a specific player
     */
    public static void savePlayerHomes(UUID playerId, MinecraftServer server) {
        savePlayerHomesToFile(playerId, server);
    }

    /**
     * Save all homes on server shutdown
     */
    public static void saveAllHomes(MinecraftServer server) {
        for (Map.Entry<UUID, Map<String, HomeData>> entry : PLAYER_HOMES.entrySet()) {
            savePlayerHomesToFile(entry.getKey(), server);
        }
        LOGGER.info("Saved all player homes");
    }

    /**
     * Home data class
     */
    public static class HomeData {
        public double x, y, z;
        public float yaw, pitch;
        public String dimension;

        public HomeData() {}

        public HomeData(double x, double y, double z, float yaw, float pitch, String dimension) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.dimension = dimension;
        }
    }
}