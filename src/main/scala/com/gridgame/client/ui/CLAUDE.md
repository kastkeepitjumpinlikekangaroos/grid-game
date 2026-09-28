# client/ui

The menus, in JavaFX. `ClientMain` (in client/) is the app: it starts up, switches screens
(`switchScreen`, which stops the last screen's animations and drops the client listeners it set),
connects, and shuts down. Each screen is a class in `screens/` with a `show(stage)`, and reaches the
app only through `screens/Screens` — the client, `switchScreen`, the hook that stops it
(`stopCurrentScreen`) and every other screen's `show`. `ClientMain` implements it, so a screen test
can drive a real screen through `app.showLobbyRoom(stage)` and the like.

- **theme.css** — the look, nearly all of it: the palette, type scale, cards, buttons, fields,
  chips, tables, the character panel. A screen gives its nodes the sheet's classes.
- **Theme** — the rest of the look as code sees it: the fonts (`loadFonts`, `Fonts`), the palette
  for what code draws (`Palette`), putting a page on the stage (`newScene`, `showPage`), status lines
  (`showStatus` with a `Tone`), `steppedLoop`, the lobby settings every form offers.
- **Widgets** — the parts every screen is built from: text in the type scale, buttons, cards,
  chips, keycaps, medals, avatars, stat tiles, tables (`Col`, `tableHead`, `tableRow`), fields,
  MapleStory's outlined titles (`displayTitle`), and the page itself (`page`, `topBar`, `brand`).
- **SkyBackdrop** — the sky behind every screen: clouds, hills, trees, and characters stood on them.
- **Icons** — line icons, as SVG paths. **MapPreview** — the maps from above, cached, always
  whole: a frame of another shape than the (square) map is filled out in the colour of the map's
  outer band, so it reads as the map going on. It used to crop to fill its frame, which cut off
  half of a square map in the lobby form's wide preview.
- **CharacterSelectionPanel** — the character picker (grid, filter, search, the picked character in
  detail), with **AbilityPreviewRenderer** (the animated ability previews; each projectile type's
  colours and preview shape are **AbilityPreviewPalette**'s). Its role line ("Melee · 145 HP ·
  Slow") is `roleLine`.
- **SpriteGenerator** — character sprites for the menus: portraits (one frame, cut once) and the
  few whole sheets that animate.
- **UiActivity** — whether anybody is using the window (animations hold still when not).
- **ViewportCache**, **LoginForm**.

`screens/Chrome` frames every screen after login the way a web site's pages share a header: the sky
and a top bar with the game's name, a tab per place to go (Lobbies, Practice, Ranked,
Leaderboard), the sound switch and the player's chip (their profile). A screen hands `Chrome.page`
its body and its tab, and a `leave` for what going elsewhere must undo — the lobby room leaves the
lobby, the ranked queue leaves the queue, the scoreboard goes back to the browser.

## The look: MapleStory's, as a clean web page

Clean and simple like a modern web site, with the feel of the characters and projectiles, which
are drawn in MapleStory's style: a bright sky with fat outlined clouds and round green hills (the
same sky the Meadow is played under, BackgroundPainter), white cards on it, one rounded typeface
(Nunito, OFL, in `fonts/`), and chunky buttons — a flat face with one hard highlight band, an
outline in a dark version of its own colour, and a lip under it that a press closes. Ink is a dark
navy, never black, as the sprites' outlines are. The yellow button is the one thing a screen is for
(log in, create, start, find a match). Titles that matter (the game's name, "Victory!") are
lettered as MapleStory letters its own: a fat outline and a hard shadow (`displayTitle`).

Judge a change with the gallery, which draws every screen in the states a player sees it in,
without a server:

```bash
bazel run //src/main/scala/com/gridgame/client:ui_gallery -- /tmp/ui              # 1440x900
bazel run //src/main/scala/com/gridgame/client:ui_gallery -- /tmp/ui --scale=2    # as on Retina
bazel run //src/main/scala/com/gridgame/client:ui_gallery -- /tmp/ui --lang=ko --only=07_room_host_teams
```

`16_map_previews` is every map's preview at each size the menus draw one; a new map's has to read
as the whole map at all three.

1440x900 is a small laptop's window, and the pages are laid out for it: the lobby room fits it
without scrolling, for the host too (the match's settings are drop-down chips in its heading, not
a card of their own), and so does a scoreboard of six; a bigger one scrolls a little.

## Styling: what outranks what

A scene's stylesheet outranks what code sets. A rule in theme.css beats `setTextFill` and
`setFont`, and so does anything Modena derives from a colour looked up in the sheet or inherits
from `.root`'s font: once the sheet sets the text colour lookups and the font on `.root`, a label
coloured or sized from code comes out in the sheet's colour and size. So text is coloured and
sized by class, or inline where it varies at run time (a team's colour, an avatar's size) — an
inline style outranks the sheet. The colour utilities (`.text-leaf` and the rest) are last in the
sheet so they win over a class above that also sets a colour. A button's classes set its
background's colours, insets and radii together, or Modena's own layers show through.

## Menus: every changed frame repaints the whole window
JavaFX on macOS presents the whole window for any change, however small, so the menus' cost
is set by how often something on them moves, not by what moves: a 10px square animating at
60 fps on an otherwise empty full-screen window burns ~60% of a core on a 5K display. Rules
the screens follow:
- **Little moves, and nothing moves when nobody's looking.** The character grid holds still (a
  portrait per cell) and only the cell under the mouse walks; the picked character walks at 6
  steps a second and the three ability previews run at 30 fps, on the same pulses. `steppedLoop`
  (Theme) runs any looping decoration at 12 fps. All of it holds still while the window is in
  the background or has had no input for 30s (`UiActivity`).
- **Depth is layered backgrounds, not effects.** A card's edge and the lip under it, a button's
  outline and lip, a field's focus ring: each is a background inset under the next. An
  `-fx-effect` (a shadow, a glow) renders its node off screen on every repaint; the old dark menus
  had a blurred shadow on every card and button. The only one left is the combo box popup's.
- **The sky never moves and draws cheaply.** It is ellipses (clouds, hills, tree crowns), which
  JavaFX fills with a shader rather than rasterising a path, built again only when the window's
  size changes; it is clipped to the window and asks the page for no room.
- **No per-frame images.** `SpriteGenerator` keeps portraits — one 128px frame per character,
  cut once on a thread of its own, 64KB each, 7MB for the roster — and at most four whole sheets,
  for what animates, drawn a frame at a time through an ImageView's viewport. The grid used to
  animate every cell from a sheet each (33MB for the roster, every visible cell redrawn six times a
  second); before that it kept 16 `WritableImage`s per character forever: 117MB of heap and 1792
  GPU textures. A sprite drawn at its frame's own size (the picked character, the login screen's)
  is drawn unsmoothed: exactly doubled on a Retina display, one for one on any other.
- **Map previews are read once** (`MapPreview`): drawing one parses the map's file, which the lobby
  list used to do for every row every time the list refreshed.
- **`ViewportCache.disable` on any `ScrollPane` around animated content** — every page's, since
  `Widgets.page` puts each page in one. ScrollPane's skin caches its viewport as a bitmap; around
  canvases that animate, every frame re-renders the whole viewport into a texture as big as the
  viewport and draws it again.
- **Screens don't outlive themselves.** `switchScreen` unregisters the listeners that update
  a screen's controls (they held the whole scene graph, and the lobby chat kept rebuilding
  off screen through the next match), and `showGameScene` swaps the hidden stage's scene
  for an empty one and drops the sprite cache, so none of the menus stay resident in a match.

What none of this fixes: a visible JavaFX window on macOS builds up a pool of about 15
window-sized IOSurfaces as it keeps presenting — ~650MB on a 5K display, ~120MB at 1080p —
and only gives it back when the window is hidden (a match start does). It is Core
Animation's pool behind the `CAOpenGLLayer` JavaFX draws into; nothing in JavaFX's API sizes
it, a resize doesn't release it, and JavaFX 21.0.12 and 23.0.2 behave exactly like 21.0.1
(measured interleaved). Fewer presents only delay it; Low quality's 1x menus quarter it. The
GLFW game window's surfaces don't grow. When measuring the menus, keep the window visible
and unobstructed: an occluded window presents less and flatters every number.
