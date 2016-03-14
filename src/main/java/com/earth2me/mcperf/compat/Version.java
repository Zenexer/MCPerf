package com.earth2me.mcperf.compat;

import com.earth2me.mcperf.Util;
import com.earth2me.mcperf.annotation.NonNull;
import com.earth2me.mcperf.annotation.Nullable;
import org.bukkit.Bukkit;

import java.util.Arrays;
import java.util.logging.Level;

@SuppressWarnings("unused")
public final class Version implements Comparable<Version> {
    public final int major;
    public final int minor;
    public final int revision;
    public final int releaseMajor;
    public final int releaseMinor;
    private final int[] components;
    private String string;
    private Integer hashCode;

    public Version(@NonNull int[] components) {
        this(components[0], components[1], components[2], components[3], components[4]);
    }

    public Version(int major) {
        this(major, 0, 0, 0, 0);
    }

    public Version(int major, int minor) {
        this(major, minor, 0, 0, 0);
    }

    public Version(int major, int minor, int revision) {
        this(major, minor, revision, 0, 0);
    }

    public Version(int major, int minor, int revision, int releaseMajor) {
        this(major, minor, revision, releaseMajor, 0);
    }

    public Version(int major, int minor, int revision, int releaseMajor, int releaseMinor) {
        this.major = major;
        this.minor = minor;
        this.revision = revision;
        this.releaseMajor = releaseMajor;
        this.releaseMinor = releaseMinor;

        this.components = new int[] { major, minor, revision, releaseMajor, releaseMinor };
    }

    static void parsePartialVersion(@NonNull String versionString, @NonNull String tokenizable, @NonNull int[] version, int offset, int max) {
        String[] tok = tokenizable.split("\\D+", max + 1);

        for (int i = 0; i < tok.length && i < max; i++) {
            int v;
            try {
                v = Integer.parseUnsignedInt(tok[i]);
            } catch (NumberFormatException e) {
                Bukkit.getLogger().log(Level.WARNING, String.format("[%s] Failed to parse version string %s from %s", Util.NAME, tokenizable, versionString), e);
                v = 0;
            }

            version[offset + i] = v;
        }
    }

    @Override
    public int compareTo(@NonNull Version o) {
        for (int i = 0; i < components.length; i++) {
            if (components[i] < o.components[i]) {
                return -1;
            } else if (components[i] > o.components[i]) {
                return 1;
            }
        }

        return 0;
    }

    @Override
    public String toString() {
        if (string == null) {
            string = String.join(".", Arrays.stream(components).mapToObj(String::valueOf).toArray(String[]::new));
        }

        return string;
    }

    @Override
    public int hashCode() {
        if (hashCode == null) {
            hashCode = Arrays.hashCode(components);
        }

        return hashCode;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        return obj != null && obj.getClass() == getClass() && equals((Version) obj);
    }

    public boolean equals(@Nullable Version version) {
        return version != null && compareTo(version) == 0;
    }

    public boolean doesNotEqual(@NonNull Version version) {
        return !equals(version);
    }

    public boolean isGreaterThan(@NonNull Version version) {
        return compareTo(version) > 0;
    }

    public boolean isLessThan(@NonNull Version version) {
        return compareTo(version) < 0;
    }

    public boolean isGreaterThanOrEqualTo(@NonNull Version version) {
        return compareTo(version) >= 0;
    }

    public boolean isLessThanOrEqualTo(@NonNull Version version) {
        return compareTo(version) <= 0;
    }
}
