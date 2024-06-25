package com.rexcantor64.triton.velocity.packetinterceptor.packets;

import com.rexcantor64.triton.Triton;
import com.rexcantor64.triton.api.config.FeatureSyntax;
import com.rexcantor64.triton.api.language.MessageParser;
import com.rexcantor64.triton.velocity.packetinterceptor.SimplePacketWrapper;
import com.rexcantor64.triton.velocity.player.VelocityLanguagePlayer;
import com.velocitypowered.proxy.protocol.packet.HeaderAndFooterPacket;
import com.velocitypowered.proxy.protocol.packet.LegacyPlayerListItemPacket;
import com.velocitypowered.proxy.protocol.packet.RemovePlayerInfoPacket;
import com.velocitypowered.proxy.protocol.packet.UpsertPlayerInfoPacket;
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder;
import lombok.val;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.function.Function;

public class TabHandler {

    private MessageParser parser() {
        return Triton.get().getMessageParser();
    }

    private boolean shouldNotTranslateTab() {
        return !Triton.get().getConfig().isTab();
    }

    private FeatureSyntax getTabSyntax() {
        return Triton.get().getConfig().getTabSyntax();
    }

    public void handlePlayerListHeaderFooter(
            final @NotNull SimplePacketWrapper<HeaderAndFooterPacket> wrapper,
            final @NotNull VelocityLanguagePlayer player
    ) {
        val packet = wrapper.getPacket();
        if (shouldNotTranslateTab()) return;
        player.setLastTabHeader(packet.getHeader().getComponent());
        player.setLastTabFooter(packet.getFooter().getComponent());
        final Function<Component, ComponentHolder> translate = component ->
                parser()
                        .translateComponent(component, player, getTabSyntax())
                        .getResultOrToRemove(Component::empty)
                        .map(result -> new ComponentHolder(player.getProtocolVersion(), result))
                        .orElse(null);
        val newHeader=  translate.apply(packet.getHeader().getComponent());
        val newFooter = translate.apply(packet.getFooter().getComponent());
        if (newHeader != null && newFooter != null) {
            wrapper.setModifiedPacket(new HeaderAndFooterPacket(newHeader, newFooter));
        }
    }

    public void handlePlayerListItem(
            @NotNull SimplePacketWrapper<LegacyPlayerListItemPacket> wrapper,
            @NotNull VelocityLanguagePlayer player
    ) {
        if (shouldNotTranslateTab()) return;
        val packet = wrapper.getPacket();
        for (LegacyPlayerListItemPacket.Item item : packet.getItems()) {
            val uuid = item.getUuid();
            switch (packet.getAction()) {
                case LegacyPlayerListItemPacket.ADD_PLAYER:
                case LegacyPlayerListItemPacket.UPDATE_DISPLAY_NAME:
                    if (item.getDisplayName() == null) {
                        player.deleteCachedPlayerListItem(uuid);
                        break;
                    }
                    val result = parser()
                            .translateComponent(
                                    item.getDisplayName(),
                                    player,
                                    getTabSyntax()
                            );
                    if (result.isChanged()) {
                        player.cachePlayerListItem(uuid, item.getDisplayName());
                        item.setDisplayName(result.getResultRaw());
                    } else {
                        player.deleteCachedPlayerListItem(uuid);
                    }
                    break;
                case LegacyPlayerListItemPacket.REMOVE_PLAYER:
                    player.deleteCachedPlayerListItem(uuid);
                    break;
            }
        }
    }

    public void handleUpsertPlayerInfo(@NotNull SimplePacketWrapper<UpsertPlayerInfoPacket> wrapper, @NotNull VelocityLanguagePlayer player) {
        val packet = wrapper.getPacket();
        if (shouldNotTranslateTab() || !packet.getActions().contains(UpsertPlayerInfoPacket.Action.UPDATE_DISPLAY_NAME)) {
            return;
        }
        for (UpsertPlayerInfoPacket.Entry item : packet.getEntries()) {
            val uuid = item.getProfileId();
            if (item.getDisplayName() == null) {
                player.deleteCachedPlayerListItem(uuid);
                break;
            }
            val result = parser()
                    .translateComponent(
                            item.getDisplayName().getComponent(),
                            player,
                            getTabSyntax()
                    );
            if (result.isChanged()) {
                player.cachePlayerListItem(uuid, item.getDisplayName().getComponent());
                item.setDisplayName(new ComponentHolder(player.getProtocolVersion(), result.getResultRaw()));
            } else {
                player.deleteCachedPlayerListItem(uuid);
            }
            break;
        }
    }

    public void handleRemovePlayerInfo(@NotNull SimplePacketWrapper<RemovePlayerInfoPacket> wrapper, @NotNull VelocityLanguagePlayer player) {
        if (shouldNotTranslateTab()) {
            return;
        }
        for (UUID uuid : wrapper.getPacket().getProfilesToRemove()) {
            player.deleteCachedPlayerListItem(uuid);
        }
    }
}
