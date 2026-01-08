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
This project uses the ForgeGradle plugin. Update the Forge and Minecraft versions in `build.gradle` to match your target.

## Singleplayer Testing (Minecraft 1.20.1)
The development environment targets Minecraft 1.20.1 and requires Java 17. See the steps below to install Java 17 and run the mod in a singleplayer test world.

### 1) Install Java 17
You can install Java 17 (Temurin/OpenJDK) alongside newer Java versions. Make sure you know the install path.

Example default Windows install path:
```
C:\Program Files\Eclipse Adoptium\jdk-17.x.x
```

### 2) Open a terminal
Use a system terminal to run the commands below (not the Minecraft chat):
- **Windows:** PowerShell or Command Prompt
- **macOS/Linux:** Terminal

### 3) Point the terminal to Java 17 (Windows)
If your system default is a newer Java version, set Java 17 for the current terminal session.

Command Prompt:
```
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.x.x
set PATH=%JAVA_HOME%\bin;%PATH%
```

PowerShell:
```
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.x.x"
$env:PATH = "$env:JAVA_HOME\\bin;$env:PATH"
```

### 4) Verify the Java version
```
java -version
```
Confirm the output shows Java 17.

### 5) Run the development client
From the repository root (the folder containing `build.gradle`), use Gradle 8.6 (newer 8.x releases can fail with a `versionParser` error when applying ForgeGradle):
```
gradle runClient
```
This starts the Forge development client with the mod loaded.

### 6) Create a singleplayer world and test
In Minecraft:
1. Choose **Singleplayer**
2. Create or open a world
3. Test commands:
   - `/faction create <name>`
   - `/faction claim`
   - `/faction info`
   - `/faction ally add <faction>`
   - `/faction war declare <faction>`

If you see protection behavior working as expected, the mod is running correctly.
