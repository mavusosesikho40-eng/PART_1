# Baseflow Dashboard

Baseflow's live operating screen: one view of check-ins, areas, sessions and floor teams for
a busy venue, built to the **Baseflow Brand Kit v1** (November 2026). It opens on a sample
event, Careers Expo at the Main venue, and keeps itself live: guests check in every few
seconds, people move between areas, and a gate goes offline and comes back.

The figures are illustrative. Nothing here talks to a server yet.

## Running it

No build step and no dependencies. Open `index.html` in a browser, or serve the folder:

```
python3 -m http.server 8000
```

then visit `http://localhost:8000`.

## What's on the screen

Read top-left to bottom-right, floor first, leadership last.

| Part | What it shows |
| --- | --- |
| **Alerts** | What needs doing now, worst first: an offline gate, a full or nearly full area, a session booked into a room that's too small. Each says what changed, then what to do, with the action beside it. |
| **Metric tiles** | Checked in (top-left, the most important number), On site now, Sessions on time and Entry points online. Every number carries its *Updated* time. |
| **Areas** | Each place in the venue: how many people are in it against its capacity, what's on now or next, and Open, Nearly full (85%) or Full. |
| **Now and next** | The rest of today's programme, with delays, room changes and rooms that are too small. |
| **Arrivals today** | Check-ins per 30 minutes since the doors opened. Point at a bar, or focus the chart and use the arrow keys, to read it. |
| **Entry points** | Each desk and gate, online or offline, with the latest check-ins by booking code. No guest names are shown. |
| **Floor teams** | Who's on shift and how far through their tasks each team is. |
| **Activity** | Changes as they happen, newest first. |
| **Last 7 days** | Venue check-ins per day for leadership, compared with the 7 days before. **Copy report** puts the rows on the clipboard for a spreadsheet or email. |

**Send update** is the screen's one primary action. It sends a room change, a delay or a
notice to the floor teams you pick ("Room change sent to 14 staff."). The screen updates at
once: the session moves, people start walking to the new room, and the change appears in
Activity. The buttons on the alerts open it already filled in.

Each chart has a **Show as a table** view.

## Floor mode and Wall screen

- **Light** is the default. **Floor mode** is the brand's dark theme, for control rooms and
  dim venues. The screen follows the device setting until someone presses **Floor mode**;
  that choice is remembered in the browser.
- **Wall screen** (on screens 1024px and wider) is Floor mode at wall-screen sizes: metrics
  at 64px and nothing smaller than 18px, with the controls and the leadership panels put
  away. It fits a 1920 × 1080 display without scrolling. Press Escape or **Exit wall screen**
  to leave it.

## How it follows the brand kit

- **Colour, type, spacing, radius, shadow and sizes** are the kit's tokens (`tokens.json`),
  defined once at the top of `css/dashboard.css` for both themes. Brand colour is an accent:
  Flow Blue for the one primary action, links and selection; signal colours for state only,
  always with a word or an icon.
- **Type:** Lexend for headings and the uppercase eyebrow labels; Atkinson Hyperlegible for
  everything people read or count, with tabular figures on live numbers so they don't jiggle.
- **Components:** the kit's `bf-` Button, StatusBadge and MetricTile.
- **Logo:** the kit's own files, never re-typed. The gradient lockup on light grounds, the
  reversed lockup in Floor mode, and the wordmark alone below 120px.
- **Icons:** Lucide outline icons at 20px, as the kit specifies. No emoji.
- **Voice:** plain words, numbers over adjectives, sentence case, 24-hour times, thousands
  grouped with a space (1 248), dates as 19 Nov 2026, South African English.
- **Motion:** new data fades in over 160ms; the Live dot pulses every 2 seconds; with
  reduced motion only opacity changes.

## Showing real numbers

Everything starts from `js/sample-data.js`: the event, areas, sessions, entry points,
teams, the arrivals so far and the last 7 days. Change the values there to describe a real
venue. To go live, replace `simulate()` in `js/dashboard.js` with your feed and call
`requestRender()` after each change; the rest of the screen follows the same state.

## Files

```
index.html          The page, with the icon sprite
css/dashboard.css   Brand tokens, both themes, layout and components
js/sample-data.js   The sample event
js/dashboard.js     Rendering, the live simulation, charts and Send update
fonts/              Lexend and Atkinson Hyperlegible from the brand kit, with their licences
images/             Baseflow logo files and app icon from the brand kit
```

## Licences

Lexend and Atkinson Hyperlegible are under the SIL Open Font License 1.1 (see `fonts/`).
Lucide icons are under the ISC licence. The Baseflow logo and app icon belong to Baseflow
Technologies (Pty) Ltd.
