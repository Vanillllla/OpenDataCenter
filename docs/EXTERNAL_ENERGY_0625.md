# External energy — 0.6.2.5

## Verified upstream contracts

- HBM Extended official repository, Custom-1.12.2, source commit a9afe4900bdae1a522b55f6168b38320329ed7c7, RefStrings version 3.0.3, mod id hbm: https://github.com/Alcatergit/Hbm-s-Nuclear-Tech-GIT/tree/a9afe4900bdae1a522b55f6168b38320329ed7c7/src/main/java/api/hbm/energy
- IEnergyConnector.transferPower(long) returns the *unaccepted remainder*. getPower/getMaxPower and getTransferWeight describe demand. ILoadedTile.isLoaded is used for subscriber cleanup. IEnergyConductor.getPowerNet, IPowerNet.subscribe/isSubscribed/isValid, ForgeDirection.getOrientation(EnumFacing) and canConnect are used for subscription. No unsupported extraction method or direct mutation of HBM storage. See upstream PowerNet and TileEntityConverterHeRf.
- Forge 1.12.2 IEnergyStorage/CapabilityEnergy: https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.12.2/net/minecraftforge/energy/IEnergyStorage.html
- Ender IO documents FE as its µI API, including equivalence to Immersive Flux: https://github.com/SleepyTrousers/EnderIO-1.5-1.12/wiki/Energy-%28%C2%B5I%29 . FE-compatible endpoints need no additional adapters. Legacy RF-only endpoints are not supported directly.

## Accounting

The scheduler reserves against rated capacity provisionally, then opens an exact 20-tick receipt window for the demand not covered by creative sources. All transformers in the physical grid share the outstanding millijoules; each has its own rating cap. HE and FE offers decrement the same remaining demand. The last indivisible external unit may be rounded up, and any excess is lost. A simulated offer never alters receipts. FE input is exposed on all faces and never exports; extract-only neighboring FE storage is polled only while demand remains. HE uses native push subscription. Both input types can contribute, without counting one receipt twice.

At the next cycle, a matching live physical grid closes the window and verifies its receipt. An unpaid requested window trips the whole grid. A new/increased request without previous sufficient paid energy is not credited and equipment is not presented as powered at settlement; it awaits a fresh window. Receipts are single-use, in memory only; they are not stored across restart, topology changes or unloading. This conservative one-cycle startup/ramp delay can discard unused paid energy when demand changes; no energy is created. Full external-grid unloaded simulation is deferred to 0.6.2.6. The GUI distinguishes installed rating, last received/funded watts and HE/FE input units.

Configuration: heMilliJoulesPerUnit and feMilliJoulesPerUnit (default 50 each, provisional; 1 W at 20 TPS is 1 J per 20-tick cycle). Default tier output ratings 5/25/100 kW remain unchanged. Native HBM API declarations in src/compileOnlyHbm are minimal compile-time signatures only, excluded from packaged JAR. Forge Optional.Interface removes the connector interface without HBM; the HBM-only bridge runs only when hbm is loaded. Use the packaged mod for runtime tests, not the compile-only ABI classes in development output.

Manual validation still required: Forge only, dedicated server without HBM, actual HBM Extended 3.0.3 or installed release, HE cable and direct battery, FE push/pull, Ender IO conduit, IE connector, mixed source, tier cap, no load, insufficient power, breaker reset, removal and reconnection. No real game tests were performed.
