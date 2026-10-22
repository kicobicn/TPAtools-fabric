package com.kicobicn.TPATools.Commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.kicobicn.TPATools.config.ModConfigs;
import com.kicobicn.TPATools.database.DatabaseManager;
import com.kicobicn.TPATools.util.ModUtils;
import com.kicobicn.TPATools.util.TeleportDelayManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BackHandler {
    private static final Logger LOGGER = LogManager.getLogger("TPAtools");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String JSON_FILE = "tpatool_back.json";

    private static final Map<UUID, PlayerPosition> previousPositions = new HashMap<>();

    public static class PlayerPosition {
        public Identifier dimension;
        public final double x, y, z;
        public final float yRot, xRot;

        public PlayerPosition(Identifier dimension, double x, double y, double z, float yRot, float xRot) {
            this.dimension = dimension != null
                    ? dimension
                    : Identifier.fromNamespaceAndPath("minecraft", "overworld");
            this.x = x;
            this.y = y;
            this.z = z;
            this.yRot = yRot;
            this.xRot = xRot;
        }

        public PlayerPosition(ServerLevel level, double x, double y, double z, float yRot, float xRot) {
            this(level.dimension().identifier(), x, y, z, yRot, xRot);
        }
    }

    // ==================== 持久化 ====================
    public static void loadBackPositions() {
        previousPositions.clear();

        Connection conn = DatabaseManager.getConnection();
        if (conn != null) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM player_back")) {
                while (rs.next()) {
                    previousPositions.put(
                            UUID.fromString(rs.getString("player_uuid")),
                            new PlayerPosition(
                                    ModUtils.parseIdentifier(rs.getString("dimension")),
                                    rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                                    rs.getFloat("y_rot"), rs.getFloat("x_rot")));
                }
                ModConfigs.DebugLog.info("Loaded /back positions from MySQL");
                return;
            } catch (SQLException e) {
                LOGGER.error("Failed to load /back positions from MySQL: {}", e.getMessage());
                return;
            }
        }

        Path path = ModConfigs.getConfigDir().resolve(JSON_FILE);
        if (!Files.exists(path)) {
            return;
        }
        try {
            String json = Files.readString(path);
            Map<UUID, PlayerPosition> loaded = GSON.fromJson(
                    json, new TypeToken<Map<UUID, PlayerPosition>>() {}.getType());
            if (loaded != null) {
                previousPositions.putAll(loaded);
                ModConfigs.DebugLog.info("Loaded /back positions from {}", path);
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Failed to load /back positions: {}", e.getMessage());
        }
    }

    public static void saveBackPositions() {
        Connection conn = DatabaseManager.getConnection();
        if (conn != null) {
            try {
                try (Statement clear = conn.createStatement()) {
                    clear.execute("DELETE FROM player_back");
                }
                try (PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO player_back (player_uuid, dimension, x, y, z, x_rot, y_rot) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    for (Map.Entry<UUID, PlayerPosition> entry : previousPositions.entrySet()) {
                        PlayerPosition pos = entry.getValue();
                        stmt.setString(1, entry.getKey().toString());
                        stmt.setString(2, ModUtils.formatIdentifier(pos.dimension));
                        stmt.setDouble(3, pos.x);
                        stmt.setDouble(4, pos.y);
                        stmt.setDouble(5, pos.z);
                        stmt.setFloat(6, pos.xRot);
                        stmt.setFloat(7, pos.yRot);
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }
                ModConfigs.DebugLog.info("Saved /back positions to MySQL");
                return;
            } catch (SQLException e) {
                LOGGER.error("Failed to save /back positions to MySQL: {}", e.getMessage());
                return;
            }
        }

        try {
            Path path = ModConfigs.getConfigDir().resolve(JSON_FILE);
            Files.writeString(path, GSON.toJson(previousPositions));
            ModConfigs.DebugLog.info("Saved /back positions to {}", path);
        } catch (IOException e) {
            LOGGER.error("Failed to save /back positions: {}", e.getMessage());
        }
    }

    // ==================== 命令 ====================
    public static void onRegisterCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(
                Commands.literal("back")
                        .requires(source -> ModConfigs.checkCommandPermission(source, "back"))
                        .executes(context -> {
                            try {
                                ServerPlayer player = context.getSource().getPlayerOrException();
                                ModConfigs.DebugLog.info("Executing /back command for player: {}", player.getName().getString());

                                if (!validatePreviousPosition(player)) {
                                    return 0;
                                }

                                TeleportDelayManager.requestTeleport(player, () -> teleportBack(player));
                                return 1;
                            } catch (Exception e) {
                                LOGGER.error("Unexpected error in /back command", e);
                                context.getSource().sendFailure(Component.literal("An unexpected error occurred."));
                                return 0;
                            }
                        })
        );
    }

    public static void recordPosition(ServerPlayer player) {
        previousPositions.put(player.getUUID(), new PlayerPosition(
                player.level(),
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot()
        ));
        saveBackPositions();
        ModConfigs.DebugLog.log("Recorded position for {}: dimension={}, x={}, y={}, z={}",
                player.getName().getString(), player.level().dimension().identifier(),
                player.getX(), player.getY(), player.getZ());
    }

    private static int teleportBack(ServerPlayer player) {
        PlayerPosition pos = previousPositions.get(player.getUUID());
        if (pos == null) {
            player.sendSystemMessage(ModUtils.translateWithFallback(
                    "command.tpatools.back.no_position", "No previous position recorded!"
            ));
            return 0;
        }
        MinecraftServer server = player.level().getServer();
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, pos.dimension));
        if (level == null) {
            player.sendSystemMessage(ModUtils.translateWithFallback(
                    "command.tpatools.back.invalid_dimension", "Invalid dimension for previous position!"
            ));
            previousPositions.remove(player.getUUID());
            saveBackPositions();
            return 0;
        }
        recordPosition(player);
        double teleX = pos.x;
        double teleY = pos.y;
        double teleZ = pos.z;
        if (ModConfigs.isSafeTeleport()) {
            BlockPos safePos = ModUtils.findSafeTeleportPosition(level, teleX, teleY, teleZ);
            teleX = safePos.getX() + 0.5;
            teleY = safePos.getY();
            teleZ = safePos.getZ() + 0.5;
        }
        ModUtils.teleportWithAllChains(player, level, teleX, teleY, teleZ, pos.yRot, pos.xRot);
        player.sendSystemMessage(ModUtils.translateWithFallback(
                "command.tpatools.back.success", "Teleported to previous position."
        ));
        ModConfigs.DebugLog.info("Player {} teleported back to dimension={}, x={}, y={}, z={}",
                player.getName().getString(), pos.dimension, pos.x, pos.y, pos.z);
        return 1;
    }

    private static boolean validatePreviousPosition(ServerPlayer player) {
        PlayerPosition pos = previousPositions.get(player.getUUID());
        if (pos == null) {
            player.sendSystemMessage(ModUtils.translateWithFallback(
                    "command.tpatools.back.no_position",
                    "No previous position recorded!"
            ));
            return false;
        }
        MinecraftServer server = player.level().getServer();
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, pos.dimension));
        if (level == null) {
            player.sendSystemMessage(ModUtils.translateWithFallback(
                    "command.tpatools.back.invalid_dimension",
                    "Invalid dimension for previous position!"
            ));
            previousPositions.remove(player.getUUID());
            saveBackPositions();
            return false;
        }
        return true;
    }
}
