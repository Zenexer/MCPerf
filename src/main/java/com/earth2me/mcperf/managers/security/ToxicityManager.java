package com.earth2me.mcperf.managers.security;

import com.earth2me.mcperf.annotation.ContainsConfig;
import com.earth2me.mcperf.annotation.Service;
import com.earth2me.mcperf.config.ConfigSetting;
import com.earth2me.mcperf.integration.mute.MuteIntegration;
import com.earth2me.mcperf.managers.Manager;
import io.netty.util.internal.ConcurrentSet;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;

@Service
@ContainsConfig
public class ToxicityManager extends Manager {
    private static final Pattern colorStripper = Pattern.compile("§.");  // Can't use ChatColor for this: it only strips valid codes
    private static final Pattern wordSplitter = Pattern.compile("\\W+");

    @Getter
    @Setter
    @ConfigSetting
    private long muteHours = 24;
    @Getter
    @Setter
    @ConfigSetting
    private double minDelayMinutes = 0.5;
    @Getter
    @Setter
    @ConfigSetting
    private double maxDelayMinutes = 4;
    @SuppressWarnings({"RedundantArrayCreation", "SpellCheckingInspection"})
    @Getter
    @Setter
    @ConfigSetting
    private List<String> toxicText = Arrays.asList(new String[]{
            "卐",
            "\uFFFF",
    });
    @SuppressWarnings({"RedundantArrayCreation", "SpellCheckingInspection"})
    @Getter
    @Setter
    @ConfigSetting
    private List<String> toxicWords = Arrays.asList(new String[]{
            "nigger",
            "niggers",
            "nigga",
            "niggas",
    });

    private final ConcurrentSet<UUID> caughtPlayers = new ConcurrentSet<>();
    private final ThreadLocal<Random> random = ThreadLocal.withInitial(Random::new);

    public ToxicityManager() {
        super("EJRddG94aWNpdHmH");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        checkToxicity(event.getPlayer(), event.getMessage(), "chat");
    }

    private boolean checkToxicity(Player player, String text, String check) {
        if (!isToxic(text)) {
            return false;
        }

        onToxic(player, text, check);
        return true;
    }

    private void onToxic(Player player, String text, String check) {
        sendNoticeAsync("Toxic %s by %s: %s", check, player.getName(), text);

        if (getMuteHours() != 0 && caughtPlayers.add(player.getUniqueId())) {
            int min = (int) Math.round(getMinDelayMinutes() * 60 * 20);
            int max = (int) Math.round(getMaxDelayMinutes() * 60 * 20);
            long delay = random.get().nextInt(max - min) + min;

            String reason = "[MCPerf] Toxic " + check;
            Duration duration = getMuteHours() < 0 ? null : Duration.ofHours(getMuteHours());

            getServer().getScheduler().scheduleSyncDelayedTask(
                    getPlugin(),
                    () -> {
                        caughtPlayers.remove(player.getUniqueId());
                        MuteIntegration.get().mute(player, duration, reason);
                    },
                    delay);
        }
    }

    private boolean isToxic(String text) {
        text = colorStripper.matcher(text).replaceAll("")
                .toLowerCase(Locale.ROOT);

        for (String bad : getToxicText()) {
            if (text.contains(bad)) {
                return true;
            }
        }

        String[] words = wordSplitter.split(text);
        for (String word : words) {
            if (getToxicWords().contains(word)) {
                return true;
            }
        }

        return false;
    }
}
