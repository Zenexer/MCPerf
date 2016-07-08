package com.earth2me.mcperf.managers.creative.validity;

import org.bukkit.Bukkit;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

public abstract class GenericMetaValidator extends MetaValidator<ItemMeta> {
    private static final int ENCHANT_THRESHOLD = 10;

    @Override
    public Class<ItemMeta> getMetaType() {
        return ItemMeta.class;
    }

    @Override
    public boolean isApplicable(ItemMeta meta) {
        return true;
    }

    protected abstract boolean isValidNBT(ItemStack stack, ItemMeta meta);

    @Override
    @SuppressWarnings("RedundantIfStatement")
    protected boolean isValidMeta(ItemStack stack, ItemMeta meta, boolean strict) {
        if (meta.hasDisplayName()) {
            String name = meta.getDisplayName();

            if (name.length() > getConfig().getMaxNameLength() || name.isEmpty()) {
                onInvalid("display name length (%d)", name.length());
                return false;
            }

            if (!isValidText(name)) {
                onInvalid("display name text");
                return false;
            }
        }

        if (meta.hasLore()) {
            List<String> lore = meta.getLore();

            if (lore.size() > getConfig().getMaxLoreLines()) {
                onInvalid("lore line count (%d)", lore.size());
                return false;
            }

            int i = 1;
            for (String line : lore) {
                if (line.length() > getConfig().getMaxLoreLineLength()) {
                    onInvalid("lore line length (%d, line %d)", line.length(), i);
                    return false;
                }

                if (!isValidText(line)) {
                    onInvalid("lore text");
                    return false;
                }

                i++;
            }
        }


        if (getConfig().isEnchantmentCheckingEnabled() && meta.hasEnchants()) {
            Map<Enchantment, Integer> enchantments = meta.getEnchants();
            if (!validateEnchantments(enchantments)) {
                return false;
            }
        }

        try {
            if (!isValidNBT(stack, meta)) {
                return false;
            }
        } catch (Exception ex) {
            Bukkit.getLogger().severe("[MCPerf] isValidNBT threw an exception!\n" + ex.toString());
        }

        return true;
    }

    protected boolean validateEnchantments(Map<Enchantment, Integer> enchantments) {
        for (Map.Entry<Enchantment, Integer> kv : enchantments.entrySet()) {
            Enchantment enchantment = kv.getKey();
            int level = kv.getValue();

            if (level < enchantment.getStartLevel() || level > Math.max(ENCHANT_THRESHOLD, enchantment.getMaxLevel())) {
                onInvalid("enchantment level (%d)", level);
                return false;
            }

            for (Enchantment e : enchantments.keySet()) {
                if (!enchantment.equals(e) && enchantment.conflictsWith(e)) {
                    onInvalid("enchantment combination (%s + %s)", enchantment.getName(), e.getName());
                    return false;
                }
            }
        }

        return true;
    }
}
