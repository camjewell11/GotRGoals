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
 * Values drawn from the configured assumptions rather than real games are shown in gray.
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
		panelComponent.setPreferredSize(new Dimension(250, 0));
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

		addRow("Estimated remaining", null, null, true);

		if (goal.isLevelGoalEnabled())
		{
			addLevelRow(goal);
		}
		if (showRewards)
		{
			addRewardRows(goal);
		}

		addBasisRow();

		return super.render(graphics);
	}

	private void addLevelRow(Goal goal)
	{
		String label = (goal.isTargetXpOverride()
			? QuantityFormatter.quantityToStackSize(goal.getTargetXp()) + " xp"
			: "Level " + goal.getTargetLevel()) + ":";

		int xpRemaining = goal.getXpRemaining();
		if (xpRemaining <= 0)
		{
			addRow(label, "Done", Color.GREEN, false);
			return;
		}

		Integer games = Goal.gamesForXp(xpRemaining, plugin.getXpPerGame());
		if (games == null)
		{
			addRow(label, "finish a game first", Color.GRAY, false);
			return;
		}

		addRow(label, formatGames(games), plugin.isXpPerGameObserved() ? Color.WHITE : ASSUMED_COLOR, false);
	}

	private void addRewardRows(Goal goal)
	{
		int pearlsRemaining = goal.getPearlsRemaining();
		if (pearlsRemaining <= 0)
		{
			addRow("Rewards:", "Done", Color.GREEN, false);
			return;
		}

		int searchesNeeded = Goal.searchesForPearls(pearlsRemaining, plugin.getPearlsPerSearch());
		String searchesText = QuantityFormatter.formatNumber(searchesNeeded);
		if (plugin.isPointsKnown())
		{
			searchesText += " (" + QuantityFormatter.formatNumber(plugin.getBankedSearches()) + " banked)";
		}
		addRow("Searches:", searchesText, Color.WHITE, false);

		// Without knowing what's banked, a games estimate would count every banked point as still
		// needing to be earned - better to say so than show a confidently wrong number.
		if (!plugin.isPointsKnown())
		{
			addRow("Reward games:", "check Rewards Guardian", Color.GRAY, false);
			return;
		}

		double haveElemental = plugin.getElementalPoints()
			+ plugin.getCurrentGameElementalEnergy() / Goal.ENERGY_PER_POINT;
		double haveCatalytic = plugin.getCatalyticPoints()
			+ plugin.getCurrentGameCatalyticEnergy() / Goal.ENERGY_PER_POINT;

		Integer games = Goal.gamesForSearches(searchesNeeded, haveElemental, haveCatalytic,
			plugin.getElementalPerGame(), plugin.getCatalyticPerGame());
		if (games == null)
		{
			addRow("Reward games:", "finish a game first", Color.GRAY, false);
			return;
		}
		if (games == 0)
		{
			addRow("Reward games:", "search your points", Color.GREEN, false);
			return;
		}

		addRow("Reward games:", formatGames(games), plugin.isPointsPerGameObserved() ? Color.WHITE : ASSUMED_COLOR, false);
	}

	/**
	 * A small footer saying what the estimates are based on, so a wild number from a single
	 * unusual game reads as such.
	 */
	private void addBasisRow()
	{
		int games = plugin.getHistory().countInWindow(config.gamesWindow());
		String text;
		if (games == 0)
		{
			text = config.assumedXpPerGame() > 0 || config.assumedPointsPerGame() > 0
				? "Using assumed rates until a game finishes"
				: "Waiting for a game to finish";
		}
		else
		{
			text = "Avg of last " + games + (games == 1 ? " game" : " games");
			if (plugin.getXpPerGame() != null && plugin.isXpPerGameObserved())
			{
				text += ": " + QuantityFormatter.quantityToStackSize(Math.round(plugin.getXpPerGame())) + " xp";
			}
			if (plugin.getElementalPerGame() != null && plugin.getCatalyticPerGame() != null)
			{
				text += ", " + String.format("%.1f/%.1f pts", plugin.getElementalPerGame(), plugin.getCatalyticPerGame());
			}
		}
		panelComponent.getChildren().add(LineComponent.builder()
			.left(text)
			.leftColor(Color.GRAY)
			.leftFont(FontManager.getRunescapeSmallFont())
			.build());
	}

	private String formatGames(int games)
	{
		String text = QuantityFormatter.formatNumber(games) + (games == 1 ? " game" : " games");
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
			return Math.max(1, (int) Math.ceil(minutes)) + " min";
		}
		return String.format("%.1f hr", minutes / 60.0);
	}

	private void addRow(String left, String right, Color rightColor, boolean header)
	{
		var line = LineComponent.builder()
			.left(left)
			.leftFont(header ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeFont());
		if (right != null)
		{
			line.right(right)
				.rightFont(FontManager.getRunescapeFont())
				.rightColor(rightColor);
		}
		panelComponent.getChildren().add(line.build());
	}
}
