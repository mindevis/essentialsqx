package com.essentials.qx.neoforge;

import com.essentials.qx.HelpManager;
import net.minecraft.client.Minecraft;

/**
 * Client-only initializer. Registers callback for QxHelpScreen.
 * Payload receiver is registered in EssentialsQXNeoForge via RegisterPayloadHandlersEvent.
 */
public final class EssentialsQXNeoForgeClient {

    public static void init() {
        HelpManager.setClientOpenScreenCallback(json ->
            Minecraft.getInstance().setScreen(new QxHelpScreen(json)));
    }
}
