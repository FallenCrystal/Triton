package com.rexcantor64.triton.packetinterceptor.pe;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlaySendEvent;
import com.github.retrooper.packetevents.event.simple.PacketStatusSendEvent;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTString;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import com.github.retrooper.packetevents.wrapper.status.server.WrapperStatusServerResponse;
import com.google.gson.Gson;
import com.google.gson.JsonPrimitive;
import com.rexcantor64.triton.SpigotMLP;
import com.rexcantor64.triton.config.MainConfig;
import com.rexcantor64.triton.player.SpigotLanguagePlayer;
import com.rexcantor64.triton.wrappers.AdventureComponentWrapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.val;
import net.kyori.adventure.text.Component;
import net.md_5.bungee.chat.ComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

@SuppressWarnings("unused")
public final class PacketEventsHandler extends SimplePacketListenerAbstract {
    @Getter private static @Nullable PacketEventsHandler instance = null;

    private final @NotNull PacketEventsAPI<?> api = PacketEvents.getAPI();
    private final @NotNull SpigotMLP main;

    private final @NotNull Gson gson = new Gson();

    public PacketEventsHandler(final @NotNull SpigotMLP main) {
        super(PacketListenerPriority.LOW);
        instance = this;
        this.main = main;
    }

    private final List<Handler<?>> HANDLERS = Arrays.asList(
            new Handler<>(
                    PacketType.Play.Server.SYSTEM_CHAT_MESSAGE,
                    WrapperPlayServerSystemChatMessage::new,
                    this::handleSystemChat
            ),
            new Handler<>(
                    PacketType.Play.Server.PLAYER_LIST_HEADER_AND_FOOTER,
                    WrapperPlayServerPlayerListHeaderAndFooter::new,
                    Handler.WrapperHandler.of(this::handlePlayerListHeaderFooter)
            ),
            new Handler<>(
                    PacketType.Play.Server.OPEN_WINDOW,
                    WrapperPlayServerOpenWindow::new,
                    Handler.WrapperHandler.of(this::handleOpenWindow)
            ),
            new Handler<>(
                    PacketType.Play.Server.DISCONNECT,
                    WrapperPlayServerDisconnect::new,
                    Handler.WrapperHandler.of(this::handleDisconnect)
            ),
            new Handler<>(
                    PacketType.Play.Server.TEAMS,
                    WrapperPlayServerTeams::new,
                    Handler.WrapperHandler.of(this::handleScoreboardTeam)
            ),
            new Handler<>(
                    PacketType.Play.Server.BLOCK_ENTITY_DATA,
                    WrapperPlayServerBlockEntityData::new,
                    Handler.WrapperHandler.of(this::handleTileData)
            )
    );

    @AllArgsConstructor
    private static final class Handler<W extends PacketWrapper<?>> {
        private final @NotNull PacketType.Play.Server packetType;
        private final @NotNull Function<PacketPlaySendEvent, W> wrapperFunction;
        private final @NotNull WrapperHandler<W> wrapperHandler;

        public boolean handle(final @NotNull PacketPlaySendEvent event, final @NotNull SpigotLanguagePlayer languagePlayer) {
            if (event.getPacketType() == packetType) {
                final W wrapper = wrapperFunction.apply(event);
                wrapperHandler.handle(event, wrapper, languagePlayer);
                return true;
            }
            return false;
        }

        interface WrapperHandler<W extends PacketWrapper<?>> {
            void handle(
                    final @NotNull PacketPlaySendEvent event,
                    final @NotNull W wrapper,
                    final @NotNull SpigotLanguagePlayer languagePlayer
            );

            static <W extends PacketWrapper<?>> WrapperHandler<W> of(final @NotNull BiConsumer<W, SpigotLanguagePlayer> handler) {
                return (event, wrapper, languagePlayer) -> handler.accept(wrapper, languagePlayer);
            }
        }
    }

    private @NotNull Optional<Component> translate(
            final @NotNull Component component,
            final @NotNull MainConfig.FeatureSyntax syntax,
            final @NotNull SpigotLanguagePlayer languagePlayer
    ) {
        return Optional.ofNullable(
                main.getLanguageParser().parseComponent(
                        languagePlayer,
                        syntax,
                        AdventureComponentWrapper.toMd5Component(component)
                )
        ).map(AdventureComponentWrapper::fromMd5Component);
    }

    @Override
    public void onPacketPlaySend(PacketPlaySendEvent event) {
        final PacketType.Play.Server packetType = event.getPacketType();
        final SpigotLanguagePlayer player = (SpigotLanguagePlayer) main.getPlayerManager().get(event.getUser().getUUID());
        for (final @NotNull Handler<?> handler : HANDLERS) {
            if (handler.handle(event, player)) break;
        }
    }

    @Override
    public void onPacketStatusSend(PacketStatusSendEvent event) {
        if (event.getPacketType() == PacketType.Status.Server.RESPONSE && main.getConf().isMotd()) {
            handleServerInfo(event);
        }
    }

    public void handleServerInfo(final @NotNull PacketStatusSendEvent event) {
        val ipAddress = Optional.of(event.getUser())
                .map(User::getAddress)
                .map(InetSocketAddress::getAddress)
                .map(InetAddress::getHostAddress);
        if (!ipAddress.isPresent()) {
            main.getLogger().logWarning("Failed to get IP address for player, could not translate MOTD");
            return;
        }
        final @NotNull WrapperStatusServerResponse wrapper = new WrapperStatusServerResponse(event);
        val lang = main.getStorage().getLanguageFromIp(ipAddress.get()).getName();
        val syntax = main.getConf().getMotdSyntax();

        // PacketEvents does not provide a parser. We should parse and modify it ourselves.
        final @NotNull MotdInfoWrapper motdInfo = gson.fromJson(wrapper.getComponent(), MotdInfoWrapper.class);

        // PlayerInfo Sample
        Optional.ofNullable(motdInfo.getPlayers()).ifPresent(player -> {
            if (player.getSample().isEmpty()) return;
            final List<MotdInfoWrapper.Sample> samples = new ArrayList<>();
            for (final @NotNull MotdInfoWrapper.Sample sample : player.getSample()) {
                val translated = main.getLanguageParser().replaceLanguages(
                        sample.getName(), lang, syntax
                );
                val split = translated.split("\n", -1);
                if (split.length > 1) {
                    final UUID uuid = new UUID(0, 0);
                    for (final String line : split) {
                        samples.add(new MotdInfoWrapper.Sample(uuid, line));
                    }
                } else {
                    sample.setName(translated);
                    samples.add(sample);
                }
            }
            player.setSample(samples);
        });

        // Protocol name
        motdInfo.getVersion().setName(
                main.getLanguageParser().replaceLanguages(motdInfo.getVersion().getName(), lang, syntax)
        );
        val description = main.getLanguageParser().parseComponent(
                lang, syntax, ComponentSerializer.parse(motdInfo.getDescription().toString())
        );
        if (description != null) motdInfo.setDescription(
                new JsonPrimitive(ComponentSerializer.toString(description))
        );
        wrapper.setComponent(gson.toJsonTree(motdInfo).getAsJsonObject());
    }

    public void handleSystemChat(
            final @NotNull PacketPlaySendEvent event,
            final @NotNull WrapperPlayServerSystemChatMessage wrapper,
            final @NotNull SpigotLanguagePlayer languagePlayer
    ) {
        val ab = wrapper.isOverlay();
        if ((ab && !main.getConfig().isActionbars()) || (!ab && !main.getConfig().isChat())) return;
        final Optional<Component> component = translate(
                wrapper.getMessage(),
                ab ? main.getConf().getActionbarSyntax() : main.getConf().getChatSyntax(),
                languagePlayer
        );
        if (component.isPresent()) {
            wrapper.setMessage(component.get());
        } else {
            event.setCancelled(true);
        }
    }

    private void handlePlayerListHeaderFooter(
            final @NotNull WrapperPlayServerPlayerListHeaderAndFooter wrapper,
            final @NotNull SpigotLanguagePlayer languagePlayer
    ) {
        if (!main.getConf().isTab()) return;
        final Function<Component, Component> translateFunction = component ->
                translate(component, main.getConf().getTabSyntax(), languagePlayer).orElse(Component.empty());
        wrapper.setHeader(translateFunction.apply(wrapper.getHeader()));
        wrapper.setFooter(translateFunction.apply(wrapper.getFooter()));
    }

    private void handleOpenWindow(
            final @NotNull WrapperPlayServerOpenWindow wrapper,
            final @NotNull SpigotLanguagePlayer SpigotLanguagePlayer
    ) {
        if (!main.getConf().isGuis()) return;
        wrapper.setTitle(translate(wrapper.getTitle(), main.getConf().getGuiSyntax(), SpigotLanguagePlayer).orElse(Component.empty()));
    }

    private void handleDisconnect(
            final @NotNull WrapperPlayServerDisconnect wrapper,
            final @NotNull SpigotLanguagePlayer languagePlayer
    ) {
        if (!main.getConf().isKick()) return;
        wrapper.setReason(
                translate(wrapper.getReason(), main.getConf().getKickSyntax(), languagePlayer).orElse(Component.empty())
        );
    }

    // Unused because force depend on ProtocolLib in SpigotLanguagePlayer.ScoreboardTeam
    private void handleScoreboardTeam(
            final @NotNull WrapperPlayServerTeams wrapper,
            final @NotNull SpigotLanguagePlayer languagePlayer
    ) {
        if (!main.getConf().isScoreboards()) return;

        switch (wrapper.getTeamMode()) {
            case REMOVE: {
                languagePlayer.removeScoreboardTeam(wrapper.getTeamName());
                break;
            }
            case CREATE:
            case UPDATE:
                break;
            default:return;
        }
        val teamInfo = wrapper.getTeamInfo().orElse(null);
        if (teamInfo == null) return;
        Component displayName = teamInfo.getDisplayName();
        Component prefix = teamInfo.getPrefix();
        Component suffix = teamInfo.getSuffix();
        /*
        val team = new SpigotLanguagePlayer.ScoreboardTeam(
                AdventureComponentWrapper.toJson(displayName),
                AdventureComponentWrapper.toJson(prefix),
                AdventureComponentWrapper.toJson(suffix),
                teamInfo.getTagVisibility(),
                teamInfo.getCollisionRule().getId(),
                EnumWrappers.
        )
         */
    }

    private final List<String> SIGN_TYPE = Arrays.asList("minecraft:sign", "Sign", "minecraft:hanging_sign");

    private void handleTileData(
            final @NotNull WrapperPlayServerBlockEntityData wrapper,
            final @NotNull SpigotLanguagePlayer languagePlayer
    ) {
        final NBTCompound nbt = wrapper.getNBT();
        final NBTString stringTag = nbt.getStringTagOrNull("type");
        if (stringTag == null) return;
        final String type = stringTag.getValue();
        if (SIGN_TYPE.stream().anyMatch(it -> it.equals(type))) {
            // TODO
        }
    }
}
