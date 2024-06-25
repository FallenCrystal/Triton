package com.rexcantor64.triton.velocity.packetinterceptor.packets;

import com.rexcantor64.triton.Triton;
import com.rexcantor64.triton.api.config.FeatureSyntax;
import com.rexcantor64.triton.api.language.MessageParser;
import com.rexcantor64.triton.velocity.packetinterceptor.SimplePacketWrapper;
import com.rexcantor64.triton.velocity.player.VelocityLanguagePlayer;
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder;
import com.velocitypowered.proxy.protocol.packet.title.GenericTitlePacket;
import com.velocitypowered.proxy.protocol.packet.title.LegacyTitlePacket;
import com.velocitypowered.proxy.protocol.packet.title.TitleActionbarPacket;
import lombok.val;
import org.jetbrains.annotations.NotNull;

public class TitleHandler {

    private MessageParser parser() {
        return Triton.get().getMessageParser();
    }

    private boolean shouldNotTranslateTitles() {
        return !Triton.get().getConfig().isTitles();
    }

    private boolean shouldNotTranslateActionBars() {
        return !Triton.get().getConfig().isActionbars();
    }

    private FeatureSyntax getTitleSyntax() {
        return Triton.get().getConfig().getTitleSyntax();
    }

    private FeatureSyntax getActionBarSyntax() {
        return Triton.get().getConfig().getActionbarSyntax();
    }

    private boolean isActionBarPacket(GenericTitlePacket titlePacket) {
        if (titlePacket instanceof LegacyTitlePacket) {
            return titlePacket.getAction() == GenericTitlePacket.ActionType.SET_ACTION_BAR;
        }
        return titlePacket instanceof TitleActionbarPacket;
    }

    public boolean handleGenericTitle(
            @NotNull GenericTitlePacket titlePacket,
            @NotNull VelocityLanguagePlayer player
    ) {
        val isActionBarPacket = isActionBarPacket(titlePacket);
        if (isActionBarPacket ? shouldNotTranslateActionBars() : shouldNotTranslateTitles()) {
            return false;
        }

        val modified = parser()
                .translateComponent(
                        titlePacket.getComponent().getComponent(),
                        player,
                        isActionBarPacket ? getActionBarSyntax() : getTitleSyntax()
                )
                .map(result -> new ComponentHolder(player.getProtocolVersion(), result))
                .mapToObj(
                        result -> {
                            titlePacket.setComponent(result);
                            return titlePacket;
                        },
                        () -> titlePacket,
                        () -> null
                );
        return modified == null;
    }

    public <T extends GenericTitlePacket> void handle(final @NotNull SimplePacketWrapper<T> wrapper, final @NotNull VelocityLanguagePlayer player) {
        wrapper.setCancelled(handleGenericTitle(wrapper.getPacket(), player));
    }

    public void handleLegacy(final @NotNull SimplePacketWrapper<LegacyTitlePacket> wrapper, final @NotNull VelocityLanguagePlayer player) {
        if (wrapper.getPacket().getAction().ordinal() < 3)  // SET_TITLE SET_SUBTITLE, SET_ACTION_BAR,
            handle(wrapper, player);
    }
}
