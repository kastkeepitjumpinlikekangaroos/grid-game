"""What the client's BUILD files share: who may use its libraries, and the assets its binaries load."""

# Who may depend on the client's libraries: the client itself
CLIENT = ["//src/main/scala/com/gridgame/client:__subpackages__"]

# The assets every binary (and the tests) load from the classpath
RESOURCES = [
    "//worlds:world_files",
    "//sprites:sprite_files",
    "//fonts:font_files",
    "//i18n:i18n_files",
    "//sounds:sound_files",
]
