package com.rexcantor64.triton.velocity.packetinterceptor.packets;

import com.rexcantor64.triton.Triton;
import com.rexcantor64.triton.api.config.FeatureSyntax;
import com.rexcantor64.triton.api.language.MessageParser;
import com.rexcantor64.triton.velocity.packetinterceptor.SimplePacketWrapper;
import com.rexcantor64.triton.velocity.player.VelocityLanguagePlayer;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.packet.DisconnectPacket;
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder;
import lombok.val;
import org.jetbrains.annotations.NotNull;

public class DisconnectHandler {

    private MessageParser parser() {
        return Triton.get().getMessageParser();
    }

    private boolean shouldNotTranslateKick() {
        return !Triton.get().getConfig().isKick();
    }

    private FeatureSyntax getKickSyntax() {
        return Triton.get().getConfig().getKickSyntax();
    }

    public void handleDisconnect(@NotNull SimplePacketWrapper<DisconnectPacket> wrapper, @NotNull VelocityLanguagePlayer player) {
        if (shouldNotTranslateKick()) {
            return;
        }
        val packet = wrapper.getPacket();
        val result = parser()
                .translateComponent(
                        packet.getReason().getComponent(),
                        player,
                        getKickSyntax()
                )
                .map(it ->
                    // During the Login phase, this packet is supposed to send JSON text even on 1.20.3+ (instead of NBT data)
                    new ComponentHolder(
                            ((ConnectedPlayer) player.getParent()).getConnection().getState() == StateRegistry.LOGIN ? ProtocolVersion.MINECRAFT_1_20_2 : player.getProtocolVersion(),
                            it
                    )
                );
        if (result.isChanged()) {
            packet.setReason(result.getResultRaw());
        } else {
            wrapper.setCancelled(true); // ?
        }
    }

}
