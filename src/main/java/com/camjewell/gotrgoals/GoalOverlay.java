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

/**
 * Pearl progress toward the ticked Temple Supplies rewards. The level goal has its own
 * {@link LevelOverlay}, since the two goals are unrelated.
 *
 * Each bar's name and current/goal amounts sit on a text line above it, leaving only percent and
 * remaining on the bar itself - a bar has no room to wrap, so packing three labels into it
 * overprinted them at anything narrower than ~250px.
 */
class GoalOverlay extends OverlayPanel
{
	static final int PREFERRED_WIDTH = 150;

	private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.0");
	private static final int BORDER_SIZE = 3;
	private static final int VERTICAL_GAP = 2;
	private static final int ICON_GAP = 5;
	private static final Rectangle TOP_PANEL_BORDER = new Rectangle(2, 0, 4, 4);
	private static final Color PROGRESS_BAR_BACKGROUND_COLOR = new Color(61, 56, 49);
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
		panelComponent.setPreferredSize(new Dimension(PREFERRED_WIDTH, 0));
		panelComponent.setBorder(new Rectangle(BORDER_SIZE, BORDER_SIZE, BORDER_SIZE, BORDER_SIZE));
		panelComponent.setGap(new Point(0, VERTICAL_GAP));

		topPanel.setBorder(TOP_PANEL_BORDER);
		topPanel.setBackgroundColor(null);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Goal goal = plugin.getGoal();

		if (goal.getSelected().isEmpty())
		{
			return null;
		}
		if (config.onlyShowAtGotr() && !plugin.isAtGotr())
		{
			return null;
		}

		graphics.setFont(FontManager.getRunescapeSmallFont());

		addRewardHeader(goal);
		addPearlSection(goal);
		addFooter();

		return super.render(graphics);
	}

	private void addRewardHeader(Goal goal)
	{
		topPanel.getChildren().clear();

		String name = goal.getSelected().size() == 1
			? goal.getDisplayName()
			: goal.getSelected().size() + " rewards";
		var topLine = LineComponent.builder()
			.left(name)
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

	private void addPearlSection(Goal goal)
	{
		panelComponent.getChildren().add(labelLine("Pearls", QuantityFormatter.quantityToStackSize(goal.getPearlsOwned())
			+ "/" + QuantityFormatter.quantityToStackSize(goal.getPearlGoal())));

		var progressBar = newBar(PEARL_COLOR, goal.getPearlProgress());

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

		progressBar.setCenterLabel(centerLabel(config.showPercentages(), goal.getPearlProgress(), goal.getPearlsRemaining()));
		panelComponent.getChildren().add(progressBar);
	}

	private void addFooter()
	{
		if (config.showPointsLine())
		{
			if (plugin.isPointsKnown())
			{
				addSmallLine("Points:", QuantityFormatter.formatNumber(plugin.getElementalPoints())
					+ " / " + QuantityFormatter.formatNumber(plugin.getCatalyticPoints()), Color.WHITE);
			}
			else
			{
				addSmallLine("Points:", "Check guardian", Color.GRAY);
			}
		}

		if (!plugin.isBankPearlsKnown())
		{
			addSmallLine("Bank pearls:", "open bank", Color.GRAY);
		}
	}

	static OutlinedProgressBarComponent newBar(Color color, double progress)
	{
		var progressBar = new OutlinedProgressBarComponent();
		progressBar.setForegroundColor(color);
		progressBar.setBackgroundColor(PROGRESS_BAR_BACKGROUND_COLOR);
		progressBar.setPercentage(progress);
		return progressBar;
	}

	/**
	 * The text line that sits above a bar: its name on the left, current/goal on the right.
	 */
	static LineComponent labelLine(String left, String right)
	{
		return LineComponent.builder()
			.left(left)
			.leftFont(FontManager.getRunescapeSmallFont())
			.right(right)
			.rightFont(FontManager.getRunescapeSmallFont())
			.build();
	}

	private void addSmallLine(String left, String right, Color color)
	{
		panelComponent.getChildren().add(LineComponent.builder()
			.left(left)
			.leftFont(FontManager.getRunescapeSmallFont())
			.right(right)
			.rightColor(color)
			.rightFont(FontManager.getRunescapeSmallFont())
			.build());
	}

	static String centerLabel(boolean showPercentage, double progress, int remaining)
	{
		String remainingText = remaining > 0 ? "(" + QuantityFormatter.quantityToStackSize(remaining) + ")" : "";
		if (showPercentage)
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
