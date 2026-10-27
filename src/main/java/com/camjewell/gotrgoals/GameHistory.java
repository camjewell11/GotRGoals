package com.camjewell.gotrgoals;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * The player's most recent completed Guardians of the Rift games, used to average XP and reward
 * points per game. Unlike MTA's per-tick rate windows this is a per-game window: games are
 * ~10 minutes long and vary a lot (mass vs. solo, combination runes, leaving early), so the
 * natural unit is "the last N games" rather than "the last N minutes".
 *
 * Stored as a plain "xp,elemental,catalytic,ticks;..." string in the RuneScape-profile config so
 * estimates survive client restarts - with one data point per ~10 minutes, throwing the history
 * away on logout (as MTA does with its per-tick windows) would leave the estimates empty for most
 * of every session.
 */
class GameHistory
{
	/**
	 * Hard cap on stored samples, independent of {@link GotrGoalsConfig#gamesWindow()} (which is
	 * capped lower), so the persisted string stays small however long someone plays.
	 */
	static final int MAX_STORED_GAMES = 100;

	static final int UNKNOWN = -1;

	private final Deque<Sample> samples = new ArrayDeque<>();

	void add(Sample sample)
	{
		samples.addLast(sample);
		while (samples.size() > MAX_STORED_GAMES)
		{
			samples.removeFirst();
		}
	}

	void clear()
	{
		samples.clear();
	}

	int size()
	{
		return samples.size();
	}

	/**
	 * @return mean Runecraft XP over the last {@code window} games whose XP was measured, or null
	 * if none were (e.g. every recent game was joined part-way through).
	 */
	Double averageXp(int window)
	{
		return average(window, Sample::xp);
	}

	Double averageElemental(int window)
	{
		return average(window, Sample::elemental);
	}

	Double averageCatalytic(int window)
	{
		return average(window, Sample::catalytic);
	}

	/**
	 * @return mean real-world ticks per game (including the wait before it, when the previous game
	 * was played back-to-back) over the last {@code window} games, or null if unmeasured.
	 */
	Double averageTicks(int window)
	{
		return average(window, Sample::ticks);
	}

	/**
	 * @return how many of the last {@code window} games the averages are drawn from.
	 */
	int countInWindow(int window)
	{
		return Math.min(Math.max(window, 1), samples.size());
	}

	private Double average(int window, SampleField field)
	{
		int remaining = Math.max(window, 1);
		long total = 0;
		int count = 0;
		Iterator<Sample> it = samples.descendingIterator();
		while (it.hasNext() && remaining > 0)
		{
			int value = field.get(it.next());
			remaining--;
			if (value == UNKNOWN)
			{
				continue;
			}
			total += value;
			count++;
		}
		return count == 0 ? null : total / (double) count;
	}

	String serialize()
	{
		StringBuilder sb = new StringBuilder();
		for (Sample s : samples)
		{
			if (sb.length() > 0)
			{
				sb.append(';');
			}
			sb.append(s.xp()).append(',')
				.append(s.elemental()).append(',')
				.append(s.catalytic()).append(',')
				.append(s.ticks());
		}
		return sb.toString();
	}

	/**
	 * Replaces the current history with the parsed one. Malformed entries are skipped rather than
	 * failing the whole load, so one bad entry never wipes out every other game.
	 *
	 * Entries with unknown XP are dropped too: early versions recorded games joined part-way (with
	 * unknown XP and usually 0 points), which dragged the points average down. Games are only
	 * recorded when their start was seen now, so this cleans those out of saved history.
	 */
	void deserialize(String value)
	{
		samples.clear();
		if (value == null || value.isEmpty())
		{
			return;
		}
		List<Sample> parsed = new ArrayList<>();
		for (String entry : value.split(";"))
		{
			String[] parts = entry.split(",");
			if (parts.length != 4)
			{
				continue;
			}
			try
			{
				parsed.add(new Sample(
					Integer.parseInt(parts[0].trim()),
					Integer.parseInt(parts[1].trim()),
					Integer.parseInt(parts[2].trim()),
					Integer.parseInt(parts[3].trim())));
			}
			catch (NumberFormatException e)
			{
				// skip this entry only
			}
		}
		for (Sample s : parsed)
		{
			if (s.xp() != UNKNOWN)
			{
				add(s);
			}
		}
	}

	private interface SampleField
	{
		int get(Sample sample);
	}

	static final class Sample
	{
		private final int xp;
		private final int elemental;
		private final int catalytic;
		private final int ticks;

		/**
		 * @param xp        Runecraft XP gained over the game (crafting + the end-of-game bonus), or
		 *                  {@link #UNKNOWN} - only in history saved by early versions, which still
		 *                  recorded games joined part-way; those aren't recorded at all now
		 * @param elemental elemental reward points awarded at the end of the game
		 * @param catalytic catalytic reward points awarded at the end of the game
		 * @param ticks     game ticks the game took, or {@link #UNKNOWN}
		 */
		Sample(int xp, int elemental, int catalytic, int ticks)
		{
			this.xp = xp;
			this.elemental = elemental;
			this.catalytic = catalytic;
			this.ticks = ticks;
		}

		int xp()
		{
			return xp;
		}

		int elemental()
		{
			return elemental;
		}

		int catalytic()
		{
			return catalytic;
		}

		int ticks()
		{
			return ticks;
		}
	}
}
