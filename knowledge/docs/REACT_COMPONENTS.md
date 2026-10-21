# Supplied UI designs and native integration

The Android app is Kotlin/Jetpack Compose. React DOM components cannot be pasted into
its composable layout. Native counterparts now live in `DesignControls.kt`,
`MusicPlayerWidget.kt`, `LightingEditor.kt` and `Appearance.kt`. These are the components
shown by the actual Android localhost viewer at http://localhost:8767.

The reusable React reference is also provided at the repository root:

- `components/ui/cromia-color-picker.tsx`
- `components/ui/music-player-widget.tsx`
- `components/ui/liquid-toggle.tsx`
- `components/ui/slider-05.tsx`
- `styles/index.css` — Tailwind 4 imports, theme tokens and scoped music/animation styles.
- `components.json` — shadcn aliases, CSS location and TypeScript settings.
- `component-lab/main.tsx` — reference usage, supplied track/cover URLs and theme controls.

`@/components/ui` resolves to the root `components/ui` directory. Keeping reusable
controls there makes the supplied imports and shadcn's `aliases.ui` agree; native
Kotlin files keep their existing Android package location. `@/lib/utils` provides `cn`.

## Setup and checks

Dependencies are installed and pinned in `package-lock.json`. From the project root:

```powershell
npm ci
npm run build:components
npm run dev:components
```

The optional reference page is http://localhost:8768/component-lab.html. It is not the
native app. The actual app viewer continues to use port 8767. The component reference
defaults to silent in-memory playback; it does not load the sample MP3s. Passing
`preview={false}` enables the standalone HTML audio component for supplied media,
which is separate from Spotify controller functionality.

The built reference is also included in `artifacts/ui-components/` and served by the
running Android viewer at http://localhost:8767/components. This is a lightweight
way to review the requested React controls without depending on emulator performance;
it is explicitly labelled as a component reference rather than the native app.

For a fresh equivalent React setup, initialize a Vite React/TypeScript project, install
React, React DOM, lucide-react, clsx and tailwind-merge, then install Tailwind CSS 4,
`@tailwindcss/vite`, TypeScript, React type definitions and `tw-animate-css`. Configure
the Vite React/Tailwind plugins and `@/*` alias before running `npx shadcn@latest init`.
This repository already contains the equivalent configuration, so reinitialization
is unnecessary. Official references: [Tailwind Vite setup](https://tailwindcss.com/docs/installation/using-vite),
[shadcn alias configuration](https://ui.shadcn.com/docs/components-json), and
[React 19 ref compatibility](https://react.dev/blog/2024/04/25/react-19-upgrade-guide).

## Design adaptations

The pasted colour picker was compiled JavaScript including bundled render helpers
and React compiler caches. The reference file is readable typed source with the
same principal hue/saturation/brightness/alpha props, colour area, sliders, HEX input,
swatches and numeric channels; controlled colour/change/commit props are supported.
It does not require a compiler-runtime import or a global context provider.

The native picker targets RGB LEDs, so it uses RGB HEX and HSV colour selection.
LED output level is controlled by brightness; alpha transparency is a React colour
reference feature rather than an unsupported field sent to the current RGB API.
Manual colour controls appear only when Move with music is off, including presets.

The music design is adapted for a Spotify remote: record artwork, playback controls,
progress, shuffle/repeat and volume UI. Native seek/shuffle/repeat currently operate
only on preview state; existing live transports still require those operations to
be connected after the UI review. No local MP3 player or synthesized transition sound
is substituted for Spotify on Android. Native record artwork is drawn locally and
requires no remote image download. The React reference uses the supplied cover URLs.

The supplied liquid-toggle and Slider05 snippets contain usage examples but not
the implementation modules. Matching typed and native implementations are provided.
On/off settings use switches; brightness and volume retain continuous 0–100 controls.
The simulation banner was removed from the app layout as requested; the surrounding
localhost viewer still identifies preview data and no physical outputs.
