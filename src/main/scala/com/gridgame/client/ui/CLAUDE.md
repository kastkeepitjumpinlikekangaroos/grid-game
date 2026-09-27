# client/ui

The menus, in JavaFX. `ClientMain` (in client/) is the app: it starts up, switches screens
(`switchScreen`, which stops the last screen's animations and drops the client listeners it set),
connects, and shuts down. Each screen is a class in `screens/` with a `show(stage)`, and reaches the
app only through `screens/Screens` — the client, `switchScreen`, the hook that stops it
(`stopCurrentScreen`) and every other screen's `show`. `ClientMain` implements it, so a screen test
can drive a real screen through `app.showLobbyRoom(stage)` and the like.

- **Theme** — the palette, the control styles, and the small widgets and behaviours every screen
  shares (hover effects, styled combos, stat boxes, map previews, `fadeInScene`, `steppedLoop`). A
  screen imports it (`import Theme._`).
- **CharacterSelectionPanel** — the character grid and its detail panel, with
  **AbilityPreviewRenderer** (the animated ability previews; each projectile type's colours and
  preview shape are **AbilityPreviewPalette**'s). Its role line ("Melee · 145 HP · Slow") is
  `roleLine`.
- **SpriteGenerator** — character sheets for the menus, as whole images (never a frame each).
- **UiActivity** — whether anybody is using the window (animations hold still when not).
- **ViewportCache**, **LoginForm**.

## Menus: every changed frame repaints the whole window
JavaFX on macOS presents the whole window for any change, however small, so the menus' cost
is set by how often something on them moves, not by what moves: a 10px square animating at
60 fps on an otherwise empty full-screen window burns ~60% of a core on a 5K display. Rules
the screens follow:
- **Animations are stepped and pause when nobody's looking.** `steppedLoop` (Theme)
  runs looping decoration at 12 fps; the character panel's ability previews run at 30 fps
  with the sprite steps on the same pulses. All of it holds still while the window is in
  the background or has had no input for 30s (`UiActivity`). Measured interleaved on a 5K
  display, character select went from 50% of a core to 40% in use and 1% idle (it used to
  cost the same idle as in use), and the idle login screen from 35-90% to ~2%.
- **No per-frame images.** `SpriteGenerator` keeps whole sheets — thumbnails decoded at the
  size the grid draws them, on JavaFX's background loader — and draws frames with a source
  rectangle. It used to keep 16 `WritableImage`s per character forever: 117MB of heap and
  1792 GPU textures after browsing the grid.
- **Only on-screen grid cells are drawn**, when their frame changes.
- **`ViewportCache.disable` on any `ScrollPane` around animated content.** ScrollPane's skin
  caches its viewport as a bitmap; around canvases that animate, every frame re-renders the
  whole viewport into a texture as big as the viewport and draws it again.
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
