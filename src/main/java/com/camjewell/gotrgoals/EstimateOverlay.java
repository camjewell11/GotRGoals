package com.camjewell.gotrgoals;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
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
 * wrapping; the supporting detail (searches needed, how many games the averages cover, the
 * averages themselves) is in a hover tooltip instead, so the overlay is just the answers.
 */
class EstimateOverlay extends OverlayPanel
{
	private static final Color ASSUMED_COLOR = Color.LIGHT_GRAY;

	private final GotrGoalsPlugin plugin;
	private final GotrGoalsConfig config;
	private final Client client;
	private final TooltipManager tooltipManager;

	@Inject
	EstimateOverlay(GotrGoalsPlugin plugin, GotrGoalsConfig config, Client client, TooltipManager tooltipManager)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		this.client = client;
		this.tooltipManager = tooltipManager;

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

		Dimension dimension = super.render(graphics);

		// getBounds() is where the overlay was last drawn, which is where the mouse is pointing.
		net.runelite.api.Point mouse = client.getMouseCanvasPosition();
		if (mouse != null && getBounds().contains(mouse.getX(), mouse.getY()))
		{
			tooltipManager.add(new Tooltip(basisTooltip(searchesNeeded)));
		}

		return dimension;
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
	 * Hover text saying what the estimates are based on, so a wild number from a single unusual
	 * game reads as such.
	 */
	private String basisTooltip(Integer searchesNeeded)
	{
		List<String> lines = new ArrayList<>();
		if (searchesNeeded != null)
		{
			lines.add("Searches needed: " + QuantityFormatter.formatNumber(searchesNeeded));
			lines.add(String.format("Pearls / search: %.2f", plugin.getPearlsPerSearch()));
		}

		int games = plugin.getHistory().countInWindow(config.gamesWindow());
		if (games == 0)
		{
			boolean assumed = config.assumedXpPerGame() > 0 || config.assumedPointsPerGame() > 0;
			lines.add(assumed ? "Using assumed rates (gray) until a game is recorded" : "No games recorded yet");
			return String.join("</br>", lines);
		}

		lines.add("Averaged over: " + games + (games == 1 ? " game" : " games"));
		if (plugin.getXpPerGame() != null && plugin.isXpPerGameObserved())
		{
			lines.add("XP / game: " + QuantityFormatter.quantityToStackSize(Math.round(plugin.getXpPerGame())));
		}
		if (plugin.getElementalPerGame() != null && plugin.getCatalyticPerGame() != null)
		{
			lines.add(String.format("Points / game: %.1f elemental, %.1f catalytic",
				plugin.getElementalPerGame(), plugin.getCatalyticPerGame()));
		}
		if (plugin.getMinutesPerGame() != null)
		{
			lines.add(String.format("Time / game: %.1f min", plugin.getMinutesPerGame()));
		}
		return String.join("</br>", lines);
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
}
