# Spellcaster's Archives

A Minecraft 1.12.2 Forge mod that adds a "Spell Archive" block: a bottomless, capability-aware storage for Electroblob's Wizardry spell books. It integrates with hoppers/pipes and provides an in-game GUI for browsing and extracting books.

## Features

- Bottomless per-type storage (counts up to Integer.MAX_VALUE; overflow is always voided)
- Distinguishes books by item + metadata (NBT ignored for identity)
- Forge capability (IItemHandler) for automation with stable slot order
- NBT-preserving drops (breaking/placing keeps contents)
- In-game GUI:
	- Scales to 50% screen width and 75% height
	- Left panel: element-colored spines arranged in shelves by tier
		- Tier label shown as a tab above each shelf
		- Hover outline, vertical pagination by visible rows
	- Right panel: details for the hovered book
		- Spell name/description (or glyph if undiscovered)
		- Element icon/name, cost/cooldown/charge
		- Spell icon (if discovered)
	- Extraction: click a spine to receive books
		- Left-click: 1 book; Shift-click: a stack (16)

## Requirements

- Minecraft 1.12.2
- Forge 1.12.2 (14.23.5.2859 recommended)
- Electroblob's Wizardry (1.12.2)

## Installation

- Place the built jar in your `mods` folder alongside Electroblob's Wizardry.
- Launch with a compatible Forge 1.12.2 installation.

## Usage

- Place the Spell Archive block and insert Wizardry spell books (right-click or via automation).
- Open the GUI to browse shelves grouped by tier; shelves use the element color as spine fill.
- Hover a spine to see details on the right; click to extract (Shift for 16).
- Breaking the block drops an item that carries all stored contents via NBT; placing it restores the inventory.

### Debug command

- `/archives fill <count|"max">`
	- Fills the Spell Archive block you are looking at (ray traced up to ~6 blocks) with all spells.
	- Uses colored chat feedback to report success/errors and totals added.

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
