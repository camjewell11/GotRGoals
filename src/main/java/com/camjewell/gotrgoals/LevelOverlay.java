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
import net.runelite.client.util.QuantityFormatter;

/**
 * Runecraft XP progress toward the level goal, kept separate from the pearl overlay so each can be
 * placed (or left off) independently.
 */
class LevelOverlay extends OverlayPanel
{
	private static final Color RUNECRAFT_COLOR = Color.decode("#ffb74d");

	private final GotrGoalsPlugin plugin;
	private final GotrGoalsConfig config;

	@Inject
	LevelOverlay(GotrGoalsPlugin plugin, GotrGoalsConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;

		setPosition(OverlayPosition.TOP_RIGHT);
		setPriority(PRIORITY_MED);
		panelComponent.setPreferredSize(new Dimension(GoalOverlay.PREFERRED_WIDTH, 0));
		panelComponent.setBorder(new Rectangle(3, 3, 3, 3));
		panelComponent.setGap(new Point(0, 2));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Goal goal = plugin.getGoal();
		if (!goal.isLevelGoalEnabled())
		{
			return null;
		}
		if (config.onlyShowAtGotr() && !plugin.isAtGotr())
		{
			return null;
		}

		graphics.setFont(FontManager.getRunescapeSmallFont());

		String target = goal.isTargetXpOverride()
			? QuantityFormatter.quantityToStackSize(goal.getTargetXp()) + " xp"
			: String.valueOf(goal.getTargetLevel());
		panelComponent.getChildren().add(GoalOverlay.labelLine(
			"Level " + goal.getCurrentLevel() + " > " + target,
			QuantityFormatter.quantityToStackSize(goal.getCurrentXp())
				+ "/" + QuantityFormatter.quantityToStackSize(goal.getTargetXp())));

		var progressBar = GoalOverlay.newBar(RUNECRAFT_COLOR, goal.getXpProgress());
		progressBar.setCenterLabel(GoalOverlay.centerLabel(config.showPercentages(), goal.getXpProgress(), goal.getXpRemaining()));
		panelComponent.getChildren().add(progressBar);

		return super.render(graphics);
	}
}
