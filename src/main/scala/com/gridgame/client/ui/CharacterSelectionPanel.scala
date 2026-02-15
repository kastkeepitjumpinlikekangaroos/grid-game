package com.gridgame.client.ui

import com.gridgame.common.model._
import javafx.animation.AnimationTimer
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.canvas.Canvas
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.FontWeight

/**
 * Shared character selection panel used in both lobby and ranked queue screens.
 * Shows sprite preview, carousel navigation, ability previews with animations, and description.
 */
class CharacterSelectionPanel(
    getSelectedId: () => Byte,
    onSelect: Byte => Unit
) {

  private var animTick = 0
  private var dirIndex = 0
  private val dirs = Array(Direction.Down, Direction.Left, Direction.Up, Direction.Right)

  // UI elements that need updating
  private var charNameLabel: Label = _
  private var charDescLabel: Label = _
  private var charCountLabel: Label = _
  private var previewCanvas: Canvas = _

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

  private val sectionHeaderStyle = "-fx-text-fill: #99aabb; -fx-font-size: 13; -fx-font-weight: bold;"
  private val cardBgSubtle = "-fx-background-color: #1c1c34; -fx-background-radius: 12; -fx-border-color: rgba(255,255,255,0.05); -fx-border-radius: 12; -fx-border-width: 1; -fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.3), 12, 0, 0, 4);"
  private val arrowBtnStyle = "-fx-background-color: rgba(255,255,255,0.06); -fx-text-fill: #8899aa; -fx-font-size: 18; -fx-font-weight: bold; -fx-padding: 8 16; -fx-background-radius: 10; -fx-cursor: hand; -fx-border-color: rgba(255,255,255,0.08); -fx-border-width: 1; -fx-border-radius: 10;"
  private val arrowBtnHoverStyle = "-fx-background-color: rgba(74, 158, 255, 0.15); -fx-text-fill: #4a9eff; -fx-font-size: 18; -fx-font-weight: bold; -fx-padding: 8 16; -fx-background-radius: 10; -fx-cursor: hand; -fx-border-color: rgba(74, 158, 255, 0.3); -fx-border-width: 1; -fx-border-radius: 10;"

  def createPanel(): VBox = {
    val charSectionLabel = new Label("SELECT CHARACTER")
    charSectionLabel.setStyle(sectionHeaderStyle)

    // Sprite preview
    val previewSize = 96.0
    previewCanvas = new Canvas(previewSize, previewSize)

    charNameLabel = new Label("")
    charNameLabel.setFont(Font.font("System", FontWeight.BOLD, 17))
    charNameLabel.setTextFill(Color.web("#4a9eff"))

    charDescLabel = new Label("")
    charDescLabel.setFont(Font.font("System", 13))
    charDescLabel.setTextFill(Color.web("#aabbcc"))
    charDescLabel.setWrapText(true)
    charDescLabel.setMaxWidth(400)
    charDescLabel.setStyle("-fx-line-spacing: 2;")

    // Carousel
    charCountLabel = new Label("")
    charCountLabel.setFont(Font.font("System", 13))
    charCountLabel.setTextFill(Color.web("#8899aa"))

    val prevBtn = new Button("\u25C0")
    prevBtn.setStyle(arrowBtnStyle)
    prevBtn.setOnMouseEntered(_ => prevBtn.setStyle(arrowBtnHoverStyle))
    prevBtn.setOnMouseExited(_ => prevBtn.setStyle(arrowBtnStyle))
    prevBtn.setOnAction(_ => selectByIndex(currentCharIndex - 1))

    val nextBtn = new Button("\u25B6")
    nextBtn.setStyle(arrowBtnStyle)
    nextBtn.setOnMouseEntered(_ => nextBtn.setStyle(arrowBtnHoverStyle))
    nextBtn.setOnMouseExited(_ => nextBtn.setStyle(arrowBtnStyle))
    nextBtn.setOnAction(_ => selectByIndex(currentCharIndex + 1))

    val carouselBox = new HBox(16, prevBtn, charCountLabel, nextBtn)
    carouselBox.setAlignment(Pos.CENTER)

    // Ability preview rows
    primaryRow = createAbilityRow()
    qRow = createAbilityRow()
    eRow = createAbilityRow()

    val primarySection = buildAbilitySection(primaryRow)
    val qSection = buildAbilitySection(qRow)
    val eSection = buildAbilitySection(eRow)

    val abilitiesCard = new VBox(8, primarySection, createThinSeparator(), qSection, createThinSeparator(), eSection)
    abilitiesCard.setPadding(new Insets(10, 14, 10, 14))
    abilitiesCard.setMaxWidth(420)
    abilitiesCard.setStyle(cardBgSubtle)

    // Description card
    val descCard = new VBox(8, charDescLabel)
    descCard.setPadding(new Insets(10, 14, 10, 14))
    descCard.setMaxWidth(420)
    descCard.setStyle(cardBgSubtle)

    val previewBox = new VBox(6, previewCanvas, charNameLabel)
    previewBox.setAlignment(Pos.CENTER)

    // Initialize
    updateCharacterInfo()
    charCountLabel.setText(s"${currentCharIndex + 1} / ${CharacterDef.all.size}")

    // Animation timer
    timer = new AnimationTimer {
      override def handle(now: Long): Unit = {
        animTick += 1
        if (animTick % 40 == 0) {
          dirIndex = (dirIndex + 1) % dirs.length
        }
        drawPreview()
        renderAbilityCanvases()
      }
    }
    timer.start()

    val charSection = new VBox(12, charSectionLabel, previewBox, carouselBox, abilitiesCard, descCard)
    charSection.setAlignment(Pos.CENTER)
    charSection.setPadding(new Insets(0, 24, 0, 24))
    charSection
  }

  def stop(): Unit = {
    if (timer != null) timer.stop()
  }

  private def currentCharIndex: Int = {
    val idx = CharacterDef.all.indexWhere(_.id.id == getSelectedId())
    if (idx < 0) 0 else idx
  }

  private def selectByIndex(idx: Int): Unit = {
    val wrapped = ((idx % CharacterDef.all.size) + CharacterDef.all.size) % CharacterDef.all.size
    val charDef = CharacterDef.all(wrapped)
    onSelect(charDef.id.id)
    updateCharacterInfo()
    charCountLabel.setText(s"${wrapped + 1} / ${CharacterDef.all.size}")
  }

  private def drawPreview(): Unit = {
    val gc = previewCanvas.getGraphicsContext2D
    val s = previewCanvas.getWidth
    gc.clearRect(0, 0, s, s)
    val charDef = CharacterDef.get(getSelectedId())
    val dir = dirs(dirIndex)
    val frame = (animTick / 10) % 4
    val sprite = SpriteGenerator.getSprite(0, dir, frame, charDef.id.id)
    gc.setStroke(Color.web("#3a3a5e"))
    gc.setLineWidth(2)
    gc.strokeOval(4, 4, s - 8, s - 8)
    gc.setFill(Color.web("#1a1a30"))
    gc.fillOval(6, 6, s - 12, s - 12)
    val spriteDisplaySize = 68.0
    val offset = (s - spriteDisplaySize) / 2.0
    gc.drawImage(sprite, offset, offset, spriteDisplaySize, spriteDisplaySize)
  }

  private def updateCharacterInfo(): Unit = {
    val charDef = CharacterDef.get(getSelectedId())
    charNameLabel.setText(charDef.displayName)
    charDescLabel.setText(charDef.description)
    animTick = 0
    dirIndex = 0
    drawPreview()

    // Update ability rows
    updateAbilityRow(primaryRow, "LMB", "Primary Attack", charDef.primaryProjectileType, 0, charDef)
    updateAbilityRow(qRow, "Q", charDef.qAbility.name, charDef.qAbility.projectileType, charDef.qAbility.cooldownMs, charDef)
    updateAbilityRow(eRow, "E", charDef.eAbility.name, charDef.eAbility.projectileType, charDef.eAbility.cooldownMs, charDef)

    renderAbilityCanvases()
  }

  private def createAbilityRow(): AbilityRow = {
    val nameLabel = new Label("")
    nameLabel.setFont(Font.font("System", FontWeight.BOLD, 12))
    nameLabel.setTextFill(Color.web("#ccdde8"))

    val cooldownLabel = new Label("")
    cooldownLabel.setFont(Font.font("System", 11))
    cooldownLabel.setTextFill(Color.web("#8899aa"))

    val canvas = new Canvas(200, 44)

    val statsLabel = new Label("")
    statsLabel.setFont(Font.font("System", 11))
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
          case _ =>
            appendProjectileStats(stats, ab.projectileType, ab.damage, ab.maxRange, ab.castBehavior)
        }
      case None =>
        // Primary attack
        appendProjectileStats(stats, projType, 0, 0, StandardProjectile)
    }

    row.statsLabel.setText(stats.toString)
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
    pDef.onHitEffect.foreach {
      case PullToOwner => stats.append("  Pull")
      case Freeze(dur) => stats.append(s"  Freeze ${dur / 1000}s")
      case Push(dist) => stats.append(s"  Push")
      case TeleportOwnerBehind(_, _) => stats.append("  Teleport")
      case LifeSteal(pct) => stats.append(s"  LifeSteal ${pct}%")
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
