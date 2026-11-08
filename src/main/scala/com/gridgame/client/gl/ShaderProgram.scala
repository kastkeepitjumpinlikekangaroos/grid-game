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

  // Uniform location cache — Java HashMap avoids Scala Option wrapping on getOrElseUpdate
  private val uniformCache = new java.util.HashMap[String, java.lang.Integer]()

  @inline private def getLocation(name: String): Int = {
    val cached = uniformCache.get(name)
    if (cached != null) cached.intValue()
    else {
      val loc = glGetUniformLocation(programId, name)
      uniformCache.put(name, loc)
      loc
    }
  }

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
  // The batches' shader is DrawStream's (shapes and sprites in one stream)

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
  // The same nine-tap kernel as before in five fetches: each pair of neighbouring taps is one
  // bilinear fetch placed between them in proportion to their weights (the targets are
  // GL_LINEAR), which the hardware blends for free. Four fewer fetches a pixel, in two passes.
  val BLUR_FRAG: String =
    """#version 330 core
      |in vec2 vTexCoord;
      |uniform sampler2D uTexture;
      |uniform vec2 uDirection; // (1/w, 0) or (0, 1/h)
      |out vec4 FragColor;
      |void main() {
      |  vec2 o1 = uDirection * 1.3846154;
      |  vec2 o2 = uDirection * 3.2307692;
      |  FragColor = texture(uTexture, vTexCoord) * 0.2270270
      |    + (texture(uTexture, vTexCoord + o1) + texture(uTexture, vTexCoord - o1)) * 0.3162162
      |    + (texture(uTexture, vTexCoord + o2) + texture(uTexture, vTexCoord - o2)) * 0.0702703;
      |}""".stripMargin

  // ── Bloom composite + vignette + lighting + chromatic aberration + distortion ──
  // One source, compiled into a program per combination of what is switched on (PostProcessor's
  // composite variants): each `#if` below was a branch on a uniform, and the dead ones still cost
  // every pixel of the display — about 1.8 ms a frame at Low on a 4480x2404 screen, a fifth of the
  // GPU's frame (gl/CLAUDE.md, *The composite is a program per combination*). Every flag is defined
  // to 0 or 1 by `compositeFrag`; the arithmetic of each part is what it was, so the frame is too.
  //   BLOOM, WIDE_BLOOM, SHARPEN, GRAIN, LIGHT_MAP  the quality tier's parts
  //   TONE  0 = softClip (the bright maps, space), 1 = ACES (the dark maps), 2 = a mix of the two
  //   DISTORT, HURT, OVERLAY  the moment's: an explosion's warp, a hit's colour split and red edge,
  //                           the match's fade in
  private val COMPOSITE_BODY: String =
    """in vec2 vTexCoord;
      |uniform sampler2D uScene;
      |uniform sampler2D uBloom;
      |uniform sampler2D uBloomQ;
      |uniform sampler2D uLightMap;
      |uniform float uBloomStrength;
      |uniform float uVignetteStrength;
      |uniform vec4 uOverlayColor;
      |uniform float uTime;
      |uniform float uChromaticAberration;
      |uniform vec2 uDistortionCenter;
      |uniform float uDistortionStrength;
      |uniform float uDamageVignette;
      |uniform vec2 uResolution;
      |uniform float uWideBloom;  // weight of the quarter-res bloom
      |uniform float uToneMap;    // TONE 2's mix: 1 = ACES, 0 = softClip
      |// ACES filmic tone mapping
      |vec3 acesToneMap(vec3 x) {
      |  return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
      |}
      |// Colours as authored up to 0.8, then rolled off toward 1 instead of clipping. ACES lifts
      |// mid-tones and pulls highlights down, which a dark map wants and a bright one doesn't:
      |// it turns a meadow's grass to pastel.
      |vec3 softClip(vec3 x) {
      |  vec3 over = max(x - 0.8, 0.0);
      |  return min(x, vec3(0.8)) + 0.2 * (1.0 - exp(-over * 5.0));
      |}
      |out vec4 FragColor;
      |void main() {
      |  vec2 uv = vTexCoord;
      |#if DISTORT
      |  // Screen distortion (radial sine warp for explosion shockwave)
      |  if (uDistortionStrength > 0.0) {
      |    vec2 toCenter = uv - uDistortionCenter;
      |    float d = length(toCenter);
      |    float warp = sin(d * 30.0 - uTime * 0.5) * uDistortionStrength * exp(-d * 5.0);
      |    uv += normalize(toCenter + vec2(0.001)) * warp;
      |  }
      |#endif
      |#if HURT
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
      |#else
      |  vec4 scene = texture(uScene, uv);
      |#endif
      |#if SHARPEN
      |  // Unsharp mask sharpening — sample 4 neighbors, enhance edges.
      |  // Four extra full-resolution fetches per pixel, so the quality tiers drop it first.
      |  vec2 texel = 1.0 / uResolution;
      |  vec3 blurSample = (
      |    texture(uScene, uv + vec2(texel.x, 0.0)).rgb +
      |    texture(uScene, uv - vec2(texel.x, 0.0)).rgb +
      |    texture(uScene, uv + vec2(0.0, texel.y)).rgb +
      |    texture(uScene, uv - vec2(0.0, texel.y)).rgb
      |  ) * 0.25;
      |  scene.rgb += (scene.rgb - blurSample) * 0.15;
      |#endif
      |  vec3 color = scene.rgb;
      |#if BLOOM
      |#if WIDE_BLOOM
      |  vec4 bloom = texture(uBloom, uv) * 0.6 + texture(uBloomQ, uv) * uWideBloom;
      |#else
      |  vec4 bloom = texture(uBloom, uv) * 0.6;
      |#endif
      |  // Screen blend for bloom — brightens without blowing out whites
      |  color += bloom.rgb * uBloomStrength * (1.0 - scene.rgb);
      |#endif
      |#if LIGHT_MAP
      |  // Dynamic lighting: multiply scene by light map
      |  vec3 light = texture(uLightMap, vTexCoord).rgb;
      |  color *= min(light * 1.6, vec3(1.2));
      |#endif
      |  // Warm-cool color grading: teal shadows, warm highlights
      |  float luma = dot(color, vec3(0.299, 0.587, 0.114));
      |  vec3 shadows = vec3(0.01, 0.02, 0.05);
      |  vec3 highlights = vec3(1.03, 1.01, 0.96);
      |  color = mix(color + shadows * (1.0 - luma), color * highlights, luma);
      |  // Subtle contrast boost — S-curve in luminance
      |  float contrastLuma = dot(color, vec3(0.299, 0.587, 0.114));
      |  float boosted = smoothstep(0.0, 1.0, contrastLuma);
      |  color *= (boosted / max(contrastLuma, 0.001)) * 0.15 + 0.85;
      |  // ACES filmic tone mapping — cinematic highlight rolloff (a bright map rolls off with softClip)
      |#if TONE == 1
      |  color = acesToneMap(color);
      |#elif TONE == 0
      |  color = softClip(color);
      |#else
      |  color = mix(softClip(color), acesToneMap(color), uToneMap);
      |#endif
      |  // Slight saturation boost for vibrancy
      |  float postLuma = dot(color, vec3(0.299, 0.587, 0.114));
      |  color = mix(vec3(postLuma), color, 0.96);
      |#if GRAIN
      |  // Film grain: per-pixel noise. Hashed from the pixel and a frame index that wraps, without
      |  // sin(): uTime counts frames, and sin() of a number that large loses its precision on a GPU
      |  // within minutes, so the grain turned into bands and blocks the longer a match ran.
      |  vec3 p3 = fract(vec3(gl_FragCoord.xyx + mod(uTime, 97.0) * vec3(13.1, 7.7, 13.1)) * 0.1031);
      |  p3 += dot(p3, p3.yzx + 33.33);
      |  float grain = fract((p3.x + p3.y) * p3.z);
      |  color += (grain - 0.5) * 0.015;
      |#endif
      |  // Soft vignette using smoothstep for gradual falloff
      |  vec2 vigUV = vTexCoord * 2.0 - 1.0;
      |  float dist = dot(vigUV, vigUV);
      |  float vignette = 1.0 - smoothstep(0.4, 1.8, dist) * uVignetteStrength;
      |  color *= vignette;
      |#if OVERLAY
      |  // Overlay (generic color overlay: the match's fade in from black)
      |  color = mix(color, uOverlayColor.rgb, uOverlayColor.a);
      |#endif
      |#if HURT
      |  // Damage vignette: red glow at screen edges only
      |  if (uDamageVignette > 0.0) {
      |    float edgeDist = length(vigUV * vec2(1.0, 0.8));
      |    float edgeMask = smoothstep(0.5, 1.2, edgeDist);
      |    vec3 dmgColor = vec3(0.8, 0.05, 0.02);
      |    color = mix(color, dmgColor, edgeMask * uDamageVignette * 0.45);
      |  }
      |#endif
      |  FragColor = vec4(color, 1.0);
      |}""".stripMargin

  /** The composite's source for one combination of its parts (see COMPOSITE_BODY). */
  def compositeFrag(bloom: Boolean, wideBloom: Boolean, sharpen: Boolean, grain: Boolean, lightMap: Boolean,
                    tone: Int, distort: Boolean, hurt: Boolean, overlay: Boolean): String = {
    def b(on: Boolean) = if (on) 1 else 0
    s"""#version 330 core
       |#define BLOOM ${b(bloom)}
       |#define WIDE_BLOOM ${b(wideBloom)}
       |#define SHARPEN ${b(sharpen)}
       |#define GRAIN ${b(grain)}
       |#define LIGHT_MAP ${b(lightMap)}
       |#define TONE $tone
       |#define DISTORT ${b(distort)}
       |#define HURT ${b(hurt)}
       |#define OVERLAY ${b(overlay)}
       |""".stripMargin + COMPOSITE_BODY
  }

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
