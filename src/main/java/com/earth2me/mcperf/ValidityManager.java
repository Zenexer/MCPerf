package com.earth2me.mcperf;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.BlockState;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@RequiredArgsConstructor
public final class ValidityManager implements Listener
{
	private final Server server;
	private final Logger logger;

	@Getter
	@Setter
	private int maxLoreLines = 5;
	@Getter
	@Setter
	private int maxLoreLineLength = 64;
	@Getter
	@Setter
	private int maxNameLength = 64;
	@Getter
	@Setter
	private boolean fullUnicodeAllowed = false;

	private void onInvalid(String property, String sender, ItemStack itemStack)
	{
		logger.warning(String.format("Found item stack %s:%d x%d with invalid %s for %s", itemStack.getType().toString(), itemStack.getDurability(), itemStack.getAmount(), property, sender == null ? "(unknown)" : sender));
	}

	public boolean isValid(ItemStack stack, HumanEntity sender)
	{
		return isValid(stack, sender == null ? null : sender.getName());
	}

	public boolean isValid(ItemStack stack, String sender)
	{
		try
		{
			return isValidUnsafe(stack, sender);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return true;
		}
	}

	@SuppressWarnings("RedundantIfStatement")
	private boolean isValidDurability(Material material, short durability)
	{
		if (durability == 0 || material == null)
		{
			return true;
		}

		if (durability < 0)
		{
			return false;
		}

		switch (material)
		{
			case POTION:
				return true;
		}

		if (durability <= getMaxDurability(material))
		{
			return true;
		}

		return false;
	}

	private static short getMaxDurability(Material material)
	{
		if (material.isBlock())
		{
			return 15;
		}

		switch (material)
		{
			case SKULL_ITEM:
				return (short)Math.max(material.getMaxDurability(), 4);

			case FLOWER_POT_ITEM:
				return 13;

			case CAULDRON_ITEM:
				return 3;

			case BREWING_STAND_ITEM:
				return 7;
		}

		return material.getMaxDurability();
	}

	private boolean isValidUnsafe(ItemStack stack, String sender)
	{
		if (stack == null)
		{
			return true;
		}

		if (stack.getType() != Material.AIR)
		{
			if (stack.getAmount() < 1 || stack.getAmount() > stack.getMaxStackSize())
			{
				onInvalid("amount", sender, stack);
				return false;
			}

			if (!isValidDurability(stack.getType(), stack.getDurability()))
			{
				onInvalid("durability", sender, stack);
				return false;
			}
		}

		if (stack.hasItemMeta())
		{
			ItemMeta meta = stack.getItemMeta();

			if (meta.hasEnchants())
			{
				for (Map.Entry<Enchantment, Integer> kv : stack.getEnchantments().entrySet())
				{
					Enchantment enchantment = kv.getKey();
					int level = kv.getValue();

					if (level < enchantment.getStartLevel() || level > enchantment.getMaxLevel())
					{
						onInvalid("enchantment level", sender, stack);
						return false;
					}

					for (Enchantment e : stack.getEnchantments().keySet())
					{
						if (!enchantment.equals(e) && enchantment.conflictsWith(e))
						{
							onInvalid("enchantment combination", sender, stack);
							return false;
						}
					}
				}
			}

			if (meta.hasDisplayName())
			{
				String name = meta.getDisplayName();

				if (name.length() > getMaxNameLength() || name.isEmpty())
				{
					onInvalid("display name length", sender, stack);
					return false;
				}

				if (!isValid(name))
				{
					onInvalid("display name text", sender, stack);
					return false;
				}
			}

			if (meta.hasLore())
			{
				List<String> lore = meta.getLore();

				if (lore.size() > getMaxLoreLines())
				{
					onInvalid("lore line count", sender, stack);
					return false;
				}

				for (String line : lore)
				{
					if (line.length() > getMaxLoreLineLength())
					{
						onInvalid("lore line length", sender, stack);
						return false;
					}

					if (!isValid(line))
					{
						onInvalid("lore text", sender, stack);
						return false;
					}
				}
			}
		}

		return true;
	}

	public boolean isValid(String text)
	{
		if (text == null || text.isEmpty())
		{
			return true;
		}

		if (text.contains("§k"))
		{
			return false;
		}

		for (char c : text.toCharArray())
		{
			if (c < ' ')
			{
				// Control character
				return false;
			}

			if (c < 0x7F || c == '§')
			{
				// Skip Unicode checks
				continue;
			}

			// Process as UTF-16

			if (!isFullUnicodeAllowed())
			{
				return false;
			}

			if (c >= 0xD800 && c <= 0xDBFF)
			{
				// Part of a code point outside the BMP
				return false;
			}

			// TODO: Additional Unicode checks
		}

		return true;
	}

	private void validate(Inventory inventory, HumanEntity sender)
	{
		try
		{
			for (ItemStack stack : inventory.getContents())
			{
				if (!isValid(stack, sender))
				{
					inventory.remove(stack);
				}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onInventoryOpen(InventoryOpenEvent event)
	{
		validate(event.getInventory(), event.getPlayer());
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onInventoryCreative(InventoryCreativeEvent event)
	{
		if (!isValid(event.getCursor(), event.getWhoClicked()))
		{
			try
			{
				event.setCursor(null);
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onPlayerDropItem(PlayerDropItemEvent event)
	{
		try
		{
			if (!event.isCancelled() && !isValid(event.getItemDrop().getItemStack(), event.getPlayer()))
			{
				event.setCancelled(true);
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onItemSpawn(ItemSpawnEvent event)
	{
		try
		{
			if (!event.isCancelled() && !isValid(event.getEntity().getItemStack(), (HumanEntity)null))
			{
				event.setCancelled(true);
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onPlayerLogin(PlayerLoginEvent event)
	{
		try
		{
			Player player = event.getPlayer();

			if (!isValid(player.getItemInHand(), player))
			{
				player.setItemInHand(null);
			}

			validate(player.getInventory(), player);
			validate(player.getEnderChest(), player);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onChunkLoad(ChunkLoadEvent event)
	{
		for (Entity entity : event.getChunk().getEntities())
		{
			if (entity.isEmpty())
			{
				continue;
			}

			if (entity instanceof Item)
			{
				Item item = (Item)entity;

				if (!isValid(item.getItemStack(), "[chunk load]"))
				{
					try
					{
						item.remove();
					}
					catch (Exception e)
					{
						e.printStackTrace();
					}
				}
			}
		}

		for (BlockState tile : event.getChunk().getTileEntities())
		{
			if (tile instanceof ItemFrame)
			{
				ItemFrame frame = (ItemFrame)tile;

				if (!isValid(frame.getItem(), "[chunk load]"))
				{
					try
					{
						frame.setItem(null);
					}
					catch (Exception e)
					{
						e.printStackTrace();
					}
				}
			}
		}
	}
}
