# 0.6.2.8 — Rack PDU & Finalization

## Rendering contract

Rear opening: remove exactly the two centre rear wall surfaces from all six world rack role models and both inventory models. Keep frames, roof, side panels, front door, handles and all existing block states. World PDU rendered once by master TESR; inventory model includes a simplified four-socket rail. Rack-local rear is +Z, module ends at .90. PDU is inside x=.825–.895, z=.925–.985. Each U has a socket, and each installed physical PSU slot maps to one lane. A 4U case can have zero through four leads, with sparse slots respected. Every switch has one lead regardless of tier/power state. Leads are swept meshes with separated Y lanes, two connectors and rear panel texture; power state does not change physical cabling. Same local transform as front modules. No extra tile entity, inventory, collision, packet or power mechanic. New geometry is cached and submitted in the existing opaque batch. Front patch leads and door rendering are unchanged.

## Regression scope

The existing CI runs tools/test-core.sh (compute, server specification, patch cords, task/economy, pool/resource bindings, DataOS, event journal, network topology and bandwidth, energy budget, PSU efficiency, transformer conversion, grid protection, external receipt accounting, persistent policy, monitoring), tools/validate_assets.py (models, PNGs, localization, recipes and geometric bounds) and Forge build/check (including NBT and packet tests). Added RackPowerMeshTest and rear-opening/PDU asset assertions. These are source-level and automated checks, not live-game validation. Build status is determined by GitHub Actions.

## Manual checks still open

- Standard and glass 4U/9U racks: empty see-through rear, front unchanged, doors open/close; all four rotations; no z-fighting or visible crossing at near/far viewing distances.
- 1U/2U/4U chassis with each PSU population and sparse slots, installed incomplete server, remove/reinsert/modify hardware; every switch tier; rear leads persist when power off; front patch cords unchanged.
- Merge two 4U racks, separate either half, remove devices, restart world and rejoin multiplayer; no duplicate PDU or cables on slave block.
- Creative battery GUI and save, transformer HE/FE with actual HBM Extended, Ender IO and IE; rate and tier limits, shared physical grids and separate LANs, local PSU fault vs whole-grid trip, manual restore, no unpowered earnings.
- Virtual exclusive grids: source chunk tickets, unload/reload, partial pause, lost external power, ticket denial, restart, no double accounting. External upstream chunks need their own loading.
- DataOS Power: independent grid selection, chart history, source pagination, categories, off/tripped status, GUI scaling and actual client/server synchronization. Unpowered DataOS still black.
- Dedicated Forge server with/without HBM; actual startup and runtime classloading, world migration, multiplayer permissions and load.

No gameplay validation is claimed here. Source-level completion does not certify real integration. Cooling and thermal mechanics are outside 0.6.2.
