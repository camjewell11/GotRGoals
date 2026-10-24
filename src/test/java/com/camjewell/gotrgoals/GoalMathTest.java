package com.camjewell.gotrgoals;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import net.runelite.api.Experience;
import org.junit.Test;

public class GoalMathTest
{
	private static final double EPSILON = 1e-9;

	@Test
	public void pearlsPerSearchMatchesWikiDropTable()
	{
		assertEquals(15.0 * 18 / 140, Goal.pearlsPerSearch(false, false), EPSILON);
		assertEquals(15.0 * 18 / 125, Goal.pearlsPerSearch(true, false), EPSILON);
		assertEquals(15.0 * 18 / 125 + 50.0 / 400 + 100.0 / 700, Goal.pearlsPerSearch(true, true), EPSILON);
	}

	@Test
	public void fullRaimentsTakesAboutSixHundredSearches()
	{
		// 1,350 pearls / 2.16 per search
		assertEquals(625, Goal.searchesForPearls(1350, Goal.pearlsPerSearch(true, false)));
		assertEquals(0, Goal.searchesForPearls(0, 2.0));
	}

	@Test
	public void gamesForSearchesUsesTheScarcerPointType()
	{
		// Need 100 searches. Elemental: 40 banked, 60 short at 6/game = 10 games.
		// Catalytic: 70 banked, 30 short at 5/game = 6 games. Elemental is the bottleneck.
		assertEquals(Integer.valueOf(10), Goal.gamesForSearches(100, 40, 70, 6.0, 5.0));
	}

	@Test
	public void gamesForSearchesCountsCurrentGameEnergy()
	{
		// 2.5 points earned so far this game closes part of a 10-point gap at 5/game.
		assertEquals(Integer.valueOf(2), Goal.gamesForSearches(10, 2.5, 10, 5.0, 5.0));
	}

	@Test
	public void gamesForSearchesIsZeroWhenEnoughBanked()
	{
		assertEquals(Integer.valueOf(0), Goal.gamesForSearches(50, 60, 55, null, null));
	}

	@Test
	public void gamesForSearchesNeedsARateForAShortType()
	{
		assertNull(Goal.gamesForSearches(50, 10, 60, null, 5.0));
		assertNull(Goal.gamesForSearches(50, 10, 10, 0.0, 5.0));
	}

	@Test
	public void gamesForXpRoundsUp()
	{
		assertEquals(Integer.valueOf(4), Goal.gamesForXp(100_000, 25_000.0));
		assertEquals(Integer.valueOf(5), Goal.gamesForXp(100_001, 25_000.0));
		assertEquals(Integer.valueOf(0), Goal.gamesForXp(0, null));
		assertNull(Goal.gamesForXp(1_000, null));
	}

	@Test
	public void targetLevelZeroMeansNextLevel()
	{
		Goal goal = new Goal();
		goal.recalculate(targetLevelConfig(0), 0, Experience.getXpForLevel(74) + 1);
		assertEquals(75, goal.getTargetLevel());
		assertEquals(Experience.getXpForLevel(75), goal.getTargetXp());

		// Already 99: the goal is just met rather than pointing past the cap.
		goal.recalculate(targetLevelConfig(0), 0, Experience.getXpForLevel(99));
		assertEquals(99, goal.getTargetLevel());
		assertEquals(0, goal.getXpRemaining());

		goal.recalculate(targetLevelConfig(85), 0, Experience.getXpForLevel(74));
		assertEquals(85, goal.getTargetLevel());
	}

	private static GotrGoalsConfig targetLevelConfig(int targetLevel)
	{
		return new GotrGoalsConfig()
		{
			@Override
			public int targetLevel()
			{
				return targetLevel;
			}
		};
	}

	@Test
	public void historyAveragesOnlyTheRecentWindowAndSkipsUnknowns()
	{
		GameHistory history = new GameHistory();
		history.add(new GameHistory.Sample(99_999, 99, 99, 9_999));
		history.add(new GameHistory.Sample(20_000, 4, 6, 600));
		history.add(new GameHistory.Sample(GameHistory.UNKNOWN, 6, 4, GameHistory.UNKNOWN));
		history.add(new GameHistory.Sample(30_000, 5, 5, 700));

		assertEquals(25_000.0, history.averageXp(3), EPSILON);
		assertEquals(5.0, history.averageElemental(3), EPSILON);
		assertEquals(5.0, history.averageCatalytic(3), EPSILON);
		assertEquals(650.0, history.averageTicks(3), EPSILON);
		assertEquals(3, history.countInWindow(3));
	}

	@Test
	public void historyRoundTripsAndSkipsMalformedEntries()
	{
		GameHistory history = new GameHistory();
		history.add(new GameHistory.Sample(20_000, 4, 6, 600));
		history.add(new GameHistory.Sample(GameHistory.UNKNOWN, 6, 4, GameHistory.UNKNOWN));

		GameHistory loaded = new GameHistory();
		loaded.deserialize(history.serialize() + ";garbage;1,2,x,4");
		assertEquals(2, loaded.size());
		assertEquals(history.serialize(), loaded.serialize());
	}

	@Test
	public void historyIsCapped()
	{
		GameHistory history = new GameHistory();
		for (int i = 0; i < GameHistory.MAX_STORED_GAMES + 5; i++)
		{
			history.add(new GameHistory.Sample(i, 1, 1, 1));
		}
		assertEquals(GameHistory.MAX_STORED_GAMES, history.size());
	}
}
