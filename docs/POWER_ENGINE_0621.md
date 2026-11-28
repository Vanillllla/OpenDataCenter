# 0.6.2.1 — Power Engine

PowerGrid discovers loaded physical components using power cable and closed relay, not LAN. Rack upper halves and DataOS upper blocks alias their base; sources and consumers are unique. Adjacent consumers alone do not form a connection. A shared input can connect cable branches. Oversized components fail closed. Network invalidation handles block, relay and chunk changes.

PowerEngine budgets one 20-tick simulation cycle, using integer watt-ticks (1/20 J). Preview reservations share the same budget per domain and are non-mutating. The scheduler first accounts for always-on devices and rack switches, then plans server consumption, excluding unfunded servers before crediting work. No FE input capability remains on consuming devices. Creative batteries sum their configured watts; numerical defaults are retained for this stage. No conversion, persistent shared trip or storage blocks yet.

Virtual execution remains conservative: only exclusively creative-powered, fully unloaded verified grids; per-rack last verified server budget, old FE snapshots discarded. Full external and partial-chunk support belongs to 0.6.2.6.

Manual checks: two sources one cable, two separate power grids on one LAN, shared grid across LANs, looped cable, relay split/reconnect, 9U upper contact, unpowered terminal, insufficient power, save/reload and chunk unload. Check that total actual consumption never exceeds capacity and unsupported work earns no payout. Test in a new world first.
