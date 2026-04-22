package com.essentials.qx;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Teleport Accept (TPA) system
 */
public class TPAManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("EssentialsQX-TPA");
    private static net.minecraft.server.MinecraftServer server;

    // Active TPA requests: RequesterUUID -> TargetUUID -> RequestData
    private static final Map<UUID, Map<UUID, TPARequest>> activeRequests = new ConcurrentHashMap<>();

    /**
     * Initialize TPA system
     */
    public static void init(net.minecraft.server.MinecraftServer serverInstance) {
        server = serverInstance;
        if (server != null) {
            LOGGER.info("TPAManager initialized with server");
        } else {
            LOGGER.info("TPAManager initialized (server not available yet)");
        }
    }

    /**
     * Send TPA request from one player to another
     */
    public static boolean sendTPARequest(ServerPlayer requester, ServerPlayer target) {
        UUID requesterId = requester.getUUID();
        UUID targetId = target.getUUID();

        // Check if requester has active outgoing request
        if (hasOutgoingRequest(requesterId)) {
            requester.sendSystemMessage(Component.literal(
                LocalizationManager.getMessage("tpa.already_have_request")
            ));
            return false;
        }

        // Check if target has incoming request from someone else
        if (hasIncomingRequest(targetId)) {
            requester.sendSystemMessage(Component.literal(
                LocalizationManager.getMessage("tpa.target_busy")
            ));
            return false;
        }

        // Create new request
        TPARequest request = new TPARequest(requesterId, targetId, Instant.now());
        activeRequests.computeIfAbsent(requesterId, k -> new ConcurrentHashMap<>()).put(targetId, request);

        // Notify both players
        requester.sendSystemMessage(Component.literal(
            LocalizationManager.getMessage("tpa.request_sent", target.getName().getString())
        ));

        target.sendSystemMessage(Component.literal(
            LocalizationManager.getMessage("tpa.request_received", requester.getName().getString())
        ));

        LOGGER.info("TPA request sent from {} to {}", requester.getName().getString(), target.getName().getString());

        return true;
    }

    /**
     * Accept incoming TPA request
     */
    public static boolean acceptTPARequest(ServerPlayer acceptor) {
        UUID acceptorId = acceptor.getUUID();

        // Find incoming request
        TPARequest request = findIncomingRequest(acceptorId);
        if (request == null) {
            acceptor.sendSystemMessage(Component.literal(
                LocalizationManager.getMessage("tpa.no_request")
            ));
            return false;
        }

        // Get requester
        ServerPlayer requester = acceptor.getServer().getPlayerList().getPlayer(request.requesterId);
        if (requester == null) {
            acceptor.sendSystemMessage(Component.literal(
                LocalizationManager.getMessage("tpa.requester_offline")
            ));
            removeRequest(request);
            return false;
        }

        // Check if requester can teleport (cooldown)
        if (!TeleportManager.canBypassTeleportCooldown(requester) &&
            TeleportManager.isOnCooldown(requester.getUUID())) {
            int remainingSeconds = TeleportManager.getRemainingCooldownSeconds(requester.getUUID());
            acceptor.sendSystemMessage(Component.literal(
                LocalizationManager.getMessage("tpa.requester_on_cooldown", remainingSeconds)
            ));
            requester.sendSystemMessage(Component.literal(
                LocalizationManager.getMessage("tpa.you_on_cooldown", remainingSeconds)
            ));
            removeRequest(request);
            return false;
        }

        // Check safety of acceptor location
        if (!SafetyManager.isSafeTeleportLocation(acceptor.serverLevel(), acceptor.blockPosition())) {
            acceptor.sendSystemMessage(Component.literal(
                LocalizationManager.getMessage("tpa.destination_unsafe")
            ));
            requester.sendSystemMessage(Component.literal(
                LocalizationManager.getMessage("tpa.target_location_unsafe")
            ));
            removeRequest(request);
            return false;
        }

        // Check if requester has active teleport
        if (TeleportManager.hasActiveTeleport(requester.getUUID())) {
            acceptor.sendSystemMessage(Component.literal(
                LocalizationManager.getMessage("tpa.requester_busy")
            ));
            requester.sendSystemMessage(Component.literal(
                LocalizationManager.getMessage("tpa.you_have_active_teleport")
            ));
            removeRequest(request);
            return false;
        }

        // Start teleport
        performTeleport(requester, acceptor);
        removeRequest(request);

        return true;
    }

    /**
     * Cancel outgoing TPA request
     */
    public static boolean cancelTPARequest(ServerPlayer canceller) {
        UUID cancellerId = canceller.getUUID();

        TPARequest request = findOutgoingRequest(cancellerId);
        if (request == null) {
            canceller.sendSystemMessage(Component.literal(
                LocalizationManager.getMessage("tpa.no_outgoing_request")
            ));
            return false;
        }

        // Notify both players
        ServerPlayer target = canceller.getServer().getPlayerList().getPlayer(request.targetId);
        if (target != null) {
            target.sendSystemMessage(Component.literal(
                LocalizationManager.getMessage("tpa.request_cancelled_by_sender", canceller.getName().getString())
            ));
        }

        canceller.sendSystemMessage(Component.literal(
            LocalizationManager.getMessage("tpa.request_cancelled")
        ));

        removeRequest(request);
        LOGGER.info("TPA request cancelled by sender: {}", canceller.getName().getString());

        return true;
    }

    /**
     * Perform teleport after request acceptance
     */
    private static void performTeleport(ServerPlayer requester, ServerPlayer target) {
        // Store target position for lambda
        final double targetX = target.getX();
        final double targetY = target.getY();
        final double targetZ = target.getZ();
        final float targetYaw = target.getYRot();
        final float targetPitch = target.getXRot();
        final String requesterName = requester.getName().getString();
        final String targetName = target.getName().getString();

        Runnable teleportAction = () -> {
            try {
                // Double-check safety before teleporting
                if (!SafetyManager.isSafeTeleportLocation(target.serverLevel(), target.blockPosition())) {
                    requester.sendSystemMessage(Component.literal(
                        LocalizationManager.getMessage("tpa.destination_now_unsafe")
                    ));
                    target.sendSystemMessage(Component.literal(
                        LocalizationManager.getMessage("tpa.your_location_now_unsafe")
                    ));
                    return;
                }

                requester.teleportTo(target.serverLevel(), targetX, targetY, targetZ, targetYaw, targetPitch);

                requester.sendSystemMessage(Component.literal(
                    LocalizationManager.getMessage("tpa.teleport_success", targetName)
                ));

                target.sendSystemMessage(Component.literal(
                    LocalizationManager.getMessage("tpa.teleport_completed", requesterName)
                ));

                // Apply cooldown
                TeleportManager.setCooldown(requester.getUUID());

                LOGGER.info("TPA teleport completed: {} -> {}", requesterName, targetName);

            } catch (Exception e) {
                requester.sendSystemMessage(Component.literal(
                    LocalizationManager.getMessage("tpa.teleport_failed")
                ));
                target.sendSystemMessage(Component.literal(
                    LocalizationManager.getMessage("tpa.teleport_failed_for_target", requesterName)
                ));
                LOGGER.error("TPA teleport failed: {} -> {}", requesterName, targetName, e);
            }
        };

        TeleportManager.startTeleportCountdown(requester, teleportAction);
    }

    /**
     * Check if player has outgoing TPA request
     */
    private static boolean hasOutgoingRequest(UUID playerId) {
        Map<UUID, TPARequest> playerRequests = activeRequests.get(playerId);
        return playerRequests != null && !playerRequests.isEmpty();
    }

    /**
     * Check if player has incoming TPA request
     */
    private static boolean hasIncomingRequest(UUID playerId) {
        for (Map<UUID, TPARequest> requests : activeRequests.values()) {
            if (requests.containsKey(playerId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find incoming request for player
     */
    private static TPARequest findIncomingRequest(UUID playerId) {
        for (Map<UUID, TPARequest> requests : activeRequests.values()) {
            TPARequest request = requests.get(playerId);
            if (request != null) {
                return request;
            }
        }
        return null;
    }

    /**
     * Find outgoing request from player
     */
    private static TPARequest findOutgoingRequest(UUID playerId) {
        Map<UUID, TPARequest> playerRequests = activeRequests.get(playerId);
        if (playerRequests != null && !playerRequests.isEmpty()) {
            return playerRequests.values().iterator().next(); // Return first (should be only one)
        }
        return null;
    }

    /**
     * Remove TPA request
     */
    private static void removeRequest(TPARequest request) {
        Map<UUID, TPARequest> requesterRequests = activeRequests.get(request.requesterId);
        if (requesterRequests != null) {
            requesterRequests.remove(request.targetId);
            if (requesterRequests.isEmpty()) {
                activeRequests.remove(request.requesterId);
            }
        }
    }

    /**
     * Process expired requests (called from server tick)
     */
    public static void processExpiredRequests() {
        int timeoutSeconds = EssentialsQXMod.getConfig().tpaRequestTimeoutSeconds;
        Instant now = Instant.now();

        activeRequests.entrySet().removeIf(entry -> {
            Map<UUID, TPARequest> requests = entry.getValue();
            requests.entrySet().removeIf(requestEntry -> {
                TPARequest request = requestEntry.getValue();
                if (request.createdTime.plusSeconds(timeoutSeconds).isBefore(now)) {
                    // Request expired
                    UUID requesterId = request.requesterId;
                    UUID targetId = request.targetId;

                    ServerPlayer requester = getServerPlayer(requesterId);
                    ServerPlayer target = getServerPlayer(targetId);

                    if (requester != null) {
                        requester.sendSystemMessage(Component.literal(
                            LocalizationManager.getMessage("tpa.request_expired")
                        ));
                    }

                    if (target != null) {
                        target.sendSystemMessage(Component.literal(
                            LocalizationManager.getMessage("tpa.request_expired_for_target", requester != null ? requester.getName().getString() : "Unknown")
                        ));
                    }

                    LOGGER.debug("TPA request expired: {} -> {}", requesterId, targetId);
                    return true;
                }
                return false;
            });
            return requests.isEmpty();
        });
    }

    /**
     * Get server player by UUID
     */
    private static ServerPlayer getServerPlayer(UUID playerId) {
        if (server != null) {
            return server.getPlayerList().getPlayer(playerId);
        }
        return null;
    }

    /**
     * TPA request data class
     */
    private static class TPARequest {
        public final UUID requesterId;
        public final UUID targetId;
        public final Instant createdTime;

        public TPARequest(UUID requesterId, UUID targetId, Instant createdTime) {
            this.requesterId = requesterId;
            this.targetId = targetId;
            this.createdTime = createdTime;
        }
    }
}