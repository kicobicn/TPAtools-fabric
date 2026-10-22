package com.kicobicn.TPATools.chat;

import com.kicobicn.TPATools.config.ModConfigs;
import com.kicobicn.TPATools.util.ModUtils;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatHandler {
    private static final Logger LOGGER = LogManager.getLogger("TPAtools");

    /** &1 ~ &9：展示快捷栏对应槽位的物品 */
    private static final Pattern ITEM_PATTERN = Pattern.compile("&([1-9])");

    /** 判断单词边界用的标点集合（提及只在“完整单词”上生效，避免 Ann 命中 Anna） */
    private static final String PUNCTUATION = " \t\n,，.。!！?？:：;；\"'“”‘’()（）[]【】<>《》-_/\\|+*~^%$#@=";

    public static void registerEvents() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            try {
                String originalMessage = message.decoratedContent().getString();
                ModConfigs.DebugLog.info("Processing chat message from {}: {}",
                        sender.getName().getString(), originalMessage);

                BuildResult result = buildMessage(originalMessage, sender);
                if (!result.modified()) {
                    return true;
                }

                // 只在消息确实被改写（提及标色 / 物品展示）时才取消原消息并重新广播
                MutableComponent chatMessage = Component.literal("<")
                        .append(sender.getDisplayName())
                        .append(Component.literal("> "))
                        .append(result.component());

                // 只使用工程中已有的 API（ServerPlayer#sendSystemMessage），避免依赖新版未验证的方法
                sender.level().getServer().execute(() -> sender.level().getServer()
                        .getPlayerList().getPlayers()
                        .forEach(target -> target.sendSystemMessage(chatMessage)));
                LOGGER.info("<{}> {}", sender.getName().getString(), result.component().getString());
                return false;
            } catch (Exception e) {
                LOGGER.error("Error processing chat message", e);
                return true;
            }
        });
    }

    /**
     * 把原始聊天文本转换为带样式/物品组件的结果。
     * <p>一次扫描同时处理两个功能，避免对已经加过样式码的字符串再做正则替换。</p>
     */
    private static BuildResult buildMessage(String raw, ServerPlayer sender) {
        List<Object> parts = new ArrayList<>();
        boolean modified = false;
        Set<UUID> pinged = new HashSet<>();

        Matcher itemMatcher = ITEM_PATTERN.matcher(raw);
        int lastIndex = 0;
        while (itemMatcher.find()) {
            if (itemMatcher.start() > lastIndex) {
                String plain = raw.substring(lastIndex, itemMatcher.start());
                if (appendMentionParts(parts, plain, sender, pinged)) {
                    modified = true;
                }
            }

            int slot = Integer.parseInt(itemMatcher.group(1)) - 1;
            ItemStack itemStack = sender.getInventory().getItem(slot);
            if (!itemStack.isEmpty()) {
                parts.add(createItemComponent(itemStack));
                modified = true;
                ModConfigs.DebugLog.info("Replaced &{} with item: {}", slot + 1,
                        itemStack.getDisplayName().getString());
            } else {
                parts.add(itemMatcher.group(0));
            }
            lastIndex = itemMatcher.end();
        }

        if (lastIndex < raw.length()) {
            if (appendMentionParts(parts, raw.substring(lastIndex), sender, pinged)) {
                modified = true;
            }
        }

        MutableComponent component = Component.literal("");
        for (Object part : parts) {
            if (part instanceof Component c) {
                component.append(c);
            } else {
                component.append(Component.literal(String.valueOf(part)));
            }
        }
        return new BuildResult(component, modified);
    }

    /**
     * 按空白与标点切分文本，对“完整单词”形式的在线玩家名标色并发送提醒，
     * 返回是否发生了修改。
     */
    private static boolean appendMentionParts(List<Object> parts, String text, ServerPlayer sender, Set<UUID> pinged) {
        if (text.isEmpty()) {
            return false;
        }

        boolean modified = false;
        int index = 0;
        int length = text.length();
        while (index < length) {
            // 跳过前导标点
            int tokenStart = index;
            while (tokenStart < length && isPunctuation(text.charAt(tokenStart))) {
                tokenStart++;
            }
            if (tokenStart > index) {
                parts.add(text.substring(index, tokenStart));
            }
            if (tokenStart >= length) {
                break;
            }

            int tokenEnd = tokenStart;
            while (tokenEnd < length && !isPunctuation(text.charAt(tokenEnd))) {
                tokenEnd++;
            }

            String token = text.substring(tokenStart, tokenEnd);
            ServerPlayer mentioned = resolveMention(token, sender);
            if (mentioned != null) {
                MutableComponent highlight = Component.literal(token)
                        .withStyle(ChatFormatting.AQUA);
                parts.add(highlight);
                modified = true;
                if (pinged.add(mentioned.getUUID())) {
                    notifyMention(mentioned, sender);
                }
            } else {
                parts.add(token);
            }
            index = tokenEnd;
        }
        return modified;
    }

    private static boolean isPunctuation(char c) {
        return PUNCTUATION.indexOf(c) >= 0;
    }

    /** @ 前缀在提醒时忽略，例如 “@Steve” 与 “Steve” 等价 */
    private static String stripAt(String token) {
        return token.startsWith("@") ? token.substring(1) : token;
    }

    private static ServerPlayer resolveMention(String token, ServerPlayer sender) {
        String name = stripAt(token);
        if (name.isEmpty()) {
            return null;
        }
        for (ServerPlayer candidate : sender.level().getServer().getPlayerList().getPlayers()) {
            if (candidate.getUUID().equals(sender.getUUID())) {
                continue;
            }
            if (candidate.getName().getString().equalsIgnoreCase(name)) {
                return candidate;
            }
        }
        return null;
    }

    private static void notifyMention(ServerPlayer target, ServerPlayer sender) {
        if (target.connection == null) {
            return;
        }
        ModConfigs.DebugLog.info("Player {} mentioned {}", sender.getName().getString(),
                target.getName().getString());

        ClientboundSetTitleTextPacket titlePacket = new ClientboundSetTitleTextPacket(
                ModUtils.translateWithFallback(
                        "command.tpatools.chat.mention",
                        "%s mentioned you",
                        sender.getName()).withStyle(ChatFormatting.AQUA));
        ClientboundSetTitlesAnimationPacket timesPacket = new ClientboundSetTitlesAnimationPacket(10, 40, 10);
        target.connection.send(timesPacket);
        target.connection.send(titlePacket);
        target.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 1.0F);
    }

    private static MutableComponent createItemComponent(ItemStack itemStack) {
        MutableComponent itemDisplayName = itemStack.getDisplayName().copy();
        return Component.literal("[")
                .withStyle(ChatFormatting.GOLD)
                .append(itemDisplayName)
                .append(Component.literal("]").withStyle(ChatFormatting.GOLD));
    }

    /** buildMessage 的结果：组件 + 是否被改写 */
    private record BuildResult(MutableComponent component, boolean modified) {
    }
}
