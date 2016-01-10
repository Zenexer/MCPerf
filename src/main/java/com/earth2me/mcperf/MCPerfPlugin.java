package com.earth2me.mcperf;

import com.earth2me.mcperf.validity.ValidityConfiguration;
import lombok.Getter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MCPerfPlugin extends JavaPlugin {
    @Getter
    private EntityManager entityManager;
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

    private final List<Manager> managers = new ArrayList<>();

    private void ensureConfig() {
        try {
            if (!new File("config.yml").exists()) {
                saveDefaultConfig();
            }
        } catch (Exception ex) {
            getLogger().warning("Couldn't save default configuration: " + ex.getMessage());
        }
    }

    private void loadConfiguration() {
        FileConfiguration config = getConfig();

        entityManager.setNearbyChunkRadius(config.getInt("entityManager.nearbyChunkRadius", entityManager.getNearbyChunkRadius()));
        entityManager.setNearbyCreatureLimit(config.getInt("entityManager.nearbyCreatureLimit", entityManager.getNearbyCreatureLimit()));
        entityManager.setNearbyItemLimit(config.getInt("entityManager.nearbyItemLimit", entityManager.getNearbyItemLimit()));
        entityManager.setWorldCreatureLimit(config.getInt("entityManager.worldCreatureLimit", entityManager.getWorldCreatureLimit()));
        entityManager.setWorldItemLimit(config.getInt("entityManager.worldItemLimit", entityManager.getWorldItemLimit()));

        validityManager.setConfig(new ValidityConfiguration(config));

        screeningManager.setEnabled(config.getBoolean("screeningManager.enabled", screeningManager.isEnabled()));
        screeningManager.setGracePeriod(config.getLong("screeningManager.gracePeriod", screeningManager.getGracePeriod()));
    }

    @Override
    public void onEnable() {
        ensureConfig();

        managers.addAll(Arrays.asList(
                securityManager = new SecurityManager(getServer(), getLogger(), this),
                monitorManager = new MonitorManager(getServer(), getLogger(), this),
                entityManager = new EntityManager(getServer(), getLogger(), this),
                validityManager = new ValidityManager(getServer(), getLogger(), this),
                pluginMessageManager = new PluginMessageManager(getServer(), getLogger(), this),
                screeningManager = new ScreeningManager(getServer(), getLogger(), this)
        ));
        pluginMessageManager.register();

        // Listeners must already be instantiated.
        loadConfiguration();

        PluginManager pluginManager = getServer().getPluginManager();
        managers.forEach(listener -> pluginManager.registerEvents(listener, this));

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

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName()) {
            case "mcperf":
                if (!sender.hasPermission("mcperf.reload") && !sender.isOp()) {
                    return false;
                }

                reload();
                sender.sendMessage("MCPerf reloaded");
                return true;

            default:
                return false;
        }
    }
}
