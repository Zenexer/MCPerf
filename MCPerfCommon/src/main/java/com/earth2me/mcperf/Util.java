package com.earth2me.mcperf;

import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;

import java.util.stream.Stream;

public class Util {
    private static final String ALERT_PREFIX = ChatColor.RED + "/!\\ MCPerf /!\\ " + ChatColor.LIGHT_PURPLE;
    private static final String NOTICE_PREFIX = ChatColor.GRAY + "[MCPerf] ";

    private Util() {
        throw new UnsupportedOperationException("Static class");
    }

    // TODO: This doesn't seem to be used consistently.
    public static boolean denyPermission(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Permission denied.");
        return true;
    }

    public static void sendAlert(Server server, String format, Object... args) {
        sendAlert(server, String.format(format, args));
    }

    public static void sendAlert(Server server, String message) {
        send(server, ALERT_PREFIX, message);
    }

    @SuppressWarnings("unused")
    public static void sendNotice(Server server, String format, Object... args) {
        sendNotice(server, String.format(format, args));
    }

    public static void sendNotice(Server server, String message) {
        send(server, NOTICE_PREFIX, message);
    }

    private static void send(Server server, String prefix, String message) {
        String text = prefix + message;

        Stream.concat(
                Stream.of(server.getConsoleSender()),
                server.getOnlinePlayers().stream().filter(
                        p -> p.isOp() ||
                                p.hasPermission("mcperf.receivealerts") ||
                                p.hasPermission("mcperf.*") ||
                                p.hasPermission("*")
                )
        ).distinct().forEach(s -> s.sendMessage(text));
    }

    public static void println(Server server, String format, Object... args) {
        println(server, String.format(format, args));
    }

    public static void println(Server server, String message) {
        final String substitute = ChatColor.GRAY.toString();
        final ChatColor[] replace = {
                ChatColor.BLACK,
                ChatColor.DARK_GRAY,
        };

        for (ChatColor color : replace) {
            message = message.replace(color.toString(), substitute);
        }

        server.getConsoleSender().sendMessage(message);
    }
}