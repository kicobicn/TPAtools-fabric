package com.kicobicn.TPATools;

import com.kicobicn.TPATools.Commands.*;
import com.kicobicn.TPATools.chat.ChatHandler;
import com.kicobicn.TPATools.config.ModConfigs;
import com.kicobicn.TPATools.database.DatabaseManager;
import com.kicobicn.TPATools.util.ModUtils;
import com.kicobicn.TPATools.util.TeleportDelayManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TPATools implements ModInitializer {
    public static final String MODID = "tpatools";
    public static final Logger LOGGER = LoggerFactory.getLogger("TPATools");

    @Override
    public void onInitialize() {
        LOGGER.info("TPATools mod initializing...");

        // 注册服务器事件
        ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
        // 注意：数据库连接必须在所有 save*() 之后才能关闭，因此放到 SERVER_STOPPED
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> DatabaseManager.close());

        // 注册连接事件
        ServerPlayConnectionEvents.JOIN.register((handler, server, client) ->
                ModConfigs.DebugLog.info("Player {} joined the server",
                        handler.getPlayer().getName().getString()));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();
            if (player == null) {
                return;
            }
            // 玩家退出时清理待处理传送与 TPA 请求，避免持有已下线玩家
            TeleportDelayManager.clear(player.getUUID());
            TPAHandler.onPlayerDisconnect(player);
        });

        // 注册命令
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            TPAHandler.onRegisterCommands(dispatcher, registryAccess);
            HomeHandler.onRegisterCommands(dispatcher, registryAccess);
            BackHandler.onRegisterCommands(dispatcher, registryAccess);
            GraveHandler.onRegisterCommands(dispatcher, registryAccess);
            RTPHandler.onRegisterCommands(dispatcher, registryAccess);
            WarpHandler.onRegisterCommands(dispatcher, registryAccess);
            ModConfigs.onRegisterCommands(dispatcher, registryAccess);
        });

        // 注册其他事件
        ChatHandler.registerEvents();
        ModUtils.registerEvents();

        LOGGER.info("TPATools mod initialized successfully");
    }

    private void onServerStarting(MinecraftServer server) {
        LOGGER.info("TPATools mod: Server starting...");

        // 加载配置
        ModConfigs.setServer(server);
        ModConfigs.loadConfigValues();
        ModConfigs.saveConfigValues();

        // 初始化数据库
        DatabaseManager.initialize();
    }

    private void onServerStopping(MinecraftServer server) {
        LOGGER.info("TPATools mod: Server stopping...");
        // 玩家数据由 ModUtils 注册的 SERVER_STOPPING 监听器保存；
        // 数据库连接在 SERVER_STOPPED 关闭。
        ModConfigs.saveConfigValues();
    }
}
