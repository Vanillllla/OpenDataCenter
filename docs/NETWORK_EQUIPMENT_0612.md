# Network equipment — 0.6.1.2-alpha

Central `NetworkEquipment` catalogue (decimal Mb/s and FE/t):

| Device | Nominal Mb/s | Idle FE/t |
|---|---:|---:|
| Radio | 500 | 12 |
| Satellite | 2000 | 40 |
| Tower | 10000 | 80 |
| Terminal T1 | 1000 | 5 |
| Terminal T2 | 10000 | 15 |
| Terminal T3 | 100000 | 40 |

Three terminal blocks were chosen over module upgrades for this stage. New blocks have creative entries; no crafting recipes added for new equipment. Existing recipes unchanged. Energy uses existing Powered/Buffer and Forge Energy; tier ratings and consumption are provisional.

Tower: three actual block states, facing and part in metadata. Place with two free upper positions. Only the base has a Station tile/UUID, accepts optical/power cables, and drops the item. Upper parts forward interaction to base. Breaking a section removes the remaining structure. Offline index ignores upper parts. Creative harvest and survival removal have explicit paths. Conservative collision boxes. In-game placement and destruction not verified.

The information GUI uses existing server container property synchronization (32-bit values split into two shorts), with mouse-wheel scrolling. Direct optical peers and LAN-discovered devices are distinguished; LAN discovery does not imply a logical uplink. Peer status distinguishes verified path/power and unverified paths. The physical index does not duplicate the source UUID across LAN domains. The old loaded-block direct-adjacency WAN remains transitional and uses the new nominal station ratings; optical WAN is NOT allocated to tasks until Stage 4/7. No port count limits added. No changes to rack, server, task formulas or reservations.

Generated models use 16x16 textures; tower uses actual separate model sections and a compact inventory model. Reproduce with `python tools/network_models.py` (Pillow). Asset validation and pure-Java core tests passed. No Forge/JDK 8 build or game test performed.
