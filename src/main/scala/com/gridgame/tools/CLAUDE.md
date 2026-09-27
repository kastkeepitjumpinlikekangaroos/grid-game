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
# Build fat JARs
bazel build //src/main/scala/com/gridgame/client:client_deploy.jar
bazel build //src/main/scala/com/gridgame/client:client_windows_deploy.jar

# Create a GitHub release with both JARs
gh release create v1.0.0 \
  bazel-bin/src/main/scala/com/gridgame/client/client_deploy.jar#"Grid Game (macOS)" \
  bazel-bin/src/main/scala/com/gridgame/client/client_windows_deploy.jar#"Grid Game (Windows)"
```
