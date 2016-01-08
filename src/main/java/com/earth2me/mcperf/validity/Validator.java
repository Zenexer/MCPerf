package com.earth2me.mcperf.validity;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

public abstract class Validator {
    private final ThreadLocal<Consumer<String>> stackInvalidListener = new ThreadLocal<>();

    @Getter
    @Setter
    private ValidityConfiguration config;

    protected void onInvalid(String format, Object... args) {
        onInvalid(String.format(format, args));
    }

    protected void onInvalid(String reason) {
        Consumer<String> invalidListener = stackInvalidListener.get();
        if (invalidListener != null) {
            invalidListener.accept(reason);
        }
    }

    public boolean isValid(ItemStack stack, boolean strict, Consumer<String> invalidListener) {
        stackInvalidListener.set(invalidListener);
        try {
            return isValid(stack, strict);
        } finally {
            stackInvalidListener.remove();
        }
    }

    public boolean isValid(ItemStack stack, boolean strict) {
        if (stack.getType() == Material.AIR) {
            return true;
        }

        if (config.isZeroQuantityBanned() && stack.getAmount() == 0) {
            onInvalid("amount");
            return false;
        }

        if (strict) {
            if (!isValidDurability(stack.getType(), stack.getDurability())) {
                onInvalid("durability");
                return false;
            }
        }

        return true;
    }

    public boolean isValidText(String text) {
        if (text == null || text.isEmpty()) {
            return true;
        }

        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];

            if (c >>> 5 == 0) {  // Optimized: c < ' '
                // Control character
                switch (c) {
                    case '\0':  // null byte
                        if (!getConfig().isNullCharAllowed()) {
                            return false;
                        }
                        continue;

                    case 0x1b:  // escape
                        if (!getConfig().isEscapeCharAllowed()) {
                            return false;
                        }
                        continue;

                    case '\r':  // carriage return (CR)
                    case '\n':  // line feed (LF)
                        if (!getConfig().isLinebreakUnicodeAllowed()) {
                            return false;
                        }
                        continue;

                    case '\t':  // horizontal tab (HT)
                    case 0xb:   // vertical tab (VT)
                        if (!getConfig().isUnusualWhitespaceUnicodeAllowed()) {
                            return false;
                        }
                        continue;

                    default:
                        if (!getConfig().isControlUnicodeAllowed()) {
                            return false;
                        }
                        continue;
                }
            }

            if (c >>> 7 == 0) {  // Optimized: c <= 0x7F
                // It's 7-bit ASCII; skip the rest
                continue;
            }

            if (c == '§') {
                if (!getConfig().isFormattingAllowed()) {
                    return false;
                }

                if (i == chars.length - 1) {
                    // Last character in string
                    return false;
                }

                char f = chars[++i];
                // Character.toLowerCase() uses Unicode, which is unnecessary for this task.
                // This quick trick emulates a traditional C locale (ASCII, 7-bit).
                // This is incompatible with ISO-8859, which requires additional logic to handle chars above '~' (0x7F).
                if (f >= 'A' && f <= 'Z') {
                    f -= 0x20;
                }
                ChatColor format = ChatColor.getByChar(f);
                if (format == null) {
                    // Invalid format character
                    return false;
                }
                if (!getConfig().isCrazyTextAllowed() && format == ChatColor.MAGIC) {
                    return false;
                }
            }

            /*
             * Non-ASCII character (above U+007F)
             *
             * IMPORTANT: Don't assume Java uses UTF-16!  The code here is implementation-independent.  It assumes
             *            a JVM could be using an alternative encoding, such as UTF-8, which may not use the surrogate
             *            pair system.
             *
             * ALSO:      With most/all existing JVMs, a single Unicode character can be TWO consecutive char values!
             *            This means we have to be careful to convert to a 32-bit int, which can hold all code points,
             *            before we perform our tests.  If we neglect to do so, only code points within the basic
             *            multilingual plane (BMP) will validate.  This alienates many non-Latin-based scripts that
             *            rely heavily on code points in the SMP and SIP  With Java's default UTF-16 encoding, anything
             *            outside the BMP will consume two char values.
             *
             * COMMON MISCONCEPTIONS:.
             *  - Unicode characters range from 0 to 0xFFFF.
             *      That's only the Basic Multilingual Plane (BMP), where most Western characters are coded.  There are
             *      15 other planes, the first two of which are very important many non-Western scripts.  Across all
             *      planes, Unicode code points range from 0 to 0x10FFFF.
             *  - There is no way a future implementation of Java could use anything other than UTF-16.
             *      This is not a guarantee.  Only a few methods would break if this changed.
             *  - A char will always be 16 bits.
             *      This is not a guarantee.  It's quite possible that at some point in the future, char will come to
             *      represent a full Unicode character.  Currently, it can only represent BMP code points in full; it
             *      takes two chars to represent anything outside that.
             *  - Java uses UCS-2.
             *      UCS-2 is deprecated and only supports characters in the BMP.  Java supports all 16 planes.
             *  - UTF-16 means that every Unicode character takes up exactly one 16-bit unit.
             *      That's UCS-2.  Only characters in the first plane (the BMP) consume one unit; the other 15 planes
             *      use pairs of units.
             *  - My players don't use anything outside the BMP.
             *      Ever seen an emoticon?  SMS, Hangouts, and many others use actual emoticon characters rather than
             *      images.  That's why emoticons differ between platforms.  The BMP is plane 0; emoticons are
             *      generally in planes 1 (SMP) and 15 (PUA).  If you plan on adding pretty icons to your items,
             *      you'll need to venture outside the BMP.
             */

            if (!getConfig().isBasicUnicodeAllowed() && !getConfig().isSupplementaryUnicodeAllowed()) {
                // Well that was easy.
                // PUA counts as supplementary for our purposes.
                return false;
            }

            int codepoint = Character.codePointAt(chars, i);

            if (!Character.isValidCodePoint(codepoint)) {
                // Something went terribly wrong and codepoint is outside the valid range (0 - 0x10FFFF as of writing).
                return false;
            }

            if (!Character.isDefined(codepoint)) {
                // Character.charCount is unpredictable if the code point is undefined; must break loop here.
                // (It's invalid anyway.)
                return false;
            }

            // This is the number of char values, NOT the number of Unicode characters.  The number of Unicode
            // characters is ALWAYS one.  The number of char values is NOT always one.
            i += Character.charCount(codepoint) - 1;  // - 1 to account for i++ for loop stepper

            int type = Character.getType(c);

            if (type == Character.UNASSIGNED) {
                // Skip everything.
                return false;
            }

            if (Character.isBmpCodePoint(codepoint)) {
                if (!getConfig().isBasicUnicodeAllowed()) {
                    return false;
                }
            } else if (!getConfig().isSupplementaryUnicodeAllowed()) {
                return false;
            }

            if (Character.isISOControl(codepoint)) {
                if (!getConfig().isControlUnicodeAllowed()) {
                    return false;
                }
                continue;
            }

            switch (type) {
                // noinspection ConstantConditions
                case Character.UNASSIGNED:   // Non-existent code point--we already handled this
                    return false;

                case Character.CONTROL:      // Non-ASCII control character (we already handled ASCII)
                    if (!getConfig().isControlUnicodeAllowed()) {
                        return false;
                    }
                    break;

                case Character.PRIVATE_USE:  // PUA
                    if (!getConfig().isPrivateUnicodeAllowed()) {
                        return false;
                    }
                    break;

                case Character.LINE_SEPARATOR:
                case Character.PARAGRAPH_SEPARATOR:
                    if (!getConfig().isLinebreakUnicodeAllowed()) {
                        return false;
                    }
                    break;

                default:
                    if (Character.isWhitespace(codepoint)) {  // We already handled ASCII whitespace above.
                        if (!getConfig().isUnusualWhitespaceUnicodeAllowed()) {
                            return false;
                        }
                    }
                    break;
            }
        }

        return true;
    }

    @SuppressWarnings("RedundantIfStatement")
    private boolean isValidDurability(Material material, short durability) {
        if (durability == 0 || material == null) {
            return true;
        }

        if (durability < 0) {
            return false;
        }

        switch (material) {
            case POTION:
            case MAP:
                return true;
        }

        if (durability <= getMaxDurability(material)) {
            return true;
        }

        return false;
    }

    private static short getMaxDurability(Material material) {
        if (material.isBlock()) {
            return 15;
        }

        switch (material) {
            case SKULL_ITEM:
                return (short) Math.max(material.getMaxDurability(), 4);

            case FLOWER_POT_ITEM:
                return 13;

            case CAULDRON_ITEM:
                return 3;

            case BREWING_STAND_ITEM:
                return 7;
        }

        return material.getMaxDurability();
    }
}
