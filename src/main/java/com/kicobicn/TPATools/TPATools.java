package com.kicobicn.TPATools;

import com.kicobicn.TPATools.Commands.*;
import com.kicobicn.TPATools.chat.ChatHandler;
import com.kicobicn.TPATools.config.ModConfigs;
import com.kicobicn.TPATools.database.DatabaseManager;
import com.kicobicn.TPATools.util.ModUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;

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
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);

        // 注册连接事件
        ServerPlayConnectionEvents.JOIN.register((handler, server, client) -> {
            // 玩家加入事件，可以用来初始化玩家状态
            ModConfigs.DebugLog.info("Player {} joined the server", handler.getPlayer().getName().getString());
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
        DatabaseManager.close();
        ModConfigs.saveConfigValues();
    }

    private void onServerTick(MinecraftServer server) {
        //暂不进行处理
    }
}