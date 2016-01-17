package com.earth2me.mcperf;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.stream.Stream;


public class Util {
    private Util() {
        throw new UnsupportedOperationException("Static class");
    }

    public static boolean denyPermission(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Permission denied.");
        return true;
    }

    public static void sendOpMessage(Server server, String format, Object... args) {
        final String message = "/!\\ MCPerf /!\\ " + String.format(format, args);
        Stream.concat(
                Stream.of(server.getConsoleSender()),
                server.getOnlinePlayers().stream().filter(Player::isOp)
        ).forEach(s -> s.sendMessage(message));
    }
}