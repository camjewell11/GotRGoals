package com.camjewell.gotrgoals;

import com.google.inject.Provides;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

/**
 * Tracks progress toward a Runecraft level goal and toward Temple Supplies rewards, and estimates
 * how many more Guardians of the Rift games each will take.
 */
@Slf4j
@PluginDescriptor(
	name = "Guardians of the Rift Goals",
	description = "Predicts how many Guardians of the Rift games until a Runecraft level or a Temple Supplies reward",
	tags = {"gotr", "guardians", "rift", "runecraft", "minigame", "overlay", "pearls"}
)
public class GotrGoalsPlugin extends Plugin
{
	/**
	 * The Temple of the Eye, where the lobby, Great Guardian and Rewards Guardian all are. The
	 * runic altars visited mid-game are elsewhere, so {@link InterfaceID.GotrHud} presence is
	 * checked as well.
	 */
	private static final int TEMPLE_REGION = 14484;

	private static final int TICKS_PER_MINUTE = 100;

	/**
	 * How long after a game's end message to wait before recording it, so the end-of-game XP
	 * bonus and the "energy attuned" message (whichever order they arrive in, relative to the
	 * end message) are both captured in the same sample.
	 */
	private static final int FINALIZE_DELAY_TICKS = 5;

	/**
	 * Back-to-back games are timed end-to-end (so the wait in the lobby counts toward time per
	 * game); a gap longer than this means the player stepped away, so that game is timed from its
	 * own start instead. A game plus the lobby wait is ~10-12 minutes.
	 */
	private static final int MAX_BACK_TO_BACK_TICKS = 20 * TICKS_PER_MINUTE;

	private static final String GAME_START_MESSAGE = "The rift becomes active!";
	private static final String[] GAME_END_MESSAGES = {
		"The Great Guardian successfully closed the rift",
		"The rift has been subdued",
	};
	private static final String LOOT_MESSAGE = "You found some loot:";

	/**
	 * Matched against tag-stripped text. The game colours the numbers, so the raw message reads
	 * e.g. "Elemental energy attuned: &lt;col=ef1020&gt;5&lt;/col&gt;. Catalytic energy attuned: ...".
	 */
	private static final Pattern ATTUNED_PATTERN = Pattern.compile(
		"Elemental energy attuned:\\s*([\\d,]+).*Catalytic energy attuned:\\s*([\\d,]+)");
	private static final Pattern TOTAL_PATTERN = Pattern.compile(
		"Total elemental energy:\\s*([\\d,]+).*Total catalytic energy:\\s*([\\d,]+)");

	/**
	 * The Rewards Guardian's "Check" dialog. Note catalytic comes first here, unlike every chat
	 * message.
	 */
	private static final Pattern CHECK_PATTERN = Pattern.compile(
		"You have\\s+([\\d,]+)\\s+catalytic energy and\\s+([\\d,]+)\\s+elemental energy");

	private static final String KEY_ELEMENTAL = "bankedElemental";
	private static final String KEY_CATALYTIC = "bankedCatalytic";
	private static final String KEY_BANK_PEARLS = "bankPearls";
	private static final String KEY_HISTORY = "gameHistory";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private GotrGoalsConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private Notifier notifier;

	@Inject
	private LevelOverlay levelOverlay;

	@Inject
	private GoalOverlay goalOverlay;

	@Inject
	private EstimateOverlay estimateOverlay;

	private final Goal goal = new Goal();
	private final GameHistory history = new GameHistory();

	private int elementalPoints = 0;
	private int catalyticPoints = 0;
	private boolean pointsKnown = false;

	private int inventoryPearls = 0;
	private int bankPearls = -1;

	private int tickCounter = 0;
	private boolean gameActive = false;
	private int gameStartTick = -1;
	private int gameStartXp = -1;
	private int lastGameEndTick = -1;
	private int finalizeAtTick = -1;
	private int pendingEndTick = -1;
	private int pendingElemental = 0;
	private int pendingCatalytic = 0;

	private boolean previouslyMetThreshold = false;

	private Double xpPerGame;
	private boolean xpPerGameObserved;
	private Double elementalPerGame;
	private Double catalyticPerGame;
	private boolean pointsPerGameObserved;
	private Double minutesPerGame;

	@Provides
	GotrGoalsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GotrGoalsConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(levelOverlay);
		overlayManager.add(goalOverlay);
		overlayManager.add(estimateOverlay);
		resetGameTracking();
		previouslyMetThreshold = false;
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				loadProfile();
				inventoryPearls = countPearls(client.getItemContainer(InventoryID.INV));
			}
			refresh();
		});
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(levelOverlay);
		overlayManager.remove(goalOverlay);
		overlayManager.remove(estimateOverlay);
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		loadProfile();
		refresh();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			// A game in progress can't be finished after logging out or hopping, so drop it
			// rather than let its start XP get paired with some later game's end.
			resetGameTracking();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		tickCounter++;
		if (finalizeAtTick >= 0 && tickCounter >= finalizeAtTick)
		{
			finalizeGame();
		}
		if (isAtGotr())
		{
			readCheckDialog();
		}
		refresh();
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() == ChatMessageType.SPAM)
		{
			// Each search spends one of each point. "Big-search" sends one of these per roll.
			if (pointsKnown && isAtGotr() && event.getMessage().startsWith(LOOT_MESSAGE))
			{
				setBankedPoints(Math.max(elementalPoints - 1, 0), Math.max(catalyticPoints - 1, 0));
			}
			return;
		}
		if (event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		String message = Text.removeTags(event.getMessage());

		if (message.contains(GAME_START_MESSAGE))
		{
			gameActive = true;
			gameStartTick = tickCounter;
			gameStartXp = client.getSkillExperience(Skill.RUNECRAFT);
			return;
		}

		for (String endMessage : GAME_END_MESSAGES)
		{
			if (message.contains(endMessage))
			{
				onGameEnded();
				return;
			}
		}

		Matcher attuned = ATTUNED_PATTERN.matcher(message);
		if (attuned.find())
		{
			onGameEnded();
			pendingElemental = parseNumber(attuned.group(1));
			pendingCatalytic = parseNumber(attuned.group(2));
			return;
		}

		Matcher total = TOTAL_PATTERN.matcher(message);
		if (total.find())
		{
			setBankedPoints(parseNumber(total.group(1)), parseNumber(total.group(2)));
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.INV)
		{
			inventoryPearls = countPearls(event.getItemContainer());
		}
		else if (event.getContainerId() == InventoryID.BANK)
		{
			int pearls = countPearls(event.getItemContainer());
			if (pearls != bankPearls)
			{
				bankPearls = pearls;
				configManager.setRSProfileConfiguration(GotrGoalsConfig.CONFIG_GROUP, KEY_BANK_PEARLS, pearls);
			}
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals(GotrGoalsConfig.CONFIG_GROUP))
		{
			return;
		}
		refresh();
	}

	Goal getGoal()
	{
		return goal;
	}

	GameHistory getHistory()
	{
		return history;
	}

	int getElementalPoints()
	{
		return config.useManualPoints() ? config.manualElementalPoints() : elementalPoints;
	}

	int getCatalyticPoints()
	{
		return config.useManualPoints() ? config.manualCatalyticPoints() : catalyticPoints;
	}

	/**
	 * @return true once banked points have been read at least once (from an end-of-game message
	 * or the Rewards Guardian's Check option), or manual totals are in use
	 */
	boolean isPointsKnown()
	{
		return config.useManualPoints() || pointsKnown;
	}

	/**
	 * @return true once the bank has been opened (this session or a previous one) so banked pearls
	 * are included, or manual totals are in use
	 */
	boolean isBankPearlsKnown()
	{
		return config.useManualPoints() || bankPearls >= 0;
	}

	/**
	 * @return searches the banked points pay for right now - each needs one of each type
	 */
	int getBankedSearches()
	{
		return Math.min(getElementalPoints(), getCatalyticPoints());
	}

	/**
	 * @return elemental energy earned so far in the game in progress, or 0 between games (when the
	 * last game's energy is already reflected in the banked points)
	 */
	int getCurrentGameElementalEnergy()
	{
		return gameActive ? Math.max(client.getVarbitValue(VarbitID.GOTR_ELEMENTAL_EARNED_THIS_GAME), 0) : 0;
	}

	int getCurrentGameCatalyticEnergy()
	{
		return gameActive ? Math.max(client.getVarbitValue(VarbitID.GOTR_CATALYTIC_EARNED_THIS_GAME), 0) : 0;
	}

	double getPearlsPerSearch()
	{
		return Goal.pearlsPerSearch(config.ownAllPouches(), config.countTradeIns());
	}

	/**
	 * @return average Runecraft XP per game from recent games, else the configured assumption, else
	 * null
	 */
	Double getXpPerGame()
	{
		return xpPerGame;
	}

	boolean isXpPerGameObserved()
	{
		return xpPerGameObserved;
	}

	Double getElementalPerGame()
	{
		return elementalPerGame;
	}

	Double getCatalyticPerGame()
	{
		return catalyticPerGame;
	}

	boolean isPointsPerGameObserved()
	{
		return pointsPerGameObserved;
	}

	/**
	 * @return average real-world minutes per game from recent games, or null if none were timed
	 */
	Double getMinutesPerGame()
	{
		return minutesPerGame;
	}

	/**
	 * @return true if the player is in the Temple of the Eye, or anywhere in a game (including
	 * inside a runic altar mid-game)
	 */
	boolean isAtGotr()
	{
		Player player = client.getLocalPlayer();
		if (player != null && player.getWorldLocation().getRegionID() == TEMPLE_REGION)
		{
			return true;
		}
		Widget hud = client.getWidget(InterfaceID.GotrHud.CONTENT);
		return hud != null && !hud.isHidden();
	}

	private void refresh()
	{
		int pearls = config.useManualPoints()
			? config.manualPearls()
			: inventoryPearls + Math.max(bankPearls, 0);
		int xp = client.getGameState() == GameState.LOGGED_IN ? client.getSkillExperience(Skill.RUNECRAFT) : goal.getCurrentXp();
		goal.recalculate(config, pearls, xp);
		updateAverages();
		checkThresholdNotification();
	}

	/**
	 * Recomputed once per tick rather than per overlay frame; the history only changes once per
	 * game anyway.
	 */
	private void updateAverages()
	{
		int window = config.gamesWindow();

		Double observedXp = history.averageXp(window);
		xpPerGameObserved = observedXp != null;
		xpPerGame = observedXp != null ? observedXp
			: config.assumedXpPerGame() > 0 ? (double) config.assumedXpPerGame() : null;

		pointsPerGameObserved = history.size() > 0;
		if (pointsPerGameObserved)
		{
			elementalPerGame = history.averageElemental(window);
			catalyticPerGame = history.averageCatalytic(window);
		}
		else
		{
			Double assumed = config.assumedPointsPerGame() > 0 ? (double) config.assumedPointsPerGame() : null;
			elementalPerGame = assumed;
			catalyticPerGame = assumed;
		}

		Double ticks = history.averageTicks(window);
		minutesPerGame = ticks == null ? null : ticks / TICKS_PER_MINUTE;
	}

	/**
	 * Opens (or extends nothing, if already open) the short window that collects a finished game's
	 * XP and attuned points before recording it.
	 */
	private void onGameEnded()
	{
		if (finalizeAtTick >= 0)
		{
			return;
		}
		finalizeAtTick = tickCounter + FINALIZE_DELAY_TICKS;
		pendingEndTick = tickCounter;
		pendingElemental = 0;
		pendingCatalytic = 0;
		gameActive = false;
	}

	private void finalizeGame()
	{
		int xp = GameHistory.UNKNOWN;
		if (gameStartXp >= 0)
		{
			int gained = client.getSkillExperience(Skill.RUNECRAFT) - gameStartXp;
			xp = gained >= 0 ? gained : GameHistory.UNKNOWN;
		}

		int ticks = GameHistory.UNKNOWN;
		if (lastGameEndTick >= 0 && pendingEndTick - lastGameEndTick <= MAX_BACK_TO_BACK_TICKS)
		{
			ticks = pendingEndTick - lastGameEndTick;
		}
		else if (gameStartTick >= 0)
		{
			ticks = pendingEndTick - gameStartTick;
		}

		history.add(new GameHistory.Sample(xp, pendingElemental, pendingCatalytic, ticks));
		configManager.setRSProfileConfiguration(GotrGoalsConfig.CONFIG_GROUP, KEY_HISTORY, history.serialize());
		log.debug("Recorded GotR game: xp={} elemental={} catalytic={} ticks={}", xp, pendingElemental, pendingCatalytic, ticks);

		lastGameEndTick = pendingEndTick;
		finalizeAtTick = -1;
		pendingEndTick = -1;
		gameStartTick = -1;
		gameStartXp = -1;
	}

	private void resetGameTracking()
	{
		gameActive = false;
		gameStartTick = -1;
		gameStartXp = -1;
		lastGameEndTick = -1;
		finalizeAtTick = -1;
		pendingEndTick = -1;
	}

	/**
	 * Reads the Rewards Guardian's "Check" dialog while it's open. Only persists on an actual
	 * change, since the dialog stays open (and matching) for many ticks.
	 */
	private void readCheckDialog()
	{
		Widget dialog = client.getWidget(InterfaceID.Messagebox.TEXT);
		if (dialog == null || dialog.isHidden() || dialog.getText() == null)
		{
			return;
		}
		String text = Text.removeTags(dialog.getText().replace("<br>", " "));
		Matcher matcher = CHECK_PATTERN.matcher(text);
		if (!matcher.find())
		{
			return;
		}
		int catalytic = parseNumber(matcher.group(1));
		int elemental = parseNumber(matcher.group(2));
		if (!pointsKnown || elemental != elementalPoints || catalytic != catalyticPoints)
		{
			setBankedPoints(elemental, catalytic);
		}
	}

	private void setBankedPoints(int elemental, int catalytic)
	{
		elementalPoints = elemental;
		catalyticPoints = catalytic;
		pointsKnown = true;
		configManager.setRSProfileConfiguration(GotrGoalsConfig.CONFIG_GROUP, KEY_ELEMENTAL, elemental);
		configManager.setRSProfileConfiguration(GotrGoalsConfig.CONFIG_GROUP, KEY_CATALYTIC, catalytic);
	}

	private void loadProfile()
	{
		Integer elemental = readProfileInt(KEY_ELEMENTAL);
		Integer catalytic = readProfileInt(KEY_CATALYTIC);
		pointsKnown = elemental != null && catalytic != null;
		elementalPoints = pointsKnown ? elemental : 0;
		catalyticPoints = pointsKnown ? catalytic : 0;

		Integer bank = readProfileInt(KEY_BANK_PEARLS);
		bankPearls = bank != null ? bank : -1;

		history.deserialize(configManager.getRSProfileConfiguration(GotrGoalsConfig.CONFIG_GROUP, KEY_HISTORY));
	}

	private Integer readProfileInt(String key)
	{
		String value = configManager.getRSProfileConfiguration(GotrGoalsConfig.CONFIG_GROUP, key);
		if (value == null)
		{
			return null;
		}
		try
		{
			return Integer.parseInt(value);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	private void checkThresholdNotification()
	{
		boolean nowMet = goal.isMetThreshold();
		if (nowMet && !previouslyMetThreshold)
		{
			notifier.notify(config.thresholdNotification(),
				"You have enough Abyssal pearls to purchase your selected Guardians of the Rift rewards.");
		}
		previouslyMetThreshold = nowMet;
	}

	private static int countPearls(ItemContainer container)
	{
		if (container == null)
		{
			return 0;
		}
		int pearls = 0;
		for (Item item : container.getItems())
		{
			if (item.getId() == ItemID.ABYSSAL_PEARL)
			{
				pearls += item.getQuantity();
			}
		}
		return pearls;
	}

	private static int parseNumber(String value)
	{
		return Integer.parseInt(value.replace(",", ""));
	}
}
