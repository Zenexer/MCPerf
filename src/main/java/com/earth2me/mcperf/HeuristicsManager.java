package com.earth2me.mcperf;

import com.earth2me.mcperf.config.ConfigSetting;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

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
    private int missThreshold = 4;
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
    @ConfigSetting
    private List<String> commands = Collections.singletonList("kick %1$s [MCPerf] Cheating: %2$s");
    @Getter
    @Setter
    @ConfigSetting
    private boolean debugEnabled = false;

    private final Random random = new Random();
    private static final Set<String> bannedNames = new HashSet<>();
    private static final Set<String> bannedIps = new HashSet<>();

    static {
        // TODO: migrate to separate manager
        //noinspection SpellCheckingInspection,ArraysAsListWithZeroOrOneArgument
        bannedNames.addAll(Arrays.asList(
        ));
        //noinspection ArraysAsListWithZeroOrOneArgument
        bannedIps.addAll(Arrays.asList(
        ));
    }

    public HeuristicsManager(Server server, Logger logger, MCPerfPlugin plugin) {
        super(server, logger, plugin, false);

        server.getOnlinePlayers().forEach(this::auditPlayer);
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
    public void onPlayerJoin(PlayerJoinEvent event) {
        auditPlayer(event.getPlayer());
    }

    private void auditPlayer(final Player player) {
        String name = player.getName().toLowerCase();
        String ip = player.getAddress().getAddress().getHostAddress();

        if (bannedNames.contains(name) || bannedIps.contains(ip)) {
            bannedNames.add(name);
            bannedIps.add(ip);

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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getFinalDamage() == 0.0 || !(event.getEntity() instanceof Player)) {
            return;
        }

        Player victim = (Player) event.getEntity();
        getAuraDetector(victim).onDamaged();  // We need to call this even if damager isn't a player.

        if (event.getDamager() instanceof Player) {
            Player attacker = (Player) event.getDamager();

            switch (event.getCause()) {
                case ENTITY_ATTACK:
                    getAuraDetector(attacker).markHit(victim);
                    if (event.getDamage(EntityDamageEvent.DamageModifier.BLOCKING) < 0) {
                        debug("%s is blocking", event.getEntity().getName());
                    }
                    break;

                case PROJECTILE:
                    // TODO
                    break;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByBlock(EntityDamageByBlockEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            getAuraDetector(player).onDamaged();
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
        Vector velocity = event.getVelocity();
        if (velocity.length() > 1.0) {
            debug("Velocity for %s: %s", event.getPlayer().getName(), event.getVelocity());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.isCancelled()) {
            return;  // Just to be safe
        }

        Player player = event.getPlayer();
        AuraDetector detector = getAuraDetector(player);

        switch (event.getAction()) {
            case LEFT_CLICK_AIR:
                detector.markMiss();
                break;

            case LEFT_CLICK_BLOCK:
                if (event.useInteractedBlock() == Event.Result.DENY) {
                    // We're not going to receive a BlockBreakEvent, so we need to figure out if this
                    // type of block breaks instantly.
                    Material material = event.getClickedBlock().getType();
                    if (!material.isSolid()) {  // Should be good enough.
                        return;
                    }
                }

                detector.markMiss();
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

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        // It's possible to break certain blocks very, very quickly.  We don't always know what
        // materials those are because they change with updates, and the server may not be as
        // up-to-date as the client.  Easiest to just taint everything, which doesn't really
        // create any false negatives.

        Player player = event.getPlayer();
        if (player != null) {
            getAuraDetector(player).onAttackSpeedTainted();
        }
    }

    private void onPlayerDepart(Player player) {
        AuraDetector detector = auraDetectors.remove(player);
        if (detector != null) {
            detector.reset();
            detector.close();
        }
    }

    public void onCaughtCheating(Player player, String reason) {
        onPlayerDepart(player);

        if (!player.isOnline()) {
            return;
        }

        Object[] args = new Object[]{
                player.getName(),
                reason,
                player.getAddress().getAddress().getHostAddress(),
                player.getUniqueId(),
        };

        sendAlert("Caught %s cheating: %s.  IP: %s  UUID: %s", args);

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
        private Double missDistance = null;
        private Double hitDistance = null;
        private int suspiciousAims = 0;
        private int farHits = 0;
        private int highSpeedAttacks = 0;

        public AuraDetector(Player player) {
            this.player = new WeakReference<>(player);
        }

        public void onKilled(@SuppressWarnings("UnusedParameters") Player killer) {
            Player player = getPlayerIfEnabled();
            if (player == null) {
                return;
            }

            scheduleAutosoupTask();
        }

        private void scheduleAutosoupTask() {
            Player player = getPlayerIfEnabled();
            if (player == null) {
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

            Player player = getPlayerIfEnabled();
            if (player == null) {
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
            Player player = getPlayerIfEnabled();
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
            Player player = getPlayerIfEnabled();
            if (player == null) {
                return;
            }

            lastDamaged = System.currentTimeMillis();

            if (lastAutosoupCheck != null) {
                lastAutosoupCheck = null;

                if (actualHealth != null) {
                    popState();
                }
            }
        }

        public void onEating() {
            Player player = getPlayerIfEnabled();
            if (player == null) {
                return;
            }

            if (lastAutosoupCheck != null) {
                debug("Eating delay: %d", System.currentTimeMillis() - lastAutosoupCheck);

                if (System.currentTimeMillis() - lastAutosoupCheck < 300) {
                    //onCaughtCheating(player, "autosoup");
                    getLogger().log(Level.INFO, String.format("%s appears to be using autosoup (this test is inaccurate)", player.getName()));
                }

                lastAutosoupCheck = null;
            }

            debug("Eating, but autosoup check is null");
        }

        private double getDistance(Player from, Player to) {
            return from.getEyeLocation().distance(to.getEyeLocation());
        }

        @SuppressWarnings("unused")
        private double getDistanceBuggy(Player from, Player to) {
            Location a = from.getEyeLocation();
            Location b = to.getEyeLocation();
            Vector direction = a.getDirection().normalize();

            List<Double> possibleDistances = new ArrayList<>(3);
            if (a.getX() != b.getX()) {
                possibleDistances.add(direction.clone().multiply((b.getX() - a.getX()) / direction.getX()).length());
            }
            if (a.getZ() != b.getZ()) {
                possibleDistances.add(direction.clone().multiply((b.getZ() - a.getZ()) / direction.getZ()).length());
            }
            if (a.getY() != b.getY()) {
                possibleDistances.add(direction.clone().multiply((b.getY() - a.getY()) / direction.getY()).length());
            }

            if (possibleDistances.isEmpty()) {
                return 0;
            }

            double distance = possibleDistances.stream().filter(n -> n > 0).map(Math::abs).min(Double::compare).get();
            Vector estimator = direction.clone().setY(0).normalize();
            distance -= Math.min(0.5, Math.min(Math.abs(estimator.getX()), Math.abs(estimator.getZ())) * 1.0);
            distance -= 0.45;

            return distance;
        }

        private void markGotHit() {
            reset();
        }

        public void markMiss() {
            Player player = getPlayerIfEnabled();
            if (player == null) {
                return;
            }

            misses++;

            List<Entity> nearby = player.getNearbyEntities(7, 7, 7);
            Double distance = null;
            Player nearest = null;
            for (Entity entity : nearby) {
                if (!(entity instanceof Player)) {
                    return;
                }

                Player target = (Player) entity;
                double d = getDistance(player, target);
                if (distance == null || d < distance) {
                    distance = d;
                    nearest = target;
                }
            }

            if (nearest == null) {
                debug("%s swatted at air", player.getName());
            } else {
                debug("%s missed %s (%01.3f)", player.getName(), nearest.getName(), distance);

                if (hitDistance != null && distance > 0 && (double) hitDistance == distance) {
                    suspiciousAims++;
                    hitDistance = null;

                    if (suspiciousAims >= 8) {
                        onCaughtCheating(player, "aimbot (miss)");  // Particularly Kryptonite and Reflex
                    }
                }

                missDistance = distance;
                if (misses > 3) {
                    suspiciousAims = 0;
                }
            }

            update();
        }

        public void resetSpeed() {
            suspiciousHits = 0;
            misses = 0;
            highSpeedAttacks = 0;
        }

        public void reset() {
            resetSpeed();

            missDistance = null;
            suspiciousAims = 0;
            farHits = 0;
            hitDistance = null;
        }

        public void onAttackSpeedTainted() {
            resetSpeed();
        }

        private void update() {
            Player player = getPlayerIfEnabled();
            if (player == null) {
                return;
            }

            long time = System.currentTimeMillis();
            Long deltaTime = lastTime == null || lastTime > time ? null : time - lastTime;
            if (deltaTime != null) {
                if (deltaTime > 1750) {
                    debug("Initial attack: %d ms", deltaTime);
                    reset();
                } else if (deltaTime > 75) {
                    debug("Normal attack: %d ms", deltaTime);
                    resetSpeed();
                } else {
                    highSpeedAttacks++;
                    debug("High-speed attack %d: %d ms", highSpeedAttacks, deltaTime);

                    if (highSpeedAttacks >= 10) {
                        onCaughtCheating(player, "killaura/speed attack/lag");
                    }
                }
            }

            lastTime = time;
        }

        @SuppressWarnings({"deprecation", "RedundantCast"})
        public void markHit(Player target) {
            getAuraDetector(target).markGotHit();

            if (!isEnabled()) {
                return;
            }

            Player player = getPlayer();

            // Variance: How far from the eye point the hit occurred.
            Location a = player.getEyeLocation();
            Location b = target.getEyeLocation();
            double distance = getDistance(player, target);
            Vector direction = a.getDirection().normalize();
            Vector lookingAt = a.toVector().add(direction.multiply(distance));
            Vector variance = lookingAt.subtract(b.toVector());
            double varianceH = Math.sqrt(Math.pow(variance.getX(), 2) + Math.pow(variance.getZ(), 2));
            double varianceV = variance.getY();
            //double v = Math.sqrt(Math.pow(varianceH, 2) + Math.pow(varianceV, 2));
            variancesH.add(varianceH);
            variancesV.add(varianceV);
            if (variancesH.size() > 10) {
                variancesH.remove(0);
            }
            if (variancesV.size() > 10) {
                variancesV.remove(0);
            }
            //double vMeanH = variancesH.stream().collect(Collectors.averagingDouble(Double::doubleValue));
            //double vMeanV = variancesV.stream().map(Math::abs).collect(Collectors.averagingDouble(Double::doubleValue));
            double vMinH = variancesH.stream().min(Double::compare).get();
            double vMaxH = variancesH.stream().max(Double::compare).get();
            double vMinV = variancesV.stream().min(Double::compare).get();
            double vMaxV = variancesV.stream().max(Double::compare).get();
            double vvH = vMaxH - vMinH;
            double vvV = vMaxV - vMinV;

            if (misses >= getMissThreshold()) {
                suspiciousHits++;
                misses = 0;
            }

            if (missDistance != null) {
                if (distance > 0 && (double) missDistance == distance) {
                    suspiciousAims++;
                }
                missDistance = null;
            }

            if (distance >= 4.2) {
                farHits += 4;
            } else if (farHits > 0) {
                farHits--;
            }

            debug("%s hit %d;  D:%01.3f;  H:%01.3f V:%01.3f;  vH:%01.3f dV:%01.3f;  far:%d aims:%d;  %s",
                    getPlayer().getName(),
                    variancesH.size(),
                    distance,
                    varianceH,
                    varianceV,
                    vvH,
                    vvV,
                    farHits,
                    suspiciousAims,
                    player.isOnGround() && !player.isFlying() ? "" : "  jumping"
            );

            update();

            if (suspiciousAims >= 6) {
                onCaughtCheating(player, "aimbot (hit)");  // Particularly Kryptonite and Reflex
            }

            if (suspiciousHits >= 3) {
                giveBlackmarks(1);
            }

            if (farHits >= 24) {
                onCaughtCheating(player, "reach hack");
            }
        }

        public void giveBlackmarks(int count) {
            Player player = getPlayerIfEnabled();
            if (player == null) {
                return;
            }

            debug("%s got %d blackmark(s); total blackmarks: %d", player.getName(), count, blackmarks);
            reset();

            long time = System.currentTimeMillis() / 1000L;
            if (lastBlackmarkTime != null && lastBlackmarkTime < time && time - lastBlackmarkTime >= getBlackmarksTimeout()) {
                blackmarks = 0;
            } else {
                blackmarks += count;
            }

            if (blackmarks >= getMaxBlackmarks()) {
                onCaughtCheating(player, "killaura/auto-click");
            }
        }

        public Player getPlayer() {
            return player.get();
        }

        public boolean isEnabled() {
            return getPlayerIfEnabled() != null;
        }

        public Player getPlayerIfEnabled() {
            Player player = this.player.get();

            if (player == null || !player.isOnline()) {
                return null;
            }

            GameMode mode = player.getGameMode();
            switch (mode) {
                case ADVENTURE:
                case SURVIVAL:
                    return player;

                case CREATIVE:
                case SPECTATOR:
                    return null;

                default:
                    getLogger().warning(String.format("Unrecognized game mode: %s", mode));
                    return null;
            }
        }

        public void forgiveBlackmarks(int count) {
            blackmarks = Math.max(0, blackmarks - count);
            reset();
        }
    }
}
