package com.earth2me.mcperf.managers.security;

import com.earth2me.mcperf.Util;
import com.earth2me.mcperf.config.ConfigSetting;
import com.earth2me.mcperf.managers.Manager;
import com.earth2me.mcperf.annotation.ContainsConfig;
import com.earth2me.mcperf.annotation.Service;
import lombok.Getter;
import lombok.Setter;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;

import java.util.Base64;
import java.util.ConcurrentModificationException;
import java.util.Random;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.stream.Stream;

@Service
@ContainsConfig
public final class ScreeningManager extends Manager {
    // Don't use SecureRandom; no reason to waste entropy on this.
    private static final Random random = new Random();

    private final WeakHashMap<Player, Info> info = new WeakHashMap<>();

    @Getter
    @Setter
    @ConfigSetting
    private long gracePeriod = 20;

    public ScreeningManager() {
        super("MTkbc2NyZWVuaW5nCg==");
    }

    @Override
    public void onInit() {
        getServer().getPluginCommand("screen").setExecutor(this::onCommand);
    }

    @Override
    public synchronized void onDeinit() {
        // This could potentially become a threading issue due to GC/async events; not much
        // we can do about it, though.
        try {
            info.values().forEach(Info::clear);
        } catch (ConcurrentModificationException e) {
            // Yeah... that happened.  Edge case, one or two people get kicked.
            getLogger().log(Level.WARNING, "Couldn't clear all kill tasks due to concurrency issue: " + e.getMessage(), e);
        }
        // Don't clear it in case users respond to tests; we want to block those messages.
    }

    private Info getInfo(Player player) {
        return getInfo(player, true);
    }

    private Info getInfo(Player player, boolean createIfMissing) {
        Info i = info.get(player);
        if (i == null && createIfMissing) {
            info.put(player, i = new Info());
        }
        return i;
    }

    private void fail(Player player, boolean caught) {
        getServer().getScheduler().callSyncMethod(getPlugin(), () -> {
            getInfo(player).clear();

            if (caught) {
                player.kickPlayer("We don't allow hack clients.");
                Util.sendAlert(getServer(), "Caught %s with a hack client.", player.getName());
            } else {
                player.kickPlayer("You failed to respond to the captcha in time.");
            }

            return null;
        });
    }

    private void pass(Player player) {
        getInfo(player).setChecked(true);
        player.sendMessage("Thank you.");
    }

    private <T extends Event & Cancellable> void screen(Entity entity, T event) {
        if (entity instanceof Player) {
            screen((Player) entity, event);
        }
    }

    private <T extends Event & Cancellable> void screen(final Player player, T event) {
        if (!isEnabled()) {
            return;
        }

        try {
            final Info i = getInfo(player);
            if (i.isChecked()) {
                return;
            }

            long time = player.getWorld().getFullTime();
            long last = i.getLastMessageTime();
            if (time < last || time - 20 >= last) {
                i.setLastMessageTime(time);
                getServer().getScheduler().scheduleSyncDelayedTask(getPlugin(), () -> {
                    if (!i.isChecked()) {
                        for (int n = 0; n < 5; n++) {
                            player.spigot().sendMessage(i.getMessage());
                        }
                    }
                }, 2);
                player.spigot().sendMessage(i.getMessage());
            }

            if (event != null) {
                event.setCancelled(true);
            }

            if (gracePeriod > 0 && i.getKillTask() < 0) {
                i.setKillTask(getServer().getScheduler().scheduleSyncDelayedTask(getPlugin(), () -> {
                    if (!player.isOnline()) {
                        return;
                    }

                    if (getInfo(player, false) != i) {
                        getLogger().log(Level.WARNING, "Duplicate or missing screening info for player: " + player.getName());
                        return;
                    }

                    if (!i.isChecked()) {
                        fail(player, false);
                    }
                }, gracePeriod * 20));
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Exception while screening player: " + e.getMessage(), e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!isEnabled()) {
            return;
        }

        try {
            Player player = event.getPlayer();

            Info i = new Info();
            i.setChecked(false);
            info.put(player, i);

            screen(player, null);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Screening exception on player join: " + e.getMessage(), e);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncPlayerChatEvent(AsyncPlayerChatEvent event) {
        try {
            final Player player = event.getPlayer();
            String message = event.getMessage();
            Info i = isEnabled() ? getInfo(player) : getInfo(player, false);

            if (i == null || message == null) {
                return;
            }

            if (!isEnabled() || i.isChecked()) {
                if (message.equals(i.getTokenWithPrefix())) {
                    cancelEvent(event);
                }
                return;
            }

            cancelEvent(event);

            if (message.equals(i.getToken())) {  // Test this first in case of bots/etc.
                // Hack client.
                fail(player, true);
            } else if (message.equals(i.getTokenWithPrefix())) {
                pass(player);
            } else {
                screen(player, null);
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Exception while processing player chat event for screening: " + e.getMessage(), e);
        }
    }

    private static void cancelEvent(AsyncPlayerChatEvent event) {
        event.setCancelled(true);
        event.setMessage("");
        event.setFormat("");
        event.getRecipients().clear();
    }

    private boolean onCommand(final CommandSender sender, Command command, String label, String[] args) {
        if (!isEnabled()) {
            sender.sendMessage("This feature is currently disabled.");
            return true;
        }

        boolean hasAllPermissions = sender.isOp() || sender.hasPermission("*") || sender.hasPermission("mcperf.*") || sender.hasPermission("mcperf.screen.*");

        if (!hasAllPermissions && !sender.hasPermission("mcperf.screen")) {
            return Util.denyPermission(sender);
        }

        if (args.length < 1) {
            return false;
        }

        if (args.length > 1 && !hasAllPermissions && !sender.hasPermission("mcperf.screen.multiple")) {
            return Util.denyPermission(sender);
        }

        Stream<Player> players;
        // TODO: We can probably extract this bit, but there are a lot of local variables.
        //noinspection Duplicates
        if (args.length == 1 && "*".equals(args[0])) {
            if (!hasAllPermissions && !sender.hasPermission("mcperf.screen.all")) {
                return Util.denyPermission(sender);
            }

            players = getServer().getOnlinePlayers().stream().map(p -> p);  // Gets around buggy generics
        } else {
            players = Stream.of(args)
                    .map(getServer()::getPlayer)
                    .filter(java.util.Objects::nonNull);
        }

        players.forEach(info::remove);
        sender.sendMessage("Screening started.");

        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerLeave(PlayerQuitEvent event) {
        onPlayerLeave(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerLeave(PlayerKickEvent event) {
        onPlayerLeave(event.getPlayer());
    }

    private void onPlayerLeave(Player player) {
        Info i = info.remove(player.getPlayer());

        if (i != null) {
            i.clear();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onScreen(PlayerCommandPreprocessEvent event) {
        screen(event.getPlayer(), event);

        if (event.isCancelled()) {
            event.setMessage("/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxx:xxxxxxxxxxxxxxxxxxxxxxxxxx");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onScreen(PlayerMoveEvent event) {
        if (!event.getFrom().toVector().toBlockVector().equals(event.getTo().toVector().toBlockVector())) {
            screen(event.getPlayer(), event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onScreen(PlayerInteractEvent event) {
        screen(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onScreen(CraftItemEvent event) {
        screen(event.getWhoClicked(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onScreen(PlayerDropItemEvent event) {
        screen(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onScreen(PlayerBucketFillEvent event) {
        screen(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onScreen(PlayerBucketEmptyEvent event) {
        screen(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onScreen(PlayerBedEnterEvent event) {
        screen(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onScreen(PlayerPortalEvent event) {
        screen(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onScreen(PlayerEditBookEvent event) {
        screen(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onScreen(PlayerItemConsumeEvent event) {
        screen(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onScreen(PlayerItemDamageEvent event) {
        screen(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onScreen(PlayerItemHeldEvent event) {
        screen(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onScreen(PlayerPickupItemEvent event) {
        screen(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onScreen(PlayerLeashEntityEvent event) {
        screen(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onScreen(PlayerShearEntityEvent event) {
        screen(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onScreen(PlayerTeleportEvent event) {
        screen(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onScreen(PlayerUnleashEntityEvent event) {
        screen(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onScreen(PlayerInteractAtEntityEvent event) {
        screen(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onScreen(PlayerInteractEntityEvent event) {
        screen(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onScreen(PlayerAnimationEvent event) {
        screen(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onScreen(PlayerToggleFlightEvent event) {
        screen(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onScreen(PlayerToggleSneakEvent event) {
        screen(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onScreen(PlayerToggleSprintEvent event) {
        screen(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onScreen(PlayerArmorStandManipulateEvent event) {
        screen(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onScreen(PlayerFishEvent event) {
        screen(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onScreen(VehicleEnterEvent event) {
        screen(event.getEntered(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onScreen(VehicleExitEvent event) {
        screen(event.getExited(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onScreen(EntityInteractEvent event) {
        screen(event.getEntity(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onScreen(EntityDamageByEntityEvent event) {
        screen(event.getEntity(), event);
        screen(event.getDamager(), event);
    }


    private class Info {
        private AtomicBoolean checked = new AtomicBoolean(true);
        @Getter
        private volatile String token;
        @Getter
        private volatile String tokenWithPrefix;
        @Getter
        private volatile BaseComponent[] message;
        @Getter
        @Setter
        private volatile long lastMessageTime;
        @Getter
        @Setter
        private volatile int killTask = -1;

        public Info() {
            generateToken();
        }

        private void generateToken() {
            byte[] nonsense = new byte[18];  // 24 chars
            random.nextBytes(nonsense);
            token = ".x ignore this " + Base64.getEncoder().encodeToString(nonsense).toLowerCase();
            tokenWithPrefix = ".say " + token;
            lastMessageTime = Long.MAX_VALUE;

            message = new ComponentBuilder("You need to ")
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, tokenWithPrefix))
                    .append("CLICK HERE")
                    .retain(ComponentBuilder.FormatRetention.EVENTS)
                    .underlined(true)
                    .color(ChatColor.LIGHT_PURPLE)
                    .append(" before you can play.")
                    .retain(ComponentBuilder.FormatRetention.EVENTS)
                    .create();
        }

        private void clear() {
            if (killTask >= 0) {
                try {
                    getServer().getScheduler().cancelTask(killTask);
                } catch (Exception e) {
                    getLogger().log(Level.WARNING, "Exception while canceling kill task: " + e.getMessage(), e);
                }
            }

            // Memory cleanup
            token = null;
            //tokenWithPrefix = null;  // We save this so we can filter duplicate clicks.
            message = null;
            killTask = -1;
            lastMessageTime = Long.MAX_VALUE;
        }

        public void setChecked(boolean value) {
            checked.set(value);

            if (value) {
                clear();
            } else {
                generateToken();
            }
        }

        public boolean isChecked() {
            return checked.get();
        }
    }
}
