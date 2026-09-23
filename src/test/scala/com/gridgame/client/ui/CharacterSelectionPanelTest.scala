package com.gridgame.client.ui

import com.gridgame.client.Fx
import com.gridgame.client.i18n.I18n
import com.gridgame.common.model.CharacterDef
import com.gridgame.common.model.CharacterId
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import org.junit.Assert._
import org.junit.Test

/** The character grid every screen that picks a character uses (lobby, practice, ranked). */
class CharacterSelectionPanelTest {
  private var selected: Byte = CharacterId.DEFAULT.id

  private def panel(): (CharacterSelectionPanel, VBox) = {
    val p = new CharacterSelectionPanel(() => selected, id => selected = id)
    val root = p.createPanel()
    new Scene(root, 1200, 900)
    (p, root)
  }

  /** The grid's cells: a clickable pane holding a portrait and the character's name. */
  private def cells(root: Node): Map[String, StackPane] =
    Fx.all(root).collect {
      case l: Label if l.getParent != null && l.getParent.getParent.isInstanceOf[StackPane] &&
        l.getParent.getParent.getOnMouseClicked != null => l.getText -> l.getParent.getParent.asInstanceOf[StackPane]
    }.toMap

  private def tab(root: Node, name: String): Label =
    Fx.all(root).collectFirst { case l: Label if l.getText == name && l.getOnMouseClicked != null => l }.get

  @Test def everyCharacterIsInTheGrid(): Unit = Fx {
    val (p, root) = panel()
    try assertEquals(CharacterDef.all.map(I18n.characterName).toSet, cells(root).keySet)
    finally p.stop()
  }

  @Test def clickingACharacterPicksIt(): Unit = Fx {
    val (p, root) = panel()
    try {
      Fx.click(cells(root)("Wizard"))
      assertEquals(CharacterId.Wizard.id, selected)
      assertTrue("the detail panel shows it", Fx.labels(root).contains("Wizard"))
    } finally p.stop()
  }

  @Test def theDetailsSayTheRoleTheHealthAndThePace(): Unit = Fx {
    val (p, root) = panel()
    try {
      Fx.click(cells(root)("Barbarian"))
      assertTrue(Fx.labels(root).contains("Melee  ·  145 HP  ·  Slow"))
      Fx.click(cells(root)("Golem"))
      assertTrue(Fx.labels(root).contains("Skirmisher  ·  150 HP  ·  Medium speed"))
      Fx.click(cells(root)("Wizard"))
      assertTrue(Fx.labels(root).contains("Ranged  ·  60 HP  ·  Fast"))
    } finally p.stop()
  }

  @Test def everyCharacterIsInExactlyOneCategory(): Unit = Fx {
    // Adding a character without a category would leave it findable only under "All"
    val (p, root) = panel()
    try {
      val byCategory = Seq("Melee", "Ranged", "Assassin", "Tank", "Blaster", "Controller").map { cat =>
        Fx.click(tab(root, cat))
        cat -> cells(root).keySet
      }
      val names = byCategory.flatMap(_._2)
      assertEquals("none in two", names.size, names.toSet.size)
      assertEquals("all in one", CharacterDef.all.map(I18n.characterName).toSet, names.toSet)
      Fx.click(tab(root, "All"))
      assertEquals(CharacterDef.all.size, cells(root).size)
    } finally p.stop()
  }

  @Test def theCountFollowsTheFilter(): Unit = Fx {
    val (p, root) = panel()
    try {
      Fx.click(tab(root, "Tank"))
      val shown = cells(root).size
      assertTrue(Fx.labels(root).contains(s"$shown characters"))
    } finally p.stop()
  }

  @Test def searchNarrowsTheGridByName(): Unit = Fx {
    val (p, root) = panel()
    try {
      val search = Fx.all(root).collectFirst { case f: TextField => f }.get
      search.setText("wiz")
      assertEquals(Set("Wizard"), cells(root).keySet)
      search.setText("")
      assertEquals(CharacterDef.all.size, cells(root).size)
    } finally p.stop()
  }
}
