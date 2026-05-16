# ✦ VoidRift

> Гибкая система событий для Minecraft серверов. Работает автономно или как аддон к SkyBound.

![Version](https://img.shields.io/badge/version-1.0.0-purple)
![API](https://img.shields.io/badge/Spigot-1.16.5+-green)
![Java](https://img.shields.io/badge/Java-8+-orange)

---

## 📋 Что это?

VoidRift — плагин событий (ивентов) с порталами, волнами мобов, боссами, наградами и гибкой системой целей. Игроки входят через порталы, выполняют задания, получают лут.

**Ключевые фичи:**
- 🌀 Порталы (статические, динамические, промежуточные двусторонние)
- ⚔️ Волны мобов с боссами (EliteMobs, MythicMobs, ванильные)
- 🎯 30+ типов целей (убить, собрать, выжить, дойти до волны...)
- 🎁 Лут-сундуки (рандомные + боссовые)
- 📊 Sidebar, ActionBar, BossBar с прогрессом
- 🏆 Лидерборд, статистика, достижения
- 🔧 Визарды для настройки через игру (без ручного редактирования конфигов)

---

## 🚀 Установка

1. Положи `VoidRift.jar` в папку `plugins/`
2. Положи `SopLib.jar` в папку `plugins/` (обязательно)
3. Перезапусти сервер
4. Настрой через визарды или конфиги

---

## 📦 Зависимости

| Плагин | Обязательный? | Зачем |
|--------|:---:|--------|
| **SopLib** | ✅ | Мультиверсионность, утилиты |
| EliteMobs | ❌ | Кастомные боссы |
| FreeMinecraftModels | ❌ | 3D модели мобов |
| MythicMobs | ❌ | Альтернатива EliteMobs |
| SkyBound | ❌ | XP острова, банк |
| Vault | ❌ | Экономика |
| PlaceholderAPI | ❌ | Плейсхолдеры везде |
| FlexAchievements | ❌ | Достижения |
| SopItemsCreator | ❌ | Кастомные предметы |
| SopCustomBlocks | ❌ | Кастомные блоки |
| Citizens | ❌ | NPC |

> Все опциональные плагины подключаются автоматически если найдены на сервере.

---

## 🎮 Команды

### Игроки
| Команда | Описание |
|---------|----------|
| `/event` | Открыть GUI меню ивентов |
| `/event top [ивент]` | Лидерборд |

### Админы
| Команда | Описание |
|---------|----------|
| `/riftadmin setup <ивент>` | Визард порталов |
| `/riftadmin setupzone <зона>` | Визард зоны |
| `/riftadmin createevent <id>` | Визард создания ивента |
| `/riftadmin startnow <ивент>` | Запустить ивент |
| `/riftadmin stopnow <ивент>` | Остановить ивент |
| `/riftadmin start <ивент>` | Запустить с отсчётом |
| `/riftadmin reload` | Перезагрузить конфиги |
| `/riftadmin info [ивент]` | Информация |

---

## ⚙️ Конфигурация

### config.yml

```yaml
language: ru
max-active-events: 3

auto-start:
  enabled: true

portal:
  preview-seconds: 10

sounds:
  event-start: { sound: BLOCK_PORTAL_TRIGGER, volume: 0.7, pitch: 1.5 }
  wave-clear: { sound: ENTITY_PLAYER_LEVELUP, volume: 1.0, pitch: 1.5 }
  boss-spawn: { sound: ENTITY_WITHER_SPAWN, volume: 1.0, pitch: 0.8 }

modifiers:
  enabled: true
  max-count: 2

display:
  actionbar: true    # Прогресс в ActionBar
  sidebar: true      # Панель справа
  bossbar: true      # Полоска сверху

rewards:
  cooldown-seconds: 3600  # Кулдаун наград (1 час)

scaling:
  enabled: true
  per-player-multiplier: 0.5  # +50% мобов за каждого доп. игрока

loot-chests:
  random:
    enabled: true
    count: 3
    particle: VILLAGER_HAPPY
  boss:
    enabled: true
    mode: CHEST  # CHEST / DROP / KILLER / TOP_DAMAGE
    particle: FLAME
```

### events.yml

```yaml
events:
  rift_assault:
    display-name: "&5✦ Штурм Разлома"
    description: "Выживи против волн мобов!"
    type: WAVE_SURVIVAL          # WAVE_SURVIVAL, BOSS_FIGHT, RESOURCE_RACE, CUSTOM
    zone: "my_zone"
    duration-seconds: 300
    interval-seconds: 1800
    min-players: 1
    max-players: 10
    complete-on: ALL             # ALL = все цели, ANY = хотя бы одна
    objectives:
      - type: KILL_MOBS
        amount: 15
      - type: KILL_BOSS
        target: rift_boss.yml
        amount: 1
      - type: SURVIVE_TIME
        amount: 120
    loot-table:
      - item: DIAMOND
        amount: 2
        chance: 0.3
      - item: GOLD_INGOT
        amount: 5
        chance: 0.6
    rewards:
      money: 5000
      island-xp: 100
      flex-achievement: "rift_complete"
      commands:
        - "give {player} diamond 1"
```

### zones.yml

```yaml
zones:
  my_zone:
    world: world
    pos1: { x: 0, y: 60, z: 0 }
    pos2: { x: 50, y: 100, z: 50 }
    max-mobs: 15
    spawn-points:
      sp1: { x: 10, y: 64, z: 10 }
      sp2: { x: 40, y: 64, z: 40 }
    mob-pools:
      - mob-id: rift_soldier.yml
        mob-type: ELITEMOBS       # VANILLA, ELITEMOBS, MYTHICMOBS
        weight: 5
        wave: 0                   # 0 = все волны
      - mob-id: SKELETON
        mob-type: VANILLA
        model: my_model           # FMM модель (опционально)
        weight: 3
        wave: 0
    bonus-waves:
      5:
        - mob-id: rift_boss.yml
          mob-type: ELITEMOBS
          weight: 1
```

---

## 🎯 Типы целей (Objectives)

| Тип | Описание | target |
|-----|----------|--------|
| `KILL_MOBS` | Убить N мобов | тип/файл босса (пусто = любой) |
| `KILL_BOSS` | Убить конкретного босса | файл.yml |
| `KILL_ELITE` | Убить любого элитного моба | — |
| `KILL_STREAK` | Убить N без смерти | — |
| `LAST_HIT_BOSS` | Добить босса | файл.yml |
| `DEAL_DAMAGE` | Нанести N урона | — |
| `TAKE_DAMAGE` | Получить N урона | — |
| `NO_DEATH` | Не умереть | amount=1 |
| `REACH_WAVE` | Дожить до волны N | — |
| `SURVIVE_TIME` | Выжить N секунд | — |
| `ALL_MOBS_DEAD` | Убить всех мобов | — |
| `CLEAR_WAVES` | Очистить N волн | — |
| `COLLECT_ITEM` | Собрать предмет | материал |
| `COLLECT_FROM_MOB` | Дроп с моба | материал |
| `COLLECT_FROM_CHEST` | Из сундука | материал |
| `MINE_BLOCK` | Сломать N блоков | тип блока |
| `PLACE_BLOCK` | Поставить N блоков | тип блока |
| `SCORE_POINTS` | Набрать N очков | — |
| `ENTER_ZONE` | Войти в зону | id зоны |
| `USE_PORTAL` | Использовать портал | id портала |
| `TRAVEL_DISTANCE` | Пройти N блоков | — |
| `EAT_FOOD` | Съесть N еды | тип еды |
| `CRAFT_ITEM` | Скрафтить | предмет |
| `USE_ITEM` | Использовать предмет | предмет |
| `COMPLETE_BEFORE` | Успеть за N секунд | — |
| `SPEED_KILL` | Убить босса за N сек | — |
| `PLAYERS_IN_EVENT` | N игроков в ивенте | — |
| `CUSTOM` | Кастомное через API | id |

---

## 🔌 Интеграции

### EliteMobs
Боссы спавнятся автоматически если указан `mob-type: ELITEMOBS` в zones.yml.
Файлы боссов кладутся в `plugins/EliteMobs/custombosses/`.

```yaml
mob-pools:
  - mob-id: my_boss.yml
    mob-type: ELITEMOBS
    weight: 1
    wave: 3
```

### FreeMinecraftModels
Модели применяются к ванильным мобам через поле `model`:

```yaml
mob-pools:
  - mob-id: ZOMBIE
    mob-type: VANILLA
    model: my_custom_model    # имя .bbmodel без расширения
    weight: 5
```

### MythicMobs
Аналогично EliteMobs:

```yaml
mob-pools:
  - mob-id: MyMythicMob
    mob-type: MYTHICMOBS
    weight: 3
```

### PlaceholderAPI
Все плейсхолдеры работают в любом тексте плагина (сообщения, sidebar, actionbar).

**Доступные плейсхолдеры:**
| Плейсхолдер | Значение |
|-------------|----------|
| `%voidrift_event%` | Название текущего ивента |
| `%voidrift_event_id%` | ID ивента |
| `%voidrift_time%` | Оставшееся время (m:ss) |
| `%voidrift_score%` | Очки игрока |
| `%voidrift_wave%` | Текущая волна |
| `%voidrift_players%` | Игроков в ивенте |
| `%voidrift_in_event%` | true/false |
| `%voidrift_objective_1%` | Прогресс цели 1 (3/10) |
| `%voidrift_objective_2%` | Прогресс цели 2 |
| `%voidrift_active_count%` | Активных ивентов |
| `%voidrift_top_1_name%` | Имя #1 в лидерборде |
| `%voidrift_top_1_score%` | Очки #1 |

### FlexAchievements
При выполнении целей ивента автоматически вызывается `fireCustomEvent`:

```yaml
rewards:
  flex-achievement: "my_achievement_id"
```

### SkyBound
В режиме аддона награды идут на остров:

```yaml
rewards:
  money: 5000        # → банк острова
  island-xp: 100    # → XP острова
```

### Vault
Если SkyBound нет — деньги через Vault экономику.

### SopItemsCreator / SopCustomBlocks
Кастомные предметы и блоки в наградах через команды:

```yaml
rewards:
  commands:
    - "sopitems give {player} custom_sword 1"
```

---

## 🧙 Визарды (настройка через игру)

### Порталы: `/riftadmin setup <ивент>`
1. Выбери тип входа (статический/динамический)
2. Установи позицию входа
3. Установи назначение (куда ТП)
4. Установи выход
5. Добавь промежуточные порталы (двусторонние)

### Зоны: `/riftadmin setupzone <id>`
1. Установи угол 1
2. Установи угол 2
3. Добавь точки спавна мобов
4. Добавь мобов (через чат: тип, id, модель, вес)
5. Установи макс. мобов

### Ивенты: `/riftadmin createevent <id>`
1. Введи название
2. Выбери тип
3. Укажи зону
4. Длительность
5. Макс. игроков
6. Награда

> Все визарды имеют кнопки **Skip** (пропустить) и **Back** (назад).

---

## 🎁 Лут-сундуки

### Рандомные
Спавнятся при старте ивента в случайных местах зоны. Стоят пока не заберут.

### Боссовые
Появляются после убийства босса. 4 режима:

| Режим | Описание |
|-------|----------|
| `CHEST` | Сундук на месте смерти |
| `DROP` | Предметы на землю |
| `KILLER` | В инвентарь убившему |
| `TOP_DAMAGE` | Тому кто нанёс больше урона |

---

## 📊 Отображение

- **ActionBar** — название ивента, время, очки, прогресс цели
- **Sidebar** — панель справа с целями и прогрессом
- **BossBar** — полоска сверху с таймером

Всё отключается в `config.yml` → `display`.

---

## 🌐 Мультиязычность

Все сообщения в `lang.yml`. Поддерживает:
- Цветовые коды (`&a`, `&l`)
- Hex цвета (`&#FF5555`) через SopLib
- PlaceholderAPI в любом тексте
- Плагины смены языка

---

## 📄 Лицензия

Проприетарный. © Reil
