package com.essentials.qx;

import java.util.Arrays;
import java.util.List;

/**
 * Configuration manager for EssentialsQX mod.
 * Fields ending with _msg are descriptions only and are ignored by the mod logic.
 */
public class ConfigManager {
    public boolean debug = true;
    public String debug_msg = "Debug mode (logs to console)";

    public int teleportDelaySeconds = 5;
    public String teleportDelaySeconds_msg = "Teleport delay in seconds";

    public boolean allowMovementDuringTeleport = true;
    public String allowMovementDuringTeleport_msg = "Allow movement during teleport";

    public boolean allowDamageDuringTeleport = false;
    public String allowDamageDuringTeleport_msg = "Allow taking damage during teleport";

    public int teleportCooldownSeconds = 60;
    public String teleportCooldownSeconds_msg = "Teleport cooldown in seconds";

    public boolean allowOperatorsBypassTeleportDelay = true;
    public String allowOperatorsBypassTeleportDelay_msg = "Operators bypass teleport delay";

    public boolean allowOperatorsBypassTeleportCooldown = true;
    public String allowOperatorsBypassTeleportCooldown_msg = "Operators bypass teleport cooldown";

    public String language = "ru";
    public String language_msg = "Language: ru or en";

    public int maxHomesPerPlayer = 3;
    public String maxHomesPerPlayer_msg = "Maximum homes per player";

    public List<String> allowedHomeDimensions = Arrays.asList(
        "minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"
    );
    public String allowedHomeDimensions_msg = "Dimensions where homes can be created";

    public boolean allowOperatorsBypassHomeLimit = true;
    public String allowOperatorsBypassHomeLimit_msg = "Operators bypass home limit";

    public int rtpPointsCount = 10;
    public String rtpPointsCount_msg = "Number of RTP points when creating (/rtp create)";

    public List<String> rtpAllowedDimensions = Arrays.asList(
        "minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"
    );
    public String rtpAllowedDimensions_msg = "Dimensions for RTP";

    public int tpaRequestTimeoutSeconds = 15;
    public String tpaRequestTimeoutSeconds_msg = "TPA request timeout in seconds";

    public int dayDurationTicks = 36000;
    public String dayDurationTicks_msg = "Day duration in ticks (30 min = 36000)";

    public int nightDurationTicks = 12000;
    public String nightDurationTicks_msg = "Night duration in ticks (10 min = 12000)";

    /** /qxdump — CSV dumps into world/essentialsqx/dumps/ (see essentialsqx.json) */
    public boolean dumpEnabled = true;
    public String dumpEnabled_msg = "Enable /qxdump commands";

    public boolean dumpRequireOp = true;
    public String dumpRequireOp_msg = "Require permission level 2 (OP) for /qxdump";

    public boolean dumpAllowConsole = true;
    public String dumpAllowConsole_msg = "Allow dedicated-server console to run /qxdump";

    public int dumpMaxItems = 10000;
    public String dumpMaxItems_msg = "Maximum rows per CSV file";

    public ConfigManager() {}
}