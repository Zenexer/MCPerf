package com.earth2me.mcperf;

import lombok.Getter;
import org.bukkit.Server;

public class PluginCommandSender extends DelegateCommandSender {
    @Getter
    private final String id;
    private final String name;

    public PluginCommandSender(Server server, String id) {
        super(server);

        this.id = id;
        this.name = "MCPerf:" + id;
    }

    @Override
    public String getName() {
        return name;
    }
}
