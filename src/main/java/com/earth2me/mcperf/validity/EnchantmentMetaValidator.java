package com.earth2me.mcperf.validity;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.Map;

public class EnchantmentMetaValidator extends MetaValidator<EnchantmentStorageMeta> {
    @Override
    public Class<EnchantmentStorageMeta> getMetaType() {
        return EnchantmentStorageMeta.class;
    }

    @Override
    protected boolean isValidMeta(ItemStack stack, EnchantmentStorageMeta meta, boolean strict) {
        if (!super.isValidMeta(stack, meta, strict)) {
            return false;
        }

        if (!getConfig().isEnchantmentCheckingEnabled() || !meta.hasEnchants()) {
            return true;
        }

        for (Map.Entry<Enchantment, Integer> kv : stack.getEnchantments().entrySet()) {
            Enchantment enchantment = kv.getKey();
            int level = kv.getValue();

            if (level < enchantment.getStartLevel() || level > enchantment.getMaxLevel()) {
                onInvalid("enchantment level (%d)", level);
                return false;
            }

            for (Enchantment e : stack.getEnchantments().keySet()) {
                if (!enchantment.equals(e) && enchantment.conflictsWith(e)) {
                    onInvalid("enchantment combination (%s + %s)", enchantment.getName(), e.getName());
                    return false;
                }
            }
        }

        return true;
    }
}
