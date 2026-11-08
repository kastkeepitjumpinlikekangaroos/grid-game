package com.gridgame.client.gl

import org.lwjgl.opengl.GL11._
import org.lwjgl.opengl.GL15._
import org.lwjgl.opengl.GL20._
import org.lwjgl.opengl.GL30._
import org.lwjgl.BufferUtils

/**
 * Post-processing pipeline: renders game to FBO, then applies bloom + vignette + overlay.
 *
 * Pipeline: Scene FBO → Bloom extract → Blur H → Blur V → Composite (scene + bloom + vignette + overlay)
 */
object PostProcessor {
  /**
   * Dev check: `GRIDGAME_HOLECHECK=1` clears the scene to magenta instead of black, so any pixel
   * the terrain leaves uncovered shows. The ground is drawn as exact diamonds with no blending and
   * the background is skipped whenever the world fills the screen, both of which rely on the
   * terrain covering every pixel inside the world.
   */
  private[gl] val HoleCheck: Boolean = System.getenv("GRIDGAME_HOLECHECK") != null

  // The composite's parts, as the bits of its program's key: the quality tier's, the moment's (an
  // explosion's warp, a hit, the match's fade in), and the grade's tone curve in the top two bits
  private final val BloomBit = 1
  private final val WideBit = 2
  private final val SharpenBit = 4
  private final val GrainBit = 8
  private final val LightBit = 16
  private final val DistortBit = 32
  private final val HurtBit = 64
  private final val OverlayBit = 128
  private final val MomentBits = DistortBit | HurtBit | OverlayBit
  private final val ToneShift = 8
  private val CompositeVariants = 3 << ToneShift
}

class PostProcessor(var width: Int, var height: Int) {
  import PostProcessor._
  // FBOs (bloom at half resolution for efficiency). They stay allocated even when a
  // quality tier turns bloom off — together they are under a fifth of a screen, and the
  // composite samples them unconditionally (weighted to zero) rather than needing a
  // second shader.
  private var sceneFBO: GLTexture = GLTexture.createFBO(width, height)
  private var bloomExtractFBO: GLTexture = GLTexture.createFBO(width / 2, height / 2)
  private var blurPingFBO: GLTexture = GLTexture.createFBO(width / 2, height / 2)
  private var blurPongFBO: GLTexture = GLTexture.createFBO(width / 2, height / 2)
  // Quarter-res bloom for wider, softer glow
  private var bloomQPingFBO: GLTexture = GLTexture.createFBO(width / 4, height / 4)
  private var bloomQPongFBO: GLTexture = GLTexture.createFBO(width / 4, height / 4)

  // Shaders
  private val bloomExtractShader = new ShaderProgram(ShaderProgram.FULLSCREEN_VERT, ShaderProgram.BLOOM_EXTRACT_FRAG)
  private val blurShader = new ShaderProgram(ShaderProgram.FULLSCREEN_VERT, ShaderProgram.BLUR_FRAG)
  // The composite, a program per combination of its parts, made the first time it is wanted
  // (`composite`): indexed by its key (endScene)
  private val composites = new Array[ShaderProgram](PostProcessor.CompositeVariants)

  // Fullscreen quad VAO
  private val quadVao = glGenVertexArrays()
  private val quadVbo = glGenBuffers()
  setupQuad()

  // Set sampler uniforms once at creation (they never change)
  bloomExtractShader.use()
  bloomExtractShader.setUniform1i("uTexture", 0)
  blurShader.use()
  blurShader.setUniform1i("uTexture", 0)

  // Post-process parameters (tuned for subtle enhancement, not high contrast)
  var bloomThreshold: Float = 0.80f
  var bloomStrength: Float = 0.22f
  var vignetteStrength: Float = 0.25f
  /** How much of the ACES curve the composite applies: 1 (the default) for the dark maps, low
    * for a bright one, whose colours it would wash out. Set per background (GLGameRenderer). */
  var toneMap: Float = 1f
  var animationTime: Float = 0f
  var overlayR: Float = 0f
  var overlayG: Float = 0f
  var overlayB: Float = 0f
  var overlayA: Float = 0f

  // Dynamic lighting
  var lightMapTexture: GLTexture = _
  var useLightMap: Boolean = false

  // Chromatic aberration (set on damage)
  var chromaticAberration: Float = 0f

  // Damage vignette (red edge flash, 0=off, 1=max)
  var damageVignette: Float = 0f

  // Screen distortion (set on nearby explosions)
  var distortionCenterX: Float = 0.5f
  var distortionCenterY: Float = 0.5f
  var distortionStrength: Float = 0f

  /** The composite program for `key`, made now if it hasn't been, with every sibling that differs
    * from it only in the moment's parts: so the first hit or explosion of a match doesn't wait for
    * a shader to compile. */
  private def composite(key: Int): ShaderProgram = {
    if (composites(key) == null) {
      val base = key & ~MomentBits
      var m = 0
      while (m <= MomentBits) {
        if ((m & ~MomentBits) == 0 && composites(base | m) == null) makeComposite(base | m)
        m += DistortBit
      }
    }
    composites(key)
  }

  private def makeComposite(key: Int): ShaderProgram = {
    val p = new ShaderProgram(ShaderProgram.FULLSCREEN_VERT, ShaderProgram.compositeFrag(
      bloom = (key & BloomBit) != 0, wideBloom = (key & WideBit) != 0, sharpen = (key & SharpenBit) != 0,
      grain = (key & GrainBit) != 0, lightMap = (key & LightBit) != 0, tone = key >>> ToneShift,
      distort = (key & DistortBit) != 0, hurt = (key & HurtBit) != 0, overlay = (key & OverlayBit) != 0))
    p.use()
    p.setUniform1i("uScene", 0)
    p.setUniform1i("uBloom", 1)
    p.setUniform1i("uLightMap", 2)
    p.setUniform1i("uBloomQ", 3)
    composites(key) = p
    p
  }

  private def setupQuad(): Unit = {
    // Fullscreen triangle strip: positions + texcoords
    val data = Array[Float](
      -1f, -1f, 0f, 0f,
       1f, -1f, 1f, 0f,
      -1f,  1f, 0f, 1f,
       1f,  1f, 1f, 1f
    )
    val buf = BufferUtils.createFloatBuffer(data.length)
    buf.put(data).flip()

    glBindVertexArray(quadVao)
    glBindBuffer(GL_ARRAY_BUFFER, quadVbo)
    glBufferData(GL_ARRAY_BUFFER, buf, GL_STATIC_DRAW)
    glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * 4, 0)
    glEnableVertexAttribArray(0)
    glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * 4, 2 * 4L)
    glEnableVertexAttribArray(1)
    glBindVertexArray(0)
  }

  /** Begin rendering the scene. All draw calls after this go to the scene FBO. */
  def beginScene(): Unit = {
    sceneFBO.bindAsTarget()
    glViewport(0, 0, width, height)
    if (PostProcessor.HoleCheck) glClearColor(1f, 0f, 1f, 1f) else glClearColor(0f, 0f, 0f, 1f)
    glClear(GL_COLOR_BUFFER_BIT)
  }

  /**
   * Bloom chain: extract bright pixels, then blur them at half resolution and (for the
   * wide glow) again at quarter resolution. Five off-screen passes, so the quality tiers
   * drop the quarter-res pair first and the whole chain at the lowest tier.
   */
  private def renderBloom(wide: Boolean): Unit = {
    // All bloom passes use half-resolution — set viewport once
    val halfW = width / 2; val halfH = height / 2
    glViewport(0, 0, halfW, halfH)

    // 1. Extract bright pixels
    bloomExtractFBO.bindAsTarget()
    glClear(GL_COLOR_BUFFER_BIT)
    bloomExtractShader.use()
    bloomExtractShader.setUniform1f("uThreshold", bloomThreshold)
    sceneFBO.bind(0)
    drawQuad()
    bloomExtractFBO.unbindTarget()

    // 2. Horizontal blur
    blurPingFBO.bindAsTarget()
    glClear(GL_COLOR_BUFFER_BIT)
    blurShader.use()
    blurShader.setUniform2f("uDirection", 1f / halfW, 0f)
    bloomExtractFBO.bind(0)
    drawQuad()
    blurPingFBO.unbindTarget()

    // 3. Vertical blur
    blurPongFBO.bindAsTarget()
    glClear(GL_COLOR_BUFFER_BIT)
    // blurShader already in use from horizontal pass
    blurShader.setUniform2f("uDirection", 0f, 1f / halfH)
    blurPingFBO.bind(0)
    drawQuad()
    blurPongFBO.unbindTarget()

    if (!wide) return

    // 3b. Quarter-res bloom — horizontal blur
    val quartW = width / 4; val quartH = height / 4
    glViewport(0, 0, quartW, quartH)
    bloomQPingFBO.bindAsTarget()
    glClear(GL_COLOR_BUFFER_BIT)
    blurShader.setUniform2f("uDirection", 1f / quartW, 0f)
    bloomExtractFBO.bind(0) // re-use bloom extract as input
    drawQuad()
    bloomQPingFBO.unbindTarget()

    // 3c. Quarter-res bloom — vertical blur
    bloomQPongFBO.bindAsTarget()
    glClear(GL_COLOR_BUFFER_BIT)
    blurShader.setUniform2f("uDirection", 0f, 1f / quartH)
    bloomQPingFBO.bind(0)
    drawQuad()
    bloomQPongFBO.unbindTarget()
  }

  /** End scene rendering and apply post-processing to the default framebuffer. */
  def endScene(screenWidth: Int, screenHeight: Int): Unit = {
    sceneFBO.unbindTarget()

    val doBloom = RenderQuality.bloom
    val doWideBloom = doBloom && RenderQuality.wideBloom
    if (doBloom) renderBloom(doWideBloom)

    // 4. Composite: scene + bloom + vignette + lighting + effects → default framebuffer
    glBindFramebuffer(GL_FRAMEBUFFER, 0)
    glViewport(0, 0, screenWidth, screenHeight)
    glClear(GL_COLOR_BUFFER_BIT)
    // Only the parts that show are in the program: each was a branch on a uniform, which every
    // pixel of the display paid for whether it was taken or not
    val light = useLightMap && lightMapTexture != null && RenderQuality.lighting
    val sharpen = RenderQuality.sharpen
    val grain = RenderQuality.grain
    val distort = distortionStrength > 0f
    val hurt = chromaticAberration > 0f || damageVignette > 0f
    val overlay = overlayA > 0f
    val tone = if (toneMap >= 1f) 1 else if (toneMap <= 0f) 0 else 2
    var key = tone << ToneShift
    if (doBloom) key |= BloomBit
    if (doWideBloom) key |= WideBit
    if (sharpen) key |= SharpenBit
    if (grain) key |= GrainBit
    if (light) key |= LightBit
    if (distort) key |= DistortBit
    if (hurt) key |= HurtBit
    if (overlay) key |= OverlayBit
    val compositeShader = composite(key)
    compositeShader.use()
    // (a uniform the program doesn't have is skipped by setUniform)
    compositeShader.setUniform1f("uBloomStrength", bloomStrength)
    compositeShader.setUniform1f("uWideBloom", 0.4f)
    compositeShader.setUniform1f("uVignetteStrength", vignetteStrength)
    compositeShader.setUniform1f("uToneMap", toneMap)
    compositeShader.setUniform1f("uTime", animationTime)
    compositeShader.setUniform2f("uResolution", width.toFloat, height.toFloat)
    if (light) lightMapTexture.bind(2)
    if (overlay) compositeShader.setUniform4f("uOverlayColor", overlayR, overlayG, overlayB, overlayA)
    if (hurt) {
      compositeShader.setUniform1f("uChromaticAberration", chromaticAberration)
      compositeShader.setUniform1f("uDamageVignette", damageVignette)
    }
    if (distort) {
      compositeShader.setUniform2f("uDistortionCenter", distortionCenterX, distortionCenterY)
      compositeShader.setUniform1f("uDistortionStrength", distortionStrength)
    }
    sceneFBO.bind(0)
    blurPongFBO.bind(1)
    bloomQPongFBO.bind(3)
    // Every pixel is written whole (alpha 1), so blending would only read the display's pixels
    // back to multiply them by zero: a read of the full-resolution framebuffer every frame, the
    // largest single target the frame touches (at Low on a HiDPI screen this pass is two fifths
    // of all the fragments). Off, the same pixels come out. The batches drawn after it turn it on.
    glDisable(GL_BLEND)
    drawQuad()
    glEnable(GL_BLEND)
  }

  /** Resize all FBOs when window size changes. */
  def resize(newWidth: Int, newHeight: Int): Unit = {
    if (newWidth == width && newHeight == height) return
    width = newWidth
    height = newHeight
    sceneFBO = GLTexture.resizeFBO(sceneFBO, width, height)
    bloomExtractFBO = GLTexture.resizeFBO(bloomExtractFBO, width / 2, height / 2)
    blurPingFBO = GLTexture.resizeFBO(blurPingFBO, width / 2, height / 2)
    blurPongFBO = GLTexture.resizeFBO(blurPongFBO, width / 2, height / 2)
    bloomQPingFBO = GLTexture.resizeFBO(bloomQPingFBO, width / 4, height / 4)
    bloomQPongFBO = GLTexture.resizeFBO(bloomQPongFBO, width / 4, height / 4)
  }

  private def drawQuad(): Unit = {
    glBindVertexArray(quadVao)
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
    glBindVertexArray(0)
  }

  def dispose(): Unit = {
    sceneFBO.dispose()
    bloomExtractFBO.dispose()
    blurPingFBO.dispose()
    blurPongFBO.dispose()
    bloomQPingFBO.dispose()
    bloomQPongFBO.dispose()
    bloomExtractShader.dispose()
    blurShader.dispose()
    var k = 0
    while (k < composites.length) { if (composites(k) != null) composites(k).dispose(); composites(k) = null; k += 1 }
    glDeleteVertexArrays(quadVao)
    glDeleteBuffers(quadVbo)
  }
}
