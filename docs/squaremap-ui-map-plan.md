# In-UI Real Map Background Plan (Squaremap Tiles + Fallback)

## Goal
Add a **real terrain map background** to the existing Faction Map tab by using Squaremap-rendered web tiles when available, while preserving the current claim-grid-only rendering as the fallback.

## Current baseline (what exists now)
- The faction map UI currently renders a chunk grid from claim data only.
- The UI has no terrain tile/background rendering pipeline.
- Squaremap integration currently exists server-side for web-layer claim markers, not for in-UI tile rendering.

## Non-goals (initial version)
- Replacing the current claim overlay model.
- Hard dependency on Squaremap (must stay optional).
- Full browser embedding in the Minecraft UI.

## Constraint: Preserve recent GUI scaling behavior
- The implementation must preserve the newer responsive Faction UI layout behavior (small/large GUI scale and varying resolutions).
- Any map background controls should integrate with existing dynamic layout calculations, not hardcoded pixel coordinates.
- If available map area is tight, controls should collapse/hide before clipping the map panel.

## High-level approach
1. Keep the current claim rendering as the authoritative overlay and interaction surface.
2. Add an optional "map background provider" abstraction on the client.
3. Implement a Squaremap tile provider that:
   - discovers Squaremap availability/config via server packet metadata,
   - requests PNG/JPG tile images over HTTP,
   - caches and renders tile textures behind the existing claim cells.
4. If availability check fails at any stage, render exactly the current grid-only background.

## Phased implementation

### Phase 1 — Architecture + fallback-safe scaffolding
- Add a new client abstraction, for example:
  - `MapBackgroundProvider` interface
  - `NoopBackgroundProvider` (fallback/current behavior)
  - `SquaremapTileBackgroundProvider` (disabled until configured)
- Add a small `MapBackgroundState` attached to the existing map snapshot lifecycle.
- Keep render order:
  1. background (optional),
  2. claim cells,
  3. selection and tooltips.

**Acceptance:**
- With no Squaremap metadata, the UI looks unchanged.
- At GUI scales 2/3/4 and common 16:9 + ultrawide resolutions, the map panel remains readable with no control overlap.

### Phase 2 — Server capability/config handshake
- Extend existing claim-map packet (or add a companion packet) with optional webmap metadata:
  - provider type (`NONE`, `SQUAREMAP`)
  - base tile URL
  - world/dimension mapping key
  - min/max/default zoom hints
- Server should only send Squaremap metadata when:
  - Squaremap is detected and configured,
  - URL config is valid.
- Client defaults to `NONE` if metadata missing/invalid.

**Acceptance:**
- Old clients still function (if using versioned/optional fields).
- New client auto-falls back when metadata absent.

### Phase 3 — Tile math + fetch + cache
- Implement chunk-to-tile projection utility:
  - convert chunk center/world coords -> tile x/y at selected zoom.
- Build a bounded in-memory tile cache (LRU style) keyed by `(dimension, zoom, tileX, tileY)`.
- Fetch tiles asynchronously (no render-thread blocking).
- Add simple failure policy:
  - timeouts, retry limit, and negative cache entries for repeated misses.

**Acceptance:**
- Moving around the map loads nearby tiles progressively.
- Missing tiles do not break rendering; fallback background remains visible.

### Phase 4 — UI controls + UX polish
- Add optional controls in Faction Map tab:
  - "Background: Off / Squaremap"
  - zoom in/out (bounded by metadata hints)
  - "Reset view to player"
- Place new controls through existing panel layout helpers so button/field placement tracks GUI scaling updates.
- For narrow layouts, prefer compact controls (icons or single cycle button) over introducing a second overlapping row.
- Add subtle loading indicator when tile requests are in-flight.
- Persist per-client preference for background enabled/disabled.

**Acceptance:**
- Users can disable terrain background and return to current look instantly.

### Phase 5 — Observability + hardening
- Add debug logs around provider selection and URL health checks.
- Add basic metrics counters (requests, cache hits, failures) if project has telemetry hooks.
- Ensure graceful degradation for:
  - offline client,
  - invalid TLS cert/network errors,
  - dimension mismatch.

**Acceptance:**
- No crashes when tile endpoint is unreachable.
- Rendering remains stable under repeated failures.

## Fallback behavior matrix

| Condition | Behavior |
|---|---|
| Squaremap mod not present server-side | Use `NoopBackgroundProvider` (current map view) |
| Squaremap present, but metadata disabled/missing | Fallback to `NoopBackgroundProvider` |
| Metadata present, tile fetch fails/timeouts | Keep claim overlay + fallback background, continue retry policy |
| User manually disables background | Force fallback/current view |

## Suggested class/file touch points (first pass)
- Client GUI renderer layer:
  - `src/main/java/com/mcprotector/client/gui/FactionMapRenderer.java`
- Client map state:
  - `src/main/java/com/mcprotector/client/FactionMapClientData.java`
- Packet metadata path:
  - `src/main/java/com/mcprotector/network/FactionClaimMapPacket.java`
- Optional config hooks for server-side metadata:
  - `src/main/java/com/mcprotector/config/FactionConfig.java`
- Optional bridge metadata helper:
  - `src/main/java/com/mcprotector/webmap/SquaremapBridge.java`

## Validation checklist (include in implementation PRs)
- Verify fallback rendering with Squaremap absent: map remains current grid-only view.
- Verify metadata-present but endpoint-down behavior: no crash, no render-thread stutter.
- Verify GUI scale compatibility at minimum window sizes used by players.
- Verify map interaction still works (hover tooltips, drag select, claim submit) with background on/off.

## Risks and mitigations
- **Risk:** URL/tile scheme differs per Squaremap deployment.
  - **Mitigation:** metadata-driven URL template from server config, not hardcoded assumptions.
- **Risk:** Render-thread stalls from network/image decoding.
  - **Mitigation:** async fetch/decode + render only ready textures.
- **Risk:** packet compatibility issues.
  - **Mitigation:** additive optional fields or companion packet with safe defaults.

## Deliverable slicing (recommended PRs)
1. **PR 1:** provider abstraction + noop fallback + no visual changes.
2. **PR 2:** packet metadata handshake + config plumbing.
3. **PR 3:** Squaremap tile provider (fetch/cache/render) behind feature flag.
4. **PR 4:** controls, persistence, polish, and docs.
