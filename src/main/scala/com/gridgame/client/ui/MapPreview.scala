package com.gridgame.client.ui

import com.gridgame.common.WorldRegistry
import com.gridgame.common.world.WorldLoader

import javafx.scene.Node
import javafx.scene.image.{Image, ImageView, WritableImage}
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle

/**
 * The maps seen from above, a pixel per tile in each tile's colour, for the menus (the lobby
 * list, the lobby form, the lobby room). Each map is read once: drawing one parses its file,
 * which the lobby list used to do for every row every time the list refreshed.
 */
private[client] object MapPreview {
  /** A map's picture, and the colour its outer band reads as (its forest, sea or void). */
  private final case class Picture(image: Image, edge: Color)

  private val pictures = new java.util.HashMap[Integer, Picture]()

  private def picture(mapIndex: Int): Picture = {
    val key = Integer.valueOf(mapIndex)
    if (!pictures.containsKey(key)) {
      val p = try {
        val world = WorldLoader.load("worlds/" + WorldRegistry.getFilename(mapIndex))
        val out = new WritableImage(world.width, world.height)
        val px = out.getPixelWriter
        for (y <- 0 until world.height; x <- 0 until world.width) px.setArgb(x, y, 0xFF000000 | world.getTile(x, y).color)
        // What a frame of another shape leaves over is filled with the colour the map's outer band
        // reads as from a distance, leaving out the rim round its very edge (the space maps'
        // obsidian): the band's own colour where one colour is most of it (a void, open sea),
        // and the average of it where it is many (a forest)
        val rim = 2
        val band = Math.max(rim + 2, Math.round(world.width * 0.06).toInt)
        val reader = out.getPixelReader
        val counts = scala.collection.mutable.HashMap.empty[Int, Int]
        var r, g, b, n = 0L
        for (y <- rim until world.height - rim; x <- rim until world.width - rim
             if x < band || y < band || x >= world.width - band || y >= world.height - band) {
          val argb = reader.getArgb(x, y)
          counts(argb) = counts.getOrElse(argb, 0) + 1
          r += (argb >> 16) & 0xFF; g += (argb >> 8) & 0xFF; b += argb & 0xFF; n += 1
        }
        val (commonest, count) = counts.maxBy(_._2)
        val edge =
          if (count * 2 >= n) Color.rgb((commonest >> 16) & 0xFF, (commonest >> 8) & 0xFF, commonest & 0xFF)
          else Color.rgb((r / n).toInt, (g / n).toInt, (b / n).toInt)
        Picture(out, edge)
      } catch { case _: Exception => null }
      pictures.put(key, p)
    }
    pictures.get(key)
  }

  /**
   * The whole map in a `w` x `h` frame with rounded corners. A frame of another shape than the
   * map's is filled out with the colour of the map's own edge, so it reads as the map going on.
   * Drawn up large its tiles stay square pixels; drawn down small it is smoothed.
   */
  def view(mapIndex: Int, w: Double, h: Double, radius: Double = 12): Node = {
    val frame = new StackPane()
    frame.setMinSize(w, h); frame.setPrefSize(w, h); frame.setMaxSize(w, h)
    val p = picture(mapIndex)
    val fill = if (p != null) Theme.hex(p.edge) else "#cfe1f4"
    frame.setStyle(s"-fx-background-color: $fill; -fx-background-radius: $radius;")
    if (p != null) {
      val view = new ImageView(p.image)
      // All of it, as big as the frame allows
      val scale = Math.min(w / p.image.getWidth, h / p.image.getHeight)
      view.setFitWidth(p.image.getWidth * scale)
      view.setFitHeight(p.image.getHeight * scale)
      view.setSmooth(scale < 1)
      frame.getChildren.add(view)
      val clip = new Rectangle(w, h)
      clip.setArcWidth(radius * 2); clip.setArcHeight(radius * 2)
      frame.setClip(clip)
    }
    frame
  }
}
