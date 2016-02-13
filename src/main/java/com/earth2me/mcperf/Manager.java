package com.earth2me.mcperf;

import com.earth2me.mcperf.config.ConfigSetting;
import com.earth2me.mcperf.config.ConfigSettingSetter;
import com.earth2me.mcperf.config.Configurable;
import com.earth2me.mcperf.ob.ContainsConfig;
import lombok.Getter;
import org.bukkit.Server;
import org.bukkit.event.Listener;

import java.util.Base64;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

@ContainsConfig
public abstract class Manager implements Listener, Configurable {
    @Getter
    @ConfigSetting
    private boolean enabled;
    @Getter
    private final Logger logger;
    @Getter
    private final Server server;
    @Getter
    private final MCPerfPlugin plugin;
    @Getter
    private final PluginCommandSender commandSender;
    private String configPathCache;
    private boolean initializedOnce = false;
    private final String id;

    public Manager(String id, Server server, Logger logger, MCPerfPlugin plugin) {
        this(id, server, logger, plugin, true);
    }

    public Manager(String id, Server server, Logger logger, MCPerfPlugin plugin, boolean enabled) {
        this.id = decodeId(id);
        this.server = server;
        this.logger = logger;
        this.plugin = plugin;
        this.enabled = enabled;
        this.commandSender = new PluginCommandSender(server, id);
    }

    private static String decodeId(String encodedId) {
        byte[] bytes = Base64.getDecoder().decode(encodedId);
        return new String(bytes, 3, bytes.length - 4);
    }

    @Override
    public final String getId() {
        return id;
    }

    final void init() {
        initializedOnce = true;

        if (!enabled) {
            return;
        }

        getLogger().info("Initializing " + getId());
        onInit();
    }

    protected void onInit() {
    }

    private void deinit(boolean log) {
        if (!enabled || !initializedOnce) {
            return;
        }

        if (log) {
            getLogger().info("De-initializing " + getId());
        }

        onDeinit();
    }

    protected void onDeinit() {
    }

    @ConfigSettingSetter
    public final void setEnabled(boolean value) {
        if (value) {
            enable();
        } else {
            disable();
        }
    }

    // Keep in mind this won't be called initially if enabled = true is passed to constructor.
    public void enable() {
        if (enabled) {
            return;
        }

        getLogger().info("Enabling " + getId());
        enabled = true;

        if (initializedOnce) {
            init();
        }
    }

    public void disable() {
        if (!enabled) {
            return;
        }

        getLogger().info("Disabling " + getId());

        deinit(false);
        enabled = false;
    }

    public void dispatchCommand(String command) {
        getServer().dispatchCommand(getCommandSender(), command);
    }

    public void dispatchCommand(String format, Object... args) {
        dispatchCommand(String.format(format, args));
    }

    protected void println(String format, Object... args) {
        Util.println(getServer(), format, args);
    }

    protected void println(String message) {
        Util.println(getServer(), message);
    }

    protected void sendAlertAsync(String format, Object... args) {
        sendAlertAsync(String.format(format, args));
    }

    protected Future<Boolean> sendAlertAsync(String message) {
        return getServer().getScheduler().callSyncMethod(getPlugin(), () -> {
            Util.sendAlert(getServer(), message);
            return true;
        });
    }

    protected boolean sendAlert(String format, Object... args) {
        return sendAlert(String.format(format, args));
    }

    protected boolean sendAlert(String message) {
        Util.sendAlert(getServer(), message);
        return true;
    }

    @SuppressWarnings("unused")
    protected boolean sendAlertSync(String format, Object... args) {
        return sendAlertSync(String.format(format, args));
    }

    protected boolean sendAlertSync(String message) {
        if (getServer().isPrimaryThread()) {
            Util.sendAlert(getServer(), message);
            return true;
        } else {
            try {
                return sendAlertAsync(message).get();
            } catch (InterruptedException | ExecutionException e) {
                getLogger().log(Level.SEVERE, "Error sending async alert", e);
                return false;
            }
        }
    }

    protected boolean sendNotice(String message) {
        Util.sendAlert(getServer(), message);
        return true;
    }

    @SuppressWarnings("unused")
    protected boolean sendNotice(String format, Object... args) {
        Util.sendAlert(getServer(), format, args);
        return true;
    }

    @Override
    public String getConfigPath() {
        if (configPathCache == null) {
            String name = getId() + "Manager";  // TODO: Remove "Manager" suffix
            if (name.length() > 1) {
                configPathCache = Character.toLowerCase(name.charAt(0)) + name.substring(1);
            } else {
                assert !name.isEmpty();
                configPathCache = name.toLowerCase();
            }
        }

        return configPathCache;
    }
}
