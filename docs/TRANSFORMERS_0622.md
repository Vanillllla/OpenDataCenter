# Transformers — 0.6.2.2

| Tier | Default rated output |
|---|---:|
| T1 | 5000 W |
| T2 | 25000 W |
| T3 | 100000 W |

Config (energy): transformerT1Watts, transformerT2Watts, transformerT3Watts (1–100000000 W); heMilliJoulesPerUnit and feMilliJoulesPerUnit (default 50, provisional). 1 watt-tick at 20 TPS = 50 mJ. Conversion is ceiling-rounded at input and floor-rounded at output; the rated limit is always enforced. TransformerCore.ExternalInput describes a paid-debit boundary, but no external adapter is installed or connected to the scheduler in this version. Do not expose quoted external availability as funded capacity without implementing actual settlement before crediting work in 0.6.2.5.

Transformer blocks participate in physical grid discovery even with switch open. Their rated capacity sums for display only; funded capacity remains zero. Creative sources continue to work unchanged. The local breaker is saved in NBT and is editable only from the owner's/creative player's validated container within eight blocks. It does not disconnect other sources or switch the entire grid yet. A lit amber switch indicator means armed, not externally powered. No buffering, reverse conversion or generator.

Manual verification: creative inventory all tiers, place/rotate, power-cable connection, no power from transformer alone, battery + transformer still battery-only capacity, multiple nominal ratings sum, independent grids, switch ON/OFF, close/reopen, save/reload, permissions, dedicated server. Recipes and actual HE/FE input are out of scope.
