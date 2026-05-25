# Phantasia

**Phantasia** is a Ponder-inspired multiblock visualization and teaching tool built for modern GregTech-based modpacks.

It is designed to help players understand complex multiblocks through interactive scenes, layered visualization, and scripted camera/control systems.

---

##  Features

###  Multiblock Visualization
- Full multiblock scene rendering
- Footprint and structure overlay view
- Layer-by-layer inspection
- Block group filtering (casings, coils, controllers, etc.)

###  Camera Controls
- Rotation
- Zooming
- Panning
- Focus targeting on structure parts
- Expand/collapse multiblock visualization

###  Dynamic Multiblock Support
- Coil variant switching (e.g. GTM coil tiers)
- Expandable multiblock definitions
- Layer selection and toggling
- Controller-focused views

###  Script System
Phantasia includes an in-game scripting system that allows scene authors to:
- Move and animate the camera
- Show/hide layers, blocks, or groups
- Highlight specific components
- Display instructional text overlays
- Control multiblock expansion stages
- Build guided tutorial sequences

An in-game editor is included so scenes can be created without coding knowledge.

---

##  Player Controls

### Command Access


Opens the Phantasia scene browser and script search interface.

### Contextual Access (Jade Integration)
When viewing a multiblock through Jade (or compatible tooltip providers), a Phantasia keybind will appear.

- Press the assigned keybind to open the relevant multiblock scene directly.

---

##  Design Philosophy

Phantasia is built as a **visual learning layer** for complex tech modpacks, especially those based around GregTech.

It aims to:
- Reduce reliance on external wikis
- Improve onboarding for multiblock systems
- Provide intuitive in-game explanations of machine structure and behavior
- Integrate directly into existing inspection workflows (e.g. Jade tooltips)

---

##  Compatibility

Phantasia is designed primarily for:
- GregTech Modern (GTM)-based modpacks
- Expert progression packs (e.g. TerraFirmaGreg-style environments)

It is compatible with:
- Jade (tooltip integration)
- GregTech Modern multiblocks.
- Json users.

---

##  Notes

- Phantasia does not alter progression systems or recipes.
- It is a visualization and educational layer only.
- Performance may vary in heavily modded environments with dense multiblock usage.

---

## 🛠️\ For Modpack Developers

Phantasia is intended to be embedded into expert modpacks as a teaching tool.

Recommended integration:
- Provide structured multiblock definitions
- Expose coil/multiblock variants via data-driven formats

---

##  Planned Features

- Scene bookmarking system
- Multiplayer-safe synchronized tutorials
- Advanced comparison mode (multiblock variants side-by-side)
- Quest system integration hooks
- Expanded scripting API
---


## 👤 Credits

Designed as a multiblock visualization and teaching system for advanced tech modpacks.
Inspired by Ponder-style tutorial systems and GregTech multiblock complexity.
