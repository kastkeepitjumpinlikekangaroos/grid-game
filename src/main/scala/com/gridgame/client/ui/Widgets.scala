package com.gridgame.client.ui

import com.gridgame.client.ui.Theme.{Fonts, Palette}

import javafx.geometry.{Insets, Pos}
import javafx.scene.Node
import javafx.scene.control.{Button, Label, PasswordField, ScrollPane, TextField, Tooltip}
import javafx.scene.layout.{HBox, Priority, Region, StackPane, VBox}
import javafx.scene.paint.{Color, Paint}
import javafx.scene.shape.{Circle, StrokeLineJoin, StrokeType}
import javafx.scene.text.Text

/**
 * The widgets every screen is built from, each a node carrying theme.css's classes: text in the
 * type scale, buttons, cards, chips, fields, avatars, stat tiles, MapleStory's outlined titles,
 * and the page every screen sits on — the sky, a bar across the top, a column of content. A
 * screen imports them (`import Widgets._`).
 */
private[client] object Widgets {

  // ── Text ───────────────────────────────────────────────────────────────

  def text(s: String, classes: String*): Label = {
    val l = new Label(s)
    l.getStyleClass.addAll(classes: _*)
    l
  }
  def h1(s: String): Label = text(s, "h1")
  def h2(s: String): Label = text(s, "h2")
  def h3(s: String): Label = text(s, "h3")
  def muted(s: String): Label = text(s, "muted")
  def small(s: String): Label = text(s, "small")
  def eyebrow(s: String): Label = text(s, "eyebrow")

  /** Text that wraps at its container's width. */
  def para(s: String, classes: String*): Label = {
    val l = text(s, classes: _*)
    l.setWrapText(true)
    l.setMinHeight(Region.USE_PREF_SIZE)
    l
  }

  /**
   * A title as MapleStory letters its own: a fat outline in `ink` round a `face` fill, and the
   * same shape in `ink` dropped under it for a hard shadow. For the one title a screen has.
   */
  def displayTitle(s: String, size: Double, face: Paint, ink: Color): StackPane = {
    def layer(fill: Paint, cls: String): Text = {
      val t = new Text(s)
      // Inline: the sheet's font on .root outranks one set from code (see theme.css)
      t.setStyle(s"-fx-font-family: '${Fonts.Black}'; -fx-font-size: ${size}px;")
      t.setFill(fill)
      t.setStroke(ink)
      t.setStrokeWidth(Math.max(3, size * 0.085))
      t.setStrokeType(StrokeType.OUTSIDE)
      t.setStrokeLineJoin(StrokeLineJoin.ROUND)
      t.getStyleClass.add(cls)
      t
    }
    val shadow = layer(ink, "display-shadow")
    shadow.setTranslateY(Math.max(3, size * 0.07))
    val title = new StackPane(shadow, layer(face, "display-face"))
    title.setPadding(new Insets(size * 0.08, size * 0.1, size * 0.14, size * 0.1))
    title
  }

  // ── Buttons ────────────────────────────────────────────────────────────

  /** A button of one of the sheet's kinds (sun, sky, leaf, berry, plain, ghost), "lg" or "sm" sized. */
  def button(s: String, kind: String, size: String = ""): Button = {
    val b = new Button(s)
    b.getStyleClass.addAll("btn", s"btn-$kind")
    if (size.nonEmpty) b.getStyleClass.add(s"btn-$size")
    b
  }

  /** A button with an icon before its text. */
  def button(s: String, kind: String, icon: Icons.Icon, iconColour: Color): Button = {
    val b = button(s, kind)
    b.setGraphic(Icons.node(icon, 16, iconColour))
    b
  }

  /** A round button with only an icon, explained by its tooltip. */
  def iconButton(icon: Icons.Icon, tip: String, colour: Color = Palette.Muted): Button = {
    val b = new Button()
    b.getStyleClass.addAll("btn", "btn-ghost", "btn-icon")
    b.setGraphic(Icons.node(icon, 20, colour))
    b.setTooltip(new Tooltip(tip))
    b
  }

  // ── Surfaces ───────────────────────────────────────────────────────────

  def card(children: Node*): VBox = {
    val c = new VBox(14)
    c.getStyleClass.add("card")
    c.getChildren.addAll(children: _*)
    c
  }

  /** A card's first line: its title, and whatever sits at the right end of it. */
  def cardHeader(title: String, right: Node*): HBox = {
    val h = new HBox(h2(title), grow())
    h.getStyleClass.add("card-header")
    h.getChildren.addAll(right: _*)
    h
  }

  def divider(): Region = {
    val r = new Region()
    r.getStyleClass.add("divider")
    r
  }

  /** Space that takes up what is left of a row. */
  def grow(): Region = {
    val r = new Region()
    HBox.setHgrow(r, Priority.ALWAYS)
    r
  }

  def row(spacing: Double, children: Node*): HBox = {
    val h = new HBox(spacing)
    h.setAlignment(Pos.CENTER_LEFT)
    h.getChildren.addAll(children: _*)
    h
  }

  def column(spacing: Double, children: Node*): VBox = {
    val v = new VBox(spacing)
    v.getChildren.addAll(children: _*)
    v
  }

  /** What a list says when it has nothing in it. */
  def emptyState(message: String, icon: Icons.Icon): VBox = {
    val v = new VBox(10, Icons.node(icon, 32, Color.web("#9ab0c9")), muted(message))
    v.setAlignment(Pos.CENTER)
    v.setPadding(new Insets(36, 16, 36, 16))
    v
  }

  // ── Small parts ────────────────────────────────────────────────────────

  /** A rounded tag in a tone (sky, leaf, berry, gold, violet, grey; none for the plain one). */
  def chip(s: String, tone: String = ""): Label = {
    val l = text(s, "chip")
    if (tone.nonEmpty) l.getStyleClass.add(s"chip-$tone")
    l
  }

  def chip(s: String, tone: String, icon: Icons.Icon, iconColour: Color): Label = {
    val l = chip(s, tone)
    l.setGraphic(Icons.node(icon, 13, iconColour))
    l
  }

  /** A key on the keyboard: the one an ability is on. */
  def keycap(s: String): Label = text(s, "keycap")

  /** A finishing place: gold, silver and bronze for the first three. */
  def medal(rank: Int): Label = {
    val l = text(rank.toString, "medal")
    if (rank >= 1 && rank <= 3) l.getStyleClass.add(s"medal-$rank")
    l
  }

  /** A player's avatar: the first letter of their name on their colour, or a robot for a bot. */
  def avatar(name: String, size: Double, bot: Boolean = false): StackPane = {
    val disc = new Circle(size / 2, if (bot) Color.web("#8aa0bb") else Palette.forName(name))
    val face: Node =
      if (bot) Icons.node(Icons.Bot, size * 0.58, Color.WHITE)
      else {
        val initial = text(name.take(1).toUpperCase, "avatar-initial")
        initial.setStyle(s"-fx-font-size: ${size * 0.46}px;")
        initial
      }
    val a = new StackPane(disc, face)
    a.setMinSize(size, size); a.setMaxSize(size, size)
    a
  }

  /** A number with what it counts under it; returns the tile and the number's label. */
  def statTile(label: String, value: String, valueTone: String = ""): (VBox, Label) = {
    val v = text(value, "stat-value")
    if (valueTone.nonEmpty) v.getStyleClass.add(s"text-$valueTone")
    val tile = new VBox(v, text(label, "stat-label"))
    tile.getStyleClass.add("stat-tile")
    HBox.setHgrow(tile, Priority.ALWAYS)
    tile.setMaxWidth(Double.MaxValue)
    (tile, v)
  }

  // ── Tables ─────────────────────────────────────────────────────────────

  /** A table's column: its heading, and its width (0 for the one that takes what is left). */
  final case class Col(title: String, width: Double, align: Pos = Pos.CENTER_LEFT)

  private def cell(col: Col, content: Node): HBox = {
    val c = new HBox(content)
    c.setAlignment(col.align)
    if (col.width > 0) { c.setMinWidth(col.width); c.setPrefWidth(col.width); c.setMaxWidth(col.width) }
    else { HBox.setHgrow(c, Priority.ALWAYS); c.setMinWidth(0); c.setMaxWidth(Double.MaxValue) }
    c
  }

  /** The row of column headings over a table. */
  def tableHead(cols: Seq[Col]): HBox = {
    val h = new HBox()
    h.getStyleClass.add("table-head")
    cols.foreach(c => h.getChildren.add(cell(c, eyebrow(c.title))))
    h
  }

  /** A table's row: a node per column, in its column's width. */
  def tableRow(cols: Seq[Col], cells: Seq[Node]): HBox = {
    val r = new HBox()
    r.getStyleClass.addAll("row", "row-hover")
    cols.zip(cells).foreach { case (c, n) => r.getChildren.add(cell(c, n)) }
    r
  }

  // ── Fields ─────────────────────────────────────────────────────────────

  def field(prompt: String): TextField = {
    val f = new TextField()
    f.setPromptText(prompt)
    f.setMaxWidth(Double.MaxValue)
    f
  }

  def passwordField(prompt: String): PasswordField = {
    val f = new PasswordField()
    f.setPromptText(prompt)
    f.setMaxWidth(Double.MaxValue)
    f
  }

  /** A form control with its label above it. */
  def labeled(label: String, control: Node): VBox = {
    val v = new VBox(6, text(label, "field-label"), control)
    v.setFillWidth(true)
    v
  }

  /** A search box: a field with a magnifying glass in it. */
  def searchField(prompt: String): (StackPane, TextField) = {
    val f = field(prompt)
    f.getStyleClass.add("search-field")
    val glass = Icons.node(Icons.Search, 16, Palette.Muted)
    StackPane.setAlignment(glass, Pos.CENTER_LEFT)
    StackPane.setMargin(glass, new Insets(0, 0, 0, 13))
    (new StackPane(f, glass), f)
  }

  // ── The page ───────────────────────────────────────────────────────────

  /**
   * A screen: the sky, `topBar` across the top, and `body` under it in a centred column at most
   * `maxWidth` wide, scrolling when it is taller than the window. Returns the root, and the part
   * that changes from page to page (for Theme.showPage to bring in). `sky` is the backdrop, for a
   * screen that stands characters on it.
   */
  def page(topBar: Node, body: Region, maxWidth: Double, sky: SkyBackdrop = new SkyBackdrop()): (StackPane, Node) = {
    body.setMaxWidth(maxWidth)
    val column = new StackPane(body)
    StackPane.setAlignment(body, Pos.TOP_CENTER)
    column.setPadding(new Insets(24, 32, 32, 32))
    // Tall enough to hold the body, so the scroll pane scrolls rather than squeezing it
    column.setMinHeight(Region.USE_PREF_SIZE)
    val scroll = new ScrollPane(column)
    scroll.setFitToWidth(true)
    scroll.setFitToHeight(true)
    scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER)
    ViewportCache.disable(scroll) // pages with the character panel animate inside it
    val frame = new VBox(topBar, scroll)
    VBox.setVgrow(scroll, Priority.ALWAYS)
    val root = new StackPane(sky, frame)
    root.getStyleClass.add("page")
    (root, column)
  }

  /** The bar across the top of a page: `left` from the left edge, `right` against the right. */
  def topBar(left: Seq[Node], right: Seq[Node]): HBox = {
    val bar = new HBox()
    bar.getStyleClass.add("topbar")
    bar.getChildren.addAll(left: _*)
    bar.getChildren.add(grow())
    bar.getChildren.addAll(right: _*)
    bar.setMinHeight(Region.USE_PREF_SIZE)
    bar
  }

  /** The game's name and its wizard, at the left of the top bar. */
  def brand(onClick: () => Unit): HBox = {
    val mascot = SpriteGenerator.portraitView(com.gridgame.common.model.CharacterId.Wizard.id,
      com.gridgame.common.model.Direction.Down, 40)
    val name = text("Grid Game", "brand-name")
    val b = new HBox(6, mascot, name)
    b.setAlignment(Pos.CENTER_LEFT)
    b.setPadding(new Insets(0, 18, 0, 0))
    if (onClick != null) {
      b.setStyle("-fx-cursor: hand;")
      b.setOnMouseClicked(_ => onClick())
    }
    b
  }
}
