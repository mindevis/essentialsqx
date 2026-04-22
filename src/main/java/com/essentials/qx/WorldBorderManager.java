package com.essentials.qx;

import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages world border distance validation
 */
public class WorldBorderManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("EssentialsQX-WorldBorder");

    /**
     * Check if the player is far enough from the world border
     */
    public static boolean isFarEnoughFromWorldBorder(ServerPlayer player) {
        try {
            var worldBorder = player.level().getWorldBorder();
            var playerPos = player.position();

            // Get the world border bounds
            double minX = worldBorder.getMinX();
            double maxX = worldBorder.getMaxX();
            double minZ = worldBorder.getMinZ();
            double maxZ = worldBorder.getMaxZ();

            // Check minimum distance from each border (3 blocks minimum)
            double distanceFromMinX = playerPos.x - minX;
            double distanceFromMaxX = maxX - playerPos.x;
            double distanceFromMinZ = playerPos.z - minZ;
            double distanceFromMaxZ = maxZ - playerPos.z;

            // All distances must be at least 3 blocks
            return distanceFromMinX >= 3.0 && distanceFromMaxX >= 3.0 &&
                   distanceFromMinZ >= 3.0 && distanceFromMaxZ >= 3.0;

        } catch (Exception e) {
            LOGGER.warn("Error checking world border distance: {}", e.getMessage());
            return false; // Fail-safe: don't allow spawn if we can't check
        }
    }
}