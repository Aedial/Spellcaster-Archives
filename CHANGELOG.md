# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog and this project adheres to Semantic Versioning.

- Keep a Changelog: https://keepachangelog.com/en/1.1.0/
- Semantic Versioning: https://semver.org/spec/v2.0.0.html

## [0.4.5] - 2025-11-17
### Fixed
- Fix Spell books from other mods using ebwizardry Spell books, resulting in broken books and not being able to extract them correctly.


## [0.4.4] - 2025-11-17
### Fixed
- Fix crash on dedicated servers.

## [0.4.2] - 2025-11-16
### Added
- Add auto-pickup feature for Spellcaster's Archives:
  - When enabled in config and present in player's inventory, the Archives will automatically eat any spell books that the player picks up.


## [0.3.1] - 2025-11-15
### Added
- Add possibility to use Identification Scrolls inside the Spellcaster's Archives GUI to identify undiscovered spells directly.
  - Inventory slot for them is at the bottom center of the left panel (configurable number of items).
  - Can be inserted like spells, but only manually extracted.
  - Right-click on an undiscovered spell in the left panel to identify it and consume one scroll.
  - Has a config option to disable this feature if desired.
- Add instructions about click controls to the spell details panel when a spell is selected.


## [0.2.4] - 2025-11-13
### Added
- Add dumping of all spells in inventory to the Spellcaster's Archives when holding right-click.

### Fixed
- Fix wrapping of spell descriptions for texts without spaces (e.g., Chinese).
- Fix continous spells formatting and cost in the spell details panel.
- Properly escape % characters in config localization entries to avoid formatting issues.
- Fix centering of element icons on book spines.


## [0.2.2] - 2025-11-12
### Added
- Add block rotation and valid rotation queries in `BlockSpellArchive`.
- Add dismantling support via wrench tools (e.g., Crescent Hammer).

### Fixed
- Fix null tile entity issue when moving the Spellcaster's Archives.
- Fix glyphs rendering when spell is undiscovered.
- Fix discoveryMode not being taken into account.
- Fix unlocalized text for elements.
- Fix blockState changes (rotation) wiping out the tile entity data.


## [0.2.1] - 2025-11-11
### Added
- Add crafting recipe for Spellcaster's Archive:
  - 4x Runestone (Electroblob's Wizardry)
  - 1x Ruined Spell Book (Electroblob's Wizardry)
  - 1x Grand Magic Crystal (Electroblob's Wizardry)
  - 1x Bookshelf (Minecraft)
  - 2x Wood Planks (any type) (Minecraft)

### Fixed
- Fix changelog script (script name) and bump version that was missed in last release.
- Fix NPE when breaking the Spellcaster's Archives next to AE2 Storage Bus (in some versions of AE2-UEL).

### Changed
- Move from a virtual slot system to true slotless:
  - Slot doesn't matter for insertion.
  - Slot still matters to some extent for extraction and reporting (stable order).
  - Remove stack limit on lookup and extraction.
  - Remove key deletion on slot exhaustion, to keep the record consistent across ticks.
- Optimize slot-aware logic (extraction, lookup) to avoid O(n) behavior.


## [0.2.0] - 2025-11-11
### Added
- Add localization entries for GUI, config, and command.
- Create Spellcaster's Archive block model with dynamic textures:
  - The sides and corners are randomly textured with Wizardry element runes and spell icons on each resource reload.
  - The number of spell types in the library affects the front texture (more types = more books shown, up to 14).
- Extensive config options for GUI layout:
  - New `ClientConfig` with automatic reload (file watcher + Forge config changed event) and percent-based float storage to avoid precision artifacts.
  - Add many GUI sizing and layout keys (window ratios, panel radii, grid padding, groove colors, spine parameters, navigation button sizing).
  - Color customization via hex `0xAARRGGBB` entries for left/right panels, groove wood, detail text, hover border.
  - Panel theming parameters (`PANEL_GRADIENT`, `OUTER_SHADOW_STRENGTH`) enable optional gradient/shadow effects.
  - Screen darken factor (`SCREEN_DARKEN_FACTOR`) while Archive GUI is open.
- Add theme system:
  - Theme picker entry in the Forge config GUI (`ClientConfigGuiFactory` + `ThemePickerEntry`).
  - Dedicate Theme Picker screen (`ThemePickerGui`) with left/right panel theme selection.
  - Theme Details screen (`ThemeDetailsGui`) showing swatches and per-color Apply / Apply All.
  - Built‑in themes: `bookshelf`, `parchment`, `dark` with preset fills, borders, groove colors, text colors, gradient/shadow suggestions.
- Dynamic texture generation:
  - `DynamicTextureFactory` generates cached book spine textures (curvature, vertical shading, tilt, noise, decorative bands, optional embedded element icon) invalidated by `GuiStyle.CONFIG_REVISION`.
- Expand capabilities for the archive tile:
  - Integrate `ISlotlessItemHandler` (enchlib) and improved slotless insertion/extraction logic.
  - Stable aggregated view via `IItemRepository.ItemRecord` for external automation.
- GUI style & spine rendering enhancements:
  - Configurable spine curvature, tilt, noise, bands, icon embedding, band thickness/gap/top space, brightness factors, vertical shading range.
  - Revision bump mechanism (`GuiStyle.CONFIG_REVISION`) to invalidate cached dynamic textures safely.
- Miscellaneous UX improvements:
  - Hover outlines, refined color clamping to ensure minimum alpha, tier/element color palettes preserved.
  - Theme apply writes values in-memory for immediate GUI reflection without restarting.

### Changed
- Refactor GUI constants into `GuiStyle` with reload logic and theming helpers (`computeThemePresetColors`, `computePanelThemeParams`).
- Config GUI now includes a non-editable theme picker row at top for quicker theming access.
- Spine textures now dynamically reflect configuration toggles instead of fixed assets.

### Fixed
- Ensure unknown or invalid color alpha values are clamped to avoid invisible UI elements.
- Graceful handling of missing spell icons (falls back to bookshelf side texture).

### Technical
- Percent float storage avoids binary float display artifacts in config file.
- Deterministic pseudo-random generation for spine texture noise and curvature for reproducible visuals per config revision.
- LinkedHashMap usage in tile entity preserves stable slot ordering for external handlers.
- Automatic dynamic texture cache invalidation tied to config revision.


## [0.1.0] - 2025-10-19

### Added
- Spellcaster's Archives block: bottomless, per-type storage for Electroblob's Wizardry spell books
- Forge IItemHandler capability with stable slot order and a virtual insertion slot
- In-game GUI: responsive layout (50% width, 75% height) with tier shelves and element-colored spines
- Right panel with spell details: icons, tier, element, costs/cooldown/charge, description; undiscovered handling
- Click-to-extract: left-click 1, Shift-click 16
- Custom networking (MessageExtractBook) for precise extraction by key
- Debug command `/archives fill <count|"max">` ray-tracing the targeted archive and reporting with colored chat
- Centralized GUI constants via `GuiStyle`
