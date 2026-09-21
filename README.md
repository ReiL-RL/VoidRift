# ✦ VoidRift

VoidRift — плагин событий для Minecraft-сервера. Может работать как отдельный плагин, но также умеет быть аддоном к SkyBound: выдавать награды в экономику/XP острова и использовать островной геймплей.

Главная идея: игрок входит в событие через портал, попадает на арену, выполняет цели, сражается с мобами/боссами, получает награды и выходит обратно только через портал выхода.

---

## Быстрый тест за 10 минут

### 1. Собери JAR

```bash
mvn -DskipTests clean package
```

Готовый файл будет здесь:

```text
target/voidrift-1.0.0-SNAPSHOT.jar
```

Если хочешь только проверить компиляцию:

```bash
mvn -DskipTests clean compile
```

### 2. Положи плагины на сервер

В папку `plugins/` положи:

- `voidrift-1.0.0-SNAPSHOT.jar`
- `SopLib.jar`
- `EliteMobs.jar`
- `FreeMinecraftModels.jar`

Важно: сейчас `SopLib`, `EliteMobs` и `FreeMinecraftModels` обязательные. Без них VoidRift не включится, потому что они указаны в `depend` внутри `plugin.yml`.

Опционально, но полезно:

- `SkyBound.jar` — если тестируешь как аддон к SkyBound.
- `Vault.jar` + плагин экономики — если хочешь проверять деньги без SkyBound.
- `PlaceholderAPI.jar` — если хочешь PAPI-плейсхолдеры.
- `MythicMobs.jar`, `Citizens.jar`, `SopCustomBlocks.jar`, `SopItemsCreator.jar` — дополнительные интеграции.

### 3. Запусти сервер

После первого запуска должны появиться файлы:

```text
plugins/VoidRift/config.yml
plugins/VoidRift/events.yml
plugins/VoidRift/zones.yml
plugins/VoidRift/portals.yml
plugins/VoidRift/lang.yml
```

В консоли не должно быть ошибок включения `VoidRift`.

### 4. Проверь здоровье плагина

В игре или консоли:

```text
/riftadmin doctor
```

Если всё хорошо, увидишь зависимости, количество событий/зон/порталов и результат проверки. Если что-то не настроено, команда скажет что именно исправить.

### 5. Быстрый путь через шаблоны

Встань в центр будущей арены и выполни:

```text
/riftadmin zonetemplate waves arena1 25
/riftadmin template waves void_waves arena1
/riftadmin setup void_waves
/riftadmin validate void_waves
/riftadmin startnow void_waves
```

Что делает каждая команда:

| Команда | Что делает |
|---|---|
| `/riftadmin zonetemplate waves arena1 25` | Создаёт зону `arena1` вокруг тебя радиусом 25 блоков. |
| `/riftadmin template waves void_waves arena1` | Создаёт событие `void_waves` типа волны мобов. |
| `/riftadmin setup void_waves` | Открывает мастер порталов: вход, точка телепорта, выход, возврат. |
| `/riftadmin validate void_waves` | Проверяет, можно ли запускать событие. |
| `/riftadmin startnow void_waves` | Мгновенно запускает событие. |

Можно также вывести эти шаги прямо в игре:

```text
/riftadmin quickstart
```

Если `/riftadmin validate` не находится, почти всегда на сервере стоит старый JAR. Пересобери проект, замени JAR в `plugins/` и полностью перезапусти сервер:

```bash
mvn -DskipTests clean package
```

Поддерживаются варианты:

```text
/riftadmin validate void_waves
/riftadmin validate all
/riftadmin val void_waves
/riftadmin check void_waves
```

---

## Минимальный тестовый сценарий игроком

После запуска события:

1. Игрок подходит к порталу входа.
2. Его телепортирует на арену.
3. На арене нельзя ломать/ставить блоки без права `voidrift.build`.
4. Игрок убивает мобов/выполняет цели.
5. Награда выдаётся после выполнения цели/ивента.
6. Выйти из события нужно через портал выхода.

Полезные команды игрока:

```text
/event
/event list
/event join void_waves
/event top
/event status
```

---

## Команды администратора

Основная команда:

```text
/riftadmin
```

Алиас:

```text
/evadmin
```

| Команда | Назначение |
|---|---|
| `/riftadmin doctor` | Полная проверка зависимостей, конфигов, ключей языка, событий и зон. |
| `/riftadmin quickstart` | Показывает быстрый тестовый маршрут. |
| `/riftadmin validate <event|all>` | Проверяет событие или все события перед запуском. |
| `/riftadmin val <event|all>` | Короткий алиас validate. |
| `/riftadmin check <event|all>` | Алиас validate/check. |
| `/riftadmin info [event]` | Показывает список событий/порталов или информацию по событию. |
| `/riftadmin info zone <zone>` | Показывает подробную информацию по зоне. |
| `/riftadmin template <type> <event> [zone]` | Создаёт готовый шаблон события. |
| `/riftadmin zonetemplate <type> <zone> [radius]` | Создаёт готовую зону вокруг игрока. |
| `/riftadmin createevent <id>` | Чат-мастер создания события на 12 шагов. |
| `/riftadmin setupzone <zone>` | Мастер настройки зоны через предметы в хотбаре. |
| `/riftadmin setup <event>` | Мастер настройки порталов через предметы в хотбаре. |
| `/riftadmin start <event>` | Запускает событие с отсчётом. |
| `/riftadmin startnow <event>` | Запускает событие сразу. |
| `/riftadmin stop <event>` | Останавливает событие с отсчётом. |
| `/riftadmin stopnow <event>` | Останавливает событие сразу. |
| `/riftadmin reload` | Перезагружает конфиги VoidRift. |

Типы шаблонов:

```text
waves, boss, resource, pvp, timed, islandwar
```

Для `zonetemplate` доступны:

```text
waves, boss, resource, pvp, timed
```

---

## Команды игрока

Основная команда:

```text
/event
```

Алиасы:

```text
/ev
/events
/rift
```

| Команда | Назначение |
|---|---|
| `/event` | Открыть GUI событий. |
| `/event list` | Список событий. |
| `/event join <event>` | Войти в событие, если доступно. |
| `/event leave` | Выйти, если событие разрешает. В портальных событиях выход только через портал. |
| `/event top [event]` | Таблица лидеров. |
| `/event status` | Статус интеграций. |
| `/event heart` | Информация по сердцу острова в Island War. |
| `/event attack` | Меню атаки островов в Island War. |

---

## Права

| Право | Кому | Что даёт |
|---|---|---|
| `voidrift.admin` | Админ | Все админ-команды. |
| `voidrift.join` | Игрок | Участие в событиях. |
| `voidrift.build` | Админ/строитель | Можно строить и ломать внутри арен VoidRift. |

---

## Что проверять при первом тесте

### Обязательная проверка

```text
/riftadmin doctor
/riftadmin validate all
```

`doctor` должен показать:

- SopLib: OK
- EliteMobs: OK
- FreeMinecraftModels: OK
- события загружены;
- зоны загружены;
- порталы настроены;
- нет missing-ключей `lang.yml`.

### Если событие не запускается

Проверь:

```text
/riftadmin validate <event>
```

Частые причины:

- нет зоны;
- в зоне нет точек спавна;
- в зоне пустой `mob-pool`;
- не настроен портал входа;
- не настроен портал выхода;
- не настроена точка возврата у выхода;
- указан EliteMobs/MythicMobs моб, которого нет;
- нет экономики, но в наградах указаны деньги.

---

## Важная логика: арена и зоны

В VoidRift зона — это не “вся арена целиком навсегда”, а прямоугольная часть арены. Одна арена может состоять из нескольких зон/областей.

Правильная модель такая:

```text
Арена события
├─ зона/область 1: основной зал
├─ зона/область 2: боковая комната
├─ зона/область 3: мост/коридор
├─ точки спавна мобов
└─ mob-pool: какие мобы могут появляться
```

В `zones.yml` один `zone-id` описывает арену события, но внутри него могут быть несколько прямоугольных `areas`. Это нужно, чтобы арена не была только одной простой коробкой.

Пример:

```yaml
zones:
  void_arena:
    world: world
    areas:
      - pos1: { x: 0, y: 60, z: 0 }
        pos2: { x: 40, y: 90, z: 40 }
      - pos1: { x: 45, y: 60, z: 10 }
        pos2: { x: 70, y: 90, z: 30 }
    spawn-points:
      sp1: { x: 10, y: 64, z: 10 }
      sp2: { x: 35, y: 64, z: 35 }
      sp3: { x: 55, y: 64, z: 20 }
    max-mobs: 20
```

То есть в будущем лучше думать так:

- событие использует одну арену/zone-id;
- внутри zone-id может быть несколько прямоугольных областей;
- спавны мобов относятся к этой арене;
- portal входа/выхода относится к событию.

---

## Как работают мобы в зоне

Мобы появляются не “где попало”, а в точках `spawn-points`.

`mob-pools` говорит, какие мобы могут появляться:

```yaml
mob-pools:
  - mob-type: VANILLA
    mob-id: ZOMBIE
    model: void_zombie
    weight: 5
    wave: 0

  - mob-type: ELITEMOBS
    mob-id: void_reaver.yml
    weight: 1
    wave: 5
```

Поля:

| Поле | Что значит |
|---|---|
| `mob-type` | Тип источника моба: `VANILLA`, `ELITEMOBS`, `MYTHICMOBS`. |
| `mob-id` | Для vanilla — `ZOMBIE`, `SKELETON`; для EliteMobs — имя файла босса `.yml`. |
| `model` | Модель FreeMinecraftModels для vanilla-моба. Можно `-` или пусто. |
| `weight` | Вес спавна. Чем больше, тем чаще выбирается моб. |
| `wave` | Волна появления. `0` значит с самого начала/любая волна. |

### Как примерно выбирается моб

Если в пуле есть:

```yaml
- ZOMBIE weight: 5
- SKELETON weight: 3
- void_reaver.yml weight: 1
```

То чаще всего будет Zombie, реже Skeleton, ещё реже EliteMobs босс.

### Bonus waves

Можно добавить особых мобов на конкретную волну:

```yaml
bonus-waves:
  5:
    - mob-type: ELITEMOBS
      mob-id: void_reaver.yml
      weight: 1
      wave: 5
```

---

## Как работать с EliteMobs

EliteMobs обязателен для текущей версии VoidRift.

1. Установи EliteMobs.
2. Положи босса в папку EliteMobs, обычно:

```text
plugins/EliteMobs/custombosses/
```

3. В `zones.yml` укажи:

```yaml
mob-pools:
  - mob-type: ELITEMOBS
    mob-id: void_reaver.yml
    weight: 1
    wave: 5
```

Если `validate` ругается на EliteMobs-моба, проверь:

- файл реально существует;
- имя файла совпадает;
- EliteMobs загрузился без ошибок;
- моб указан именно как `mob-type: ELITEMOBS`.

---

## Как работать с FreeMinecraftModels / FMM

FreeMinecraftModels обязателен для текущей версии VoidRift.

FMM нужен для моделей на vanilla-мобах. Например, ты хочешь обычного Zombie, но с моделью `void_zombie`.

```yaml
mob-pools:
  - mob-type: VANILLA
    mob-id: ZOMBIE
    model: void_zombie
    weight: 5
    wave: 0
```

Важно:

- `mob-id` остаётся vanilla-типом: `ZOMBIE`, `SKELETON`, `CREEPER`.
- `model` — ID модели FMM.
- Если модель не нужна, оставь `model` пустым или `-`.

---

## Индивидуальные настройки запуска события

У каждого события можно включить/выключить автозапуск, предупреждения и отсчёт.

Пример:

```yaml
events:
  void_waves:
    enabled: true
    interval-seconds: 1800

    announcements:
      enabled: true
      warning-seconds: [300, 60, 10]

    preview:
      enabled: true
      seconds: 10
```

Что это значит:

| Поле | Что делает |
|---|---|
| `enabled` | Полностью включает/выключает событие. Если `false`, событие не стартует. |
| `interval-seconds` | Через сколько секунд событие может стартовать автоматически. |
| `announcements.enabled` | Включает/выключает предупреждения до старта. |
| `announcements.warning-seconds` | За сколько секунд предупреждать: `300` = 5 минут, `60` = 1 минута, `10` = 10 секунд. |
| `preview.enabled` | Включает короткий отсчёт перед стартом. |
| `preview.seconds` | Длина отсчёта перед стартом. |

Если хочешь предупреждение только за 5 минут:

```yaml
announcements:
  enabled: true
  warning-seconds: [300]
```

Если вообще не хочешь предупреждений:

```yaml
announcements:
  enabled: false
```

---

## Название события над порталом

Когда событие активно, над входным порталом появляется название события.

Настройка в `config.yml`:

```yaml
portal:
  labels:
    enabled: true
    format: "&d✦ {event}"
    offset-y: 2.4
```

Плейсхолдеры:

| Плейсхолдер | Значение |
|---|---|
| `{event}` | Красивое имя события. |
| `{id}` | ID события. |

Если подпись мешает:

```yaml
portal:
  labels:
    enabled: false
```

### Если игрок не может выйти

Для портальных событий это нормально: выход должен быть через портал выхода. Проверь настройку:

```text
/riftadmin info <event>
```

У события должен быть портал `exit` с `pos` и `dest`.

---

## Настройка через визарды

### Мастер зоны

```text
/riftadmin setupzone arena1
```

Мастер использует предметы в хотбаре:

1. Угол 1.
2. Угол 2.
3. Дополнительные прямоугольные зоны, если арена не одна ровная коробка.
4. Точки спавна мобов.
5. Мобы:
   - Vanilla / FMM моб;
   - EliteMobs босс.
6. Максимум мобов.

На арене строительство и ломание блоков запрещены для игроков события.

### Мастер порталов

```text
/riftadmin setup void_waves
```

Шаги:

1. Выбрать статический или динамический вход.
2. Поставить вход.
3. Поставить назначение входа — куда телепортирует игрока.
4. Поставить портал выхода.
5. Поставить точку возврата для выхода.
6. При желании добавить промежуточные двусторонние порталы.

### Мастер события

```text
/riftadmin createevent my_event
```

Он спрашивает:

1. Название.
2. Описание.
3. Тип события.
4. Зону.
5. Длительность.
6. Интервал автозапуска.
7. Минимум игроков.
8. Максимум игроков.
9. Деньги.
10. XP острова SkyBound.
11. Сколько мобов добавлять за игрока.
12. Подтверждение.

---

## Конфиги

### `config.yml`

Главные настройки плагина:

- `max-active-events` — сколько событий может идти одновременно.
- `arena-protection` — запрет строительства/ломания на арене.
- `auto-start` — автозапуск событий.
- `portal` — радиус порталов, частицы, задержки.
- `sounds` — звуки старта, конца, порталов, целей.
- `modifiers` — случайные модификаторы событий.
- `display` — ActionBar, Sidebar, BossBar.
- `rewards` — кулдаун наград.
- `scaling` — масштабирование сложности под количество игроков.
- `loot-chests` — случайные и боссовые сундуки.
- `integrations` — настройки интеграций.

### `events.yml`

Список событий. Главное:

- `display-name` — название.
- `description` — описание.
- `type` — тип события.
- `zone` — ID зоны.
- `duration-seconds` — длительность.
- `interval-seconds` — интервал автозапуска.
- `min-players`, `max-players` — лимиты игроков.
- `complete-on` — `ALL` или `ANY`.
- `objectives` — цели.
- `rewards` — награды.

### `zones.yml`

Зоны/арены:

- `world` — мир.
- `areas` или `pos1/pos2` — прямоугольные области.
- `spawn-points` — точки спавна мобов.
- `mob-pools` — какие мобы спавнятся.
- `bonus-waves` — дополнительные мобы на конкретных волнах.
- `max-mobs` — лимит мобов.

### `portals.yml`

Порталы событий:

- `entry` — вход в событие.
- `exit` — выход из события.
- `dynamic` — вход с несколькими возможными позициями.
- `intermediate` — промежуточные двусторонние порталы внутри события.

### `lang.yml`

Все сообщения плагина. Поддерживает:

- `&a`, `&l` и другие цветовые коды;
- hex-цвета через SopLib;
- PlaceholderAPI;
- плейсхолдеры вида `{event}`, `{player}`, `{score}`.

---

## Типы событий

| Тип | Что делает |
|---|---|
| `WAVE_SURVIVAL` | Волны мобов, выживание, зачистка волн. |
| `BOSS_FIGHT` | Бой с боссом. |
| `RESOURCE_RACE` | Гонка ресурсов/очков за добычу и сбор. |
| `PVP_ARENA` | PvP-событие с очками за убийства. |
| `TIMED_CHALLENGE` | Испытание на время. |
| `ISLAND_WAR` | Война островов для режима SkyBound. |
| `CUSTOM` | Кастомное событие под ручную настройку. |

---

## Цели событий

Поддерживаемые цели:

```text
KILL_MOBS
KILL_BOSS
KILL_ELITE
DEAL_DAMAGE
TAKE_DAMAGE
NO_DEATH
KILL_STREAK
LAST_HIT_BOSS
REACH_WAVE
SURVIVE_TIME
ALL_MOBS_DEAD
CLEAR_WAVES
COLLECT_ITEM
COLLECT_FROM_MOB
COLLECT_FROM_CHEST
MINE_BLOCK
PLACE_BLOCK
SCORE_POINTS
USE_PORTAL
USE_ITEM
EAT_FOOD
CRAFT_ITEM
COMPLETE_BEFORE
CUSTOM
```

Пример:

```yaml
objectives:
  - type: KILL_MOBS
    amount: 20
    target: ""
  - type: SURVIVE_TIME
    amount: 180
    target: ""
```

---

## Интеграции

### SopLib

Обязательная зависимость. Используется как ядро совместимости версий и для обработки текста/цветов.

### EliteMobs

Обязательная зависимость. Для EliteMobs моба укажи:

```yaml
mob-type: ELITEMOBS
mob-id: my_boss.yml
```

Файлы боссов обычно лежат в:

```text
plugins/EliteMobs/custombosses/
```

### FreeMinecraftModels

Обязательная зависимость. Для модели на vanilla-мобе:

```yaml
mob-type: VANILLA
mob-id: ZOMBIE
model: my_model
```

### SkyBound

Опционально. Если установлен, VoidRift работает как аддон:

- деньги могут идти через SkyBound;
- `island-xp` выдаётся острову;
- доступна война островов.

### Vault

Опционально. Если SkyBound недоступен, деньги могут выдаваться через Vault-экономику.

### PlaceholderAPI

Опционально. Тексты проходят через PlaceholderAPI.

---

## Рекомендованный тестовый чеклист

1. Сервер запустился без ошибок.
2. `/riftadmin doctor` не показывает критических проблем.
3. `/riftadmin zonetemplate waves arena1 25` создал зону.
4. `/riftadmin template waves void_waves arena1` создал событие.
5. `/riftadmin setup void_waves` настроил вход, назначение, выход и возврат.
6. `/riftadmin validate void_waves` показывает OK.
7. `/riftadmin startnow void_waves` запускает событие.
8. Игрок входит через портал.
9. Игрок не может ломать/ставить блоки на арене.
10. Мобы появляются.
11. Цели засчитываются.
12. Награда выдаётся.
13. Игрок выходит через портал выхода.
14. `/riftadmin stopnow void_waves` корректно останавливает событие.

---

## Если что-то сломалось

Сначала выполни:

```text
/riftadmin doctor
/riftadmin validate all
```

Потом смотри консоль сервера. VoidRift пишет предупреждения по отсутствующим ключам языка, проблемам конфигов и интеграциям.

Если менял `lang.yml` и видишь `[Missing lang: ...]`, значит в языке нет нужного ключа.

---

## Статус

Проект сейчас в стадии активного тестирования. Перед релизом нужно прогнать реальные сценарии на сервере:

- standalone без SkyBound;
- addon-режим со SkyBound;
- волны;
- босс;
- resource race;
- PvP arena;
- timed challenge;
- Island War.
