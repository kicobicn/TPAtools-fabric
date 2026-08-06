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
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class ModConfigs {
    private static final Logger LOGGER = LogManager.getLogger("TPAtools");

    // ==================== 配置字段（可变 + 持久化） ====================
    private static final Gson CONFIG_GSON = new GsonBuilder().setPrettyPrinting().create();

    private static int cooldownSeconds = 60;
    private static int waitSeconds = 30;
    private static boolean debugMode = false;
    private static int maxHomes = 2;
    private static int homeInviteCooldown = 0;
    private static int homeInviteTimeout = 30;
    private static boolean allowTeleportRideEntity = false;
    private static String databaseType = "json";
    private static String mysqlHost = "localhost";
    private static int mysqlPort = 3306;
    private static String mysqlDatabase = "tpatools";
    private static String mysqlUsername = "root";
    private static String mysqlPassword = "";
    private static int rtpScope = 100000;
    private static int rtpCooldownTime = 30;
    private static int rtpMaxAttempts = 50;
    private static int rtpMinY = -64;
    private static int rtpMaxY = 320;
    private static int maxWarpCount = 3;
    private static long cooldownTime = 3 * 1000L;
    private static int timeoutTicks = 30 * 20;
    private static String defaultLanguage = "zh_cn";
    private static boolean safeTeleport = true;

    // ==================== Getters ====================
    public static int getWaitSeconds() { return waitSeconds; }
    public static int getMaxHomes() { return maxHomes; }
    public static int getHomeInviteCooldown() { return homeInviteCooldown; }
    public static int getHomeInviteTimeout() { return homeInviteTimeout; }
    public static boolean isAllowTeleportRideEntity() { return allowTeleportRideEntity; }
    public static String getDatabaseType() { return databaseType; }
    public static String getMysqlHost() { return mysqlHost; }
    public static int getMysqlPort() { return mysqlPort; }
    public static String getMysqlDatabase() { return mysqlDatabase; }
    public static String getMysqlUsername() { return mysqlUsername; }
    public static String getMysqlPassword() { return mysqlPassword; }
    public static int getRtpScope() { return rtpScope; }
    public static int getRtpCooldownTime() { return rtpCooldownTime; }
    public static int getRtpMaxAttempts() { return rtpMaxAttempts; }
    public static int getRtpMinY() { return rtpMinY; }
    public static int getRtpMaxY() { return rtpMaxY; }
    public static int getMaxWarpCount() { return maxWarpCount; }
    public static long getCooldownTime() { return cooldownTime; }
    public static int getTimeoutTicks() { return timeoutTicks; }
    public static String getDefaultLanguage() { return defaultLanguage; }
    public static boolean isDebugEnabled() { return debugMode; }
    public static boolean isSafeTeleport() { return safeTeleport; }

    // ==================== Setters ====================
    public static void setWaitSeconds(int v) { waitSeconds = v; saveConfigValues(); }
    public static void setDebugMode(boolean v) { debugMode = v; saveConfigValues(); }
    public static void setMaxHomes(int v) { maxHomes = v; saveConfigValues(); }
    public static void setHomeInviteCooldown(int v) { homeInviteCooldown = v; saveConfigValues(); }
    public static void setHomeInviteTimeout(int v) { homeInviteTimeout = v; saveConfigValues(); }
    public static void setAllowTeleportRideEntity(boolean v) { allowTeleportRideEntity = v; saveConfigValues(); }
    public static void setRtpScope(int v) { rtpScope = v; saveConfigValues(); }
    public static void setRtpCooldownTime(int v) { rtpCooldownTime = v; saveConfigValues(); }
    public static void setMaxWarpCount(int v) { maxWarpCount = v; saveConfigValues(); }
    public static void setCooldownTime(long v) { cooldownTime = v; saveConfigValues(); }
    public static void setDefaultLanguage(String v) { defaultLanguage = v; saveConfigValues(); }
    public static void setSafeTeleport(boolean v) { safeTeleport = v; saveConfigValues(); }

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
            return Commands.LEVEL_GAMEMASTERS.check(source.permissions()); // 需要OP权限
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

    // ==================== 配置持久化 ====================
    private static Path getConfigFile() {
        return getConfigDir().resolve("config.json");
    }

    public static void loadConfigValues() {
        Path configFile = getConfigFile();
        if (!Files.exists(configFile)) {
            DebugLog.info("Config file not found, using defaults");
            return;
        }
        try (Reader reader = Files.newBufferedReader(configFile)) {
            ConfigData data = CONFIG_GSON.fromJson(reader, ConfigData.class);
            if (data == null) return;
            if (data.cooldownSeconds != null) cooldownSeconds = data.cooldownSeconds;
            if (data.waitSeconds != null) waitSeconds = data.waitSeconds;
            if (data.debugMode != null) debugMode = data.debugMode;
            if (data.maxHomes != null) maxHomes = data.maxHomes;
            if (data.homeInviteCooldown != null) homeInviteCooldown = data.homeInviteCooldown;
            if (data.homeInviteTimeout != null) homeInviteTimeout = data.homeInviteTimeout;
            if (data.allowTeleportRideEntity != null) allowTeleportRideEntity = data.allowTeleportRideEntity;
            if (data.databaseType != null) databaseType = data.databaseType;
            if (data.mysqlHost != null) mysqlHost = data.mysqlHost;
            if (data.mysqlPort != null) mysqlPort = data.mysqlPort;
            if (data.mysqlDatabase != null) mysqlDatabase = data.mysqlDatabase;
            if (data.mysqlUsername != null) mysqlUsername = data.mysqlUsername;
            if (data.mysqlPassword != null) mysqlPassword = data.mysqlPassword;
            if (data.rtpScope != null) rtpScope = data.rtpScope;
            if (data.rtpCooldownTime != null) rtpCooldownTime = data.rtpCooldownTime;
            if (data.rtpMaxAttempts != null) rtpMaxAttempts = data.rtpMaxAttempts;
            if (data.rtpMinY != null) rtpMinY = data.rtpMinY;
            if (data.rtpMaxY != null) rtpMaxY = data.rtpMaxY;
            if (data.maxWarpCount != null) maxWarpCount = data.maxWarpCount;
            if (data.cooldownTime != null) cooldownTime = data.cooldownTime;
            if (data.timeoutTicks != null) timeoutTicks = data.timeoutTicks;
            if (data.safeTeleport != null) safeTeleport = data.safeTeleport;
            if (data.defaultLanguage != null) defaultLanguage = data.defaultLanguage;
            DebugLog.info("Config loaded from {}", configFile);
        } catch (Exception e) {
            LOGGER.error("Failed to load config: {}", e.getMessage());
        }
    }

    public static void saveConfigValues() {
        ConfigData data = new ConfigData();
        data.cooldownSeconds = cooldownSeconds;
        data.waitSeconds = waitSeconds;
        data.debugMode = debugMode;
        data.maxHomes = maxHomes;
        data.homeInviteCooldown = homeInviteCooldown;
        data.homeInviteTimeout = homeInviteTimeout;
        data.allowTeleportRideEntity = allowTeleportRideEntity;
        data.databaseType = databaseType;
        data.mysqlHost = mysqlHost;
        data.mysqlPort = mysqlPort;
        data.mysqlDatabase = mysqlDatabase;
        data.mysqlUsername = mysqlUsername;
        data.mysqlPassword = mysqlPassword;
        data.rtpScope = rtpScope;
        data.rtpCooldownTime = rtpCooldownTime;
        data.rtpMaxAttempts = rtpMaxAttempts;
        data.rtpMinY = rtpMinY;
        data.rtpMaxY = rtpMaxY;
        data.maxWarpCount = maxWarpCount;
        data.cooldownTime = cooldownTime;
        data.timeoutTicks = timeoutTicks;
        data.safeTeleport = safeTeleport;
        data.defaultLanguage = defaultLanguage;
        try (Writer writer = Files.newBufferedWriter(getConfigFile())) {
            CONFIG_GSON.toJson(data, writer);
        } catch (Exception e) {
            LOGGER.error("Failed to save config: {}", e.getMessage());
        }
    }

    private static class ConfigData {
        Integer cooldownSeconds;
        Integer waitSeconds;
        Boolean debugMode;
        Integer maxHomes;
        Integer homeInviteCooldown;
        Integer homeInviteTimeout;
        Boolean allowTeleportRideEntity;
        String databaseType;
        String mysqlHost;
        Integer mysqlPort;
        String mysqlDatabase;
        String mysqlUsername;
        String mysqlPassword;
        Integer rtpScope;
        Integer rtpCooldownTime;
        Integer rtpMaxAttempts;
        Integer rtpMinY;
        Integer rtpMaxY;
        Integer maxWarpCount;
        Long cooldownTime;
        Integer timeoutTicks;
        Boolean safeTeleport;
        String defaultLanguage;
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
                                .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
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
                                                        setDefaultLanguage(lang);
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
                                                    setMaxHomes(count);
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
                                                    setCooldownTime(time * 1000L);
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
                                                    setWaitSeconds(time);
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
                                                    setHomeInviteTimeout(time);
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
                                                    setHomeInviteCooldown(time);
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
                                                    setAllowTeleportRideEntity(enable);
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
                                .then(Commands.literal("safeteleport")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            ModChatMenus.ConfigMenus.showSafeTeleportMenu(player);
                                            return 1;
                                        })
                                        .then(Commands.argument("enable", StringArgumentType.string())
                                                .suggests(BOOLEAN_SUGGESTIONS)
                                                .executes(context -> {
                                                    String enableStr = StringArgumentType.getString(context, "enable");
                                                    boolean enable = enableStr.equalsIgnoreCase("true");
                                                    setSafeTeleport(enable);
                                                    context.getSource().sendSuccess(
                                                            () -> ModUtils.translateWithFallback(
                                                                    enable ? "command.tpatool.config.safeteleport.enabled" : "command.tpatool.config.safeteleport.disabled",
                                                                    enable ? "Safe teleport enabled." : "Safe teleport disabled."
                                                            ),
                                                            true
                                                    );
                                                    DebugLog.info("Safe teleport {} by {}", enable ? "enabled" : "disabled", context.getSource().getDisplayName().getString());
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
                                                    setRtpCooldownTime(time);
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
                                                    setRtpScope(scope);
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
                                                    setMaxWarpCount(count);
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
                                .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
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
                                        setDebugMode(enable);
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
                                .requires(source -> Commands.LEVEL_ALL.check(source.permissions()))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    ModChatMenus.ConfigMenus.showAboutMenu(player);
                                    return 1;
                                })
                        )
        );
    }
}