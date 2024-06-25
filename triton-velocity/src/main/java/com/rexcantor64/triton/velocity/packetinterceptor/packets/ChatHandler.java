package com.rexcantor64.triton.velocity.packetinterceptor.packets;

import com.rexcantor64.triton.Triton;
import com.rexcantor64.triton.velocity.packetinterceptor.SimplePacketWrapper;
import com.rexcantor64.triton.velocity.player.VelocityLanguagePlayer;
import com.rexcantor64.triton.velocity.utils.ComponentUtils;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.packet.chat.ChatType;
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder;
import com.velocitypowered.proxy.protocol.packet.chat.SystemChatPacket;
import com.velocitypowered.proxy.protocol.packet.chat.legacy.LegacyChatPacket;
import lombok.val;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

public class ChatHandler {

    public void handleSystemChat(@NotNull SimplePacketWrapper<SystemChatPacket> wrapper, @NotNull VelocityLanguagePlayer player) {
        handleChat(wrapper, player, it -> it.getType() == ChatType.GAME_INFO,
                it -> it.getComponent().getComponent(),
                it -> new ComponentHolder(player.getProtocolVersion(), it),
                ((i1, i2) -> new SystemChatPacket(i2, i1.getType()))
        );
    }

    public void handleLegacyChat(@NotNull SimplePacketWrapper<LegacyChatPacket> wrapper, @NotNull VelocityLanguagePlayer player) {
        final boolean actionBar = wrapper.getPacket().getType() == 2;
        handleChat(wrapper, player, it -> actionBar, it ->
                ComponentUtils.deserializeFromJson(it.getMessage(), player.getProtocolVersion()),
                result -> ComponentUtils.serializeToJson(result, player.getProtocolVersion(), actionBar),
                ((i1, i2) -> new LegacyChatPacket(i2, i1.getType(), i1.getSenderUuid()))
        );
    }

    private <T extends MinecraftPacket, U> void handleChat(
            final @NotNull SimplePacketWrapper<T> wrapper,
            final @NotNull VelocityLanguagePlayer player,
            final @NotNull Predicate<T> isActionbar,
            final @NotNull Function<T, Component> toComponent,
            final @NotNull Function<Component, U> map,
            final @NotNull BiFunction<T, U, T> clone
    ) {
        final @NotNull T packet = wrapper.getPacket();
        boolean actionBar = isActionbar.test(packet);
        val config = Triton.get().getConfig();
        if ((!actionBar && !config.isChat()) || (actionBar && !config.isActionbars())) return;
        val modified = Triton
                .get()
                .getMessageParser()
                .translateComponent(toComponent.apply(packet), player, actionBar ? config.getActionbarSyntax() : config.getChatSyntax())
                .map(map)
                .mapToObj(
                        result -> clone.apply(packet, result),
                        () -> packet,
                        () -> null
                );
        wrapper.handleModified(modified);
    }

}
