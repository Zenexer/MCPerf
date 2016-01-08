package com.earth2me.mcperf;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Server;
import org.bukkit.event.Listener;

import java.util.logging.Logger;

public abstract class Manager implements Listener {
    @Getter
    @Setter
    private boolean enabled;
    @Getter
    private final Logger logger;
    @Getter
    private final Server server;
    @Getter
    private final MCPerfPlugin plugin;

    public Manager(Server server, Logger logger, MCPerfPlugin plugin) {
        this(server, logger, plugin, true);
    }

    public Manager(Server server, Logger logger, MCPerfPlugin plugin, boolean enabled) {
        this.server = server;
        this.logger = logger;
        this.plugin = plugin;
        this.enabled = enabled;
    }
}
