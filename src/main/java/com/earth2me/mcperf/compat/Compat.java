package com.earth2me.mcperf.compat;

import org.bukkit.Bukkit;

public class Compat {
    private static final Version V;
    private static final Version v_1_9 = new Version(1, 9);

    static {
        int[] version = new int[5];
        String versionString = Bukkit.getBukkitVersion();
        String[] tok = versionString.split("-", 3);

        Version.parsePartialVersion(versionString, tok[0], version, 0, 3);

        if (tok.length >= 2 && tok[1].startsWith("R")) {
            Version.parsePartialVersion(versionString, tok[1].substring(1), version, 3, 2);
        }

        V = new Version(version);
    }
    private Compat() {
        throw new UnsupportedOperationException("Static class");
    }

    public static boolean hasAttributeApi() {
        return V.isGreaterThanOrEqualTo(v_1_9);
    }
}
