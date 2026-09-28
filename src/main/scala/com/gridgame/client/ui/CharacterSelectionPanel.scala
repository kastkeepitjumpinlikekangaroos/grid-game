package com.gridgame.client.ui

import com.gridgame.common.model._
import com.gridgame.client.i18n.{I18n, Messages}
import com.gridgame.client.ui.Theme.Palette
import com.gridgame.client.ui.Widgets._

import javafx.animation.AnimationTimer
import javafx.geometry.{Insets, Pos}
import javafx.scene.canvas.Canvas
import javafx.scene.control.{Label, ScrollPane}
import javafx.scene.image.{Image, ImageView}
import javafx.scene.layout.{FlowPane, HBox, Priority, StackPane, TilePane, VBox}
import javafx.scene.paint.Color
import javafx.scene.shape.Ellipse

/**
 * The character picker every screen that picks a character uses (lobby, practice, ranked): a card
 * with the roster as a grid of portraits, filtered by playstyle or searched by name, and the picked
 * character in detail — walking round on a patch of grass, their role, and each ability with a
 * preview of it in flight.
 *
 * The grid holds still: a cell is a portrait (SpriteGenerator), and only the one under the mouse
 * walks. Every frame the menus change costs a repaint of the whole window (ui/CLAUDE.md), so the
 * only things that move are that cell, the picked character (6 steps a second) and the three
 * ability previews (30 fps), all on the same pulses, and none of it while nobody is using the
 * window (UiActivity).
 */
class CharacterSelectionPanel(
    getSelectedId: () => Byte,
    onSelect: Byte => Unit
) {

  private var animTick = 0
  private var dirIndex = 0
  private val dirs = Array(Direction.Down, Direction.Left, Direction.Up, Direction.Right)
  // What the walking sprites last showed, so they change only when their frame or facing does
  private var shownStep = -1
  // The character the details are of
  private var shownSelection: Int = -1

  private var filteredChars: Seq[CharacterDef] = CharacterDef.all
  private var selectedCategory: String = "All"
  private var searchQuery: String = ""

  /** A grid cell: the clickable tile, its sprite, and the portrait the sprite rests on. */
  private final class Cell(val pane: StackPane, val view: ImageView) {
    var portrait: Image = _
  }
  private var cells = Map.empty[Byte, Cell]
  private var grid: TilePane = _
  private var countLabel: Label = _
  // The cell under the mouse walks, from its full sheet once that has loaded
  private var hovered: Cell = _
  private var hoveredId: Byte = -1
  private var hoveredSheet: Image = _

  // The picked character, walking round on the stage
  private var heroView: ImageView = _
  private var heroSheet: Image = _
  private var heroPortraitFor: Int = -1
  private var detailNameLabel: Label = _
  // Role, health and pace: "Melee  ·  145 HP  ·  Slow"
  private var detailRoleLabel: Label = _
  private var detailDescLabel: Label = _

  private final class AbilityRow(val key: Label, val name: Label, val cooldown: Label, val canvas: Canvas, val stats: Label)
  private var primaryRow: AbilityRow = _
  private var qRow: AbilityRow = _
  private var eRow: AbilityRow = _

  private var timer: AnimationTimer = _

  // Combat style category definitions (character IDs grouped by playstyle)
  private val categories: Seq[(String, Set[Int])] = Seq(
    ("All", Set.empty[Int]),
    ("Melee", Set(1, 6, 9, 11, 28, 31, 34, 35, 36, 45, 48, 72, 76, 77, 78, 95, 100, 107)),
    ("Ranged", Set(3, 30, 43, 53, 54, 57, 60, 67, 70, 73, 90, 104, 106, 109)),
    ("Assassin", Set(2, 7, 19, 21, 25, 29, 32, 33, 37, 40, 41, 50, 58, 64, 65, 71, 83, 86, 94, 111)),
    ("Tank", Set(15, 23, 26, 39, 42, 44, 51, 59, 75, 79, 85, 87, 93, 98, 105)),
    ("Blaster", Set(4, 5, 10, 12, 13, 14, 16, 17, 22, 27, 47, 49, 55, 63, 66, 68, 81, 82, 89, 101, 102, 110)),
    ("Controller", Set(0, 8, 18, 20, 24, 38, 46, 52, 56, 61, 62, 69, 74, 80, 84, 88, 91, 92, 96, 97, 99, 103, 108))
  )

  private var categoryButtons = Map.empty[String, Label]

  private val CellWidth = 84.0
  private val CellHeight = 100.0
  private val PortraitPx = 64.0
  private val HeroPx = 128.0
  private val DetailWidth = 330.0

  /** The panel: a card to put on the page. */
  def createPanel(): VBox = {
    // Category filter tabs
    val tabsRow = new FlowPane(6, 6)
    tabsRow.setAlignment(Pos.CENTER_LEFT)
    for ((catName, _) <- categories) {
      val tab = text(Messages.t(catName), "filter-chip")
      if (catName == "All") tab.getStyleClass.add("filter-chip-active")
      tab.setOnMouseClicked(_ => selectCategory(catName))
      categoryButtons += (catName -> tab)
      tabsRow.getChildren.add(tab)
    }

    val (searchBox, searchField) = Widgets.searchField(Messages.t("Search characters..."))
    searchBox.setPrefWidth(240)
    searchBox.setMaxWidth(240)
    searchField.textProperty().addListener((_, _, newVal) => {
      searchQuery = newVal.trim.toLowerCase
      rebuildGrid()
    })

    // How many the current filter shows (see rebuildGrid)
    countLabel = muted(Messages.t("{0} characters", CharacterDef.all.size.toString))

    // Every character's cell is made once; a filter shows some of them
    grid = new TilePane(8, 8)
    grid.setPrefTileWidth(CellWidth)
    grid.setPrefTileHeight(CellHeight)
    grid.setPadding(new Insets(2, 6, 6, 2))
    cells = CharacterDef.all.map(d => d.id.id -> makeCell(d)).toMap
    rebuildGrid()

    val gridScroll = new ScrollPane(grid)
    gridScroll.setFitToWidth(true)
    gridScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER)
    gridScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED)
    gridScroll.setPrefHeight(470)
    gridScroll.setMinHeight(260)
    gridScroll.setMaxHeight(Double.MaxValue)
    VBox.setVgrow(gridScroll, Priority.ALWAYS)
    ViewportCache.disable(gridScroll) // a cell animates under the mouse
    // Cells share the grid's width out between them, so the columns run edge to edge
    gridScroll.viewportBoundsProperty().addListener((_, _, bounds) => if (bounds != null) {
      val inner = bounds.getWidth - grid.getPadding.getLeft - grid.getPadding.getRight
      val cols = Math.max(1, ((inner + grid.getHgap) / (CellWidth + grid.getHgap)).toInt)
      val tile = Math.floor((inner - (cols - 1) * grid.getHgap) / cols)
      if (tile >= CellWidth && tile != grid.getPrefTileWidth) {
        grid.setPrefTileWidth(tile)
        cells.values.foreach(_.pane.setPrefWidth(tile))
      }
    })

    val gridSection = new VBox(12, tabsRow, gridScroll)
    HBox.setHgrow(gridSection, Priority.ALWAYS)

    val detailsPanel = createDetails()

    // Initialize detail panel
    updateDetailPanel()

    // Animation timer. Any change to the screen costs a repaint of the whole window, so
    // everything here changes on the same pulses: the ability previews run at 30 fps (every
    // other pulse), and the walking sprites — which only step every 10 pulses — ride along on
    // one of those.
    UiActivity.touch()
    timer = new AnimationTimer {
      override def handle(now: Long): Unit = {
        // Nobody's watching (window in the background, or no input for a while): hold the
        // current frame rather than repaint the window 30 times a second for it
        if (UiActivity.idle) return
        // Picked from outside the panel (the server's word on it, a bench browsing the roster)
        if (getSelectedId() != shownSelection) {
          updateCellStyles()
          updateDetailPanel()
        }
        animTick += 1
        if (animTick % 40 == 0) dirIndex = (dirIndex + 1) % dirs.length
        if ((animTick & 1) == 0) {
          val step = dirIndex * 4 + (animTick / 10) % 4
          if (step != shownStep) {
            shownStep = step
            stepSprites()
          }
          renderAbilityCanvases()
        }
      }
    }
    timer.start()

    val mainRow = new HBox(22, gridSection, detailsPanel)
    mainRow.setAlignment(Pos.TOP_LEFT)
    VBox.setVgrow(mainRow, Priority.ALWAYS)

    val header = row(12, h2(Messages.t("Choose your character")), countLabel, grow(), searchBox)
    header.getStyleClass.add("card-header")
    val panel = card(header, mainRow)
    panel.setMaxHeight(Double.MaxValue)
    panel
  }

  def stop(): Unit = {
    if (timer != null) timer.stop()
  }

  private def makeCell(charDef: CharacterDef): Cell = {
    val charId = charDef.id.id
    val view = new ImageView()
    view.setFitWidth(PortraitPx)
    view.setFitHeight(PortraitPx)
    view.setSmooth(true)
    // A little shadow for them to stand on
    val shadow = new Ellipse(PortraitPx * 0.2, PortraitPx * 0.05)
    shadow.setFill(Color.rgb(28, 53, 88, 0.14))
    StackPane.setAlignment(shadow, Pos.BOTTOM_CENTER)
    StackPane.setMargin(shadow, new Insets(0, 0, PortraitPx * 0.1, 0))
    val sprite = new StackPane(shadow, view)
    sprite.setPrefSize(PortraitPx, PortraitPx)

    val nameLabel = text(I18n.characterName(charDef), "char-name")
    nameLabel.setMaxWidth(CellWidth - 8)

    val content = new VBox(0, sprite, nameLabel)
    content.setAlignment(Pos.CENTER)

    val pane = new StackPane(content)
    pane.getStyleClass.add("char-cell")
    pane.setPrefSize(CellWidth, CellHeight)
    val cell = new Cell(pane, view)

    SpriteGenerator.portrait(charId, Direction.Down) { img =>
      cell.portrait = img
      if (hovered ne cell) view.setImage(img)
    }

    pane.setOnMouseClicked(_ => {
      onSelect(charId)
      updateCellStyles()
      updateDetailPanel()
    })
    pane.setOnMouseEntered(_ => {
      hovered = cell
      hoveredId = charId
      hoveredSheet = SpriteGenerator.sheet(charId)
      shownStep = -1 // step it on the next pulse
    })
    pane.setOnMouseExited(_ => if (hovered eq cell) {
      hovered = null
      hoveredId = -1
      hoveredSheet = null
      view.setViewport(null)
      view.setImage(cell.portrait)
    })
    cell
  }

  /** Step the picked character and the hovered cell to the current frame and facing. */
  private def stepSprites(): Unit = {
    val dir = dirs(dirIndex)
    val frame = (animTick / 10) % 4
    if (heroSheet != null && heroSheet.getProgress >= 1.0 && !heroSheet.isError) {
      if (heroView.getImage ne heroSheet) heroView.setImage(heroSheet)
      heroView.setViewport(SpriteGenerator.frame(dir, frame))
    }
    val cell = hovered
    if (cell != null && hoveredSheet != null && hoveredSheet.getProgress >= 1.0 && !hoveredSheet.isError) {
      if (cell.view.getImage ne hoveredSheet) cell.view.setImage(hoveredSheet)
      cell.view.setViewport(SpriteGenerator.frame(dir, frame))
    }
  }

  private def selectCategory(catName: String): Unit = {
    selectedCategory = catName
    categoryButtons.foreach { case (name, label) =>
      label.getStyleClass.remove("filter-chip-active")
      if (name == catName) label.getStyleClass.add("filter-chip-active")
    }
    rebuildGrid()
  }

  private def rebuildGrid(): Unit = {
    val catFiltered = if (selectedCategory == "All") {
      CharacterDef.all
    } else {
      val idSet = categories.find(_._1 == selectedCategory).get._2
      CharacterDef.all.filter(c => idSet.contains(c.id.id.toInt))
    }
    filteredChars = if (searchQuery.isEmpty) {
      catFiltered
    } else {
      catFiltered.filter(c =>
        I18n.characterName(c).toLowerCase.contains(searchQuery) ||
        c.displayName.toLowerCase.contains(searchQuery))
    }
    grid.getChildren.setAll(filteredChars.map(c => cells(c.id.id).pane): _*)
    countLabel.setText(Messages.t("{0} characters", filteredChars.size.toString))
    updateCellStyles()
  }

  private def updateCellStyles(): Unit = {
    val selectedId = getSelectedId()
    cells.foreach { case (charId, cell) =>
      val classes = cell.pane.getStyleClass
      classes.remove("char-cell-selected")
      if (charId == selectedId) classes.add("char-cell-selected")
    }
  }

  /** The picked character: the stage they walk on, who they are, and their three abilities. */
  private def createDetails(): VBox = {
    heroView = new ImageView()
    heroView.setFitWidth(HeroPx)
    heroView.setFitHeight(HeroPx)
    // Drawn at the sheet's own frame size: its pixels exactly doubled on a Retina display, one
    // for one on any other, where smoothing it up to some other size blurred it
    heroView.setSmooth(false)

    // A patch of grass on a little sky, drawn as the hills behind the menus are
    val stageH = 150.0
    val mound = new StackPane()
    val edge = new Ellipse(96, 22); edge.setFill(Color.web("#4f9c3c"))
    val grass = new Ellipse(94, 20); grass.setFill(Color.web("#86cf5e")); grass.setTranslateY(2.5)
    val lit = new Ellipse(70, 11); lit.setFill(Color.web("#9bdc72")); lit.setTranslateY(-3)
    val shadow = new Ellipse(30, 7); shadow.setFill(Color.rgb(30, 80, 30, 0.35)); shadow.setTranslateY(-2)
    mound.getChildren.addAll(edge, grass, lit, shadow)
    mound.setMaxSize(200, 48)
    StackPane.setAlignment(mound, Pos.BOTTOM_CENTER)
    StackPane.setMargin(mound, new Insets(0, 0, 14, 0))
    StackPane.setAlignment(heroView, Pos.BOTTOM_CENTER)
    StackPane.setMargin(heroView, new Insets(0, 0, 14 + 25 - HeroPx * 0.16, 0))
    val stage = new StackPane(mound, heroView)
    stage.getStyleClass.add("hero-stage")
    stage.setMinHeight(stageH); stage.setPrefHeight(stageH); stage.setMaxHeight(stageH)

    detailNameLabel = text("", "char-title")
    detailRoleLabel = text("", "chip", "chip-sky")
    detailDescLabel = para("", "body")

    primaryRow = createAbilityRow()
    qRow = createAbilityRow()
    eRow = createAbilityRow()

    val abilities = new VBox(12, abilitySection(primaryRow), abilitySection(qRow), abilitySection(eRow))

    val who = new VBox(6, detailNameLabel, detailRoleLabel)
    val details = new VBox(14, stage, who, detailDescLabel, divider(), abilities)
    details.setMinWidth(DetailWidth)
    details.setPrefWidth(DetailWidth)
    details.setMaxWidth(DetailWidth)
    details
  }

  private def drawHeroPortrait(charId: Byte): Unit = {
    if (heroPortraitFor == charId) return
    heroPortraitFor = charId
    heroView.setViewport(null)
    heroView.setImage(null)
    SpriteGenerator.portrait(charId, Direction.Down) { img =>
      // Only until the sheet it walks from has loaded, and only if it is still the one picked
      if (heroPortraitFor == charId && (heroView.getImage eq null)) heroView.setImage(img)
    }
  }

  private def updateDetailPanel(): Unit = {
    val charDef = CharacterDef.get(getSelectedId())
    shownSelection = charDef.id.id
    detailNameLabel.setText(I18n.characterName(charDef))
    detailRoleLabel.setText(CharacterSelectionPanel.roleLine(charDef))
    detailDescLabel.setText(I18n.characterDesc(charDef))
    animTick = 0
    dirIndex = 0
    shownStep = -1
    heroSheet = SpriteGenerator.sheet(charDef.id.id)
    drawHeroPortrait(charDef.id.id)
    stepSprites()

    updateAbilityRow(primaryRow, "LMB", Messages.t("Primary Attack"), charDef.primaryProjectileType, 0, charDef)
    updateAbilityRow(qRow, "Q", I18n.qName(charDef), charDef.qAbility.projectileType, charDef.qAbility.cooldownMs, charDef)
    updateAbilityRow(eRow, "E", I18n.eName(charDef), charDef.eAbility.projectileType, charDef.eAbility.cooldownMs, charDef)

    renderAbilityCanvases()
  }

  private def createAbilityRow(): AbilityRow =
    new AbilityRow(keycap(""), text("", "ability-name"), chip("", "grey", Icons.Clock, Palette.Muted),
      new Canvas(DetailWidth, 44), text("", "small"))

  private def abilitySection(r: AbilityRow): VBox = {
    r.name.setMaxWidth(DetailWidth - 110)
    new VBox(6, row(8, r.key, r.name, grow(), r.cooldown), r.canvas, r.stats)
  }

  private def updateAbilityRow(row: AbilityRow, keybind: String, name: String,
                                projType: Byte, cooldownMs: Int, charDef: CharacterDef): Unit = {
    row.key.setText(keybind)
    row.name.setText(name)

    if (cooldownMs > 0) {
      row.cooldown.setText(seconds(cooldownMs))
      row.cooldown.setVisible(true)
    } else {
      row.cooldown.setVisible(false)
    }

    // Build stats text
    val stats = new StringBuilder()
    val ability = if (keybind == "Q") Some(charDef.qAbility) else if (keybind == "E") Some(charDef.eAbility) else None

    ability match {
      case Some(ab) =>
        ab.castBehavior match {
          case PhaseShiftBuff(dur) =>
            stats.append(s"${dur / 1000}s duration")
          case DashBuff(maxDist, _, _) =>
            stats.append(s"${maxDist} cells")
          case TeleportCast(maxDist) =>
            stats.append(s"${maxDist} cells")
          case BarrierCast(dur) =>
            stats.append(s"${seconds(dur)}, ${Barrier.WIDTH.toInt} wide, blocks shots")
          case TrapCast(trapType, range) =>
            appendTrapStats(stats, trapType, range)
          case _ =>
            appendProjectileStats(stats, ab.projectileType, ab.damage, ab.maxRange, ab.castBehavior)
        }
      case None =>
        // Primary attack
        appendProjectileStats(stats, projType, 0, 0, StandardProjectile)
    }

    row.stats.setText(stats.toString)
  }

  /** A hold's length as the stats line shows it: to a tenth of a second unless it is whole.
    * Divided down to whole seconds, a 0.7s freeze read "Freeze 0s". */
  private def seconds(ms: Int): String =
    if (ms % 1000 == 0) s"${ms / 1000}s" else f"${ms / 1000.0}%.1fs"

  /** A trap's line: how far it is thrown, how long before it is live, and what it does to whoever
    * steps on it — the numbers that decide whether the ability is worth taking. */
  private def appendTrapStats(stats: StringBuilder, trapType: Byte, range: Int): Unit = {
    val tDef = TrapDef.get(trapType)
    stats.append(s"$range rng")
    if (tDef == null) return
    if (tDef.damage > 0) stats.append(s"  ${tDef.damage} dmg")
    tDef.explosion.foreach(e =>
      stats.append(s"  ${e.centerDamage}-${e.edgeDamage} blast ${e.blastRadius.toInt} cells"))
    tDef.effects.foreach {
      case Stun(dur) => stats.append(s"  Stun ${seconds(dur)}")
      case Freeze(dur) => stats.append(s"  Freeze ${seconds(dur)}")
      case Root(dur) => stats.append(s"  Root ${seconds(dur)}")
      case Slow(dur, _) => stats.append(s"  Slow ${seconds(dur)}")
      case Burn(total, dur, _) => stats.append(s"  Burn $total over ${seconds(dur)}")
      case Poison(total, dur, _) => stats.append(s"  Poison $total over ${seconds(dur)}")
      case Push(_) => stats.append("  Push")
      case _ =>
    }
    stats.append(f"  arms ${tDef.armDelayMs / 1000.0}%.1fs  ${tDef.maxActive} at once")
  }

  private def appendProjectileStats(stats: StringBuilder, projType: Byte, abilityDamage: Int,
                                     abilityRange: Int, castBehavior: CastBehavior): Unit = {
    val pDef = ProjectileDef.get(projType)

    // Damage
    pDef.distanceDamageScaling match {
      case Some(dds) =>
        stats.append(s"${dds.baseDamage}-${dds.maxDamage} dmg")
      case None =>
        pDef.chargeDamageScaling match {
          case Some(cs) =>
            stats.append(s"${cs.min.toInt}-${cs.max.toInt} dmg")
          case None =>
            if (pDef.damage > 0) stats.append(s"${pDef.damage} dmg")
        }
    }

    // Range
    if (stats.nonEmpty) stats.append("  ")
    pDef.chargeRangeScaling match {
      case Some(cs) =>
        stats.append(s"${cs.min.toInt}-${cs.max.toInt} rng")
      case None =>
        if (pDef.maxRange > 0) stats.append(s"${pDef.maxRange} rng")
    }

    // Speed
    val speed = pDef.speedMultiplier
    val speedStr = if (speed < 0.5) "Slow" else if (speed <= 0.8) "Med" else "Fast"
    if (stats.nonEmpty) stats.append("  ")
    stats.append(speedStr)

    // Special effects
    pDef.onHitEffects.foreach {
      case PullToOwner => stats.append("  Pull")
      case Freeze(dur) => stats.append(s"  Freeze ${seconds(dur)}")
      case Stun(dur) => stats.append(s"  Stun ${seconds(dur)}")
      case Push(dist) => stats.append(s"  Push")
      case TeleportOwnerBehind(_, _) => stats.append("  Teleport")
      case LifeSteal(pct) => stats.append(s"  LifeSteal ${pct}%")
      case Burn(_, _, _) => stats.append("  Burn")
      case Poison(total, _, _) => stats.append(s"  Poison $total")
      case SpeedBoost(_) => stats.append("  Speed")
      case VortexPull(_, _) => stats.append("  Vortex")
      case Root(dur) => stats.append(s"  Root ${seconds(dur)}")
      case Slow(dur, _) => stats.append(s"  Slow ${seconds(dur)}")
    }

    if (pDef.aoeOnHit.isDefined || pDef.aoeOnMaxRange.isDefined) stats.append("  AoE")
    if (pDef.explosionConfig.isDefined) stats.append("  Explodes")
    if (pDef.passesThroughWalls) stats.append("  Wall-pierce")

    castBehavior match {
      case FanProjectile(count, angle) =>
        val isCircle = angle >= 2 * Math.PI - 0.1
        if (isCircle) stats.append(s"  ${count}-way")
        else stats.append(s"  ${count}-fan")
      case _ =>
    }
  }

  private def renderAbilityCanvases(): Unit = {
    val charDef = CharacterDef.get(getSelectedId())
    AbilityPreviewRenderer.render(primaryRow.canvas.getGraphicsContext2D,
      charDef.primaryProjectileType, StandardProjectile,
      animTick, primaryRow.canvas.getWidth, primaryRow.canvas.getHeight)
    AbilityPreviewRenderer.render(qRow.canvas.getGraphicsContext2D,
      charDef.qAbility.projectileType, charDef.qAbility.castBehavior,
      animTick, qRow.canvas.getWidth, qRow.canvas.getHeight)
    AbilityPreviewRenderer.render(eRow.canvas.getGraphicsContext2D,
      charDef.eAbility.projectileType, charDef.eAbility.castBehavior,
      animTick, eRow.canvas.getWidth, eRow.canvas.getHeight)
  }
}

object CharacterSelectionPanel {
  /** What the character is for and the two numbers their role sets: "Melee  ·  145 HP  ·  Slow". */
  def roleLine(d: CharacterDef): String =
    Seq(Messages.t(d.role.name), Messages.t("{0} HP", d.maxHealth.toString), paceName(d.moveSpeed))
      .mkString("  ·  ")

  /** A walking pace in words, against the roster's: ranged characters (the base rate) are the
    * quick ones, skirmishers a little slower, melee slowest. The roles are only a few percent
    * apart, so a word per pace says more than a number nobody can compare. */
  def paceName(moveSpeed: Float): String =
    if (moveSpeed >= 0.99f) Messages.t("Fast")
    else if (moveSpeed >= 0.955f) Messages.t("Medium speed")
    else Messages.t("Slow")
}
