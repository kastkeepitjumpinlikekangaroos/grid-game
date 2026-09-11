package com.gridgame.client.ui

import javafx.scene.control.ScrollPane

/**
 * ScrollPane's skin caches its viewport as a bitmap (`viewRect.setCache(true)` in
 * ScrollPaneSkin), which pays off for static content. Around content that animates it is
 * worse than no cache at all: every canvas redraw invalidates it, so the whole viewport is
 * rendered into the cache and then drawn again, every frame, and the cache is a texture as
 * big as the viewport — 44MB for a full-screen page on a 5K display. The character select
 * screens animate at 30 fps inside two nested scroll panes.
 */
object ViewportCache {
  def disable(sp: ScrollPane): Unit = {
    def apply(): Unit = {
      val viewport = sp.lookup(".viewport")
      if (viewport != null) viewport.setCache(false)
    }
    // The viewport only exists once the skin does
    if (sp.getSkin != null) apply()
    sp.skinProperty().addListener((_: javafx.beans.Observable) => apply())
  }
}
