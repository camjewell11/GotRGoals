package com.camjewell.gotrgoals;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.text.DecimalFormat;
import javax.inject.Inject;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import net.runelite.client.ui.overlay.components.ImageComponent;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.SplitComponent;
import net.runelite.client.util.QuantityFormatter;

class GoalOverlay extends OverlayPanel
{
	private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.0");
	private static final int BORDER_SIZE = 3;
	private static final int VERTICAL_GAP = 2;
	private static final int ICON_GAP = 5;
	private static final Rectangle TOP_PANEL_BORDER = new Rectangle(2, 0, 4, 4);
	private static final Color PROGRESS_BAR_BACKGROUND_COLOR = new Color(61, 56, 49);
	private static final Color RUNECRAFT_COLOR = Color.decode("#ffb74d");
	private static final Color PEARL_COLOR = Color.decode("#4dd0e1");

	private final GotrGoalsPlugin plugin;
	private final GotrGoalsConfig config;
	private final ItemManager itemManager;

	private final PanelComponent topPanel = new PanelComponent();

	private BufferedImage cachedRewardIcon;
	private RewardItem cachedRewardItem;

	@Inject
	GoalOverlay(GotrGoalsPlugin plugin, GotrGoalsConfig config, ItemManager itemManager)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;

		setPosition(OverlayPosition.TOP_RIGHT);
		setPriority(PRIORITY_MED);
		panelComponent.setPreferredSize(new Dimension(250, 0));
		panelComponent.setBorder(new Rectangle(BORDER_SIZE, BORDER_SIZE, BORDER_SIZE, BORDER_SIZE));
		panelComponent.setGap(new Point(0, VERTICAL_GAP));

		topPanel.setBorder(TOP_PANEL_BORDER);
		topPanel.setBackgroundColor(null);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
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

		if (goal.isLevelGoalEnabled())
		{
			addLevelBar(goal);
		}

		if (showRewards)
		{
			addRewardHeader(goal);
			addPearlBar(goal);
			addFooter();
		}

		return super.render(graphics);
	}

	private void addLevelBar(Goal goal)
	{
		var progressBar = new OutlinedProgressBarComponent();
		progressBar.setForegroundColor(RUNECRAFT_COLOR);
		progressBar.setBackgroundColor(PROGRESS_BAR_BACKGROUND_COLOR);
		progressBar.setPercentage(goal.getXpProgress());

		String target = goal.isTargetXpOverride()
			? QuantityFormatter.quantityToStackSize(goal.getTargetXp()) + " xp"
			: "Lvl " + goal.getTargetLevel();
		progressBar.setLeftLabel("RC " + goal.getCurrentLevel() + " > " + target);
		progressBar.setRightLabel(QuantityFormatter.quantityToStackSize(goal.getCurrentXp())
			+ "/" + QuantityFormatter.quantityToStackSize(goal.getTargetXp()));
		progressBar.setCenterLabel(centerLabel(goal.getXpProgress(), goal.getXpRemaining()));

		panelComponent.getChildren().add(progressBar);
	}

	private void addRewardHeader(Goal goal)
	{
		topPanel.getChildren().clear();

		var topLine = LineComponent.builder()
			.left(goal.getDisplayName())
			.leftFont(FontManager.getRunescapeFont())
			.build();

		var bottomLine = LineComponent.builder()
			.left("Progress:")
			.leftFont(FontManager.getRunescapeFont())
			.right(DECIMAL_FORMAT.format(goal.getPearlProgress() * 100) + "%")
			.rightFont(FontManager.getRunescapeFont())
			.rightColor(goal.isMetThreshold() ? Color.GREEN : Color.WHITE)
			.build();

		var textSplit = SplitComponent.builder()
			.first(topLine)
			.second(bottomLine)
			.orientation(ComponentOrientation.VERTICAL)
			.build();

		var rewardImageComponent = new ImageComponent(getRewardImage(goal.getRepresentativeItem()));
		var topInfoSplit = SplitComponent.builder()
			.first(rewardImageComponent)
			.second(textSplit)
			.orientation(ComponentOrientation.HORIZONTAL)
			.gap(new Point(ICON_GAP, 0))
			.build();

		topPanel.getChildren().add(topInfoSplit);
		panelComponent.getChildren().add(topPanel);
	}

	private void addPearlBar(Goal goal)
	{
		var progressBar = new OutlinedProgressBarComponent();
		progressBar.setForegroundColor(PEARL_COLOR);
		progressBar.setBackgroundColor(PROGRESS_BAR_BACKGROUND_COLOR);
		progressBar.setPercentage(goal.getPearlProgress());
		progressBar.setLeftLabel("Pearls");
		progressBar.setRightLabel(QuantityFormatter.quantityToStackSize(goal.getPearlsOwned())
			+ "/" + QuantityFormatter.quantityToStackSize(goal.getPearlGoal()));

		// Banked reward points are pearls-in-waiting: tint the part of the remaining bar that
		// searching them would be expected to fill, the same way MTA Goals tints Alchemist gold
		// that's held but not yet deposited.
		if (plugin.isPointsKnown() && goal.getPearlGoal() > 0)
		{
			double expectedPearls = plugin.getBankedSearches() * plugin.getPearlsPerSearch();
			double pendingFraction = expectedPearls / goal.getPearlGoal();
			if (pendingFraction > 0)
			{
				progressBar.setPendingPercentage(pendingFraction);
				progressBar.setPendingColor(new Color(PEARL_COLOR.getRed(), PEARL_COLOR.getGreen(), PEARL_COLOR.getBlue(), 120));
			}
		}

		progressBar.setCenterLabel(centerLabel(goal.getPearlProgress(), goal.getPearlsRemaining()));
		panelComponent.getChildren().add(progressBar);
	}

	private void addFooter()
	{
		if (config.showPointsLine())
		{
			var pointsLine = LineComponent.builder()
				.left("Reward points:")
				.leftFont(FontManager.getRunescapeSmallFont());
			if (plugin.isPointsKnown())
			{
				pointsLine.right(QuantityFormatter.formatNumber(plugin.getElementalPoints())
					+ " / " + QuantityFormatter.formatNumber(plugin.getCatalyticPoints()))
					.rightFont(FontManager.getRunescapeSmallFont());
			}
			else
			{
				pointsLine.right("Check the Rewards Guardian")
					.rightColor(Color.GRAY)
					.rightFont(FontManager.getRunescapeSmallFont());
			}
			panelComponent.getChildren().add(pointsLine.build());
		}

		if (!plugin.isBankPearlsKnown())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Open your bank to count banked pearls")
				.leftColor(Color.GRAY)
				.leftFont(FontManager.getRunescapeSmallFont())
				.build());
		}
	}

	private String centerLabel(double progress, int remaining)
	{
		String remainingText = remaining > 0 ? "(" + QuantityFormatter.quantityToStackSize(remaining) + ")" : "";
		if (config.showPercentages())
		{
			String percentText = DECIMAL_FORMAT.format(progress * 100) + "%";
			return remainingText.isEmpty() ? percentText : percentText + " " + remainingText;
		}
		return remainingText;
	}

	private BufferedImage getRewardImage(RewardItem rewardItem)
	{
		if (cachedRewardItem != rewardItem)
		{
			cachedRewardItem = rewardItem;
			cachedRewardIcon = itemManager.getImage(rewardItem.itemId());
		}
		return cachedRewardIcon;
	}
}
