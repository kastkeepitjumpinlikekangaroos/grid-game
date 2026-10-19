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

# What a binary runs with, per platform: JavaFX, and LWJGL's native libraries. A Linux build is one
# per processor: JavaFX's jars for Linux keep their natives at the top of the jar, under the same
# names on x86_64 and arm64, so a program carrying both would load whichever came first.
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

JAVAFX_LINUX = [
    "@maven//:org_openjfx_javafx_base_linux",
    "@maven//:org_openjfx_javafx_controls_linux",
    "@maven//:org_openjfx_javafx_graphics_linux",
]

JAVAFX_LINUX_ARM64 = [
    "@maven//:org_openjfx_javafx_base_linux_aarch64",
    "@maven//:org_openjfx_javafx_controls_linux_aarch64",
    "@maven//:org_openjfx_javafx_graphics_linux_aarch64",
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

LWJGL_LINUX_NATIVES = [
    "@maven//:org_lwjgl_lwjgl_natives_linux",
    "@maven//:org_lwjgl_lwjgl_glfw_natives_linux",
    "@maven//:org_lwjgl_lwjgl_opengl_natives_linux",
    "@maven//:org_lwjgl_lwjgl_stb_natives_linux",
]

LWJGL_LINUX_ARM64_NATIVES = [
    "@maven//:org_lwjgl_lwjgl_natives_linux_arm64",
    "@maven//:org_lwjgl_lwjgl_glfw_natives_linux_arm64",
    "@maven//:org_lwjgl_lwjgl_opengl_natives_linux_arm64",
    "@maven//:org_lwjgl_lwjgl_stb_natives_linux_arm64",
]

MAC_NATIVES = JAVAFX_MAC + LWJGL_MAC_NATIVES

WINDOWS_NATIVES = JAVAFX_WINDOWS + LWJGL_WINDOWS_NATIVES

LINUX_NATIVES = JAVAFX_LINUX + LWJGL_LINUX_NATIVES

LINUX_ARM64_NATIVES = JAVAFX_LINUX_ARM64 + LWJGL_LINUX_ARM64_NATIVES

# The machine doing the build's: what the tests, the dev tools and a plain `bazel run` of the
# client or the map editor run with
HOST_NATIVES = select({
    "@bazel_tools//src/conditions:windows": WINDOWS_NATIVES,
    "@bazel_tools//src/conditions:linux_x86_64": LINUX_NATIVES,
    "@bazel_tools//src/conditions:linux_aarch64": LINUX_ARM64_NATIVES,
    "//conditions:default": MAC_NATIVES,
})

JAVAFX_HOST = select({
    "@bazel_tools//src/conditions:windows": JAVAFX_WINDOWS,
    "@bazel_tools//src/conditions:linux_x86_64": JAVAFX_LINUX,
    "@bazel_tools//src/conditions:linux_aarch64": JAVAFX_LINUX_ARM64,
    "//conditions:default": JAVAFX_MAC,
})

def on_mac(jvm_flags):
    """`jvm_flags` when the build machine is a Mac, and none elsewhere: the java of any other system
    refuses to start with a Mac's own flags (-XstartOnFirstThread, -Xdock:name)."""
    return select({
        "@bazel_tools//src/conditions:darwin": jvm_flags,
        "//conditions:default": [],
    })
