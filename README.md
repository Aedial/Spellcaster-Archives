# Spellcaster's Archives

A Minecraft 1.12.2 Forge mod that adds a "Spell Archive" block: a bottomless, capability-aware storage for Electroblob's Wizardry spell books. It integrates with hoppers/pipes and provides an in-game GUI for browsing and extracting books.

## Features

- Bottomless per-type storage (up to Max Int per spell; overflow is always voided)
- Item insertion/extraction for automation with stable slot order
- Spells can be manually inserted via right-clicking with a spell book
- Content is preserved when broken or when spell mods are removed/added (removed spells are lost, but others remain unaffected)
- In-game GUI:
    - Configurable layout with paging (configuration through mod config, with config file hot-reloading)
    - Left panel: element-colored spines arranged in shelves by tier
    - Right panel: details for the hovered book
        - Spell name/description (or glyph if undiscovered)
        - Element icon/name, cost/cooldown/charge (? if undiscovered)
        - Spell icon (if discovered)
    - Extraction: left click a spine to retrieve books (1 on click, 16 on shift-click)

## Requirements

- Minecraft 1.12.2
- Forge 1.12.2
- Electroblob's Wizardry (1.12.2)

## Installation

- Place the built jar in your `mods` folder alongside Electroblob's Wizardry.
- Launch with a compatible Forge 1.12.2 installation.

### Debug command

- `/archives fill <count|"max"> [typesCount]`
    - Fills the Spell Archive block you are looking at (ray traced up to ~6 blocks) with all spells.
    - Uses colored chat feedback to report success/errors and totals added.
    - `count`: Number of each spell book to add (or "max" for Max Int).
    - `typesCount`: Number of different spell types to add (optional; defaults to all).

## Building

This project uses ForgeGradle 2.3 and Gradle. You can build from Windows, WSL, Linux, or macOS.

- Build (Linux/macOS):

```bash
./gradlew clean build
```

- Build (Windows PowerShell):

```powershell
./gradlew.bat clean build
```

## License

This project is licensed under the MIT License. See `LICENSE`.
