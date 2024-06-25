package com.rexcantor64.triton.velocity.packetinterceptor;

import com.rexcantor64.triton.velocity.player.VelocityLanguagePlayer;
import com.velocitypowered.proxy.protocol.packet.ClientSettingsPacket;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class VelocityNettyDecoder extends ChannelInboundHandlerAdapter {

    private final VelocityLanguagePlayer player;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ClientSettingsPacket) {
            ClientSettingsPacket cs = (ClientSettingsPacket) msg;
            player.setClientLocale(cs.getLocale());
        }
        super.channelRead(ctx, msg);
    }
}
