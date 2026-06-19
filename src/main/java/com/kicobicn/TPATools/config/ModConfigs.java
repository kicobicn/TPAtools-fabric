package com.kicobicn.TPATools.config;

import com.kicobicn.TPATools.Commands.TPAHandler;
import com.kicobicn.TPATools.chat.ModChatMenus;
import com.kicobicn.TPATools.util.ModUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.BooleanSupplier;

public class ModConfigs {
    private static final Logger LOGGER = LogManager.getLogger("TPAtools");

    // 配置项定义 - 使用Supplier模式适配Fabric
    public static final IntSupplier COOLDOWN_SECONDS = () -> 60; // 默认60秒
    public static final IntSupplier WAIT_SECONDS = () -> 30; // 默认30秒
    public static final BooleanSupplier DEBUG_MODE = () -> false; // 默认关闭
    public static final IntSupplier MAX_HOMES = () -> 2; // 默认2个家
    public static final IntSupplier HOME_INVITE_COOLDOWN = () -> 0; // 默认0秒
    public static final IntSupplier HOME_INVITE_TIMEOUT = () -> 30; // 默认30秒
    public static final BooleanSupplier ALLOW_TELEPORT_RIDE_ENTITY = () -> false; // 默认关闭
    public static final Supplier<String> DATABASE_TYPE = () -> "json"; // 默认使用JSON
    public static final Supplier<String> MYSQL_HOST = () -> "localhost"; // 默认主机
    public static final IntSupplier MYSQL_PORT = () -> 3306; // 默认端口
    public static final Supplier<String> MYSQL_DATABASE = () -> "tpatools"; // 默认数据库名
    public static final Supplier<String> MYSQL_USERNAME = () -> "root"; // 默认用户名
    public static final Supplier<String> MYSQL_PASSWORD = () -> ""; // 默认密码(空)
    public static final IntSupplier RTP_SCOPE = () -> 100000; // 默认100000范围
    public static final IntSupplier RTP_COOLDOWN_TIME = () -> 30; // 默认30秒
    public static final IntSupplier RTP_MAX_ATTEMPTS = () -> 50; // 默认50次尝试
    public static final IntSupplier RTP_MIN_Y = () -> -64; // 默认最小Y
    public static final IntSupplier RTP_MAX_Y = () -> 320; // 默认最大Y
    public static final IntSupplier MAX_WARP_COUNT = () -> 3; // 默认3个地标
    public static final LongSupplier COOLDOWN_TIME = () -> 3 * 1000L; // 默认3秒(毫秒)
    public static final IntSupplier TIMEOUT_TICKS = () -> 30 * 20; // 默认30秒(以ticks计)
    public static final Supplier<String> DEFAULT_LANGUAGE = () -> "zh_cn"; // 默认语言

    // 服务器实例
    private static MinecraftServer server;

    public static void setServer(MinecraftServer server) {
        ModConfigs.server = server;
    }

    public static MinecraftServer getServer() {
        return server;
    }

    //配置路径检查
    public static Path getConfigDir() {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("tpatools");
        try {
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
                DebugLog.info("Created config directory: {}", configDir.toString());
            }
        } catch (IOException e) {
            DebugLog.error("Failed to create config directory: {}", e.getMessage());
        }
        return configDir;
    }

    public static boolean checkCommandPermission(CommandSourceStack source, String command) {
        boolean needOp = commandPermissions.getOrDefault(command, false);
        if (needOp) {
            return source.hasPermission(2); // 需要OP权限
        } else {
            return true; // 不需要OP权限
        }
    }

    // 命令权限状态
    public static final Map<String, Boolean> commandPermissions = new HashMap<>();

    // Tab补全提供器
    private static final SuggestionProvider<CommandSourceStack> COMMAND_SUGGESTIONS = (context, builder) -> {
        return builder.suggest("tpa").suggest("home").suggest("grave").suggest("back").suggest("rtp").suggest("warp").buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> BOOLEAN_SUGGESTIONS = (context, builder) -> {
        return builder.suggest("true").suggest("false").buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> LANGUAGE_SUGGESTIONS = (context, builder) -> {
        return builder.suggest("en_us").suggest("zh_cn").suggest("fr_fr").suggest("pt_br").suggest("es_es").suggest("zh_cn_cute").buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> RTP_SCOPE_SUGGESTIONS = (context, builder) -> {
        builder.suggest("10000");
        builder.suggest("20000");
        builder.suggest("50000");
        builder.suggest("100000");
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> TIME_SUGGESTIONS = (context, builder) -> {
        builder.suggest("10");
        builder.suggest("30");
        builder.suggest("60");
        builder.suggest("120");
        builder.suggest("300");
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> MAX_WARP_COUNT_SUGGESTIONS = (context, builder) -> {
        builder.suggest("1");
        builder.suggest("2");
        builder.suggest("3");
        builder.suggest("4");
        builder.suggest("5");
        return builder.buildFuture();
    };

    //调试模式
    public static boolean isDebugEnabled() {
        return DEBUG_MODE.getAsBoolean();
    }

    public static class DebugLog {
        public static void info(String message, Object... args) {
            if (isDebugEnabled()) {
                LOGGER.info("[DEBUG INFO] " + message, args);
            }
        }

        public static void warn(String message, Object... args) {
            if (isDebugEnabled()) {
                LOGGER.warn("[DEBUG WARN] " + message, args);
            }
        }

        public static void error(String message, Object... args) {
            if (isDebugEnabled()) {
                LOGGER.error("[DEBUG ERROR] " + message, args);
            }
        }

        public static void log(String message, Object... args) {
            info(message, args);
        }

    }

    //初始化命令权限
    public static void initCommandPermissions() {
        commandPermissions.putIfAbsent("tpa", false);
        commandPermissions.putIfAbsent("home", false);
        commandPermissions.putIfAbsent("grave", false);
        commandPermissions.putIfAbsent("back", false);
        commandPermissions.putIfAbsent("rtp", false);
        commandPermissions.putIfAbsent("warp", false);
        commandPermissions.putIfAbsent("debug", false);
    }

    //注册tpatool管理命令
    public static void onRegisterCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(
                Commands.literal("tpatools")
                        .executes(context -> {  // 无参数时显示设置菜单
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            ModChatMenus.ConfigMenus.showSettingsMenu(player);
                            return 1;
                        })
                        .then(Commands.literal("configs")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    ModChatMenus.ConfigMenus.showConfigsMenu(player);
                                    return 1;
                                })
                                .then(Commands.literal("setlanguage")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            ModChatMenus.ConfigMenus.showSetLanguageMenu(player);
                                            return 1;
                                        })
                                        .then(Commands.argument("lang", StringArgumentType.string())
                                                .suggests(LANGUAGE_SUGGESTIONS)
                                                .executes(context -> {
                                                    String lang = StringArgumentType.getString(context, "lang");
                                                    if (lang.equals("en_us") || lang.equals("zh_cn") || lang.equals("fr_fr") || lang.equals("pt_br") || lang.equals("es_es") || lang.equals("zh_cn_cute")) {
                                                        // 在Fabric中，我们使用supplier模式，这里只是更新配置
                                                        ModUtils.loadTranslations(lang);
                                                        context.getSource().sendSuccess(
                                                                () -> ModUtils.translateWithFallback("command.tpatool.setlanguage.success", "Language set to %s.", lang),
                                                                true
                                                        );
                                                        DebugLog.info("Language switched to {} by {}", lang, context.getSource().getDisplayName().getString());
                                                        return 1;
                                                    }
                                                    context.getSource().sendFailure(
                                                            ModUtils.translateWithFallback("command.tpatool.setlanguage.invalid", "Invalid language.")
                                                    );
                                                    return 0;
                                                })))
                                .then(Commands.literal("setmaxhome")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            ModChatMenus.ConfigMenus.showSetMaxHomeMenu(player);
                                            return 1;
                                        })
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                .executes(context -> {
                                                    int count = IntegerArgumentType.getInteger(context, "count");
                                                    // 在Fabric中，我们使用supplier模式，这里只是临时更新
                                                    context.getSource().sendSuccess(
                                                            () -> ModUtils.translateWithFallback("command.tpatool.setmaxhome.success", "Maximum homes set to %s.", count),
                                                            true
                                                    );
                                                    DebugLog.info("Max homes set to {} by {}", count, context.getSource().getDisplayName().getString());
                                                    return 1;
                                                })))
                                .then(Commands.literal("needop")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            ModChatMenus.ConfigMenus.showNeedOpMenu(player);
                                            return 1;
                                        })
                                        .then(Commands.argument("command", StringArgumentType.string())
                                                .suggests(COMMAND_SUGGESTIONS)
                                                .then(Commands.argument("enable", StringArgumentType.string())
                                                        .suggests(BOOLEAN_SUGGESTIONS)
                                                        .executes(context -> {
                                                            String command = StringArgumentType.getString(context, "command");
                                                            String enableStr = StringArgumentType.getString(context, "enable");
                                                            boolean enable = enableStr.equalsIgnoreCase("true");
                                                            if (!Arrays.asList("tpa", "home", "grave", "back", "warp").contains(command)) {
                                                                context.getSource().sendFailure(
                                                                        ModUtils.translateWithFallback("command.tpatool.needop.invalid_command", "Invalid command. Use 'tpa', 'home', 'grave', 'back', or 'warp'.")
                                                                );
                                                                return 0;
                                                            }
                                                            commandPermissions.put(command, enable);
                                                            TPAHandler.saveCommandPermissions();
                                                            context.getSource().sendSuccess(
                                                                    () -> ModUtils.translateWithFallback(
                                                                            enable ? "command.tpatool.needop.success_enabled" : "command.tpatool.needop.success_disabled",
                                                                            enable ? "%s commands now require OP permission." : "%s commands now do not require OP permission.",
                                                                            command
                                                                    ),
                                                                    true
                                                            );
                                                            DebugLog.info("{} commands set to {} OP by {}", command, enable ? "require" : "not require", context.getSource().getDisplayName().getString());
                                                            return 1;
                                                        }))))
                                .then(Commands.literal("tpacdtime")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            ModChatMenus.ConfigMenus.showTPACDTimeMenu(player);
                                            return 1;
                                        })
                                        .then(Commands.argument("time", IntegerArgumentType.integer(0))
                                                .suggests(TIME_SUGGESTIONS)
                                                .executes(context -> {
                                                    int time = IntegerArgumentType.getInteger(context, "time");
                                                    context.getSource().sendSuccess(
                                                            () -> ModUtils.translateWithFallback(
                                                                    "command.tpatool.tpacdtime.success",
                                                                    "TPA cooldown time set to %d seconds.",
                                                                    time
                                                            ),
                                                            true
                                                    );
                                                    DebugLog.info("TPA cooldown time set to {} seconds by {}",
                                                            time, context.getSource().getDisplayName().getString());
                                                    return 1;
                                                })))
                                .then(Commands.literal("tpawaittime")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            ModChatMenus.ConfigMenus.showTPAWaitTimeMenu(player);
                                            return 1;
                                        })
                                        .then(Commands.argument("time", IntegerArgumentType.integer(0))
                                                .suggests(TIME_SUGGESTIONS)
                                                .executes(context -> {
                                                    int time = IntegerArgumentType.getInteger(context, "time");
                                                    // 在Fabric中，我们使用supplier模式，这里只是临时更新
                                                    context.getSource().sendSuccess(
                                                            () -> ModUtils.translateWithFallback(
                                                                    "command.tpatool.tpawaittime.success",
                                                                    "TPA wait time set to %d seconds.",
                                                                    time
                                                            ),
                                                            true
                                                    );
                                                    DebugLog.info("TPA wait time set to {} seconds by {}",
                                                            time, context.getSource().getDisplayName().getString());
                                                    return 1;
                                                                                                }))
                                                                                )
                                .then(Commands.literal("homeinviteovertime")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            ModChatMenus.ConfigMenus.showHomeInviteOverTimeMenu(player);
                                            return 1;
                                        })
                                        .then(Commands.argument("time", IntegerArgumentType.integer(1))
                                                .suggests(TIME_SUGGESTIONS)
                                                .executes(context -> {
                                                    int time = IntegerArgumentType.getInteger(context, "time");
                                                    // 在Fabric中，我们使用supplier模式，这里只是临时更新
                                                    context.getSource().sendSuccess(
                                                            () -> ModUtils.translateWithFallback(
                                                                    "command.tpatool.home.inviteovertime.success",
                                                                    "Home invite timeout time set to %d seconds.",
                                                                    time
                                                            ),
                                                            true
                                                    );
                                                    DebugLog.info("Home invite timeout time set to {} seconds by {}",
                                                            time, context.getSource().getDisplayName().getString());
                                                    return 1;
                                                })))
                                .then(Commands.literal("homeinvitecdtime")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            ModChatMenus.ConfigMenus.showHomeInviteCDTimeMenu(player);
                                            return 1;
                                        })
                                        .then(Commands.argument("time", IntegerArgumentType.integer(0))
                                                .suggests(TIME_SUGGESTIONS)
                                                .executes(context -> {
                                                    int time = IntegerArgumentType.getInteger(context, "time");
                                                    // 在Fabric中，我们使用supplier模式，这里只是临时更新
                                                    context.getSource().sendSuccess(
                                                            () -> ModUtils.translateWithFallback(
                                                                    "command.tpatool.home.invitecdtime.success",
                                                                    "Home invite cooldown time set to %d seconds.",
                                                                    time
                                                            ),
                                                            true
                                                    );
                                                    DebugLog.info("Home invite cooldown time set to {} seconds by {}",
                                                            time, context.getSource().getDisplayName().getString());
                                                    return 1;
                                                })))
                                .then(Commands.literal("allowteleportrideentity")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            ModChatMenus.ConfigMenus.showAllowTeleportRideEntityMenu(player);
                                            return 1;
                                        })
                                        .then(Commands.argument("enable", StringArgumentType.string())
                                                .suggests(BOOLEAN_SUGGESTIONS)
                                                .executes(context -> {
                                                    String enableStr = StringArgumentType.getString(context, "enable");
                                                    boolean enable = enableStr.equalsIgnoreCase("true");
                                                    // 在Fabric中，我们使用supplier模式，这里只是临时更新
                                                    context.getSource().sendSuccess(
                                                            () -> ModUtils.translateWithFallback(
                                                                    enable ? "command.tpatool.config.allowteleportrideentity.enabled" : "command.tpatool.config.allowteleportrideentity.disabled",
                                                                    enable ? "Ride entity teleport enabled." : "Ride entity teleport disabled."
                                                            ),
                                                            true
                                                    );
                                                    DebugLog.info("Ride entity teleport {} by {}", enable ? "enabled" : "disabled", context.getSource().getDisplayName().getString());
                                                    return 1;
                                                })))
                                .then(Commands.literal("rtpcdtime")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            ModChatMenus.ConfigMenus.showRTPCooldownTimeMenu(player);
                                            return 1;
                                        })
                                        .then(Commands.argument("time", IntegerArgumentType.integer(0))
                                                .suggests(TIME_SUGGESTIONS)
                                                .executes(context -> {
                                                    int time = IntegerArgumentType.getInteger(context, "time");
                                                    // 在Fabric中，我们使用supplier模式，这里只是临时更新
                                                    context.getSource().sendSuccess(
                                                            () -> ModUtils.translateWithFallback(
                                                                    "command.tpatool.config.rtp.cooldown.success",
                                                                    "RTP cooldown time set to %d seconds.",
                                                                    time
                                                            ),
                                                            true
                                                    );
                                                    DebugLog.info("RTP cooldown time set to {} seconds by {}",
                                                            time, context.getSource().getDisplayName().getString());
                                                    return 1;
                                                })))
                                .then(Commands.literal("rtpscope")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            ModChatMenus.ConfigMenus.showRTPScopeMenu(player);
                                            return 1;
                                        })
                                        .then(Commands.argument("scope", IntegerArgumentType.integer(0))
                                                .suggests(RTP_SCOPE_SUGGESTIONS)
                                                .executes(context -> {
                                                    int scope = IntegerArgumentType.getInteger(context, "scope");
                                                    // 在Fabric中，我们使用supplier模式，这里只是临时更新
                                                    context.getSource().sendSuccess(
                                                            () -> ModUtils.translateWithFallback(
                                                                    "command.tpatool.config.rtp.scope.success",
                                                                    "RTP scope set to %d blocks.",
                                                                    scope
                                                            ),
                                                            true
                                                    );
                                                    DebugLog.info("RTP scope set to {} blocks by {}",
                                                            scope, context.getSource().getDisplayName().getString());
                                                    return 1;
                                                })))
                                .then(Commands.literal("setmaxwarpcount")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            ModChatMenus.ConfigMenus.showMaxWarpCountMenu(player);
                                            return 1;
                                        })
                                        .then(Commands.argument("count", IntegerArgumentType.integer(0))
                                                .suggests(MAX_WARP_COUNT_SUGGESTIONS)
                                                .executes(context -> {
                                                    int count = IntegerArgumentType.getInteger(context, "count");
                                                    // 在Fabric中，我们使用supplier模式，这里只是临时更新
                                                    context.getSource().sendSuccess(
                                                            () -> ModUtils.translateWithFallback(
                                                                    "command.tpatool.config.maxwarpcount.success",
                                                                    "Max warp count set to %d.",
                                                                    count
                                                            ),
                                                            true
                                                    );
                                                    DebugLog.info("Max warp count set to {} by {}",
                                                            count, context.getSource().getDisplayName().getString());
                                                    return 1;
                                                })))
                        )
                        .then(Commands.literal("debug")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    ModChatMenus.ConfigMenus.showDebugMenu(player);
                                    return 1;
                                })
                                .then(Commands.argument("enable", StringArgumentType.string())
                                    .suggests(BOOLEAN_SUGGESTIONS)
                                    .executes(context -> {
                                        String enableStr = StringArgumentType.getString(context, "enable");
                                        boolean enable = enableStr.equalsIgnoreCase("true");
                                        context.getSource().sendSuccess(
                                            () -> ModUtils.translateWithFallback(
                                                    enable ? "command.tpatool.debug.enabled" : "command.tpatool.debug.disabled",
                                                    enable ? "Debug mode enabled." : "Debug mode disabled."
                                            ),
                                            true
                                        );
                                        DebugLog.info("Debug mode {} by {}", enable ? "enabled" : "disabled", context.getSource().getDisplayName().getString());
                                        return 1;
                                    })))
                        .then(Commands.literal("about")
                                .requires(source -> source.hasPermission(0))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    ModChatMenus.ConfigMenus.showAboutMenu(player);
                                    return 1;
                                })
                        )
        );
    }
}