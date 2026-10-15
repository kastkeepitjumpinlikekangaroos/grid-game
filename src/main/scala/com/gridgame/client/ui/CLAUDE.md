# client/ui

The menus, in JavaFX. `ClientMain` (in client/) is the app: it starts up, switches screens
(`switchScreen`, which stops the last screen's animations and drops the client listeners it set),
connects, logs in and stays logged in (the saved login, design/networking.md, *Staying logged
in*), and shuts down. It shows the stage once, as it starts; no screen shows it, so a screen test
opens no window. Each screen is a class in `screens/` with a `show(stage)`, and reaches the
app only through `screens/Screens` — the client, `switchScreen`, the hook that stops it
(`stopCurrentScreen`) and every other screen's `show`. `ClientMain` implements it, so a screen test
can drive a real screen through `app.showLobbyRoom(stage)` and the like.

- **theme.css** — the look, nearly all of it: the palette, type scale, glass cards, buttons,
  fields, chips, tables, the character panel, the shade over the world. A screen gives its nodes
  the sheet's classes.
- **Theme** — the rest of the look as code sees it: the fonts (`loadFonts`, `Fonts`), the palette
  for what code draws (`Palette`), putting a page on the stage (`newScene`, `showPage`), status lines
  (`showStatus` with a `Tone`), `steppedLoop`, the lobby settings every form offers.
- **Widgets** — the parts every screen is built from: text in the type scale (an `eyebrow` in
  capitals, which a sheet can't set), buttons, cards, chips, keycaps, medals, avatars, stat tiles,
  tables (`Col`, `tableHead`, `tableRow`), fields, the lettered titles (`displayTitle`), and the
  page itself (`page`, `topBar`, `brand`).
- **WorldBackdrop** — the world behind every screen: the middle of the Meadow as the match draws
  it, softened, under the sheet's shade; the login screen's characters stand along its bottom.
- **MapArt** — a map drawn as the match draws it, on the CPU into an image: TerrainPainter's
  passes over the tile atlas (`sprites/tiles.png`) in the match's projection — the ground with the
  shadows blocks cast, then blocks and props a diagonal at a time, far to near — for any window of
  its cells, optionally standing on a slab of its own ground. Small pictures are filtered, not
  picked: the atlas is halved four times over and each pixel averages a grid of samples.
- **MapPreview** — the maps for the menus, as models of themselves on a dark stage: where its
  matches are played (every cell its spawn points can walk to, `MapArt.playArea`) and a rim of its
  border. Drawn once per size shown, at twice it, on a thread of its own, and kept to 12MB. Also
  `patch`, a few cells of grass for a character to stand on. The whole arena, always: the lobby
  form once cut a square map to fill its wide frame, which hid half of it; the border (a forest,
  the sea, the void) is scenery, and shown whole it left the arena small.
- **Icons** — line icons, as SVG paths.
- **CharacterSelectionPanel** — the character picker (grid, filter, search, the picked character in
  detail), with **AbilityPreviewRenderer** (the animated ability previews; each projectile type's
  colours and preview shape are **AbilityPreviewPalette**'s). Its role line ("Melee · 145 HP ·
  Slow") is `roleLine`.
- **SpriteGenerator** — character sprites for the menus: portraits (one frame, cut once) and the
  few whole sheets that animate.
- **UiActivity** — whether anybody is using the window (animations hold still when not).
- **DropDowns** — every click on a closed drop-down opens it. JavaFX 21's combo box decides open or
  close on the release by a flag that flips whenever its list closes by itself with the mouse over
  the box (another app in front, a click outside the window, Escape); the next click then closed
  the closed list and did nothing to see. `Theme.newScene` installs it on every menu scene, so a
  new combo box needs nothing; the map editor's dialog isn't a menu scene and doesn't have it.
- **ViewportCache**, **LoginForm**.

`screens/WelcomeScreen` is the login screen: the form (with "Remember me", and the last login's name
and server filled in) or, when the app holds a saved login, its card instead — who, on which
server, "Continue as ...", which the app presses for the player as it starts with a remembered
login and once the connection has dropped, and "Use another account". A server that isn't the one
met at its address before gets a card of its own (`showServerChanged`): nothing was sent, both
identities to compare, Back (the default) or "Trust the new identity". The profile has Log out.
A tick box is styled as a field is (`.check-box` in theme.css).

`screens/Chrome` frames every screen after login the way a web site's pages share a header: the
world behind it and a top bar with the game's name, a tab per place to go (Lobbies, Practice, Ranked,
Leaderboard), the sound switch and the player's chip (their profile). A screen hands `Chrome.page`
its body and its tab, and a `leave` for what going elsewhere must undo — the lobby room leaves the
lobby, the ranked queue leaves the queue, the scoreboard goes back to the browser.

## The look: dark glass over the game's own world

The menus look as the match's HUD does — dark translucent panels in Exo 2 over a bright cartoon
world — and leave the colour to the art. Behind every page is the Meadow as the match draws it
(WorldBackdrop), dimmed most along the top and the bottom so the page reads over it; everything on
it is navy glass with a hairline of light round its edge, brightest along the top. Text is a cool
white and greys of it. Two accents: the HUD's gold for the one thing a screen is for (log in,
create, start, find a match) and for what is picked, and a sky blue for links, focus and the
other actions. One typeface, Exo 2 (OFL, in `fonts/`, the HUD's own), in seven weights; the titles
that matter (the game's name, "Victory!") are lettered in its black italic with an outline and a
hard shadow (`displayTitle`). Buttons are lit from above, with a darker lip a press closes. Maps
are shown as models of themselves (MapPreview), and the picked character stands on a patch of the
Meadow's grass drawn the same way.

It replaced a look that read as a children's web site — a cartoon sky with outlined clouds and
green hills, white cards, Nunito, chunky cel buttons — when the player found it cheap; the map
previews were a pixel per tile in each tile's flat colour.

Judge a change with the gallery, which draws every screen in the states a player sees it in,
without a server:

```bash
bazel run //src/main/scala/com/gridgame/client:ui_gallery -- /tmp/ui              # 1440x900
bazel run //src/main/scala/com/gridgame/client:ui_gallery -- /tmp/ui --scale=2    # as on Retina
bazel run //src/main/scala/com/gridgame/client:ui_gallery -- /tmp/ui --lang=ko --only=07_room_host_teams
```

`16_map_previews` is every map's preview at each size the menus draw one (the lobby form's, a lobby
row's, the lobby room's); a new map's arena has to read at all three. The arena is found from the
map's spawn points: a map whose spawns can't walk to all of it shows only what they can.

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
- **Depth is layered backgrounds and borders, not effects.** A button's lip, a field's focus ring,
  a card's edge of light: each is a background inset under the next, or a border. An `-fx-effect`
  (a shadow, a glow) renders its node off screen on every repaint; the first dark menus had a
  blurred shadow on every card and button. The only one left is the combo box popup's. Glass
  is translucent, and a translucent layer shows every layer under it, so a glass surface's edge is
  a border rather than a layer beneath it.
- **The world behind never moves and draws cheaply.** It is one picture, 1280x800 (4MB), drawn
  on the map-art thread as the client starts (and again after a match, which drops it) and shared
  by every screen: scaled to cover the window under one region of gradients, it is a single
  textured quad to repaint. It is clipped to the window and asks the page for no room.
- **No per-frame images.** `SpriteGenerator` keeps portraits — one 128px frame per character,
  cut once on a thread of its own, 64KB each, 7MB for the roster — and at most four whole sheets,
  for what animates, drawn a frame at a time through an ImageView's viewport. The grid used to
  animate every cell from a sheet each (33MB for the roster, every visible cell redrawn six times a
  second); before that it kept 16 `WritableImage`s per character forever: 117MB of heap and 1792
  GPU textures. A sprite drawn at its frame's own size (the picked character, the login screen's)
  is drawn unsmoothed: exactly doubled on a Retina display, one for one on any other.
- **Maps are read once and drawn once a size** (`MapPreview`), off the FX thread: a picture is
  the map's tens of thousands of cells drawn on the CPU, and the lobby list used to parse every
  row's map file each time the list refreshed. The pictures are kept to 12MB, the least lately
  shown dropped first; a lobby row's is 70KB, the lobby form's 0.9MB.
- **`ViewportCache.disable` on any `ScrollPane` around animated content** — every page's, since
  `Widgets.page` puts each page in one. ScrollPane's skin caches its viewport as a bitmap; around
  canvases that animate, every frame re-renders the whole viewport into a texture as big as the
  viewport and draws it again.
- **Screens don't outlive themselves.** `switchScreen` unregisters the listeners that update
  a screen's controls (they held the whole scene graph, and the lobby chat kept rebuilding
  off screen through the next match), and `showGameScene` swaps the hidden stage's scene
  for an empty one and drops the sprite cache, the maps' pictures and the world behind
  (`MatchWindow.open`), so none of the menus stay resident in a match. MapArt lets the tile art
  (8MB with its halvings) go whenever its thread runs out of pictures to draw.

What none of this fixes: a visible JavaFX window on macOS builds up a pool of about 15
window-sized IOSurfaces as it keeps presenting — ~650MB on a 5K display, ~120MB at 1080p —
and only gives it back when the window is hidden (a match start does). It is Core
Animation's pool behind the `CAOpenGLLayer` JavaFX draws into; nothing in JavaFX's API sizes
it, a resize doesn't release it, and JavaFX 21.0.12 and 23.0.2 behave exactly like 21.0.1
(measured interleaved). Fewer presents only delay it; Low quality's 1x menus quarter it. The
GLFW game window's surfaces don't grow. When measuring the menus, keep the window visible
and unobstructed: an occluded window presents less and flatters every number.
