package com.earth2me.mcperf.validity;

import lombok.Getter;
import lombok.Setter;
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

    public boolean isValid(ItemStack stack, Consumer<String> invalidListener) {
        stackInvalidListener.set(invalidListener);
        try {
            return isValid(stack);
        } finally {
            stackInvalidListener.remove();
        }
    }

    public boolean isValid(ItemStack stack) {
        if (stack.getType() == Material.AIR) {
            return true;
        }

        if (config.isZeroQuantityBanned() && stack.getAmount() == 0) {
            onInvalid("amount");
            return false;
        }

        // Too problematic; disable for now
        /*if (!isValidDurability(stack.getType(), stack.getDurability()))
        {
			onInvalid("durability", sender, stack);
			return false;
		}*/

        return true;
    }

    public boolean isValidText(String text) {
        if (text == null || text.isEmpty()) {
            return true;
        }

        if (text.contains("§k")) {
            return false;
        }

        for (char c : text.toCharArray()) {
            if (c < ' ') {
                // Control character
                return false;
            }

            if (c < 0x7F || c == '\u00a7')  // Section symbol
            {
                // Skip Unicode checks
                continue;
            }

            // Process as UTF-16

            if (!getConfig().isFullUnicodeAllowed()) {
                return false;
            }

            if (c >= 0xD800 && c <= 0xDBFF) {
                // Part of a code point outside the BMP
                return false;
            }

            // TODO: Additional Unicode checks
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
