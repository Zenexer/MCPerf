package com.earth2me.mcperf.validity;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public abstract class MetaValidator<T extends ItemMeta> extends Validator
{
	public abstract Class<T> getMetaType();

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
