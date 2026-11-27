# Changelog

## 0.2.1-alpha — Server Hardware

- Real per-server ItemStack inventory persisted inside the server item.
- Basic/pro motherboards, 4/16 GB RAM, 128 GB HDD, 512 GB SSD; six new recipes and sprites.
- Mandatory board, CPU, RAM and disk; GPU optional. Chassis/board slot limits and boot diagnostics.
- Assembly station supports empty, partially assembled and complete servers. Individual hardware GUI in rack, removal and upgrades, shift-click transfer, exclusive maintenance lock and task pause.
- Legacy CPU/GPU conversion without free components; old assembler slot indices retained.
- 30,000 randomized hardware configurations and new regression tests. Forge compilation/gameplay still unverified.

## 0.2.0-alpha

### Added
- Three controller-owned workloads: mining (GPU), bank (CPU), AI (eligible GPU VRAM).
- Per-physical-server assignment to a controller task and persistent controller UUIDs.
- Powered network station (WAN 128 Mb/s) and switch (LAN 64 Mb/s), recipes, power-state models, GUIs.
- Shared per-owner data-domain budget, fair allocation, energy-constrained work, one server-side scheduling pass per 20 ticks.
- Per-server active/off rack panels and two-frame active LED animation.
- Russian/English task manager and rack assignment GUI; separate marketplace GUI.
- Configurable bandwidth and bank/AI payouts; offline Java regression tests; asset validator; Linux build script.

### Changed
- Redrawn all assets in 16×16 vanilla-inspired gray palette and simplified cuboid geometry.
- Mining no longer uses CPU hashpower; bank can use CPU-only machines; AI requires >=8 GB VRAM per eligible GPU.
- Reduced internal buffers to 12 000 FE per rack and 480 FE per powered network/management device for more responsive outages.
- Old rack-level autonomous mining removed: tasks need reachable, powered controller and network resources.
- Preserved prior registry names and assembled-server NBT schema. Existing worlds need new assignments and a backup before upgrade.

### Verification
- Independent core compiled and tested with Java 8 target; 12 000 random resource plans passed.
- Resource checks and Java 8 syntax parsing passed.
- Full Forge compilation/client/server runtime blocked by environment; source alpha, no compiled mod JAR.

## 0.1.0-alpha
- Initial rack, assembly station, CPUs/GPUs, DataOS, internal creative power/data cables, mining and configurable four-offer market.
