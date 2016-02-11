package com.earth2me.mcperf;

import com.earth2me.mcperf.config.ConfigSetting;
import com.earth2me.mcperf.config.ConfigSettingSetter;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ProxyManager extends Manager {
    @SuppressWarnings("MismatchedReadAndWriteOfArray")  // IntelliJ is being stupid; remove later
    @Getter
    @Setter
    @ConfigSetting
    // Not safe to alter; only safe to assign
    private int[] tcpPorts = {
            22,
            53,
            80,
            500,   // VPN: IKEv1/v2 (normally UDP, but PIA also has TCP open)
            1080,  // SOCKS
            1723,  // VPN: PPTP
    };
    @SuppressWarnings("MismatchedReadAndWriteOfArray")
    @Getter
    @Setter
    @ConfigSetting
    // Not used yet
    // Not safe to alter; only safe to assign
    private int[] udpPorts =  {
            500,   // VPN: IKEv1
            1701,  // VPN: L2TP
            4500,  // VPN: IKEv1/v2
    };
    @Getter
    @Setter
    @ConfigSetting
    private int tcpTimeout = 1_000;  // ms
    @Getter
    @ConfigSetting
    private int threadPoolSize = 18;

    private volatile ExecutorService executorService;

    public ProxyManager(Server server, Logger logger, MCPerfPlugin plugin) {
        super(server, logger, plugin, false);

        getServer().getPluginCommand("proxy").setExecutor(this::onCommand);
    }

    @Override
    protected void onInit() {
        startService();
    }

    @Override
    protected void onDeinit() {
        killService();
    }

    private void killService() {
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
    }

    private void startService() {
        executorService = Executors.newFixedThreadPool(getThreadPoolSize());
    }

    @ConfigSettingSetter
    public void setThreadPoolSize(int value) {
        if (value == threadPoolSize) {
            return;
        }

        threadPoolSize = value;

        if (executorService != null) {
            killService();
            startService();
        }
    }

    /**
     * @throws RejectedExecutionException
     */
    private static Future<Boolean> scanTcpPort(ExecutorService es, InetAddress addr, int port, int timeout) {
        return es.submit(() -> {
            try {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(addr, port), timeout);
                socket.close();
                return true;
            } catch (Exception ex) {
                return false;
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!isEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        scanPlayer(player, openPorts -> sendAlertAsync("Player %s may be using a proxy/VPN.  Open ports: %s", player.getName(), String.join(", ", openPorts)), null);
    }

    private boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender.isOp() || sender.hasPermission("*") || sender.hasPermission("mcperf.*") || sender.hasPermission("mcperf.proxy"))) {
            return Util.denyPermission(sender);
        }

        if (args.length < 1) {
            return false;
        }

        Player player = getServer().getPlayer(args[0]);
        if (player == null) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return true;
        }

        scanPlayer(
                player,
                openPorts -> sender.sendMessage(String.format("Player %s may be using a proxy/VPN.  Open ports: %s", player.getName(), String.join(", ", openPorts))),
                () -> sender.sendMessage(String.format("Player %s does not appear to be using a proxy/VPN.", player.getName()))
        );
        return true;
    }

    public BukkitTask scanPlayer(Player player, Consumer<List<String>> ifProxy, Runnable ifNotProxy) {
        InetAddress addr = player.getAddress().getAddress();
        int tcpTimeout = getTcpTimeout();
        int[] tcpPorts = getTcpPorts();
        BukkitScheduler scheduler = getServer().getScheduler();
        Plugin plugin = getPlugin();

        if (this.executorService == null) {
            startService();
        }
        assert this.executorService != null;
        ExecutorService executorService = this.executorService;

        return scheduler.runTaskAsynchronously(plugin, () -> {
            List<Future<Boolean>> tasks = new LinkedList<>();

            try {
                for (int port : tcpPorts) {
                    tasks.add(scanTcpPort(executorService, addr, port, tcpTimeout));
                }
            } catch (RejectedExecutionException e) {
                return;
            }

            List<String> openPorts = new LinkedList<>();
            try {
                int i = 0;
                for (Future<Boolean> task : tasks) {
                    int port = tcpPorts[i++];

                    Boolean open;
                    try {
                        open = task.get(tcpTimeout + 10, TimeUnit.MILLISECONDS);
                    } catch (TimeoutException ex) {
                        continue;
                    } catch (ExecutionException e) {
                        Throwable cause = e.getCause();
                        if (!(cause instanceof InterruptedException)) {
                            getLogger().log(Level.SEVERE, "Exception while scanning ports", cause);
                        }
                        return;
                    }

                    if (open != null && open) {
                        openPorts.add(String.format("%d/tcp", port));
                    }
                }
            } catch (InterruptedException e) {
                return;
            }

            if (openPorts.isEmpty()) {
                getLogger().log(Level.INFO, "No open proxy ports for player " + player.getName());

                if (ifNotProxy != null) {
                    ifNotProxy.run();
                }
            } else {
                getLogger().log(Level.INFO, String.format("Open proxy ports for player %s: %s", player.getName(), String.join(", ", openPorts)));

                if (ifProxy != null) {
                    ifProxy.accept(openPorts);
                }
            }
        });
    }
}
