# Persistent Power Simulation (0.6.2.6)

## Authority and energy

Snapshot schema 4 stores a certified exclusive consumer set, electrical footprints, source types/positions/ratings, adjacent external connection positions, fixed loads, rack mapping, nodes and routing. Older snapshots must be recaptured with the installation loaded; existing server faults and grid latches survive. Loaded transformers are required for real external energy. Only creative batteries may be represented by their saved rated capacity when unloaded.

A snapshot is virtual only when all recorded consumers are unloaded. The presence of a source or power cable does not make it live. Fully loaded verified installations use the normal scheduler; partial installations pause instead of running an inconsistent second simulation. The snapshot does not claim unrelated or shared electrical networks. A virtual electrical group replaces source-only loaded fragments in PowerGrid rather than adding another budget. Its demand is fixed equipment load plus the current task plan (including PSU efficiency). Both modes use one 20-tick external receipt window, confirmed before workload settlement; receipts are never saved or replayed. Routing is provisional for planning, then filtered by actual funded groups at settlement. Loss of source, invalid electrical topology or insufficient supply latches the affected group and disables its servers; unrelated groups remain independent. PSU overload remains local. The economy clock advances one simulated second per 20 ticks; no catch-up for server lag or shutdown.

## Chunk tickets

Forge 1.12.2 ForgeChunkManager NORMAL tickets, one per distinct chunk. Only transformer chunks and captured adjacent external tile chunks are requested, not the entire compute footprint. Limit energy.persistentSourceChunks defaults to 64 per dimension, range 0–1024. Forge's own chunkloading limits still apply. Tickets are reconciled against active jobs and untripped saved groups; stale tickets are released, restart restores authority from snapshots rather than ticket contents. A source's chunk may incidentally contain consumers; those chunks cannot be separated physically. If some consumers stay loaded while others unload, the installation pauses. Distant third-party generator and cable chunks must be maintained by the external mod or server administrator; missing power is a real failure, not simulated energy.

API reference: https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.12.2/net/minecraftforge/common/ForgeChunkManager.html

## Required live tests

1. Separate source and compute chunks with creative battery; unload all consumers, reload and compare task count and power.
2. HBM Extended HE, Forge Energy push and pull; use an independently loaded upstream supply. Confirm actual source depletion after player leaves; no income without receipts.
3. Two transformers, mixed creative and HE/FE, shared demand and tier limits; independent grids.
4. Load only some consumers: no virtual duplicate, no work from paused installation. Reload full footprint and resume.
5. Stop last task, trip grid, destroy source or cable, deny Forge tickets; verify release, saved latch and manual recovery.
6. Restart dedicated server: no wall-clock catch-up, no persisted receipts, rebuild tickets from snapshot, re-confirm energy. Test with and without HBM.
7. Multiple owners, overlapping/shared physical grids and network routing: no duplicate capacity or credits. Shared/ambiguous installations intentionally fail closed rather than virtualize unknown loads.
8. Low TPS: simulated time follows game ticks, not real seconds.

The pure tests and NBT round-trip do not replace these world/ticket/API checks.
