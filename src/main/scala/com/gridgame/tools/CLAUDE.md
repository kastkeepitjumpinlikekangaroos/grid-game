# tools

Dev tools that keep generated text in step with the roster. Not shipped.

- `gendocs` (`GenDocsRoster`, on `DocsRoster`) — rewrites each character card's role and health
  pill in `docs/index.html` from the roster: `bazel run //src/main/scala/com/gridgame/tools:gendocs`.
  `DocsRosterTest` fails until it has been run after a change to a role or a health.
- `gencontent` (`GenContentCatalog`) — regenerates the English game-content entries of
  `i18n/messages_en.json` (every character's and ability's name and description):
  `bazel run //src/main/scala/com/gridgame/tools:gencontent 2>/dev/null`. The catalog wins over the
  code (`I18n.tOr`), so renaming an ability in the roster alone leaves the old name on every screen;
  `ContentCatalogTest` catches it.

## Website (GitHub Pages)

Static landing page served from the `docs/` directory on `main` via GitHub Pages.

### Files
- `docs/index.html` — Single-page site (hero, features, characters, controls, download). Each
  character card's pill ("Melee &middot; 145 HP") is written from the roster by
  `bazel run //src/main/scala/com/gridgame/tools:gendocs` (`tools/DocsRoster`). Run it after any
  change to a role or a health; `DocsRosterTest` fails until you do.
- `docs/style.css` — Dark theme stylesheet
- `docs/script.js` — Smooth scroll for nav anchors
- `docs/BUILD.bazel` — Bazel filegroup target

### Deployment
GitHub Pages is configured to deploy from `main` branch, `/docs` directory. Any push to `main` that modifies `docs/` will auto-deploy.

### Creating Releases (Deploy JARs)
Bazel's `scala_binary` auto-supports `_deploy.jar` suffix targets, producing fat JARs with all dependencies bundled. No BUILD file changes needed.

```bash
# Build fat JARs (client is the build machine's: made on the Mac, it is the macOS one)
bazel build //src/main/scala/com/gridgame/client:client_deploy.jar
bazel build //src/main/scala/com/gridgame/client:client_windows_deploy.jar
bazel build //src/main/scala/com/gridgame/client:client_linux_deploy.jar
bazel build //src/main/scala/com/gridgame/client:client_linux_arm64_deploy.jar

# The Linux apps with their own runtime (no Java to install), and the .debs, which are how a
# Chromebook installs the game (client/CLAUDE.md, *Packaging the apps*; Docker off Linux)
scripts/build_linux_package.sh --arch x86_64 --deb
scripts/build_linux_package.sh --arch arm64 --deb

# Create a GitHub release with them all (each .deb's name carries the minute it was built)
DEB_X64=$(ls dist/linux/grid-game_*_amd64.deb); DEB_ARM64=$(ls dist/linux/grid-game_*_arm64.deb)
gh release create v1.0.0 \
  bazel-bin/src/main/scala/com/gridgame/client/client_deploy.jar#"Grid Game (macOS)" \
  bazel-bin/src/main/scala/com/gridgame/client/client_windows_deploy.jar#"Grid Game (Windows)" \
  bazel-bin/src/main/scala/com/gridgame/client/client_linux_deploy.jar#"Grid Game (Linux x86_64, jar)" \
  bazel-bin/src/main/scala/com/gridgame/client/client_linux_arm64_deploy.jar#"Grid Game (Linux arm64, jar)" \
  "$DEB_X64#Grid Game (Linux and Chromebook x86_64, .deb)" \
  "$DEB_ARM64#Grid Game (Linux and Chromebook arm64, .deb)" \
  dist/linux/grid-game-linux-x86_64.tar.gz#"Grid Game (Linux x86_64)" \
  dist/linux/grid-game-linux-arm64.tar.gz#"Grid Game (Linux arm64)"
```
