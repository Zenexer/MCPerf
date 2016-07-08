package com.earth2me.mcperf.integration.mute;

import lombok.AccessLevel;
import lombok.Getter;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class MuteIntegration {
    private static MuteIntegration instance;
    @Getter(AccessLevel.PROTECTED)
    private static Server server;
    @Getter(AccessLevel.PROTECTED)
    private static Logger logger;

    MuteIntegration() {
    }

    public static void init(Server server, Logger logger) {
        MuteIntegration.instance = null;
        MuteIntegration.server = server;
        MuteIntegration.logger = logger;
    }

    public static void deinit() {
        if (instance != null) {
            instance.onDisable();
        }

        instance = null;
        server = null;
        logger = null;
    }

    public static MuteIntegration get() {
        if (instance == null) {
            try {
                instance = new BanManagerIntegration();
            } catch (NoClassDefFoundError ignored) {
                instance = new BukkitIntegration();
            }
        }

        return instance;
    }

    protected void onDisable() {
    }

    @SuppressWarnings("unused")
    public void mute(Player player, String reason) {
        mute(player, null, null, reason);
    }

    public void mute(Player player, Duration duration, String reason) {
        mute(player, null, duration, reason);
    }

    public void mute(Player player, Player actor, Duration duration, String reason) {
        if (player == null) {
            getLogger().log(Level.SEVERE, String.format("Tried to mute a null player for reason: %s", reason), new IllegalArgumentException("Tried to ban null player"));
            return;
        }

        if (player.isOp()) {
            getLogger().log(Level.WARNING, String.format("Tried to mute an op (%s) for reason: %s", player.getName(), reason));
            return;
        }

        // Dancing with exceptions!
        try {
            onMute(player, actor, duration, reason);
        } catch (Exception e) {
            _onMute(player, actor, duration, reason);
        }
    }

    protected void onMute(Player player, Player actor, Duration duration, String reason) throws Exception {
        try {
            _onMute(player, actor, duration, reason);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, String.format("Mute with Bukkit failed for %s with reason %s", player.getName(), reason));
        }
    }

    @SuppressWarnings("UnusedParameters")
    private void _onMute(Player player, Player actor, Duration duration, String reason) {
        throw new UnsupportedOperationException("Bukkit doesn't support muting yet.  BanManager integration required.");
    }
}
