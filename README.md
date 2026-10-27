# Guardians of the Rift Goals

A RuneLite plugin that predicts how many more Guardians of the Rift games it'll take to reach a
Runecraft level goal, or to afford the Temple Supplies rewards you're saving for.

Sibling of [Mage Training Arena Goals](../MageTrainingArenaGoals), with the same two-overlay layout.

## How it works

Everything is off by default. Turn on **Track level goal** for the level overlay, tick a reward for
the pearl overlay, and turn on **Show estimates overlay** for the games-remaining overlay.

- **Level Goal** (config): a target Runecraft level (default 0, meaning *your next level*), or an
  exact **Target XP** for anything past 99. Turn off **Show level overlay** to hide the XP bar and
  keep the goal only as a row in the estimates overlay. The overlay shows a progress bar from your current XP to the target.
- **Reward Tracking** (config): checkboxes for every non-talisman Temple Supplies item (the Raiments of
  the Eye pieces, Ring of the elements, Abyssal needle, Abyssal lantern, Guardian's eye). Tick
  several to track their *combined* Abyssal pearl cost.
- **Points** (config): what the plugin reads in-game, all saved per character so it survives restarts:
  - **Banked reward points** come from the end-of-game `Total elemental energy: … Total catalytic
    energy: …` message, or the Rewards Guardian's **Check** option. There's no varbit for them,
    so until you finish a game or Check, the overlay asks you to. Each `You found some loot:`
    (one per roll, Big-search included) takes 1 of each off.
  - **Pearls** are your inventory plus your bank as of the last time you opened it.
  - **Use manual totals** switches to typed-in values, e.g. to preview the overlay away from the
    minigame.
- **Overlays** (150px wide by default, resizable): a **level** overlay with the XP bar, and a
  **reward** overlay with the reward header (icon, name, % complete), a pearl bar and your banked
  points. The translucent segment on the pearl bar is the pearls your banked points are *expected*
  to give once you search them.
- **Completion Estimates** (second overlay):
  - **Level**: XP remaining divided by your **rolling average XP per game**. That average is
    measured from `The rift becomes active!` to just after the game ends, so it includes both
    crafting XP and the end-of-game bonus (RC level × 45).
  - **Searches**: pearls remaining divided by **expected pearls per search**, from the wiki drop
    table. Pearls come as 14–16 at 18/140 per search, or 18/125 once you own every pouch:
    ≈1.93 or ≈2.16 per search. You can optionally add in dye/lantern trade-ins.
  - **Reward games**: each search costs one elemental *and* one catalytic point. So for each type,
    the plugin takes searches needed, minus banked points, minus this game's energy so far / 100,
    and divides by your **rolling average points per game** for that type. The larger result is
    the answer.
  - **Hover** the estimates overlay to see what the numbers are based on: searches needed, pearls
    per search, and how many games are averaged, with their XP, points and time per game.
  - **Games to average** (default 10) sets how many recent games the rolling averages cover.
  - Only **full games** count: you must be inside the barrier when the rift opens. Games joined
    part-way are left out entirely. That covers walking in late, turning the plugin on or logging
    in mid-game, and being away from the temple for over a minute.
  - **Reset averages** forgets every recorded game so the averages start fresh. The config panel
    can't hold buttons, so it's a checkbox that unticks itself. Banked points and pearls are kept.
  - **Show time estimates** adds `(~X hr)`, from how long your recent games took. Back-to-back
    games are timed end to end, so the lobby wait counts.
  - **Assumed XP / points per game** are optional starting rates used until your first game is
    recorded. They default to 0, which shows nothing until then. Values based on them are drawn
    in gray.

## Not yet verified in-game

Confirmed live: the `The rift becomes active!` start message, the `The Great Guardian
successfully closed the rift!` end message, and the `Elemental energy attuned: …` and `Total
elemental energy: …` messages. All of these were seen across about 30 games. So was the GotR HUD
(`InterfaceID.GotrHud`) showing only inside the barrier, which is how "in the game from the
start" is decided: games reached as the rift closed were skipped, full games were recorded.

These come from other GotR plugins and the wiki, and haven't been confirmed yet:

- The Check dialog being `InterfaceID.Messagebox.TEXT`.
- A **failed** game (Great Guardian defeated) is probably not recorded, because its message text
  isn't known yet.
- Searching with 25 pearls instead of points also sends `You found some loot:`, so the banked
  points drift by 1. The next game's total, or a Check, corrects them.

## Running the dev client

```
./gradlew run
```

Follow the [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
instructions to log in to the development client.

## License

BSD 2-Clause, see [LICENSE](LICENSE).
