package com.gridgame.client.gl

import org.lwjgl.opengl.GL20._
import org.lwjgl.opengl.GL11._
import java.nio.FloatBuffer

/** Compiles and links a vertex + fragment shader pair. Provides uniform setters. */
class ShaderProgram(vertexSrc: String, fragmentSrc: String) {
  val programId: Int = glCreateProgram()
  private val vertexId = compileShader(vertexSrc, GL_VERTEX_SHADER)
  private val fragmentId = compileShader(fragmentSrc, GL_FRAGMENT_SHADER)

  glAttachShader(programId, vertexId)
  glAttachShader(programId, fragmentId)
  glLinkProgram(programId)

  if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
    val log = glGetProgramInfoLog(programId)
    throw new RuntimeException(s"Shader link error: $log")
  }

  // Shaders can be detached after linking
  glDetachShader(programId, vertexId)
  glDetachShader(programId, fragmentId)
  glDeleteShader(vertexId)
  glDeleteShader(fragmentId)

  def use(): Unit = glUseProgram(programId)

  def setUniformMat4(name: String, mat: FloatBuffer): Unit = {
    val loc = glGetUniformLocation(programId, name)
    if (loc >= 0) glUniformMatrix4fv(loc, false, mat)
  }

  def setUniform1i(name: String, value: Int): Unit = {
    val loc = glGetUniformLocation(programId, name)
    if (loc >= 0) glUniform1i(loc, value)
  }

  def setUniform1f(name: String, value: Float): Unit = {
    val loc = glGetUniformLocation(programId, name)
    if (loc >= 0) glUniform1f(loc, value)
  }

  def setUniform2f(name: String, x: Float, y: Float): Unit = {
    val loc = glGetUniformLocation(programId, name)
    if (loc >= 0) glUniform2f(loc, x, y)
  }

  def setUniform3f(name: String, x: Float, y: Float, z: Float): Unit = {
    val loc = glGetUniformLocation(programId, name)
    if (loc >= 0) glUniform3f(loc, x, y, z)
  }

  def setUniform4f(name: String, x: Float, y: Float, z: Float, w: Float): Unit = {
    val loc = glGetUniformLocation(programId, name)
    if (loc >= 0) glUniform4f(loc, x, y, z, w)
  }

  def dispose(): Unit = glDeleteProgram(programId)

  private def compileShader(src: String, shaderType: Int): Int = {
    val id = glCreateShader(shaderType)
    glShaderSource(id, src)
    glCompileShader(id)
    if (glGetShaderi(id, GL_COMPILE_STATUS) == GL_FALSE) {
      val log = glGetShaderInfoLog(id)
      val typeName = if (shaderType == GL_VERTEX_SHADER) "vertex" else "fragment"
      throw new RuntimeException(s"$typeName shader compile error: $log")
    }
    id
  }
}

object ShaderProgram {
  // ── Color shader: position (vec2) + color (vec4) ──
  val COLOR_VERT: String =
    """#version 330 core
      |layout(location = 0) in vec2 aPos;
      |layout(location = 1) in vec4 aColor;
      |uniform mat4 uProjection;
      |out vec4 vColor;
      |void main() {
      |  gl_Position = uProjection * vec4(aPos, 0.0, 1.0);
      |  vColor = aColor;
      |}""".stripMargin

  val COLOR_FRAG: String =
    """#version 330 core
      |in vec4 vColor;
      |out vec4 FragColor;
      |void main() {
      |  FragColor = vColor;
      |}""".stripMargin

  // ── Texture shader: position (vec2) + texcoord (vec2) + tint (vec4) ──
  val TEXTURE_VERT: String =
    """#version 330 core
      |layout(location = 0) in vec2 aPos;
      |layout(location = 1) in vec2 aTexCoord;
      |layout(location = 2) in vec4 aColor;
      |uniform mat4 uProjection;
      |out vec2 vTexCoord;
      |out vec4 vColor;
      |void main() {
      |  gl_Position = uProjection * vec4(aPos, 0.0, 1.0);
      |  vTexCoord = aTexCoord;
      |  vColor = aColor;
      |}""".stripMargin

  val TEXTURE_FRAG: String =
    """#version 330 core
      |in vec2 vTexCoord;
      |in vec4 vColor;
      |uniform sampler2D uTexture;
      |out vec4 FragColor;
      |void main() {
      |  FragColor = texture(uTexture, vTexCoord) * vColor;
      |}""".stripMargin

  // ── Bloom extract: threshold bright pixels ──
  val BLOOM_EXTRACT_FRAG: String =
    """#version 330 core
      |in vec2 vTexCoord;
      |uniform sampler2D uTexture;
      |uniform float uThreshold;
      |out vec4 FragColor;
      |void main() {
      |  vec4 color = texture(uTexture, vTexCoord);
      |  float brightness = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
      |  if (brightness > uThreshold) {
      |    FragColor = color;
      |  } else {
      |    FragColor = vec4(0.0);
      |  }
      |}""".stripMargin

  // ── Gaussian blur (single-pass, direction via uniform) ──
  val BLUR_FRAG: String =
    """#version 330 core
      |in vec2 vTexCoord;
      |uniform sampler2D uTexture;
      |uniform vec2 uDirection; // (1/w, 0) or (0, 1/h)
      |out vec4 FragColor;
      |void main() {
      |  vec4 result = vec4(0.0);
      |  float weights[5] = float[](0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216);
      |  result += texture(uTexture, vTexCoord) * weights[0];
      |  for (int i = 1; i < 5; i++) {
      |    vec2 off = uDirection * float(i);
      |    result += texture(uTexture, vTexCoord + off) * weights[i];
      |    result += texture(uTexture, vTexCoord - off) * weights[i];
      |  }
      |  FragColor = result;
      |}""".stripMargin

  // ── Bloom composite + vignette ──
  val COMPOSITE_FRAG: String =
    """#version 330 core
      |in vec2 vTexCoord;
      |uniform sampler2D uScene;
      |uniform sampler2D uBloom;
      |uniform float uBloomStrength;
      |uniform float uVignetteStrength;
      |uniform vec4 uOverlayColor;
      |out vec4 FragColor;
      |void main() {
      |  vec4 scene = texture(uScene, vTexCoord);
      |  vec4 bloom = texture(uBloom, vTexCoord);
      |  // Screen blend for bloom — brightens without blowing out whites
      |  vec3 color = scene.rgb + bloom.rgb * uBloomStrength * (1.0 - scene.rgb);
      |  // Soft vignette using smoothstep for gradual falloff
      |  vec2 uv = vTexCoord * 2.0 - 1.0;
      |  float dist = dot(uv, uv);
      |  float vignette = 1.0 - smoothstep(0.4, 1.8, dist) * uVignetteStrength;
      |  color *= vignette;
      |  // Overlay (damage flash)
      |  color = mix(color, uOverlayColor.rgb, uOverlayColor.a);
      |  FragColor = vec4(color, 1.0);
      |}""".stripMargin

  // Fullscreen quad vertex shader (shared by post-processing passes)
  val FULLSCREEN_VERT: String =
    """#version 330 core
      |layout(location = 0) in vec2 aPos;
      |layout(location = 1) in vec2 aTexCoord;
      |out vec2 vTexCoord;
      |void main() {
      |  gl_Position = vec4(aPos, 0.0, 1.0);
      |  vTexCoord = aTexCoord;
      |}""".stripMargin
}
