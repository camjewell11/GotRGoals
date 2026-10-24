package com.camjewell.gotrgoals;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Notification;
import net.runelite.client.config.Range;

import static com.camjewell.gotrgoals.GotrGoalsConfig.CONFIG_GROUP;

@ConfigGroup(CONFIG_GROUP)
public interface GotrGoalsConfig extends Config
{
	String CONFIG_GROUP = "gotrgoals";

	@ConfigSection(
		name = "Reward Tracking",
		description = "Tick the Temple Supplies rewards you're working toward. The overlay shows progress toward "
			+ "their combined Abyssal pearl cost.",
		position = 0
	)
	String REWARD_TRACKING = "RewardTracking";

	@ConfigItem(
		section = REWARD_TRACKING,
		keyName = "thresholdNotification",
		name = "Threshold notification",
		description = "Fires once when your pearls meet the combined cost of every selected reward",
		position = 0
	)
	default Notification thresholdNotification()
	{
		return Notification.OFF;
	}

	@ConfigItem(
		section = REWARD_TRACKING,
		keyName = "trackHatOfTheEye",
		name = "Hat of the Eye",
		description = "Include the Hat of the Eye (400 pearls) in the tracked total",
		position = 1
	)
	default boolean trackHatOfTheEye()
	{
		return false;
	}

	@ConfigItem(
		section = REWARD_TRACKING,
		keyName = "trackRobeTopOfTheEye",
		name = "Robe top of the Eye",
		description = "Include the Robe top of the Eye (350 pearls) in the tracked total",
		position = 2
	)
	default boolean trackRobeTopOfTheEye()
	{
		return false;
	}

	@ConfigItem(
		section = REWARD_TRACKING,
		keyName = "trackRobeBottomsOfTheEye",
		name = "Robe bottoms of the Eye",
		description = "Include the Robe bottoms of the Eye (350 pearls) in the tracked total",
		position = 3
	)
	default boolean trackRobeBottomsOfTheEye()
	{
		return false;
	}

	@ConfigItem(
		section = REWARD_TRACKING,
		keyName = "trackBootsOfTheEye",
		name = "Boots of the Eye",
		description = "Include the Boots of the Eye (250 pearls) in the tracked total",
		position = 4
	)
	default boolean trackBootsOfTheEye()
	{
		return false;
	}

	@ConfigItem(
		section = REWARD_TRACKING,
		keyName = "trackRingOfTheElements",
		name = "Ring of the elements",
		description = "Include the Ring of the elements (400 pearls) in the tracked total",
		position = 5
	)
	default boolean trackRingOfTheElements()
	{
		return false;
	}

	@ConfigItem(
		section = REWARD_TRACKING,
		keyName = "trackAbyssalNeedle",
		name = "Abyssal needle",
		description = "Include the Abyssal needle (750 pearls) in the tracked total",
		position = 6
	)
	default boolean trackAbyssalNeedle()
	{
		return false;
	}

	@ConfigItem(
		section = REWARD_TRACKING,
		keyName = "trackAbyssalLantern",
		name = "Abyssal lantern",
		description = "Include the Abyssal lantern (1,500 pearls) in the tracked total",
		position = 7
	)
	default boolean trackAbyssalLantern()
	{
		return false;
	}

	@ConfigItem(
		section = REWARD_TRACKING,
		keyName = "trackGuardiansEye",
		name = "Guardian's eye",
		description = "Include the Guardian's eye (3,000 pearls) in the tracked total",
		position = 8
	)
	default boolean trackGuardiansEye()
	{
		return false;
	}

	@ConfigSection(
		name = "Level Goal",
		description = "A Runecraft level (or exact XP) to estimate games remaining toward.",
		position = 1
	)
	String LEVEL_GOAL = "LevelGoal";

	@ConfigItem(
		section = LEVEL_GOAL,
		keyName = "trackLevelGoal",
		name = "Track level goal",
		description = "Show progress and games remaining toward the Runecraft target below",
		position = 0
	)
	default boolean trackLevelGoal()
	{
		return false;
	}

	@ConfigItem(
		section = LEVEL_GOAL,
		keyName = "showLevelOverlay",
		name = "Show level overlay",
		description = "Show the XP progress bar overlay. Turn off to keep the level goal only as a row in the "
			+ "estimates overlay.",
		position = 1
	)
	default boolean showLevelOverlay()
	{
		return true;
	}

	@ConfigItem(
		section = LEVEL_GOAL,
		keyName = "targetLevel",
		name = "Target level",
		description = "The Runecraft level you're working toward. 0 always targets your next level.",
		position = 2
	)
	@Range(min = 0, max = 99)
	default int targetLevel()
	{
		return 0;
	}

	@ConfigItem(
		section = LEVEL_GOAL,
		keyName = "targetXp",
		name = "Target XP (overrides level)",
		description = "An exact Runecraft XP target, e.g. for 200M. Leave at 0 to use Target level instead.",
		position = 3
	)
	@Range(min = 0, max = 200_000_000)
	default int targetXp()
	{
		return 0;
	}

	@ConfigSection(
		name = "Points",
		description = "Where your banked reward points and pearl count come from. Points are read from the end-of-game "
			+ "chat message and the Rewards Guardian's Check option; pearls from your inventory and bank. The fields "
			+ "below are a manual fallback, or a way to preview the overlay away from the minigame.",
		position = 2
	)
	String POINTS = "Points";

	@ConfigItem(
		section = POINTS,
		keyName = "useManualPoints",
		name = "Use manual totals",
		description = "When enabled, the fields below are used instead of what the plugin reads in-game",
		position = 0
	)
	default boolean useManualPoints()
	{
		return false;
	}

	@ConfigItem(
		section = POINTS,
		keyName = "manualElementalPoints",
		name = "Elemental points",
		description = "Your current banked elemental reward points",
		position = 1
	)
	@Range(min = 0, max = 65535)
	default int manualElementalPoints()
	{
		return 0;
	}

	@ConfigItem(
		section = POINTS,
		keyName = "manualCatalyticPoints",
		name = "Catalytic points",
		description = "Your current banked catalytic reward points",
		position = 2
	)
	@Range(min = 0, max = 65535)
	default int manualCatalyticPoints()
	{
		return 0;
	}

	@ConfigItem(
		section = POINTS,
		keyName = "manualPearls",
		name = "Abyssal pearls",
		description = "Your current Abyssal pearls (inventory + bank)",
		position = 3
	)
	@Range(min = 0, max = 100_000)
	default int manualPearls()
	{
		return 0;
	}

	@ConfigSection(
		name = "Overlay",
		description = "Overlay display options",
		position = 3
	)
	String OVERLAY = "Overlay";

	@ConfigItem(
		section = OVERLAY,
		keyName = "onlyShowAtGotr",
		name = "Only show at Guardians of the Rift",
		description = "Hide the overlays unless you're in the Temple of the Eye or in a game",
		position = 0
	)
	default boolean onlyShowAtGotr()
	{
		return true;
	}

	@ConfigItem(
		section = OVERLAY,
		keyName = "showPercentages",
		name = "Show percent complete",
		description = "Adds percent complete to each progress bar, alongside the remaining amount",
		position = 1
	)
	default boolean showPercentages()
	{
		return true;
	}

	@ConfigItem(
		section = OVERLAY,
		keyName = "showPointsLine",
		name = "Show banked points",
		description = "Shows your banked elemental/catalytic reward points under the pearl bar",
		position = 2
	)
	default boolean showPointsLine()
	{
		return true;
	}

	@ConfigSection(
		name = "Completion Estimates",
		description = "A second overlay estimating how many more games until your level and reward goals are met.",
		position = 4
	)
	String ESTIMATES = "Estimates";

	@ConfigItem(
		section = ESTIMATES,
		keyName = "showEstimatesOverlay",
		name = "Show estimates overlay",
		description = "Toggle the games/searches remaining overlay",
		position = 0
	)
	default boolean showEstimatesOverlay()
	{
		return false;
	}

	@ConfigItem(
		section = ESTIMATES,
		keyName = "gamesWindow",
		name = "Games to average",
		description = "How many of your most recent games the XP and points-per-game averages use. Fewer reacts "
			+ "faster to a change in how you play (e.g. switching to combination runes); more is steadier.",
		position = 1
	)
	@Range(min = 1, max = 50)
	default int gamesWindow()
	{
		return 10;
	}

	@ConfigItem(
		section = ESTIMATES,
		keyName = "ownAllPouches",
		name = "Own all essence pouches",
		description = "Pearls are slightly more common once you own every essence pouch (or a colossal pouch), "
			+ "since the pouch rolls drop out of the Rewards Guardian's table",
		position = 2
	)
	default boolean ownAllPouches()
	{
		return true;
	}

	@ConfigItem(
		section = ESTIMATES,
		keyName = "countTradeIns",
		name = "Count dye/lantern trade-ins",
		description = "Include the pearls you'd get selling Rewards Guardian dyes (50) and lanterns (100) back "
			+ "to Apprentice Felix in the expected pearls per search",
		position = 3
	)
	default boolean countTradeIns()
	{
		return false;
	}

	@ConfigItem(
		section = ESTIMATES,
		keyName = "showTimeEstimates",
		name = "Show time estimates",
		description = "Adds an estimated play time in parentheses next to each games-remaining count, based on "
			+ "how long your recent games took",
		position = 4
	)
	default boolean showTimeEstimates()
	{
		return true;
	}

	@ConfigItem(
		section = ESTIMATES,
		keyName = "assumedXpPerGame",
		name = "Assumed XP per game",
		description = "Used until you've finished a game with the plugin on. 0 shows nothing until then.",
		position = 5
	)
	@Range(min = 0, max = 200_000)
	default int assumedXpPerGame()
	{
		return 0;
	}

	@ConfigItem(
		section = ESTIMATES,
		keyName = "assumedPointsPerGame",
		name = "Assumed points per game",
		description = "Elemental and catalytic points each, used until you've finished a game with the plugin on. "
			+ "0 shows nothing until then.",
		position = 6
	)
	@Range(min = 0, max = 10)
	default int assumedPointsPerGame()
	{
		return 0;
	}
}
