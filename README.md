# MC Protector

A NeoForge mod scaffold for faction creation, chunk claiming, and claim protection with optional Dynmap integration.

## Features
- Basic faction creation and management commands.
- Chunk claiming with protection against block breaking, placement, redstone use, container access, and entity interaction.
- Optional Dynmap marker updates for claimed chunks (requires Dynmap).
- Role-based permissions for faction members.

## Commands
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

## Dynmap Integration
Dynmap markers are enabled automatically when the Dynmap API is available. Claims are mapped to area markers in the `Faction Claims` marker set.

## Development
This project uses the NeoForge ModDev plugin. Update the NeoForge and Minecraft versions in `build.gradle` to match your target.
