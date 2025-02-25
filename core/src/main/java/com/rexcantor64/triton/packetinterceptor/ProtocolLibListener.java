package com.rexcantor64.triton.packetinterceptor;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerOptions;
import com.comphenix.protocol.events.ListeningWhitelist;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketListener;
import com.comphenix.protocol.injector.GamePhase;
import com.comphenix.protocol.reflect.FuzzyReflection;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.reflect.accessors.Accessors;
import com.comphenix.protocol.reflect.accessors.FieldAccessor;
import com.comphenix.protocol.reflect.accessors.MethodAccessor;
import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.utility.MinecraftVersion;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedTeamParameters;
import com.comphenix.protocol.wrappers.nbt.NbtCompound;
import com.comphenix.protocol.wrappers.nbt.NbtFactory;
import com.google.gson.JsonSyntaxException;
import com.rexcantor64.triton.SpigotMLP;
import com.rexcantor64.triton.Triton;
import com.rexcantor64.triton.language.item.SignLocation;
import com.rexcantor64.triton.packetinterceptor.protocollib.AdvancementsPacketHandler;
import com.rexcantor64.triton.packetinterceptor.protocollib.BossBarPacketHandler;
import com.rexcantor64.triton.packetinterceptor.protocollib.EntitiesPacketHandler;
import com.rexcantor64.triton.packetinterceptor.protocollib.HandlerFunction;
import com.rexcantor64.triton.packetinterceptor.protocollib.SignPacketHandler;
import com.rexcantor64.triton.player.SpigotLanguagePlayer;
import com.rexcantor64.triton.utils.ComponentUtils;
import com.rexcantor64.triton.utils.ItemStackTranslationUtils;
import com.rexcantor64.triton.utils.NMSUtils;
import com.rexcantor64.triton.wrappers.AdventureComponentWrapper;
import com.rexcantor64.triton.wrappers.WrappedClientConfiguration;
import com.rexcantor64.triton.wrappers.WrappedPlayerChatMessage;
import lombok.val;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.rexcantor64.triton.packetinterceptor.protocollib.HandlerFunction.asAsync;
import static com.rexcantor64.triton.packetinterceptor.protocollib.HandlerFunction.asSync;

@SuppressWarnings({"deprecation"})
public class ProtocolLibListener implements PacketListener, PacketInterceptor {
    private final Class<?> CONTAINER_PLAYER_CLASS;
    private final Class<?> MERCHANT_RECIPE_LIST_CLASS;
    private final MethodAccessor CRAFT_MERCHANT_RECIPE_FROM_BUKKIT_METHOD;
    private final MethodAccessor CRAFT_MERCHANT_RECIPE_TO_MINECRAFT_METHOD;
    private final Class<BaseComponent[]> BASE_COMPONENT_ARRAY_CLASS = BaseComponent[].class;
    private final Class<?> ADVENTURE_COMPONENT_CLASS;
    private final Optional<Class<?>> NUMBER_FORMAT_CLASS;
    private final FieldAccessor PLAYER_ACTIVE_CONTAINER_FIELD;
    private final FieldAccessor PLAYER_INVENTORY_CONTAINER_FIELD;
    private final String MERCHANT_RECIPE_SPECIAL_PRICE_FIELD;
    private final String MERCHANT_RECIPE_DEMAND_FIELD;
    private final String SIGN_NBT_ID;

    private final HandlerFunction ASYNC_PASSTHROUGH = asAsync((_packet, _player) -> {
    });

    private final AdvancementsPacketHandler advancementsPacketHandler = AdvancementsPacketHandler.newInstance();
    private final BossBarPacketHandler bossBarPacketHandler = new BossBarPacketHandler();
    private final EntitiesPacketHandler entitiesPacketHandler = new EntitiesPacketHandler();
    private final SignPacketHandler signPacketHandler = new SignPacketHandler();

    private final SpigotMLP main;
    private final List<HandlerFunction.HandlerType> allowedTypes;
    private final Map<PacketType, HandlerFunction> packetHandlers = new HashMap<>();
    private final AtomicBoolean firstRun = new AtomicBoolean(true);

    public ProtocolLibListener(SpigotMLP main, HandlerFunction.HandlerType... allowedTypes) {
        this.main = main;
        this.allowedTypes = Arrays.asList(allowedTypes);
        if (MinecraftVersion.CAVES_CLIFFS_1.atOrAbove()) { // 1.17+
            MERCHANT_RECIPE_LIST_CLASS = NMSUtils.getClass("net.minecraft.world.item.trading.MerchantRecipeList");
        } else if (MinecraftVersion.VILLAGE_UPDATE.atOrAbove()) { // 1.14+
            MERCHANT_RECIPE_LIST_CLASS = NMSUtils.getNMSClass("MerchantRecipeList");
        } else {
            MERCHANT_RECIPE_LIST_CLASS = null;
        }
        if (MinecraftVersion.VILLAGE_UPDATE.atOrAbove()) { // 1.14+
            val craftMerchantRecipeClass = NMSUtils.getCraftbukkitClass("inventory.CraftMerchantRecipe");
            CRAFT_MERCHANT_RECIPE_FROM_BUKKIT_METHOD = Accessors.getMethodAccessor(craftMerchantRecipeClass, "fromBukkit", MerchantRecipe.class);
            CRAFT_MERCHANT_RECIPE_TO_MINECRAFT_METHOD = Accessors.getMethodAccessor(craftMerchantRecipeClass, "toMinecraft");
        } else {
            CRAFT_MERCHANT_RECIPE_FROM_BUKKIT_METHOD = null;
            CRAFT_MERCHANT_RECIPE_TO_MINECRAFT_METHOD = null;
        }
        if (MinecraftVersion.CAVES_CLIFFS_1.atOrAbove()) { // 1.17+
            MERCHANT_RECIPE_SPECIAL_PRICE_FIELD = "g";
            MERCHANT_RECIPE_DEMAND_FIELD = "h";
        } else {
            MERCHANT_RECIPE_SPECIAL_PRICE_FIELD = "specialPrice";
            MERCHANT_RECIPE_DEMAND_FIELD = "demand";
        }
        ADVENTURE_COMPONENT_CLASS = NMSUtils.getClassOrNull("net.kyori.adventure.text.Component");
        NUMBER_FORMAT_CLASS = MinecraftReflection.getOptionalNMS("network.chat.numbers.NumberFormat");
        if (MinecraftVersion.EXPLORATION_UPDATE.atOrAbove()) { // 1.11+
            SIGN_NBT_ID = "minecraft:sign";
        } else {
            SIGN_NBT_ID = "Sign";
        }

        val containerClass = MinecraftReflection.getMinecraftClass("world.inventory.Container", "world.inventory.AbstractContainerMenu", "Container");
        CONTAINER_PLAYER_CLASS = MinecraftReflection.getMinecraftClass("world.inventory.ContainerPlayer", "world.inventory.InventoryMenu", "ContainerPlayer");
        if (MinecraftVersion.v1_20_5.atOrAbove()) { // 1.20.5+
            val fuzzyHuman = FuzzyReflection.fromClass(MinecraftReflection.getEntityHumanClass());
            // We have to use this field matcher because the function in accessors matches superclasses
            PLAYER_ACTIVE_CONTAINER_FIELD = Accessors.getFieldAccessor(
                    fuzzyHuman.getField((field, clazz) -> field.getType() == containerClass)
            );
            PLAYER_INVENTORY_CONTAINER_FIELD = Accessors.getFieldAccessor(
                    fuzzyHuman.getField((field, clazz) -> field.getType() == CONTAINER_PLAYER_CLASS)
            );

            // Sanity check
            assert PLAYER_ACTIVE_CONTAINER_FIELD.getField() != PLAYER_INVENTORY_CONTAINER_FIELD.getField();
        } else {
            val activeContainerField = Arrays.stream(MinecraftReflection.getEntityHumanClass().getDeclaredFields())
                    .filter(field -> field.getType() == containerClass && !field.getName().equals("defaultContainer"))
                    .findAny()
                    .orElseThrow(() -> new RuntimeException("Failed to find field for player's active container"));
            PLAYER_ACTIVE_CONTAINER_FIELD = Accessors.getFieldAccessor(activeContainerField);
            PLAYER_INVENTORY_CONTAINER_FIELD = null;
        }

        setupPacketHandlers();
    }

    @Override
    public Plugin getPlugin() {
        return main.getLoader();
    }

    private void setupPacketHandlers() {
        if (MinecraftVersion.WILD_UPDATE.atOrAbove()) { // 1.19+
            // New chat packets on 1.19
            packetHandlers.put(PacketType.Play.Server.SYSTEM_CHAT, asAsync(this::handleSystemChat));
            if (!MinecraftVersion.FEATURE_PREVIEW_UPDATE.atOrAbove()) {
                // Removed in 1.19.3
                packetHandlers.put(PacketType.Play.Server.CHAT_PREVIEW, asAsync(this::handleChatPreview));
            }
        }
        // In 1.19+, this packet is signed, but we can still edit it, since it might contain
        // formatting from chat plugins.
        packetHandlers.put(PacketType.Play.Server.CHAT, asAsync(this::handleChat));
        if (MinecraftVersion.CAVES_CLIFFS_1.atOrAbove()) { // 1.17+
            // Title packet split on 1.17
            packetHandlers.put(PacketType.Play.Server.SET_TITLE_TEXT, asAsync(this::handleTitle));
            packetHandlers.put(PacketType.Play.Server.SET_SUBTITLE_TEXT, asAsync(this::handleTitle));

            // New actionbar packet
            packetHandlers.put(PacketType.Play.Server.SET_ACTION_BAR_TEXT, asAsync(this::handleActionbar));

            // Combat packet split on 1.17
            packetHandlers.put(PacketType.Play.Server.PLAYER_COMBAT_KILL, asAsync(this::handleDeathScreen));
        } else {
            packetHandlers.put(PacketType.Play.Server.TITLE, asAsync(this::handleTitle));
            packetHandlers.put(PacketType.Play.Server.COMBAT_EVENT, asAsync(this::handleDeathScreen));
        }

        packetHandlers.put(PacketType.Play.Server.PLAYER_LIST_HEADER_FOOTER, asAsync(this::handlePlayerListHeaderFooter));
        packetHandlers.put(PacketType.Play.Server.OPEN_WINDOW, asAsync(this::handleOpenWindow));
        packetHandlers.put(PacketType.Play.Server.KICK_DISCONNECT, asSync(this::handleKickDisconnect));
        if (MinecraftVersion.AQUATIC_UPDATE.atOrAbove()) { // 1.13+
            // Scoreboard rewrite on 1.13
            // It allows unlimited length team prefixes and suffixes
            packetHandlers.put(PacketType.Play.Server.SCOREBOARD_TEAM, asAsync(this::handleScoreboardTeam));
            packetHandlers.put(PacketType.Play.Server.SCOREBOARD_OBJECTIVE, asAsync(this::handleScoreboardObjective));
            // Register the packets below so their order is kept between all scoreboard packets
            packetHandlers.put(PacketType.Play.Server.SCOREBOARD_DISPLAY_OBJECTIVE, ASYNC_PASSTHROUGH);
            packetHandlers.put(PacketType.Play.Server.SCOREBOARD_SCORE, ASYNC_PASSTHROUGH);
            if (MinecraftVersion.v1_20_4.atOrAbove()) {
                packetHandlers.put(PacketType.Play.Server.RESET_SCORE, ASYNC_PASSTHROUGH);
            }
        }
        packetHandlers.put(PacketType.Play.Server.WINDOW_ITEMS, asAsync(this::handleWindowItems));
        packetHandlers.put(PacketType.Play.Server.SET_SLOT, asAsync(this::handleSetSlot));
        if (MinecraftVersion.VILLAGE_UPDATE.atOrAbove()) { // 1.14+
            // Villager merchant interface redesign on 1.14
            packetHandlers.put(PacketType.Play.Server.OPEN_WINDOW_MERCHANT, asAsync(this::handleMerchantItems));
        }

        // External Packet Handlers
        if (advancementsPacketHandler != null) {
            advancementsPacketHandler.registerPacketTypes(packetHandlers);
        }
        bossBarPacketHandler.registerPacketTypes(packetHandlers);
        entitiesPacketHandler.registerPacketTypes(packetHandlers);
        signPacketHandler.registerPacketTypes(packetHandlers);
    }

    /* PACKET HANDLERS */

    private void handleChat(PacketEvent packet, SpigotLanguagePlayer languagePlayer) {
        boolean isSigned = MinecraftVersion.WILD_UPDATE.atOrAbove(); // MC 1.19+
        if (isSigned && !main.getConfig().isSignedChat()) return;
        // action bars are not sent here on 1.19+ anymore
        boolean ab = !isSigned && isActionbar(packet.getPacket());

        // Don't bother parsing anything else if it's disabled on config
        if ((ab && !main.getConfig().isActionbars()) || (!ab && !main.getConfig().isChat())) return;

        val chatModifier = packet.getPacket().getChatComponents();
        val baseComponentModifier = packet.getPacket().getSpecificModifier(BASE_COMPONENT_ARRAY_CLASS);
        BaseComponent[] result = null;
        boolean hasPlayerChatMessageRecord = isSigned && !MinecraftVersion.FEATURE_PREVIEW_UPDATE.atOrAbove(); // MC 1.19-1.19.2

        // Hot fix for 1.16 Paper builds 472+ (and 1.17+)
        StructureModifier<?> adventureModifier =
                ADVENTURE_COMPONENT_CLASS == null ? null : packet.getPacket().getSpecificModifier(ADVENTURE_COMPONENT_CLASS);
        StructureModifier<WrappedPlayerChatMessage> playerChatModifier = null;

        if (hasPlayerChatMessageRecord) {
            // The message is wrapped in a PlayerChatMessage record
            playerChatModifier = packet.getPacket().getModifier().withType(WrappedPlayerChatMessage.getWrappedClass(), WrappedPlayerChatMessage.CONVERTER);
            WrappedPlayerChatMessage playerChatMessage = playerChatModifier.readSafely(0);
            if (playerChatMessage != null) {
                Optional<WrappedChatComponent> msg = playerChatMessage.getMessage();
                if (msg.isPresent()) {
                    result = ComponentSerializer.parse(msg.get().getJson());
                }
            }
        } else if (adventureModifier != null && adventureModifier.readSafely(0) != null) {
            Object adventureComponent = adventureModifier.readSafely(0);
            result = AdventureComponentWrapper.toMd5Component(adventureComponent);
            adventureModifier.writeSafely(0, null);
        } else if (baseComponentModifier.readSafely(0) != null) {
            result = baseComponentModifier.readSafely(0);
        } else {
            val msg = chatModifier.readSafely(0);
            if (msg != null) result = ComponentSerializer.parse(msg.getJson());
        }

        // Something went wrong while getting data from the packet, or the packet is empty...?
        if (result == null) return;

        // Translate the message
        result = main.getLanguageParser().parseComponent(
                languagePlayer,
                ab ? main.getConf().getActionbarSyntax() : main.getConf().getChatSyntax(),
                result);

        // Handle disabled line
        if (result == null) {
            packet.setCancelled(true);
            return;
        }

        if (MinecraftVersion.FEATURE_PREVIEW_UPDATE.atOrAbove()) { // MC 1.19.3+
            // While chat is signed, we can still mess around with formatting and prefixes
            chatModifier.writeSafely(0, WrappedChatComponent.fromJson(ComponentSerializer.toString(result)));
        } else if (hasPlayerChatMessageRecord) { // MC 1.19-1.19.2
            // While chat is signed, we can still mess around with formatting and prefixes
            playerChatModifier.readSafely(0).setMessage(Optional.of(WrappedChatComponent.fromJson(ComponentSerializer.toString(result))));
        } else if (ab && !MinecraftVersion.EXPLORATION_UPDATE.atOrAbove()) {
            // The Notchian client does not support true JSON messages on actionbars
            // on 1.10 and below. Therefore, we must convert to a legacy string inside
            // a TextComponent.
            baseComponentModifier.writeSafely(0, ComponentUtils.mergeComponents(result));
        } else {
            baseComponentModifier.writeSafely(0, result);
        }
    }

    /**
     * Handle a system chat outbound packet, added in Minecraft 1.19.
     * Apparently most chat messages and actionbars are sent through here in Minecraft 1.19+.
     *
     * @param packet         ProtocolLib's packet event
     * @param languagePlayer The language player this packet is being sent to
     * @since 3.8.0 (Minecraft 1.19)
     */
    private void handleSystemChat(PacketEvent packet, SpigotLanguagePlayer languagePlayer) {
        boolean ab = isActionbar(packet.getPacket());

        // Don't bother parsing anything else if it's disabled on config
        if ((ab && !main.getConfig().isActionbars()) || (!ab && !main.getConfig().isChat())) return;

        val stringModifier = packet.getPacket().getStrings();
        val chatModifier = packet.getPacket().getChatComponents();

        BaseComponent[] result = null;

        // Hot fix for Paper builds
        StructureModifier<?> adventureModifier =
                ADVENTURE_COMPONENT_CLASS == null ? null : packet.getPacket().getSpecificModifier(ADVENTURE_COMPONENT_CLASS);

        if (adventureModifier != null && adventureModifier.readSafely(0) != null) {
            Object adventureComponent = adventureModifier.readSafely(0);
            result = AdventureComponentWrapper.toMd5Component(adventureComponent);
            adventureModifier.writeSafely(0, null);
        } else if (chatModifier.readSafely(0) != null) {
            try {
                result = ComponentSerializer.parse(chatModifier.readSafely(0).getJson());
            } catch (JsonSyntaxException ignore) {
                // The md_5 chat library can't handle some messages of 1.20.4
                // https://github.com/SpigotMC/BungeeCord/issues/3578
                return;
            }
        } else {
            val msgJson = stringModifier.readSafely(0);
            if (msgJson != null) {
                result = ComponentSerializer.parse(msgJson);
            }
        }

        // Packet is empty
        if (result == null) return;

        // Translate the message
        result = main.getLanguageParser().parseComponent(
                languagePlayer,
                ab ? main.getConf().getActionbarSyntax() : main.getConf().getChatSyntax(),
                result);

        // Handle disabled line
        if (result == null) {
            packet.setCancelled(true);
            return;
        }

        if (chatModifier.size() > 0) {
            chatModifier.writeSafely(0, WrappedChatComponent.fromJson(ComponentSerializer.toString(result)));
        } else {
            stringModifier.writeSafely(0, ComponentSerializer.toString(result));
        }
    }

    /**
     * Handle a chat preview outbound packet, added in Minecraft 1.19.
     * This changes the preview of the message to translate placeholders there
     *
     * @param packet         ProtocolLib's packet event
     * @param languagePlayer The language player this packet is being sent to
     * @since 3.8.2 (Minecraft 1.19)
     */
    private void handleChatPreview(PacketEvent packet, SpigotLanguagePlayer languagePlayer) {
        val chatComponentsModifier = packet.getPacket().getChatComponents();

        BaseComponent[] result = null;

        // Hot fix for Paper builds
        StructureModifier<?> adventureModifier =
                ADVENTURE_COMPONENT_CLASS == null ? null : packet.getPacket().getSpecificModifier(ADVENTURE_COMPONENT_CLASS);

        if (adventureModifier != null && adventureModifier.readSafely(0) != null) {
            Object adventureComponent = adventureModifier.readSafely(0);
            result = AdventureComponentWrapper.toMd5Component(adventureComponent);
            adventureModifier.writeSafely(0, null);
        } else {
            val msg = chatComponentsModifier.readSafely(0);
            if (msg != null) {
                result = ComponentSerializer.parse(msg.getJson());
            }
        }

        // Packet is empty
        if (result == null) return;

        // Translate the message
        result = main.getLanguageParser().parseComponent(
                languagePlayer,
                main.getConf().getChatSyntax(),
                result
        );

        // Handle disabled line
        if (result == null) {
            packet.setCancelled(true);
            return;
        }

        chatComponentsModifier.writeSafely(0, WrappedChatComponent.fromJson(ComponentSerializer.toString(result)));
    }

    private void handleActionbar(PacketEvent packet, SpigotLanguagePlayer languagePlayer) {
        if (!main.getConf().isActionbars()) return;

        val baseComponentModifier = packet.getPacket().getSpecificModifier(BASE_COMPONENT_ARRAY_CLASS);
        BaseComponent[] result = null;

        // Hot fix for Paper builds 472+
        StructureModifier<?> adventureModifier =
                ADVENTURE_COMPONENT_CLASS == null ? null : packet.getPacket().getSpecificModifier(ADVENTURE_COMPONENT_CLASS);

        if (adventureModifier != null && adventureModifier.readSafely(0) != null) {
            Object adventureComponent = adventureModifier.readSafely(0);
            result = AdventureComponentWrapper.toMd5Component(adventureComponent);
            adventureModifier.writeSafely(0, null);
        } else if (baseComponentModifier.readSafely(0) != null) {
            result = baseComponentModifier.readSafely(0);
            baseComponentModifier.writeSafely(0, null);
        } else {
            val msg = packet.getPacket().getChatComponents().readSafely(0);
            if (msg != null) result = ComponentSerializer.parse(msg.getJson());
        }

        // Something went wrong while getting data from the packet, or the packet is empty...?
        if (result == null) return;

        // Translate the message
        result = main.getLanguageParser().parseComponent(
                languagePlayer,
                main.getConf().getActionbarSyntax(),
                result);

        // Handle disabled line
        if (result == null) {
            packet.setCancelled(true);
            return;
        }

        // Flatten action bar's json
        packet.getPacket().getChatComponents().writeSafely(0, WrappedChatComponent.fromJson(ComponentSerializer.toString(result)));
    }

    private void handleTitle(PacketEvent packet, SpigotLanguagePlayer languagePlayer) {
        if (!main.getConf().isTitles()) return;

        WrappedChatComponent msg = packet.getPacket().getChatComponents().readSafely(0);
        if (msg == null) return;
        BaseComponent[] result = main.getLanguageParser().parseComponent(languagePlayer,
                main.getConf().getTitleSyntax(), ComponentSerializer.parse(msg.getJson()));
        if (result == null) {
            packet.setCancelled(true);
            return;
        }
        msg.setJson(ComponentSerializer.toString(result));
        packet.getPacket().getChatComponents().writeSafely(0, msg);
    }

    private void handlePlayerListHeaderFooter(PacketEvent packet, SpigotLanguagePlayer languagePlayer) {
        if (!main.getConf().isTab()) return;
        StructureModifier<?> adventureModifier =
                ADVENTURE_COMPONENT_CLASS == null ? null : packet.getPacket().getSpecificModifier(ADVENTURE_COMPONENT_CLASS);

        WrappedChatComponent header = packet.getPacket().getChatComponents().readSafely(0);
        String headerJson = null;
        WrappedChatComponent footer = packet.getPacket().getChatComponents().readSafely(1);
        String footerJson = null;

        if (adventureModifier != null && adventureModifier.readSafely(0) != null
                && adventureModifier.readSafely(1) != null) {
            // Paper 1.18 builds now have Adventure Component fields for header and footer, handle conversion
            // In future versions we might implement an Adventure parser
            Object adventureHeader = adventureModifier.readSafely(0);
            Object adventureFooter = adventureModifier.readSafely(1);

            headerJson = AdventureComponentWrapper.toJson(adventureHeader);
            footerJson = AdventureComponentWrapper.toJson(adventureFooter);

            adventureModifier.writeSafely(0, null);
            adventureModifier.writeSafely(1, null);
        }
        if (headerJson == null) {
            if (header == null || footer == null) {
                Triton.get().getLogger().logWarning("Could not translate player list header footer because content is null.");
                return;
            }
            headerJson = header.getJson();
            footerJson = footer.getJson();
        }

        BaseComponent[] resultHeader = main.getLanguageParser().parseComponent(languagePlayer,
                main.getConf().getTabSyntax(), ComponentSerializer.parse(headerJson));
        if (resultHeader == null)
            resultHeader = new BaseComponent[]{new TextComponent("")};
        else if (resultHeader.length == 1 && resultHeader[0] instanceof TextComponent) {
            // This is needed because the Notchian client does not render the header/footer
            // if the content of the header top level component is an empty string.
            val textComp = (TextComponent) resultHeader[0];
            if (textComp.getText().length() == 0 && !headerJson.equals("{\"text\":\"\"}"))
                textComp.setText("§0§1§2§r");
        }
        header = WrappedChatComponent.fromJson(ComponentSerializer.toString(resultHeader));
        packet.getPacket().getChatComponents().writeSafely(0, header);

        BaseComponent[] resultFooter = main.getLanguageParser().parseComponent(languagePlayer,
                main.getConf().getTabSyntax(), ComponentSerializer.parse(footerJson));
        if (resultFooter == null)
            resultFooter = new BaseComponent[]{new TextComponent("")};
        footer = WrappedChatComponent.fromJson(ComponentSerializer.toString(resultFooter));
        packet.getPacket().getChatComponents().writeSafely(1, footer);
        languagePlayer.setLastTabHeader(headerJson);
        languagePlayer.setLastTabFooter(footerJson);
    }

    private void handleOpenWindow(PacketEvent packet, SpigotLanguagePlayer languagePlayer) {
        if (!main.getConf().isGuis()) return;

        WrappedChatComponent msg = packet.getPacket().getChatComponents().readSafely(0);
        BaseComponent[] result = main.getLanguageParser()
                .parseComponent(languagePlayer, main.getConf().getGuiSyntax(), ComponentSerializer
                        .parse(msg.getJson()));
        if (result == null)
            result = new BaseComponent[]{new TextComponent("")};
        if (MinecraftVersion.NETHER_UPDATE.atOrAbove()) { // 1.16+
            msg.setJson(ComponentSerializer.toString(result));
        } else {
            msg.setJson(ComponentSerializer.toString(ComponentUtils.mergeComponents(result)));
        }
        packet.getPacket().getChatComponents().writeSafely(0, msg);
    }

    private void handleKickDisconnect(PacketEvent packet, SpigotLanguagePlayer languagePlayer) {
        if (!main.getConf().isKick()) return;

        WrappedChatComponent msg = packet.getPacket().getChatComponents().readSafely(0);
        BaseComponent[] result = main.getLanguageParser().parseComponent(languagePlayer,
                main.getConf().getKickSyntax(), ComponentSerializer.parse(msg.getJson()));
        if (result == null)
            result = new BaseComponent[]{new TextComponent("")};
        msg.setJson(ComponentSerializer.toString(result));
        packet.getPacket().getChatComponents().writeSafely(0, msg);
    }

    private void handleWindowItems(PacketEvent packet, SpigotLanguagePlayer languagePlayer) {
        if (!main.getConf().isItems()) return;

        if (!main.getConf().isInventoryItems() && isPlayerInventoryOpen(packet.getPlayer()))
            return;

        if (MinecraftVersion.EXPLORATION_UPDATE.atOrAbove()) { // 1.11+
            List<ItemStack> items = packet.getPacket().getItemListModifier().readSafely(0);
            for (ItemStack item : items) {
                ItemStackTranslationUtils.translateItemStack(item, languagePlayer, true);
            }
            packet.getPacket().getItemListModifier().writeSafely(0, items);

            if (MinecraftVersion.CAVES_CLIFFS_1.atOrAbove()) { // 1.17+
                ItemStack carriedItem = packet.getPacket().getItemModifier().readSafely(0);
                carriedItem = ItemStackTranslationUtils.translateItemStack(carriedItem, languagePlayer, false);
                packet.getPacket().getItemModifier().writeSafely(0, carriedItem);
            }
        } else {
            ItemStack[] items = packet.getPacket().getItemArrayModifier().readSafely(0);
            for (ItemStack item : items) {
                ItemStackTranslationUtils.translateItemStack(item, languagePlayer, true);
            }
            packet.getPacket().getItemArrayModifier().writeSafely(0, items);
        }
    }

    private void handleSetSlot(PacketEvent packet, SpigotLanguagePlayer languagePlayer) {
        if (!main.getConf().isItems()) return;

        if (!main.getConf().isInventoryItems() && isPlayerInventoryOpen(packet.getPlayer()))
            return;

        ItemStack item = packet.getPacket().getItemModifier().readSafely(0);
        ItemStackTranslationUtils.translateItemStack(item, languagePlayer, true);
        packet.getPacket().getItemModifier().writeSafely(0, item);
    }

    @SuppressWarnings({"unchecked"})
    private void handleMerchantItems(PacketEvent packet, SpigotLanguagePlayer languagePlayer) {
        if (!main.getConf().isItems()) return;

        try {
            ArrayList<?> recipes = (ArrayList<?>) packet.getPacket()
                    .getSpecificModifier(MERCHANT_RECIPE_LIST_CLASS).readSafely(0);
            ArrayList<Object> newRecipes = (ArrayList<Object>) MERCHANT_RECIPE_LIST_CLASS.newInstance();
            for (val recipeObject : recipes) {
                val recipe = (MerchantRecipe) NMSUtils.getMethod(recipeObject, "asBukkit");
                val originalSpecialPrice = NMSUtils.getDeclaredField(recipeObject, MERCHANT_RECIPE_SPECIAL_PRICE_FIELD);
                val originalDemand = NMSUtils.getDeclaredField(recipeObject, MERCHANT_RECIPE_DEMAND_FIELD);

                val newRecipe = new MerchantRecipe(ItemStackTranslationUtils.translateItemStack(recipe.getResult()
                        .clone(), languagePlayer, false), recipe.getUses(), recipe.getMaxUses(), recipe
                        .hasExperienceReward(), recipe.getVillagerExperience(), recipe.getPriceMultiplier());

                for (val ingredient : recipe.getIngredients()) {
                    newRecipe.addIngredient(ItemStackTranslationUtils.translateItemStack(ingredient.clone(), languagePlayer, false));
                }

                Object newCraftRecipe = CRAFT_MERCHANT_RECIPE_FROM_BUKKIT_METHOD.invoke(null, newRecipe);
                Object newNMSRecipe = CRAFT_MERCHANT_RECIPE_TO_MINECRAFT_METHOD.invoke(newCraftRecipe);
                NMSUtils.setDeclaredField(newNMSRecipe, MERCHANT_RECIPE_SPECIAL_PRICE_FIELD, originalSpecialPrice);
                NMSUtils.setDeclaredField(newNMSRecipe, MERCHANT_RECIPE_DEMAND_FIELD, originalDemand);
                newRecipes.add(newNMSRecipe);
            }
            packet.getPacket().getModifier().writeSafely(1, newRecipes);
        } catch (IllegalAccessException | InstantiationException e) {
            Triton.get().getLogger().logError(e, "Failed to translate merchant items.");
        }
    }

    private void handleScoreboardTeam(PacketEvent packet, SpigotLanguagePlayer languagePlayer) {
        if (!main.getConf().isScoreboards()) return;

        val teamName = packet.getPacket().getStrings().readSafely(0);
        val mode = packet.getPacket().getIntegers().readSafely(0);

        if (mode == 1) {
            languagePlayer.removeScoreboardTeam(teamName);
            return;
        }

        if (mode != 0 && mode != 2) return; // Other modes don't change text

        WrappedChatComponent displayName, prefix, suffix;
        SpigotLanguagePlayer.ScoreboardTeam team;

        if (MinecraftVersion.CAVES_CLIFFS_1.atOrAbove()) { // 1.17+
            Optional<WrappedTeamParameters> paramsOpt = packet.getPacket().getOptionalTeamParameters().readSafely(0);
            if (!paramsOpt.isPresent()) return;

            val parameters = paramsOpt.get();

            displayName = parameters.getDisplayName();
            prefix = parameters.getPrefix();
            suffix = parameters.getSuffix();

            team = new SpigotLanguagePlayer.ScoreboardTeam(
                    displayName.getJson(),
                    prefix.getJson(),
                    suffix.getJson(),
                    parameters.getNametagVisibility(),
                    parameters.getCollisionRule(),
                    parameters.getColor(),
                    parameters.getOptions()
            );
        } else {
            val chatComponents = packet.getPacket().getChatComponents();
            displayName = chatComponents.readSafely(0);
            prefix = chatComponents.readSafely(1);
            suffix = chatComponents.readSafely(2);

            team = new SpigotLanguagePlayer.ScoreboardTeam(
                    displayName.getJson(),
                    prefix.getJson(),
                    suffix.getJson(),
                    packet.getPacket().getStrings().readSafely(1),
                    packet.getPacket().getStrings().readSafely(2),
                    packet.getPacket().getChatFormattings().readSafely(0),
                    packet.getPacket().getIntegers().readSafely(1)
            );
        }

        languagePlayer.setScoreboardTeam(teamName, team);

        for (WrappedChatComponent component : Arrays.asList(displayName, prefix, suffix)) {
            BaseComponent[] result = main.getLanguageParser()
                    .parseComponent(languagePlayer, main.getConf().getScoreboardSyntax(), ComponentSerializer
                            .parse(component.getJson()));
            if (result == null) result = new BaseComponent[]{new TextComponent("")};
            component.setJson(ComponentSerializer.toString(result));
        }

        if (MinecraftVersion.CAVES_CLIFFS_1.atOrAbove()) { // 1.17+
            val parameters = WrappedTeamParameters.newBuilder()
                    .displayName(displayName)
                    .prefix(prefix)
                    .suffix(suffix)
                    .nametagVisibility(team.getNameTagVisibility())
                    .collisionRule(team.getCollisionRule())
                    .color(team.getColor())
                    .options(team.getOptions())
                    .build();

            packet.getPacket().getOptionalTeamParameters().writeSafely(0, Optional.of(parameters));
        } else {
            val chatComponents = packet.getPacket().getChatComponents();
            chatComponents.writeSafely(0, displayName);
            chatComponents.writeSafely(1, prefix);
            chatComponents.writeSafely(2, suffix);
        }
    }

    private void handleScoreboardObjective(PacketEvent packet, SpigotLanguagePlayer languagePlayer) {
        if (!main.getConf().isScoreboards()) return;

        val objectiveName = packet.getPacket().getStrings().readSafely(0);
        val mode = packet.getPacket().getIntegers().readSafely(0);

        if (mode == 1) {
            languagePlayer.removeScoreboardObjective(objectiveName);
            return;
        }
        // There are only 3 modes, so no need to check for more modes

        val healthDisplay = packet.getPacket().getModifier().readSafely(2);
        val displayName = packet.getPacket().getChatComponents().readSafely(0);
        val numberFormat = NUMBER_FORMAT_CLASS
                .map(numberFormatClass -> packet.getPacket().getSpecificModifier(numberFormatClass).readSafely(0))
                .orElse(null);

        languagePlayer.setScoreboardObjective(objectiveName, displayName.getJson(), healthDisplay, numberFormat);

        BaseComponent[] result = main.getLanguageParser()
                .parseComponent(languagePlayer, main.getConf().getScoreboardSyntax(), ComponentSerializer
                        .parse(displayName.getJson()));
        if (result == null) result = new BaseComponent[]{new TextComponent("")};
        displayName.setJson(ComponentSerializer.toString(result));
        packet.getPacket().getChatComponents().writeSafely(0, displayName);
    }

    private void handleDeathScreen(PacketEvent packet, SpigotLanguagePlayer languagePlayer) {
        if (!main.getConf().isDeathScreen()) return;

        val component = packet.getPacket().getChatComponents().readSafely(0);
        if (component == null) {
            // Likely it's MC 1.16 or below and type of packet is not ENTITY_DIED.
            // Alternatively, this will always be null on 1.8.8 since it uses a String, but there's nothing interesting to translate anyway.
            return;
        }

        BaseComponent[] result = main.getLanguageParser().parseComponent(
                languagePlayer,
                main.getConf().getDeathScreenSyntax(),
                ComponentSerializer.parse(component.getJson())
        );
        if (result == null) {
            result = new BaseComponent[]{new TextComponent("")};
        }
        component.setJson(ComponentSerializer.toString(result));

        packet.getPacket().getChatComponents().writeSafely(0, component);
    }

    /* PROTOCOL LIB */

    @Override
    public void onPacketSending(PacketEvent packet) {
        if (!packet.isServerPacket()) return;

        if (firstRun.compareAndSet(true, false) && !Bukkit.getServer().isPrimaryThread()) {
            Thread.currentThread().setName("Triton Async Packet Handler");
        }

        SpigotLanguagePlayer languagePlayer;
        try {
            languagePlayer =
                    (SpigotLanguagePlayer) Triton.get().getPlayerManager().get(packet.getPlayer().getUniqueId());
        } catch (Exception e) {
            Triton.get().getLogger()
                    .logWarning("Failed to translate packet because UUID of the player is unknown (possibly " +
                            "because the player hasn't joined yet).");
            if (Triton.get().getConfig().getLogLevel() >= 1)
                e.printStackTrace();
            return;
        }
        if (languagePlayer == null) {
            Triton.get().getLogger().logWarning("Language Player is null on packet sending");
            return;
        }

        val handler = packetHandlers.get(packet.getPacketType());
        if (handler != null) {
            handler.getHandlerFunction().accept(packet, languagePlayer);
        }
    }

    @Override
    public void onPacketReceiving(PacketEvent packet) {
        if (packet.isServerPacket()) return;
        SpigotLanguagePlayer languagePlayer;
        try {
            languagePlayer =
                    (SpigotLanguagePlayer) Triton.get().getPlayerManager().get(packet.getPlayer().getUniqueId());
        } catch (Exception ignore) {
            Triton.get().getLogger()
                    .logTrace("Failed to get SpigotLanguagePlayer because UUID of the player is unknown " +
                            "(possibly because the player hasn't joined yet).");
            return;
        }
        if (languagePlayer == null) {
            Triton.get().getLogger().logWarning("Language Player is null on packet receiving");
            return;
        }
        if (!languagePlayer.isWaitingForClientLocale()) {
            return;
        }
        if (packet.getPacketType() == PacketType.Play.Client.SETTINGS) {
            Bukkit.getScheduler().runTask(
                    main.getLoader(),
                    () -> languagePlayer.setLang(
                            main.getLanguageManager()
                                    .getLanguageByLocale(packet.getPacket().getStrings().readSafely(0), true)
                    )
            );
        } else if (packet.getPacketType().getProtocol() == PacketType.Protocol.CONFIGURATION) {
            val clientConfigurations = packet.getPacket().getStructures().withType(WrappedClientConfiguration.getWrappedClass(), WrappedClientConfiguration.CONVERTER);
            val locale = clientConfigurations.readSafely(0).getLocale();
            val language = main.getLanguageManager().getLanguageByLocale(locale, true);
            Bukkit.getScheduler().runTaskLater(main.getLoader(), () -> languagePlayer.setLang(language), 2L);
        }
    }

    @Override
    public ListeningWhitelist getSendingWhitelist() {
        val types = packetHandlers.entrySet().stream()
                .filter(entry -> this.allowedTypes.contains(entry.getValue().getHandlerType()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        return ListeningWhitelist.newBuilder()
                .gamePhase(GamePhase.PLAYING)
                .types(types)
                .mergeOptions(ListenerOptions.ASYNC)
                .highest()
                .build();
    }

    @Override
    public ListeningWhitelist getReceivingWhitelist() {
        val types = new ArrayList<PacketType>();
        if (this.allowedTypes.contains(HandlerFunction.HandlerType.SYNC)) {
            // only listen for these packets in the sync handler
            types.add(PacketType.Play.Client.SETTINGS);
            if (MinecraftVersion.CONFIG_PHASE_PROTOCOL_UPDATE.atOrAbove()) { // MC 1.20.2
                types.add(PacketType.Configuration.Client.CLIENT_INFORMATION);
            }
        }

        return ListeningWhitelist.newBuilder()
                .gamePhase(GamePhase.PLAYING)
                .types(types)
                .mergeOptions(ListenerOptions.ASYNC)
                .highest()
                .build();
    }

    /* REFRESH */

    @Override
    public void refreshSigns(SpigotLanguagePlayer player) {
        signPacketHandler.refreshSignsForPlayer(player);
    }

    @Override
    public void refreshEntities(SpigotLanguagePlayer player) {
        entitiesPacketHandler.refreshEntities(player);
    }

    @Override
    public void refreshTabHeaderFooter(SpigotLanguagePlayer player, String header, String footer) {
        player.toBukkit().ifPresent(bukkitPlayer -> {
            PacketContainer packet =
                    ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.PLAYER_LIST_HEADER_FOOTER);
            packet.getChatComponents().writeSafely(0, WrappedChatComponent.fromJson(header));
            packet.getChatComponents().writeSafely(1, WrappedChatComponent.fromJson(footer));
            ProtocolLibrary.getProtocolManager().sendServerPacket(bukkitPlayer, packet, true);
        });
    }

    @Override
    public void refreshBossbar(SpigotLanguagePlayer player, UUID uuid, String text) {
        bossBarPacketHandler.refreshBossbar(player, uuid, text);
    }

    @Override
    public void refreshScoreboard(SpigotLanguagePlayer player) {
        val bukkitPlayerOpt = player.toBukkit();
        if (!bukkitPlayerOpt.isPresent()) return;
        val bukkitPlayer = bukkitPlayerOpt.get();

        player.getObjectivesMap().forEach((key, value) -> {
            val packet = ProtocolLibrary.getProtocolManager()
                    .createPacket(PacketType.Play.Server.SCOREBOARD_OBJECTIVE);
            packet.getIntegers().writeSafely(0, 2); // Update display name mode
            packet.getStrings().writeSafely(0, key);
            packet.getChatComponents().writeSafely(0, WrappedChatComponent.fromJson(value.getChatJson()));
            packet.getModifier().writeSafely(2, value.getType());
            NUMBER_FORMAT_CLASS.ifPresent(numberFormatClass ->
                    packet.getSpecificModifier((Class<Object>) numberFormatClass)
                            .writeSafely(0, value.getNumberFormat()));
            ProtocolLibrary.getProtocolManager().sendServerPacket(bukkitPlayer, packet, true);
        });

        player.getTeamsMap().forEach((key, value) -> {
            val packet = ProtocolLibrary.getProtocolManager()
                    .createPacket(PacketType.Play.Server.SCOREBOARD_TEAM);
            packet.getIntegers().writeSafely(0, 2); // Update team info mode
            packet.getStrings().writeSafely(0, key);
            if (MinecraftVersion.CAVES_CLIFFS_1.atOrAbove()) { // MC 1.17+
                val parameters = WrappedTeamParameters.newBuilder()
                        .displayName(WrappedChatComponent.fromJson(value.getDisplayJson()))
                        .prefix(WrappedChatComponent.fromJson(value.getPrefixJson()))
                        .suffix(WrappedChatComponent.fromJson(value.getSuffixJson()))
                        .nametagVisibility(value.getNameTagVisibility())
                        .collisionRule(value.getCollisionRule())
                        .color(value.getColor())
                        .options(value.getOptions())
                        .build();

                packet.getOptionalTeamParameters().writeSafely(0, Optional.of(parameters));
            } else {
                packet.getChatComponents().writeSafely(0, WrappedChatComponent.fromJson(value.getDisplayJson()));
                packet.getChatComponents().writeSafely(1, WrappedChatComponent.fromJson(value.getPrefixJson()));
                packet.getChatComponents().writeSafely(2, WrappedChatComponent.fromJson(value.getSuffixJson()));

                packet.getStrings().writeSafely(1, value.getNameTagVisibility());
                packet.getStrings().writeSafely(2, value.getCollisionRule());
                packet.getChatFormattings().writeSafely(0, value.getColor());
                packet.getIntegers().writeSafely(1, value.getOptions());
            }

            ProtocolLibrary.getProtocolManager().sendServerPacket(bukkitPlayer, packet, true);
        });
    }

    @Override
    public void refreshAdvancements(SpigotLanguagePlayer languagePlayer) {
        if (this.advancementsPacketHandler == null) return;

        this.advancementsPacketHandler.refreshAdvancements(languagePlayer);
    }

    @Override
    public void resetSign(Player p, SignLocation location) {
        World world = Bukkit.getWorld(location.getWorld());
        if (world == null) return;
        Block block = world.getBlockAt(location.getX(), location.getY(), location.getZ());
        BlockState state = block.getState();
        if (!(state instanceof Sign))
            return;
        String[] lines = ((Sign) state).getLines();
        if (MinecraftReflection.signUpdateExists()) {
            PacketContainer container =
                    ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.UPDATE_SIGN, true);
            container.getBlockPositionModifier().writeSafely(0, new BlockPosition(location.getX(), location.getY(),
                    location.getZ()));
            container.getChatComponentArrays().writeSafely(0,
                    new WrappedChatComponent[]{WrappedChatComponent.fromText(lines[0]),
                            WrappedChatComponent.fromText(lines[1]), WrappedChatComponent.fromText(lines[2]),
                            WrappedChatComponent.fromText(lines[3])});
            try {
                ProtocolLibrary.getProtocolManager().sendServerPacket(p, container, false);
            } catch (Exception e) {
                main.getLogger().logError(e, "Failed refresh sign.");
            }
        } else {
            PacketContainer container =
                    ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.TILE_ENTITY_DATA, true);
            container.getBlockPositionModifier().writeSafely(0, new BlockPosition(location.getX(), location.getY(),
                    location.getZ()));
            container.getIntegers().writeSafely(0, 9); // Action (9): Update sign text
            NbtCompound nbt = NbtFactory.asCompound(container.getNbtModifier().readSafely(0));
            for (int i = 0; i < 4; i++)
                nbt.put("Text" + (i + 1), ComponentSerializer.toString(TextComponent.fromLegacyText(lines[i])));
            nbt.put("name", "null")
                    .put("x", block.getX())
                    .put("y", block.getY())
                    .put("z", block.getZ())
                    .put("id", SIGN_NBT_ID);
            try {
                ProtocolLibrary.getProtocolManager().sendServerPacket(p, container, false);
            } catch (Exception e) {
                main.getLogger().logError("Failed refresh sign.");
            }
        }
    }

    /* UTILITIES */

    private boolean isActionbar(PacketContainer container) {
        if (MinecraftVersion.WILD_UPDATE.atOrAbove()) { // 1.19+
            val booleans = container.getBooleans();
            if (booleans.size() > 0) {
                return booleans.readSafely(0);
            }
            return container.getIntegers().readSafely(0) == 2;
        } else if (MinecraftVersion.COLOR_UPDATE.atOrAbove()) { // 1.12+
            return container.getChatTypes().readSafely(0) == EnumWrappers.ChatType.GAME_INFO;
        } else {
            return container.getBytes().readSafely(0) == 2;
        }
    }

    private boolean isPlayerInventoryOpen(Player player) {
        val nmsHandle = NMSUtils.getHandle(player);

        if (MinecraftVersion.v1_20_5.atOrAbove()) { // 1.20.5+
            return PLAYER_ACTIVE_CONTAINER_FIELD.get(nmsHandle) == PLAYER_INVENTORY_CONTAINER_FIELD.get(nmsHandle);
        } else {
            return PLAYER_ACTIVE_CONTAINER_FIELD.get(nmsHandle).getClass() == CONTAINER_PLAYER_CLASS;
        }
    }

}
