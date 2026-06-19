package com.kicobicn.TPATools.Commands;

import com.kicobicn.TPATools.config.ModConfigs;
import com.kicobicn.TPATools.util.ModUtils;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BackHandler {
    private static final Map<UUID, PlayerPosition> previousPositions = new HashMap<>();

    public static class PlayerPosition {
        public ResourceLocation dimension;
        public final double x, y, z;
        public final float yRot, xRot;

        public PlayerPosition(ServerLevel level, double x, double y, double z, float yRot, float xRot) {
            this.dimension = level.dimension().location();
            this.x = x;
            this.y = y;
            this.z = z;
            this.yRot = yRot;
            this.xRot = xRot;
        }
    }

    public static void onRegisterCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {

        dispatcher.register(
                Commands.literal("back")
                        .requires(source -> ModConfigs.checkCommandPermission(source, "back"))
                        .executes(context -> {
                            try {
                                ServerPlayer player = context.getSource().getPlayerOrException();
                                ModConfigs.DebugLog.info("Executing /back command for player: {}", player.getName().getString());
                                return teleportBack(player);
                            } catch (Exception e) {
                                ModConfigs.DebugLog.error("Unexpected error in /back command: ", e);
                                context.getSource().sendFailure(Component.literal("An unexpected error occurred."));
                                return 0;
                            }
                        })
        );
    }

    public static void recordPosition(ServerPlayer player) {
        previousPositions.put(player.getUUID(), new PlayerPosition(
                player.serverLevel(),
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot()
        ));
        ModConfigs.DebugLog.log("Recorded position for {}: dimension={}, x={}, y={}, z={}",
                player.getName().getString(), player.serverLevel().dimension().location(),
                player.getX(), player.getY(), player.getZ());
    }

    private static int teleportBack(ServerPlayer player) {
        PlayerPosition pos = previousPositions.get(player.getUUID());
        if (pos == null) {
            player.sendSystemMessage(ModUtils.translateWithFallback(
                    "command.tpatool.back.no_position", "No previous position recorded!"
            ));
            return 0;
        }
        ServerLevel level = player.getServer().getLevel(ResourceKey.create(
                Registries.DIMENSION, pos.dimension));
        if (level == null) {
            player.sendSystemMessage(ModUtils.translateWithFallback(
                    "command.tpatool.back.invalid_dimension", "Invalid dimension for previous position!"
            ));
            previousPositions.remove(player.getUUID());
            return 0;
        }
        recordPosition(player);
        // 使用支持骑乘链和拴绳链的传送方法
        ModUtils.teleportWithAllChains(player, level, pos.x, pos.y, pos.z, pos.yRot, pos.xRot);
            player.sendSystemMessage(ModUtils.translateWithFallback(
                "command.tpatool.back.success", "Teleported to previous position."
        ));
        ModConfigs.DebugLog.info("Player {} teleported back to dimension={}, x={}, y={}, z={}",
                player.getName().getString(), pos.dimension, pos.x, pos.y, pos.z);
        return 1;
    }
}