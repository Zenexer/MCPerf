/*
 * WARNING: THIS FILE IS EXTREMELY SENSITIVE
 * =========================================
 *
 * UNLESS YOU HAVE BEEN GRANTED EXPLICIT WRITTEN PERMISSION TO READ THIS
 * SPECIFIC FILE, YOU DO NOT HAVE PERMISSION TO DO SO.  IT CONTAINS
 * HIGHLY CONFIDENTIAL TRADE SECRETS.
 *
 * THE LIST OF PEOPLE CURRENTLY AUTHORIZED TO VIEW THIS FILE:
 *
 *   * Paul Buonopane
 *
 * IF YOU ARE NOT IN THE ABOVE LIST, PLEASE CLOSE THIS FILE.  DOING
 * OTHERWISE IS A BREACH OF LICENSE.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */
package com.earth2me.mcperf;

import com.earth2me.mcperf.config.ConfigSetting;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.*;
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
    private final static Set<Integer> METRO_FLIGHT_ACCEL = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            // These are definitely accurate.
            802, 807, 808, 943, 949, 951, 1336, 1337, 1345, 1347, 1666, 1667, 1704, 1705, 2227, 2228, 2242, 2245, 2400, 2422, 2423, 2940, 2953, 2954, 3264, 3272, 3458, 3463, 3575, 3578, 3645, 3647, 3687, 3688, 3712, 3713, 3727, 3728, 3736, 3737, 3742,
            // Not so sure about these ones.
            60, 100, 167, 278, 279, 289, 464, 465, 481, 773, 775, 1289, 1292, 1560, 1673, 2148, 2153, 2436, 2504, 2962, 3002, 3277, 3301, 3466, 3481, 3580, 3589
    )));

    private final WeakHashMap<Player, Detector> detectors = new WeakHashMap<>();

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

    private Detector getDetector(Player player) {
        return getDetector(player, true);
    }

    private Detector getDetector(Player player, boolean createIfMissing) {
        Detector detector = detectors.get(player);
        if (detector == null && createIfMissing) {
            detectors.put(player, detector = new Detector(player));
        }
        return detector;
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
        getDetector(victim).onDamaged();  // We need to call this even if damager isn't a player.

        if (event.getDamager() instanceof Player) {
            Player attacker = (Player) event.getDamager();

            switch (event.getCause()) {
                case ENTITY_ATTACK:
                    getDetector(attacker).markHit(victim);
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
            getDetector(player).onDamaged();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        Inventory inventory = player.getInventory();
        ItemStack a = inventory.getItem(event.getNewSlot());
        ItemStack b = inventory.getItem(event.getPreviousSlot());

        if (a != null && a.getType() != null && a.getType().isEdible() && (b == null || b.getType() == null || !b.getType().isEdible())) {
            getDetector(player).onEating();
        }

        getDetector(player).onItemHeld();
    }

    @SuppressWarnings("deprecation")
    private static boolean isOnGround(Player player) {
        // As usual, Bukkit deprecated this inappropriately.
        // The reason they provided: Inconsistent with Entity.isOnGround()
        // Yes, that's why it'd be overridden.  Duh.  Except they didn't override it, so it's just wrong.
        return player.isOnGround();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerVelocity(PlayerVelocityEvent event) {
        Player player = event.getPlayer();
        Vector velocity = event.getVelocity();

        if (velocity.getY() == -0.0784000015258789) {
            debug("%s fell and landed on ground", player.getName());
        } else {
            debug("Velocity for %s: %s; %s; speed %f", player.getName(), velocity, isOnGround(player) ? "on ground" : "in air", player.isFlying() ? player.getFlySpeed() : player.getWalkSpeed());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!player.isFlying()) {
            Vector delta = event.getTo().toVector().subtract(event.getFrom().toVector());
            double deltaV = delta.getY();
            double deltaH = Math.sqrt(Math.pow(delta.getX(), 2) + Math.pow(delta.getZ(), 2));
            if (player.getWalkSpeed() != 0.2) {
                float speed = player.getWalkSpeed();
                deltaH /= speed * 5;
            }
            if (deltaV > 0.5 || (deltaH > 0.6 && deltaV != 0.41999998688697815) || (deltaV > 0 && player.getName().equals("Exabit"))) {  // 0.412 is jump while sprinting
                debug("Movement for %s: %s; H:%.4f, V:%.4f; %s; speed %f", player.getName(), delta, deltaH, deltaV, isOnGround(player) ? "on ground" : "in air", player.getWalkSpeed());
            }
            getDetector(player).onMove(deltaH, deltaV);
        } else {
            getDetector(player).reset();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.isCancelled()) {
            return;  // Just to be safe
        }

        Player player = event.getPlayer();
        Detector detector = getDetector(player);

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
        getDetector(event.getEntity()).forgiveBlackmarks(forgivenOnDeath);

        EntityDamageEvent damageEvent = event.getEntity().getLastDamageCause();
        if (damageEvent instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent e = (EntityDamageByEntityEvent) damageEvent;
            if (e.getDamager() instanceof Player) {
                getDetector((Player) e.getDamager()).onKilled(event.getEntity());
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
            getDetector(player).onAttackSpeedTainted();
        }
    }

    private void onPlayerDepart(Player player) {
        Detector detector = detectors.remove(player);
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
        if (isDebugEnabled()) {
            debug(String.format(format, args));
        }
    }

    protected void debug(String message) {
        if (isDebugEnabled()) {
            println("[MCPerf:Heuristics] " + message);
        }
    }

    protected void info(String format, Object... args) {
        info(String.format(format, args));
    }

    protected void info(String message) {
        println("[MCPerf:Heuristics] " + message);
    }


    private class Detector {
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
        private Long lastItemHeldTime = null;
        private int obviousFlyHacks = 0;
        private int suspiciousFlyHacks = 0;
        private Long firstInAir = null;
        private int inAirScore = 0;
        private double lastAirDeltaV = 0;
        private Double lastAirAccelV = null;
        private Boolean lastAirAccelGoingUp = null;

        public Detector(Player player) {
            this.player = new WeakReference<>(player);
        }

        public void onMove(double deltaH, double deltaV) {
            Player player = getPlayerIfEnabled();
            if (player == null) {
                return;
            }

            int deltaV4s = (int)(deltaV * 10000);
            int deltaV4 = Math.abs(deltaV4s);
            int deltaH4 = (int)(deltaH * 10000);

            boolean inAir = !isOnGround(player) && player.getLocation().getX() > 0;
            if (inAir) {
                long now = System.currentTimeMillis();

                if (firstInAir == null) {
                    firstInAir = now;
                    inAirScore = 1;
                    lastAirDeltaV = deltaV;
                    lastAirAccelV = null;
                    lastAirAccelGoingUp = null;
                    debug("Entered airspace: %s", player.getName());
                } else {
                    double accelV = deltaV - lastAirDeltaV;
                    int accelV4 = (int)(accelV * 10000);
                    long timeInAir = now - firstInAir;

                    if (lastAirAccelV != null) {
                        boolean airAccelGoingUp = accelV > lastAirAccelV;
                        if (lastAirAccelGoingUp != null && airAccelGoingUp != lastAirAccelGoingUp) {
                            inAirScore += 8;
                            debug("In air +8 change in vertical accel^2 direction: %d; %d ms, %s", inAirScore, timeInAir, player.getName());
                        }
                        lastAirAccelGoingUp = airAccelGoingUp;
                    }

                    if (deltaV < -0.38 ) {  // Is this effective?
                        if (inAirScore > 0) {
                            inAirScore--;
                            if (inAirScore > 0 || obviousFlyHacks > 1 || suspiciousFlyHacks > 1) {
                                info("In air -1: %d; %d ms; %s", inAirScore, timeInAir, player.getName());
                            } else {
                                debug("In air -1: %d; %d ms; %s", inAirScore, timeInAir, player.getName());
                            }
                        } else {
                            debug("In air, but score already 0 for %s", player.getName());
                        }
                    } else if (accelV4 == 4020) {
                        inAirScore += 8;
                        info("In air +8 obvious Metro downward accel: %d; %d ms; %s", inAirScore, timeInAir, player.getName());
                    } else if (accelV4 == 3332) {
                        inAirScore += 8;
                        info("In air +8 obvious Huzuni downward accel: %d; %d ms; %s", inAirScore, timeInAir, player.getName());
                    } else if (deltaV > lastAirDeltaV) {
                        info("In air +6: %d; %d ms; %s", inAirScore, timeInAir, player.getName());
                        inAirScore += 6;
                    } else if (deltaV == lastAirDeltaV){
                        info("In air +2: %d; %d ms; %s", inAirScore, timeInAir, player.getName());
                        inAirScore += 3;
                    } else {
                        // Jumps trigger this
                        info("Accelerating downward (jumping/falling): %s; %.4f blocks/sec^2", player.getName(), accelV);
                    }

                    if (timeInAir >= 600 && inAirScore >= 24) {
                        onCaughtCheating(player, "flight");
                    } else {
                        lastAirAccelV = accelV;
                    }
                }
            } else if (firstInAir != null) {
                debug("Touched ground: %s", player.getName());
                firstInAir = null;
                inAirScore = 0;
                lastAirDeltaV = 0;
                lastAirAccelV = null;
                lastAirAccelGoingUp = null;
            }

            if (deltaH == 0 && (deltaV == 1 || deltaV == -1)) {  // Wurst
                obviousFlyHacks += 4;
                if (inAirScore > 1 || obviousFlyHacks > 1 || suspiciousFlyHacks > 1) {
                    info("Obvious flight hack (Wurst flight) for %s +4: %d", player.getName(), obviousFlyHacks);
                } else {
                    debug("Obvious flight hack (Wurst flight) for %s +4: %d", player.getName(), obviousFlyHacks);
                }
            } else if (deltaV4 == 3750) {  // Metro
                obviousFlyHacks += 4;
                info("Obvious flight hack (Metro flight) for %s +4: %d; %.4f", player.getName(), obviousFlyHacks, deltaV);
            } else if (deltaV4 == 3749) {  // Huzuni
                obviousFlyHacks += 4;
                info("Obvious flight hack (Huzuni flight) for %s +4: %d; %.4f", player.getName(), obviousFlyHacks, deltaV);
            } else if (deltaH4 == 9183 || deltaH4 == 10014) {  // Wurst speed hacks
                obviousFlyHacks += 2;
                info("Obvious speed hack (Wurst speed) for %s +1: %d; %.4f", player.getName(), obviousFlyHacks, deltaH);
            } else if (obviousFlyHacks > 0) {
                obviousFlyHacks--;
                if (obviousFlyHacks > 0 || inAirScore > 1 || suspiciousFlyHacks > 1) {
                    info("Decremented obvious fly hack movements for %s -1: %d", player.getName(), obviousFlyHacks);
                } else {
                    debug("Decremented obvious fly hack movements for %s -1: %d", player.getName(), obviousFlyHacks);
                }
            }

            if (deltaH4 == 9800) {  // Wurst
                suspiciousFlyHacks += 16;
                info("Suspicious flight (Wurst flight) for %s +16: %d; %.4f", player.getName(), suspiciousFlyHacks, deltaV);
            } else if (deltaH4 >= 15000) {
                suspiciousFlyHacks += 16;
                info("Very fast horizontal movement for %s +16: %d; %.4f", player.getName(), suspiciousFlyHacks, deltaH);
            } else if (deltaV4 == 10100) {  // Not sure which client this is
                suspiciousFlyHacks += 24;
                info("Hack client signature upward movement for %s +24: %d; %.4f", player.getName(), suspiciousFlyHacks, deltaV);
            } else if (deltaV4s > 11200) {
                // 1.1200 and 1.0192 seem to be PvP-related; knockback, maybe?  Sometimes 1.1084 instead of 1.1200.
                suspiciousFlyHacks += 16;
                info("Very fast upward movement for %s +16: %d; %.4f", player.getName(), suspiciousFlyHacks, deltaV);
            } else if (METRO_FLIGHT_ACCEL.contains(deltaV4)) {  // Metro flight at speed 1.0
                suspiciousFlyHacks++;
                if (suspiciousFlyHacks > 1 || obviousFlyHacks > 1 || inAirScore > 1) {
                    info("Suspicious vertical acceleration for %s +1: %d, %.4f", player.getName(), suspiciousFlyHacks, deltaV);
                } else {
                    debug("Suspicious vertical acceleration for %s +1: %d, %.4f", player.getName(), suspiciousFlyHacks, deltaV);
                }
            } else if (suspiciousFlyHacks > 0 && obviousFlyHacks <= 0) {
                suspiciousFlyHacks--;
                if (suspiciousFlyHacks > 0 || obviousFlyHacks > 1 || inAirScore > 1) {
                    info("Decremented suspicious movements for %s -1: %d", player.getName(), suspiciousFlyHacks);
                } else {
                    debug("Decremented suspicious movements for %s -1: %d", player.getName(), suspiciousFlyHacks);
                }
            }

            if (obviousFlyHacks >= 12) {
                onCaughtCheating(player, "fly hacks");
            }
            if (suspiciousFlyHacks >= 64) {
                onCaughtCheating(player, "fly/speed hacks");
            }
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

            //autosoupTask = getServer().getScheduler().scheduleSyncDelayedTask(getPlugin(), this::checkAutosoup, 2 * 20);
        }

        @SuppressWarnings("unused")
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

            player.clear();
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
                long now = System.currentTimeMillis();
                long sinceEating = lastItemHeldTime == null ? -1 : now - lastItemHeldTime;
                long delay = now - lastAutosoupCheck;
                debug("Eating delay: %d ms; since eating: %d ms", delay, sinceEating);

                if (sinceEating < 1000) {
                    return;
                }

                if (delay < 250) {
                    //onCaughtCheating(player, "autosoup");
                    getLogger().log(Level.INFO, String.format("%s appears to be using autosoup (this test is inaccurate) (delay: %d ms; since eating: %d ms)", player.getName(), delay, sinceEating));
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
            suspiciousFlyHacks = 0;
            obviousFlyHacks = 0;
            inAirScore = 0;
            firstInAir = null;
            lastAirDeltaV = 0;
            lastAirAccelGoingUp = null;
            lastAirAccelV = null;
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
            getDetector(target).markGotHit();

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

            if (distance >= 6) {
                farHits += 4;
            } else if (distance >= 5) {
                farHits += 2;
            } if (distance >= 4.2) {
                farHits += 1;
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
                onCaughtCheating(player, "reach hack/excessive lag");
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

        public void onItemHeld() {
            lastItemHeldTime = System.currentTimeMillis();
        }
    }
}
