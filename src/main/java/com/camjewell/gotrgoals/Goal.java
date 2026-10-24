package com.camjewell.gotrgoals;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Experience;

/**
 * Aggregates the ticked Temple Supplies rewards into one combined pearl target, tracks the
 * Runecraft level/XP target, and holds the pure math the estimate overlay uses to turn "pearls
 * or XP still needed" into "games still needed".
 *
 * The reward chain is pearls -> searches -> reward points -> games: shop items cost pearls,
 * pearls only come (randomly) from Rewards Guardian searches, each search costs one elemental and
 * one catalytic point, and points are awarded at the end of each game.
 */
class Goal
{
	/**
	 * Abyssal pearls roll as 14-16 (15 on average) on the Rewards Guardian's "other" table, at
	 * 18/140 per search - or 18/125 once the player owns every essence pouch (or a colossal
	 * pouch), since the pouch slots then drop out of the table. Per the OSRS Wiki's Rewards
	 * Guardian drop table, post-November 2024 changes.
	 */
	private static final double PEARLS_PER_HIT = 15.0;
	private static final double PEARL_RATE_WITH_POUCHES_TABLE = 18.0 / 140.0;
	private static final double PEARL_RATE_ALL_POUCHES_OWNED = 18.0 / 125.0;

	/**
	 * Expected extra pearls per search from selling the Rewards Guardian's dyes (1/400, 50 pearls
	 * each) and abyssal lanterns (1/700, 100 pearls each) back to Apprentice Felix.
	 */
	private static final double TRADE_IN_PEARLS_PER_SEARCH = 50.0 / 400.0 + 100.0 / 700.0;

	/**
	 * One reward point is awarded per 100 energy of that type at the end of a game (with the
	 * remainder as a percent chance of one more), so mid-game energy is worth energy/100 points.
	 */
	static final double ENERGY_PER_POINT = 100.0;

	private final List<RewardItem> selected = new ArrayList<>();
	private String displayName = "";
	private RewardItem representativeItem = RewardItem.NONE;

	private int pearlGoal = 0;
	private int pearlsOwned = 0;
	private boolean metThreshold = false;

	private boolean levelGoalEnabled = false;
	private int currentXp = 0;
	private int currentLevel = 1;
	private int targetXp = 0;
	private int targetLevel = 1;
	private boolean targetXpOverride = false;

	List<RewardItem> getSelected()
	{
		return selected;
	}

	String getDisplayName()
	{
		return displayName;
	}

	RewardItem getRepresentativeItem()
	{
		return representativeItem;
	}

	int getPearlGoal()
	{
		return pearlGoal;
	}

	int getPearlsOwned()
	{
		return pearlsOwned;
	}

	int getPearlsRemaining()
	{
		return Math.max(pearlGoal - pearlsOwned, 0);
	}

	double getPearlProgress()
	{
		return pearlGoal == 0 ? 1.0 : Math.min((double) pearlsOwned / pearlGoal, 1.0);
	}

	boolean isMetThreshold()
	{
		return metThreshold;
	}

	boolean isLevelGoalEnabled()
	{
		return levelGoalEnabled;
	}

	int getCurrentXp()
	{
		return currentXp;
	}

	int getCurrentLevel()
	{
		return currentLevel;
	}

	int getTargetXp()
	{
		return targetXp;
	}

	int getTargetLevel()
	{
		return targetLevel;
	}

	/**
	 * @return true if the target is an exact XP amount rather than a level
	 */
	boolean isTargetXpOverride()
	{
		return targetXpOverride;
	}

	int getXpRemaining()
	{
		return Math.max(targetXp - currentXp, 0);
	}

	double getXpProgress()
	{
		return targetXp == 0 ? 1.0 : Math.min((double) currentXp / targetXp, 1.0);
	}

	void recalculate(GotrGoalsConfig config, int pearlsOwned, int currentXp)
	{
		selected.clear();
		if (config.trackHatOfTheEye())
		{
			selected.add(RewardItem.HAT_OF_THE_EYE);
		}
		if (config.trackRobeTopOfTheEye())
		{
			selected.add(RewardItem.ROBE_TOP_OF_THE_EYE);
		}
		if (config.trackRobeBottomsOfTheEye())
		{
			selected.add(RewardItem.ROBE_BOTTOMS_OF_THE_EYE);
		}
		if (config.trackBootsOfTheEye())
		{
			selected.add(RewardItem.BOOTS_OF_THE_EYE);
		}
		if (config.trackRingOfTheElements())
		{
			selected.add(RewardItem.RING_OF_THE_ELEMENTS);
		}
		if (config.trackAbyssalNeedle())
		{
			selected.add(RewardItem.ABYSSAL_NEEDLE);
		}
		if (config.trackAbyssalLantern())
		{
			selected.add(RewardItem.ABYSSAL_LANTERN);
		}
		if (config.trackGuardiansEye())
		{
			selected.add(RewardItem.GUARDIANS_EYE);
		}

		if (selected.isEmpty())
		{
			displayName = "";
			representativeItem = RewardItem.NONE;
		}
		else if (selected.size() == 1)
		{
			displayName = selected.get(0).itemName();
			representativeItem = selected.get(0);
		}
		else
		{
			displayName = "Selected rewards (" + selected.size() + ")";
			representativeItem = selected.get(0);
		}

		int goal = 0;
		for (RewardItem item : selected)
		{
			goal += item.pearlCost();
		}
		this.pearlGoal = goal;
		this.pearlsOwned = pearlsOwned;
		this.metThreshold = !selected.isEmpty() && pearlsOwned >= pearlGoal;

		this.levelGoalEnabled = config.trackLevelGoal();
		this.currentXp = currentXp;
		// getLevelForXp() goes up to virtual level 126; only real levels are meaningful here.
		this.currentLevel = Math.min(Experience.getLevelForXp(currentXp), Experience.MAX_REAL_LEVEL);
		this.targetXpOverride = config.targetXp() > 0;
		if (targetXpOverride)
		{
			this.targetXp = Math.min(config.targetXp(), Experience.MAX_SKILL_XP);
			this.targetLevel = Math.min(Experience.getLevelForXp(this.targetXp), Experience.MAX_REAL_LEVEL);
		}
		else
		{
			this.targetLevel = Math.max(2, Math.min(config.targetLevel(), Experience.MAX_REAL_LEVEL));
			this.targetXp = Experience.getXpForLevel(this.targetLevel);
		}
	}

	/**
	 * @return expected Abyssal pearls per Rewards Guardian search
	 */
	static double pearlsPerSearch(boolean ownAllPouches, boolean countTradeIns)
	{
		double rate = PEARLS_PER_HIT * (ownAllPouches ? PEARL_RATE_ALL_POUCHES_OWNED : PEARL_RATE_WITH_POUCHES_TABLE);
		return countTradeIns ? rate + TRADE_IN_PEARLS_PER_SEARCH : rate;
	}

	/**
	 * @return expected searches (each costing one elemental and one catalytic point) to earn the
	 * given pearls
	 */
	static int searchesForPearls(int pearlsRemaining, double pearlsPerSearch)
	{
		if (pearlsRemaining <= 0 || pearlsPerSearch <= 0)
		{
			return 0;
		}
		// The epsilon keeps floating-point noise (e.g. 1350 / 2.16 = 625.0000000001) from rounding
		// an exact answer up by one.
		return (int) Math.ceil(pearlsRemaining / pearlsPerSearch - 1e-9);
	}

	/**
	 * Games still needed to have {@code searchesNeeded} of BOTH point types, since every search
	 * spends one of each - so whichever type is further behind (after what's banked and what's
	 * already been earned this game) is the bottleneck.
	 *
	 * @param haveElemental banked elemental points plus this game's energy so far / 100
	 * @param haveCatalytic banked catalytic points plus this game's energy so far / 100
	 * @param avgElemental  elemental points earned per game, or null if unknown
	 * @param avgCatalytic  catalytic points earned per game, or null if unknown
	 * @return games remaining (0 if enough points are already banked), or null if a type is still
	 * short and there's no positive per-game rate for it to estimate from
	 */
	static Integer gamesForSearches(int searchesNeeded, double haveElemental, double haveCatalytic,
		Double avgElemental, Double avgCatalytic)
	{
		Double elementalGames = gamesForDeficit(searchesNeeded - haveElemental, avgElemental);
		Double catalyticGames = gamesForDeficit(searchesNeeded - haveCatalytic, avgCatalytic);
		if (elementalGames == null || catalyticGames == null)
		{
			return null;
		}
		return (int) Math.ceil(Math.max(elementalGames, catalyticGames));
	}

	/**
	 * @return games to gain {@code xpRemaining}, 0 if already there, or null without a rate
	 */
	static Integer gamesForXp(int xpRemaining, Double avgXpPerGame)
	{
		Double games = gamesForDeficit(xpRemaining, avgXpPerGame);
		return games == null ? null : (int) Math.ceil(games);
	}

	private static Double gamesForDeficit(double deficit, Double perGame)
	{
		if (deficit <= 0)
		{
			return 0.0;
		}
		if (perGame == null || perGame <= 0)
		{
			return null;
		}
		return deficit / perGame;
	}
}
