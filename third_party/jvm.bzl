"""The Maven jars the game's libraries compile against, grouped, and what its binaries run with on
each platform (MODULE.bazel installs them)."""

# JavaFX's API. The plain artifacts are empty: the classes are in each platform's own jars, which
# are what the libraries compile against. On a Windows machine coursier resolves only the Windows
# ones, so they are picked by the OS; a Mac cross-building client_windows compiles against its own.
JAVAFX = [
    "@maven//:org_openjfx_javafx_base",
    "@maven//:org_openjfx_javafx_controls",
    "@maven//:org_openjfx_javafx_graphics",
] + select({
    "@bazel_tools//src/conditions:windows": [
        "@maven//:org_openjfx_javafx_base_win",
        "@maven//:org_openjfx_javafx_controls_win",
        "@maven//:org_openjfx_javafx_graphics_win",
    ],
    "//conditions:default": [
        "@maven//:org_openjfx_javafx_base_mac_aarch64",
        "@maven//:org_openjfx_javafx_controls_mac_aarch64",
        "@maven//:org_openjfx_javafx_graphics_mac_aarch64",
    ],
})

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

# The native libraries a binary runs with, per platform
LWJGL_MAC_NATIVES = [
    "@maven//:org_lwjgl_lwjgl_natives_macos_arm64",
    "@maven//:org_lwjgl_lwjgl_glfw_natives_macos_arm64",
    "@maven//:org_lwjgl_lwjgl_opengl_natives_macos_arm64",
    "@maven//:org_lwjgl_lwjgl_stb_natives_macos_arm64",
]

MAC_NATIVES = [
    "@maven//:org_openjfx_javafx_base_mac_aarch64",
    "@maven//:org_openjfx_javafx_controls_mac_aarch64",
    "@maven//:org_openjfx_javafx_graphics_mac_aarch64",
] + LWJGL_MAC_NATIVES

WINDOWS_NATIVES = [
    "@maven//:org_openjfx_javafx_base_win",
    "@maven//:org_openjfx_javafx_controls_win",
    "@maven//:org_openjfx_javafx_graphics_win",
    "@maven//:org_lwjgl_lwjgl_natives_windows",
    "@maven//:org_lwjgl_lwjgl_glfw_natives_windows",
    "@maven//:org_lwjgl_lwjgl_opengl_natives_windows",
    "@maven//:org_lwjgl_lwjgl_stb_natives_windows",
]
