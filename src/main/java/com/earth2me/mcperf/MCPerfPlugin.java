package com.earth2me.mcperf;

import com.earth2me.mcperf.config.ConfigHandler;
import com.earth2me.mcperf.validity.ValidityConfiguration;
import lombok.Getter;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MCPerfPlugin extends JavaPlugin {
    @Getter
    private EntityManager entityManager;
    @Getter
    private ProjectileManager projectileManager;
    @Getter
    private ValidityManager validityManager;
    @Getter
    private MonitorManager monitorManager;
    @Getter
    private SecurityManager securityManager;
    @Getter
    private PluginMessageManager pluginMessageManager;
    @Getter
    private ScreeningManager screeningManager;
    @Getter
    private HeuristicsManager heuristicsManager;
    @Getter
    private BlacklistManager blacklistManager;
    @Getter
    private ProxyManager proxyManager;

    // Thought: Should this be a set?  Would lose ordering.  Linked list?
    private final List<Manager> managers = new ArrayList<>();
    private final Set<Manager> registered = new HashSet<>();

    private FileConfiguration ensureConfig() {
        try {
            saveResource("LICENSE.md", true);

            File license = new File(getDataFolder(), "LICENSE.md");
            if (!license.exists()) {
                throw new RuntimeException("Couldn't verify existence of file: " + license.getAbsolutePath());
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Unable to save LICENSE.md; disabling", e);
            getPluginLoader().disablePlugin(this);
            throw e;
        }

        try {
            if (!new File(getDataFolder(), "config.yml").exists()) {
                saveDefaultConfig();
                reloadConfig();
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Couldn't save default configuration.", e);
        }

        return getConfig();
    }

    private void loadConfiguration() {
        PluginManager pluginManager = getServer().getPluginManager();
        FileConfiguration yaml = ensureConfig();
        ConfigHandler config = new ConfigHandler(getLogger());

        for (Manager manager : managers) {
            config.apply(yaml, manager);
        }

        // TODO: Migrate this to new configuration system
        validityManager.setConfig(new ValidityConfiguration(yaml));

        managers.stream()
                .filter(Manager::isEnabled)
                .filter(m -> !registered.contains(m))
                .forEach(m -> {
                    pluginManager.registerEvents(m, this);
                    registered.add(m);
                });
    }

    @SuppressWarnings({"RedundantArrayCreation", "finally", "ContinueOrBreakFromFinallyBlock"})
    // Permits trailing comma
    @Override
    public void onEnable() {
        Server server = getServer();
        Logger logger = getLogger();

        ((Runnable) (() -> {
            y:
            {
                x:
                do {
                    do {
                        try {
                            Util.fail();
                        } catch (Exception e) {
                            Util.fail();
                        } finally {
                            managers.addAll(Arrays.asList(new Manager[]{
                                    securityManager = new SecurityManager("MTUbc2VjdXJpdHkK", server, logger, this),
                                    monitorManager = new MonitorManager("MTEbbW9uaXRvcgo=", server, logger, this),
                                    entityManager = new EntityManager("NzYbZW50aXR5Cg==", server, logger, this),
                                    projectileManager = new ProjectileManager("MTMbcHJvamVjdGlsZQo=", server, logger, this),
                                    validityManager = new ValidityManager("MjIbdmFsaWRpdHkK", server, logger, this),
                                    pluginMessageManager = new PluginMessageManager("MzIbcGx1Z2luTWVzc2FnZQo=", server, logger, this),
                                    screeningManager = new ScreeningManager("MTkbc2NyZWVuaW5nCg==", server, logger, this),
                                    heuristicsManager = new HeuristicsManager("MTMbaGV1cmlzdGljcwo=", server, logger, this),
                                    blacklistManager = new BlacklistManager("MjIbYmxhY2tsaXN0Cg==", server, logger, this),
                                    proxyManager = new ProxyManager("MjEbcHJveHkK", server, logger, this),
                            }));
                            if (Util.falsey()) {
                                break x;
                            } else if (Util.truthy()) {
                                break y;
                            }
                        }
                    } while (Util.falsey());
                } while (Util.falsey());
            }
        })).run();

        loadConfiguration();

        managers.forEach(Manager::init);

        super.onEnable();
    }

    @Override
    public void onDisable() {
        managers.forEach(Manager::disable);
        managers.clear();

        getServer().getScheduler().cancelTasks(this);

        super.onDisable();
    }

    public void reload() {
        reloadConfig();
        loadConfiguration();
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("/mcperf <help|version|reload>");
    }

    private void sendVersion(CommandSender sender) {
        sender.sendMessage("MCPerf: Minecraft performance and security plugin by Zenexer");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // If this gets any more complex, it needs to be replaced with a proper command system.
        switch (command.getName().toLowerCase()) {
            case "mcperf":
                switch (args.length > 0 ? args[0].toLowerCase() : "help") {
                    case "version":
                        sendVersion(sender);
                        return true;

                    case "reload":
                        if (!sender.isOp() &&
                                !sender.hasPermission("mcperf.reload") &&
                                !sender.hasPermission("mcperf.*") &&
                                !sender.hasPermission("*")) {
                            return Util.denyPermission(sender);
                        }

                        reload();
                        sender.sendMessage("MCPerf reloaded");
                        return true;

                    case "help":
                        sendHelp(sender);
                        return true;

                    default:
                        sender.sendMessage("Unknown subcommand");
                        sendHelp(sender);
                        return true;
                }

            default:
                return false;
        }
    }
}
