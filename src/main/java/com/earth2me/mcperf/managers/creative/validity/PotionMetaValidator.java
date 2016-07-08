package com.earth2me.mcperf.managers.creative.validity;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;

public class PotionMetaValidator extends MetaValidator<PotionMeta> {
    @Override
    public Class<PotionMeta> getMetaType() {
        return PotionMeta.class;
    }

    @Override
    public boolean isApplicable(ItemMeta meta) {
        return meta instanceof PotionMeta;
    }

    @Override
    protected boolean isValidMeta(ItemStack stack, PotionMeta meta, boolean strict) {
        boolean checkNormal = getConfig().isPotionCheckingEnabled();
        boolean checkSplash = getConfig().isSplashPotionCheckingEnabled();
        boolean checkLingering = getConfig().isLingeringPotionCheckingEnabled();
        boolean banSplash = getConfig().isSplashPotionsBanned();
        boolean banLingering = getConfig().isLingeringPotionsBanned();

        if (!checkNormal && !checkSplash && !banSplash && !checkLingering && !banLingering) {
            return true;
        }

        switch (stack.getType()) {
            case POTION:
                if (!checkNormal) {
                    return true;
                }
                break;

            case SPLASH_POTION:
                if (banSplash) {
                    onInvalid("banned splash potion");
                    return false;
                } else if (!checkSplash) {
                    return true;
                }
                break;

            case LINGERING_POTION:
                if (banLingering) {
                    onInvalid("banned lingering potion");
                    return false;
                } else if (!checkLingering) {
                    return true;
                }
                break;
        }

        if (meta.hasCustomEffects()) {
            onInvalid("potion with banned custom effects");
            return false;
        }

        return true;
    }
}