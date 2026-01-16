# MC Protector

MC Protector is a **NeoForge mod scaffold** (Minecraft 1.21.1) with project metadata, Gradle setup, and the default NeoForge resource files in place. It also documents the intended gameplay scope (factions, claims, and protection) so you can keep the product vision alongside the codebase.

## Features (planned)
- Basic faction creation and management commands.
- Chunk claiming with protection against block breaking, placement, redstone use, container access, and entity interaction.
- Optional Dynmap marker updates for claimed chunks (requires Dynmap).
- Role-based permissions for faction members.

## Commands (planned)
- `/faction create <name>`
- `/faction disband`
- `/faction claim`
- `/faction unclaim`
- `/faction overtake`
- `/faction info`
- `/faction ally add <faction>`
- `/faction ally remove <faction>`
- `/faction war declare <faction>`
- `/faction war end <faction>`

## Functions / UI (planned)
The mod includes a client-side faction UI with tabs for members, invites, permissions, relations, and claims.

### Open the UI
1. Join a world with the mod loaded.
2. Press the **G** key (default keybind: `Faction UI`) to open the screen.

### UI actions
- **Members:** view member list and roles.
- **Invites:** send faction invites by player name and view outgoing invites.
- **Permissions:** cycle roles/permissions and grant or revoke permissions.
- **Relations:** view active ally/war relations.
- **Claims:** view claimed chunks and claim/unclaim the current chunk.

The UI requests fresh data from the server when it opens and can be refreshed with the **Refresh** button.

## Dynmap integration (planned)
Dynmap markers are enabled automatically when the Dynmap API is available. Claims are mapped to area markers in the `Faction Claims` marker set.

## What’s in this repo

- **Gradle build setup** using `net.neoforged.gradle.userdev`.
- **Mod metadata** in `gradle.properties` (mod id, name, version, authors, description).
- **Base resources** (`META-INF/neoforge.mods.toml`, `pack.mcmeta`) that are populated from the Gradle properties.

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

## Next steps

This project does not yet include a main mod class or any gameplay features. To begin development:

1. Create your main mod class in `src/main/java` annotated with `@Mod` and using the same `mod_id` as in `gradle.properties`.
2. Add any registry setup, event handlers, and content in your preferred package structure.
3. Extend resources under `src/main/resources` as needed (assets, data, configs, etc.).

## License

The project currently uses the license specified in `gradle.properties` (`mod_license`). Update it if you intend to distribute the mod under a different license.
