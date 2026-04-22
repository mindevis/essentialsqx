package com.essentials.qx;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages location safety checks
 */
public class SafetyManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("EssentialsQX-Safety");

    /**
     * Check if the player's current location is safe for spawn
     */
    public static boolean isSafeSpawnLocation(ServerPlayer player) {
        try {
            ServerLevel level = player.serverLevel();
            BlockPos playerPos = player.blockPosition();

            // Check if player is in water or lava
            FluidState fluidState = level.getFluidState(playerPos);
            if (fluidState.is(FluidTags.WATER) || fluidState.is(FluidTags.LAVA)) {
                return false;
            }

            // Check if there's solid ground beneath the player (check 1 block down)
            BlockPos groundPos = playerPos.below();
            BlockState groundBlock = level.getBlockState(groundPos);

            // Must have a solid block that blocks movement (not air, water, lava, etc.)
            if (groundBlock.isAir() || !groundBlock.blocksMotion()) {
                return false;
            }

            // Optional: Check a few blocks below to ensure stability
            for (int i = 1; i <= 3; i++) {
                BlockPos checkPos = playerPos.below(i);
                BlockState checkBlock = level.getBlockState(checkPos);

                // If we hit air or non-solid block too soon, it's not stable
                if (checkBlock.isAir() || !checkBlock.blocksMotion()) {
                    return false;
                }

                // If we find bedrock or similar, it's definitely stable
                if (checkBlock.getBlock().getDescriptionId().contains("bedrock")) {
                    break;
                }
            }

            return true;

        } catch (Exception e) {
            LOGGER.warn("Error checking spawn location safety: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if a teleport destination location is safe
     */
    public static boolean isSafeTeleportLocation(ServerLevel level, BlockPos pos) {
        try {
            // Check if player would spawn inside a block
            BlockState blockAtFeet = level.getBlockState(pos);
            if (!blockAtFeet.isAir()) {
                return false;
            }

            // Check if player would spawn inside a block (upper body)
            BlockState blockAtHead = level.getBlockState(pos.above());
            if (!blockAtHead.isAir()) {
                return false;
            }

            // Check for liquids at spawn position
            FluidState fluidAtFeet = level.getFluidState(pos);
            if (fluidAtFeet.is(FluidTags.WATER) || fluidAtFeet.is(FluidTags.LAVA)) {
                return false;
            }

            // Check for liquids at head position
            FluidState fluidAtHead = level.getFluidState(pos.above());
            if (fluidAtHead.is(FluidTags.WATER) || fluidAtHead.is(FluidTags.LAVA)) {
                return false;
            }

            // Check if there's solid ground beneath the feet
            BlockPos groundPos = pos.below();
            BlockState groundBlock = level.getBlockState(groundPos);
            if (groundBlock.isAir() || !groundBlock.blocksMotion()) {
                return false;
            }

            // Check for open space above (2 blocks of air)
            for (int i = 1; i <= 2; i++) {
                BlockState aboveBlock = level.getBlockState(pos.above(i));
                if (!aboveBlock.isAir()) {
                    return false;
                }
            }

            return true;

        } catch (Exception e) {
            LOGGER.warn("Error checking teleport location safety: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Find a safe teleport location near the given position
     * Searches up and down to find a safe spot
     */
    public static BlockPos findSafeTeleportLocation(ServerLevel level, BlockPos originalPos) {
        // First, try the original position
        if (isSafeTeleportLocation(level, originalPos)) {
            return originalPos;
        }

        // Search upwards (useful if spawn is underground)
        for (int y = 1; y <= 10; y++) {
            BlockPos testPos = originalPos.above(y);
            if (isSafeTeleportLocation(level, testPos)) {
                return testPos;
            }
        }

        // Search downwards (useful if spawn is in the air)
        for (int y = 1; y <= 10; y++) {
            BlockPos testPos = originalPos.below(y);
            if (isSafeTeleportLocation(level, testPos)) {
                return testPos;
            }
        }

        // If no safe location found, return null
        return null;
    }
}