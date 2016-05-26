package com.earth2me.mcperf.util.concurrent;

import lombok.AccessLevel;
import lombok.Getter;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Tasks {
    @Getter(AccessLevel.PACKAGE)
    private static ExecutorService pool;
    @Getter(AccessLevel.PACKAGE)
    private static Server server;
    @Getter(AccessLevel.PACKAGE)
    private static Plugin plugin;

    private Tasks() {
        throw new UnsupportedOperationException();
    }

    public synchronized static void init(Server server, Plugin plugin) {
        Tasks.server = server;
        Tasks.plugin = plugin;
        Tasks.pool = Executors.newCachedThreadPool();
    }

    private static void logDeinit(Level level, String message) {
        if (plugin != null && plugin.getLogger() != null) {
            plugin.getLogger().log(level, message);
        } else {
            System.out.println("[MCPerf] " + message);
        }
    }

    public synchronized static void deinit() {
        if (server != null && plugin != null && getBukkitScheduler() != null) {
            logDeinit(Level.INFO, "Stopping Bukkit tasks");
            getBukkitScheduler().cancelTasks(plugin);
        }

        if (pool != null && !pool.isShutdown()) {
            logDeinit(Level.INFO, "Stopping custom tasks");
            pool.shutdown();

            boolean result;
            try {
                result = pool.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                result = false;
            }

            if (!result) {
                logDeinit(Level.WARNING, "Custom task thread pool didn't stop in time; forcing shutdown via interrupts");
                pool.shutdownNow();
            }
        }

        server = null;
        plugin = null;
        pool = null;
    }

    static BukkitScheduler getBukkitScheduler() {
        return getServer().getScheduler();
    }

    static Logger getLogger() {
        return getPlugin().getLogger();
    }

    public static void async(TaskFunc task) {
        Tasks.getPool().submit((Callable<Object>) task);
    }

    public static void sync(TaskFunc task) {
        Tasks.getBukkitScheduler().runTask(Tasks.getPlugin(), task);
    }
}
