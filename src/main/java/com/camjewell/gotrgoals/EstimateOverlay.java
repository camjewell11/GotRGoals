package com.camjewell.gotrgoals;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.util.QuantityFormatter;

/**
 * Estimates how many more games until the level and reward goals are met, from rolling averages
 * over the player's last {@link GotrGoalsConfig#gamesWindow()} games:
 * <ul>
 *     <li>Level: XP still needed / average Runecraft XP per game.</li>
 *     <li>Reward: pearls still needed / expected pearls per search = searches needed; each search
 *     costs one elemental and one catalytic point, so games = the larger of each type's shortfall
 *     (after banked points and this game's energy so far) / that type's average points per game.</li>
 * </ul>
 * Values drawn from the configured assumptions rather than real games are shown in gray. Every
 * row is kept short (label + a number or two) so it fits the narrow default width without
 * wrapping.
 */
class EstimateOverlay extends OverlayPanel
{
	private static final Color ASSUMED_COLOR = Color.LIGHT_GRAY;

	private final GotrGoalsPlugin plugin;
	private final GotrGoalsConfig config;

	@Inject
	EstimateOverlay(GotrGoalsPlugin plugin, GotrGoalsConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;

		setPosition(OverlayPosition.TOP_RIGHT);
		setPriority(PRIORITY_LOW);
		panelComponent.setPreferredSize(new Dimension(GoalOverlay.PREFERRED_WIDTH, 0));
		panelComponent.setBorder(new Rectangle(3, 3, 3, 3));
		panelComponent.setGap(new Point(0, 2));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showEstimatesOverlay())
		{
			return null;
		}

		Goal goal = plugin.getGoal();
		boolean showRewards = !goal.getSelected().isEmpty();
		if (!showRewards && !goal.isLevelGoalEnabled())
		{
			return null;
		}
		if (config.onlyShowAtGotr() && !plugin.isAtGotr())
		{
			return null;
		}

		graphics.setFont(FontManager.getRunescapeSmallFont());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Games remaining")
			.leftFont(FontManager.getRunescapeBoldFont())
			.build());

		if (goal.isLevelGoalEnabled())
		{
			addLevelRow(goal);
		}
		Integer searchesNeeded = showRewards ? addRewardRow(goal) : null;

		addBasisRows(searchesNeeded);

		return super.render(graphics);
	}

	private void addLevelRow(Goal goal)
	{
		String label = goal.isTargetXpOverride()
			? QuantityFormatter.quantityToStackSize(goal.getTargetXp()) + " xp:"
			: "Level " + goal.getTargetLevel() + ":";

		int xpRemaining = goal.getXpRemaining();
		if (xpRemaining <= 0)
		{
			addRow(label, "Done", Color.GREEN);
			return;
		}

		Integer games = Goal.gamesForXp(xpRemaining, plugin.getXpPerGame());
		if (games == null)
		{
			addRow(label, "play a game", Color.GRAY);
			return;
		}

		addRow(label, formatGames(games), plugin.isXpPerGameObserved() ? Color.WHITE : ASSUMED_COLOR);
	}

	/**
	 * @return the searches still needed (shown in the basis rows), or null if the goal is met
	 */
	private Integer addRewardRow(Goal goal)
	{
		int pearlsRemaining = goal.getPearlsRemaining();
		if (pearlsRemaining <= 0)
		{
			addRow("Rewards:", "Done", Color.GREEN);
			return null;
		}

		int searchesNeeded = Goal.searchesForPearls(pearlsRemaining, plugin.getPearlsPerSearch());

		// Without knowing what's banked, a games estimate would count every banked point as still
		// needing to be earned - better to say so than show a confidently wrong number.
		if (!plugin.isPointsKnown())
		{
			addRow("Rewards:", "Check guardian", Color.GRAY);
			return searchesNeeded;
		}

		double haveElemental = plugin.getElementalPoints()
			+ plugin.getCurrentGameElementalEnergy() / Goal.ENERGY_PER_POINT;
		double haveCatalytic = plugin.getCatalyticPoints()
			+ plugin.getCurrentGameCatalyticEnergy() / Goal.ENERGY_PER_POINT;

		Integer games = Goal.gamesForSearches(searchesNeeded, haveElemental, haveCatalytic,
			plugin.getElementalPerGame(), plugin.getCatalyticPerGame());
		if (games == null)
		{
			addRow("Rewards:", "play a game", Color.GRAY);
		}
		else if (games == 0)
		{
			addRow("Rewards:", "Search now", Color.GREEN);
		}
		else
		{
			addRow("Rewards:", formatGames(games), plugin.isPointsPerGameObserved() ? Color.WHITE : ASSUMED_COLOR);
		}
		return searchesNeeded;
	}

	/**
	 * Small gray rows saying what the estimates are based on, so a wild number from a single
	 * unusual game reads as such.
	 */
	private void addBasisRows(Integer searchesNeeded)
	{
		if (searchesNeeded != null)
		{
			addSmallRow("Searches needed:", QuantityFormatter.formatNumber(searchesNeeded));
		}

		int games = plugin.getHistory().countInWindow(config.gamesWindow());
		if (games == 0)
		{
			boolean assumed = config.assumedXpPerGame() > 0 || config.assumedPointsPerGame() > 0;
			addSmallRow(assumed ? "Using assumed rates" : "No games recorded", null);
			return;
		}

		addSmallRow("Averaged over:", games + (games == 1 ? " game" : " games"));
		if (plugin.getXpPerGame() != null && plugin.isXpPerGameObserved())
		{
			addSmallRow("XP / game:", QuantityFormatter.quantityToStackSize(Math.round(plugin.getXpPerGame())));
		}
		if (plugin.getElementalPerGame() != null && plugin.getCatalyticPerGame() != null)
		{
			addSmallRow("Points / game:",
				String.format("%.1f / %.1f", plugin.getElementalPerGame(), plugin.getCatalyticPerGame()));
		}
	}

	private String formatGames(int games)
	{
		String text = QuantityFormatter.formatNumber(games);
		if (config.showTimeEstimates() && plugin.getMinutesPerGame() != null)
		{
			text += " (~" + formatMinutes(games * plugin.getMinutesPerGame()) + ")";
		}
		return text;
	}

	private static String formatMinutes(double minutes)
	{
		if (minutes < 60)
		{
			return Math.max(1, (int) Math.ceil(minutes)) + "m";
		}
		return String.format("%.1fh", minutes / 60.0);
	}

	private void addRow(String left, String right, Color rightColor)
	{
		panelComponent.getChildren().add(LineComponent.builder()
			.left(left)
			.leftFont(FontManager.getRunescapeFont())
			.right(right)
			.rightFont(FontManager.getRunescapeFont())
			.rightColor(rightColor)
			.build());
	}

	private void addSmallRow(String left, String right)
	{
		panelComponent.getChildren().add(LineComponent.builder()
			.left(left)
			.leftColor(Color.GRAY)
			.leftFont(FontManager.getRunescapeSmallFont())
			.right(right)
			.rightColor(Color.GRAY)
			.rightFont(FontManager.getRunescapeSmallFont())
			.build());
	}
}
