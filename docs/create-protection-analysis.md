# Analysis: Why Create machines can still affect faction-protected chunks

## Scope reviewed
- `ClaimProtectionHandler` event hooks and permission checks.
- `FactionData` claim permission resolution.
- `FactionConfig` defaults related to automation/fake players.

## Main finding
The current protection layer is **primarily player-interaction event based** (`BlockEvent.BreakEvent`, `EntityPlaceEvent`, right-click interaction events).

That works for normal player actions, but some Create systems (for example contraption-driven tools like the Mechanical Drill and some Schematic Cannon flows) can modify world blocks through non-player world operations. If those operations do not emit the specific events currently listened to, they bypass this handler entirely.

## Evidence in code
1. **Break protection is tied to player break events only**
   - `onBlockBreak` only handles `BlockEvent.BreakEvent` and depends on `event.getPlayer()` permission checks.
   - There is no additional generic guard for machine-driven block destruction paths.

2. **Placement protection assumes `EntityPlaceEvent` / `EntityMultiPlaceEvent` are fired**
   - Non-player placement is canceled when chunk is claimed, but only if those exact place events are emitted.
   - If a mod performs direct set/remove operations without these events, the protection code never runs.

3. **Create-specific permission checks are interaction-oriented, not world-mutation-oriented**
   - Create detection (`isCreateBlock` / `isCreateBlockEntity`) is used for right-click/use permissions and create block placement checks.
   - There is no Create-specific hook around contraption harvest/destroy logic.

4. **Fake player controls only apply when a `ServerPlayer`/`FakePlayer` reaches `isAllowed`**
   - `allowFakePlayerActionsInClaims` protects actions attributed to fake players.
   - If Create action path is not represented as a fake player (or bypasses the checked events), this toggle cannot help.

## Most likely bypass scenarios
1. **Contraption break path bypass**
   - Mechanical Drill/contraption destroys a protected block using an internal world call path that does not fire `BlockEvent.BreakEvent`.

2. **Schematic Cannon place/replace path mismatch**
   - Cannon may place/replace via internals where normal place events are missing or happen in a way not attributable to a player/fake player that reaches these checks.

3. **Cross-boundary machine actions**
   - A machine physically in unclaimed chunk affecting claimed chunk can bypass ownership checks if only source-side interaction was authorized and target-side block mutation was not event-checked.

## Practical fix options (non-mutually exclusive)

### Option A: Add lower-level block mutation guards (recommended)
Introduce additional server-side handlers/mixins covering non-player block mutation paths (block remove/replace) and enforce claim checks on the **target position** regardless of actor type.

- Pros: Strongest protection, covers more modded automation.
- Cons: More implementation complexity; careful compatibility handling needed.

### Option B: Add Create integration hooks for contraption actions
Add optional Create integration layer that intercepts contraption tool actions (drill/saw/plough/schematic cannon operations) and validates claimed target positions before execution.

- Pros: Precise for known offenders; less global risk than fully generic interception.
- Cons: Requires Create-version-aware integration and maintenance.

### Option C: Post-action reconciliation safeguard
Track/validate block changes in claimed chunks and revert unauthorized machine changes.

- Pros: Defensive fallback when upstream hooks are incomplete.
- Cons: Heavier; can cause visual flicker/dupe edge cases if not carefully designed.

### Option D: Hard policy for machine effects in claimed chunks
Config gate to deny all non-player world mutations inside claims unless explicitly whitelisted by owner faction.

- Pros: Simple rule, high safety.
- Cons: Restrictive gameplay; may block benign automation.

## Suggested implementation strategy
1. Keep existing event handlers (they are still correct for player actions).
2. Add a dedicated “non-player block mutation” protection layer first (Option A).
3. Add Create-specific hooks second for known contraption/schematic paths (Option B).
4. Keep a minimal reconciliation fallback for unknown mod interactions (Option C).

## Quick validation checklist after implementation
- Mechanical Drill cannot break claimed enemy blocks.
- Schematic Cannon cannot place/replace blocks in enemy claim.
- Machine in unclaimed chunk cannot affect claimed target chunk.
- Own-faction/trusted behavior still matches configured permissions.
- Fake-player toggle behaves as expected for mods that do use fake players.

## Implementation status (current)
- ✅ Step 1 complete: existing player event handlers are preserved.
- ✅ Step 2 complete: non-player mutation layer covers pistons, living destroys, mob griefing, fluid spread, explosion filtering, and tool-based block transforms.
- ✅ Step 3 complete: Create-specific heuristics gate likely machine breaks and machine-driven placement allowances for same-owner claims, with configurable enable/disable, owner-allow, and radius controls.
- ✅ Step 4 complete: minimal reconciliation fallback is in place with configurable enable/disable and a max-restores-per-tick safety limit.


- 🟡 New hardening increment: tool-based block transformations (`BlockToolModificationEvent`) now go through mutation checks, and strict non-player mutation behavior is configurable (`strictNonPlayerMutationChecks`).

### Practical current stage
The original 4-step strategy is now implemented. The remaining future hardening item is deeper low-level mutation interception (mixin/core-level) to reduce dependence on event and heuristic coverage.
