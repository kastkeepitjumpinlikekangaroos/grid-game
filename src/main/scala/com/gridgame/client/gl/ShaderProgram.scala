package com.gridgame.client.gl

import org.lwjgl.opengl.GL20._
import org.lwjgl.opengl.GL11._
import java.nio.FloatBuffer
import scala.collection.mutable

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

  // Uniform location cache — avoids string-based glGetUniformLocation lookups every frame
  private val uniformCache = mutable.Map.empty[String, Int]

  @inline private def getLocation(name: String): Int =
    uniformCache.getOrElseUpdate(name, glGetUniformLocation(programId, name))

  def use(): Unit = glUseProgram(programId)

  def setUniformMat4(name: String, mat: FloatBuffer): Unit = {
    val loc = getLocation(name)
    if (loc >= 0) glUniformMatrix4fv(loc, false, mat)
  }

  def setUniform1i(name: String, value: Int): Unit = {
    val loc = getLocation(name)
    if (loc >= 0) glUniform1i(loc, value)
  }

  def setUniform1f(name: String, value: Float): Unit = {
    val loc = getLocation(name)
    if (loc >= 0) glUniform1f(loc, value)
  }

  def setUniform2f(name: String, x: Float, y: Float): Unit = {
    val loc = getLocation(name)
    if (loc >= 0) glUniform2f(loc, x, y)
  }

  def setUniform3f(name: String, x: Float, y: Float, z: Float): Unit = {
    val loc = getLocation(name)
    if (loc >= 0) glUniform3f(loc, x, y, z)
  }

  def setUniform4f(name: String, x: Float, y: Float, z: Float, w: Float): Unit = {
    val loc = getLocation(name)
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

  // ── Bloom composite + vignette + lighting + chromatic aberration + distortion ──
  val COMPOSITE_FRAG: String =
    """#version 330 core
      |in vec2 vTexCoord;
      |uniform sampler2D uScene;
      |uniform sampler2D uBloom;
      |uniform sampler2D uLightMap;
      |uniform float uBloomStrength;
      |uniform float uVignetteStrength;
      |uniform vec4 uOverlayColor;
      |uniform float uTime;
      |uniform int uUseLightMap;
      |uniform float uChromaticAberration;
      |uniform vec2 uDistortionCenter;
      |uniform float uDistortionStrength;
      |uniform float uDamageVignette;
      |out vec4 FragColor;
      |void main() {
      |  vec2 uv = vTexCoord;
      |  // Screen distortion (radial sine warp for explosion shockwave)
      |  if (uDistortionStrength > 0.0) {
      |    vec2 toCenter = uv - uDistortionCenter;
      |    float d = length(toCenter);
      |    float warp = sin(d * 30.0 - uTime * 0.5) * uDistortionStrength * exp(-d * 5.0);
      |    uv += normalize(toCenter + vec2(0.001)) * warp;
      |  }
      |  // Chromatic aberration (radial RGB offset on damage)
      |  vec4 scene;
      |  if (uChromaticAberration > 0.0) {
      |    vec2 dir = uv - vec2(0.5);
      |    float rOff = uChromaticAberration;
      |    float bOff = -uChromaticAberration;
      |    float sr = texture(uScene, uv + dir * rOff).r;
      |    float sg = texture(uScene, uv).g;
      |    float sb = texture(uScene, uv + dir * bOff).b;
      |    scene = vec4(sr, sg, sb, 1.0);
      |  } else {
      |    scene = texture(uScene, uv);
      |  }
      |  vec4 bloom = texture(uBloom, uv);
      |  // Screen blend for bloom — brightens without blowing out whites
      |  vec3 color = scene.rgb + bloom.rgb * uBloomStrength * (1.0 - scene.rgb);
      |  // Dynamic lighting: multiply scene by light map
      |  if (uUseLightMap == 1) {
      |    vec3 light = texture(uLightMap, vTexCoord).rgb;
      |    color *= min(light * 1.6, vec3(1.2));
      |  }
      |  // Warm-cool color grading: shadows toward blue-purple, highlights toward warm amber
      |  float luma = dot(color, vec3(0.299, 0.587, 0.114));
      |  vec3 shadows = vec3(0.05, 0.03, 0.1);
      |  vec3 highlights = vec3(1.05, 1.0, 0.92);
      |  color = mix(color + shadows * (1.0 - luma), color * highlights, luma);
      |  // Contrast S-curve: subtle contrast boost
      |  color = color * color * (3.0 - 2.0 * color);
      |  // Film grain: per-pixel noise
      |  float grain = fract(sin(dot(vTexCoord * uTime, vec2(12.9898, 78.233))) * 43758.5453);
      |  color += (grain - 0.5) * 0.015;
      |  // Soft vignette using smoothstep for gradual falloff
      |  vec2 vigUV = vTexCoord * 2.0 - 1.0;
      |  float dist = dot(vigUV, vigUV);
      |  float vignette = 1.0 - smoothstep(0.4, 1.8, dist) * uVignetteStrength;
      |  color *= vignette;
      |  // Overlay (generic color overlay)
      |  color = mix(color, uOverlayColor.rgb, uOverlayColor.a);
      |  // Damage vignette: red glow at screen edges only
      |  if (uDamageVignette > 0.0) {
      |    float edgeDist = length(vigUV * vec2(1.0, 0.8));
      |    float edgeMask = smoothstep(0.5, 1.2, edgeDist);
      |    vec3 dmgColor = vec3(0.8, 0.05, 0.02);
      |    color = mix(color, dmgColor, edgeMask * uDamageVignette * 0.45);
      |  }
      |  FragColor = vec4(color, 1.0);
      |}""".stripMargin

  // ── Tile transition shader: blends neighbor tile via alpha mask ──
  val TRANSITION_VERT: String =
    """#version 330 core
      |layout(location = 0) in vec2 aPos;
      |layout(location = 1) in vec2 aTileUV;
      |layout(location = 2) in vec2 aMaskUV;
      |uniform mat4 uProjection;
      |out vec2 vTileUV;
      |out vec2 vMaskUV;
      |void main() {
      |  gl_Position = uProjection * vec4(aPos, 0.0, 1.0);
      |  vTileUV = aTileUV;
      |  vMaskUV = aMaskUV;
      |}""".stripMargin

  val TRANSITION_FRAG: String =
    """#version 330 core
      |in vec2 vTileUV;
      |in vec2 vMaskUV;
      |uniform sampler2D uTileAtlas;
      |uniform sampler2D uMaskAtlas;
      |out vec4 FragColor;
      |void main() {
      |  vec4 tileColor = texture(uTileAtlas, vTileUV);
      |  float maskAlpha = texture(uMaskAtlas, vMaskUV).a;
      |  FragColor = vec4(tileColor.rgb, tileColor.a * maskAlpha);
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
