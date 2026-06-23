package com.kicobicn.TPATools.util;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.kicobicn.TPATools.Commands.GraveHandler;
import com.kicobicn.TPATools.Commands.HomeHandler;
import com.kicobicn.TPATools.Commands.TPAHandler;
import com.kicobicn.TPATools.Commands.WarpHandler;
import com.kicobicn.TPATools.config.ModConfigs;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
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
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            HomeHandler.saveHomes();
            GraveHandler.saveGraves();
            TPAHandler.saveToggleStates();
            TPAHandler.saveLockedPlayers();
            TPAHandler.saveCommandPermissions();
            WarpHandler.saveWarpPoints();
        });

        // 注册服务器tick事件，用于处理TPA超时
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tick();
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
        return Component.literal(String.format(translated, stringArgs));
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

    private static final Set<String> availableLanguages = new HashSet<>(Set.of("en_us", "zh_cn", "fr_fr", "pt_br", "es_es", "zh_cn_cute"));

    public static void detectAvailableLanguages() {
        availableLanguages.clear();
        try {
            MinecraftServer server = ModConfigs.getServer(); // 使用Fabric版的服务器获取方法
            if (server != null) {
                var resources = server.getResourceManager().listResources("lang", path -> path.getPath().endsWith(".json"));
                for (var entry : resources.entrySet()) {
                    ResourceLocation loc = entry.getKey();
                    String path = loc.getPath();
                    if (path.startsWith("lang/") && path.endsWith(".json")) {
                        String langCode = path.substring(5, path.length() - 5); // 去掉 "lang/" 和 ".json"
                        availableLanguages.add(langCode);
                    }
                }
                if (availableLanguages.isEmpty()) {
                    ModConfigs.DebugLog.warn("No languages detected, fallback to en_us/zh_cn");
                    availableLanguages.add("en_us");
                    availableLanguages.add("zh_cn");
                    availableLanguages.add("fr_fr");
                    availableLanguages.add("pt_br");
                    availableLanguages.add("es_es");
                    availableLanguages.add("zh_cn_cute");
                }
                ModConfigs.DebugLog.info("Detected languages: {}", availableLanguages);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to detect languages: {}", e.getMessage());
        }
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
                serverPlayer.teleportTo(targetLevel, x, y, z, yRot, xRot);
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
                    serverPlayer.teleportTo(targetLevel, x, y, z, yRot, xRot);
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
                        serverPlayer.teleportTo(targetLevel, newX, newY, newZ, newYRot, entity.getXRot());
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
        // 移除实体的骑乘关系
        entity.unRide();

        // 检查目标维度是否相同
        if (entity.level() != targetLevel) {
            // 不同维度，需要跨维度传送
            entity.changeDimension(targetLevel);
            // changeDimension后实体位置可能不会立即更新，等待实体到达新维度后再设置位置
        }

        // 设置实体位置和旋转
        entity.moveTo(x, y, z, yRot, xRot);
    }

    public static void tick() {
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, List<TPAHandler.TPARequest>> entry : TPAHandler.requests.entrySet()) {
            Iterator<TPAHandler.TPARequest> iterator = entry.getValue().iterator();
            while (iterator.hasNext()) {
                TPAHandler.TPARequest request = iterator.next();
                if (System.currentTimeMillis() - request.timestamp >= ModConfigs.getTimeoutTicks() * 50) {
                    request.target.sendSystemMessage(translateWithFallback(
                            request.isTPHere ? "command.tpatool.tpahere.timeout" : "command.tpatool.tpa.timeout",
                            "Teleport request from %s has timed out.", request.sender.getName()
                    ));
                    request.sender.sendSystemMessage(translateWithFallback(
                            "command.tpatool.tpa.timeout_self",
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