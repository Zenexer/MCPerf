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

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;

public class MCPerfPlugin extends JavaPlugin {
    private final List<Manager> managers = new LinkedList<>();
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

        managers.stream().forEach(Manager::disable);

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

        managers.clear();

        Iterator<Manager> iterator = ServiceLoader.load(Manager.class, getClass().getClassLoader()).iterator();
        boolean errors = false;
        for (; ; ) {
            try {
                if (!iterator.hasNext()) {
                    break;
                }

                Manager manager = iterator.next();
                manager.initService(server, logger, this);
                managers.add(manager);
            } catch (ServiceConfigurationError e) {
                errors = true;
                getLogger().warning(String.format("Error while loading managers: %s", e.getMessage()));
            }
        }

        if (managers.isEmpty()) {
            errors = true;
            logger.log(Level.SEVERE, "No managers were found!");
        }

        if (errors) {
            logger.log(Level.SEVERE, "Errors were encountered while loading managers.  Running troubleshooter.");
            troubleshootLoader();
        }

        loadConfiguration();

        managers.forEach(Manager::init);

        super.onEnable();
    }

    private void troubleshootLoader() {
        Logger logger = getLogger();
        ClassLoader loaderContext = getClass().getClassLoader();

        String path = "META-INF/services/" + Manager.class.getName();
        logger.log(Level.SEVERE, "Class loader: " + loaderContext.getClass().getName());
        logger.log(Level.SEVERE, "Searched path: " + path);

        try {
            Enumeration<URL> urls = loaderContext.getResources(path);

            for (URL url; urls.hasMoreElements(); ) {
                url = urls.nextElement();
                logger.log(Level.SEVERE, "Resource URL: " + Objects.toString(url));

                try (
                        InputStream in = url.openStream();
                        BufferedReader rx = new BufferedReader(new InputStreamReader(in, "utf-8"))
                ) {
                    for (String line; (line = rx.readLine()) != null; ) {
                        logger.log(Level.SEVERE, "Service: " + line);
                    }
                }
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Exception searching path", e);
        }
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

    public final class IntIterator implements Iterator<Integer> {
        private final int[] arr;
        private int i = 0;

        public IntIterator(int[] arr) {
            this.arr = arr;
        }

        @Override
        public boolean hasNext() {
            return i < arr.length;
        }

        @Override
        public Integer next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            return arr[i++];
        }
    }
}
