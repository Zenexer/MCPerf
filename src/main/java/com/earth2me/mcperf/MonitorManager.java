package com.earth2me.mcperf;

import com.earth2me.mcperf.ob.ContainsConfig;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

import java.util.Set;
import java.util.logging.Logger;

@ContainsConfig
public final class MonitorManager extends Manager {
    private static final int MAX_DISPLAY_RECIPIENTS = 30;

    public MonitorManager(String id, Server server, Logger logger, MCPerfPlugin plugin) {
        super(id, server, logger, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerChat(@SuppressWarnings("deprecation") org.bukkit.event.player.PlayerChatEvent event) {
        if (!isEnabled()) {
            return;
        }

        String world = event.getPlayer().getWorld().getName();
        String sender = event.getPlayer().getName();
        String displayName = event.getPlayer().getDisplayName();
        String message = event.getMessage();
        String messageFormat = event.getFormat();
        Set<Player> recipients = event.getRecipients();
        int recipCount = recipients.size();
        int totalCount = getServer().getOnlinePlayers().size();

        boolean canceled = event.isCancelled() || "".equals(messageFormat) || messageFormat == null || "".equals(message) || message == null;
        String tag = canceled ? ChatColor.RED + "canceled" + ChatColor.RESET + ":" : "";

        String format;
        if (recipCount == 0) {
            format = "[%7$sCUSTOM-CHAT=?/%2$d:%3$s:%4$s] %5$s: %6$s";
        } else if (recipCount < totalCount) {
            format = "[%7$sLIMITED-CHAT=%1$d/%2$d:%3$s:%4$s] %5$s: %6$s";
        } else {
            format = "[%7$sCHAT=%1$d/%2$d:%3$s:%4$s] %5$s: %6$s";
        }

        println(format, recipCount, totalCount, world, sender, displayName, message, tag);

        if (recipCount > 0 && recipCount < totalCount && recipCount <= MAX_DISPLAY_RECIPIENTS) {
            println("<<RECIPIENTS>> " + String.join(", ", recipients.stream().map(OfflinePlayer::getName).toArray(String[]::new)));
        }
    }
}
