package com.rexcantor64.triton.velocity.packetinterceptor.packets;

import com.rexcantor64.triton.Triton;
import com.rexcantor64.triton.api.config.FeatureSyntax;
import com.rexcantor64.triton.api.language.MessageParser;
import com.rexcantor64.triton.velocity.packetinterceptor.SimplePacketWrapper;
import com.rexcantor64.triton.velocity.player.VelocityLanguagePlayer;
import com.velocitypowered.proxy.protocol.packet.BossBarPacket;
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder;
import lombok.val;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

public class BossBarHandler {

    private MessageParser parser() {
        return Triton.get().getMessageParser();
    }

    private boolean shouldNotTranslateBossBars() {
        return !Triton.get().getConfig().isBossbars();
    }


    private FeatureSyntax getBossBarSyntax() {
        return Triton.get().getConfig().getBossbarSyntax();
    }


    public void handleBossBar(
            @NotNull SimplePacketWrapper<BossBarPacket> wrapper,
            @NotNull VelocityLanguagePlayer player
    ) {
        if (shouldNotTranslateBossBars()) {
            return;
        }

        val packet = wrapper.getPacket();
        val action = packet.getAction();

        if (action == BossBarPacket.REMOVE) {
            player.removeBossbar(packet.getUuid());
            return;
        }

        val text = packet.getName();
        if (text != null && (action == BossBarPacket.ADD || action == BossBarPacket.UPDATE_NAME)) {
            player.setBossbar(packet.getUuid(), packet.getName().getComponent());

            parser()
                    .translateComponent(
                            packet.getName().getComponent(),
                            player,
                            getBossBarSyntax()
                    )
                    .getResultOrToRemove(Component::empty)
                    .map(result -> new ComponentHolder(player.getProtocolVersion(), result))
                    .ifPresent(packet::setName);
        }
    }
}
