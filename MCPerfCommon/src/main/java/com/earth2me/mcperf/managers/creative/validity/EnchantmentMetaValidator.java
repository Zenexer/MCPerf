package com.earth2me.mcperf.managers.creative.validity;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

public class EnchantmentMetaValidator extends MetaValidator<EnchantmentStorageMeta> {
    @Override
    public Class<EnchantmentStorageMeta> getMetaType() {
        return EnchantmentStorageMeta.class;
    }

    @SuppressWarnings("SimplifiableIfStatement")
    @Override
    protected boolean isValidMeta(ItemStack stack, EnchantmentStorageMeta meta, boolean strict) {
        if (!super.isValidMeta(stack, meta, strict)) {
            return false;
        }

        if (!getConfig().isEnchantmentCheckingEnabled() || !meta.hasStoredEnchants()) {
            return true;
        }

        return validateEnchantments(meta.getStoredEnchants());
    }
}
