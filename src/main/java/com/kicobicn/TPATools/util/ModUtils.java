package com.kicobicn.TPATools.util;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.kicobicn.TPATools.Commands.BackHandler;
import com.kicobicn.TPATools.Commands.GraveHandler;
import com.kicobicn.TPATools.Commands.HomeHandler;
import com.kicobicn.TPATools.Commands.RTPHandler;
import com.kicobicn.TPATools.Commands.TPAHandler;
import com.kicobicn.TPATools.Commands.WarpHandler;
import com.kicobicn.TPATools.config.ModConfigs;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.core.Direction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ModUtils {

    private static final Logger LOGGER = LogManager.getLogger("TPAtools");

    public static final Map<String, String> translations = new HashMap<>();

    public static void registerEvents() {
        // 注册服务器生命周期事件
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            loadTranslations(ModConfigs.getDefaultLanguage());
            HomeHandler.loadHomes();
            GraveHandler.loadGraves();
            TPAHandler.loadToggleStates();
            TPAHandler.loadLockedPlayers();
            TPAHandler.loadCommandPermissions();
            WarpHandler.loadWarpPoints();
            BackHandler.loadBackPositions();
            RTPHandler.loadCooldowns();
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            HomeHandler.saveHomes();
            GraveHandler.saveGraves();
            TPAHandler.saveToggleStates();
            TPAHandler.saveLockedPlayers();
            TPAHandler.saveCommandPermissions();
            WarpHandler.saveWarpPoints();
            BackHandler.saveBackPositions();
            RTPHandler.saveCooldowns();
        });

        // 注册服务器tick事件，用于处理TPA超时
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tick();
        });

        ServerLivingEntityEvents.AFTER_DEATH.register(( entity, source) -> {
            if (entity instanceof ServerPlayer player) {
                GraveHandler.onPlayerDeath(player);
            }
        });

    }

    public static MutableComponent translateWithFallback(String key, String fallback, Object... args) {
        String translated = translations.getOrDefault(key, fallback);
        Object[] stringArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Component component) {
                stringArgs[i] = component.getString();
            } else {
                stringArgs[i] = args[i];
            }
        }
        String text;
        try {
            text = String.format(translated, stringArgs);
        } catch (RuntimeException e) {
            // 占位符与实参不匹配（例如译文含 %s 但调用方没传参）时退化为纯文本，
            // 避免在服务器 tick 内抛 MissingFormatArgumentException 打断主循环。
            LOGGER.warn("Bad format for translation key {}: {}", key, translated);
            text = translated;
        }
        return Component.literal(text);
    }

    // 加载翻译（通过 classpath 读取，不依赖服务器 ResourceManager）
    public static void loadTranslations(String lang) {
        translations.clear();
        String resourcePath = "assets/tpatools/lang/" + lang + ".json";
        try (InputStream is = ModUtils.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is != null) {
                String jsonContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> loadedTranslations = new Gson().fromJson(jsonContent, new TypeToken<Map<String, String>>(){}.getType());
                translations.putAll(loadedTranslations);
                ModConfigs.DebugLog.info("Loaded translations for language: {}", lang);
            } else {
                LOGGER.warn("Translation file not found: {}, using fallback", resourcePath);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load translations for {}: {}, using fallback", lang, e.getMessage());
        }
    }

    /**
     * 把 identifier 字符串（形如 {@code minecraft:overworld}）解析回 {@link Identifier}。
     * <p>存储层统一用 {@link Identifier#toString()} 写入，读取时必须用本方法还原，
     * 否则会得到 {@code minecraft:minecraft:overworld} 这类非法维度。</p>
     */
    public static Identifier parseIdentifier(String raw) {
        if (raw == null || raw.isBlank()) {
            return Identifier.fromNamespaceAndPath("minecraft", "overworld");
        }
        String value = raw.trim();
        int split = value.indexOf(':');
        if (split < 0) {
            return Identifier.fromNamespaceAndPath("minecraft", value);
        }
        String namespace = value.substring(0, split);
        String path = value.substring(split + 1);
        if (namespace.isEmpty()) {
            namespace = "minecraft";
        }
        if (path.isEmpty()) {
            path = "overworld";
        }
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    /** 统一的 identifier 写库格式 */
    public static String formatIdentifier(Identifier identifier) {
        return identifier == null ? "minecraft:overworld" : identifier.toString();
    }

    /**
     * 获取玩家骑乘的所有实体（包括链式骑乘）
     */
    public static List<Entity> getRiddenEntities(Entity rider) {
        List<Entity> entities = new ArrayList<>();
        Entity current = rider;

        while (current != null) {
            entities.add(current);
            current = current.getVehicle();
        }

        return entities;
    }

    /**
     * 传送玩家及其骑乘的所有实体
     */
    public static void teleportWithRideChain(Entity player, ServerLevel targetLevel, double x, double y, double z, float yRot, float xRot) {
        if (!ModConfigs.isAllowTeleportRideEntity()) {
            // 如果配置禁用骑乘传送，只传送玩家
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.teleportTo(targetLevel, x, y, z,Set.of(), yRot, xRot, false);
            } else {
                // 对于非玩家实体，使用通用传送方法
                player.unRide();
                teleportEntityToPosition(player, targetLevel, x, y, z, yRot, xRot);
            }
            return;
        }

        // 获取骑乘链中的所有实体
        List<Entity> rideChain = getRiddenEntities(player);

        // 记录所有实体的相对位置和旋转
        Map<Entity, Vec3> relativePositions = new HashMap<>();
        Map<Entity, Float> relativeRotations = new HashMap<>();

        for (int i = 0; i < rideChain.size() - 1; i++) {
            Entity rider = rideChain.get(i);
            Entity vehicle = rideChain.get(i + 1);

            // 计算相对位置和旋转
            Vec3 relativePos = rider.position().subtract(vehicle.position());
            float relativeYRot = rider.getYRot() - vehicle.getYRot();

            relativePositions.put(rider, relativePos);
            relativeRotations.put(rider, relativeYRot);
        }

        // 从最底层的实体开始传送
        for (int i = rideChain.size() - 1; i >= 0; i--) {
            Entity entity = rideChain.get(i);

            if (i == rideChain.size() - 1) {
                // 最底层的实体传送到目标位置
                if (entity instanceof ServerPlayer serverPlayer) {
                    serverPlayer.teleportTo(targetLevel, x, y, z, Set.of(), yRot, xRot, false);
                } else {
                    // 对于非玩家实体，使用通用传送方法
                    entity.unRide();
                    teleportEntityToPosition(entity, targetLevel, x, y, z, yRot, xRot);
                }
            } else {
                // 其他实体根据相对位置传送
                Entity vehicle = rideChain.get(i + 1);
                Vec3 relativePos = relativePositions.get(entity);
                float relativeYRot = relativeRotations.get(entity);

                if (relativePos != null) {
                    double newX = vehicle.getX() + relativePos.x;
                    double newY = vehicle.getY() + relativePos.y;
                    double newZ = vehicle.getZ() + relativePos.z;
                    float newYRot = vehicle.getYRot() + relativeYRot;

                    if (entity instanceof ServerPlayer serverPlayer) {
                        serverPlayer.teleportTo(targetLevel, newX, newY, newZ, Set.of(), newYRot, entity.getXRot(), false);
                    } else {
                        // 对于非玩家实体，使用通用传送方法
                        entity.unRide();
                        teleportEntityToPosition(entity, targetLevel, newX, newY, newZ, newYRot, entity.getXRot());
                    }
                }
            }
        }
    }

    /**
     * 查找安全传送位置
     */
    public static BlockPos findSafeTeleportPosition(ServerLevel level, double x, double y, double z) {
        BlockPos target = BlockPos.containing(x, y, z);

        if (isPositionSafeForTeleport(level, target)) {
            return target;
        }

        BlockPos nearestSafe = null;
        double minDistanceSq = Double.MAX_VALUE;

        // 搜索范围：以目标为中心，半径 5 格的正方形区域（11×11=121个位置）
        // 同时检查 y 轴上下 2 格
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos checkPos = target.offset(dx, dy, dz);
                    if (isPositionSafeForTeleport(level, checkPos)) {
                        // 计算距离平方（比较距离不需要开根号）
                        double distSq = dx * dx + dy * dy + dz * dz;
                        if (distSq < minDistanceSq) {
                            minDistanceSq = distSq;
                            nearestSafe = checkPos;
                        }
                    }
                }
            }
        }

        // 如果找到了安全位置，返回最近的那个
        if (nearestSafe != null) {
            return nearestSafe;
        }

        // 实在找不到，回退到原位置
        return target;
    }

    private static boolean isPositionSafeForTeleport(ServerLevel level, BlockPos pos) {
        BlockState feetBlock = level.getBlockState(pos);
        BlockState headBlock = level.getBlockState(pos.above());
        BlockState belowBlock = level.getBlockState(pos.below());

        // 脚部/头部位置有完整碰撞体积方块（会窒息）
        if (isFullBlocking(level, pos, feetBlock) || isFullBlocking(level, pos.above(), headBlock)) {
            return false;
        }

        // 脚下方块必须能站立（有碰撞体积）
        if (!belowBlock.isCollisionShapeFullBlock(level, pos)) {
            return false;
        }

        // 排除危险方块
        if (feetBlock.is(Blocks.LAVA) || headBlock.is(Blocks.LAVA)
                || belowBlock.is(Blocks.LAVA)) {
            return false;
        }

        return true;
    }

    /**
     * 判断方块是否会对玩家造成阻挡（完整方块碰撞体积）
     * 雪花、地毯等薄层不会被判定为阻挡
     */
    private static boolean isFullBlocking(ServerLevel level, BlockPos pos, BlockState state) {
        if (!state.isCollisionShapeFullBlock(level, pos)) return false;
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) return false;
        // 碰撞体积最大高度 >= 0.5 格才视为有效阻挡
        // 雪花(<4层)、地毯等薄层 maxY < 0.5，不算阻挡
        return shape.max(Direction.Axis.Y) >= 0.5;
    }

    /**
     * 传送玩家及其骑乘实体
     */
    public static void teleportWithAllChains(Entity player, ServerLevel targetLevel, double x, double y, double z, float yRot, float xRot) {
        // 首先传送骑乘链
        teleportWithRideChain(player, targetLevel, x, y, z, yRot, xRot);
    }

    /**
     * 传送实体到指定位置（用于非ServerPlayer实体）
     */
    private static void teleportEntityToPosition(Entity entity, ServerLevel targetLevel, double x, double y, double z, float yRot, float xRot) {
            entity.unRide();
            entity.teleportTo(targetLevel, x, y, z, Set.of(), yRot, xRot, false);
    }

    public static void tick() {
        TeleportDelayManager.tick();

        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, List<TPAHandler.TPARequest>> entry : TPAHandler.requests.entrySet()) {
            Iterator<TPAHandler.TPARequest> iterator = entry.getValue().iterator();
            while (iterator.hasNext()) {
                TPAHandler.TPARequest request = iterator.next();
                if (System.currentTimeMillis() - request.timestamp >= ModConfigs.getWaitSeconds() * 1000L) {
                    request.target.sendSystemMessage(translateWithFallback(
                            request.isTPHere ? "command.tpatools.tpahere.timeout" : "command.tpatools.tpa.timeout",
                            "Teleport request from %s has timed out.", request.sender.getName()
                    ));
                    request.sender.sendSystemMessage(translateWithFallback(
                            "command.tpatools.tpa.timeout_self",
                            "Your teleport request to %s has timed out.", request.target.getName()
                    ));
                    iterator.remove();
                    ModConfigs.DebugLog.info("TPA request timed out: {} -> {}",
                            request.sender.getName().getString(), request.target.getName().getString());
                }
            }
            if (entry.getValue().isEmpty()) {
                toRemove.add(entry.getKey());
            }
        }
        toRemove.forEach(uuid -> TPAHandler.requests.remove(uuid));
    }
}