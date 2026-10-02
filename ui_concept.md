UI / UX SPECIFICATION

Create a dark-themed desktop/tablet application based on the following wireframe and behavior.

GENERAL STYLE
- Black / very dark background.
- White text, borders, controls, and outlines.
- Material Design should be used as the general design basis.
- The original wireframe is hand-drawn; improve spacing, proportions, typography, hierarchy, and usability as needed.
- Gray handwritten text in the original sketch represents designer comments and must NOT appear in the final UI unless explicitly described below.
- The app content must NOT draw underneath the Android/system status bar at the top.
- The app content must NOT draw underneath the system task/navigation bar at the bottom.
- The application should respect system safe areas / insets.

==================================================
1. TOP APP BAR
==================================================

The application begins below the Android/system status bar.

Left side:
- Application title:
  "APP NAME"

Right side:
- Button:
  "PICK DEMO"

- Button:
  "SETTINGS"

The app bar itself belongs to the application and should visually separate the system status bar from the main content.

==================================================
2. DEMO INFORMATION CARD
==================================================

Near the top of the page, show a centered information card.

Title:
- "DEMO NAME"

Fields:
- "AUTHOR: <name>"
- "Author steamid: <steamid>"
- "demo creation time: HH:MM DD/month/YYYY"
- "TOTAL detections: <number>"

Example:
AUTHOR: nickname
Author steamid: 7656119...
demo creation time: 14:32 02/October/2026
TOTAL detections: 123

COPY BEHAVIOR:
- Values indicated as copyable should be copied when the user performs a press-and-hold / long-press interaction.
- In the original wireframe, arrows originating from the handwritten note "copied on hold" point at the fields that support this interaction.
- These include SteamID fields and any other value explicitly marked this way in the source wireframe.

On desktop platforms, an equivalent click-and-hold / context interaction may be used.

==================================================
3. MAIN ACTIONS
==================================================

Under the demo information card, show two actions.

Primary prominent button:
- "ANALYZE!"

Secondary button:
- "Export Detections to JSON"

Behavior:
- "ANALYZE!" starts analysis of the selected demo.
- "Export Detections to JSON" exports the current detection results as a JSON file.

==================================================
4. DETECTIONS PANEL
==================================================

Below the action buttons, show a large rounded rectangular results panel.

Panel header:
- "Detections: <total>"

Example:
Detections: 123

The results use a hierarchical expandable/collapsible accordion/tree structure.

==================================================
5. PLAYER / USER ROWS
==================================================

Each top-level result row represents a nickname/player.

Example collapsed row:

▶ NICK NAME — 20 detections                         <steamid>

Example expanded row:

▼ NICK NAME — 20 detections                         <steamid>

Structure:
- Left:
  - expand/collapse arrow
  - nickname
  - number of detections

- Right:
  - SteamID

SteamID should support long-press / press-and-hold copy behavior.

EXPAND/COLLAPSE ARROW:
- The arrow icon itself rotates when the row is expanded or collapsed.
- Do not replace it with an unrelated icon.
- Conceptually:
  collapsed = ▶
  expanded = ▼
- Prefer implementing this as a smooth rotation animation of the same chevron/triangle icon.

==================================================
6. DETECTION / ALGORITHM GROUPS
==================================================

Inside an expanded player row, show nested algorithm/detection groups.

Example:

▼ ALG01 — 12 detections

Expanded content:

1. tick 12345
2. tick 12789
3. tick 13110

Another collapsed group:

▶ ALG02 — 8 detections

The same arrow rotation behavior applies:
- collapsed arrow points right
- expanded arrow points down
- animate rotation during state change

Each algorithm group may contain multiple detection instances / ticks.

==================================================
7. EXPANDED DETAILS AREA
==================================================

When an algorithm group is expanded, its inner details area should have a gray background.

Example:

▼ ALG01 — 12 detections

[GRAY DETAIL AREA]
1. tick 12345
2. tick 12789
3. tick 13110
[/GRAY DETAIL AREA]

This visual difference makes expanded details easier to distinguish from the surrounding black interface.

==================================================
8. SCROLLING
==================================================

The detections/results panel can contain many players and many nested detection groups.

Requirements:
- It must support vertical scrolling.
- A vertical scrollbar may be visible on the right side.
- The hierarchy must remain readable even with deeply nested content.
- Expansion must not break the panel layout.

More players / groups continue below using the exact same structure.

==================================================
9. ANALYSIS PROGRESS
==================================================

At the bottom of the application, above the system task/navigation bar, show an analysis progress area.

Include:
- horizontal progress bar
- percentage
- tick progress

Example:

[=======================---------] 72%   72000 / 100000 ticks

The text from the sketch approximately corresponds to:
- "<xx>% <xxxxx> ticks"

A more useful implementation may display:
- percentage
- processed ticks
- total ticks

For example:
"72% — 72,000 / 100,000 ticks"

This is ACTUAL UI content, not a designer comment.

==================================================
10. SYSTEM BARS / SAFE AREA
==================================================

Important layout requirement:

TOP:
- Same concept as Android status bar handling.
- The application must start BELOW the operating-system status bar.
- Do not render application content underneath the status bar.

BOTTOM:
- Same as with the status bar.
- The application must end ABOVE the system task/navigation bar.
- Do not render application content underneath the task/navigation bar.

Use system safe-area insets.

==================================================
11. PAGE HIERARCHY
==================================================

App
├── System Status Bar
│   └── not drawn by the app
│
├── Application App Bar
│   ├── APP NAME
│   ├── PICK DEMO
│   └── SETTINGS
│
├── Demo Information Card
│   ├── DEMO NAME
│   ├── AUTHOR
│   ├── Author SteamID
│   ├── Demo Creation Time
│   └── Total Detections
│
├── Main Actions
│   ├── ANALYZE!
│   └── Export Detections to JSON
│
├── Detections Panel
│   └── Player
│       ├── Nickname
│       ├── Number of Detections
│       ├── SteamID
│       └── Algorithm Group
│           ├── Algorithm Name
│           ├── Number of Detections
│           └── Detection Entries
│               └── Tick Number
│
├── Analysis Progress
│   ├── Progress Bar
│   ├── Percentage
│   └── Tick Count
│
└── System Task / Navigation Bar
    └── not drawn by the app

==================================================
12. INTERACTION MODEL
==================================================

PICK DEMO:
- Opens a demo/file selection interface.

ANALYZE:
- Starts demo analysis.
- Progress bar becomes active.
- Percentage and processed tick count update while analysis runs.
- Detection results populate as appropriate.

EXPORT:
- Exports all current detections to JSON.

PLAYER ROW:
- Click/tap row or chevron to expand/collapse it.
- Chevron smoothly rotates between right-facing and downward-facing state.

ALGORITHM ROW:
- Click/tap row or chevron to expand/collapse it.
- Chevron uses the same rotation behavior.

COPYABLE VALUES:
- Press-and-hold / long-press copies the value.
- Especially relevant for SteamIDs and other fields marked by "copied on hold" arrows in the wireframe.
- Optionally show brief feedback such as:
  "Copied"

SCROLL:
- Results panel supports vertical scrolling.
- The rest of the layout should remain stable.

==================================================
13. DESIGN FREEDOM
==================================================

The wireframe defines:
- information architecture
- feature placement
- hierarchy
- interaction concepts

It does NOT require pixel-perfect reproduction of the hand-drawn shapes.

The final UI may improve:
- padding
- margins
- typography
- responsive layout
- card sizes
- button sizes
- visual hierarchy
- shadows
- borders
- accessibility
- hover/focus states
- animations

Do not change the fundamental information architecture or interaction behavior without a clear UX reason.

Settings UI is not finalized and may be redesigned later.
For now, only provide the SETTINGS entry button in the main app bar unless a settings screen is explicitly requested.
