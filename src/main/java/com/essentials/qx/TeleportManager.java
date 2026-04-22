package com.essentials.qx;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.Math;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;

/**
 * Manages teleportation with countdown timer
 */
public class TeleportManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("EssentialsQX-Teleport");

    private static final Map<UUID, TeleportState> ACTIVE_TELEPORTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Instant> PLAYER_COOLDOWNS = new ConcurrentHashMap<>();

    /**
     * Start teleport countdown for a player
     */
    public static void startTeleportCountdown(ServerPlayer player, Runnable onComplete) {
        UUID playerId = player.getUUID();

        // Check cooldown (unless player can bypass)
        if (!canBypassTeleportCooldown(player) && isOnCooldown(playerId)) {
            int remainingSeconds = getRemainingCooldownSeconds(playerId);
            player.sendSystemMessage(Component.literal("§c" + LocalizationManager.getMessage("teleport.on_cooldown", remainingSeconds) + "§c"));
            return;
        }

        // Check if delay is bypassed for operators
        int effectiveDelay = canBypassTeleportDelay(player) ? 0 : EssentialsQXMod.getConfig().teleportDelaySeconds;

        if (effectiveDelay <= 0) {
            // Instant teleport
            onComplete.run();
            if (!canBypassTeleportCooldown(player)) {
                setCooldown(playerId); // Set cooldown only if player can't bypass it
            }
            return;
        }

        TeleportState teleportState = new TeleportState(
            player.getX(), player.getY(), player.getZ(),
            effectiveDelay * 20, // Convert seconds to ticks (20 ticks = 1 second)
            () -> {
                onComplete.run();
                if (!canBypassTeleportCooldown(player)) {
                    setCooldown(playerId); // Set cooldown after successful teleport (unless bypassed)
                }
            }
        );

        ACTIVE_TELEPORTS.put(playerId, teleportState);

        // Send initial message
        player.sendSystemMessage(Component.literal("§e" + LocalizationManager.getMessage("teleport.starting", effectiveDelay)));

        // Show countdown info only if countdown starts later than immediately
        int countdownThreshold = Math.min(effectiveDelay, 5);
        if (countdownThreshold < effectiveDelay) {
            player.sendSystemMessage(Component.literal("§7" + LocalizationManager.getMessage("teleport.countdown_info", countdownThreshold)));
        }

        LOGGER.info("Started teleport countdown for player {} ({} seconds)", player.getName().getString(), effectiveDelay);
    }

    /**
     * Cancel teleport countdown for a player
     */
    public static void cancelTeleportCountdown(UUID playerId, String reason) {
        TeleportState removed = ACTIVE_TELEPORTS.remove(playerId);
        if (removed != null) {
            LOGGER.info("Cancelled teleport countdown for player {}: {}", playerId, reason);
        }
    }

    /**
     * Check if player has active teleport
     */
    public static boolean hasActiveTeleport(UUID playerId) {
        return ACTIVE_TELEPORTS.containsKey(playerId);
    }

    /**
     * Check if player is on teleport cooldown
     */
    public static boolean isOnCooldown(UUID playerId) {
        Instant lastTeleport = PLAYER_COOLDOWNS.get(playerId);
        if (lastTeleport == null) {
            return false;
        }

        int cooldownSeconds = EssentialsQXMod.getConfig().teleportCooldownSeconds;
        Instant cooldownEnd = lastTeleport.plusSeconds(cooldownSeconds);
        return Instant.now().isBefore(cooldownEnd);
    }

    /**
     * Get remaining cooldown time in seconds
     */
    public static int getRemainingCooldownSeconds(UUID playerId) {
        Instant lastTeleport = PLAYER_COOLDOWNS.get(playerId);
        if (lastTeleport == null) {
            return 0;
        }

        int cooldownSeconds = EssentialsQXMod.getConfig().teleportCooldownSeconds;
        Instant cooldownEnd = lastTeleport.plusSeconds(cooldownSeconds);
        long remainingSeconds = java.time.Duration.between(Instant.now(), cooldownEnd).getSeconds();
        return Math.max(0, (int) remainingSeconds);
    }

    /**
     * Set player's teleport cooldown
     */
    public static void setCooldown(UUID playerId) {
        PLAYER_COOLDOWNS.put(playerId, Instant.now());
    }

    /**
     * Check if player can bypass teleport delay (operator always bypasses)
     */
    public static boolean canBypassTeleportDelay(ServerPlayer player) {
        return player.hasPermissions(2);  // Operator always bypasses
    }

    /**
     * Check if player can bypass teleport cooldown (operator always bypasses)
     */
    public static boolean canBypassTeleportCooldown(ServerPlayer player) {
        return player.hasPermissions(2);  // Operator always bypasses
    }

    /**
     * Server tick handler for teleport countdown
     */
    public static void onServerTick(MinecraftServer server) {
        // Update all active teleports
        ACTIVE_TELEPORTS.entrySet().removeIf(entry -> {
            UUID playerId = entry.getKey();
            TeleportState state = entry.getValue();

            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                // Player disconnected, cancel teleport and remove cooldown
                PLAYER_COOLDOWNS.remove(playerId);
                return true;
            }

            // Check if movement is not allowed and player moved
            if (!EssentialsQXMod.getConfig().allowMovementDuringTeleport) {
                Vec3 currentPos = player.position();
                double distanceMoved = Math.sqrt(
                    Math.pow(currentPos.x - state.startX(), 2) +
                    Math.pow(currentPos.y - state.startY(), 2) +
                    Math.pow(currentPos.z - state.startZ(), 2)
                );

                if (distanceMoved > 0.1) { // Allow small movements (looking around, etc.)
                    cancelTeleportCountdown(playerId, "player moved");
                    player.sendSystemMessage(Component.literal("§c" + LocalizationManager.getMessage("teleport.cancelled_movement")));
                    return true;
                }
            }

            state.decrementTicks();

            // Show countdown messages
            int secondsRemaining = state.ticksRemaining / 20;
            int originalDelay = EssentialsQXMod.getConfig().teleportDelaySeconds;
            int countdownThreshold = Math.min(originalDelay, 5);

            if (secondsRemaining <= countdownThreshold && secondsRemaining > 0 && state.ticksRemaining % 20 == 0) {
                player.sendSystemMessage(Component.literal("§e" + LocalizationManager.getMessage("teleport.countdown", secondsRemaining)));
            }

            if (state.ticksRemaining <= 0) {
                // Teleport complete
                try {
                    state.onComplete().run();
                    player.sendSystemMessage(Component.literal("§a" + LocalizationManager.getMessage("teleport.success")));
                    LOGGER.info("Successfully teleported player {}", player.getName().getString());
                } catch (Exception e) {
                    player.sendSystemMessage(Component.literal("§c" + LocalizationManager.getMessage("teleport.failed")));
                    LOGGER.error("Error during teleport completion for player {}: {}", player.getName().getString(), e.getMessage());
                }
                return true; // Remove from map
            }

            return false; // Keep in map
        });
    }

    /**
     * Teleport state class
     */
    private static class TeleportState {
        private final double startX, startY, startZ;
        private int ticksRemaining;
        private final Runnable onComplete;

        public TeleportState(double startX, double startY, double startZ, int ticksRemaining, Runnable onComplete) {
            this.startX = startX;
            this.startY = startY;
            this.startZ = startZ;
            this.ticksRemaining = ticksRemaining;
            this.onComplete = onComplete;
        }

        public double startX() { return startX; }
        public double startY() { return startY; }
        public double startZ() { return startZ; }
        public int ticksRemaining() { return ticksRemaining; }
        public Runnable onComplete() { return onComplete; }

        public void decrementTicks() { ticksRemaining--; }
    }
}