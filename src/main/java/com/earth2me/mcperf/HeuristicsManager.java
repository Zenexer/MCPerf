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
import com.earth2me.mcperf.mojang.MathHelper;
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
    private static final Set<Integer> JUMP_ACCELS = new HashSet<>(Arrays.asList(-850, -1684, -2501, -3331, -4115, -4884, -3487, -752, -5637, -6375, -7098, -4372));

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
        Detector victimD = getDetector(victim);
        victimD.onDamaged();  // We need to call this even if damager isn't a player.

        if (event.getDamager() instanceof Player) {
            Player attacker = (Player) event.getDamager();
            Detector attackerD = getDetector(attacker);

            victimD.markGotHit(attacker);

            switch (event.getCause()) {
                case ENTITY_ATTACK:
                    attackerD.markHit(victim);
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
    private boolean isOnGround(Player player) {
        // As usual, Bukkit deprecated this inappropriately.
        // The reason they provided: Inconsistent with Entity.isOnGround()
        // Yes, that's why it'd be overridden.  Duh.  Except they didn't override it, so it's just wrong.
        return player.isOnGround();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerVelocity(PlayerVelocityEvent event) {
        Player player = event.getPlayer();
        Vector velocity = event.getVelocity();
        //Detector detector = getDetector(player);

        if (velocity.getY() == -0.0784000015258789 && velocity.getX() == 0.0 && velocity.getZ() == 0.0) {
            debug("Standard damage velocity event: %s", player.getName());
        } else {
            debug("Velocity for %s: %s; %s; speed %f", player.getName(), velocity, isOnGround(player) ? "on ground" : "in air", player.isFlying() ? player.getFlySpeed() : player.getWalkSpeed());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!player.isFlying() && player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
            Vector delta = event.getTo().toVector().subtract(event.getFrom().toVector());
            double deltaV = delta.getY();
            double deltaH = Math.sqrt(Math.pow(delta.getX(), 2) + Math.pow(delta.getZ(), 2));
            if (player.getWalkSpeed() != 0.2) {
                float speed = player.getWalkSpeed();
                deltaH /= speed * 5;
            }
            if (deltaV > 0.5 || (deltaH > 0.6 && deltaV != 0.41999998688697815)) {  // 0.412 is jump while sprinting
                debug("Movement for %s: %s; H:%.6f, V:%.6f; %s; speed %f", player.getName(), delta, deltaH, deltaV, isOnGround(player) ? "on ground" : "in air", player.getWalkSpeed());
            }
            getDetector(player).onMove(deltaH, deltaV);
        } else {
            //debug("Reset due to flight/game mode: %s", player.getName());
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

    private static final UUID devId = UUID.fromString("04e66058-ddf6-4520-93b2-3bc3f675c132");

    protected void dev(String message) {
        Player dev = getServer().getPlayer(devId);
        if (dev != null) {
            dev.sendMessage(":: " + message);
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
            dev(message);
        }
    }

    protected void info(String format, Object... args) {
        info(String.format(format, args));
    }

    protected void info(String message) {
        println("[MCPerf:Heuristics] " + message);
        dev(message);
    }

    private static boolean isClimbable(Material material) {
        switch (material) {
            case LADDER:
            case WATER:
            case STATIONARY_WATER:
            case LAVA:
            case STATIONARY_LAVA:
            case VINE:
            case WEB:
                return true;

            default:
                return false;
        }
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
        private int jumpingCount = 0;
        private int fallingCount = 0;
        private Long lastKnockbackTime = null;
        private int antiKnockbackCount = 0;
        private final long knockbackTimeout = 500;
        private final long consecutiveKnockbackTimeout = 300;
        private long lastClimbableTime = 0;

        public Detector(Player player) {
            this.player = new WeakReference<>(player);
        }

        public void onKnockback(Entity damager) {
            Player player = getPlayerIfEnabled();
            if (player == null) {
                return;
            }

            long now = System.currentTimeMillis();

            if (lastKnockbackTime != null) {
                long timeSinceKnockback = now - lastKnockbackTime;
                if (timeSinceKnockback > consecutiveKnockbackTimeout) {
                    debug("Anti-knockback +6: %d; %s; %d ms delay; subsequent knockback", antiKnockbackCount + 6, player.getName(), timeSinceKnockback);
                    onAntiKnockback(6, 1);
                }
            } else {
                Location pl = player.getLocation();
                Vector pv = pl.toVector();
                Vector dv = damager.getLocation().toVector();
                double distX = dv.getX() - pv.getX();
                double distZ = dv.getZ() - pv.getZ();
                double dist2 = distX * distX + distZ * distZ;

                if (dist2 < 0.0001) {
                    // Direction is random
                    return;
                }

                player.getVelocity();

                //float yaw = (float) (MathHelper.yaw(distZ, distX) * 180.0D / 3.1415927410125732D - (double) pl.getYaw());
                float dist = MathHelper.sqrt(dist2);
                float factor = 0.4F;
                Vector mot = player.getVelocity();
                double motX = mot.getX();
                double motY = mot.getY();
                double motZ = mot.getZ();
                motX /= 2.0D;
                motY /= 2.0D;
                motZ /= 2.0D;
                motX -= distX / (double) dist * (double) factor;
                motY += (double) factor;
                motZ -= distZ / (double) dist * (double) factor;
                if (motY > 0.4000000059604645D) {
                    motY = 0.4000000059604645D;
                }
                Vector change = new Vector(motX, motY, motZ).subtract(mot);
                mot.setX(motX);
                mot.setY(motY);
                mot.setZ(motZ);

                debug("Expected knockback for %s: %s", player.getName(), change.toString());

                // TODO: Account for attribute: generic.knockbackResistance

                lastKnockbackTime = now;
            }
        }

        private void onAntiKnockback(int inc, int id) {
            Player player = getPlayerIfEnabled();
            if (player == null) {
                return;
            }

            antiKnockbackCount += inc;

            if (antiKnockbackCount >= 24) {
                info("TRIGGERED anti-knockback#%d %d: %s", id, antiKnockbackCount, player.getName());
                //onCaughtCheating(player, String.format("anti-knockback#%d", id));
            }
        }

        public void onMove(double deltaH, double deltaV) {
            Player player = getPlayerIfEnabled();
            if (player == null) {
                return;
            }

            int deltaV4s = (int) (deltaV * 10000);
            int deltaV4 = Math.abs(deltaV4s);
            int deltaH4 = (int) (deltaH * 10000);
            boolean inAir = !isOnGround(player) && player.getLocation().getY() >= 0.0;
            long now = System.currentTimeMillis();
            Long preLastKnockbackTime = lastKnockbackTime;

            if (lastKnockbackTime != null) {
                long timeSinceKnockback = now - lastKnockbackTime;

                if (timeSinceKnockback <= knockbackTimeout && deltaV4s <= 0) {
                    debug("Knockback grace period: %s", player.getName());
                } else {
                    lastKnockbackTime = null;

                    if (timeSinceKnockback <= knockbackTimeout && deltaV4s >= 0) {
                        if (antiKnockbackCount > 0) {
                            antiKnockbackCount--;
                            debug("Knockback check passed -1: %d; %s; %d ms delay; V: %.6f", antiKnockbackCount, player.getName(), timeSinceKnockback, deltaV);
                        } else {
                            debug("Received knockback: %d; %s; %d ms delay; V: %.6f", antiKnockbackCount, player.getName(), timeSinceKnockback, deltaV);
                        }
                    } else {
                        final int diff = 5;
                        debug("Anti-knockback +%d: %d; %s; %d ms delay; V: %.6f", diff, antiKnockbackCount + diff, player.getName(), timeSinceKnockback, deltaV);
                        onAntiKnockback(diff, 1);
                    }
                }
            }

            if (deltaH == 0.0 && deltaV == 0.0) {
                // Otherwise evasion is possible by rotating head often
                return;
            }

            boolean jumping = false;
            boolean falling = false;

            Location location = player.getLocation();
            World world = location.getWorld();
            long climbableTimeDelta = now - lastClimbableTime;
            boolean climbing = false;
            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();
            for (int ix = x - 1; ix <= x + 1; ix++) {
                for (int iz = z - 1; iz <= z + 1; iz++) {
                    for (int iy = y; iy <= y + 1; iy++) {
                        if (isClimbable(world.getBlockAt(ix, iy, iz).getType())) {
                            climbing = true;
                            break;
                        }
                    }
                }
            }
            if (climbing) {
                inAir = false;
                resetFlight();
                lastClimbableTime = now;
                debug("On climbable: %s", player.getName());
            } else if (climbableTimeDelta <= 800) {
                inAir = false;
                resetFlight();
                debug("Recently on climbable: %s", player.getName());
            }

            if (inAir) {
                if (firstInAir == null) {
                    firstInAir = now;
                    inAirScore = 1;
                    lastAirDeltaV = deltaV;
                    lastAirAccelV = null;
                    lastAirAccelGoingUp = null;
                    debug("Entered airspace: %s", player.getName());
                } else {
                    final int MAX_FALLING_COUNT = 4;
                    final int NORMAL_JUMPING_COUNT = 10;  // Often only 9

                    double accelV = deltaV - lastAirDeltaV;
                    int accelV4s = (int) (accelV * 10000);
                    @SuppressWarnings("unused")
                    int accelV4 = Math.abs(accelV4s);
                    long timeInAir = now - firstInAir;

                    if (lastAirAccelV != null) {
                        boolean airAccelGoingUp = accelV > lastAirAccelV;
                        if (lastAirAccelGoingUp != null && airAccelGoingUp != lastAirAccelGoingUp) {
                            if (jumpingCount > 1) {
                                debug("Ignoring expected vertical jerk from jump: %s", player.getName());
                            } else if (preLastKnockbackTime != null && now - preLastKnockbackTime < 600) {
                                debug("Ignoring expected vertical jerk from knockback: %s", player.getName());
                            } else {
                                if (inAirScore > 8) {
                                    inAirScore += 4;
                                    info("In-air +4 vertical jerk: %d; %d ms, %s", inAirScore, timeInAir, player.getName());
                                } else {
                                    debug("Ignoring in-air vertical jerk: %d; %d ms, %s", inAirScore, timeInAir, player.getName());
                                }
                            }
                        }
                        lastAirAccelGoingUp = airAccelGoingUp;
                    }

                    if (deltaV == 0) {
                        if (accelV == 0.0 && lastAirAccelV != null && lastAirAccelV == 0.0) {
                            inAirScore += 8;
                            info("In air, no vertical velocity +8: %d; %d ms; %s", inAirScore, timeInAir, player.getName());
                        }
                    } else if (accelV4 == 4020) {
                        if (inAirScore > 1) {
                            inAirScore += 8;
                            info("Vertical accel Metro +8: %d; %d ms; %s", inAirScore, timeInAir, player.getName());
                        } else {
                            inAirScore++;
                            info("Vertical accel Metro +1: %d; %d ms; %s", inAirScore, timeInAir, player.getName());
                        }
                    } else if (accelV4 == 3332) {
                        if (inAirScore > 0) {
                            //inAirScore += 3;
                            debug("Vertical accel Huzuni (disabled): %d; %d ms; %s", inAirScore, timeInAir, player.getName());
                        } else {
                            debug("Ignoring vertical accel Huzuni: %d; %d ms; %s", inAirScore, timeInAir, player.getName());
                        }
                    } else if (accelV4s > 0 && lastAirAccelV != null && lastAirAccelV > 0.0) {
                        if (preLastKnockbackTime != null && now - preLastKnockbackTime < 600) {
                            debug("Ignoring expected upwards accel from knockback: %s", player.getName());
                        } else if (inAirScore > 4) {
                            inAirScore += 2;
                            info("Upwards accel (unnatural) +2: %d; %d ms; %s", inAirScore, timeInAir, player.getName());
                        } else {
                            debug("Ignoring upwards accel: %d; %d ms; %s", inAirScore, timeInAir, player.getName());
                        }
                    } else if (jumpingCount <= 0 && (accelV4s == -752 || accelV4s == -1490 || accelV4s == -2213)) {
                        falling = true;
                        fallingCount++;

                        if (fallingCount <= 1 && JUMP_ACCELS.contains(accelV4s)) {
                            // Collision for -752, at minimum.
                            jumping = true;
                            jumpingCount++;
                            debug("Jumping %d/%d+ or falling %d/%d: %s; %.6f blocks/sec^2", jumpingCount, NORMAL_JUMPING_COUNT, fallingCount, MAX_FALLING_COUNT, player.getName(), accelV);
                        } else {
                            // Occasionally one is repeated, so > 4
                            debug("Falling%s %d/%d: %s; %.6f blocks/sec^2", fallingCount > MAX_FALLING_COUNT ? " (suspicious)" : "", fallingCount, MAX_FALLING_COUNT, player.getName(), accelV);
                            // TODO: As hack clients get smarter, we'll likely need to kick when fallingCount > some number
                        }
                    } else if (JUMP_ACCELS.contains(accelV4s)) {
                        jumping = true;
                        jumpingCount++;
                        // Normally not all elements are used, so there's a bit of padding here
                        debug("Jumping%s %d/%d+: %s; %.6f blocks/sec^2", jumpingCount > NORMAL_JUMPING_COUNT ? " (extra-high)" : "", jumpingCount, NORMAL_JUMPING_COUNT, player.getName(), accelV);
                        // TODO: As hack clients get smarter, we'll likely need to kick when jumpingCount > some number
                    } else if (deltaV4s < 0) {
                        if (inAirScore > 0) {
                            inAirScore--;
                            if (inAirScore > 1 || obviousFlyHacks > 1 || suspiciousFlyHacks > 1) {
                                debug("Falling -1: %d; %d ms, %.6f blocks/sec; %.6f blocks/sec^2; %s", inAirScore, timeInAir, deltaV, accelV, player.getName());
                            } else {
                                debug("Falling -1: %d; %d ms, %.6f blocks/sec; %.6f blocks/sec^2; %s", inAirScore, timeInAir, deltaV, accelV, player.getName());
                            }
                        } else {
                            debug("Falling: %d ms, %.6f blocks/sec; %.6f blocks/sec^2; %s", timeInAir, deltaV, accelV, player.getName());
                        }
                    } else if (deltaV4s > 0 && accelV4s > 0 && lastAirAccelV != null && lastAirAccelV > 0.0) {
                        fallingCount++;
                        falling = true;
                        if (jumpingCount > 1) {
                            jumpingCount++;
                            jumping = true;
                        }

                        inAirScore += 8;
                        info("Falling but slowing down +8: %d; %d ms, %.6f blocks/sec; %.6f blocks/sec^2; %s", inAirScore, timeInAir, deltaV, accelV, player.getName());
                    } else if (deltaV4s > 0) {
                        debug("Rising: %d; %d ms; %.6f blocks/sec; %.6f blocks/sec^2; %s", inAirScore, timeInAir, deltaV, accelV, player.getName());
                    }

                    if (timeInAir >= 600 && inAirScore >= 24) {  // TODO: Use packet count as well as time; helps prevent lag
                        // TODO: Enderpearls might break this
                        onCaughtCheating(player, "flight");
                    } else if (timeInAir > 1200 && deltaV4s >= 0 && accelV4s >= 0 && lastAirAccelV != null && lastAirAccelV >= 0) {
                        info("In air too long: %s; %d ms", player.getName(), timeInAir);
                        onCaughtCheating(player, "flight/floating");
                    } else {
                        lastAirAccelV = accelV;
                    }
                }
            } else if (firstInAir != null) {
                debug("Touched ground: %s", player.getName());
                resetFlight();
            }

            if (!falling && fallingCount != 0) {
                fallingCount = 0;
            }
            if (!jumping && jumpingCount != 0) {
                jumpingCount = 0;
            }

            if (deltaH == 0 && (deltaV == 1 || deltaV == -1)) {  // Wurst
                obviousFlyHacks += 4;
                info("Obvious flight hack (Wurst flight) for %s +4: %d", player.getName(), obviousFlyHacks);
            } else if (deltaV4 == 3750) {  // Metro
                obviousFlyHacks += 4;
                info("Obvious flight hack (Metro flight) for %s +4: %d; %.6f", player.getName(), obviousFlyHacks, deltaV);
            } else if (deltaV4 == 3749) {  // Huzuni
                obviousFlyHacks += 4;
                info("Obvious flight hack (Huzuni flight) for %s +4: %d; %.6f", player.getName(), obviousFlyHacks, deltaV);
            } else if (deltaH4 == 9183 || deltaH4 == 10014) {  // Wurst speed hacks
                obviousFlyHacks += 2;
                info("Obvious speed hack (Wurst speed) for %s +1: %d; %.6f", player.getName(), obviousFlyHacks, deltaH);
            } else if (deltaV4 > 5000 || (deltaH4 > 6000 && deltaV4 != 4199 && deltaV4 != 4200)) {  // 0.42 is jump while sprinting
                if (obviousFlyHacks > 0) {
                    info("Suspicious movement amount; not decrementing fly hacks: %s; H: %.6f; V: %.6f", player.getName(), deltaH, deltaV);
                }
            } else if (obviousFlyHacks > 0) {
                obviousFlyHacks--;
                debug("Decremented obvious fly hack movements for %s -1: %d", player.getName(), obviousFlyHacks);
            }

            if (deltaH4 == 9800) {  // Wurst
                suspiciousFlyHacks += 160;
                info("Suspicious flight (Wurst flight) for %s +160: %d; %.6f", player.getName(), suspiciousFlyHacks, deltaV);
            } else if (deltaV4 == 10100) {  // Not sure which client this is
                suspiciousFlyHacks += 240;
                info("Hack client signature upward movement for %s +240: %d; %.6f", player.getName(), suspiciousFlyHacks, deltaV);
            } else if (deltaV4s > 11200) {
                // 1.1200 and 1.0192 seem to be PvP-related; knockback, maybe?  Sometimes 1.1084 instead of 1.1200.
                //suspiciousFlyHacks += 160;
                info("Ignoring moderately fast vertical movement for %s: %d; %.6f", player.getName(), suspiciousFlyHacks, deltaV);
            } else if (deltaH4 >= 30000) {
                suspiciousFlyHacks += 160;
                info("Very fast horizontal movement for %s +160: %d; %.6f", player.getName(), suspiciousFlyHacks, deltaH);
            } else if (deltaH4 >= 12000) {
                //suspiciousFlyHacks += 60;
                info("Moderately fast horizontal movement for %s +60: %d; %.6f", player.getName(), suspiciousFlyHacks, deltaH);
            } else if (deltaH4 >= 9800) {  // Wurst
                if (deltaV4 == 0) {
                    suspiciousFlyHacks += 20;
                    info("Slightly fast and suspicious horizontal movement for %s +20: %d; %.6f", player.getName(), suspiciousFlyHacks, deltaH);
                } else {
                    //suspiciousFlyHacks += 20;
                    debug("Ignoring slightly fast horizontal movement for %s: %d; %.6f", player.getName(), suspiciousFlyHacks, deltaH);
                }
            } else if (suspiciousFlyHacks > 0 && obviousFlyHacks <= 0) {
                suspiciousFlyHacks -= 4;
                debug("Decremented suspicious movements for %s -4: %d", player.getName(), suspiciousFlyHacks);
            }

            if (obviousFlyHacks >= 12) {
                onCaughtCheating(player, "fly hacks");
            }
            if (suspiciousFlyHacks >= 640) {
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

        private void markGotHit(Entity entity) {
            resetAttack();
            onKnockback(entity);
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

        public void resetAttackSpeed() {
            suspiciousHits = 0;
            misses = 0;
            highSpeedAttacks = 0;
        }

        public void resetAttack() {
            resetAttackSpeed();

            missDistance = null;
            suspiciousAims = 0;
            farHits = 0;
            hitDistance = null;
        }

        public void resetFlight() {
            firstInAir = null;
            inAirScore = 0;
            lastAirDeltaV = 0;
            lastAirAccelV = null;
            lastAirAccelGoingUp = null;
            fallingCount = 0;
            jumpingCount = 0;
            lastClimbableTime = 0;
        }

        public void resetFlightHistory() {
            resetFlight();

            suspiciousFlyHacks = 0;
            obviousFlyHacks = 0;
        }

        public void resetKnockback() {
            antiKnockbackCount = 0;
            lastKnockbackTime = null;
        }

        public void reset() {
            resetAttack();
            resetFlightHistory();
            resetKnockback();
        }

        public void onAttackSpeedTainted() {
            resetAttackSpeed();
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
                    resetAttack();
                } else if (deltaTime > 75) {
                    debug("Normal attack: %d ms", deltaTime);
                    resetAttackSpeed();
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
            Player player = getPlayerIfEnabled();
            if (player == null) {
                return;
            }

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
            }
            if (distance >= 4.2) {
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

        @SuppressWarnings("unused")
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
            Player player = getPlayer();
            if (player == null) {
                return;
            }

            if (blackmarks > 0) {
                blackmarks = Math.max(0, blackmarks - count);
                info("Forgiven %d blackmark(s): %d; %s", count, blackmarks, player.getName());
            }

            resetAttack();
        }

        public void onItemHeld() {
            lastItemHeldTime = System.currentTimeMillis();
        }
    }
}
