package com.earth2me.mcperf;

import com.google.common.base.Joiner;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerChatEvent;

import java.lang.ref.WeakReference;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class MonitorManager extends Manager {
    private static final int MAX_DISPLAY_RECIPIENTS = 30;

    @SuppressWarnings("deprecation")
    private WeakReference<PlayerChatEvent> previousEvent;
    private Set<WeakReference<Player>> previousRecipients;
    private boolean previousCanceled;

    public MonitorManager(Server server, Logger logger, MCPerfPlugin plugin) {
        super(server, logger, plugin);
    }

    private void send(String message) {
        getServer().getConsoleSender().sendMessage(message);
    }

    @SuppressWarnings("UnusedDeclaration")
    private void send(String... lines) {
        getServer().getConsoleSender().sendMessage(lines);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerChatLowest(@SuppressWarnings("deprecation") PlayerChatEvent event) {
        previousEvent = new WeakReference<>(event);
        previousRecipients = event.getRecipients().stream().<WeakReference<Player>>map(WeakReference::new).collect(Collectors.toSet());

        onPlayerChat(event, true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerChatMonitor(@SuppressWarnings("deprecation") PlayerChatEvent event) {
        boolean intercept = previousEvent == null || previousEvent.get() != event || previousRecipients == null;

        if (intercept || !previousCanceled) {
            onPlayerChat(event, intercept);
        }

        previousEvent = null;
        previousRecipients = null;
        previousCanceled = false;
    }

    private void onPlayerChat(@SuppressWarnings("deprecation") PlayerChatEvent event, boolean intercept) {
        String world = event.getPlayer().getWorld().getName();
        String sender = event.getPlayer().getName();
        String displayName = event.getPlayer().getDisplayName();
        String message = event.getMessage();
        String messageFormat = event.getFormat();
        Set<Player> recipients = event.getRecipients();
        int recipCount = recipients.size();
        int totalCount = getServer().getOnlinePlayers().size();

        String prefix;
        if (intercept) {
            prefix = "(first-chance) ";
        } else {
            prefix = "(modified)     ";
        }

        boolean canceled =
                event.isCancelled()
                        || "".equals(messageFormat)
                        || messageFormat == null
                        || "".equals(message)
                        || message == null;

        if (canceled && !intercept) {
            return;
        }

        if (intercept || recipCount != previousRecipients.size()) {
            String format;
            if (recipCount == 0) {
                format = "[CUSTOM-CHAT=?/%2$d:%3$s:%4$s] %5$s: %6$s";
            } else if (recipCount < totalCount) {
                format = "[LIMITED-CHAT=%d/%d:%s:%s] %s: %s";
            } else {
                format = "[CHAT=%d/%d:%s:%s] %s: %s";
            }

            send(prefix + String.format(format, recipCount, totalCount, world, sender, displayName, message));

            if (!canceled && recipCount < totalCount && recipCount <= MAX_DISPLAY_RECIPIENTS) {
                send(prefix + "<<RECIPIENTS> " + Joiner.on(", ").join(recipients.stream().map(OfflinePlayer::getName).toArray()));
            }
        }

        if (intercept && canceled) {
            previousCanceled = true;
        }
    }
}
