package com.earth2me.mcperf.managers.security;

import com.earth2me.mcperf.Util;
import com.earth2me.mcperf.managers.Manager;
import com.earth2me.mcperf.ob.ContainsConfig;
import com.earth2me.mcperf.ob.Service;
import com.google.common.base.Joiner;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.event.player.PlayerUnregisterChannelEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.plugin.messaging.PluginMessageRecipient;

import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@ContainsConfig
public class PluginMessageManager extends Manager implements PluginMessageListener {
    private final static Charset CHARSET = Charset.forName("UTF-8");

    private WeakHashMap<Player, FmlInfo> fmls;

    public PluginMessageManager() {
        super("MzIbcGx1Z2luTWVzc2FnZQo=");
    }

    @Override
    public void onInit() {
        if (!isEnabled()) {
            return;
        }

        fmls = new WeakHashMap<>();

        registerDuplex("FML");
        registerDuplex("FML|HS");
        registerDuplex("FML|MP");

        getServer().getPluginCommand("chans").setExecutor(this::onChansCommand);
    }

    @Override
    public void onDeinit() {
        Bukkit.getMessenger().unregisterIncomingPluginChannel(getPlugin());
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(getPlugin());

        if (fmls != null) {
            fmls.clear();
            fmls = null;
        }
    }

    private void registerRx(String channel) {
        Bukkit.getMessenger().registerIncomingPluginChannel(getPlugin(), channel, this);
    }

    private void registerTx(String channel) {
        Bukkit.getMessenger().registerOutgoingPluginChannel(getPlugin(), channel);
    }

    private void registerDuplex(String channel) {
        registerRx(channel);
        registerTx(channel);
    }

    private boolean onChansCommand(final CommandSender sender, Command command, String label, String[] args) {
        boolean hasAllPermissions = sender.isOp() || sender.hasPermission("mcperf.chans.*");

        if (!hasAllPermissions && !sender.hasPermission("mcperf.chans")) {
            return Util.denyPermission(sender);
        }

        if (args.length < 1) {
            return false;
        }

        if (args.length > 1 && !hasAllPermissions && !sender.hasPermission("mcperf.chans.multiple")) {
            return Util.denyPermission(sender);
        }

        Stream<Player> players;
        // TODO: We can probably extract this bit, but there are a lot of local variables.
        //noinspection Duplicates
        if (args.length == 1 && "*".equals(args[0])) {
            if (!hasAllPermissions && !sender.hasPermission("mcperf.chans.all")) {
                return Util.denyPermission(sender);
            }

            players = getServer().getOnlinePlayers().stream().map(p -> p);  // Gets around buggy generics
        } else {
            players = Stream.of(args)
                    .map(getServer()::getPlayer)
                    .filter(java.util.Objects::nonNull);
        }

        Map<String, Set<String>> playerChannels = players.collect(Collectors.toMap(Player::getName, PluginMessageRecipient::getListeningPluginChannels));

        if (playerChannels.isEmpty()) {
            sender.sendMessage("No online players matched your parameters.");
            return true;
        }

        List<String> notListening = playerChannels.entrySet().stream()
                .filter(e -> e.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (notListening.isEmpty()) {
            sender.sendMessage("All specified players have at least one channel registered.");
        } else {
            sender.sendMessage("None registered: " + Joiner.on(", ").join(notListening));
        }

        playerChannels.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .forEach(e -> sender.sendMessage(String.format("- %s: %s", e.getKey(), Joiner.on(", ").join(e.getValue()))));
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerQuit(PlayerQuitEvent event) {
        fmls.remove(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKick(PlayerKickEvent event) {
        fmls.remove(event.getPlayer());
    }

    // We need to send a packet early in the connection process, at a point that only LilyPad can handle.
    // As such, this is disabled for now.
    @SuppressWarnings("unused")
    public void initFml(Player player) {
        player.sendPluginMessage(getPlugin(), "FML", new byte[]{
                FmlPacketType.INIT_HANDSHAKE.getId(),
                FmlSide.CLIENT.getId(),
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerRegisterChannel(PlayerRegisterChannelEvent event) {
        Player player = event.getPlayer();
        String channel = event.getChannel();
        String alert = String.format("[CHANSNIFF] %s registered %s", player.getName(), channel);

        switch (channel) {
            case "FML":  // I previously noted to myself that this is the one that triggers the handshake.
            case "FORGE":
            case "FML|HS":
            case "FML|MP":
                debug(alert);
                break;

            default:
                sendNotice(alert);
                break;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerUnregisterChannel(PlayerUnregisterChannelEvent event) {
        Player player = event.getPlayer();
        String channel = event.getChannel();

        debug("[CHANSNIFF] %s unregistered %s", player.getName(), channel);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] data) {
        debug("[CHANSNIFF:D] Received data from %s on channel %s", player.getName(), channel);

        try {
            switch (channel) {
                case "FML":
                    onReceivedFml(player, channel, data);
                    break;
                case "FML|HS":
                    onReceivedFmlHs(player, channel, data);
                    break;
                case "FML|MP":
                    onReceivedFmlMp(player, channel, data);
                    break;
            }
        } catch (Exception ex) {
            getLogger().warning(String.format("Got bad plugin message from player %s on channel %s: %s", player.getName(), channel, ex.getMessage()));
        }
    }

    private void debug(String format, Object... args) {
        getServer().getConsoleSender().sendMessage(String.format(format, args));
    }

    private void onModsFound(Player player, Map<String, String> mods) {
        sendAlert(
                "[MODSNIFF] Found mods for player %s: %s",
                player.getName(),
                Joiner.on(", ").join(mods.entrySet().stream()
                        .map(e -> String.format("%s=%s", e.getKey(), e.getValue()))
                        .collect(Collectors.toList()))
        );
    }

    private FmlInfo getFml(Player player) {

        FmlInfo fml;

        if (!fmls.containsKey(player)) {
            fmls.put(player, fml = new FmlInfo());
        } else {
            fml = fmls.get(player);

            if (fml == null) {
                fmls.put(player, fml = new FmlInfo());
            }
        }

        return fml;
    }

    private static int readFmlVarInt(ByteBuf buffer, int max) {
        assert max > 0 : "FML doesn't support zero-length varints";
        assert max <= 5 : "FML doesn't support varints greater than 5 iterations";

        int value = 0;

        for (byte b, i = 0; ((b = buffer.readByte()) >>> 7) == 1; i++) {
            if (i >= max) {
                throw new ArithmeticException("FML varint was too large");
            }

            value |= (b & 0x7F) << i * 7;
        }

        return value;
    }

    private static String readFmlString(ByteBuf buffer) {
        int length = readFmlVarInt(buffer, 2);
        int index = buffer.readerIndex();

        try {
            return buffer.toString(index, length, CHARSET);
        } finally {
            buffer.readerIndex(index + length);
        }
    }

    @SuppressWarnings("UnusedParameters")
    private void onReceivedFml(Player player, String channel, byte[] data) {
        // Not implemented yet.
    }

    @SuppressWarnings("UnusedParameters")
    private void onReceivedFmlMp(Player player, String channel, byte[] data) {
        // Not implemented yet.
    }

    private void onReceivedFmlHs(Player player, String channel, byte[] data) {
        FmlInfo fml = getFml(player);
        ByteBuf rx = Unpooled.unmodifiableBuffer(Unpooled.wrappedBuffer(data));
        ByteBuf tx = Unpooled.buffer();

        FmlHsStage stage = fml.hsStage != null ? fml.hsStage : FmlHsStage.HELLO;
        debug("[CHANSNIFF:D] channel %s stage %s player %s", channel, stage.name(), player.getName());

        switch (stage) {
            case START:
                try {
                    @SuppressWarnings("deprecation")
                    int environment = player.getWorld().getEnvironment().getId();
                    fml.protocol = 4;
                    fml.dimension = environment;

                    FmlHsPacketType.SERVER_HELLO.begin(tx);
                    tx.writeByte(fml.protocol);
                    tx.writeInt(fml.dimension);
                } finally {
                    fml.hsStage = FmlHsStage.HELLO;
                }
                break;

            case HELLO:
                switch (FmlHsPacketType.identify(rx)) {
                    case CLIENT_HELLO:
                        try {
                            fml.protocol = rx.readByte();
                        } finally {
                            fml.hsStage = FmlHsStage.HELLO;  // Continue with this stage
                        }
                        break;

                    case MOD_LIST:
                        debug("################## Got mod list on channel %s", channel);
                        try {
                            fml.modCount = readFmlVarInt(rx, 2);
                            HashMap<String, String> modTags = new HashMap<>(fml.modCount);
                            for (int i = 0; i < fml.modCount; i++) {
                                modTags.put(readFmlString(rx), readFmlString(rx));
                            }
                            fml.modTags = Collections.unmodifiableMap(modTags);

                            FmlHsPacketType.MOD_LIST.begin(tx);
                            tx.writeByte(0);  // Easy way of saying we have no mods.  Constitutes entire ModList packet.

                            if (fml.modCount > 0) {
                                onModsFound(player, fml.modTags);
                            }
                        } finally {
                            fml.hsStage = FmlHsStage.CLIENT_ACK;
                        }
                        break;

                    default:
                        fml.hsStage = FmlHsStage.ERROR;
                        break;
                }
                break;

            case CLIENT_ACK:
                try {
                    // RegistryData packets would go here.  Once they're finished, a HandshakeAck packet gets sent.
                    FmlHsPacketType.HANDSHAKE_ACK.begin(tx);
                    tx.writeByte(fml.hsStage.ordinal());
                } finally {
                    fml.hsStage = FmlHsStage.COMPLETE;
                }
                break;

            case COMPLETE:
                try {
                    FmlHsPacketType.HANDSHAKE_ACK.begin(tx);
                    tx.writeByte(fml.hsStage.ordinal());
                } finally {
                    fml.hsStage = FmlHsStage.DONE;
                }
                break;

            case DONE:
            case ERROR:
                return;
        }

        if (tx.writerIndex() > 0) {
            player.sendPluginMessage(getPlugin(), channel, tx.array());
        }
    }


    private static class FmlInfo {
        public FmlHsStage hsStage = FmlHsStage.START;
        public byte protocol;
        public int dimension;
        public int modCount;
        public Map<String, String> modTags = Collections.emptyMap();
    }

    @SuppressWarnings("unused")
    private enum FmlSide {
        CLIENT((byte) 0x00),
        SERVER((byte) 0x01),;

        @Getter
        private final byte id;

        FmlSide(byte id) {
            this.id = id;
        }
    }

    @SuppressWarnings("unused")
    private enum FmlPacketType {
        INIT_HANDSHAKE((byte) 0x00),
        OPEN_GUI((byte) 0x01),
        ENTITY_SPAWN_MESSAGE((byte) 0x02),
        ENTITY_ADJUST_MESSAGE((byte) 0x03),;

        @Getter
        private final byte id;

        FmlPacketType(byte id) {
            this.id = id;
        }
    }

    @SuppressWarnings("unused")
    private enum FmlHsStage {
        START,
        HELLO,
        CLIENT_ACK,
        COMPLETE,
        DONE,
        ERROR,;
    }

    @SuppressWarnings("unused")
    private enum FmlHsPacketType {
        UNKNOWN(null),
        SERVER_HELLO((byte) 0x00),
        CLIENT_HELLO((byte) 0x01),
        MOD_LIST((byte) 0x02),
        REGISTRY_DATA((byte) 0x03),
        HANDSHAKE_ACK((byte) 0xff),
        HANDSHAKE_RESET((byte) 0xfe),;

        @Getter
        private final Byte id;

        FmlHsPacketType(Byte id) {
            this.id = id;
        }

        public static FmlHsPacketType get(byte id) {
            for (FmlHsPacketType i : FmlHsPacketType.values()) {
                if (i.id == id) {
                    return i;
                }
            }

            return UNKNOWN;
        }

        public static FmlHsPacketType identify(ByteBuf rx) {
            return get(rx.readByte());
        }

        public void begin(ByteBuf tx) {
            tx.writeByte(id);
        }
    }
}
