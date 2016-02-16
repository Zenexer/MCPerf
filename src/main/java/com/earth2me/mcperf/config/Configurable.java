package com.earth2me.mcperf.config;

import org.bukkit.configuration.file.FileConfiguration;

public interface Configurable {
    String getConfigPath();

    String getId();

    void onConfig(FileConfiguration config);
}
