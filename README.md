<div align="center">

# OpenDataCenter

**Постройте свой дата-центр в Minecraft · Build your own data center in Minecraft**

`Minecraft 1.12.2` · `Forge 14.23.5.2860` · `0.6.3.9-alpha`

[Русский](#русский) · [English](#english) · [Дорожная карта](#дорожная-карта) · [Roadmap](#roadmap)

<img src="patch-preview.png" alt="Серверные стойки с патч-кордами / Server racks with patch cords" width="520">

</div>

## Русский

**OpenDataCenter** — мод для Minecraft Forge, в котором вы собираете серверы из отдельных комплектующих, размещаете их в стойках и соединяете в сеть. Через терминал **DataOS** вы управляете вычислительными пулами, распределяете ресурсы и зарабатываете игровую валюту на контрактах.

### Что уже есть

- **Серверное оборудование.** Стойки 4U и 9U, сборка серверов на верстаке, выбор CPU, GPU, памяти и накопителей.
- **DataOS.** Обзор инфраструктуры, карточки серверов, журнал событий и сводка по ресурсам и кошельку.
- **Задачи и контракты.** Постоянные вычисления, контракты и периодические услуги. Для задачи можно выбрать пул и закрепить конкретные комплектующие за её резервом.
- **Сетевая инфраструктура.** LAN и оптика, антенны, сетевые терминалы и стоечные коммутаторы. На интерактивной карте можно менять uplink-подключения и смотреть маршруты WAN и ограничения пропускной способности.

### Версия 0.6.3.9-alpha

Финализация охлаждения и регрессионные проверки. Статус сборки: [GitHub Actions](https://github.com/Vanillllla/OpenDataCenter/actions). [Ручной чек-лист](docs/COOLING_MANUAL_CHECK.md); игровые проверки не выполнены.

### Версия 0.6.3.8-alpha

Свойства сервера и вкладка охлаждения DataOS, улучшенный стол сборки, модели и текстуры охлаждения. Интеграционные проверки — этап 0.6.3.9.

### Версия 0.6.3.7-alpha

Сохранение температур, тепловые снимки и виртуальные аварии, восстановление повреждений по UUID и модели детали, пропорциональное охлаждение при подтверждённой энергии, пассивное остывание частичных снимков. Нагрузочный тест: tools/test-core.sh или Gradle thermalBenchmark.

### Версия 0.6.3.6-alpha

Тепловая защита: аварийное отключение при достижении индивидуального предела, независимые 5% на каждую электронную деталь, NBT повреждений, ручной запуск после остывания на 10 °C ниже пределов и замены сломанных деталей. Настройка cooling.thermalFailurePercent. Пока только загруженные серверы.

### Версия 0.6.3.5-alpha

Жидкостное охлаждение: коллектор 0U устанавливается ПКМ предметом по задней стороне стойки, снимается Shift+ПКМ пустой рукой. Трубы подключаются к задним портам и охладителям T1–T3. Охлаждение распределяется пропорционально запросам при подтверждённом электропитании. Пока временные модели, без рецептов и сохранения температуры контура.

### Версия 0.6.3.4-alpha

Секундная температурная симуляция загруженных серверов: каждый CPU/GPU, внутренние детали и воздух корпуса. Учитывает подтверждённое питание, колебания, КПД PSU и охлаждение; `cooling.ambientTemperature` (25 °C). Пока без защиты, физического жидкостного контура и сохранения температур между загрузками.

### Версия 0.6.3.3-alpha

18 комплектующих охлаждения: вентиляторы, пассивные/плоские/башенные кулеры, водоблоки CPU/GPU. Слоты и совместимость, индивидуальные температурные пределы, расход мощности активного охлаждения, тепловой API. Температуры ещё не симулируются; временный интерфейс и иконки.

### Версия 0.6.3.2-alpha

Девять моделей корпусов, индивидуальные PCI-E/PSU/вентиляторы и совместимость плат и охлаждения. Старые корпуса сохраняют ID. Временно общие текстуры по высоте; слоты охлаждения зарезервированы, но ещё недоступны.

### Версия 0.6.3.1-alpha

Исправление PCI-E: последовательные слоты многослотовых карт блокируются визуально и сервером; после извлечения освобождаются. Старые пересекающиеся сборки нужно исправить на верстаке, предметы не перемещаются автоматически. План охлаждения: [docs/COOLING_IMPLEMENTATION.md](docs/COOLING_IMPLEMENTATION.md).

### Версия 0.6.2.10-alpha

Светодиоды трансформаторов и задних PDU: зелёный — подтверждённое питание, красный — авария энергосети, погашенный — отключение или отсутствие подтверждённого питания. Включённый трансформатор без полученной HE/FE остаётся погашенным. Только визуальная индикация.

### Версия 0.6.2.9-alpha

Случайные колебания суммарного потребления каждой физической энергосети раз в 20 тиков. Настройка `energy.powerFluctuationPercent` (по умолчанию 10, диапазон 0–100, 0 отключает). Учитываются в бюджете, аварийной защите, внешних запросах и DataOS; характеристики отдельных устройств остаются базовыми.

### Версия 0.6.2.8-alpha

Завершение этапа питания: открытые задние панели стоек, встроенный декоративный 0U PDU, объёмные силовые провода по установленным PSU и один провод на коммутатор. Поддержаны обычные и стеклянные 4U/9U стойки, сохранены дверцы и передние патч-корды. Восемь этапов 0.6.2 реализованы в исходниках; проверка в Minecraft и с внешними модами остаётся обязательной. См. `docs/POWER_FINALIZATION_0628.md`.

### Версия 0.6.2.7-alpha

Новая вкладка питания DataOS: отдельный просмотр физических энергосетей, подтверждённое питание, фактическое потребление, свободная подтверждённая мощность, номиналы, график за 10 игровых минут, категории нагрузки и источники с состояниями. Внешняя энергия показывается по фактически принятым квитанциям, а не как гарантированный свободный номинал. Неполные сети обозначаются как неподтверждённые; данные собираются на сервере. Игровая проверка интерфейса требуется.

### Версия 0.6.2.6-alpha

Виртуальная симуляция подтверждённых эксклюзивных энергосетей с HE/FE: сохраняются источники, топология и постоянная нагрузка, чанки трансформаторов и известных соседних точек подключения удерживаются ограниченными Forge-билетами. Виртуальная и живая нагрузка используют единый бюджет и фактические квитанции энергии. При частичной загрузке подтверждённого дата-центра симуляция приостанавливается до однозначного перехода; дефицит питания вызывает сохранённую аварию. Лимит `persistentSourceChunks` по умолчанию 64 на измерение. Проверка на сервере с реальными модами ещё нужна.

### Версия 0.6.2.5-alpha

Трансформаторы принимают HE через API HBM Extended и FE через стандартную capability (включая FE-совместимые Ender IO и Immersive Engineering). Энергия принимается по запросу на 20 тиков, в пределах номиналов, без обратной выдачи. Потребление и расчёт задач подтверждаются реальными поступлениями; дефицит вызывает аварию сети. Нативные интеграции требуют проверки в Minecraft. Выгруженные внешние сети пока не симулируются.

### Версия 0.6.2.4-alpha

Творческая батарея имеет интерфейс настройки мощности (1–100 000 000 Вт) и восстановления сети после аварии. Рубильники трансформаторов управляют физической энергосетью, дефицит мощности отключает её целиком; состояния сохраняются отдельно от LAN. Серверы после восстановления включаются вручную. Внешняя интеграция HE/FE — следующий этап.

### Версия 0.6.2.3-alpha

Потребление сервера учитывает индивидуальные КПД PSU и пропорциональное распределение нагрузки. CPU/GPU сохраняют линейный закон 10–100%, прочие компоненты и сетевые устройства — постоянные номиналы. В карточках отображается фактическая входная мощность. Перегрузка PSU отличается от дефицита внешнего питания; общая защита в 0.6.2.4.

### Версия 0.6.2.2-alpha

Три промышленных трансформатора T1/T2/T3 (5/25/100 кВт, настраиваемые), 3D-модели, светлый GUI, сохраняемый локальный рубильник и основа преобразования HE/FE. Номиналы суммируются на энергосети отдельно от реально доступной мощности. Внешние адаптеры ещё не подключены: фактическая выдача трансформаторов равна нулю до 0.6.2.5. Общий рубильник — 0.6.2.4. Блоки доступны в творческом инвентаре.

### Версия 0.6.2.1-alpha

Новое внутреннее ядро питания: независимые физические энергосети, общая мощность источников в ваттах, единый бюджет потребления на цикл симуляции. Творческая батарея работает с новым ядром; её числовая настройка теперь трактуется как Вт. КПД PSU, трансформаторы, общесетевая аварийная защита и новый интерфейс будут реализованы отдельно. Игровая проверка необходима.

### Версия 0.6.1.16-alpha

Вкладка «Сеть» удалена из DataOS; подробный мониторинг доступен в сетевом терминале. Стеклянные стойки 4U и 9U имеют анимированную дверцу: ПКМ по стеклу открывает или закрывает её; доступ к оборудованию возможен только после открытия. Отключённые от коммутатора серверы исключаются из пулов и ресурсов DataOS, сохраняя своё назначение до повторного подключения. Материнские платы со встроенным NIC показывают его скорость в подсказке; иконки коммутаторов в инвентаре уменьшены на 20%. При отдалении сетевой карты используются компактные карточки и чёткие линии, а обзор DataOS показывает статистику без кнопок. У радиоантенны, спутниковой тарелки и вышки больше нет собственных окон: их характеристики доступны на карте сетевого терминала. Без питания терминалы DataOS и Network показывают чёрный экран в пределах своего стандартного окна. Сервер проверяет действия на терминалах. В подсказках сетевых терминалов указана номинальная пропускная способность, а вкладка «Серверы» в DataOS показывает только серверы, подключённые к коммутатору. [Каталог коммутаторов 0.6.1.8](docs/SWITCH_CATALOGUE_0618.md) · [Журнал изменений](CHANGELOG.md)

### Сборка и запуск

Для сборки нужен **JDK 8**. В корне репозитория запустите подходящую команду.

| Система | Команда |
| --- | --- |
| Windows | `.\build.bat` |
| Linux / macOS | `bash build.sh` |

После успешной сборки JAR появится в `build/libs/`. Поместите его в папку `mods` профиля **Minecraft 1.12.2** с **Forge 14.23.5.2860**. Для первого запуска рекомендуется новый мир.

### Дорожная карта

![Иллюстрация дорожной карты OpenDataCenter от DataOS до версии 1.0.0](docs/roadmap.png)

| Версия | Этап | Статус |
| --- | --- | --- |
| **0.5.x** | DataOS, вычислительные пулы, задачи, журнал и сводка по кошельку | В исходниках |
| **0.6.1.x** | Физическая и логическая сеть, расчёт полосы, интерактивная карта, интеграция WAN с задачами и новые коммутаторы | В исходниках · игровая проверка впереди |
| **0.6.2** | Энергосети, трансформаторы, PSU, общая аварийная защита и вкладка «Питание», PDU | Реализовано в исходниках; игровые проверки впереди |
| **0.6.3–0.6.4** | Охлаждение, перегрев и троттлинг, доработка моделей и индикаторов | Запланировано |
| **0.7.x** | Игровые счета и организации, ноутбук с магазином, валютная биржа и баланс выживания | Запланировано |
| **0.8.x** | Публичный API и конфигурация, интеграции с энергетическими модами и OpenComputers | Запланировано |
| **0.9.x–1.0.0** | Стабилизация, многопользовательская игра, производительность, документация и выпуск | Запланировано |

Номера будущих версий предварительные. [Полная дорожная карта](OpenDataCenter-ROADMAP-FULL.md) · [Чек-лист задач](OpenDataCenter-ROADMAP-CHECKLIST.md)

[Архитектура](docs/ARCHITECTURE.md) · [Интеграция WAN](docs/NETWORK_INTEGRATION_0617.md) · [Сценарии проверки задач](docs/TASKS_MANUAL_CHECK.md) · [Чек-лист сборочного верстака](docs/WORKBENCH_MANUAL_CHECK.md)

---

## English

**OpenDataCenter** is a Minecraft Forge mod where you assemble servers from individual components, mount them in racks, and connect them to a network. Use the **DataOS** terminal to manage compute pools, allocate resources, and earn in-game currency by completing contracts.

### What's available

- **Server hardware.** Build servers at a workbench with your choice of CPUs, GPUs, memory, and drives, then mount them in 4U or 9U racks.
- **DataOS.** View infrastructure, inspect individual servers, browse the event log, and check resource usage and wallet balances.
- **Jobs and contracts.** Run continuous workloads, contracts, and recurring services. Choose a compute pool and reserve specific hardware for a job.
- **Networking.** Build with LAN and fiber links, antennas, network terminals, and rack switches. Use the interactive map to edit uplinks and inspect WAN routes and bandwidth bottlenecks.

### Version 0.6.3.9-alpha

Cooling finalization and regression checks. See [CI](https://github.com/Vanillllla/OpenDataCenter/actions) and [manual test checklist](docs/COOLING_MANUAL_CHECK.md); Minecraft playtests are pending.

### Version 0.6.3.8-alpha

Server properties and Cooling tab, improved assembly workbench, cooling assets and rack hoses. Integration checks pending in 0.6.3.9.

### Version 0.6.3.7-alpha

Thermal checkpoints, virtual heat and failure simulation, pending damage reconciliation, funded coolant allocation, passive cooling for partial snapshots. Benchmark: tools/test-core.sh or Gradle thermalBenchmark.

### Version 0.6.3.6-alpha

Thermal protection: individual thresholds, one independent 5% failure roll per electronic part, persistent damage NBT and manual restart after cooling 10 °C below all limits and replacing damaged parts. Config cooling.thermalFailurePercent. Loaded servers only for now.

### Version 0.6.3.5-alpha

Liquid cooling: right-click the rear of a rack with a 0U manifold, sneak-right-click empty-handed to remove it. Pipes connect rear contacts to T1–T3 chillers. Funded cooling is distributed proportionally to demand. Provisional assets; recipes and persistent thermal state follow later.

### Version 0.6.3.4-alpha

Per-second loaded-server thermal simulation, individual CPU/GPU, internal components and case air. Uses funded power, grid fluctuation, PSU losses and installed cooling. `cooling.ambientTemperature` defaults to 25 °C. Protection, physical liquid loops and persistence follow later.

### Version 0.6.3.3-alpha

18 cooling parts: case fans, passive/flat/tower CPU coolers and CPU/GPU waterblocks. Slot validation, individual temperature limits, active cooling power draw and thermal API. No temperature simulation yet; provisional UI and icons.

### Version 0.6.3.2-alpha

Nine chassis models with independent PCIe/PSU/fan capacities and board/cooling compatibility. Legacy IDs retained; shared placeholder textures and reserved cooling slots until later stages.

### Version 0.6.3.1-alpha

PCIe hardware fixes: contiguous physical slots, disabled followers, validated server-side insertion and automatic release. Legacy overlaps remain recoverable in the workbench without automatic relocation. See [cooling implementation plan](docs/COOLING_IMPLEMENTATION.md).

### Version 0.6.2.10-alpha

Transformer and rear PDU LEDs: green confirmed power, red grid trip, dark off or unconfirmed. Armed transformers without actual HE/FE receipts stay dark. Visual only.

### Version 0.6.2.9-alpha

Per-grid random total load variation every 20 ticks, configured by `energy.powerFluctuationPercent` (default 10, 0–100, 0 disables). The shared budget, protection, external receipts and DataOS use the same factor; individual hardware ratings remain nominal.

### Version 0.6.2.8-alpha

Power infrastructure finalization: open rack backs, decorative built-in 0U PDU, 3D power leads per installed PSU and one per switch. Standard/glass 4U and 9U racks retain front patch leads and animated doors. All eight 0.6.2 stages are implemented in source; live Minecraft and external-mod validation remain pending.

### Version 0.6.2.7-alpha

DataOS Power tab: independent physical grids, confirmed supply, actual load, confirmed spare power, installed ratings, ten simulated minutes of history, equipment categories and source states. External capacity is measured from real receipts, not assumed from nameplate ratings. Partial grids are marked unverified. Server-authoritative read model; in-game GUI tests pending.

### Version 0.6.2.6-alpha

Verified exclusive electrical grids can simulate unloaded consumers using HE/FE. Source and immediate connection chunks are retained with bounded Forge tickets; live and virtual loads share actual receipt accounting. Ambiguous partial loading pauses simulation; insufficient supply trips the saved grid. Default source chunk cap: 64 per dimension. Dedicated-server integration tests remain pending.

### Version 0.6.2.5-alpha

Transformers receive HE via the HBM Extended connector API and FE through the standard Forge capability (including compatible Ender IO and Immersive Engineering endpoints). A 20-tick demand window limits input to actual requested energy and rated power; no reverse output. Work requires confirmed receipts, and an unpaid window trips the grid. In-game integration verification remains pending; unloaded external grids are not simulated yet.

### Version 0.6.2.4-alpha

Creative batteries have a persistent 1–100,000,000 W output setting and a grid reset button. Transformer breakers control their physical electrical grid; insufficient power trips the whole grid. Latches persist independently of LAN. Servers require manual restart. External HE/FE integration follows.

### Version 0.6.2.3-alpha

Server grid input accounts for individual PSU efficiencies and proportional load sharing. CPU/GPU keep the 10–100% linear curve; other components and networking equipment use fixed ratings. Equipment details show actual grid input. PSU overload is distinct from insufficient grid supply; shared protection follows in 0.6.2.4.

### Version 0.6.2.2-alpha

Three industrial transformers T1/T2/T3 (configurable 5/25/100 kW), 3D models, light GUI, persistent local breaker and HE/FE conversion primitives. Rated capacity is tracked separately from funded supply; actual output is zero until external adapters are connected in 0.6.2.5. Shared breaker follows in 0.6.2.4. Creative inventory only for now.

### Version 0.6.2.1-alpha

Independent physical power grids, shared watt-based source capacity and centralized consumption budgets. Creative battery settings are now interpreted as watts. PSU efficiency, transformers, grid-wide protection and new monitoring are later milestones. In-game verification is pending.

### Version 0.6.1.16-alpha

The Network tab is removed from DataOS; detailed monitoring is available in the Network Terminal. Glass racks (4U and 9U) have an animated door: right-click the glass to open or close it; local hardware access requires the door to be fully open. Servers unplugged from their switch are excluded from pools and resource totals until reconnected, retaining their assigned pool. Motherboards with onboard NICs show their speed in tooltips; switch inventory icons are 20% smaller. Zoomed-out network maps use compact cards and crisp links; the DataOS overview shows read-only statistics. Radio antennas, satellite dishes and telecom towers no longer open their own GUIs; their status is available on the network terminal map. DataOS and Network terminals show a black screen within their normal window bounds when unpowered. The server validates terminal actions. Network terminal tooltips show rated bandwidth, and the DataOS Servers tab lists only servers connected to a switch. [0.6.1.8 switch catalogue](docs/SWITCH_CATALOGUE_0618.md) · [Changelog](CHANGELOG.md)

### Build and run

Building requires **JDK 8**. Run the command for your platform from the repository root.

| Platform | Command |
| --- | --- |
| Windows | `.\build.bat` |
| Linux / macOS | `bash build.sh` |

After a successful build, find the JAR in `build/libs/`. Place it in the `mods` folder of a **Minecraft 1.12.2** profile running **Forge 14.23.5.2860**. A new world is recommended for the first launch.

### Roadmap

| Version | Milestone | Status |
| --- | --- | --- |
| **0.5.x** | DataOS, compute pools, jobs, event log, and wallet overview | In source |
| **0.6.1.x** | Physical and logical networking, bandwidth allocation, interactive network map, WAN integration for jobs, and new switches | In source · in-game verification pending |
| **0.6.2** | Power networks, transformers, PSU, grid-wide protection, Power tab and PDU | Implemented in source; live validation pending |
| **0.6.3–0.6.4** | Cooling, overheating and throttling, and visual refinements | Planned |
| **0.7.x** | In-game accounts and organizations, a laptop marketplace, currency exchange, and survival balancing | Planned |
| **0.8.x** | Public API and configuration, energy-mod integrations, and OpenComputers support | Planned |
| **0.9.x–1.0.0** | Stabilization, multiplayer, performance, documentation, and release | Planned |

Future version numbers are provisional. [Illustrated roadmap](docs/roadmap.png) · [Full roadmap in Russian](OpenDataCenter-ROADMAP-FULL.md) · [Development checklist in Russian](OpenDataCenter-ROADMAP-CHECKLIST.md)

[Architecture](docs/ARCHITECTURE.md) · [WAN integration](docs/NETWORK_INTEGRATION_0617.md) · [Job verification scenarios](docs/TASKS_MANUAL_CHECK.md) · [Workbench verification checklist](docs/WORKBENCH_MANUAL_CHECK.md)
