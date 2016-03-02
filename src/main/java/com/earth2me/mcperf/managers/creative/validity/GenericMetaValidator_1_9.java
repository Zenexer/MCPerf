package com.earth2me.mcperf.managers.creative.validity;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class GenericMetaValidator_1_9 extends GenericMetaValidator {
    @Override
    protected boolean isValidNBT(ItemStack stack, ItemMeta meta) {
        return true;
    }
}
