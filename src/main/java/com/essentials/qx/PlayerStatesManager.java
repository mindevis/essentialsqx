package com.essentials.qx;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player states (god mode, fly mode, etc.)
 */
public class PlayerStatesManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("EssentialsQX-PlayerStates");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Player states storage
    private static final Map<UUID, PlayerState> playerStates = new ConcurrentHashMap<>();

    /**
     * Player state data class
     */
    public static class PlayerState {
        public boolean godMode = false;
        public boolean flyMode = false;

        public PlayerState() {}

        public PlayerState(boolean godMode, boolean flyMode) {
            this.godMode = godMode;
            this.flyMode = flyMode;
        }
    }

    /**
     * Initialize player states system
     */
    public static void init(MinecraftServer server) {
        if (server != null) {
            LOGGER.info("PlayerStatesManager initialized with server");
        } else {
            LOGGER.info("PlayerStatesManager initialized (server not available yet)");
        }
    }

    /**
     * Load player state from file
     */
    public static PlayerState loadPlayerState(UUID playerId, MinecraftServer server) {
        try {
            Path stateFile = getPlayerStateFile(playerId, server);
            if (Files.exists(stateFile)) {
                String json = Files.readString(stateFile);
                PlayerState state = GSON.fromJson(json, PlayerState.class);
                if (state != null) {
                    LOGGER.debug("Loaded state for player {}", playerId);
                    return state;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to load player state for {}: {}", playerId, e.getMessage());
        }

        // Return default state
        LOGGER.debug("Using default state for player {}", playerId);
        return new PlayerState();
    }

    /**
     * Save player state to file
     */
    public static void savePlayerState(UUID playerId, MinecraftServer server) {
        try {
            PlayerState state = playerStates.get(playerId);
            if (state != null) {
                Path stateFile = getPlayerStateFile(playerId, server);
                Files.createDirectories(stateFile.getParent());
                String json = GSON.toJson(state);
                Files.writeString(stateFile, json);
                LOGGER.debug("Saved state for player {}", playerId);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save player state for {}: {}", playerId, e.getMessage());
        }
    }

    /**
     * Get or load player state
     */
    public static PlayerState getPlayerState(UUID playerId, MinecraftServer server) {
        // First check if already cached
        PlayerState cached = playerStates.get(playerId);
        if (cached != null) {
            return cached;
        }

        // Load from file and cache
        PlayerState loaded = loadPlayerState(playerId, server);
        playerStates.put(playerId, loaded);
        return loaded;
    }

    /**
     * Set god mode for player
     */
    public static boolean toggleGodMode(ServerPlayer player) {
        UUID playerId = player.getUUID();
        MinecraftServer server = player.getServer();
        PlayerState state = getPlayerState(playerId, server);

        state.godMode = !state.godMode;

        if (state.godMode) {
            // Enable god mode
            player.getAbilities().invulnerable = true;
            player.onUpdateAbilities();
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                LocalizationManager.getMessage("god.enabled")
            ));
            LOGGER.info("God mode enabled for player {}", player.getName().getString());
        } else {
            // Disable god mode
            player.getAbilities().invulnerable = false;
            player.onUpdateAbilities();
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                LocalizationManager.getMessage("god.disabled")
            ));
            LOGGER.info("God mode disabled for player {}", player.getName().getString());
        }

        savePlayerState(playerId, server);
        return state.godMode;
    }

    /**
     * Set fly mode for player
     */
    public static boolean toggleFlyMode(ServerPlayer player) {
        UUID playerId = player.getUUID();
        MinecraftServer server = player.getServer();
        PlayerState state = getPlayerState(playerId, server);

        state.flyMode = !state.flyMode;

        if (state.flyMode) {
            // Enable fly mode
            player.getAbilities().mayfly = true;
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                LocalizationManager.getMessage("fly.enabled")
            ));
            LOGGER.info("Fly mode enabled for player {}", player.getName().getString());
        } else {
            // Disable fly mode
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                LocalizationManager.getMessage("fly.disabled")
            ));
            LOGGER.info("Fly mode disabled for player {}", player.getName().getString());
        }

        savePlayerState(playerId, server);
        return state.flyMode;
    }

    /**
     * Apply saved states to player on join
     */
    public static void applySavedStates(ServerPlayer player) {
        UUID playerId = player.getUUID();
        MinecraftServer server = player.getServer();

        // Load state directly from file to avoid ConcurrentHashMap issues during player join
        PlayerState state = loadPlayerState(playerId, server);

        // Cache the state in memory for future use
        playerStates.put(playerId, state);

        // Apply god mode
        if (state.godMode) {
            player.getAbilities().invulnerable = true;
            LOGGER.debug("Restored god mode for player {}", player.getName().getString());
        }

        // Apply fly mode
        if (state.flyMode) {
            player.getAbilities().mayfly = true;
            player.getAbilities().flying = true;
            LOGGER.debug("Restored fly mode for player {}", player.getName().getString());
        }

        if (state.godMode || state.flyMode) {
            player.onUpdateAbilities();
        }
    }

    /**
     * Save all player states on server shutdown
     */
    public static void saveAllStates(MinecraftServer server) {
        for (Map.Entry<UUID, PlayerState> entry : playerStates.entrySet()) {
            savePlayerState(entry.getKey(), server);
        }
        LOGGER.info("Saved all player states");
    }

    /**
     * Get player state file path
     */
    private static Path getPlayerStateFile(UUID playerId, MinecraftServer server) {
        Path worldPath = server.getWorldPath(LevelResource.ROOT);
        Path essentialsqxFolder = worldPath.resolve(EssentialsQXMod.MOD_ID);
        Path usersFolder = essentialsqxFolder.resolve("users");
        Path playerFolder = usersFolder.resolve(playerId.toString());
        return playerFolder.resolve("states.json");
    }

    /**
     * Check if player has god mode enabled
     */
    public static boolean hasGodMode(UUID playerId, MinecraftServer server) {
        PlayerState state = getPlayerState(playerId, server);
        return state.godMode;
    }

    /**
     * Check if player has fly mode enabled
     */
    public static boolean hasFlyMode(UUID playerId, MinecraftServer server) {
        PlayerState state = getPlayerState(playerId, server);
        return state.flyMode;
    }
}