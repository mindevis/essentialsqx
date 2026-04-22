package com.essentials.qx;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
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

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages Random Teleport (RTP) system
 */
public class RTPManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("EssentialsQX-RTP");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String DIMENSION_NETHER = "minecraft:the_nether";
    private static final String DIMENSION_END = "minecraft:the_end";
    private static final int END_NEW_POINT_ATTEMPTS = 250;
    /** End: main island Y~48-50; outer islands can vary, wider range for both */
    private static final int END_SEARCH_Y_MIN = 50;
    private static final int END_SEARCH_Y_MAX = 128;
    private static final int MIN_POINT_DISTANCE = 50;
    private static final int MIN_POINT_DISTANCE_SQ = MIN_POINT_DISTANCE * MIN_POINT_DISTANCE;
    private static final int BORDER_MARGIN = 3;
    private static final int MIN_WORLD_BORDER_SIZE = 10;
    private static final int NETHER_MAX_Y = 120;
    private static final int OVERWORLD_MAX_Y = 200;
    private static final int NEW_POINT_ATTEMPTS = 50;
    /** Attempts per tick when searching — each triggers chunk load; 2 is compromise (find vs lag) */
    private static final int POINT_SEARCH_BATCH_SIZE = 2;
    /** Attempts per tick when generating RTP points (/rtp create) */
    private static final int GENERATION_BATCH_SIZE = 1;
    private static final int BEDROCK_CHECK_MIN_Y = 121;
    private static final int BEDROCK_CHECK_MAX_Y = 127;

    // RTP points storage
    private static List<RTPPoint> rtpPoints = new ArrayList<>();
    /** Index: dimension -> points in that dimension (for fast lookup in randomTeleport) */
    private static Map<String, List<RTPPoint>> dimensionIndex = new HashMap<>();
    /** Set of point keys for O(1) duplicate check when adding new points */
    private static Set<String> rtpPointKeys = new HashSet<>();
    private static boolean rtpPointsLoaded = false;

    // Player used RTP points
    private static final Map<UUID, Set<String>> playerUsedRTPs = new ConcurrentHashMap<>();

    /**
     * Initialize RTP system
     */
    public static void init(MinecraftServer server) {
        if (server != null && !rtpPointsLoaded) {
            loadRTPPoints(server);
        }
    }

    /**
     * Try one generation attempt. If valid, adds to newPoints and returns true.
     * Uses spawn-based for first points (End: center annulus; Overworld/Nether: around world spawn).
     */
    private static boolean tryOneGenerationAttempt(ServerLevel level, String dimensionName,
            int minX, int maxX, int minZ, int maxZ, List<RTPPoint> newPoints) {
        var random = ThreadLocalRandom.current();
        int x, z;
        if (DIMENSION_END.equals(dimensionName)) {
            int[] pos = pickRandomEndPosition(random, minX, maxX, minZ, maxZ);
            x = pos[0];
            z = pos[1];
        } else {
            int[] pos = pickRandomPositionFromSpawn(level, random, minX, maxX, minZ, maxZ);
            x = pos[0];
            z = pos[1];
        }
        int y = findSafeY(level, x, z);
        if (y == -1) return false;

        BlockPos pos = new BlockPos(x, y, z);
        if (!isSurfaceLocation(dimensionName, level, pos) || !SafetyManager.isSafeTeleportLocation(level, pos) || !isFarEnoughFromExistingPoints(pos, newPoints, dimensionName)) {
            return false;
        }
        newPoints.add(new RTPPoint(x, y, z, dimensionName));
        return true;
    }

    /**
     * Generate RTP points for all allowed dimensions (blocking). Prefer startTickSpreadGenerateRTPPoints.
     */
    public static void generateRTPPoints(MinecraftServer server, int count) {
        startTickSpreadGenerateRTPPoints(server, count);
    }

    /**
     * Start tick-spread RTP generation for all dimensions — avoids server freeze.
     */
    public static void startTickSpreadGenerateRTPPoints(MinecraftServer server, int count) {
        LOGGER.info("Starting tick-spread RTP points generation (count: {})", count);

        List<String> allowedDimensions = new ArrayList<>(EssentialsQXMod.getConfig().rtpAllowedDimensions);
        List<RTPPoint> newPoints = new ArrayList<>();
        int pointsPerDimension = Math.max(1, count / allowedDimensions.size());

        // Notify players
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.literal(LocalizationManager.getMessage("rtp.create_started")));
        }

        int[] dimIndex = new int[] { 0 };
        int[] generatedForDim = new int[] { 0 };
        int[] attemptsForDim = new int[] { 0 };
        int maxAttemptsPerDim = pointsPerDimension * 3;
        ServerLevel[] currentLevel = new ServerLevel[1];
        int[][] currentBounds = new int[1][];

        Runnable runBatch = new Runnable() {
            @Override
            public void run() {
                try {
                    if (dimIndex[0] >= allowedDimensions.size()) {
                        // All dimensions done — finalize
                        rtpPoints.clear();
                        Set<String> uniqueKeys = new HashSet<>();
                        for (RTPPoint point : newPoints) {
                            if (uniqueKeys.add(point.getKey())) {
                                rtpPoints.add(point);
                            }
                        }
                        rebuildDimensionIndexAndKeys();
                        saveRTPPoints(server);
                        int total = rtpPoints.size();
                        LOGGER.info("RTP points generation completed. Total points generated: {}", total);
                        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                            p.sendSystemMessage(Component.literal(LocalizationManager.getMessage("rtp.create_completed", total)));
                        }
                        return;
                    }

                    String dimensionName = allowedDimensions.get(dimIndex[0]);
                    if (currentLevel[0] == null) {
                        currentLevel[0] = server.getLevel(getDimensionKey(dimensionName));
                        if (currentLevel[0] == null) {
                            LOGGER.warn("Dimension {} not available for RTP generation", dimensionName);
                            dimIndex[0]++;
                            server.execute(this);
                            return;
                        }
                        int[] bounds = getWorldBorderBounds(currentLevel[0], dimensionName);
                        if (bounds == null) {
                            LOGGER.warn("World border too small for RTP in dimension {}", dimensionName);
                            dimIndex[0]++;
                            currentLevel[0] = null;
                            server.execute(this);
                            return;
                        }
                        currentBounds[0] = bounds;
                        generatedForDim[0] = 0;
                        attemptsForDim[0] = 0;
                        LOGGER.info("Generating RTP points for dimension {}...", dimensionName);
                    }

                    int minX = currentBounds[0][0], maxX = currentBounds[0][1], minZ = currentBounds[0][2], maxZ = currentBounds[0][3];

                    for (int i = 0; i < GENERATION_BATCH_SIZE && attemptsForDim[0] < maxAttemptsPerDim && generatedForDim[0] < pointsPerDimension; i++) {
                        if (tryOneGenerationAttempt(currentLevel[0], dimensionName, minX, maxX, minZ, maxZ, newPoints)) {
                            generatedForDim[0]++;
                        }
                        attemptsForDim[0]++;
                    }

                    if (generatedForDim[0] >= pointsPerDimension || attemptsForDim[0] >= maxAttemptsPerDim) {
                        LOGGER.info("Completed generation for dimension {}: {} points", dimensionName, generatedForDim[0]);
                        dimIndex[0]++;
                        currentLevel[0] = null;
                    }

                    server.execute(this);
                } catch (Exception e) {
                    LOGGER.error("Error during RTP generation: {}", e.getMessage());
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        p.sendSystemMessage(Component.literal(LocalizationManager.getMessage("rtp.failed")));
                    }
                }
            }
        };
        server.execute(runBatch);
    }

    /** Rebuild dimension index and key set from current rtpPoints (after load or full regenerate). */
    private static void rebuildDimensionIndexAndKeys() {
        dimensionIndex.clear();
        rtpPointKeys.clear();
        for (RTPPoint point : rtpPoints) {
            dimensionIndex.computeIfAbsent(point.dimension, k -> new ArrayList<>()).add(point);
            rtpPointKeys.add(point.getKey());
        }
    }

    private static ResourceKey<Level> getDimensionKey(String dimensionName) {
        return ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION,
            ResourceLocation.parse(dimensionName)
        );
    }

    /** Returns { minX, maxX, minZ, maxZ } or null if world border too small. */
    private static int[] getWorldBorderBounds(ServerLevel level) {
        return getWorldBorderBounds(level, level.dimension().location().toString());
    }

    /**
     * Returns search bounds for RTP. End uses world border (outer islands included).
     */
    private static int[] getWorldBorderBounds(ServerLevel level, String dimensionName) {
        var wb = level.getWorldBorder();
        int minX = (int) Math.ceil(wb.getMinX()) + BORDER_MARGIN;
        int maxX = (int) Math.floor(wb.getMaxX()) - BORDER_MARGIN;
        int minZ = (int) Math.ceil(wb.getMinZ()) + BORDER_MARGIN;
        int maxZ = (int) Math.floor(wb.getMaxZ()) - BORDER_MARGIN;
        if (maxX - minX < MIN_WORLD_BORDER_SIZE || maxZ - minZ < MIN_WORLD_BORDER_SIZE) {
            return null;
        }
        return new int[] { minX, maxX, minZ, maxZ };
    }

    /**
     * Check if a position is far enough from existing RTP points (minimum 50 blocks distance)
     */
    private static boolean isFarEnoughFromExistingPoints(BlockPos pos, List<RTPPoint> existingPoints, String dimensionName) {
        for (RTPPoint existing : existingPoints) {
            if (existing.dimension.equals(dimensionName)) {
                int dx = pos.getX() - existing.x;
                int dz = pos.getZ() - existing.z;
                if (dx * dx + dz * dz < MIN_POINT_DISTANCE_SQ) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Check if position is on surface (has sky access), not in a cave.
     * Nether/End: skip check — Nether has no caves in same sense; End canSeeSky returns false (Minecraft bug).
     */
    private static boolean isSurfaceLocation(String dimensionName, ServerLevel level, BlockPos pos) {
        if (DIMENSION_NETHER.equals(dimensionName) || DIMENSION_END.equals(dimensionName)) {
            return true;
        }
        return level.canSeeSky(pos);
    }

    /**
     * Find safe Y coordinate for RTP (surface only in Overworld/End — no caves)
     * For End, uses narrow Y range (40-70) — main island surface is ~48-50; reduces chunk loads.
     */
    private static int findSafeY(ServerLevel level, int x, int z) {
        String dimensionName = level.dimension().location().toString();
        boolean isNether = DIMENSION_NETHER.equals(dimensionName);
        boolean isEnd = DIMENSION_END.equals(dimensionName);
        int maxHeight;
        int minHeight;
        if (isEnd) {
            maxHeight = END_SEARCH_Y_MAX;
            minHeight = END_SEARCH_Y_MIN;
        } else if (isNether) {
            maxHeight = NETHER_MAX_Y;
            minHeight = 1;
        } else {
            maxHeight = OVERWORLD_MAX_Y;
            minHeight = 1;
        }

        for (int y = maxHeight; y >= minHeight; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockPos below = pos.below();

            if (level.getBlockState(pos).isAir() &&
                level.getBlockState(pos.above()).isAir() &&
                !level.getBlockState(below).isAir() &&
                level.getBlockState(below).blocksMotion() &&
                level.getBlockState(pos.above(2)).isAir()) {

                if (isNether && y <= NETHER_MAX_Y) {
                    boolean hasBedrockAbove = false;
                    for (int checkY = BEDROCK_CHECK_MIN_Y; checkY <= BEDROCK_CHECK_MAX_Y; checkY++) {
                        if (level.getBlockState(new BlockPos(x, checkY, z)).getBlock() == Blocks.BEDROCK) {
                            hasBedrockAbove = true;
                            break;
                        }
                    }
                    if (!hasBedrockAbove) continue;
                }

                if (!isSurfaceLocation(dimensionName, level, pos)) continue;

                return y;
            }
        }
        return -1;
    }

    /**
     * Perform random teleport for player
     */
    public static boolean randomTeleport(ServerPlayer player) {
        UUID playerId = player.getUUID();
        Set<String> usedPoints = playerUsedRTPs.computeIfAbsent(playerId, k -> loadPlayerUsedRTPs(playerId, player.getServer()));

        // Get current player dimension
        String currentDimension = player.level().dimension().location().toString();
        LOGGER.debug("Player {} is in dimension {}", player.getName().getString(), currentDimension);

        // Find unused RTP point in current dimension that is safe (skip unsafe points, try next)
        RTPPoint selectedPoint = null;
        MinecraftServer server = player.getServer();
        if (server == null) return false;

        ServerLevel targetLevel = server.getLevel(getDimensionKey(currentDimension));
        if (targetLevel == null) {
            player.sendSystemMessage(Component.literal(
                LocalizationManager.getMessage("rtp.dimension_unavailable")
            ));
            return false;
        }

        List<RTPPoint> pointsInDimension = dimensionIndex.get(currentDimension);
        if (pointsInDimension != null) {
            for (RTPPoint point : pointsInDimension) {
                if (usedPoints.contains(point.getKey())) continue;
                BlockPos pos = new BlockPos(point.x, point.y, point.z);
                if (!isSurfaceLocation(currentDimension, targetLevel, pos) || !SafetyManager.isSafeTeleportLocation(targetLevel, pos)) {
                    LOGGER.debug("RTP point {} skipped (cave or unsafe), trying next", point.getKey());
                    continue;
                }
                selectedPoint = point;
                break;
            }
        }

        if (selectedPoint == null) {
            LOGGER.debug("No safe RTP point in dimension {} for player {}, starting tick-spread search", currentDimension, player.getName().getString());
            int[] bounds = getWorldBorderBounds(targetLevel, currentDimension);
            if (bounds == null) {
                player.sendSystemMessage(Component.literal(
                    LocalizationManager.getMessage("rtp.no_points_in_dimension").replace("%dimension%", currentDimension)
                ));
                return false;
            }
            startTickSpreadPointSearch(server, player, currentDimension, targetLevel,
                bounds[0], bounds[1], bounds[2], bounds[3]);
            return true;  // Search started; teleport/error will be sent when done
        }

        // Perform teleport (selectedPoint is already verified safe); reuse targetLevel
        try {
            // Store values for lambda
            final ServerLevel finalLevel = targetLevel;
            final int finalX = selectedPoint.x;
            final int finalY = selectedPoint.y;
            final int finalZ = selectedPoint.z;
            final String finalDimension = selectedPoint.dimension;
            final String finalKey = selectedPoint.getKey();
            final String playerName = player.getName().getString();

            Runnable teleportAction = () -> {
                try {
                    var finalPos = new net.minecraft.world.phys.Vec3(finalX + 0.5, finalY, finalZ + 0.5);
                    player.teleportTo(finalLevel, finalPos.x, finalPos.y, finalPos.z,
                        player.getYRot(), player.getXRot());

                    player.sendSystemMessage(Component.literal(
                        LocalizationManager.getMessage("rtp.success")
                    ));

                    LOGGER.info("Player {} RTP to {}, {}, {} in {}", playerName, finalX, finalY, finalZ, finalDimension);

                } catch (Exception e) {
                    player.sendSystemMessage(Component.literal(
                        LocalizationManager.getMessage("rtp.failed")
                    ));
                    LOGGER.error("Error during RTP for player {}: {}", playerName, e.getMessage());
                }
            };

            TeleportManager.startTeleportCountdown(player, () -> {
                teleportAction.run();
                usedPoints.add(finalKey);
                savePlayerUsedRTPs(playerId, server);
            });

        } catch (Exception e) {
            player.sendSystemMessage(Component.literal(
                LocalizationManager.getMessage("rtp.failed")
            ));
            LOGGER.error("Error preparing RTP for player {}: {}", player.getName().getString(), e.getMessage());
            return false;
        }

        return true;
    }

    /** Min/max distance from player when searching for subsequent RTP - 150-300 chunks = 2400-4800 blocks */
    private static final int MIN_DISTANCE_FROM_PLAYER = 2400;
    private static final int MAX_DISTANCE_FROM_PLAYER = 4800;

    /**
     * Pick random (x, z) around dimension spawn — for first RTP in Overworld/Nether.
     * End uses pickRandomEndPosition (annulus from 0,0) instead.
     */
    private static int[] pickRandomPositionFromSpawn(ServerLevel level, java.util.Random random,
            int minX, int maxX, int minZ, int maxZ) {
        var spawn = level.getSharedSpawnPos();
        int spawnX = spawn.getX();
        int spawnZ = spawn.getZ();
        double angle = random.nextDouble() * 2 * Math.PI;
        double radius = MIN_DISTANCE_FROM_PLAYER + random.nextDouble() * (MAX_DISTANCE_FROM_PLAYER - MIN_DISTANCE_FROM_PLAYER);
        int x = spawnX + (int) (Math.cos(angle) * radius);
        int z = spawnZ + (int) (Math.sin(angle) * radius);
        x = Math.max(minX, Math.min(maxX, x));
        z = Math.max(minZ, Math.min(maxZ, z));
        return new int[] { x, z };
    }

    /**
     * Pick random (x, z) for End — annulus 500–1500 blocks from center (main + outer islands).
     */
    private static int[] pickRandomEndPosition(java.util.Random random, int minX, int maxX, int minZ, int maxZ) {
        double angle = random.nextDouble() * 2 * Math.PI;
        double radius = MIN_DISTANCE_FROM_PLAYER + random.nextDouble() * (MAX_DISTANCE_FROM_PLAYER - MIN_DISTANCE_FROM_PLAYER);
        int x = (int) (Math.cos(angle) * radius);
        int z = (int) (Math.sin(angle) * radius);
        x = Math.max(minX, Math.min(maxX, x));
        z = Math.max(minZ, Math.min(maxZ, z));
        return new int[] { x, z };
    }

    /**
     * Pick random (x, z) around player — for subsequent RTP (not first in dimension).
     * Clamps to dimension bounds (world border in all dimensions, including End outer islands).
     */
    private static int[] pickRandomPositionAroundPlayer(java.util.Random random, int playerX, int playerZ,
            String dimensionName, int minX, int maxX, int minZ, int maxZ) {
        double angle = random.nextDouble() * 2 * Math.PI;
        double radius = MIN_DISTANCE_FROM_PLAYER + random.nextDouble() * (MAX_DISTANCE_FROM_PLAYER - MIN_DISTANCE_FROM_PLAYER);
        int x = playerX + (int) (Math.cos(angle) * radius);
        int z = playerZ + (int) (Math.sin(angle) * radius);
        x = Math.max(minX, Math.min(maxX, x));
        z = Math.max(minZ, Math.min(maxZ, z));
        return new int[] { x, z };
    }

    /**
     * Try a single random RTP attempt. Returns valid point or null.
     * @param excludeKeys optional — player's used RTP keys (from used_rtp.json); never return these
     * @param playerCenterX optional — if set with playerCenterZ, search around player (subsequent RTP)
     */
    private static RTPPoint tryOneRTPAttempt(ServerLevel level, String dimensionName,
            int minX, int maxX, int minZ, int maxZ, MinecraftServer server, Set<String> excludeKeys,
            Integer playerCenterX, Integer playerCenterZ) {
        var random = ThreadLocalRandom.current();
        int x, z;
        if (playerCenterX != null && playerCenterZ != null) {
            int[] pos = pickRandomPositionAroundPlayer(random, playerCenterX, playerCenterZ, dimensionName, minX, maxX, minZ, maxZ);
            x = pos[0];
            z = pos[1];
        } else if (DIMENSION_END.equals(dimensionName)) {
            int[] pos = pickRandomEndPosition(random, minX, maxX, minZ, maxZ);
            x = pos[0];
            z = pos[1];
        } else {
            int[] pos = pickRandomPositionFromSpawn(level, random, minX, maxX, minZ, maxZ);
            x = pos[0];
            z = pos[1];
        }
        int y = findSafeY(level, x, z);
        if (y == -1) return null;

        BlockPos pos = new BlockPos(x, y, z);
        if (!isSurfaceLocation(dimensionName, level, pos) || !SafetyManager.isSafeTeleportLocation(level, pos)) {
            return null;
        }
        RTPPoint newPoint = new RTPPoint(x, y, z, dimensionName);
        String key = newPoint.getKey();
        if (rtpPointKeys.contains(key)) return null;
        if (excludeKeys != null && excludeKeys.contains(key)) return null;  // player already used this point

        rtpPoints.add(newPoint);
        dimensionIndex.computeIfAbsent(dimensionName, k -> new ArrayList<>()).add(newPoint);
        rtpPointKeys.add(key);
        if (server != null) {
            saveRTPPoints(server);
        }
        LOGGER.info("Added new RTP point: {} in {}", key, dimensionName);
        return newPoint;
    }

    /**
     * Try to generate one new RTP point (blocking, used by /rtp create). For player RTP use tick-spread.
     */
    private static RTPPoint tryGenerateOnePoint(MinecraftServer server, ServerLevel level, String dimensionName) {
        int[] bounds = getWorldBorderBounds(level, dimensionName);
        if (bounds == null) return null;

        int minX = bounds[0], maxX = bounds[1], minZ = bounds[2], maxZ = bounds[3];
        int attempts = DIMENSION_END.equals(dimensionName) ? END_NEW_POINT_ATTEMPTS : NEW_POINT_ATTEMPTS;

        for (int attempt = 0; attempt < attempts; attempt++) {
            RTPPoint p = tryOneRTPAttempt(level, dimensionName, minX, maxX, minZ, maxZ, server, null, null, null);
            if (p != null) return p;
        }
        return null;
    }

    /** Active tick-spread searches: player UUID -> true (prevents double search) */
    private static final Set<UUID> activePointSearches = ConcurrentHashMap.newKeySet();

    /**
     * Start tick-spread RTP point search — runs in small batches per tick to avoid server freeze.
     * First RTP in dimension: search from spawn. Subsequent: search around player position.
     */
    private static void startTickSpreadPointSearch(MinecraftServer server, ServerPlayer player,
            String dimensionName, ServerLevel targetLevel, int minX, int maxX, int minZ, int maxZ) {
        UUID playerId = player.getUUID();
        if (!activePointSearches.add(playerId)) {
            player.sendSystemMessage(Component.literal(LocalizationManager.getMessage("rtp.failed")));
            return;
        }
        player.sendSystemMessage(Component.literal(LocalizationManager.getMessage("rtp.fallback_search")));

        Set<String> usedPoints = playerUsedRTPs.computeIfAbsent(playerId, k -> loadPlayerUsedRTPs(playerId, server));
        boolean firstRTPInDimension = usedPoints.stream().noneMatch(k -> k.startsWith(dimensionName + ":"));
        int playerX = player.blockPosition().getX();
        int playerZ = player.blockPosition().getZ();
        Integer centerX = firstRTPInDimension ? null : playerX;
        Integer centerZ = firstRTPInDimension ? null : playerZ;

        int maxAttempts = DIMENSION_END.equals(dimensionName) ? END_NEW_POINT_ATTEMPTS : NEW_POINT_ATTEMPTS;
        int[] attemptCounter = new int[] { 0 };

        Runnable runBatch = new Runnable() {
            @Override
            public void run() {
                if (!player.isAlive() || !server.getPlayerList().getPlayers().contains(player)) {
                    activePointSearches.remove(playerId);
                    return;
                }
                for (int i = 0; i < POINT_SEARCH_BATCH_SIZE; i++) {
                    RTPPoint p = tryOneRTPAttempt(targetLevel, dimensionName, minX, maxX, minZ, maxZ, server, usedPoints, centerX, centerZ);
                    if (p != null) {
                        activePointSearches.remove(playerId);
                        finishRTPWithPoint(player, p, targetLevel, playerId);
                        return;
                    }
                    attemptCounter[0]++;
                    if (attemptCounter[0] >= maxAttempts) {
                        activePointSearches.remove(playerId);
                        player.sendSystemMessage(Component.literal(
                            LocalizationManager.getMessage("rtp.no_points_in_dimension").replace("%dimension%", dimensionName)));
                        return;
                    }
                }
                server.execute(this);
            }
        };
        server.execute(runBatch);
    }

    private static void finishRTPWithPoint(ServerPlayer player, RTPPoint selectedPoint,
            ServerLevel targetLevel, UUID playerId) {
        Set<String> usedPoints = playerUsedRTPs.computeIfAbsent(playerId, k -> loadPlayerUsedRTPs(playerId, player.getServer()));
        MinecraftServer server = player.getServer();
        if (server == null) return;

        final int finalX = selectedPoint.x;
        final int finalY = selectedPoint.y;
        final int finalZ = selectedPoint.z;
        final String finalDimension = selectedPoint.dimension;
        final String finalKey = selectedPoint.getKey();

        Runnable teleportAction = () -> {
            try {
                var finalPos = new net.minecraft.world.phys.Vec3(finalX + 0.5, finalY, finalZ + 0.5);
                player.teleportTo(targetLevel, finalPos.x, finalPos.y, finalPos.z, player.getYRot(), player.getXRot());
                player.sendSystemMessage(Component.literal(LocalizationManager.getMessage("rtp.success")));
                LOGGER.info("Player {} RTP to {}, {}, {} in {}", player.getName().getString(), finalX, finalY, finalZ, finalDimension);
            } catch (Exception e) {
                player.sendSystemMessage(Component.literal(LocalizationManager.getMessage("rtp.failed")));
                LOGGER.error("Error during RTP: {}", e.getMessage());
            }
        };

        TeleportManager.startTeleportCountdown(player, () -> {
            teleportAction.run();
            usedPoints.add(finalKey);
            savePlayerUsedRTPs(playerId, server);
        });
    }

    /**
     * Find new RTP point as fallback
     */
    private static RTPPoint findNewRTPPoint(MinecraftServer server) {
        return findNewRTPPoint(server, null);
    }

    /**
     * Find new RTP point as fallback, preferring specified dimension
     */
    private static RTPPoint findNewRTPPoint(MinecraftServer server, String preferredDimension) {
        List<String> allowedDimensions = EssentialsQXMod.getConfig().rtpAllowedDimensions;
        List<String> dimensionPriority = new ArrayList<>(allowedDimensions);
        if (preferredDimension != null && allowedDimensions.contains(preferredDimension)) {
            dimensionPriority.remove(preferredDimension);
            dimensionPriority.add(0, preferredDimension);
        }

        for (String dimensionName : dimensionPriority) {
            try {
                ServerLevel level = server.getLevel(getDimensionKey(dimensionName));
                if (level == null) continue;
                RTPPoint point = tryGenerateOnePoint(server, level, dimensionName);
                if (point != null) return point;
            } catch (Exception e) {
                LOGGER.warn("Error finding new RTP point in dimension {}: {}", dimensionName, e.getMessage());
            }
        }
        return null;
    }

    /**
     * Find new RTP point in specific dimension only
     */
    private static RTPPoint findNewRTPPointInDimension(MinecraftServer server, String dimensionName) {
        if (!EssentialsQXMod.getConfig().rtpAllowedDimensions.contains(dimensionName)) {
            LOGGER.warn("Dimension {} is not allowed for RTP", dimensionName);
            return null;
        }
        try {
            ServerLevel level = server.getLevel(getDimensionKey(dimensionName));
            if (level == null) {
                LOGGER.warn("Dimension {} not available for RTP", dimensionName);
                return null;
            }
            return tryGenerateOnePoint(server, level, dimensionName);
        } catch (Exception e) {
            LOGGER.warn("Error finding new RTP point in dimension {}: {}", dimensionName, e.getMessage());
            return null;
        }
    }

    /**
     * Update RTP points (admin command). Uses tick-spread generation; completion message sent when done.
     */
    public static void updateRTPPoints(MinecraftServer server, int additionalCount) {
        LOGGER.info("Starting RTP points update (additional count: {})", additionalCount);
        startTickSpreadGenerateRTPPoints(server, rtpPoints.size() + additionalCount);
    }

    /**
     * Load RTP points from file
     */
    private static void loadRTPPoints(MinecraftServer server) {
        try {
            Path rtpFile = getRTPFile(server);
            if (Files.exists(rtpFile)) {
                String json = Files.readString(rtpFile);
                Type type = new TypeToken<List<RTPPoint>>(){}.getType();
                List<RTPPoint> loadedPoints = GSON.fromJson(json, type);
                if (loadedPoints != null) {
                    rtpPoints = loadedPoints;
                    LOGGER.info("Loaded {} RTP points from file", rtpPoints.size());
                } else {
                    rtpPoints = new ArrayList<>();
                }
                rebuildDimensionIndexAndKeys();
            } else {
                // File doesn't exist yet, will be created when admin runs /rtp update or /tpr update
                rtpPoints = new ArrayList<>();
                rebuildDimensionIndexAndKeys();
                LOGGER.info("RTP points file not found. Use /rtp create or /tpr create to generate points first.");
            }
            rtpPointsLoaded = true;
        } catch (Exception e) {
            LOGGER.error("Error loading RTP points: {}", e.getMessage());
            rtpPoints = new ArrayList<>();
            rebuildDimensionIndexAndKeys();
            rtpPointsLoaded = true;
        }
    }

    /**
     * Save RTP points to file
     */
    private static void saveRTPPoints(MinecraftServer server) {
        try {
            Path rtpFile = getRTPFile(server);
            Files.createDirectories(rtpFile.getParent());
            String json = GSON.toJson(rtpPoints);
            Files.writeString(rtpFile, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            LOGGER.error("Error saving RTP points: {}", e.getMessage());
        }
    }

    /**
     * Load player used RTP points
     */
    private static Set<String> loadPlayerUsedRTPs(UUID playerId, MinecraftServer server) {
        try {
            Path usedFile = getPlayerUsedRTPFile(playerId, server);
            if (Files.exists(usedFile)) {
                String json = Files.readString(usedFile);
                Type type = new TypeToken<Set<String>>(){}.getType();
                Set<String> usedPoints = GSON.fromJson(json, type);
                return usedPoints != null ? usedPoints : new HashSet<>();
            }
        } catch (Exception e) {
            LOGGER.warn("Error loading used RTP points for player {}: {}", playerId, e.getMessage());
        }
        return new HashSet<>();
    }

    /**
     * Save player used RTP points
     */
    public static void savePlayerUsedRTPs(UUID playerId, MinecraftServer server) {
        try {
            Set<String> usedPoints = playerUsedRTPs.get(playerId);
            if (usedPoints != null && !usedPoints.isEmpty()) {
                Path usedFile = getPlayerUsedRTPFile(playerId, server);
                Files.createDirectories(usedFile.getParent());
                String json = GSON.toJson(usedPoints);
                Files.writeString(usedFile, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
        } catch (Exception e) {
            LOGGER.error("Error saving used RTP points for player {}: {}", playerId, e.getMessage());
        }
    }

    /**
     * Get RTP file path
     */
    private static Path getRTPFile(MinecraftServer server) {
        Path worldPath = server.getWorldPath(LevelResource.ROOT);
        Path essentialsqxFolder = worldPath.resolve(EssentialsQXMod.MOD_ID);
        return essentialsqxFolder.resolve("random_teleports.json");
    }

    /**
     * Get player used RTP file path
     */
    private static Path getPlayerUsedRTPFile(UUID playerId, MinecraftServer server) {
        Path worldPath = server.getWorldPath(LevelResource.ROOT);
        Path essentialsqxFolder = worldPath.resolve(EssentialsQXMod.MOD_ID);
        Path usersFolder = essentialsqxFolder.resolve("users");
        Path playerFolder = usersFolder.resolve(playerId.toString());
        return playerFolder.resolve("used_rtp.json");
    }

    /**
     * Save all player RTP data on server shutdown
     */
    public static void saveAllRTPData(MinecraftServer server) {
        // RTP points are already saved in generateRTPPoints
        // Save all player used RTP data
        for (Map.Entry<UUID, Set<String>> entry : playerUsedRTPs.entrySet()) {
            savePlayerUsedRTPs(entry.getKey(), server);
        }
        LOGGER.info("Saved all RTP player data");
    }

    /**
     * Reset all RTP data (admin command)
     */
    public static void resetAllRTPData(MinecraftServer server) {
        // Clear RTP points and indexes
        rtpPoints.clear();
        dimensionIndex.clear();
        rtpPointKeys.clear();

        // Delete RTP points file
        try {
            Path rtpFile = getRTPFile(server);
            Files.deleteIfExists(rtpFile);
            LOGGER.info("Deleted RTP points file");
        } catch (Exception e) {
            LOGGER.warn("Failed to delete RTP points file: {}", e.getMessage());
        }

        // Clear and delete all player used RTP files
        for (Map.Entry<UUID, Set<String>> entry : playerUsedRTPs.entrySet()) {
            UUID playerId = entry.getKey();
            try {
                Path usedFile = getPlayerUsedRTPFile(playerId, server);
                Files.deleteIfExists(usedFile);
                LOGGER.debug("Deleted used RTP file for player {}", playerId);
            } catch (Exception e) {
                LOGGER.warn("Failed to delete used RTP file for player {}: {}", playerId, e.getMessage());
            }
        }

        // Clear player data
        playerUsedRTPs.clear();

        LOGGER.info("Reset all RTP data");
    }

    /**
     * Get RTP points count
     */
    public static int getRTPPointsCount() {
        return rtpPoints.size();
    }

    /**
     * RTP point data class
     */
    public static class RTPPoint {
        public int x, y, z;
        public String dimension;

        public RTPPoint() {}

        public RTPPoint(int x, int y, int z, String dimension) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dimension = dimension;
        }

        public String getKey() {
            return dimension + ":" + x + "," + y + "," + z;
        }
    }
}