package com.rexcantor64.triton.velocity.packetinterceptor.packets;

import com.rexcantor64.triton.Triton;
import com.rexcantor64.triton.api.config.FeatureSyntax;
import com.rexcantor64.triton.api.language.MessageParser;
import com.rexcantor64.triton.velocity.packetinterceptor.SimplePacketWrapper;
import com.rexcantor64.triton.velocity.player.VelocityLanguagePlayer;
import com.velocitypowered.proxy.protocol.packet.ResourcePackRequestPacket;
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder;
import lombok.val;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

public class ResourcePackHandler {

    private MessageParser parser() {
        return Triton.get().getMessageParser();
    }

    private boolean shouldNotTranslateResourcePack() {
        return !Triton.get().getConfig().isResourcePackPrompt();
    }


    private FeatureSyntax getResourcePackSyntax() {
        return Triton.get().getConfig().getResourcePackPromptSyntax();
    }


    public void handleResourcePackRequest(
            @NotNull SimplePacketWrapper<ResourcePackRequestPacket> wrapper,
            @NotNull VelocityLanguagePlayer player
    ) {
        val packet = wrapper.getPacket();
        if (shouldNotTranslateResourcePack() || packet.getPrompt() == null) {
            return;
        }

        parser().translateComponent(
                        packet.getPrompt().getComponent(),
                        player,
                        getResourcePackSyntax()
                )
                .getResultOrToRemove(Component::empty)
                .map(result -> new ComponentHolder(player.getProtocolVersion(), result))
                .ifPresent(packet::setPrompt);
    }
}
