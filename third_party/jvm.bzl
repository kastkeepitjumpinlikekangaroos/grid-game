"""The Maven jars the game's libraries compile against, grouped, and what its binaries run with on
each platform (MODULE.bazel installs them)."""

# JavaFX's API, which a library only compiles against: JavaFX is a jar per platform (its classes
# and that platform's natives), and a program runs with the ones its binary names, from below. When
# the libraries carried a platform's jars themselves, every binary got the build machine's: a
# Windows build made on a Mac shipped the Mac jars, ahead of its own on the classpath.
JAVAFX = ["//third_party:javafx"]

LWJGL = [
    "@maven//:org_lwjgl_lwjgl",
    "@maven//:org_lwjgl_lwjgl_glfw",
    "@maven//:org_lwjgl_lwjgl_opengl",
    "@maven//:org_lwjgl_lwjgl_stb",
]

GSON = ["@maven//:com_google_code_gson_gson"]

NETTY = [
    "@maven//:io_netty_netty_buffer",
    "@maven//:io_netty_netty_codec",
    "@maven//:io_netty_netty_common",
    "@maven//:io_netty_netty_handler",
    "@maven//:io_netty_netty_transport",
]

# What a binary runs with, per platform: JavaFX, and LWJGL's native libraries
JAVAFX_MAC = [
    "@maven//:org_openjfx_javafx_base_mac_aarch64",
    "@maven//:org_openjfx_javafx_controls_mac_aarch64",
    "@maven//:org_openjfx_javafx_graphics_mac_aarch64",
]

JAVAFX_WINDOWS = [
    "@maven//:org_openjfx_javafx_base_win",
    "@maven//:org_openjfx_javafx_controls_win",
    "@maven//:org_openjfx_javafx_graphics_win",
]

LWJGL_MAC_NATIVES = [
    "@maven//:org_lwjgl_lwjgl_natives_macos_arm64",
    "@maven//:org_lwjgl_lwjgl_glfw_natives_macos_arm64",
    "@maven//:org_lwjgl_lwjgl_opengl_natives_macos_arm64",
    "@maven//:org_lwjgl_lwjgl_stb_natives_macos_arm64",
]

LWJGL_WINDOWS_NATIVES = [
    "@maven//:org_lwjgl_lwjgl_natives_windows",
    "@maven//:org_lwjgl_lwjgl_glfw_natives_windows",
    "@maven//:org_lwjgl_lwjgl_opengl_natives_windows",
    "@maven//:org_lwjgl_lwjgl_stb_natives_windows",
]

MAC_NATIVES = JAVAFX_MAC + LWJGL_MAC_NATIVES

WINDOWS_NATIVES = JAVAFX_WINDOWS + LWJGL_WINDOWS_NATIVES

# The machine doing the build's: what the tests run with
HOST_NATIVES = select({
    "@bazel_tools//src/conditions:windows": WINDOWS_NATIVES,
    "//conditions:default": MAC_NATIVES,
})
