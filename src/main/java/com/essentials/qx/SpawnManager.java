package com.essentials.qx;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Manages spawn point operations and data persistence
 */
public class SpawnManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("EssentialsQX-Spawn");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static SpawnData spawnData;

    /**
     * Initialize spawn manager
     */
    public static void init(MinecraftServer server) {
        if (server != null) {
            loadSpawnData(server);
        }
    }

    /**
     * Get current spawn data
     */
    public static SpawnData getSpawnData() {
        return spawnData;
    }

    /**
     * Set spawn data and save to file
     */
    public static void setSpawnData(SpawnData newSpawnData, MinecraftServer server) {
        spawnData = newSpawnData;
        saveSpawnData(server);
    }

    /**
     * Load spawn data from file
     */
    private static void loadSpawnData(MinecraftServer server) {
        try {
            Path spawnFile = getSpawnFile(server);
            if (Files.exists(spawnFile)) {
                String json = Files.readString(spawnFile);
                spawnData = GSON.fromJson(json, SpawnData.class);
                LOGGER.info("Spawn data loaded from: {}", spawnFile);
            } else {
                spawnData = null; // No spawn data set yet
                LOGGER.info("No spawn data file found at: {}", spawnFile);
            }
        } catch (Exception e) {
            LOGGER.error("Error loading spawn data, using default: {}", e.getMessage());
            spawnData = null;
        }
    }

    /**
     * Save spawn data to file
     */
    private static void saveSpawnData(MinecraftServer server) {
        try {
            Path spawnFile = getSpawnFile(server);
            Files.createDirectories(spawnFile.getParent()); // Ensure essentialsqx folder exists
            String json = GSON.toJson(spawnData);
            Files.writeString(spawnFile, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            LOGGER.info("Spawn data saved to: {}", spawnFile);
        } catch (IOException e) {
            LOGGER.error("Error saving spawn data: {}", e.getMessage());
        }
    }

    /**
     * Get spawn file path
     */
    private static Path getSpawnFile(MinecraftServer server) {
        Path worldPath = server.getWorldPath(LevelResource.ROOT);
        Path essentialsqxFolder = worldPath.resolve(EssentialsQXMod.MOD_ID);
        return essentialsqxFolder.resolve("spawn.json");
    }

    /**
     * Validate spawn location for setting
     */
    public static boolean isValidSpawnLocation(ServerPlayer player) {
        return SafetyManager.isSafeSpawnLocation(player) &&
               WorldBorderManager.isFarEnoughFromWorldBorder(player);
    }

    /**
     * Data class for spawn point information
     */
    public static class SpawnData {
        public double x = 0;
        public double y = 64;
        public double z = 0;
        public float yaw = 0;
        public float pitch = 0;
        public String dimension = "minecraft:overworld";

        public boolean isValid() {
            return dimension != null && !dimension.isEmpty();
        }

        public ResourceKey<Level> getDimensionKey() {
            return ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                ResourceLocation.parse(dimension));
        }
    }
}