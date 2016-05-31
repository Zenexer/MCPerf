package com.earth2me.mcperf.managers.security;

import com.earth2me.mcperf.Util;
import com.earth2me.mcperf.config.ConfigSetting;
import com.earth2me.mcperf.config.ConfigSettingSetter;
import com.earth2me.mcperf.managers.Manager;
import com.earth2me.mcperf.ob.ContainsConfig;
import com.earth2me.mcperf.ob.Service;
import com.earth2me.mcperf.util.Tuple;
import com.earth2me.mcperf.util.concurrent.Callback;
import com.earth2me.mcperf.util.concurrent.Tasks;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.stream.Collectors;

@Service
@ContainsConfig
public final class ProxyManager extends Manager {
    // Order matters.
    @SuppressWarnings("RedundantArrayCreation")
    private static final List<Service> knownServices = Collections.unmodifiableList(Arrays.asList(new Service[] {
            new Service("WTFast", 1023, 1119),
            new Service("Hotspot Shield", 80, 1723, 5050, 9000),
            new Service("PIA", 22, 53, 80, 110, 443, 500, 1723, 8888),  // HTTP 8888, Location: https://www.privateinternetaccess.com/
            new Service("SoftEther#1", 443, 992, 5555),
            // Too sensitive
            //new Service("SoftEther#2", new Integer[]{8888}, new Integer[]{22, 53, 80, 110, 443, 1723, 8080}),

            new Service("unidentified shell", 1723, 8099),
    }));
    // Order matters.
    @SuppressWarnings("RedundantArrayCreation")
    private static final List<Service> suspiciousServices = Collections.unmodifiableList(Arrays.asList(new Service[] {
            // These are just too sensitive.  Too many shitty ISPs deploy routers with 1723 open.
            /*
            new Service("generic PPTP #1", new Integer[] { 1723, 22 }, new Integer[] {
                    8888,  // DD-WRT
            }),
            new Service("generic PPTP #2", 1723, 53),
            new Service("generic PPTP #3", 1723, 443),
            */

            new Service("generic SOCKS #1", 1080, 22),
            new Service("generic SOCKS #2", 1080, 53),
            new Service("generic SOCKS #3", 1080, 80),
    }));

    @Getter
    @ConfigSetting
    // Not safe to alter; only safe to assign
    // For future
    private Set<Integer> tcpPorts = Collections.emptySet();
    @Getter
    @Setter
    @ConfigSetting
    private int tcpTimeout = 1_000;  // ms
    @Getter
    @ConfigSetting
    private int threadPoolSize = 18;
    @Getter
    @Setter
    @ConfigSetting
    private boolean vulnScannerEnabled = false;
    @Getter
    @Setter
    @ConfigSetting
    private List<String> knownServiceCommands = Collections.singletonList("tempban %1$s 1d [MCPerf] We don't allow proxies/VPNs.");
    @Getter
    @Setter
    @ConfigSetting
    private List<String> suspiciousCommands = Collections.singletonList("tempban %1$s 5m [MCPerf] You appear to be using a proxy/VPN. If this is incorrect, contact support.");
    @SuppressWarnings("SpellCheckingInspection")
    @Getter
    @Setter
    @ConfigSetting
    private Set<String> whitelist = new HashSet<>(Arrays.asList(new String[] {
            // Must be lowercase.

            "lizchlops",          // Father is an engineer; has DD-WRT + VPN; confirmed by talking to father.
                                  // Triggered by {1723, 22}.  Exclusion of 8888 fixes, but whitelist just in case.
            "kaichlops",          // Shared by LizChlops.
            "crazyviolin",        // Sibling of LizChlops.
            "xvinyl_scratchx",    // /alts for LizChlops
            "xxrainbow_dashxx",   // /alts for LizChlops
    }));

    private volatile ExecutorService executorService;

    public ProxyManager() {
        super("MjEbcHJveHkK");
    }

    @ConfigSettingSetter
    @SuppressWarnings("RedundantArrayCreation")  // Allows us to have comma after last entry
    public void setTcpPorts(Set<Integer> tcpPorts) {
        tcpPorts = new HashSet<>(tcpPorts);
        tcpPorts.add(1);  // Ensure that the user doesn't have (almost) all ports open

        for (Service service : knownServices) {
            tcpPorts.addAll(service.includePorts);
            tcpPorts.addAll(service.excludePorts);
        }

        for (Service service : suspiciousServices) {
            tcpPorts.addAll(service.includePorts);
            tcpPorts.addAll(service.excludePorts);
        }

        this.tcpPorts = Collections.unmodifiableSet(tcpPorts);
    }

    @Override
    protected void onInit() {
        getServer().getPluginCommand("proxy").setExecutor(this::onCommand);

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
        scanPlayer(player, openPorts -> sendNoticeAsync("Player %s may be using a proxy/VPN.  Open ports: %s", player.getName(), String.join(", ", openPorts)), null);
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
        Set<Integer> tcpPorts = getTcpPorts();
        BukkitScheduler scheduler = getServer().getScheduler();
        Plugin plugin = getPlugin();

        if (this.executorService == null) {
            startService();
        }
        assert this.executorService != null;
        ExecutorService executorService = this.executorService;

        return scheduler.runTaskAsynchronously(plugin, () -> {
            List<Tuple<Integer, Future<Boolean>>> tasks = new LinkedList<>();

            try {
                tasks.addAll(tcpPorts.stream().map(
                        port -> new Tuple<>(port, scanTcpPort(executorService, addr, port, tcpTimeout))
                ).collect(Collectors.toList()));
            } catch (RejectedExecutionException e) {
                return;
            }

            List<String> openPorts = new LinkedList<>();
            Set<Integer> openTcp = new HashSet<>();
            try {
                for (Tuple<Integer, Future<Boolean>> t : tasks) {
                    int port = t.getA();
                    Future<Boolean> task = t.getB();

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
                        openTcp.add(port);
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
                return;
            }

            getLogger().log(Level.INFO, String.format(
                    "Open %s ports for player %s: %s",
                    openPorts.stream().allMatch(p -> p.startsWith("80/") || p.startsWith("443/")) ? "web" : "proxy",
                    player.getName(),
                    String.join(", ", openPorts)
            ));

            if (ifProxy != null) {
                ifProxy.accept(openPorts);
            }

            if (openPorts.size() >= tcpPorts.size() - 3) {
                getLogger().log(Level.INFO, String.format(
                        "Player %s appears to have %s ports open.",
                        player.getName(),
                        openPorts.size() == tcpPorts.size() ? "all" : "almost all"
                ));
                return;
            }

            checkServices(player, openTcp);

            if (isVulnScannerEnabled()) {
                Tasks.async(() -> checkVulns(player, openTcp));
            }
        });
    }

    private void checkServices(Player player, Set<Integer> openTcp) {
        for (Service service : knownServices) {
            if (checkService(player, openTcp, service, true)) {
                return;
            }
        }

        for (Service service : suspiciousServices) {
            if (checkService(player, openTcp, service, false)) {
                return;
            }
        }
    }

    private boolean checkService(Player player, Set<Integer> openTcp, Service service, boolean confident) {
        if (!openTcp.containsAll(service.includePorts) || openTcp.stream().anyMatch(service.excludePorts::contains)) {
            return false;
        }

        boolean whitelisted = getWhitelist().contains(player.getName().toLowerCase());
        boolean banned = player.isBanned();

        sendAlert(
                "%s %s using a proxy/VPN: %s%s",
                player.getName(),
                confident ? "is" : "might be",
                service.name,
                whitelisted ? "; however, they are whitelisted" :
                banned ? "; however, they are already banned" : ""
        );

        if (!whitelisted && !banned) {
            dispatchCommandsAsync(
                    confident ? getKnownServiceCommands() : getSuspiciousCommands(),
                    player.getName(),
                    player.getAddress().getAddress().getHostAddress(),
                    service.name
            );
        }
        return true;
    }

    private void requestHttp(Player player, int port, boolean secure, Callback<HttpResponse> callback) {
        Tasks.async(() -> {
            HttpResponse response = requestHttp(player, port, secure, "GET", "/");

            if (response != null) {
                Tasks.async(() -> callback.call(response));
            }
        });
    }

    private HttpResponse requestHttp(Player player, int port, boolean secure, String method, String uri) {
        if (!uri.startsWith("/")) {
            uri = "/" + uri;
        }

        URL url;
        try {
            url = new URL(String.format(
                    "%s://%s:%d%s",
                    secure ? "https" : "http",
                    player.getAddress().getAddress().getHostAddress(),
                    port,
                    uri
            ));
        } catch (MalformedURLException e) {
            getLogger().log(Level.WARNING, "Invalid URL", e);
            return null;
        }

        HttpURLConnection conn;
        try {
            conn = (HttpURLConnection) url.openConnection();
        } catch (IOException e) {
            getLogger().log(Level.FINEST, "HTTP connection failed to initialize", e);
            return null;
        }

        try {
            conn.setRequestMethod(method);
        } catch (ProtocolException e) {
            getLogger().log(Level.WARNING, "HTTP protocol error", e);
            return null;
        }

        conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.8");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; Minecraft-Open-Proxy-Monitor)");
        conn.setRequestProperty("X-Purpose", "Ensures players are not connecting from proxies, VPNs, or insecure computers");
        conn.setUseCaches(false);
        conn.setReadTimeout(getTcpTimeout());  // TODO Separate setting, perhaps?
        conn.setConnectTimeout(getTcpTimeout()); // TODO Separate setting, perhaps?
        conn.setDoOutput(true);
        conn.setDoInput(false);

        try {
            return HttpResponse.get(conn);
        } catch (SocketTimeoutException e) {
            getLogger().log(Level.FINEST, "HTTP connection attempt timed out", e);
            return null;
        } catch (IOException e) {
            getLogger().log(Level.FINEST, "Failed to establish HTTP connection", e);
            return null;
        }
    }

    @SuppressWarnings("CodeBlock2Expr")
    private void checkVulns(Player player, Set<Integer> openTcp) {
        {
            Warner warner = new Warner(player, 80);
            if (warner.isValid(openTcp)) {
                requestHttp(player, warner.port, false, (r) -> {
                    warner.vuln(true, "VMAX Web Viewer default credentials", "http://bit.ly/22pPyRn", r.getStatus() == 200 && r.serverEquals("Boa/0.94.13"));
                });
            }
        }

        {
            Warner warner = new Warner(player, 443);
            if (warner.isValid(openTcp)) {
                // Note that this is not HTTPS!
                requestHttp(player, warner.port, false, (r) -> {
                    warner.vuln(false, "Exposed DNR-202L", "Device is accessible from the internet.", r.serverStartsWith("Embedthis-Appweb/") && r.headerContains("www-authenticate", "realm=\"DNR-202L\""));
                });
            }
        }
    }

    private static class Service {
        public final String name;
        public final Set<Integer> includePorts;
        public final Set<Integer> excludePorts;

        private Service(String name, Integer... includePorts) {
            this.name = name;
            this.includePorts = new HashSet<>(Arrays.asList(includePorts));
            this.excludePorts = Collections.emptySet();
        }

        @SuppressWarnings("unused")
        private Service(String name, Integer[] includePorts, Integer[] excludePorts) {
            this.name = name;
            this.includePorts = new HashSet<>(Arrays.asList(includePorts));
            this.excludePorts = new HashSet<>(Arrays.asList(excludePorts));
        }
    }

    @RequiredArgsConstructor
    private class Warner {
        public final Player player;
        public final int port;

        public boolean isValid(Set<Integer> openTcp) {
            return openTcp.contains(port);
        }

        private void vuln(boolean warn, String name, String details, boolean condition) {
            if (!condition) {
                return;
            }

            String message = String.format(
                    "Vulnerability: %s\n"
                    + "Port: %d/tcp\n"
                    + "Details: %s\n"
                    + "Note: This feature is still in development and may be inaccurate.",
                    name,
                    port,
                    details
            );

            Tasks.sync(() -> {
                if (warn) {
                    player.sendMessage("A potential security vulnerability was detected on your network while checking for proxies/VPNs.\n" + message);
                }

                sendNotice("Potential vulnerability for player %s: %s", player.getName(), message);
            });
        }
    }

    @SuppressWarnings("unused")
    @Data
    private static class HttpResponse {
        private final int status;
        private final String statusMessage;
        @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")  // IntelliJ being stupid
        private final Map<String, List<String>> headers;
        private final String content;

        public static HttpResponse get(HttpURLConnection connection) throws IOException {
            connection.connect();

            return new HttpResponse(
                    connection.getResponseCode(),
                    connection.getResponseMessage(),
                    normalizeHeaders(connection.getHeaderFields()),
                    connection.getContentLength() <= 300 * 1024 * 1024
                            ? (String) connection.getContent(new Class[] { String.class })
                            : null
            );
        }

        private static Map<String, List<String>> normalizeHeaders(Map<String, List<String>> dirty) {
            Map<String, List<String>> normalized = new HashMap<>();

            for (Map.Entry<String, List<String>> kv : dirty.entrySet()) {
                normalized.put(kv.getKey().toLowerCase(), kv.getValue());
            }

            return Collections.unmodifiableMap(normalized);
        }

        public List<String> getHeader(String key) {
            return headers.get(key.toLowerCase());
        }

        public boolean headerMatches(String header, Predicate<String> match) {
            return getHeader(header).stream().anyMatch(match);
        }

        public boolean headerContains(String header, String s) {
            return headerMatches(header, h -> h.contains(s));
        }

        public boolean serverMatches(Predicate<String> match) {
            return headerMatches("server", match);
        }

        public boolean serverStartsWith(String prefix) {
            return headerMatches("server", h -> h.startsWith(prefix));
        }

        public boolean serverEquals(String s) {
            return headerMatches("server", s::equals);
        }

        public boolean contentContains(String s) {
            return getContent().contains(s);
        }
    }
}
