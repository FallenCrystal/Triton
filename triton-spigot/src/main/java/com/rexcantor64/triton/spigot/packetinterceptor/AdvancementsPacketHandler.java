package com.rexcantor64.triton.spigot.packetinterceptor;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.accessors.Accessors;
import com.comphenix.protocol.reflect.accessors.FieldAccessor;
import com.comphenix.protocol.reflect.accessors.MethodAccessor;
import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.wrappers.Converters;
import com.comphenix.protocol.wrappers.MinecraftKey;
import com.rexcantor64.triton.spigot.player.SpigotLanguagePlayer;
import com.rexcantor64.triton.spigot.utils.NMSUtils;
import com.rexcantor64.triton.spigot.utils.WrappedComponentUtils;
import com.rexcantor64.triton.spigot.wrappers.WrappedAdvancementDisplay;
import lombok.val;
import org.bukkit.Bukkit;

import java.util.Arrays;
import java.util.Map;

import static com.rexcantor64.triton.spigot.packetinterceptor.HandlerFunction.asAsync;

public class AdvancementsPacketHandler extends PacketHandler {

    private final Class<?> SERIALIZED_ADVANCEMENT_CLASS;
    private final FieldAccessor ADVANCEMENT_DISPLAY_FIELD;
    private final FieldAccessor ENTITY_PLAYER_ADVANCEMENT_DATA_PLAYER_FIELD;
    private final MethodAccessor ADVANCEMENT_DATA_PLAYER_REFRESH_METHOD;
    private final MethodAccessor CRAFT_SERVER_GET_SERVER_METHOD;
    private final MethodAccessor MINECRAFT_SERVER_GET_ADVANCEMENT_DATA_METHOD;
    private final MethodAccessor ADVANCEMENT_DATA_PLAYER_LOAD_FROM_ADVANCEMENT_DATA_WORLD_METHOD;

    public AdvancementsPacketHandler() {
        SERIALIZED_ADVANCEMENT_CLASS = MinecraftReflection.getMinecraftClass("advancements.Advancement$SerializedAdvancement", "Advancement$SerializedAdvancement");
        ADVANCEMENT_DISPLAY_FIELD = Accessors.getFieldAccessor(SERIALIZED_ADVANCEMENT_CLASS, WrappedAdvancementDisplay.getWrappedClass(), true);
        val advancementDataPlayerClass = MinecraftReflection.getMinecraftClass("server.AdvancementDataPlayer", "AdvancementDataPlayer");
        ENTITY_PLAYER_ADVANCEMENT_DATA_PLAYER_FIELD = Accessors.getFieldAccessor(MinecraftReflection.getEntityPlayerClass(), advancementDataPlayerClass, true);
        ADVANCEMENT_DATA_PLAYER_REFRESH_METHOD = Accessors.getMethodAccessor(advancementDataPlayerClass, "b", MinecraftReflection.getEntityPlayerClass());

        val advancementDataWorldClass = MinecraftReflection.getMinecraftClass("server.AdvancementDataWorld", "AdvancementDataWorld");
        CRAFT_SERVER_GET_SERVER_METHOD = Accessors.getMethodAccessor(MinecraftReflection.getCraftBukkitClass("CraftServer"), "getServer");
        MINECRAFT_SERVER_GET_ADVANCEMENT_DATA_METHOD = Accessors.getMethodAccessor(Arrays.stream(MinecraftReflection.getMinecraftServerClass().getMethods())
                .filter(m -> m.getReturnType() == advancementDataWorldClass).findAny()
                .orElseThrow(() -> new RuntimeException("Unable to find method getAdvancementData([])")));

        if (getMcVersion() < 16) {
            // MC 1.12-1.15
            // Loading of achievements only needs the method to be called without any parameters
            ADVANCEMENT_DATA_PLAYER_LOAD_FROM_ADVANCEMENT_DATA_WORLD_METHOD = Accessors.getMethodAccessor(advancementDataPlayerClass, "b");
        } else {
            // MC 1.16+
            // Loading of achievements requires an AdvancementDataWorld method
            ADVANCEMENT_DATA_PLAYER_LOAD_FROM_ADVANCEMENT_DATA_WORLD_METHOD = Accessors.getMethodAccessor(advancementDataPlayerClass, "a", advancementDataWorldClass);
        }
    }

    /**
     * @return Whether the plugin should attempt to translate advancements
     */
    private boolean areAdvancementsDisabled() {
        return !getMain().getConfig().isAdvancements();
    }

    /**
     * @return Whether the plugin should attempt to refresh translated advancements
     */
    private boolean areAdvancementsRefreshDisabled() {
        return areAdvancementsDisabled() || !getMain().getConfig().isAdvancementsRefresh();
    }

    private void handleAdvancements(PacketEvent packet, SpigotLanguagePlayer languagePlayer) {
        if (areAdvancementsDisabled()) return;

        val serializedAdvancementMap = packet.getPacket().getMaps(MinecraftKey.getConverter(), Converters.passthrough(SERIALIZED_ADVANCEMENT_CLASS)).readSafely(0);

        for (Object serializedAdvancement : serializedAdvancementMap.values()) {
            val advancementDisplayHandle = ADVANCEMENT_DISPLAY_FIELD.get(serializedAdvancement);
            if (advancementDisplayHandle == null) {
                continue;
            }

            val advancementDisplay = WrappedAdvancementDisplay.fromHandle(advancementDisplayHandle).shallowClone();

            parser()
                    .translateComponent(
                            WrappedComponentUtils.deserialize(advancementDisplay.getTitle()),
                            languagePlayer,
                            getConfig().getAdvancementsSyntax()
                    )
                    .map(WrappedComponentUtils::serialize)
                    .ifChanged(advancementDisplay::setTitle);
            parser()
                    .translateComponent(
                            WrappedComponentUtils.deserialize(advancementDisplay.getDescription()),
                            languagePlayer,
                            getConfig().getAdvancementsSyntax()
                    )
                    .map(WrappedComponentUtils::serialize)
                    .ifChanged(advancementDisplay::setDescription);

            ADVANCEMENT_DISPLAY_FIELD.set(serializedAdvancement, advancementDisplay.getHandle());
        }
    }

    /**
     * Forcefully refresh the advancements for a given player.
     * To achieve this, achievements are loaded from the server's state onto the Player,
     * and then sent. This is what NMS does under the hood, we're just doing the same thing here manually.
     *
     * @param languagePlayer The player to refresh the advancements for
     */
    public void refreshAdvancements(SpigotLanguagePlayer languagePlayer) {
        if (areAdvancementsRefreshDisabled()) return;

        languagePlayer.toBukkit().ifPresent(bukkitPlayer -> {
            val nmsPlayer = NMSUtils.getHandle(bukkitPlayer);

            val advancementDataPlayer = ENTITY_PLAYER_ADVANCEMENT_DATA_PLAYER_FIELD.get(nmsPlayer);

            Bukkit.getScheduler().runTask(getMain().getLoader(), () -> {
                // These are the same methods that are called from org.bukkit.craftbukkit.<version>.util.CraftMagicNumbers#loadAdvancement
                if (getMcVersion() < 16) {
                    // MC 1.12-1.15
                    ADVANCEMENT_DATA_PLAYER_LOAD_FROM_ADVANCEMENT_DATA_WORLD_METHOD.invoke(advancementDataPlayer);
                } else {
                    // MC 1.16+
                    val minecraftServer = CRAFT_SERVER_GET_SERVER_METHOD.invoke(Bukkit.getServer());
                    val advancementDataWorld = MINECRAFT_SERVER_GET_ADVANCEMENT_DATA_METHOD.invoke(minecraftServer);
                    ADVANCEMENT_DATA_PLAYER_LOAD_FROM_ADVANCEMENT_DATA_WORLD_METHOD.invoke(advancementDataPlayer, advancementDataWorld);
                }
                ADVANCEMENT_DATA_PLAYER_REFRESH_METHOD.invoke(advancementDataPlayer, nmsPlayer);
            });
        });
    }

    @Override
    public void registerPacketTypes(Map<PacketType, HandlerFunction> registry) {
        registry.put(PacketType.Play.Server.ADVANCEMENTS, asAsync(this::handleAdvancements));
    }
}
