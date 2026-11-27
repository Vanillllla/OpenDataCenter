# 0.6.1.6 Interactive Network Editor

- Drag cards in normal mode; toggle snap; Auto layout clears saved coordinates of visible devices. Coordinates are stored by device UUID in dimension-local PhysicalNetwork NBT. Shared antenna coordinates are also shared across views. Automatic arrangement is deterministic; manual positions may overlap.
- Connect mode: choose LAN tier, click terminal and switch (either order). Choose a line to inspect, remove or upgrade a manual uplink. Fiber and patch links remain read-only. Existing list editor remains available for separated links.
- Server checks window, terminal permission, verified physical domain, endpoints, ownership and inventory; existing atomic cable operations are reused. Snapshot polling (1 second) synchronizes clients; camera is unchanged.
- Routes mode on a server highlights complete available WAN paths; properties show actual calculated served routes (up to 32 distinct paths; total count reported). Load color thresholds: idle, normal, >=80%, saturated, unavailable. Nominal labels remain cable speeds.
- No changes to assets or task admission. No Forge build or in-game interaction test in this environment.
