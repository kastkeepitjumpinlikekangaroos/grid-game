package com.gridgame.client.ui

import javafx.scene.Group
import javafx.scene.paint.Color
import javafx.scene.shape.{Rectangle, SVGPath, StrokeLineCap, StrokeLineJoin}

/**
 * The menus' icons: line drawings on a 24-unit square, stroked 2 units wide with round ends, as a
 * web page's icons are. `Icons.node(Icons.Search, 18, colour)` gives one at 18 pixels.
 *
 * Several follow the geometry of Lucide's icons (https://lucide.dev), which are under the ISC
 * License: Copyright (c) for portions of Lucide are held by Cole Bemis 2013-2022 as part of
 * Feather (MIT); all other copyright (c) for Lucide are held by Lucide Contributors 2022.
 * Permission to use, copy, modify, and/or distribute this software for any purpose with or without
 * fee is hereby granted, provided that the above copyright notice and this permission notice
 * appear in all copies.
 */
private[client] object Icons {
  final case class Icon(path: String, filled: Boolean = false)

  val SoundOn = Icon("M4 9H8L13 5V19L8 15H4Z M16.5 8.5A5 5 0 0 1 16.5 15.5 M19.5 5.5A9 9 0 0 1 19.5 18.5")
  val SoundOff = Icon("M4 9H8L13 5V19L8 15H4Z M17 9.5L22 14.5 M22 9.5L17 14.5")
  val Refresh = Icon("M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8 M21 3v5h-5")
  val Search = Icon("M3 11a8 8 0 1 0 16 0a8 8 0 1 0-16 0 M21 21l-4.3-4.3")
  val Users = Icon("M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M5 7a4 4 0 1 0 8 0a4 4 0 1 0-8 0 " +
    "M22 21v-2a4 4 0 0 0-3-3.87 M16 3.13a4 4 0 0 1 0 7.75")
  val Clock = Icon("M2 12a10 10 0 1 0 20 0a10 10 0 1 0-20 0 M12 6v6l4 2")
  val Trophy = Icon("M8 21h8 M12 17v4 M7 4h10v5a5 5 0 0 1-10 0z M7 6H4v1a3 3 0 0 0 3 3 M17 6h3v1a3 3 0 0 1-3 3")
  val Crown = Icon("M3 7l4.5 5L12 5l4.5 7L21 7l-2 11H5z")
  val Bot = Icon("M12 8V4H8 M6 8h12a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2z " +
    "M2 14h2 M20 14h2 M15 13v2 M9 13v2")
  val Send = Icon("M22 2L11 13 M22 2l-7 20-4-9-9-4z")
  val Star = Icon("M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01z", filled = true)
  val Plus = Icon("M12 5v14 M5 12h14")
  val Minus = Icon("M5 12h14")
  val Globe = Icon("M2 12a10 10 0 1 0 20 0a10 10 0 1 0-20 0 M2 12h20 " +
    "M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z")
  val Server = Icon("M4 2h16a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2z " +
    "M4 14h16a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2v-4a2 2 0 0 1 2-2z M6 6h.01 M6 18h.01")
  val Sword = Icon("M14.5 17.5L3 6V3h3l11.5 11.5 M13 19l6-6 M16 16l4 4 M19 21l2-2")
  val Target = Icon("M2 12a10 10 0 1 0 20 0a10 10 0 1 0-20 0 M6 12a6 6 0 1 0 12 0a6 6 0 1 0-12 0 " +
    "M10 12a2 2 0 1 0 4 0a2 2 0 1 0-4 0")
  val Check = Icon("M20 6L9 17l-5-5")
  val Play = Icon("M7 4l13 8-13 8z", filled = true)
  val MapPin = Icon("M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0z M9 10a3 3 0 1 0 6 0a3 3 0 1 0-6 0")
  val Chat = Icon("M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z")

  /** An icon `size` pixels square in `colour`: stroked, or filled if it is a solid shape. */
  def node(icon: Icon, size: Double, colour: Color): Group = {
    val p = new SVGPath()
    p.setContent(icon.path)
    if (icon.filled) {
      p.setFill(colour)
      p.setStroke(colour)
      p.setStrokeWidth(1)
    } else {
      p.setFill(null)
      p.setStroke(colour)
      p.setStrokeWidth(2)
    }
    p.setStrokeLineCap(StrokeLineCap.ROUND)
    p.setStrokeLineJoin(StrokeLineJoin.ROUND)
    // Scaled inside a group with an empty 24-unit square, so every icon lays out as the same
    // `size` square however much of it the drawing fills (a minus sign is one line)
    val k = size / 24.0
    val box = new Rectangle(24, 24, Color.TRANSPARENT)
    Seq(box, p).foreach(_.getTransforms.add(new javafx.scene.transform.Scale(k, k, 0, 0)))
    val g = new Group(box, p)
    g.setMouseTransparent(true)
    g
  }
}
