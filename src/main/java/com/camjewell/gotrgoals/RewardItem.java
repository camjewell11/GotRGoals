package com.camjewell.gotrgoals;

import net.runelite.api.gameval.ItemID;

/**
 * Every non-talisman item sold by Apprentice Felix's Temple Supplies shop, and its cost in
 * Abyssal pearls. Talismans are left out on purpose: at 10-100 pearls each they're a handful of
 * searches, not something worth tracking a goal toward.
 */
public enum RewardItem
{
	NONE("None", -1, 0),
	HAT_OF_THE_EYE("Hat of the Eye", ItemID.HAT_OF_THE_EYE, 400),
	ROBE_TOP_OF_THE_EYE("Robe top of the Eye", ItemID.ROBE_TOP_OF_THE_EYE, 350),
	ROBE_BOTTOMS_OF_THE_EYE("Robe bottoms of the Eye", ItemID.ROBE_BOTTOM_OF_THE_EYE, 350),
	BOOTS_OF_THE_EYE("Boots of the Eye", ItemID.BOOTS_OF_THE_EYE, 250),
	RING_OF_THE_ELEMENTS("Ring of the elements", ItemID.RING_OF_ELEMENTS_CHARGED, 400),
	ABYSSAL_NEEDLE("Abyssal needle", ItemID.ABYSSAL_NEEDLE, 750),
	ABYSSAL_LANTERN("Abyssal lantern", ItemID.ABYSSAL_LANTERN, 1500),
	GUARDIANS_EYE("Guardian's eye", ItemID.GUARDIANS_EYE, 3000);

	private final String itemName;
	private final int itemId;
	private final int pearlCost;

	RewardItem(String itemName, int itemId, int pearlCost)
	{
		this.itemName = itemName;
		this.itemId = itemId;
		this.pearlCost = pearlCost;
	}

	public String itemName()
	{
		return itemName;
	}

	public int itemId()
	{
		return itemId;
	}

	public int pearlCost()
	{
		return pearlCost;
	}
}
