# Vitreous

Liquid glass for Jetpack Compose on Android 13+ (API 33), built to cost as little as possible. Buttons, panels, a top bar and groups that merge like liquid, with an elastic pull like iOS.

Built on [Cohesion](https://github.com/zainbits/cohesion), the shared glass engine. Neither is on Maven yet: clone both side by side (`vitreous/` and `cohesion/` in the same folder); this build includes `../cohesion`.

```kotlin
val backdrop = rememberGlassBackdrop()
ProvideGlass(clarity = 0.35f, dark = isSystemInDarkTheme()) {   // 0 = ultra-clear, 1 = fully tinted
    Box {
        Content(Modifier.glassSource(backdrop))                 // what shows through the glass
        GlassTopBar(backdrop, onBack = { }, backIcon = { Icon(...) }) { Text("Title") }
        GlassGroup(backdrop) { m ->                             // one render pass for the whole row
            Row(m) { GlassIconButton(...); GlassSurface(backdrop, shape = GlassShape.Capsule) { ... } }
        }
    }
}
```

Text and icons on glass adapt: `GlassSurface` and `GlassIconButton` set `LocalContentColor` dark over bright content and light over dark (`LocalGlassContentColors`), fading between them like iOS. Turn it off with `adaptiveContent = false`.

How it stays cheap:
- The content is recorded once per frame; glass replays that recording, and when the content scrolls the render thread refreshes the glass with no UI-thread work.
- Pieces in a `GlassGroup` (and everything in `GlassTopBar`) share one offscreen pass. The cost is per render pass, not per pixel.
- One AGSL shader does shape, lens, colour boost, tint and lit rim after a single platform blur per group. (A shader tap blur is available as an experiment; it saved CPU but cost more battery on the GPU.)
- `rememberAdaptiveGlassMode()` switches to plain tinted shapes in battery saver or when the phone is hot.

Test bench: `sample/` (HUD, tune panel, bench). `tools/bench.sh` runs scripted benchmarks on a connected phone.
