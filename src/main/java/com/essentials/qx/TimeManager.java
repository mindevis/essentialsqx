package com.essentials.qx;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages custom day/night cycle durations.
 * Does not affect sleep in bed - sleep uses vanilla advancement to morning.
 */
public final class TimeManager {

    private static final int VANILLA_DAY_PHASE_TICKS = 12000; // 0-12000 = day, 12000-24000 = night
    private static final Map<Level, Double> accumulatedTime = new HashMap<>();

    public static void init() {
    }

    /** Called every level tick - register from NeoForge LevelTickEvent */
    public static void onLevelTick(ServerLevel level) {
        ConfigManager config = EssentialsQXMod.getConfig();
        if (config == null) {
            return;
        }

        // Only modify time when doDaylightCycle is enabled (vanilla behavior)
        if (!level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
            return;
        }

        int dayDuration = Math.max(1, config.dayDurationTicks);
        int nightDuration = Math.max(1, config.nightDurationTicks);

        // Vanilla: same duration for day and night = 12000 ticks each
        if (dayDuration == VANILLA_DAY_PHASE_TICKS && nightDuration == VANILLA_DAY_PHASE_TICKS) {
            return; // Use vanilla behavior
        }

        long currentTime = level.getDayTime();
        // Vanilla just added 1, so previous time was currentTime - 1
        long previousTime = currentTime - 1;
        long dayTimeValue = previousTime % 24000;

        // Determine phase: 0-12000 = day, 12000-24000 = night
        boolean isDay = dayTimeValue < VANILLA_DAY_PHASE_TICKS;
        double advancePerTick = isDay
            ? (double) VANILLA_DAY_PHASE_TICKS / dayDuration
            : (double) VANILLA_DAY_PHASE_TICKS / nightDuration;

        // Accumulate fractional time
        double accumulated = accumulatedTime.getOrDefault(level, 0.0);
        accumulated += advancePerTick;

        int toAdd = (int) Math.floor(accumulated);
        accumulated -= toAdd;
        accumulatedTime.put(level, accumulated);

        if (toAdd == 0) {
            // Need to undo vanilla's +1 since we're not adding anything this tick
            level.setDayTime(previousTime);
            return;
        }

        // Replace vanilla's +1 with our custom increment
        long newTime = previousTime + toAdd;
        level.setDayTime(newTime);
    }

    /**
     * Clears accumulated time when server stops to prevent memory leaks.
     */
    public static void clear() {
        accumulatedTime.clear();
    }
}
