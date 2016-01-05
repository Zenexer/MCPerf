package com.earth2me.mcperf;

import lombok.RequiredArgsConstructor;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.logging.Level;
import java.util.logging.Logger;

@RequiredArgsConstructor
public final class SecurityManager implements Listener {
    private static final String KICK_REASON = "Invalid player state";

    private final Server server;
    private final Logger logger;

    private void onInvalidPlayer(Player player, boolean alreadyKicked) {
        if (!alreadyKicked) {
            try {
                player.kickPlayer(KICK_REASON);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to kick player for invalid state", e);
            }
        }
    }

    public boolean assertValidPlayer(Player player) {
        if (!isValidPlayer(player)) {
            onInvalidPlayer(player, false);
            return false;
        }

        return true;
    }

    @SuppressWarnings("RedundantIfStatement")
    public boolean isValidPlayer(Player player) {
        if (server.getPlayer(player.getUniqueId()) != player) {
            return false;
        }

        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        assertValidPlayer(event.getPlayer());
    }
}
