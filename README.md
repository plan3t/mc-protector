# MC Protector

MC Protector is a **NeoForge mod for Minecraft 1.21.1** that adds a full factions and land protection system. The current build is **alpha**, so expect ongoing balancing and feature polish.

## Alpha status
- **Versioning:** prerelease tags (for example, `0.1.0-alpha.1`) indicate that features may change before a stable release.
- **Feedback:** please report issues or balance feedback as you play so we can iterate quickly.

## Features (current alpha)
- **Faction lifecycle:** create, disband (with confirmation), join/leave, invite, kick, promote/demote, and custom roles.
- **Permissions & ranks:** role-based permission grants, rank display name presets, MOTD/description settings, and configurable banner/color styling.
- **Claims & protection:** claim/unclaim chunks, auto-claim, overtake mechanics, protection tiers, trusted players, claim access logs, and borders.
- **Homes & maps:** faction home set/teleport, in-game map summaries, and a client-side faction map UI.
- **Relations:** ally/war relations plus **vassal contracts** with offers, acceptance, releases, and breakaway wars.
- **Chat:** faction chat toggle and explicit chat mode switching.
- **Administration:** safezone claims (admin-only) and data backup/restore commands.
- **Dynmap:** optional claim marker sync when Dynmap is present.

## Faction UI
The mod includes a client-side faction screen with tabs for members, invites, permissions, relations, rules, and a live claim map.

### Open the UI
1. Join a world with the mod loaded.
2. Press the **G** key (default keybind: `Faction UI`) to open the screen.

### UI actions
- **Members:** view member list and roles, and leave the faction.
- **Invites:** send invites and accept pending invites.
- **Permissions:** cycle roles/permissions and grant or revoke permissions.
- **Relations:** view ally/war relations and configure relation permissions.
- **Rules:** add/remove faction rules.
- **Faction Map:** view claims, claim/unclaim selections, and sync with Dynmap.

## Commands (high level)
Use `/faction` followed by the subcommands below (some require specific permissions or operator/admin status):

- **Core:** `create`, `disband`, `join`, `leave`, `info`.
- **Membership:** `invite`, `kick`, `promote`, `demote`, `role list/add/remove`.
- **Permissions:** `perms list/add/remove`, `rank list/set/preset/presets`.
- **Settings:** `motd set/clear`, `description set/clear`, `color`, `banner set/clear`, `protection set`.
- **Claims:** `claim`, `claim auto`, `unclaim`, `overtake`, `claiminfo`, `logs`, `border`, `safezone claim/unclaim` (admin).
- **Homes:** `home`, `sethome`.
- **Relations:** `ally add/remove`, `war declare/end`, `vassal offer/accept/decline/release/break`.
- **Chat & map:** `chat`, `chat toggle`, `map`, `map sync`.
- **Data:** `data backup`, `data restore`.

## Dynmap integration
Dynmap markers are enabled automatically when the Dynmap API is available. Claims are mapped to area markers in the `Faction Claims` marker set, and `/faction map sync` can force a resync.

## Project metadata

Update `gradle.properties` to change the mod id, name, version, authors, and description. These values are expanded into `META-INF/neoforge.mods.toml` during the build.

## Requirements

- **Java 21** (NeoForge for Minecraft 1.21.1 targets Java 21).
- **Gradle** (or use the included Gradle wrapper scripts).

## Troubleshooting

### `EXCEPTION_ACCESS_VIOLATION` in `nvoglv64.dll` during `runClient`

If the client crashes during startup with a report pointing at `nvoglv64.dll`, the JVM is typically failing inside the NVIDIA OpenGL driver, not in mod code. Common fixes:

1. **Update or clean-reinstall your NVIDIA driver** (use the latest Game Ready/Studio driver).
2. **Disable overlays and GPU hooks** (GeForce Experience overlay, Discord overlay, Steam overlay, MSI Afterburner/RivaTuner, etc.).
3. **Reset any GPU overclocks** back to stock settings.
4. **Try a different JDK 21 build** (e.g., Temurin vs. Microsoft OpenJDK) if the crash persists.

If the crash persists after the above, please attach the `hs_err_pid*.log` from `runs/client/` when reporting the issue.

## Common tasks

> Run all commands from the repository root (the folder containing `build.gradle`).

### Run the development client

```bash
gradlew runClient
```

### Run the development server

```bash
gradlew runServer
```

### Run data generation

```bash
gradlew runData
```

### Build the mod JAR

```bash
gradlew build
```

## License

The project currently uses the license specified in `gradle.properties` (`mod_license`). Update it if you intend to distribute the mod under a different license.
