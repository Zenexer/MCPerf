package com.earth2me.mcperf.validity;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public abstract class MetaValidator<T extends ItemMeta> extends Validator
{
	private static Class<?> craftMetaItemClass;
	private static Field unhandledTagsField;

	public abstract Class<T> getMetaType();

	@SuppressWarnings("RedundantIfStatement")
	protected boolean isValidMeta(ItemStack stack, T meta)
	{
		if (meta.hasDisplayName())
		{
			String name = meta.getDisplayName();

			if (name.length() > getConfig().getMaxNameLength() || name.isEmpty())
			{
				onInvalid("display name length (%d)", name.length());
				return false;
			}

			if (!isValidText(name))
			{
				onInvalid("display name text");
				return false;
			}
		}

		if (meta.hasLore())
		{
			List<String> lore = meta.getLore();

			if (lore.size() > getConfig().getMaxLoreLines())
			{
				onInvalid("lore line count (%d)", lore.size());
				return false;
			}

			int i = 1;
			for (String line : lore)
			{
				if (line.length() > getConfig().getMaxLoreLineLength())
				{
					onInvalid("lore line length (%d, line %d)", line.length(), i);
					return false;
				}

				if (!isValidText(line))
				{
					onInvalid("lore text");
					return false;
				}

				i++;
			}
		}

		try
		{
			if (!isValidNBT(stack, meta))
			{
				return false;
			}
		}
		catch (Exception ex)
		{
			Bukkit.getLogger().severe("[MCPerf] isValidNBT threw an exception!\n" + ex.toString());
		}

		return true;
	}

	private boolean isValidNBT(ItemStack stack, T meta)
	{
		if (!prepareReflection(meta))
		{
			return true;  // Indeterminate
		}

		Map<String, Object> unhandledTags;
		try
		{
			@SuppressWarnings("unchecked")
			Map<String, Object> x = (Map<String, Object>)unhandledTagsField.get(meta);
			unhandledTags = x;
		}
		catch (IllegalAccessException e)
		{
			Bukkit.getLogger().severe("[MCPerf] Error reading unhandledTags field: " + e.getMessage());
			return true;  // Indeterminate
		}

		if (unhandledTags != null && !unhandledTags.isEmpty())
		{
			StringJoiner tagText = new StringJoiner(", ");
			unhandledTags.keySet().stream().forEach(tagText::add);
			Bukkit.getLogger().warning(String.format("[MCPerf] Detected suspicious tags: %s with tags %s", stack.getType().name(), tagText.toString()));

			if (unhandledTags.containsKey("www.wurst-client.tk"))
			{
				onInvalid("mod/cheat client (www.wurst-client.tk)");
				return false;
			}
		}

		return true;
	}

	private static <T extends ItemMeta> boolean prepareReflection(T meta)
	{
		if (craftMetaItemClass != null && unhandledTagsField != null)
		{
			// Sanity check
			if (!craftMetaItemClass.isInstance(meta))
			{
				Bukkit.getLogger().warning("[MCPerf] Expected subclass of " + craftMetaItemClass.getCanonicalName() + ", but got: " + meta.getClass().getCanonicalName());
				return false;
			}

			return true;
		}

		craftMetaItemClass = null;
		unhandledTagsField = null;

		for (Class<?> metaType = meta.getClass(); craftMetaItemClass == null; metaType = metaType.getSuperclass())
		{
			if (metaType == null)
			{
				Bukkit.getLogger().warning("[MCPerf] Unable to retrieve CraftMetaItem type from " + meta.getClass().getCanonicalName());
				return false;
			}

			switch (metaType.getSimpleName())
			{
				case "CraftMetaItem":
					craftMetaItemClass = metaType;
					break;
			}
		}

		try
		{
			unhandledTagsField = craftMetaItemClass.getDeclaredField("unhandledTags");
			unhandledTagsField.setAccessible(true);
		}
		catch (NoSuchFieldException ex)
		{
			Bukkit.getLogger().severe("[MCPerf] CraftMetaItem type is missing unhandledTags field");
			return false;
		}

		return true;
	}

	@SuppressWarnings("RedundantIfStatement")
	@Override
	public boolean isValid(ItemStack stack)
	{
		if (!super.isValid(stack))
		{
			return false;
		}

		if (!isValidMeta(stack, getMetaType().cast(stack.getItemMeta())))
		{
			return false;
		}

		return true;
	}
}
