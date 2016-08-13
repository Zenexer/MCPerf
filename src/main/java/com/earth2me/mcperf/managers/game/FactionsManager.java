package com.earth2me.mcperf.managers.game;

import com.earth2me.mcperf.annotation.ContainsConfig;
import com.earth2me.mcperf.config.ConfigSetting;
import com.earth2me.mcperf.managers.Manager;
import com.google.common.base.Objects;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.RegisteredListener;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@ContainsConfig
public class FactionsManager extends Manager {
    @ConfigSetting
    @Getter
    @Setter
    private boolean bucketStackingEnabled = false;
    @ConfigSetting
    @Getter
    @Setter
    private int bucketStackSize = 64;
    @ConfigSetting
    @Getter
    @Setter
    private EnumSet<Material> bannedAnvilResults;

    {
        bannedAnvilResults = EnumSet.of(
                Material.ENCHANTED_BOOK,
                Material.SUGAR,
                Material.RAW_BEEF,
                Material.EMERALD
        );
    }

    @SuppressWarnings("SpellCheckingInspection")
    public FactionsManager() {
        super("ur8lZmFjdGlvbnNP");
    }

    @EventHandler(priority = EventPriority.NORMAL)
    private void onPrepareAnvil(PrepareAnvilEvent event) {
        Set<Material> bannedAnvilResults = getBannedAnvilResults();

        if (bannedAnvilResults == null || bannedAnvilResults.isEmpty()) {
            return;
        }

        ItemStack result = event.getResult();

        if (result == null || !bannedAnvilResults.contains(result.getType())) {
            return;
        }

        event.setResult(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        // Check isCancelled just to be safe.
        if (event.isCancelled() || !isBucketStackingEnabled() || getBucketStackSize() <= 1 || event.getItemStack() == null || event.getItemStack().getAmount() <= 1) {
            return;
        }

        ItemStack source = event.getItemStack();
        event.setItemStack(source.clone());
        source.setAmount(source.getAmount() - 1);

        ItemStack emptyBucket = source.clone();
        emptyBucket.setType(Material.BUCKET);
        emptyBucket.setAmount(1);
        event.getPlayer().getInventory().addItem(emptyBucket);
    }

    private static boolean isBucket(Material material) {
        if (material == null) {
            return false;
        }

        switch (material) {
            case BUCKET:
            case LAVA_BUCKET:
            case WATER_BUCKET:
            case MILK_BUCKET:
                return true;
        }

        return !(material.isBlock() || material.getMaxStackSize() >= 64)
                && material.name().endsWith("_BUCKET");

    }

    // TODO: Disabled for now
    //@EventHandler(priority = EventPriority.HIGHEST)
    private void onInventoryClick(InventoryClickEvent event) {
        int bucketStackSize = getBucketStackSize();

        if (event.isCancelled() || !isBucketStackingEnabled() || bucketStackSize <= 1) {
            return;
        }

        ClickType click = event.getClick();
        ItemStack slot = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        if (slot == null || cursor == null || !slot.isSimilar(cursor)) {
            return;
        }

        switch (event.getClick()) {
            case CONTROL_DROP:
            case CREATIVE:
            case DROP:
            case WINDOW_BORDER_LEFT:
            case WINDOW_BORDER_RIGHT:
            case UNKNOWN:
                return;
        }

        Material material = slot.getType();
        if (!isBucket(material) || material.getMaxStackSize() <= bucketStackSize) {
            return;
        }

        // TODO: We need to handle ClickTypes/InventoryActions here for which slot or cursor might be null.

        int countSlot = slot.getAmount();
        int countCursor = cursor.getAmount();
        boolean canAutomateOne = countSlot + 1 <= material.getMaxStackSize();
        boolean canAutomateAll = countSlot + countCursor <= material.getMaxStackSize();
        boolean canPlaceOne = countSlot + 1 <= bucketStackSize;
        boolean canPlaceAll = countSlot + countCursor <= bucketStackSize;

        if (!canPlaceOne) {
            return;
        }

        InventoryAction action = event.getAction();
        boolean single;
        switch (action) {
            case SWAP_WITH_CURSOR:
            case NOTHING:
                switch (click) {
                    case LEFT:
                        action = canPlaceAll ? InventoryAction.PLACE_ALL : InventoryAction.PLACE_SOME;
                        single = countCursor == 1;
                        break;

                    case RIGHT:
                        action = InventoryAction.PLACE_ONE;
                        single = true;
                        break;

                    default:
                        return;  // TODO: Lol I haven't the slightest fucking clue.
                }
                break;

            default:
                return;  // TODO: ???
        }

        int add = single ? 1 : countCursor;
        int newCountSlot = countSlot + add;

        if (countSlot < 1 || countCursor < 1 || newCountSlot <= material.getMaxStackSize() || !isBucket(material)) {
            return;
        }

        newCountSlot = Math.min(newCountSlot, bucketStackSize);
        int newCountCursor = countCursor - newCountSlot;
        assert newCountCursor >= 0;

        InventoryAction newAction = single ? InventoryAction.PLACE_ONE : newCountCursor > 0 ? InventoryAction.PLACE_SOME : InventoryAction.PLACE_ALL;
        InventoryClickEvent subevent = new InventoryClickEvent(event.getView(), event.getSlotType(), event.getRawSlot(), event.getClick(), newAction, event.getHotbarButton());

        boolean pending = false;
        for (RegisteredListener listener : event.getHandlers().getRegisteredListeners()) {
            if (!pending) {
                if (listener.getListener() == this) {
                    pending = true;
                }
            } else {
                try {
                    listener.callEvent(subevent);
                } catch (Throwable ex) {
                    // Well fuck.
                    Plugin plugin = listener.getPlugin();
                    Logger logger;
                    String name;
                    String developer;
                    if (plugin == null) {
                        logger = getLogger();
                        name = "IHaveNoIdeaBecauseShitHitTheFanButQuitePossiblySpigot";
                        developer = "WhenInDoubtBlame_md_5";
                    } else {
                        logger = Objects.firstNonNull(plugin.getLogger(), getLogger());
                        name = plugin.getName();
                        PluginDescriptionFile desc = plugin.getDescription();
                        List<String> authors = desc == null ? null : desc.getAuthors();
                        developer = authors == null || authors.isEmpty() ? "NotTheSlightestClue" : String.join(" + ", authors);
                    }

                    logger.log(Level.SEVERE, String.format("Some lousy plugin, %s by %s, just shit bricks while handling an InventoryClickEvent.  Fix that shit.  Love, Zenexer.", name, developer), ex);
                }
            }
        }

        if (subevent.isCancelled()) {
            return;
        }

        slot.setAmount(newCountSlot);
        cursor.setAmount(newCountCursor);
        if (newCountCursor <= 0) {
            event.getView().setCursor(null);
        }
    }
}
