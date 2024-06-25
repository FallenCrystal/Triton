package com.rexcantor64.triton.velocity.packetinterceptor;

import com.rexcantor64.triton.velocity.packetinterceptor.packets.*;
import com.rexcantor64.triton.velocity.player.VelocityLanguagePlayer;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.packet.*;
import com.velocitypowered.proxy.protocol.packet.chat.SystemChatPacket;
import com.velocitypowered.proxy.protocol.packet.chat.legacy.LegacyChatPacket;
import com.velocitypowered.proxy.protocol.packet.title.LegacyTitlePacket;
import com.velocitypowered.proxy.protocol.packet.title.TitleActionbarPacket;
import com.velocitypowered.proxy.protocol.packet.title.TitleSubtitlePacket;
import com.velocitypowered.proxy.protocol.packet.title.TitleTextPacket;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
public class VelocityNettyEncoder extends ChannelOutboundHandlerAdapter {

    private final VelocityLanguagePlayer player;
    private static final Set<PacketHandler<?>> handler = new HashSet<>();
    private static final Map<Class<?>, PacketHandler<?>> handlerMap = new HashMap<>();

    @RequiredArgsConstructor
    private static final class PacketHandler<T extends MinecraftPacket> {
        private final @NotNull Class<T> packetClass;
        private final @NotNull PacketConsumer<T> consumer;

        public @NotNull SimplePacketWrapper<T> handle(final @NotNull T packet, final @NotNull VelocityLanguagePlayer player) {
            final @NotNull SimplePacketWrapper<T> wrapper = new SimplePacketWrapper<>(packet);
            consumer.handle(wrapper, player);
            return wrapper;
        }

        @SuppressWarnings("unchecked")
        public @Nullable SimplePacketWrapper<?> handleGeneric(final @NotNull MinecraftPacket packet, final @NotNull VelocityLanguagePlayer player) {
            if (packetClass != packet.getClass()) {
                return null;
            }
            return handle((T) packet, player);
        }

        interface PacketConsumer<T extends MinecraftPacket> {
            void handle(final @NotNull SimplePacketWrapper<T> wrapper, final @NotNull VelocityLanguagePlayer player);
        }
    }

    static {
        val chatHandler = new ChatHandler();
        handler.add(new PacketHandler<>(SystemChatPacket.class, chatHandler::handleSystemChat));
        handler.add(new PacketHandler<>(LegacyChatPacket.class, chatHandler::handleLegacyChat));

        val titleHandler = new TitleHandler();
        handler.add(new PacketHandler<>(TitleTextPacket.class, titleHandler::handle));
        handler.add(new PacketHandler<>(TitleSubtitlePacket.class, titleHandler::handle));
        handler.add(new PacketHandler<>(TitleActionbarPacket.class, titleHandler::handle));
        handler.add(new PacketHandler<>(LegacyTitlePacket.class, titleHandler::handleLegacy));

        val tabHandler = new TabHandler();
        handler.add(new PacketHandler<>(HeaderAndFooterPacket.class, tabHandler::handlePlayerListHeaderFooter));
        handler.add(new PacketHandler<>(LegacyPlayerListItemPacket.class, tabHandler::handlePlayerListItem));
        handler.add(new PacketHandler<>(UpsertPlayerInfoPacket.class, tabHandler::handleUpsertPlayerInfo));
        handler.add(new PacketHandler<>(RemovePlayerInfoPacket.class, tabHandler::handleRemovePlayerInfo));

        handler.add(new PacketHandler<>(DisconnectPacket.class, new DisconnectHandler()::handleDisconnect));
        handler.add(new PacketHandler<>(ResourcePackRequestPacket.class, new ResourcePackHandler()::handleResourcePackRequest));
        handler.add(new PacketHandler<>(BossBarPacket.class, new BossBarHandler()::handleBossBar));

        for (final PacketHandler<?> handler : handler) {
            handlerMap.put(handler.packetClass, handler);
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof MinecraftPacket) {
            val packet = (MinecraftPacket) msg;
            val handler = handlerMap.get(packet.getClass());
            if (handler != null) {
                val wrapper = handler.handleGeneric(packet, player);
                if (wrapper != null) {
                    if (wrapper.isCancelled()) {
                        return;
                    } else if (wrapper.isModified()) {
                        super.write(ctx, wrapper.getModifiedPacket(), promise);
                        return;
                    }
                }
            }
        }
        super.write(ctx, msg, promise);
    }
}
