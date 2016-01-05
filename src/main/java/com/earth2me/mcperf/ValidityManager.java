package com.earth2me.mcperf;

import com.earth2me.mcperf.validity.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Server;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Stream;

@RequiredArgsConstructor
public final class ValidityManager implements Listener {
    @SuppressWarnings("UnusedDeclaration")
    private final Server server;
    private final Logger logger;

    @Getter
    private ValidityConfiguration config;

    private final Map<Class<? extends ItemMeta>, MetaValidator> metaValidators = new HashMap<>();
    private final Validator genericValidator = new GenericValidator();

    {
        registerMetaValidator(new PotionMetaValidator());
        registerMetaValidator(new EnchantmentMetaValidator());
        registerMetaValidator(new GenericMetaValidator());
    }

    private void registerMetaValidator(MetaValidator<? extends ItemMeta> validator) {
        metaValidators.put(validator.getMetaType(), validator);
    }

    public Stream<? extends Validator> getAllValidators() {
        Stream<? extends Validator> result = Stream.empty();

        result = Stream.concat(result, metaValidators.values().stream());
        result = Stream.concat(result, Stream.of(genericValidator));

        return result;
    }

    public void setConfig(final ValidityConfiguration config) {
        this.config = config;

        getAllValidators().forEach(v -> v.setConfig(config));
    }

    private void onInvalid(String propertyFormat, String sender, ItemStack itemStack, Object... propertyArgs) {
        logger.warning(String.format("Found item stack %s:%d x%d with invalid %s for %s", itemStack.getType().toString(), itemStack.getDurability(), itemStack.getAmount(), String.format(propertyFormat, propertyArgs), sender == null ? "(unknown)" : sender));
    }

    public boolean isValid(ItemStack stack, HumanEntity sender) {
        return isValid(stack, sender == null ? null : sender.getName());
    }

    public boolean isValid(ItemStack stack, String sender) {
        try {
            return isValidUnsafe(stack, sender);
        } catch (Exception e) {
            e.printStackTrace();
            return true;
        }
    }

    private boolean isValidUnsafe(final ItemStack stack, final String sender) {
        if (stack == null) {
            return true;
        }

        Validator validator = genericValidator;

        if (stack.hasItemMeta()) {
            validator = metaValidators.get(stack.getItemMeta().getClass());
            if (validator == null) {
                validator = metaValidators.get(ItemMeta.class);
            }
        }

        return validator.isValid(stack, reason -> onInvalid(reason, sender, stack));
    }

    private void validate(Inventory inventory, HumanEntity sender) {
        validate(inventory, sender == null ? null : sender.getName());
    }

    private void validate(Inventory inventory, String sender) {
        try {
            ItemStack[] contents = inventory.getContents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack stack = contents[i];

                if (!isValid(stack, sender)) {
                    inventory.clear(i);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isEnabled() {
        return getConfig().isEnabled();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!isEnabled()) {
            return;
        }

        validate(event.getInventory(), event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryCreative(InventoryCreativeEvent event) {
        if (!isEnabled()) {
            return;
        }

        if (!isValid(event.getCursor(), event.getWhoClicked())) {
            try {
                event.setCursor(null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (!isEnabled()) {
            return;
        }

        try {
            if (!event.isCancelled() && !isValid(event.getItemDrop().getItemStack(), event.getPlayer())) {
                event.setCancelled(true);
                event.getItemDrop().remove();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (!isEnabled()) {
            return;
        }

        try {
            if (!event.isCancelled() && !isValid(event.getEntity().getItemStack(), (HumanEntity) null)) {
                event.setCancelled(true);
                event.getEntity().remove();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (!isEnabled()) {
            return;
        }

        try {
            Player player = event.getPlayer();

            validate(player.getInventory(), player);
            validate(player.getEnderChest(), player);

            if (!isValid(player.getItemInHand(), player)) {
                player.setItemInHand(null);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!isEnabled()) {
            return;
        }

        for (Entity entity : event.getChunk().getEntities()) {
            if (entity.isEmpty()) {
                continue;
            }

            if (entity instanceof Item) {
                Item item = (Item) entity;

                if (!isValid(item.getItemStack(), "[chunk load]")) {
                    try {
                        item.remove();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        for (BlockState tile : event.getChunk().getTileEntities()) {
            if (tile instanceof ItemFrame) {
                ItemFrame frame = (ItemFrame) tile;

                if (!isValid(frame.getItem(), "[chunk load]")) {
                    try {
                        frame.setItem(null);
                        return;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            if (tile instanceof Chest) {
                Chest chest = (Chest) tile;
                // Only check the side of the chest that's relevant for this block so we don't check the chest twice.
                validate(chest.getBlockInventory(), "[chunk load]");
                return;
            } else if (tile instanceof InventoryHolder) {
                InventoryHolder holder = (InventoryHolder) tile;
                validate(holder.getInventory(), "[chunk load]");
                return;
            }
        }
    }
}
