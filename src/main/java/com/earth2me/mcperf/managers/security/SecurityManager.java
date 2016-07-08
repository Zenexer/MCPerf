package com.earth2me.mcperf.managers.security;

import com.earth2me.mcperf.config.ConfigSetting;
import com.earth2me.mcperf.managers.Manager;
import com.earth2me.mcperf.annotation.ContainsConfig;
import com.earth2me.mcperf.annotation.Service;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

@Service
@ContainsConfig
public final class SecurityManager extends Manager {
    private static final String KICK_REASON = "Invalid player state";

    @Getter
    @Setter
    @ConfigSetting
    private Set<String> bannedNames = new HashSet<>();
    @Getter
    @Setter
    @ConfigSetting
    private Set<String> bannedIPs = new HashSet<>();
    @Getter
    @Setter
    @ConfigSetting
    private Set<UUID> bannedUUIDs = new HashSet<>();
    private final Random random = new Random();

    public SecurityManager() {
        super("MTUbc2VjdXJpdHkK");
    }

    @Override
    public void onInit() {
        getServer().getOnlinePlayers().forEach(this::auditPlayer);
    }

    private void onInvalidPlayer(Player player, boolean alreadyKicked) {
        if (!alreadyKicked) {
            try {
                player.kickPlayer(KICK_REASON);
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Failed to kick player for invalid state", e);
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
        if (getServer().getPlayer(player.getUniqueId()) != player) {
            return false;
        }

        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!isEnabled()) {
            return;
        }

        assertValidPlayer(event.getPlayer());
        auditPlayer(event.getPlayer());
    }

    private void auditPlayer(final Player player) {
        if (!isEnabled()) {
            return;
        }

        String name = player.getName().toLowerCase();
        String ip = player.getAddress().getAddress().getHostAddress();

        if (bannedNames.contains(name) || bannedIPs.contains(ip)) {
            bannedNames.add(name);
            bannedIPs.add(ip);

            long delay = 20 * random.nextInt(10) + 2;
            getServer().getScheduler().scheduleSyncDelayedTask(getPlugin(), () -> {
                if (player.isOnline()) {
                    final String[] reasons = {
                            "Spam",
                            "Ban evasion",
                            "bypassing",
                            "Troublemaker",
                            "causing trouble",
                            "breaking rules",
                            "threatening staff",
                            "threatening unless unmuted",
                            "spamming unless unmuted",
                            "Spammer",
                            "Please don't spam",
                            "Please don't harass staff",
                            "harassing staff",
                            "ban bypass",
                            "Mute bypass",
                    };
                    int reasonId = random.nextInt(reasons.length);
                    if (reasonId < 0 || reasonId >= reasons.length) {
                        reasonId = 0;
                    }
                    player.kickPlayer(reasons[reasonId]);
                }
            }, delay);
        }
    }
}
