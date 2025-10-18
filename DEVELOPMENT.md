# Development Guide

## Project layout

- `build.gradle` — ForgeGradle 2.3 for MC 1.12.2; CurseMaven wired for Wizardry
- `settings.gradle` — project name
- `src/main/java` — Java sources
  - `com.spellarchives.SpellArchives` — @Mod entrypoint
  - `com.spellarchives.block.BlockSpellArchive` — block class
  - `com.spellarchives.tile.TileSpellArchive` — tile entity implementing storage + IItemHandler
  - `com.spellarchives.registry.ModBlocks` — registry for block, ItemBlock, and tile entity
  - `com.spellarchives.registry.ClientModels` — client model registration
  - `com.spellarchives.client.ClientProxy` — client proxy
  - `com.spellarchives.gui.GuiSpellArchive` — main container GUI
  - `com.spellarchives.gui.GuiStyle` — centralized GUI constants (sizes, colors, spacing)
  - `com.spellarchives.container.ContainerSpellArchive` — server-side container (minimal)
  - `com.spellarchives.network.NetworkHandler` — SimpleNetworkWrapper channel
  - `com.spellarchives.network.MessageExtractBook` — server message to extract a book stack
  - `com.spellarchives.command.CommandArchives` — debug command `/archives`
- `src/main/resources` — assets and `mcmod.info`

## Dev environment

- JDK 8 is required for Forge 1.12.2 toolchain.

## Building & running

- Full build (WSL/Linux/macOS):

```bash
./gradlew -q clean build
```

- Dev client:

```bash
./gradlew -q runClient
```

### Tile entity state

- Counts keyed by `registryName|meta` (NBT ignored for identity)
- Change counter (`rev`) is incremented on content changes and synced to clients for GUI refresh
- Overflow handling: always voids overflow (no toggle); counts clamp to `Integer.MAX_VALUE`

### IItemHandler (automation)

- Stable order via `LinkedHashMap` so external handlers see consistent slot ordering
- `getSlots()` returns `counts.size() + 1` to expose one virtual insertion slot for new types
- `getStackInSlot(lastSlot)` is always empty (input-only behavior for inserters)
- `insertItem(slot, stack, sim)`:
  - If slot == last (virtual), accepts any valid spell book and creates/merges its key
  - If slot < last, only accepts the exact matching key for merging
  - On overflow, inserts up to cap and voids the remainder; returns EMPTY
- `extractItem(slot, amount, sim)` denies extraction from the virtual slot

### GUI

- `GuiSpellArchive` provides:
  - Dynamic sizing (50% width, 75% height)
  - Left panel: rows per tier, element-colored spines, tier tab header above each groove
  - Right panel: spell icon (if discovered), tier/element, properties (hidden if undiscovered), and description
  - Creative mode shows everything as discovered
  - Click-to-extract: left-click=1, shift-click=16 (a stack for Spell Books)
  - Rebuilds when tile `rev` changes (live updates while open)

- `GuiStyle` centralizes constants with detailed comments: margins, paddings, icon sizes, text gaps, groove colors, etc.

### Networking

- `NetworkHandler` creates the mod channel and registers `MessageExtractBook`
- `MessageExtractBook` carries block pos + stack key + amount; server validates TE and gives/drops items, then calls `detectAndSendChanges()` for immediate client update

### Commands

- `/archives fill <count|"max">` ray-traces the block the player is looking at and fills that archive with all spells
- Colored chat feedback via `Log.chat*` helpers reports usage issues and a success summary (types added, total books added/requested)

## Testing tips

- Use hoppers or AE2/RS/vanilla pipes to insert/extract Wizardry spell books; verify per-type aggregation and virtual insertion slot behavior.
- Break and place the block; verify contents persist via NBT.
- In GUI, click spines for extraction; confirm immediate inventory updates without closing the GUI.
- Use `/archives fill 1` and `/archives fill max` while looking at an archive to populate it for testing.

