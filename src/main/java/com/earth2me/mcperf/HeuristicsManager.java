package com.earth2me.mcperf;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.BanList;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.Vector;

import java.lang.ref.WeakReference;
import java.time.Instant;
import java.time.Period;
import java.util.Date;
import java.util.WeakHashMap;
import java.util.logging.Logger;

public final class HeuristicsManager extends Manager {
    private final WeakHashMap<Player, Info> info = new WeakHashMap<>();

    @Getter
    @Setter
    private double significantMovement = 0.4;
    @Getter
    @Setter
    private long timeout = 100;
    @Getter
    @Setter
    private int threshold = 10;
    @Getter
    @Setter
    private int maxBlackmarks = 4;
    @Getter
    @Setter
    private int forgivenOnDeath = 2;
    @Getter
    @Setter
    private int blackmarksTimeout = 180;
    @Getter
    @Setter
    private String banReason = "Cheating";
    @Getter
    @Setter
    private int banDuration = 3;
    @Getter
    @Setter
    private String banSource = "Plugin:MCPerf";

    public HeuristicsManager(Server server, Logger logger, MCPerfPlugin plugin) {
        super(server, logger, plugin);
    }

    private Info getInfo(Player player) {
        return getInfo(player, true);
    }

    private Info getInfo(Player player, boolean createIfMissing) {
        Info i = info.get(player);
        if (i == null && createIfMissing) {
            info.put(player, i = new Info(player));
        }
        return i;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player && event.getEntity() instanceof Player) {
            switch (event.getCause()) {
                case ENTITY_ATTACK:
                case PROJECTILE:
                    getInfo((Player) event.getDamager()).markHit((Player) event.getEntity());
                    break;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteractEvent(PlayerInteractEvent event) {
        switch (event.getAction()) {
            case LEFT_CLICK_AIR:
            case LEFT_CLICK_BLOCK:
                getInfo(event.getPlayer()).markMiss(event.getPlayer());
                break;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeathEvent(PlayerDeathEvent event) {
        getInfo(event.getEntity()).forgiveBlackmarks(forgivenOnDeath);
    }

    public void onCaughtCheating(Player player) {
        Util.sendOpMessage(getServer(), "Caught %s cheating.  UUID: %s", player.getName(), player.getUniqueId().toString());

        if (banDuration != 0) {
            Date expires = banDuration < 0 ? null : Date.from(Instant.now().plus(Period.ofDays(banDuration)));
            getServer().getBanList(BanList.Type.NAME).addBan(player.getName(), banReason, expires, banSource);
            getServer().getBanList(BanList.Type.IP).addBan(player.getAddress().getAddress().getHostAddress(), banReason, expires, banSource);
        }

        if (player.isOnline()) {
            player.kickPlayer(banReason);
        }
    }


    private class Info {
        private final WeakReference<Player> player;
        private Vector lastTargetLocation;
        private Long lastTime = null;
        private long hitHistory = 0;
        private long hitHistorySize = 0;
        private int blackmarks = 0;
        private Long lastBlackmarkTime = null;

        public Info(Player player) {
            this.player = new WeakReference<>(player);
        }

        public void markHit(Player entity) {
            update(entity, true);
        }

        public void markMiss(Player entity) {
            update(entity, false);
        }

        public void reset() {
            if (hitHistorySize > 0) {
                hitHistorySize = 1;
                hitHistory &= 1L;
            } else {
                hitHistorySize = 0;
                hitHistory = 0;
            }
        }

        private synchronized void update(Player entity, boolean hit) {
            long time = entity.getWorld().getFullTime();
            Long deltaTime = lastTime == null || lastTime > time ? null : time - lastTime;
            if (timeout > 0 && (deltaTime == null || deltaTime > timeout)) {
                lastTargetLocation = null;
                hitHistory = 0;
                hitHistorySize = 0;
            }
            lastTime = time;

            boolean significant;
            Vector location = entity.getLocation().toVector().setY(0.0);
            Double deltaLocation = lastTargetLocation == null ? null : lastTargetLocation.distance(location);
            if (deltaLocation != null && deltaTime != null) {
                double speed = deltaLocation / deltaTime;
                if (speed > significantMovement) {
                    significant = true;
                    lastTargetLocation = location;
                } else {
                    significant = false;
                }
            } else {
                significant = true;
                lastTargetLocation = location;
            }


            if (significant) {
                hitHistory = hitHistory << 1L;

                if (hitHistorySize < 64) {
                    hitHistorySize++;
                }

                if (hit) {
                    hitHistory |= 1L;
                }
            }

            if (calculateScore() >= getThreshold()) {
                giveBlackmarks(1);
            }
        }

        public void giveBlackmarks(int count) {
            reset();

            long time = System.currentTimeMillis() / 1000L;
            if (lastBlackmarkTime != null && lastBlackmarkTime < time && time - lastBlackmarkTime >= getBlackmarksTimeout()) {
                blackmarks = 0;
            } else {
                blackmarks += count;
            }

            if (blackmarks >= getMaxBlackmarks()) {
                Player player = getPlayer();
                if (player != null) {
                    onCaughtCheating(player);
                }
            }
        }

        public Player getPlayer() {
            return player.get();
        }

        public void forgiveBlackmarks(int count) {
            blackmarks = Math.max(0, blackmarks - count);
        }

        public int calculateScore() {
            int score = 0;
            long n = hitHistory;

            for (int i = 0; i < hitHistorySize && (n & 1) == 1; i++, n >>>= 1L) {
                score++;
            }

            return score;
        }
    }
}
