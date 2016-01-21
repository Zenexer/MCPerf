package com.earth2me.mcperf;

import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;

import java.util.stream.Stream;


public class Util {
    private static final String ALERT_PREFIX = ChatColor.RED + "/!\\ MCPerf /!\\ " + ChatColor.LIGHT_PURPLE;

    private Util() {
        throw new UnsupportedOperationException("Static class");
    }

    public static boolean denyPermission(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Permission denied.");
        return true;
    }

    public static void sendAlert(Server server, String format, Object... args) {
        final String message = ALERT_PREFIX + String.format(format, args);
        Stream.concat(
                Stream.of(server.getConsoleSender()),
                server.getOnlinePlayers().stream().filter(p -> p.hasPermission("mcperf.receivealerts"))
        ).distinct().forEach(s -> s.sendMessage(message));
    }
}