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
  passes over the tile atlas (`sprites/tiles.png`) in the match's projection — the ground, with the
  soft edge where two kinds of it meet (`GroundEdges.shade`, which the match's GroundEdgePainter is
  in a shader) and the shadows blocks cast, then blocks and props a diagonal at a time, far to
  near — for any window of its cells, optionally standing on a slab of its own ground. Small
  pictures are filtered, not picked: the atlas is halved four times over and each pixel averages a
  grid of samples.
- **MapPreview** — the maps for the menus, as models of themselves on a dark stage: where its
  matches are played (every cell its spawn points can walk to, `MapArt.playArea`) and a rim of its
  border. Drawn once per size shown, at twice it, on a thread of its own, and kept to 12MB; the
  thread draws what is wanted soonest first (`Now`: a picture a page shows on its own; `Strip`: a
  carousel's little ones; `Ahead`: the maps a carousel may step to next), and a picture asked for
  sooner than it first was moves up. Also `patch`, a few cells of grass for a character to stand
  on, and `size`, how many cells a map is. The whole arena, always: the lobby form once cut a
  square map to fill its wide frame, which hid half of it; the border (a forest, the sea, the
  void) is scenery, and shown whole it left the arena small.
- **MapCarousel** — the maps to pick from, wherever a map is picked: the picked one big between
  an arrow either side (round from the last to the first), its name and size ("Medium map · 140 ×
  140") and how far along it is ("12 / 19"), and a strip of every map under it that slides to
  keep the picked one in its middle (a click picks, a double click settles, the wheel or a trackpad
  slides it; ← → Home End and Enter while it has the focus). It replaced a drop-down of the maps'
  names, where a map had to be picked to be seen. `Form` is the lobby form's (the map 320x180, as the form drew it before,
  the strip's 64x36), `Wide` the map picker's (544x306, the strip's 96x54 with their names under
  them). A map stepped to shows the strip's picture blown up for the 30-120ms its own takes to draw
  (they are framed alike); the maps either side are drawn ahead once the player steps (as it opens,
  in the picker).
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
Leaderboard), a flag to report a problem, the settings (sliders), the sound switch and the player's chip (their profile). A screen hands `Chrome.page`
its body and its tab, and a `leave` for what going elsewhere must undo — the lobby room leaves the
lobby, the ranked queue leaves the queue, the scoreboard goes back to the browser.

`screens/MapPickerDialog` is the maps for a lobby's host, from the map chip in the lobby room's
heading or the map's picture beside it: a `Wide` carousel on a card over the room, as the settings'
card is. Browsing it changes nothing; "Use this map", a double click or Enter sends the one picked
(nothing if it is the lobby's already), and Cancel, the cross or Escape leave the lobby's. The map
chip is a button drawn as the drop-down chips beside it are (`.chip-button`), and a guest's is
only a chip. An update sent for any of the host's settings carries the map last picked there,
not the client's copy of the server's, which lags a pick by a round trip.
`LobbyRoomScreenTest` and `LobbyBrowserScreenTest` click both carousels, `ui/MapCarouselTest`
the carousel itself.

`screens/SettingsDialog` is the game's settings, from the sliders in the top bar (and beside the
language on the login screen): a card over the page as a report's is, with the graphics quality,
the resolution the world is drawn at and the camera's zoom in a match (gl/CLAUDE.md, *Graphics
quality tiers*), each taken and saved as it is picked. The gallery's `23_settings` draws it. In a
match the same three are in the menu Esc brings up, which the renderer draws (render/CLAUDE.md, *The
menu Esc brings up*).

`screens/ReportDialog` is a problem report as the player makes it (design/diagnostics.md, *What the
player sees*): not a screen but a card over whatever page is showing, on a shade that takes every
click (`app.showProblemReport(stage, about)`: after a match, after a match that failed, after a crash,
or anything at all). When it happened is picked on a timeline of the match (a `Pane` laying out a
track, the moments on it as dots with tooltips, minute labels and the picked moment), or from the list
of what the game noticed under it. Its heading and buttons stay put and what is between them scrolls, the scroll
pane kept to the height of what is in it (its own guess, made at another width, came out a hand's
breadth too tall). The scoreboard asks for one in a gold callout when the player marked moments or the
client noticed trouble in the match. `ReportDialog.toast` is a line at the foot of any page for a few
seconds (a report sent later). The ui gallery draws every state of them: `17_scoreboard_marked` to
`22_report_after_crash`.

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
row's, the lobby room's, and the two carousels' strips); a new map's arena has to read at all of
them. The arena is found from the map's spawn points: a map whose spawns can't walk to all of it
shows only what they can. `04b_lobbies_map_picked` is the form's carousel with a map further along
picked, and `07c_room_host_map_picker` the host's map picker open over the room.

1440x900 is a small laptop's window, and the pages are laid out for it: the lobby room fits it
without scrolling, for the host too (the match's settings are chips in its heading, not a card of
their own: drop-downs, but for the map's, which opens the map picker over the room), and so does a
scoreboard of six; a bigger one scrolls a little.

The host's Add Bot and Remove Bot are above the lobby room's roster, which grows under them as bots
join (`07b_room_host_bots`: a free-for-all has 32 seats). Below it, each bot that wrapped it onto
another line pushed them down from under the pointer of a host adding several in a row.
`LobbyRoomScreenTest` clicks each until the lobby is full, then empty, and holds both still.

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
- **Nothing is stroked inside or outside its edge.** JavaFX draws a shape's or a text's
  `StrokeType.INSIDE` or `OUTSIDE` stroke by taking the shape's area from (or intersecting it with)
  a centred stroke twice as wide, area geometry on every curve (`Area.subtract`), each time it
  draws it, cached nowhere. The display titles' outline was drawn so: 57-317ms a title at 2x, on
  every frame of the scoreboard's entrance. An outline outside the letters is a centred stroke
  twice as wide under the fill, which hides its inner half (`Widgets.displayTitle`): 4-6ms, and the
  same picture but for the faint seam the old one left between the outline and the shadow on a
  bright ground. `ScoreboardScreenTest` holds the scoreboard to it.
- **The world behind never moves and draws cheaply.** It is one picture, 1280x800 (4MB), drawn
  on the map-art thread as the client starts and kept through every match (*Coming back from a
  match*), and shared by every screen: scaled to cover the window under one region of gradients,
  it is a single textured quad to repaint. It is clipped to the window and asks the page for no
  room.
- **No per-frame images.** `SpriteGenerator` keeps portraits — one 128px frame per character,
  cut once on a thread of its own, 64KB each, 7MB for the roster — and at most four whole sheets,
  for what animates, drawn a frame at a time through an ImageView's viewport. The grid used to
  animate every cell from a sheet each (33MB for the roster, every visible cell redrawn six times a
  second); before that it kept 16 `WritableImage`s per character forever: 117MB of heap and 1792
  GPU textures. A sprite drawn at its frame's own size (the picked character, the login screen's)
  is drawn unsmoothed: exactly doubled on a Retina display, one for one on any other.
- **Maps are drawn once a size** (`MapPreview`), off the FX thread: a picture is the map's tens
  of thousands of cells drawn on the CPU (a strip's 2-10ms, the lobby form's 10-50ms, the map
  picker's 50-230ms, warm), and the lobby list used to parse every row's map file each time the
  list refreshed. The pictures are kept to 12MB, the least lately shown dropped first; a lobby row's
  is 70KB, the lobby form's 0.9MB, the picker's 2.7MB, a carousel's strip 0.7-1.6MB all told. Only
  the last four maps read are kept (a big one is a quarter of a megabyte read, and a strip reads
  every map); every map read used to be kept for good, through every match. A match's start drops
  the pictures and every one not yet drawn.
- **No rounded clips.** A clip that isn't a plain rectangle renders its node off screen on every
  repaint, as an effect does. The map frames each had one, to round a picture's corners that were
  already clear (a map is fitted a twentieth in from the frame's edges, and a diamond leaves a
  box's corners empty); without them only the frames' outermost edge changed, by at most 15 levels
  in 255, and the lobby browser with the form's carousel (twenty more frames) renders as fast as it
  did with the drop-down: 5.8-6.8ms a full render against 6.0-7.0 (a throwaway bench, the two
  builds alternated). The picture that slides in has a square clip, which is a scissor.
- **`ViewportCache.disable` on any `ScrollPane` around animated content** — every page's, since
  `Widgets.page` puts each page in one. ScrollPane's skin caches its viewport as a bitmap; around
  canvases that animate, every frame re-renders the whole viewport into a texture as big as the
  viewport and draws it again.
- **Screens don't outlive themselves.** `switchScreen` unregisters the listeners that update
  a screen's controls (they held the whole scene graph, and the lobby chat kept rebuilding
  off screen through the next match), and `showGameScene` swaps the hidden stage's scene
  for an empty one and drops the sprite cache and the maps' pictures (`MatchWindow.open`), so
  none of the menus stay resident in a match but the world behind (4MB) and the portraits the
  scoreboard will show (*Coming back from a match*). MapArt lets the tile art (8MB with its
  halvings) go whenever its thread runs out of pictures to draw.

What none of this fixes: a visible JavaFX window on macOS builds up a pool of about 15
window-sized IOSurfaces as it keeps presenting — ~650MB on a 5K display, ~120MB at 1080p —
and only gives it back when the window is hidden (a match start does). It is Core
Animation's pool behind the `CAOpenGLLayer` JavaFX draws into; nothing in JavaFX's API sizes
it, a resize doesn't release it, and JavaFX 21.0.12 and 23.0.2 behave exactly like 21.0.1
(measured interleaved). Fewer presents only delay it; Low quality's 1x menus quarter it. The
GLFW game window's surfaces don't grow. When measuring the menus, keep the window visible
and unobstructed: an occluded window presents less and flatters every number.

## Coming back from a match

A match hides the stage, which gives those surfaces back, and macOS destroys the window behind it:
shown again at the game over, it is a new window, and a new window is slowest over its first frames.
So what the screen after a match needs is ready before the window is, and it comes up on that
screen whole, with nothing left to move (`MatchWindow.close`, for every way out of a match):

- **The screen is built on the stage while it is still hidden**, then the match's window is retired
  and the stage shown. `Theme.showPage` brings nothing in on a stage that isn't showing and styles
  the page there and then (`applyCss`), so the first frame only paints it. A page brought in faded
  and rose over the new window's first frames, the first of them styling it too.
- **The world behind is kept through the match** (`WorldBackdrop`, 4MB). Drawn again after every
  match, the scoreboard came up on the night, and the world faded in from 0.24-0.37s after the game
  over.
- **The portraits it will show are cut while the match is played.** The match's window calls
  `ScoreboardScreen.prepare` every two seconds (everyone in the match, and the top bar's wizard),
  since a match starts by dropping every portrait the menus had. Cut as the scoreboard was built,
  they came in over the quarter second after it.
- **Its titles' outline is a centred stroke** (*Nothing is stroked inside or outside its edge*,
  above). Drawn outside the letters, it took 57-317ms of every frame at 2x.

The real client (a throwaway `ClientMain` subclass that signs up, makes a lobby, adds five bots
and starts it) was run through one-minute matches against a local server, old build and new
alternately, on this machine. Numbers are ms after the client heard the game over:

| | 1x before | 1x after | 2x before | 2x after |
|---|---|---|---|---|
| the window's first frame on screen | 140-164 | 89-107 | 150-159 | 104-133 |
| the world behind and every portrait there | 243-268, then the 0.3s fade | in the first frame | 330-369, then the fade | in the first frame |
| frames over 33ms in the 3s after | 12-14, the worst 119-152 | 0-1, at most 42 | 10-12, the worst 172-302 | 1, at most 66 |
| the last of them | 620-648 | 92-107 | 736-827 | 104-133 |
| painting in those 3s | 539-551 | 14-33 | 628-734 | 21-36 |
| the FX thread kept from input, all told | 447-495 | 50-65 | 557-713 | 66-85 |

The match's own frames were the same in both builds; the portraits cut as it goes cost it nothing
that showed. 1x is how Low quality draws the menus, 2x how the other qualities draw them on a
Retina display.

Left as it is:
- **The new window's first frame**, the one slow frame still left: up to 40ms of it is the present,
  as the window's surfaces are made again. Keeping the stage shown under the match would keep them,
  about 650MB at 5K, for the whole match.
- **Hiding the match's window, 17-60ms, before the stage is shown.** Showing the stage first would
  save that wait, but hide-then-show is the order the fix for the crash in macOS's own full screen
  was checked in (gl/CLAUDE.md, *A window macOS may still be using is retired*).

To see where a menu's frames go, run the client with
`--jvm_flag=-Djavafx.pulseLogger=true --jvm_flag=-Djavafx.pulseLogger.threshold=20`. Every pulse
over 20ms is printed with its CSS pass, layout, painting and presenting, in `client.log` as well.
Keep the screen unlocked while you measure: when it is locked, the pulse runs on a timer and the
numbers mean nothing.
