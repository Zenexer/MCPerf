package com.earth2me.mcperf;

import com.earth2me.mcperf.config.ConfigHandler;
import com.earth2me.mcperf.managers.Manager;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MCPerfPlugin extends JavaPlugin {
    private static ServiceLoader<Manager> loader;
    private static ClassLoader loaderContext;

    private final List<Manager> managers = new LinkedList<>();
    private final Set<Manager> registered = new HashSet<>();

    private void initLoader() {


        initLoader(getClassLoader());
    }

    private static synchronized void initLoader(ClassLoader classLoader) {
        if (loader == null) {
            loaderContext = classLoader;
            loader = ServiceLoader.load(Manager.class, classLoader);
        }
    }

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
        Server server = getServer();
        Logger logger = getLogger();

        initLoader();

        managers.clear();
        for (Manager manager : loader) {
            manager.initService(server, logger, this);
            managers.add(manager);
        }

        if (managers.isEmpty()) {
            logger.log(Level.SEVERE, "No managers were found!");

            String path = "META-INF/services/" + Manager.class.getName();
            logger.log(Level.SEVERE, "Searched path: " + path);

            try {
                Enumeration<URL> urls = loaderContext.getResources(path);

                for (URL url; urls.hasMoreElements(); ) {
                    url = urls.nextElement();
                    logger.log(Level.SEVERE, "Resource URL: " + Objects.toString(url));
                }
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Exception searching path", e);
            }
        }

        /*managers.addAll(Arrays.asList(new Manager[]{
                new SecurityManager(, server, logger, this),
                new MonitorManager(, server, logger, this),
                new EntityManager(, server, logger, this),
                new ProjectileManager(, server, logger, this),
                new ValidityManager(, server, logger, this),
                new PluginMessageManager(, server, logger, this),
                new ScreeningManager(, server, logger, this),
                new HeuristicsManager(, server, logger, this),
                new BlacklistManager(, server, logger, this),
                new ProxyManager(, server, logger, this),
        }));*/

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
        PluginDescriptionFile desc = getDescription();
        sender.sendMessage(String.format("%s v%s: %s by %s", desc.getName(), desc.getVersion(), desc.getDescription(), String.join(", ", desc.getAuthors())));
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
