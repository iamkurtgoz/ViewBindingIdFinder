# ViewBinding Id Finder

A JetBrains / Android Studio plugin that lets you jump **from an `android:id` in a layout XML
file to only the code usages that access that view through the layout's generated
`ViewBinding` class** — instead of every raw usage of the id across the whole project.

Example: in `res/layout/activity_main.xml` the view `@+id/text_title` maps to
`ActivityMainBinding.textTitle`. Clicking the gutter icon next to that id lists only the
places in Kotlin/Java code where a value typed as `ActivityMainBinding` accesses `.textTitle`.
Read usages and write/set usages are shown with distinct icons.

## How it works

1. A `LineMarkerProvider` adds a gutter icon next to every `android:id` value in files under
   `res/layout*/`.
2. On click, the plugin derives:
   - the binding **class** name from the layout file name
     (`activity_main.xml` → `ActivityMainBinding`), and
   - the binding **field** name from the id (`text_title` → `textTitle`).
   This mapping lives in `ViewBindingNaming.kt`.
3. `ViewBindingUsageFinder` does an index-backed word search for the field name in code, then
   verifies each candidate with UAST: it keeps a usage **only if** the qualifier (receiver)
   expression is typed as the expected binding class. This is what makes it precise instead of
   matching every `something.textTitle` in the project.
4. Each kept usage is classified:
   - `READ` — the value is read (e.g. `binding.textTitle.text`),
   - `WRITE` — the field itself is assigned (rare),
   - `SET` — a mutating call or property set on the view (e.g. `binding.textTitle.setText(..)`
     or `binding.textTitle.text = "..."`).
5. Results are shown in a popup with read/write icons; selecting one navigates to it.

Works with both Kotlin and Java (resolution is done via UAST).

## Project layout

```
build.gradle.kts                      # IntelliJ Platform Gradle Plugin 2.x config
settings.gradle.kts                   # foojay toolchain resolver
gradle.properties
gradle/wrapper/…                      # Gradle 9.5.0 wrapper
src/main/kotlin/com/github/viewbindingidfinder/
    ViewBindingNaming.kt              # layout->class and id->field naming
    ViewBindingUsageFinder.kt         # UAST search filtered by binding type + read/write
    BindingUsage.kt                   # usage model + UsageKind enum
    ViewBindingLineMarkerProvider.kt  # gutter icon on android:id
    BindingGutterNavigationHandler.kt # click handler + results popup
    PluginIcons.kt
src/main/resources/
    META-INF/plugin.xml               # extension registration
    META-INF/pluginIcon.svg           # Marketplace icon
    icons/*.svg
src/test/kotlin/…/ViewBindingNamingTest.kt
```

## Requirements

- JDK 21 (the Gradle toolchain auto-provisions it via the foojay resolver).
- Gradle 9.5+ (provided by the wrapper — always use `./gradlew`).
- Target platform: IntelliJ IDEA Community 2024.2.5 with the bundled Kotlin and Java plugins.
  The resulting plugin is compatible with Android Studio (which is built on the same platform,
  `sinceBuild = 242`).

## Build

```bash
./gradlew clean test buildPlugin
```

Outputs the installable zip at:

```
build/distributions/ViewBindingIdFinder-1.0.0.zip
```

Run the configuration sanity check at any time:

```bash
./gradlew verifyPluginProjectConfiguration
```

## Run / debug in a sandbox IDE

Launch a sandbox IntelliJ IDEA with the plugin loaded:

```bash
./gradlew runIde
```

To debug, run the same task from your IDE's Gradle tool window in debug mode, or:

```bash
./gradlew runIde --debug-jvm
```

then attach a remote JVM debugger to the printed port.

### Trying it against Android Studio specifically

`runIde` starts the IDE that the build depends on (IntelliJ IDEA CE here). To exercise the
plugin inside Android Studio instead, install the built zip manually:

1. Build the zip (`./gradlew buildPlugin`).
2. In Android Studio: **Settings → Plugins → ⚙ → Install Plugin from Disk…** and choose
   `build/distributions/ViewBindingIdFinder-1.0.0.zip`.
3. Restart. Open a layout XML with `android:id` attributes and look for the gutter icon.

> If you want `runIde` to launch Android Studio directly, change the dependency in
> `build.gradle.kts` from `intellijIdeaCommunity("2024.2.5")` to a local Android Studio path
> using `local("/path/to/Android Studio.app")`. See the "Local IDE" section of the IntelliJ
> Platform Gradle Plugin docs.

## Install the pre-built zip (no build needed)

Share `build/distributions/ViewBindingIdFinder-1.0.0.zip` and install it via
**Settings → Plugins → Install Plugin from Disk…** in IntelliJ IDEA or Android Studio.

## Verify compatibility before publishing

Runs JetBrains' Plugin Verifier against recommended IDE versions:

```bash
./gradlew verifyPlugin
```

Review the report under `build/reports/pluginVerifier/`.

## Publishing to JetBrains Marketplace

### One-time setup

1. Create a vendor/account and reserve the plugin on the Marketplace:
   https://plugins.jetbrains.com/
2. Update the placeholder metadata in `build.gradle.kts` under
   `intellijPlatform.pluginConfiguration` → `vendor` (name, email, url). The `id`
   (`com.github.viewbindingidfinder`) must be globally unique on the Marketplace; change it if
   it is taken.
3. Generate a **Marketplace permanent token**: Marketplace → your profile → **My Tokens**.

### Signing (required for Marketplace)

Generate a signing certificate/key (see
https://plugins.jetbrains.com/docs/intellij/plugin-signing.html) and export these environment
variables before publishing (the build already wires them up in `intellijPlatform.signing`):

```bash
export CERTIFICATE_CHAIN="$(cat chain.crt)"
export PRIVATE_KEY="$(cat private.pem)"
export PRIVATE_KEY_PASSWORD="your-key-password"
```

Verify signing locally:

```bash
./gradlew signPlugin
```

### Publish

```bash
export PUBLISH_TOKEN="your-marketplace-token"
./gradlew publishPlugin
```

This signs and uploads the plugin. The first upload of a new plugin id goes through manual
JetBrains review before it becomes publicly visible; subsequent updates are usually automatic.

### Versioning

Bump `version` in `build.gradle.kts` and update `changeNotes` for every release.

## Known limitations / notes

- The binding **class** is derived purely from the layout file name. This matches the default
  Android ViewBinding naming. Custom `tools:viewBindingIgnore` or renamed modules are not
  special-cased.
- Matching is by the binding class **simple name**. If two modules generate binding classes
  with the same simple name for identically named layouts, usages from both would be listed.
- Detection is static (UAST). Reflection-based or indirect access is not detected.

## License

Licensed under the [Apache License, Version 2.0](LICENSE).

Copyright 2026 Mehmet Kurtgoz.
