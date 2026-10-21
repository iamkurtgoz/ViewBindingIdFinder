# ViewBinding Id Finder — Development & Maintenance Guide

Internal guide for continuing development. Captures the architecture, the key design
decisions, problems we hit and how we solved them, and the build/publish workflow. Start here
before making changes.

---

## 1. What the plugin does

An Android Studio / IntelliJ plugin that links a layout XML `android:id` to the code that uses
that view **through its generated ViewBinding class** — not by matching the raw id everywhere.

Two entry points for the user:

1. **Gutter icon on `android:id`** (in `res/layout*/` XML): click it → lists the code usages
   that access the view via the layout's binding class. One result navigates directly;
   multiple show a `File:line` chooser popup.
2. **"Find ViewBinding Parents" action** (right-click a layout file in the editor OR the
   Project view): finds the class(es) that use that layout's binding class. One → opens
   directly; many → chooser popup.

Both Kotlin and Java are supported.

---

## 2. Tech stack / toolchain

- **Language:** Kotlin `1.9.25`
- **Build:** Gradle `9.5.0` (via wrapper — always use `./gradlew`)
- **Plugin framework:** IntelliJ Platform Gradle Plugin `2.19.0`
- **JDK:** 21 (auto-provisioned by the Gradle foojay toolchain resolver in `settings.gradle.kts`)
- **Target platform (compile against):** IntelliJ IDEA Community `2024.2.5`, with bundled
  `org.jetbrains.kotlin` and `com.intellij.java` plugins
- **Analysis API:** UAST (Unified AST) — gives one API across Kotlin + Java. Used best-effort
  for READ/WRITE/SET classification (see §4), NOT for the core matching.

> Note: we compile against IntelliJ IDEA CE, but the plugin ships for Android Studio. They
> share the same IntelliJ Platform base, so this works. Android Studio build numbers map to
> IntelliJ platform numbers (e.g. Narwhal 2025.1 = build `251`).

---

## 3. Project layout

```
build.gradle.kts                       # IntelliJ Platform Gradle Plugin config + plugin metadata
settings.gradle.kts                    # foojay toolchain resolver + project name
gradle.properties
gradle/wrapper/                        # Gradle 9.5.0 wrapper (jar committed)
src/main/kotlin/com/github/viewbindingidfinder/
    ViewBindingNaming.kt               # layout file name -> binding class; id -> field name
    ViewBindingUsageFinder.kt          # core: find usages of a binding field (PSI/import-driven)
    BindingUsage.kt                    # data model: BindingUsage + UsageKind enum
    ViewBindingLineMarkerProvider.kt   # gutter icon on android:id in layout XML
    BindingGutterNavigationHandler.kt  # gutter click handler + results popup
    FindBindingParentsAction.kt        # right-click action: find classes using a layout's binding
    PluginIcons.kt                     # icon loader
src/main/resources/
    META-INF/plugin.xml                # extensions + actions registration (metadata injected from Gradle)
    META-INF/pluginIcon.svg            # Marketplace icon
    icons/{binding,read,write}.svg
src/test/kotlin/com/github/viewbindingidfinder/
    ViewBindingNamingTest.kt           # unit tests for the naming logic
```

`plugin.xml` deliberately does NOT contain `<id>/<name>/<version>/<vendor>/<description>` —
those are injected by the `patchPluginXml` task from `intellijPlatform.pluginConfiguration`
in `build.gradle.kts`. Edit metadata in Gradle, not in `plugin.xml`.

---

## 4. How it works (architecture)

### Naming — `ViewBindingNaming.kt`
Pure, testable string logic that mirrors Android's ViewBinding generator:
- `activity_main.xml` → `ActivityMainBinding` (Pascal-case of file name + `Binding`)
- `@+id/text_title` / `@id/text_title` / `text_title` → `textTitle` (strip `@.../`, camelCase)

### Core matching — `ViewBindingUsageFinder.kt` (IMPORTANT — read before changing)
Given a binding class simple name (e.g. `ActivityMainBinding`) and a field name (e.g.
`bottomNavigation`), it finds usages in three steps:

1. **Index-backed word search** for the field name in code
   (`PsiSearchHelper.processElementsWithWord`, `IN_CODE`).
2. **PSI-level qualified-access check** (`isDotQualified`): is this identifier the selector in
   `something.fieldName`? We check the previous meaningful token is a `.` — purely at the PSI
   level, independent of UAST type resolution.
3. **File-binding check** (`fileIsBoundToBindingClass`): accept the usage only if the enclosing
   file references the binding class simple name anywhere (import, supertype type-arg, field
   declaration, `::inflate`, etc.), matched with a word-boundary regex. Cached per file.

READ/WRITE/SET classification (`classifyUsage`) is done best-effort via UAST; if UAST can't
resolve the shape, we default to READ and still keep the usage.

Results are de-duplicated by **physical location** (file path + start offset), because the
index can surface the same identifier more than once.

> **Why not match on the qualifier's static type?**
> The first implementation required the receiver's type to resolve to `ActivityMainBinding`.
> That FAILS for generic/abstract wrappers like:
> ```kotlin
> abstract class ViewBindingActivity<VB : ViewBinding> { protected val binding: VB }
> class MainActivity : ViewBindingActivity<ActivityMainBinding>()
> ```
> Here `binding`'s static type is `VB`/`ViewBinding`, never `ActivityMainBinding`, so every
> usage was rejected ("No usages found"). The file-binding approach (step 3) is framework-
> agnostic: it works for direct fields, delegated `by viewBinding(...)`, AND generic base
> classes, because the using file always references the concrete binding class somewhere.

### Gutter icon — `ViewBindingLineMarkerProvider.kt`
A `LineMarkerProvider` registered for `language="XML"`. Anchors on the
`XML_ATTRIBUTE_VALUE_TOKEN` leaf of an `android:id` attribute (platform requires leaf
elements). Only activates for files under `res/layout*/`. Builds the marker with
`BindingGutterNavigationHandler`.

### Click handler — `BindingGutterNavigationHandler.kt`
Runs the search in a background `Task.Backgroundable` + `ReadAction`, then:
- 0 results → message popup
- 1 result → navigate via `PsiNavigateUtil.navigate` (NOT `Navigatable.navigate`, which is
  `@ApiStatus.OverrideOnly` and fails plugin verification)
- many → `createPopupChooserBuilder` popup, labels are just `File:line`, with READ/WRITE icons
- Extra dedupe here by `(file, line)` so the same line never appears twice.

### Find parents — `FindBindingParentsAction.kt`
An `AnAction` added to both `EditorPopupMenu` and `ProjectViewPopupMenu`. Resolves the layout
file from `VIRTUAL_FILE` or `VIRTUAL_FILE_ARRAY` (Project view may only provide the array).
Uses `getActionUpdateThread() = BGT`. Derives the binding class from the file name and word-
searches code files (`.kt`/`.java`) that reference it, excluding the layout itself and the
generated binding source. 1 → open; many → chooser.

---

## 5. Compatibility range

Set in `build.gradle.kts` → `pluginConfiguration.ideaVersion`:
- `sinceBuild = "251"` → **Android Studio Narwhal (2025.1)** and newer.
- `untilBuild = provider { null }` → **no upper bound** (works on the latest builds, e.g.
  AI-262). Do NOT re-add an upper bound unless you re-test — a fixed `untilBuild` is what blocked
  newer Android Studio builds before.

Android Studio ↔ platform build: Jellyfish 2023.3 = 233, Koala 2024.1 = 241, Ladybug 2024.2 =
242, Meerkat 2024.3 = 243, Narwhal 2025.1 = 251.

Plugin verification (`pluginVerification.ides`) uses `select { ... sinceBuild = "251" }` on the
`IntellijIdeaCommunity` RELEASE channel. We intentionally do NOT use `recommended()` because it
pulled far-future EAP builds (263) that restructure the XML module and produced false
`com.intellij.psi.xml not found` failures.

---

## 6. Build / verify / run

```bash
./gradlew clean test buildPlugin         # build + unit tests; outputs build/distributions/*.zip
./gradlew verifyPluginProjectConfiguration   # quick config sanity check
./gradlew verifyPlugin                   # JetBrains Plugin Verifier (binary compat)
./gradlew runIde                         # sandbox IntelliJ CE with the plugin
./gradlew runIde --debug-jvm             # for attaching a debugger
```

To test inside a real Android Studio: `./gradlew buildPlugin`, then in Android Studio →
Settings → Plugins → ⚙ → **Install Plugin from Disk…** → pick
`build/distributions/ViewBindingIdFinder-<version>-signed.zip` → restart.

---

## 7. Signing & publishing (JetBrains Marketplace)

**Signing is mandatory for the Marketplace.** The signing material lives in the project root
and is git-ignored (see §8). It was generated once with:
```bash
openssl genpkey -aes-256-cbc -pass "pass:<PASS>" -algorithm RSA -out private.pem -pkeyopt rsa_keygen_bits:4096
openssl req -key private.pem -passin "pass:<PASS>" -new -x509 -days 3650 -subj "/CN=iamkurtgoz/..." -out chain.crt
# passphrase stored in .signing_passphrase
```

`build.gradle.kts` wires signing + publishing to env vars. To release:
```bash
export PUBLISH_TOKEN="<marketplace token>"             # Marketplace → My Tokens
export PRIVATE_KEY="$(cat private.pem)"
export CERTIFICATE_CHAIN="$(cat chain.crt)"
export PRIVATE_KEY_PASSWORD="$(cat .signing_passphrase)"

./gradlew clean buildPlugin signPlugin publishPlugin
```

Rules / gotchas:
- **The very first upload of a NEW plugin id must be done manually** via the web
  (https://plugins.jetbrains.com/plugin/add) to set license/repo etc. After that,
  `publishPlugin` works for updates.
- **A version number can never be reused** on the Marketplace. Always bump `version` forward.
- To change only the Marketplace page description, bump the version and `publishPlugin` (the
  description comes from `pluginConfiguration.description`), or edit it in the web panel.

### Releasing a new version
1. Bump `version` in `build.gradle.kts`.
2. Update `changeNotes`.
3. `./gradlew buildPlugin signPlugin publishPlugin` (with env vars set).
4. Commit + push (see §8).

---

## 8. Git & secrets

- Remote: `git@github.com:iamkurtgoz/ViewBindingIdFinder.git` (SSH, dedicated key
  `~/.ssh/id_ed25519_github`, mapped in `~/.ssh/config`).
- Default branch: `main`.
- **Never commit secrets.** `.gitignore` excludes `private.pem`, `chain.crt`,
  `.signing_passphrase`, `*.pem`, `*.crt`, `*.p12`, `*.ks`, plus `build/`, `.gradle/`,
  `.intellijPlatform/`. Always run a quick `git diff --cached --name-only | grep -E 'pem|crt|passphrase'`
  check before committing (we caught one accidental staging this way).
- License: Apache-2.0 (`LICENSE` at repo root).

---

## 9. Changelog of what we built (chronological)

- **1.0.0** — Initial plugin: naming logic, gutter LineMarkerProvider on `android:id`,
  usage finder, READ/WRITE/SET classification, popup navigation, unit tests, signing/publish
  wiring. First upload done manually to the Marketplace.
- **(fixes before 1.0.1)** — Resolved `verifyPlugin` failures: switched popup/navigation to
  non-deprecated APIs (`createPopupChooserBuilder`, `PsiNavigateUtil`), declared K1/K2 mode via
  `supportsKotlinPluginMode`, narrowed verification range.
- **1.0.1** — Removed the `untilBuild` upper bound so the plugin works on the latest Android
  Studio (AI-262); verified Compatible on 251–262.
- **1.0.2** — Core rework: usage detection is now PSI/import-driven so it finds usages through
  abstract/generic ViewBinding base classes (e.g. `ViewBindingActivity<VB>`); de-duplicated
  results (file+offset and file+line); simplified popup labels to `File:line`; added the
  **Find ViewBinding Parents** action (editor + Project view).
- **1.0.3** — Simplified README to usage+features; enriched the Marketplace description;
  published the updated plugin page.

---

## 10. Known limitations / ideas for later

- **File-binding heuristic (step 3)** is broad: if one file references `ActivityMainBinding`
  and also accesses `.someField` through an unrelated object, it could theoretically match.
  In practice a screen binds to one binding class, so this is safe. A stricter check could
  verify the receiver chains back to the `binding` property specifically.
- "Find ViewBinding Parents" treats "references the binding class name" as "uses it". Could be
  tightened to require an actual `inflate`/field usage.
- Binding class is derived purely from the layout file name (default Android naming).
  `tools:viewBindingIgnore`, module renames, and multi-module same-named layouts are not
  special-cased.
- Detection is static (PSI/UAST). Reflection-based or fully indirect access is not detected.
- READ/WRITE/SET classification is best-effort; icons may show READ when UAST can't resolve the
  surrounding expression.
