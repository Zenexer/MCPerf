package com.earth2me.mcperf.integration.ban;

import lombok.AccessLevel;
import lombok.Getter;
import org.bukkit.BanList;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class BanIntegration {
    private static BanIntegration instance;
    @Getter(AccessLevel.PROTECTED)
    private static Server server;
    @Getter(AccessLevel.PROTECTED)
    private static Logger logger;

    BanIntegration() {
    }

    public static void init(Server server, Logger logger) {
        BanIntegration.instance = null;
        BanIntegration.server = server;
        BanIntegration.logger = logger;
    }

    public static BanIntegration get() {
        if (instance == null) {
            try {
                instance = new BanManagerIntegration();
            } catch (NoClassDefFoundError ignored) {
                instance = new BukkitIntegration();
            }
        }

        return instance;
    }

    public void ban(Player player, String reason) {
        ban(player, null, null, reason);
    }

    public void ban(Player player, Player actor, Date expires, String reason) {
        if (player == null) {
            getLogger().log(Level.SEVERE, String.format("Tried to ban a null player for reason: %s", reason), new IllegalArgumentException("Tried to ban null player"));
            return;
        }

        if (player.isOp()) {
            getLogger().log(Level.WARNING, String.format("Tried to ban an op (%s) for reason: %s", player.getName(), reason));
            return;
        }

        // Dancing with exceptions!
        try {
            onBan(player, actor, expires, reason);
        } catch (Exception e) {
            _onBan(player, actor, expires, reason);
        }

        if (player.isOnline()) {
            try {
                player.kickPlayer("Banned: " + reason);
            } catch (Exception ignored) {
            }
        }
    }

    protected void onBan(Player player, Player actor, Date expires, String reason) throws Exception {
        try {
            _onBan(player, actor, expires, reason);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, String.format("Ban with Bukkit failed for %s with reason %s", player.getName(), reason));
        }
    }

    private void _onBan(Player player, Player actor, Date expires, String reason) {
        if (!player.isBanned()) {
            getServer().getBanList(BanList.Type.NAME).addBan(player.getName(), reason, expires, actor == null ? null : actor.getName());
        }
    }
}
