package com.earth2me.mcperf.managers.creative.validity;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.logging.Level;

public abstract class MetaValidator<T extends ItemMeta> extends Validator {
    public abstract Class<T> getMetaType();

    public abstract boolean isApplicable(ItemMeta meta);

    protected abstract boolean isValidMeta(ItemStack stack, T meta, boolean strict);

    protected void log(Level level, String message) {
        Bukkit.getLogger().log(level, message);
    }

    protected void log(Level level, String message, Throwable e) {
        Bukkit.getLogger().log(level, message, e);
    }

    protected void log(Level level, Throwable e) {
        log(level, e.getMessage(), e);
    }

    @SuppressWarnings("RedundantIfStatement")
    @Override
    public boolean isValid(ItemStack stack, boolean strict) {
        if (!super.isValid(stack, strict)) {
            return false;
        }

        if (!isValidMeta(stack, getMetaType().cast(stack.getItemMeta()), strict)) {
            return false;
        }

        return true;
    }
}
