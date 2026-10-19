# ViewBinding Id Finder

An Android Studio / IntelliJ plugin that connects your layout XML to the code that uses it
**through ViewBinding** — precisely, not by matching raw ids across the whole project.

## Features

- **Jump from an `android:id` to its ViewBinding usages.** Click the gutter icon next to any
  `android:id` in a layout and get only the places in code that access that view through the
  layout's generated binding class (e.g. `activity_main.xml` → `ActivityMainBinding`,
  `@+id/text_title` → `ActivityMainBinding.textTitle`).
- **Works with abstract / generic binding wrappers.** Detects usages even when the binding is
  exposed through a base class such as
  `abstract class ViewBindingActivity<VB : ViewBinding> { val binding: VB }` or a delegated
  `by viewBinding(...)` — not just direct `ActivityMainBinding` fields.
- **Find ViewBinding Parents.** Right-click a layout file (in the editor or in the Project
  view) → **Find ViewBinding Parents** to jump to the class(es) that use that layout's
  binding. Opens directly if there is one, shows a chooser if there are several.
- **Kotlin and Java** are both supported.

## How to use

1. **From a layout id:** open a layout under `res/layout*/`, find the gutter icon next to an
   `android:id`, and click it.
   - One usage → navigates straight to it.
   - Multiple usages → a popup lists them as `File:line`; pick one to jump.
2. **From a layout file:** right-click the `.xml` file (editor tab or Project view) →
   **Find ViewBinding Parents** to go to the screen(s) that use its binding.

## Requirements

- Android Studio Narwhal (2025.1) or newer, or a compatible IntelliJ-based IDE (build 251+).

## Install

**From Marketplace:** Settings → Plugins → Marketplace → search "ViewBinding Id Finder" → Install.

**From disk:** Settings → Plugins → ⚙ → Install Plugin from Disk… → choose the plugin `.zip`.

## Notes

- The binding class is derived from the layout file name, matching the default Android
  ViewBinding naming.
- Detection is static. Reflection-based or fully indirect access is not detected.

## License

Licensed under the [Apache License, Version 2.0](LICENSE).

Copyright 2026 Mehmet Kurtgoz.
