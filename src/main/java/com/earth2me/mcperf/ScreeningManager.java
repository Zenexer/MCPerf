package com.earth2me.mcperf;

import lombok.Getter;
import org.bukkit.Server;
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
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;

import java.util.Base64;
import java.util.Random;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public final class ScreeningManager extends Manager {
    private final WeakHashMap<Player, Info> info = new WeakHashMap<>();

    public ScreeningManager(Server server, Logger logger, MCPerfPlugin plugin) {
        super(server, logger, plugin, false);

        getServer().getPluginCommand("screen").setExecutor(this::onCommand);
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

    private void fail(Player player) {
        player.kickPlayer("We don't allow hack clients.");
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

    private <T extends Event & Cancellable> void screen(Player player, T event) {
        if (!isEnabled()) {
            return;
        }

        try {
            Info i = getInfo(player);
            if (i.isChecked()) {
                return;
            }

            player.sendRawMessage(i.getTestJson());

            if (event != null) {
                event.setCancelled(true);
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
            info.put(player, new Info());
            screen(player, null);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "[MCPerf] Screening exception on player join: " + e.getMessage(), e);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChatEvent(@SuppressWarnings("deprecation") PlayerChatEvent event) {
        if (!isEnabled()) {
            return;
        }

        try {
            final Player player = event.getPlayer();
            Info i = getInfo(player);

            if (!i.isChecked()) {
                event.setCancelled(true);

                String message = event.getMessage();
                if (message.equals(i.getToken())) {  // Test this first in case of bots/etc.
                    // Hack client.
                    fail(player);
                } else if (message.equals(i.getTokenWithPrefix())) {
                    pass(player);
                } else {
                    screen(player, null);
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "[MCPerf] Exception while processing player chat event for screening: " + e.getMessage(), e);
        }
    }

    private boolean onCommand(final CommandSender sender, Command command, String label, String[] args) {
        if (!isEnabled()) {
            sender.sendMessage("This feature is currently disabled.");
            return true;
        }

        boolean hasAllPermissions = sender.isOp() || sender.hasPermission("mcperf.screen.*");

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
    public void onPlayerQuit(PlayerQuitEvent event) {
        info.remove(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKick(PlayerKickEvent event) {
        info.remove(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onScreen(PlayerCommandPreprocessEvent event) {
        screen(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onScreen(PlayerMoveEvent event) {
        screen(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onScreen(PlayerInteractEvent event) {
        screen(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onScreen(PlayerDropItemEvent event) {
        screen(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onScreen(PlayerBucketEvent event) {
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
    public void onScreen(PlayerTeleportEvent event) {
        screen(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onScreen(PlayerVelocityEvent event) {
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

    private static class Info {
        // Don't use SecureRandom; no reason to waste entropy on this.
        private static final Random random = new Random();

        @Getter
        private boolean checked = false;
        @Getter
        private String token;
        @Getter
        private String tokenWithPrefix;
        @Getter
        private String testJson;

        public Info() {
            generateToken();
        }

        private void generateToken() {
            byte[] nonsense = new byte[18];  // 24 chars
            random.nextBytes(nonsense);
            token = ".x Ignore this. " + Base64.getEncoder().encodeToString(nonsense);
            tokenWithPrefix = ".say " + token;
            testJson = "{\"text\":\"You need to CLICK HERE before you can play.\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"" + tokenWithPrefix + "\"}}";
        }

        private void clearToken() {
            // Memory cleanup
            token = null;
            tokenWithPrefix = null;
            testJson = null;
        }

        public void setChecked(boolean value) {
            checked = value;

            if (checked) {
                clearToken();
            } else {
                generateToken();
            }
        }
    }
}
