# 0.4.3-alpha — DataOS stage 1

- New scrollable Overview and Servers tabs.
- Network-local numeric IDs, group input, power toggle.
- CPU core reservations, per-type disk statistics, two-currency ledger.
- Other tabs deferred; not compiled.

# Changelog

## 0.4.1-alpha — Light DataOS

- Light Minecraft/Windows XP-inspired DataOS, server and switch panels.
- Beveled buttons, blue title bar, readable text, selected tabs and light input fields.
- World models, assets and server-side computation unchanged.

## 0.4.0-alpha — Compute & DataOS

- Независимые задачи и планирование CPU/GPU/RAM/диска/сети; эксклюзивные GPU, ручные и автоматические назначения.
- Встроенная DataOS с вкладками, группы, имена, индивидуальные GUI серверов и схема коммутации.
- Три игровые валюты, динамические сложность/курсы, два кошелька, банк, обучение и инференс ИИ.
- Динамическая энергия, перегрузка PSU, ручное восстановление, ПКМ/Ctrl+ПКМ.
- Экспериментальная виртуальная симуляция полностью выгруженных изолированных сетей.
- Модели, текстуры, рецепты и 3D-патч-корды 0.3.2 неизменны. Полная Forge-компиляция не подтверждена.


## 0.3.2-alpha — 3D Patch Cords

- Реальная трёхмерная геометрия патч-кордов с отдельными штекерами и непрерывными угловатыми изгибами.
- Точное совмещение гнёзд и штекеров T1/T2, возврат кабеля в межсерверный зазор, сохранение зазора до стекла.
- Кэширование мешей, прежняя пакетная отрисовка; отдельные тесты геометрии.
- Остальные механики и модели не изменены. Полный Forge runtime ещё не проверен.

## 0.3.1-alpha — Visual Update

- Новые текстуры и геометрия стоек, кабелей, реле, радиоантенны и спутниковой станции.
- Двухблочный терминал с анимированным синим экраном, обе половины используют один контроллер.
- Патч-корды и индивидуальные индикаторы портов в TESR; пакетная отрисовка через атлас.
- Удалены совпадающие грани рамок и торцы соединённых кабелей. Исправлена проверка вставки второго коммутатора.
- Игровые интерфейсы и основной планировщик сохранены. Обратная совместимость не поддерживается.

## 0.3.0-alpha — Hardware & Infrastructure

- Стойки 4U/9U с объединением двух рамок, универсальным центральным юнитом и безопасным разделением.
- Отдельный клиентский рендерер оборудования 1U/2U/4U и коммутаторов; индикаторы питания и активности, полупрозрачное стекло, вынесенная ручка.
- Новый Data Cable (жгут) и Power Cable (бронированный); коллизия центральной части и ответвлений.
- Power Relay: нормально замкнут, Redstone разрывает питание.
- Radio Network Station и Satellite Network Station с разными моделями и характеристиками.
- Коммутаторы теперь предметы 1U, два тира, ручное назначение порта с расходованием патч-кабеля.
- Рабочий стол — единый способ сборки и разборки. Корпуса содержат NBT-инвентарь, готовность влияет на иконку.
- Материнские платы определяют RAM/SATA/NVMe; размеры корпуса ограничивают GPU и PSU. Добавлены PSU, NIC, HDD/SSD/NVMe, новые платы и RAM.
- Базовый центральный терминал: выбор серверов, питание, запуск/остановка майнинга, страницы, обновление списка, WAN/LAN/баланс.
- Удалены прежние банковские и AI-задачи из активного движка. Майнинг CPU + GPU.
- Исправлены имена методов, вызвавшие ошибки с stable_39 в журнале сборки 0.2.0.
- Не поддерживается перенос старых миров. Полный Forge runtime ещё требуется проверить.

## 0.4.2-alpha — Component inventory and PCIe
- 31 original 32×32 component sprites, four motherboard models (T1–T3 + dual-socket T4), and all requested component tier counts. World block assets and block inventory models unchanged.
- Shared PCIe slots (1/2/4 by chassis, limited by motherboard): GPU, NIC or PCIe NVMe adapter. Advanced T3 and dual T4 have 1 Gbit/s onboard NIC. A single server patch lead uses the fastest NIC, without link aggregation. Second CPU optional on dual board; its compute and power are summed when installed. Values and recipes provisional.
- New components are registered, craftable, localized in EN/RU. Scheduler reserves physical GPU slot indices, never NIC or SSD slots; storage includes PCIe NVMe.
- Java core regression and asset validation passed; full Forge build and in-game GUI testing not performed (no Forge dependency cache/JDK 8). New world recommended as per alpha policy.
