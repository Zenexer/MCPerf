package com.earth2me.mcperf.mojang;

/**
 * Property of Mojang.
 *
 * @author Mojang
 */

import java.util.Random;
import java.util.UUID;

@SuppressWarnings("unused")
public class MathHelper {
    public static final float a = sqrt(2.0F);
    private static final float[] sin = new float[65536];
    private static final int[] c;
    private static final double d;
    private static final double[] asin;
    private static final double[] cos;

    public static float sin(float n) {
        return sin[(int) (n * 10430.378F) & 0xffff];
    }

    public static float cos(float n) {
        return sin[(int) (n * 10430.378F + 16384.0F) & 0xffff];
    }

    public static float sqrt(float n) {
        return (float) Math.sqrt((double) n);
    }

    public static float sqrt(double n) {
        return (float) Math.sqrt(n);
    }

    public static int d(float var0) {
        int var1 = (int) var0;
        return var0 < (float) var1 ? var1 - 1 : var1;
    }

    public static int floor(double var0) {
        int var2 = (int) var0;
        return var0 < (double) var2 ? var2 - 1 : var2;
    }

    public static long d(double var0) {
        long var2 = (long) var0;
        return var0 < (double) var2 ? var2 - 1L : var2;
    }

    public static float e(float var0) {
        return var0 >= 0.0F ? var0 : -var0;
    }

    public static int a(int var0) {
        return var0 >= 0 ? var0 : -var0;
    }

    public static int f(float var0) {
        int var1 = (int) var0;
        return var0 > (float) var1 ? var1 + 1 : var1;
    }

    public static int f(double var0) {
        int var2 = (int) var0;
        return var0 > (double) var2 ? var2 + 1 : var2;
    }

    public static int clamp(int var0, int var1, int var2) {
        return var0 < var1 ? var1 : (var0 > var2 ? var2 : var0);
    }

    public static float a(float var0, float var1, float var2) {
        return var0 < var1 ? var1 : (var0 > var2 ? var2 : var0);
    }

    public static double a(double var0, double var2, double var4) {
        return var0 < var2 ? var2 : (var0 > var4 ? var4 : var0);
    }

    public static double b(double var0, double var2, double var4) {
        return var4 < 0.0D ? var0 : (var4 > 1.0D ? var2 : var0 + (var2 - var0) * var4);
    }

    public static double a(double var0, double var2) {
        if (var0 < 0.0D) {
            var0 = -var0;
        }

        if (var2 < 0.0D) {
            var2 = -var2;
        }

        return var0 > var2 ? var0 : var2;
    }

    public static int nextInt(Random var0, int var1, int var2) {
        return var1 >= var2 ? var1 : var0.nextInt(var2 - var1 + 1) + var1;
    }

    public static float a(Random var0, float var1, float var2) {
        return var1 >= var2 ? var1 : var0.nextFloat() * (var2 - var1) + var1;
    }

    public static double a(Random var0, double var1, double var3) {
        return var1 >= var3 ? var1 : var0.nextDouble() * (var3 - var1) + var1;
    }

    @SuppressWarnings("ForLoopReplaceableByForEach")
    public static double a(long[] var0) {
        long var1 = 0L;
        int var4 = var0.length;

        for (int var5 = 0; var5 < var4; ++var5) {
            long var6 = var0[var5];
            var1 += var6;
        }

        return (double) var1 / (double) var0.length;
    }

    public static float g(float var0) {
        var0 %= 360.0F;
        if (var0 >= 180.0F) {
            var0 -= 360.0F;
        }

        if (var0 < -180.0F) {
            var0 += 360.0F;
        }

        return var0;
    }

    public static double g(double var0) {
        var0 %= 360.0D;
        if (var0 >= 180.0D) {
            var0 -= 360.0D;
        }

        if (var0 < -180.0D) {
            var0 += 360.0D;
        }

        return var0;
    }

    public static int a(String var0, int var1) {
        try {
            return Integer.parseInt(var0);
        } catch (Throwable var3) {
            return var1;
        }
    }

    public static int a(String var0, int var1, int var2) {
        return Math.max(var2, a(var0, var1));
    }

    public static double a(String var0, double var1) {
        try {
            return Double.parseDouble(var0);
        } catch (Throwable var4) {
            return var1;
        }
    }

    public static double a(String var0, double var1, double var3) {
        return Math.max(var3, a(var0, var1));
    }

    public static int b(int var0) {
        int var1 = var0 - 1;
        var1 |= var1 >> 1;
        var1 |= var1 >> 2;
        var1 |= var1 >> 4;
        var1 |= var1 >> 8;
        var1 |= var1 >> 16;
        return var1 + 1;
    }

    private static boolean d(int var0) {
        return var0 != 0 && (var0 & var0 - 1) == 0;
    }

    private static int e(int var0) {
        var0 = d(var0) ? var0 : b(var0);
        return c[(int) ((long) var0 * 125613361L >> 27) & 31];
    }

    public static int c(int var0) {
        return e(var0) - (d(var0) ? 0 : 1);
    }

    public static int c(int var0, int var1) {
        if (var1 == 0) {
            return 0;
        } else if (var0 == 0) {
            return var1;
        } else {
            if (var0 < 0) {
                var1 *= -1;
            }

            int var2 = var0 % var1;
            return var2 == 0 ? var0 : var0 + var1 - var2;
        }
    }

    public static UUID a(Random var0) {
        long var1 = var0.nextLong() & -61441L | 16384L;
        long var3 = var0.nextLong() & 4611686018427387903L | -9223372036854775808L;
        return new UUID(var1, var3);
    }

    public static double c(double var0, double var2, double var4) {
        return (var0 - var2) / (var4 - var2);
    }

    public static double yaw(double x, double z) {
        double distance = z * z + x * x;
        if (Double.isNaN(distance)) {
            return 0.0D / 0.0;
        } else {
            boolean negX = x < 0.0D;
            if (negX) {
                x = -x;
            }

            boolean negZ = z < 0.0D;
            if (negZ) {
                z = -z;
            }

            boolean swapped = x > z;
            if (swapped) {
                double tmp = z;
                z = x;
                x = tmp;
            }

            double norm = normalizationFactor(distance);
            z *= norm;
            x *= norm;
            double var11 = d + x;
            int var13 = (int) Double.doubleToRawLongBits(var11);
            double var14 = asin[var13];
            double var16 = cos[var13];
            double var18 = var11 - d;
            double var20 = x * var16 - z * var18;
            double var22 = (6.0D + var20 * var20) * var20 * 0.16666666666666666D;
            double var24 = var14 + var22;
            if (swapped) {
                var24 = 1.5707963267948966D - var24;
            }

            if (negZ) {
                var24 = 3.141592653589793D - var24;
            }

            if (negX) {
                var24 = -var24;
            }

            return var24;
        }
    }

    public static double normalizationFactor(double distance) {
        double halfDistance = 0.5D * distance;
        long distanceBits = Double.doubleToRawLongBits(distance);
        distanceBits = 6910469410427058090L - (distanceBits >> 1);
        distance = Double.longBitsToDouble(distanceBits);
        distance *= 1.5D - halfDistance * distance * distance;
        return distance;
    }

    static {
        int i;
        for (i = 0; i < 65536; ++i) {
            sin[i] = (float) Math.sin((double) i * 3.141592653589793D * 2.0D / 65536.0D);
        }

        c = new int[]{0, 1, 28, 2, 29, 14, 24, 3, 30, 22, 20, 15, 25, 17, 4, 8, 31, 27, 13, 23, 21, 19, 16, 7, 26, 12, 18, 6, 11, 5, 10, 9};
        d = Double.longBitsToDouble(4805340802404319232L);
        asin = new double[257];
        cos = new double[257];

        for (i = 0; i < 257; ++i) {
            double ratio = (double) i / 256.0D;
            double arcsine = Math.asin(ratio);
            cos[i] = Math.cos(arcsine);
            asin[i] = arcsine;
        }

    }
}
