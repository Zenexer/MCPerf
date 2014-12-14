package com.earth2me.mcperf;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.bukkit.Server;
import org.bukkit.block.BlockState;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
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

	private void onInvalid(String property, String sender)
	{
		logger.warning(String.format("Found item stack with invalid %s for %s", property, sender == null ? "(unknown)" : sender));
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

	private boolean isValidUnsafe(ItemStack stack, String sender)
	{
		if (stack == null)
		{
			return true;
		}

		if (stack.getAmount() < 1 || stack.getAmount() > stack.getMaxStackSize())
		{
			onInvalid("amount", sender);
			return false;
		}

		if (stack.getDurability() < 0 || stack.getDurability() > stack.getType().getMaxDurability())
		{
			onInvalid("durability", sender);
			return false;
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
						onInvalid("enchantment level", sender);
						return false;
					}

					for (Enchantment e : stack.getEnchantments().keySet())
					{
						if (!enchantment.equals(e) && enchantment.conflictsWith(e))
						{
							onInvalid("enchantment combination", sender);
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
					onInvalid("display name length", sender);
					return false;
				}

				if (!isValid(name))
				{
					onInvalid("display name text", sender);
					return false;
				}
			}

			if (meta.hasLore())
			{
				List<String> lore = meta.getLore();

				if (lore.size() > getMaxLoreLines())
				{
					onInvalid("lore line count", sender);
					return false;
				}

				for (String line : lore)
				{
					if (line.length() > getMaxLoreLineLength())
					{
						onInvalid("lore line length", sender);
						return false;
					}

					if (!isValid(line))
					{
						onInvalid("lore text", sender);
						return false;
					}
				}
			}
		}

		return true;
	}

	public static boolean isValid(String text)
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

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerDropItem(PlayerDropItemEvent event)
	{
		if (!event.isCancelled() && !isValid(event.getItemDrop().getItemStack(), event.getPlayer()))
		{
			event.setCancelled(true);
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
