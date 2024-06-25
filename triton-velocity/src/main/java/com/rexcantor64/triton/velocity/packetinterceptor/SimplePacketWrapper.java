package com.rexcantor64.triton.velocity.packetinterceptor;

import com.velocitypowered.proxy.protocol.MinecraftPacket;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


@Getter
@RequiredArgsConstructor
public final class SimplePacketWrapper<T extends MinecraftPacket> {
    private final @NotNull T packet;
    @Setter private boolean isCancelled;
    private boolean modified;
    private @Nullable T modifiedPacket;

    public void setModifiedPacket(final @Nullable T modifiedPacket) {
        modified = (modifiedPacket != null);
        this.modifiedPacket = modifiedPacket;
    }

    public void handleModified(final @Nullable T modifiedPacket) {
        if (modifiedPacket != null) {
            if (packet != modifiedPacket) setModifiedPacket(modifiedPacket);
        } else {
            setCancelled(true);
        }
    }
}
