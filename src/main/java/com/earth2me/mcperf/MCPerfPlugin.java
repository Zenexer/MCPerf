package com.earth2me.mcperf;

import com.earth2me.mcperf.config.ConfigHandler;
import com.earth2me.mcperf.validity.ValidityConfiguration;
import lombok.Getter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

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

    // Thought: Should this be a set?  Would lose ordering.  Linked list?
    private final List<Manager> managers = new ArrayList<>();
    private final Set<Manager> registered = new HashSet<>();

    private FileConfiguration ensureConfig() {
        try {
            if (!new File("config.yml").exists()) {
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

    @SuppressWarnings("RedundantArrayCreation")  // Permits trailing comma
    @Override
    public void onEnable() {
        managers.addAll(Arrays.asList(new Manager[]{
                securityManager = new SecurityManager(getServer(), getLogger(), this),
                monitorManager = new MonitorManager(getServer(), getLogger(), this),
                entityManager = new EntityManager(getServer(), getLogger(), this),
                projectileManager = new ProjectileManager(getServer(), getLogger(), this),
                validityManager = new ValidityManager(getServer(), getLogger(), this),
                pluginMessageManager = new PluginMessageManager(getServer(), getLogger(), this),
                screeningManager = new ScreeningManager(getServer(), getLogger(), this),
                heuristicsManager = new HeuristicsManager(getServer(), getLogger(), this),
                blacklistManager = new BlacklistManager(getServer(), getLogger(), this),
        }));
        pluginMessageManager.register();

        // Listeners must already be instantiated.
        loadConfiguration();

        super.onEnable();
    }

    @Override
    public void onDisable() {
        managers.clear();

        if (pluginMessageManager != null) {
            pluginMessageManager.unregister();
        }

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
                        if (!sender.isOp() && !sender.hasPermission("mcperf.reload") && !sender.hasPermission("mcperf.*")) {
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
