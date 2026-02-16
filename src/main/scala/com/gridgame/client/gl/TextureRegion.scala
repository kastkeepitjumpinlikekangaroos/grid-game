package com.gridgame.client.gl

/** Sub-region of an OpenGL texture atlas, defined by UV coordinates. */
case class TextureRegion(texture: GLTexture, u: Float, v: Float, u2: Float, v2: Float) {
  def width: Float = u2 - u
  def height: Float = v2 - v
}
