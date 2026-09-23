package com.gridgame.client.ui

import com.gridgame.common.model._
import com.gridgame.client.i18n.{I18n, Messages}
import javafx.animation.AnimationTimer
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.canvas.Canvas
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.TextField
import javafx.scene.effect.DropShadow
import javafx.scene.layout.FlowPane
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.FontWeight

/**
 * Shared character selection panel used in both lobby and ranked queue screens.
 * Shows a filterable, categorized grid of all characters with a detail panel for the selected character.
 */
class CharacterSelectionPanel(
    getSelectedId: () => Byte,
    onSelect: Byte => Unit
) {

  private var animTick = 0
  private var dirIndex = 0
  private val dirs = Array(Direction.Down, Direction.Left, Direction.Up, Direction.Right)

  // What the sprite canvases last showed. Every frame this screen changes costs a repaint
  // of the whole window, so sprites are redrawn only when their animation frame, facing or
  // character actually changes (6 times a second), not on every pulse.
  private var shownFrame = -1
  private var shownDir = -1
  private var shownSelection: Int = -1
  private var gridDirty = true

  private val cellBgColor = Color.web("#1a1a30")
  private val previewRingColor = Color.web("#3a3a5e")

  // Current filtered characters
  private var filteredChars: Seq[CharacterDef] = CharacterDef.all
  private var selectedCategory: String = "All"
  private var searchQuery: String = ""

  // Grid cell tracking (only for currently displayed chars)
  private var cellCanvases = Map.empty[Byte, Canvas]     // charId -> canvas
  private var cellPanes = Map.empty[Byte, StackPane]     // charId -> pane
  private var gridContainer: GridPane = _
  private var gridScroll: ScrollPane = _
  private var countLabel: Label = _

  // Detail panel elements
  private var detailPreviewCanvas: Canvas = _
  private var detailNameLabel: Label = _
  // Role, health and pace: "Melee  ·  145 HP  ·  Slow"
  private var detailRoleLabel: Label = _
  private var detailDescLabel: Label = _

  // Ability row elements
  private case class AbilityRow(
      nameLabel: Label,
      cooldownLabel: Label,
      canvas: Canvas,
      statsLabel: Label
  )
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

  // Category tab buttons
  private var categoryButtons = Map.empty[String, Label]

  private val sectionHeaderStyle = "-fx-text-fill: #99aabb; -fx-font-size: 13; -fx-font-weight: bold;"
  private val cardBgSubtle = "-fx-background-color: #1c1c34; -fx-background-radius: 12; -fx-border-color: rgba(255,255,255,0.05); -fx-border-radius: 12; -fx-border-width: 1; -fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.3), 12, 0, 0, 4);"

  private val cellBaseStyle = "-fx-background-color: #1c1c34; -fx-background-radius: 8; -fx-border-color: rgba(255,255,255,0.08); -fx-border-radius: 8; -fx-border-width: 1; -fx-cursor: hand;"
  private val cellHoverStyle = "-fx-background-color: #1c1c34; -fx-background-radius: 8; -fx-border-color: rgba(74, 158, 255, 0.3); -fx-border-radius: 8; -fx-border-width: 1; -fx-cursor: hand;"
  private val cellSelectedStyle = "-fx-background-color: #1c1c34; -fx-background-radius: 8; -fx-border-color: #4a9eff; -fx-border-radius: 8; -fx-border-width: 2; -fx-cursor: hand;"

  private val tabBaseStyle = "-fx-background-color: rgba(255,255,255,0.06); -fx-background-radius: 14; -fx-text-fill: #8899aa; -fx-font-size: 11; -fx-padding: 4 10 4 10; -fx-cursor: hand;"
  private val tabActiveStyle = "-fx-background-color: #4a9eff; -fx-background-radius: 14; -fx-text-fill: white; -fx-font-size: 11; -fx-font-weight: bold; -fx-padding: 4 10 4 10; -fx-cursor: hand;"

  private val CellWidth = 64
  private val GridGap = 6
  private var currentGridCols = 6

  def createPanel(): VBox = {
    val charSectionLabel = new Label(Messages.t("SELECT CHARACTER"))
    charSectionLabel.setStyle(sectionHeaderStyle)

    // Category filter tabs
    val tabsRow = new FlowPane(6, 6)
    tabsRow.setAlignment(Pos.CENTER_LEFT)
    for ((catName, _) <- categories) {
      val tab = new Label(Messages.t(catName))
      tab.setStyle(if (catName == "All") tabActiveStyle else tabBaseStyle)
      tab.setOnMouseClicked(_ => selectCategory(catName))
      tab.setOnMouseEntered(_ => {
        if (catName != selectedCategory) tab.setStyle(tabBaseStyle.replace("rgba(255,255,255,0.06)", "rgba(255,255,255,0.12)"))
      })
      tab.setOnMouseExited(_ => {
        if (catName != selectedCategory) tab.setStyle(tabBaseStyle)
      })
      categoryButtons += (catName -> tab)
      tabsRow.getChildren.add(tab)
    }

    // Search field
    val searchField = new TextField()
    searchField.setPromptText(Messages.t("Search characters..."))
    searchField.setStyle(
      "-fx-background-color: #1c1c34; -fx-text-fill: #ccdde8; -fx-prompt-text-fill: #556677; " +
      "-fx-background-radius: 8; -fx-border-color: rgba(255,255,255,0.1); -fx-border-radius: 8; " +
      "-fx-border-width: 1; -fx-padding: 6 10 6 10; -fx-font-size: 12;"
    )
    searchField.setMaxWidth(Double.MaxValue)
    searchField.textProperty().addListener((_, _, newVal) => {
      searchQuery = newVal.trim.toLowerCase
      rebuildGrid()
    })
    searchField.focusedProperty().addListener((_, _, focused) => {
      val borderColor = if (focused) "rgba(74, 158, 255, 0.4)" else "rgba(255,255,255,0.1)"
      searchField.setStyle(
        s"-fx-background-color: #1c1c34; -fx-text-fill: #ccdde8; -fx-prompt-text-fill: #556677; " +
        s"-fx-background-radius: 8; -fx-border-color: $borderColor; -fx-border-radius: 8; " +
        s"-fx-border-width: 1; -fx-padding: 6 10 6 10; -fx-font-size: 12;"
      )
    })

    // Character count label: how many the current filter shows (see rebuildGrid)
    countLabel = new Label(Messages.t("{0} characters", CharacterDef.all.size.toString))
    countLabel.setStyle("-fx-text-fill: #556677; -fx-font-size: 11;")

    val searchRow = new HBox(8, searchField, countLabel)
    searchRow.setAlignment(Pos.CENTER_LEFT)
    HBox.setHgrow(searchField, Priority.ALWAYS)

    // Build grid
    gridContainer = new GridPane()
    gridContainer.setHgap(6)
    gridContainer.setVgap(6)
    buildGrid()

    // Scrollable grid area
    val scrollPane = new ScrollPane(gridContainer)
    scrollPane.setFitToWidth(true)
    scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER)
    scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED)
    scrollPane.setStyle(
      "-fx-background-color: transparent; " +
      "-fx-border-color: transparent; -fx-padding: 0;"
    )
    scrollPane.setPrefHeight(500)
    scrollPane.setMaxHeight(Double.MaxValue)
    VBox.setVgrow(scrollPane, Priority.ALWAYS)
    ViewportCache.disable(scrollPane) // the cells animate
    gridScroll = scrollPane

    // Dynamically recompute grid columns when width changes
    scrollPane.viewportBoundsProperty().addListener((_, _, bounds) => {
      gridDirty = true
      if (bounds != null) {
        val newCols = Math.max(4, ((bounds.getWidth + GridGap) / (CellWidth + GridGap)).toInt)
        if (newCols != currentGridCols) {
          currentGridCols = newCols
          rebuildGrid()
        }
      }
    })
    // Only on-screen cells are drawn, so cells scrolled into view need drawing now
    scrollPane.vvalueProperty().addListener((_, _, _) => gridDirty = true)

    val gridSection = new VBox(8, tabsRow, searchRow, scrollPane)

    // Detail panel (right side)
    detailPreviewCanvas = new Canvas(96, 96)

    detailNameLabel = new Label("")
    detailNameLabel.setFont(Font.font("Exo 2", FontWeight.BOLD, 17))
    detailNameLabel.setTextFill(Color.web("#4a9eff"))

    detailRoleLabel = new Label("")
    detailRoleLabel.setFont(Font.font("Exo 2", FontWeight.BOLD, 12))
    detailRoleLabel.setTextFill(Color.web("#8899aa"))

    detailDescLabel = new Label("")
    detailDescLabel.setFont(Font.font("Exo 2", 13))
    detailDescLabel.setTextFill(Color.web("#aabbcc"))
    detailDescLabel.setWrapText(true)
    detailDescLabel.setMaxWidth(320)
    detailDescLabel.setStyle("-fx-line-spacing: 2;")

    // Ability preview rows
    primaryRow = createAbilityRow()
    qRow = createAbilityRow()
    eRow = createAbilityRow()

    val primarySection = buildAbilitySection(primaryRow)
    val qSection = buildAbilitySection(qRow)
    val eSection = buildAbilitySection(eRow)

    val abilitiesCard = new VBox(8, primarySection, createThinSeparator(), qSection, createThinSeparator(), eSection)
    abilitiesCard.setPadding(new Insets(10, 14, 10, 14))
    abilitiesCard.setStyle(cardBgSubtle)

    val descCard = new VBox(8, detailDescLabel)
    descCard.setPadding(new Insets(10, 14, 10, 14))
    descCard.setStyle(cardBgSubtle)

    val previewBox = new VBox(6, detailPreviewCanvas, detailNameLabel, detailRoleLabel)
    previewBox.setAlignment(Pos.CENTER)

    val detailsPanel = new VBox(12, previewBox, descCard, abilitiesCard)
    detailsPanel.setAlignment(Pos.TOP_CENTER)
    detailsPanel.setMinWidth(320)
    detailsPanel.setPrefWidth(340)

    // Initialize detail panel
    updateDetailPanel()

    // Animation timer. Any change to the screen costs a repaint of the whole window, so
    // everything here changes on the same pulses: the ability previews run at 30 fps (every
    // other pulse), and the sprites — which only step every 10 pulses — ride along on one
    // of those. It used to redraw all of it on every pulse, which held the menus at ~50% of
    // a CPU core (5K display) just sitting on this screen.
    UiActivity.touch()
    timer = new AnimationTimer {
      override def handle(now: Long): Unit = {
        // Nobody's watching (window in the background, or no input for a while): hold the
        // current frame rather than repaint the window 30 times a second for it
        if (UiActivity.idle) return
        animTick += 1
        if (animTick % 40 == 0) {
          dirIndex = (dirIndex + 1) % dirs.length
        }
        val frame = (animTick / 10) % 4
        if (frame != shownFrame || dirIndex != shownDir || getSelectedId() != shownSelection) {
          if (frame != shownFrame || dirIndex != shownDir) gridDirty = true
          drawDetailPreview()
        }
        if (gridDirty) drawVisibleGridCells()
        if ((animTick & 1) == 0) {
          if (!pendingCells.isEmpty) drawLoadedPendingCells()
          renderAbilityCanvases()
        }
      }
    }
    timer.start()

    // Main row: grid on left, details on right
    val mainRow = new HBox(20, gridSection, detailsPanel)
    mainRow.setAlignment(Pos.TOP_CENTER)
    HBox.setHgrow(gridSection, Priority.ALWAYS)

    val charSection = new VBox(12, charSectionLabel, mainRow)
    charSection.setAlignment(Pos.CENTER)
    charSection.setPadding(new Insets(0, 24, 0, 24))
    charSection
  }

  def stop(): Unit = {
    if (timer != null) timer.stop()
  }

  private def selectCategory(catName: String): Unit = {
    selectedCategory = catName
    categoryButtons.foreach { case (name, label) =>
      if (name == catName) {
        label.setStyle(tabActiveStyle)
      } else {
        label.setStyle(tabBaseStyle)
      }
    }
    rebuildGrid()
  }

  private def rebuildGrid(): Unit = {
    cellCanvases = Map.empty
    cellPanes = Map.empty
    gridContainer.getChildren.clear()
    buildGrid()
    countLabel.setText(Messages.t("{0} characters", filteredChars.size.toString))
    // Drawn on the next pulse, once layout has placed the new cells
    gridDirty = true
  }

  private def buildGrid(): Unit = {
    val selectedId = getSelectedId()

    // Filter by category
    val catFiltered = if (selectedCategory == "All") {
      CharacterDef.all
    } else {
      val idSet = categories.find(_._1 == selectedCategory).get._2
      CharacterDef.all.filter(c => idSet.contains(c.id.id.toInt))
    }

    // Filter by search
    filteredChars = if (searchQuery.isEmpty) {
      catFiltered
    } else {
      catFiltered.filter(c =>
        I18n.characterName(c).toLowerCase.contains(searchQuery) ||
        c.displayName.toLowerCase.contains(searchQuery))
    }

    val glow = new DropShadow()
    glow.setColor(Color.web("#4a9eff"))
    glow.setRadius(10)
    glow.setSpread(0.15)

    for (i <- filteredChars.indices) {
      val charDef = filteredChars(i)
      val charId = charDef.id.id
      val cellCanvas = new Canvas(44, 44)
      cellCanvases += (charId -> cellCanvas)

      val nameLabel = new Label(I18n.characterName(charDef))
      nameLabel.setFont(Font.font("Exo 2", FontWeight.BOLD, 9))
      nameLabel.setTextFill(Color.web("#aabbcc"))
      nameLabel.setMaxWidth(60)
      nameLabel.setAlignment(Pos.CENTER)

      val cellContent = new VBox(1, cellCanvas, nameLabel)
      cellContent.setAlignment(Pos.CENTER)
      cellContent.setPadding(new Insets(4, 2, 3, 2))

      val cellPane = new StackPane(cellContent)
      cellPane.setMinWidth(64)
      cellPane.setPrefWidth(64)
      cellPane.setMinHeight(66)
      cellPanes += (charId -> cellPane)

      val isSelected = charId == selectedId
      cellPane.setStyle(if (isSelected) cellSelectedStyle else cellBaseStyle)
      if (isSelected) cellPane.setEffect(glow)

      cellPane.setOnMouseClicked(_ => {
        onSelect(charId)
        updateCellStyles()
        updateDetailPanel()
      })
      cellPane.setOnMouseEntered(_ => {
        if (charId != getSelectedId()) cellPane.setStyle(cellHoverStyle)
        cellPane.setScaleX(1.05); cellPane.setScaleY(1.05)
      })
      cellPane.setOnMouseExited(_ => {
        if (charId != getSelectedId()) cellPane.setStyle(cellBaseStyle)
        cellPane.setScaleX(1.0); cellPane.setScaleY(1.0)
      })

      val col = i % currentGridCols
      val row = i / currentGridCols
      gridContainer.add(cellPane, col, row)
    }
  }

  private def updateCellStyles(): Unit = {
    val selectedId = getSelectedId()
    val glow = new DropShadow()
    glow.setColor(Color.web("#4a9eff"))
    glow.setRadius(10)
    glow.setSpread(0.15)

    cellPanes.foreach { case (charId, pane) =>
      if (charId == selectedId) {
        pane.setStyle(cellSelectedStyle)
        pane.setEffect(glow)
      } else {
        pane.setStyle(cellBaseStyle)
        pane.setEffect(null)
      }
    }
  }

  /** Whether a grid cell is inside the grid's viewport and the window. */
  private def onScreen(canvas: Canvas): Boolean = {
    val scene = canvas.getScene
    if (scene == null || gridScroll == null) return false
    val cb = canvas.localToScene(canvas.getBoundsInLocal)
    val vb = gridScroll.localToScene(gridScroll.getBoundsInLocal)
    cb.getMaxY >= Math.max(0.0, vb.getMinY) && cb.getMinY <= Math.min(scene.getHeight, vb.getMaxY) &&
      cb.getMaxX >= Math.max(0.0, vb.getMinX) && cb.getMinX <= Math.min(scene.getWidth, vb.getMaxX)
  }

  // Grid cells drawn before their sheet finished loading (sheets load in the background)
  private val pendingCells = new java.util.HashSet[java.lang.Byte]()

  private def drawCell(charId: Byte, canvas: Canvas): Unit = {
    val gc = canvas.getGraphicsContext2D
    val s = canvas.getWidth
    gc.clearRect(0, 0, s, s)
    gc.setFill(cellBgColor)
    gc.fillOval(2, 2, s - 4, s - 4)
    val spriteSize = SpriteGenerator.ThumbDisplayPx
    val offset = (s - spriteSize) / 2.0
    if (!SpriteGenerator.drawFrame(gc, charId, dirs(dirIndex), (animTick / 10) % 4, offset, offset, spriteSize))
      pendingCells.add(charId)
  }

  /** Draw the grid cells that are on screen; the rest are drawn when scrolled into view.
    * Before, all 112 were redrawn five times a second wherever the grid was scrolled. */
  private def drawVisibleGridCells(): Unit = {
    gridDirty = false
    pendingCells.clear()
    cellCanvases.foreach { case (charId, canvas) =>
      if (onScreen(canvas)) drawCell(charId, canvas)
    }
  }

  /** Draw the cells whose sheets have finished loading since they were last drawn. */
  private def drawLoadedPendingCells(): Unit = {
    var ready: List[Byte] = Nil
    val it = pendingCells.iterator()
    while (it.hasNext) {
      val charId = it.next().byteValue()
      if (SpriteGenerator.isReady(charId, SpriteGenerator.ThumbDisplayPx)) { it.remove(); ready ::= charId }
    }
    ready.foreach(charId => cellCanvases.get(charId).foreach(c => drawCell(charId, c)))
  }

  private def drawDetailPreview(): Unit = {
    val gc = detailPreviewCanvas.getGraphicsContext2D
    val s = detailPreviewCanvas.getWidth
    gc.clearRect(0, 0, s, s)
    val charDef = CharacterDef.get(getSelectedId())
    val dir = dirs(dirIndex)
    val frame = (animTick / 10) % 4
    shownFrame = frame; shownDir = dirIndex; shownSelection = charDef.id.id
    gc.setStroke(previewRingColor)
    gc.setLineWidth(2)
    gc.strokeOval(4, 4, s - 8, s - 8)
    gc.setFill(cellBgColor)
    gc.fillOval(6, 6, s - 12, s - 12)
    val spriteDisplaySize = 68.0
    val offset = (s - spriteDisplaySize) / 2.0
    SpriteGenerator.drawFrame(gc, charDef.id.id, dir, frame, offset, offset, spriteDisplaySize)
  }

  private def updateDetailPanel(): Unit = {
    val charDef = CharacterDef.get(getSelectedId())
    detailNameLabel.setText(I18n.characterName(charDef))
    detailRoleLabel.setText(CharacterSelectionPanel.roleLine(charDef))
    detailDescLabel.setText(I18n.characterDesc(charDef))
    animTick = 0
    dirIndex = 0
    drawDetailPreview()

    // Update ability rows
    updateAbilityRow(primaryRow, "LMB", Messages.t("Primary Attack"), charDef.primaryProjectileType, 0, charDef)
    updateAbilityRow(qRow, "Q", I18n.qName(charDef), charDef.qAbility.projectileType, charDef.qAbility.cooldownMs, charDef)
    updateAbilityRow(eRow, "E", I18n.eName(charDef), charDef.eAbility.projectileType, charDef.eAbility.cooldownMs, charDef)

    renderAbilityCanvases()
  }

  private def createAbilityRow(): AbilityRow = {
    val nameLabel = new Label("")
    nameLabel.setFont(Font.font("Exo 2", FontWeight.BOLD, 12))
    nameLabel.setTextFill(Color.web("#ccdde8"))

    val cooldownLabel = new Label("")
    cooldownLabel.setFont(Font.font("Exo 2", 11))
    cooldownLabel.setTextFill(Color.web("#8899aa"))

    val canvas = new Canvas(260, 44)

    val statsLabel = new Label("")
    statsLabel.setFont(Font.font("Exo 2", 11))
    statsLabel.setTextFill(Color.web("#8899bb"))

    AbilityRow(nameLabel, cooldownLabel, canvas, statsLabel)
  }

  private def buildAbilitySection(row: AbilityRow): VBox = {
    val headerBox = new HBox(8, row.nameLabel)
    headerBox.setAlignment(Pos.CENTER_LEFT)
    val spacer = new Region()
    HBox.setHgrow(spacer, Priority.ALWAYS)
    headerBox.getChildren.addAll(spacer, row.cooldownLabel)

    val canvasWrapper = new HBox(row.canvas)
    canvasWrapper.setAlignment(Pos.CENTER)

    val statsBox = new HBox(row.statsLabel)
    statsBox.setAlignment(Pos.CENTER_LEFT)

    val section = new VBox(3, headerBox, canvasWrapper, statsBox)
    section
  }

  private def createThinSeparator(): Region = {
    val sep = new Region()
    sep.setMinHeight(1)
    sep.setMaxHeight(1)
    sep.setStyle("-fx-background-color: rgba(255,255,255,0.05);")
    sep
  }

  private def updateAbilityRow(row: AbilityRow, keybind: String, name: String,
                                projType: Byte, cooldownMs: Int, charDef: CharacterDef): Unit = {
    row.nameLabel.setText(s"[$keybind]  $name")

    if (cooldownMs > 0) {
      row.cooldownLabel.setText(s"${cooldownMs / 1000}s")
    } else {
      row.cooldownLabel.setText("")
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

    row.statsLabel.setText(stats.toString)
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

    // Primary
    val primaryProjDef = ProjectileDef.get(charDef.primaryProjectileType)
    val primaryCb = primaryProjDef.chargeSpeedScaling match {
      case Some(_) => StandardProjectile
      case None => StandardProjectile
    }
    AbilityPreviewRenderer.render(
      primaryRow.canvas.getGraphicsContext2D,
      charDef.primaryProjectileType, primaryCb,
      animTick, primaryRow.canvas.getWidth, primaryRow.canvas.getHeight)

    // Q ability
    AbilityPreviewRenderer.render(
      qRow.canvas.getGraphicsContext2D,
      charDef.qAbility.projectileType, charDef.qAbility.castBehavior,
      animTick, qRow.canvas.getWidth, qRow.canvas.getHeight)

    // E ability
    AbilityPreviewRenderer.render(
      eRow.canvas.getGraphicsContext2D,
      charDef.eAbility.projectileType, charDef.eAbility.castBehavior,
      animTick, eRow.canvas.getWidth, eRow.canvas.getHeight)
  }
}

object CharacterSelectionPanel {
  /** What the character is for and the two numbers their role sets: "Melee  ·  145 HP  ·  Slow". */
  def roleLine(d: CharacterDef): String =
    Seq(Messages.t(d.role.name), Messages.t("{0} HP", d.maxHealth.toString), paceName(d.moveSpeed))
      .mkString("  \u00b7  ")

  /** A walking pace in words, against the roster's: ranged characters (the base rate) are the
    * quick ones, skirmishers a little slower, melee slowest. The roles are only a few percent
    * apart, so a word per pace says more than a number nobody can compare. */
  def paceName(moveSpeed: Float): String =
    if (moveSpeed >= 0.99f) Messages.t("Fast")
    else if (moveSpeed >= 0.955f) Messages.t("Medium speed")
    else Messages.t("Slow")
}
