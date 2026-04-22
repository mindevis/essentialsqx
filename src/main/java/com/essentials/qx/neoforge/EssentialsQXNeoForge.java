package com.essentials.qx.neoforge;

import com.essentials.qx.EssentialsQXMod;
import com.essentials.qx.HelpManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.fml.loading.FMLPaths;

@Mod(EssentialsQXMod.MOD_ID)
public final class EssentialsQXNeoForge {

    public EssentialsQXNeoForge(IEventBus modBus) {
        EssentialsQXMod.init(FMLPaths.CONFIGDIR.get());
        HelpManager.setPayloadSender((player, payload) ->
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload));

        modBus.addListener(this::registerPayloadHandlers);

        var forgeBus = net.neoforged.neoforge.common.NeoForge.EVENT_BUS;
        forgeBus.addListener(this::onServerStarted);
        forgeBus.addListener(this::onServerStopping);
        forgeBus.addListener(this::onPlayerJoin);
        forgeBus.addListener(this::onPlayerLogout);
        forgeBus.addListener(this::onRegisterCommands);
        forgeBus.addListener(this::onServerTick);
        forgeBus.addListener(this::onLevelTick);

        if (FMLEnvironment.dist.isClient()) {
            EssentialsQXNeoForgeClient.init();
        }
    }

    private void registerPayloadHandlers(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(
            HelpManager.TYPE,
            HelpManager.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                var player = context.player();
                if (player != null) {
                    HelpManager.onClientReceive(payload.json());
                }
            })
        );
    }

    private void onServerStarted(ServerStartedEvent event) {
        EssentialsQXMod.onServerStarted(event.getServer());
    }

    private void onServerStopping(ServerStoppingEvent event) {
        EssentialsQXMod.onServerStopping(event.getServer());
    }

    private void onPlayerJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            EssentialsQXMod.onPlayerJoin(player);
        }
    }

    private void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            EssentialsQXMod.onPlayerQuit(player);
        }
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        EssentialsQXMod.registerCommands(
            event.getDispatcher(),
            event.getBuildContext(),
            event.getCommandSelection()
        );
    }

    private void onServerTick(ServerTickEvent.Post event) {
        EssentialsQXMod.onServerTick(event.getServer());
    }

    private void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            com.essentials.qx.TimeManager.onLevelTick(serverLevel);
        }
    }
}
