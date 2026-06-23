package com.kicobicn.TPATools.Commands;

import com.kicobicn.TPATools.config.ModConfigs;
import com.kicobicn.TPATools.util.ModUtils;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class RTPHandler {
    
    // 冷却时间管理
    private static final Map<UUID, Long> rtpCooldowns = new HashMap<>();
    private static final Random random = new Random();

    public static void onRegisterCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(
                Commands.literal("rtp")
                        .requires(source -> ModConfigs.checkCommandPermission(source, "rtp"))
                        .executes(context -> {
                            try {
                                ServerPlayer player = context.getSource().getPlayerOrException();
                                return executeRTP(player);
                            } catch (Exception e) {
                                ModConfigs.DebugLog.error("Unexpected error in /rtp: ", e);
                                context.getSource().sendFailure(Component.literal("An unexpected error occurred."));
                                return 0;
                            }
                        })
        );
    }

    /**
     * 执行RTP传送
     */
    private static int executeRTP(ServerPlayer player) {
        UUID playerUUID = player.getUUID();
        
        // 检查冷却时间
        if (isOnCooldown(playerUUID)) {
            long remainingTime = getRemainingCooldown(playerUUID);
            player.sendSystemMessage(
                    ModUtils.translateWithFallback(
                            "command.tpatool.rtp.cooldown", 
                            "RTP is on cooldown. Please wait %d seconds.", 
                            remainingTime
                    )
            );
            return 0;
        }

        // 开始寻找安全位置
        player.sendSystemMessage(
                ModUtils.translateWithFallback(
                        "command.tpatool.rtp.searching", 
                        "Searching for a safe location..."
                )
        );

        ServerLevel world = (ServerLevel) player.level();
        // 以世界原点 (0,0) 为中心进行随机传送
        BlockPos safePos = findSafeLocation(world, 0, 0);
        
        if (safePos == null) {
            player.sendSystemMessage(
                    ModUtils.translateWithFallback(
                            "command.tpatool.rtp.failed", 
                            "Failed to find a safe location after %d attempts. Please try again.", 
                            ModConfigs.getRtpMaxAttempts()
                    )
            );
            return 0;
        }

        // 传送玩家
        player.teleportTo(world, safePos.getX() + 0.5, safePos.getY() + 1, safePos.getZ() + 0.5, 
                          player.getYRot(), player.getXRot());
        
        // 设置冷却时间
        setCooldown(playerUUID);
        
        player.sendSystemMessage(
                ModUtils.translateWithFallback(
                        "command.tpatool.rtp.success", 
                        "Successfully teleported to X: %d, Y: %d, Z: %d", 
                        safePos.getX(), safePos.getY(), safePos.getZ()
                ).withStyle(ChatFormatting.GREEN)
        );
        
        ModConfigs.DebugLog.info("Player {} used RTP to X: {}, Y: {}, Z: {}", 
                                player.getName().getString(), safePos.getX(), safePos.getY(), safePos.getZ());
        
        return 1;
    }

    /**
     * 寻找安全位置
     */
    private static BlockPos findSafeLocation(ServerLevel world, int centerX, int centerZ) {
        int scope = ModConfigs.getRtpScope();
        int maxAttempts = ModConfigs.getRtpMaxAttempts();
        int minY = ModConfigs.getRtpMinY();
        int maxY = ModConfigs.getRtpMaxY();
        
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            // 在范围内随机选择位置
            int x = centerX + random.nextInt(scope * 2) - scope;
            int z = centerZ + random.nextInt(scope * 2) - scope;
            
            // 从最高点开始向下寻找安全位置
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, maxY, z);
            
            // 向下寻找地面
            while (pos.getY() > minY) {
                BlockState blockState = world.getBlockState(pos);
                BlockState blockBelow = world.getBlockState(pos.below());
                BlockState blockAbove = world.getBlockState(pos.above());
                
                // 检查当前位置是否安全
                if (isSafePosition(world, pos, blockState, blockBelow, blockAbove)) {
                    return pos.immutable();
                }
                
                pos.move(0, -1, 0);
            }
        }
        
        return null; // 未找到安全位置
    }

    /**
     * 检查位置是否安全
     */
    private static boolean isSafePosition(ServerLevel world, BlockPos pos, BlockState blockState, 
                                         BlockState blockBelow, BlockState blockAbove) {
        // 检查是否在有效Y范围内
        if (pos.getY() < ModConfigs.getRtpMinY() || pos.getY() > ModConfigs.getRtpMaxY()) {
            return false;
        }
        
        // 检查当前位置是否可站立（非液体、非危险方块）
        if (!blockBelow.blocksMotion() || blockBelow.is(Blocks.LAVA) || 
            blockBelow.is(Blocks.MAGMA_BLOCK) || blockBelow.is(Blocks.FIRE) ||
            blockBelow.is(Blocks.SOUL_FIRE) || blockBelow.is(Blocks.CACTUS)) {
            return false;
        }
        
        // 检查当前位置是否可站立（非液体）
        FluidState fluidState = blockState.getFluidState();
        if (!fluidState.isEmpty()) {
            return false;
        }
        
        // 检查上方是否有足够空间（至少2格高）
        if (!blockAbove.isAir() || !world.getBlockState(pos.above(2)).isAir()) {
            return false;
        }
        
        // 检查当前位置是否安全（非危险方块）
        if (blockState.is(Blocks.LAVA) || blockState.is(Blocks.MAGMA_BLOCK) ||
            blockState.is(Blocks.FIRE) || blockState.is(Blocks.SOUL_FIRE) ||
            blockState.is(Blocks.CACTUS) || blockState.is(Blocks.SWEET_BERRY_BUSH)) {
            return false;
        }
        
        return true;
    }

    /**
     * 检查玩家是否在冷却中
     */
    private static boolean isOnCooldown(UUID playerUUID) {
        if (!rtpCooldowns.containsKey(playerUUID)) {
            return false;
        }
        
        long lastUsed = rtpCooldowns.get(playerUUID);
        long cooldownTime = ModConfigs.getRtpCooldownTime() * 1000L;
        return System.currentTimeMillis() - lastUsed < cooldownTime;
    }

    /**
     * 获取剩余冷却时间（秒）
     */
    private static long getRemainingCooldown(UUID playerUUID) {
        if (!rtpCooldowns.containsKey(playerUUID)) {
            return 0;
        }
        
        long lastUsed = rtpCooldowns.get(playerUUID);
        long cooldownTime = ModConfigs.getRtpCooldownTime() * 1000L;
        long elapsed = System.currentTimeMillis() - lastUsed;
        long remaining = cooldownTime - elapsed;
        
        return Math.max(0, (remaining + 999) / 1000); // 向上取整到秒
    }

    /**
     * 设置冷却时间
     */
    private static void setCooldown(UUID playerUUID) {
        rtpCooldowns.put(playerUUID, System.currentTimeMillis());
    }

    /**
     * 清除冷却时间（用于调试或重置）
     */
    public static void clearCooldown(UUID playerUUID) {
        rtpCooldowns.remove(playerUUID);
    }

    /**
     * 获取所有冷却中的玩家
     */
    public static Map<UUID, Long> getCooldowns() {
        return new HashMap<>(rtpCooldowns);
    }
}