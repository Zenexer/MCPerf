package com.earth2me.mcperf;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class HeuristicsManager extends Manager {
    private final WeakHashMap<Player, AuraDetector> auraDetectors = new WeakHashMap<>();

    @Getter
    @Setter
    private double significantMovement = 0.4;
    @Getter
    @Setter
    private long timeout = 75;
    @Getter
    @Setter
    private int missThreshold = 5;
    @Getter
    @Setter
    private int hitThreshold = 1;
    @Getter
    @Setter
    private int maxBlackmarks = 4;
    @Getter
    @Setter
    private int forgivenOnDeath = 2;
    @Getter
    @Setter
    private int blackmarksTimeout = 60;
    @Getter
    @Setter
    private List<String> commands = Collections.singletonList("kick %1$s [MCPerf] Cheating: %2$s");
    @Getter
    @Setter
    private boolean debugEnabled = false;

    public HeuristicsManager(Server server, Logger logger, MCPerfPlugin plugin) {
        super(server, logger, plugin);
    }

    private AuraDetector getAuraDetector(Player player) {
        return getAuraDetector(player, true);
    }

    private AuraDetector getAuraDetector(Player player, boolean createIfMissing) {
        AuraDetector i = auraDetectors.get(player);
        if (i == null && createIfMissing) {
            auraDetectors.put(player, i = new AuraDetector(player));
        }
        return i;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player) {
            getAuraDetector((Player) event.getEntity()).onDamaged();
        }

        if (event.getDamager() instanceof Player && event.getEntity() instanceof Player) {
            switch (event.getCause()) {
                case ENTITY_ATTACK:
                    getAuraDetector((Player) event.getDamager()).markHit((Player) event.getEntity());
                    if (event.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING)) {
                        debug("%s is blocking", event.getEntity().getName());
                    }
                    break;

                case PROJECTILE:
                    break;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDamageByBlock(EntityDamageByBlockEvent event) {
        if (event.getEntity() instanceof Player) {
            getAuraDetector((Player) event.getEntity()).onDamaged();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        Inventory inventory = player.getInventory();
        ItemStack a = inventory.getItem(event.getNewSlot());
        ItemStack b = inventory.getItem(event.getPreviousSlot());

        if (a != null && a.getType() != null && a.getType().isEdible() && (b == null || b.getType() == null || !b.getType().isEdible())) {
            getAuraDetector(player).onEating();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerVelocity(PlayerVelocityEvent event) {
        debug("Velocity for %s: %s", event.getPlayer().getName(), event.getVelocity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteract(PlayerInteractEvent event) {
        switch (event.getAction()) {
            case LEFT_CLICK_AIR:
            case LEFT_CLICK_BLOCK:
                getAuraDetector(event.getPlayer()).markMiss();
                break;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        getAuraDetector(event.getEntity()).forgiveBlackmarks(forgivenOnDeath);

        EntityDamageEvent damageEvent = event.getEntity().getLastDamageCause();
        if (damageEvent instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent e = (EntityDamageByEntityEvent) damageEvent;
            if (e.getDamager() instanceof Player) {
                getAuraDetector((Player) e.getDamager()).onKilled(event.getEntity());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKick(PlayerKickEvent event) {
        onPlayerDepart(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        onPlayerDepart(event.getPlayer());
    }

    private void onPlayerDepart(Player player) {
        getAuraDetector(player).close();
        auraDetectors.remove(player);
    }

    public void onCaughtCheating(Player player, String reason) {
        Object[] args = new Object[]{
                player.getName(),
                reason,
                player.getAddress().getAddress().getHostAddress(),
                player.getUniqueId(),
        };

        Util.sendOpMessage(getServer(), "Caught %s cheating: %s.  IP: %s  UUID: %s", args);

        List<String> commands = getCommands();
        if (commands != null) {
            for (String command : commands) {
                dispatchCommand(command, args);
            }
        }
    }

    protected void debug(String format, Object... args) {
        debug(String.format(format, args));
    }

    protected void debug(String message) {
        if (isDebugEnabled()) {
            getServer().getConsoleSender().sendMessage("[MCPerf:HeuristicsManager] " + message);
        }
    }


    private class AuraDetector {
        private WeakReference<Player> player;
        private Long lastTime = null;
        private int suspiciousHits = 0;
        private int misses = 0;
        private int blackmarks = 0;
        private Long lastBlackmarkTime = null;
        private List<Double> variancesH = new LinkedList<>();
        private List<Double> variancesV = new LinkedList<>();
        private Integer autosoupTask;
        private Double actualHealth = null;
        private Integer actualFoodLevel = null;
        private Float actualSaturation = null;
        private Long lastAutosoupCheck = null;
        private Long lastDamaged = null;

        public AuraDetector(Player player) {
            this.player = new WeakReference<>(player);
        }

        public void onKilled(@SuppressWarnings("UnusedParameters") Player player) {
            scheduleAutosoupTask();
        }

        private void scheduleAutosoupTask() {
            Player player = getPlayer();
            if (player == null || !player.isOnline()) {
                return;
            }

            if (autosoupTask != null) {
                getServer().getScheduler().cancelTask(autosoupTask);
                autosoupTask = null;
            }

            autosoupTask = getServer().getScheduler().scheduleSyncDelayedTask(getPlugin(), this::checkAutosoup, 2 * 20);
        }

        public void checkAutosoup() {
            autosoupTask = null;

            Player player = getPlayer();
            if (player == null || !player.isOnline()) {
                return;
            }

            if (player.getHealth() >= player.getMaxHealth() / 3) {
                long time = System.currentTimeMillis();
                if (lastDamaged == null || time - lastDamaged > 2000) {
                    pushState(time);
                    getServer().getScheduler().scheduleSyncDelayedTask(getPlugin(), () -> {
                        if (player.isOnline() && actualHealth != null) {
                            popState();
                        }
                    }, 1);
                } else {
                    scheduleAutosoupTask();
                }
            }
        }

        public void close() {
            popState();

            if (autosoupTask != null) {
                getServer().getScheduler().cancelTask(autosoupTask);
            }

            player = null;
        }

        @SuppressWarnings("unused")
        private void pushState() {
            pushState(System.currentTimeMillis());
        }

        private void pushState(long time) {
            Player player = getPlayer();
            if (player == null) {
                return;
            }

            lastAutosoupCheck = time;
            actualHealth = player.getHealth();
            actualFoodLevel = player.getFoodLevel();
            actualSaturation = player.getSaturation();
            player.setHealth(1.0);
            player.setFoodLevel(1);
            player.setSaturation(0.5f);
        }

        private void popState() {
            Player player = getPlayer();
            if (player == null) {
                return;
            }

            if (actualHealth == null) {
                return;
            }

            player.setHealth(actualHealth);
            player.setFoodLevel(actualFoodLevel);
            player.setSaturation(actualSaturation);

            actualHealth = null;
            actualFoodLevel = null;
            actualSaturation = null;
        }

        public void onDamaged() {
            lastDamaged = System.currentTimeMillis();

            if (lastAutosoupCheck != null) {
                lastAutosoupCheck = null;

                if (actualHealth != null) {
                    popState();
                }
            }
        }

        public void onEating() {
            if (lastAutosoupCheck != null) {
                debug("Eating delay: %d", System.currentTimeMillis() - lastAutosoupCheck);

                if (System.currentTimeMillis() - lastAutosoupCheck < 300) {
                    //onCaughtCheating(getPlayer(), "autosoup");
                    getLogger().log(Level.INFO, String.format("%s appears to be using autosoup (this test is inaccurate)", getPlayer().getName()));
                }

                lastAutosoupCheck = null;
            }

            debug("Eating, but autosoup check is null");
        }

        private void markGotHit() {
            reset();
        }

        public void markMiss() {
            debug("%s missed", getPlayer().getName());
            misses++;

            update();
        }

        public void reset() {
            suspiciousHits = 0;
            misses = 0;
        }

        private void update() {
            Player player = getPlayer();
            if (player == null) {
                return;
            }

            long time = System.currentTimeMillis();
            Long deltaTime = lastTime == null || lastTime > time ? null : time - lastTime;
            if (timeout > 0 && deltaTime != null && deltaTime > timeout) {
                reset();
            }

            lastTime = time;
        }

        @SuppressWarnings("deprecation")
        public void markHit(Player target) {
            getAuraDetector(target).markGotHit();

            Player player = getPlayer();
            if (player == null) {
                return;
            }

            // Variance: How far from the eye point the hit occurred.
            Location a = player.getEyeLocation();
            Location b = target.getEyeLocation();
            double distance = a.distance(b);
            Vector direction = a.getDirection().normalize();
            Vector lookingAt = a.toVector().add(direction.multiply(distance));
            Vector variance = lookingAt.subtract(b.toVector());
            double varianceH = Math.sqrt(Math.pow(variance.getX(), 2) + Math.pow(variance.getZ(), 2));
            double varianceV = Math.abs(variance.getY());
            //double v = Math.sqrt(Math.pow(varianceH, 2) + Math.pow(varianceV, 2));
            variancesH.add(varianceH);
            variancesV.add(varianceV);
            if (variancesH.size() > 32) {
                variancesH.remove(0);
            }
            if (variancesV.size() > 32) {
                variancesV.remove(0);
            }
            double vMeanH = variancesH.stream().collect(Collectors.averagingDouble(Double::doubleValue));
            double vMeanV = variancesV.stream().collect(Collectors.averagingDouble(Double::doubleValue));
            debug("%s hit: %01.3f, %01.3f %s", getPlayer().getName(), vMeanH, vMeanV, player.isOnGround() && !player.isFlying() ? "" : "jumping");

            update();

            if (misses >= getMissThreshold()) {
                suspiciousHits++;
                misses = 0;
            }

            if (calculateScore() >= getHitThreshold()) {
                giveBlackmarks(1);
            }
        }

        public void giveBlackmarks(int count) {
            debug("%s got %d blackmark(s); total blackmarks: %d", getPlayer().getName(), count, blackmarks);
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
                    onCaughtCheating(player, "killaura/auto-click");
                }
            }
        }

        public Player getPlayer() {
            return player.get();
        }

        public void forgiveBlackmarks(int count) {
            blackmarks = Math.max(0, blackmarks - count);
            reset();
        }

        public int calculateScore() {
            return suspiciousHits;
        }
    }
}
