# IntelliJ plugin tour

The EVL plugin (JetBrains Marketplace, 30-day trial) adds:

- **EVL Map** (Tools | EVL | Open EVL Map): one zoomable map from the application down to single flow steps. Zoom with the wheel; nodes reveal their children as they grow, and called flows appear inside their calling step. Double-click opens an element, `F` focuses, `I` isolates, `0` fits.
- **Flow Explorer** (left): every flow, grouped by module, category, domain or call hierarchy, with search, pins and recent flows. Double-click or Enter opens a flow on the map.
- **Inspector** (right): fields, relationships, callers and callees, source, requirements, decisions and diagnostics of the selected element.
- **Editor**: EVL diagnostics in Java and YAML, schema completion in `.evl.yaml`, gutter icons on annotated declarations.
- **Navigation**: Back/Forward, Go to EVL Element (`⌥⌘E` / `Ctrl+Alt+Shift+E`), breadcrumbs, `evl://open?app=…&ref=…` links.
- **Export**: the current view as SVG or PNG.

Settings live in Settings | Tools | EVL. The plugin collects no telemetry.
