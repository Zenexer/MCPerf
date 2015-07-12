package com.earth2me.mcperf.validity;

import org.bukkit.inventory.meta.ItemMeta;

public class GenericMetaValidator extends MetaValidator<ItemMeta>
{
	@Override
	public Class<ItemMeta> getMetaType()
	{
		return ItemMeta.class;
	}
}
