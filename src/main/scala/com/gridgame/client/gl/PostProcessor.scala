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
class PostProcessor(var width: Int, var height: Int) {
  // FBOs (bloom at half resolution for efficiency)
  private var sceneFBO: GLTexture = GLTexture.createFBO(width, height)
  private var bloomExtractFBO: GLTexture = GLTexture.createFBO(width / 2, height / 2)
  private var blurPingFBO: GLTexture = GLTexture.createFBO(width / 2, height / 2)
  private var blurPongFBO: GLTexture = GLTexture.createFBO(width / 2, height / 2)

  // Shaders
  private val bloomExtractShader = new ShaderProgram(ShaderProgram.FULLSCREEN_VERT, ShaderProgram.BLOOM_EXTRACT_FRAG)
  private val blurShader = new ShaderProgram(ShaderProgram.FULLSCREEN_VERT, ShaderProgram.BLUR_FRAG)
  private val compositeShader = new ShaderProgram(ShaderProgram.FULLSCREEN_VERT, ShaderProgram.COMPOSITE_FRAG)

  // Fullscreen quad VAO
  private val quadVao = glGenVertexArrays()
  private val quadVbo = glGenBuffers()
  setupQuad()

  // Post-process parameters (tuned for subtle enhancement, not high contrast)
  var bloomThreshold: Float = 0.82f
  var bloomStrength: Float = 0.18f
  var vignetteStrength: Float = 0.20f
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

  // Screen distortion (set on nearby explosions)
  var distortionCenterX: Float = 0.5f
  var distortionCenterY: Float = 0.5f
  var distortionStrength: Float = 0f

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
    glClearColor(0f, 0f, 0f, 1f)
    glClear(GL_COLOR_BUFFER_BIT)
  }

  /** End scene rendering and apply post-processing to the default framebuffer. */
  def endScene(screenWidth: Int, screenHeight: Int): Unit = {
    sceneFBO.unbindTarget()

    // 1. Extract bright pixels
    bloomExtractFBO.bindAsTarget()
    glViewport(0, 0, width / 2, height / 2)
    glClear(GL_COLOR_BUFFER_BIT)
    bloomExtractShader.use()
    bloomExtractShader.setUniform1i("uTexture", 0)
    bloomExtractShader.setUniform1f("uThreshold", bloomThreshold)
    sceneFBO.bind(0)
    drawQuad()
    bloomExtractFBO.unbindTarget()

    // 2. Horizontal blur
    blurPingFBO.bindAsTarget()
    glViewport(0, 0, width / 2, height / 2)
    glClear(GL_COLOR_BUFFER_BIT)
    blurShader.use()
    blurShader.setUniform1i("uTexture", 0)
    blurShader.setUniform2f("uDirection", 1f / (width / 2), 0f)
    bloomExtractFBO.bind(0)
    drawQuad()
    blurPingFBO.unbindTarget()

    // 3. Vertical blur
    blurPongFBO.bindAsTarget()
    glViewport(0, 0, width / 2, height / 2)
    glClear(GL_COLOR_BUFFER_BIT)
    blurShader.use()
    blurShader.setUniform2f("uDirection", 0f, 1f / (height / 2))
    blurPingFBO.bind(0)
    drawQuad()
    blurPongFBO.unbindTarget()

    // 4. Composite: scene + bloom + vignette + lighting + effects → default framebuffer
    glBindFramebuffer(GL_FRAMEBUFFER, 0)
    glViewport(0, 0, screenWidth, screenHeight)
    glClear(GL_COLOR_BUFFER_BIT)
    compositeShader.use()
    compositeShader.setUniform1i("uScene", 0)
    compositeShader.setUniform1i("uBloom", 1)
    compositeShader.setUniform1i("uLightMap", 2)
    compositeShader.setUniform1f("uBloomStrength", bloomStrength)
    compositeShader.setUniform1f("uVignetteStrength", vignetteStrength)
    compositeShader.setUniform4f("uOverlayColor", overlayR, overlayG, overlayB, overlayA)
    compositeShader.setUniform1f("uTime", animationTime)
    // Dynamic lighting
    if (useLightMap && lightMapTexture != null) {
      compositeShader.setUniform1i("uUseLightMap", 1)
      lightMapTexture.bind(2)
    } else {
      compositeShader.setUniform1i("uUseLightMap", 0)
    }
    // Chromatic aberration
    compositeShader.setUniform1f("uChromaticAberration", chromaticAberration)
    // Screen distortion
    compositeShader.setUniform2f("uDistortionCenter", distortionCenterX, distortionCenterY)
    compositeShader.setUniform1f("uDistortionStrength", distortionStrength)
    sceneFBO.bind(0)
    blurPongFBO.bind(1)
    drawQuad()
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
    bloomExtractShader.dispose()
    blurShader.dispose()
    compositeShader.dispose()
    glDeleteVertexArrays(quadVao)
    glDeleteBuffers(quadVbo)
  }
}
