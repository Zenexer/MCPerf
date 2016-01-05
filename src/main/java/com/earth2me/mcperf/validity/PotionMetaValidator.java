package com.earth2me.mcperf.validity;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;

public class PotionMetaValidator extends MetaValidator<PotionMeta> {
    @Override
    public Class<PotionMeta> getMetaType() {
        return PotionMeta.class;
    }

    @Override
    protected boolean isValidMeta(ItemStack stack, PotionMeta meta) {
        if (!super.isValidMeta(stack, meta)) {
            return false;
        }

        boolean checkNormal = getConfig().isPotionCheckingEnabled();
        boolean checkSplash = getConfig().isSplashPotionCheckingEnabled();
        boolean banSplash = getConfig().isSplashPotionsBanned();

        if (!checkNormal && !checkSplash && !banSplash) {
            return true;
        }

        short data = stack.getDurability();
        boolean isSplashPotion = (data & 0x4000) != 0;

        if (isSplashPotion) {
            if (banSplash) {
                onInvalid("is splash potion");
                return false;
            } else if (!checkSplash) {
                return true;
            }
        } else if (!checkNormal) {
            return true;
        }

        if (meta.hasCustomEffects()) {
            onInvalid("%s potion has custom effects", isSplashPotion ? "splash" : "normal");
            return false;
        }

        return true;
    }
}