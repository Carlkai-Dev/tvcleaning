# Лаунчер на Xiaomi TV Stick 4K: анализ и план улучшений

Дата: 2026-10-18. Объект: `launcher/` (пакет `com.gathin.tvhome`, ~900 строк Java + XML) из этого
репозитория. Цель: ультрасовременный, быстрый и удобный домашний экран именно на Xiaomi TV Stick 4K
(MDZ-27-AA, первое поколение, Android TV 11).

Как делался анализ: весь код лаунчера, манифест, сборка и скрипты прочитаны построчно; семь независимых
разборов (производительность, навигация и фокус, совместимость со стиком и Android TV 11, визуал, удобство,
архитектура и сборка, отказоустойчивость) дали 127 находок; каждая находка затем проходила проверку
«опровергни факт» и «опровергни ценность» с перечитыванием кода и исходников AOSP android11-release.
3 находки отброшены, у ~60 понижена серьёзность или урезано предложение. Ниже только то, что устояло.

Важное ограничение: физического стика у меня нет. Всё, что помечено **[проверить на устройстве]**,
опирается на спецификации и исходники Android, а не на замер. Раздел 10 даёт точные команды.

Содержание

1. Коротко: 12 изменений, которые дадут 80 % эффекта
2. Профиль устройства: что подтверждено, что нет
3. Что в лаунчере уже сделано правильно
4. Находки по темам (с приоритетами)
   - 4.1 Скорость: холодный старт и возврат на Home
   - 4.2 Навигация и фокус
   - 4.3 Специфика стика и Android TV 11
   - 4.4 Визуальное обновление
   - 4.5 Удобство ежедневного использования
   - 4.6 Отказоустойчивость
   - 4.7 Русский язык и типографика
   - 4.8 Сборка, установка, инструменты
5. Что делать не стоит (и почему)
6. Дорожная карта
7. Как измерять результат
8. Открытые вопросы к владельцу стика
9. Источники
10. Чек-лист команд для проверки на устройстве

Обозначения: **P0** — баг или блокер на стике; **P1** — заметное улучшение скорости или удобства;
**P2** — приятно, но не критично; **P3** — опционально или спорно. Трудоёмкость: **S** — до вечера,
**M** — 1–3 вечера, **L** — больше.

---

## 1. Коротко: 12 изменений, которые дадут 80 % эффекта

| # | Что | Почему именно на стике | Приоритет / трудоёмкость |
|---|-----|------------------------|--------------------------|
| 1 | Убрать `adapter.reload()` и `scheduleLayoutAnimation()` из `onResume`; грузить список приложений один раз в фоне и обновлять только по событию `LauncherApps` | Каждое нажатие Home на 4×Cortex-A35 сейчас заново опрашивает PackageManager и проигрывает 420 мс анимацию «въезда» | P0 / M |
| 2 | Не сбрасывать alpha стекла/героя/подсветки в `onResume`, включить stable ids и пересинхронизировать фокус | Возврат из Netflix выглядит как «лаунчер перезагружается» | P0 / S |
| 3 | Собирать release с R8 и выполнять `pm compile -m speed` после установки | Debug-APK заставляет ART игнорировать AOT и работать через интерпретатор/JIT | P1 / S |
| 4 | Обрабатывать Left/Right на плитках самим (как уже сделано для Up/Down) | «Вправо» на правом столбце выбрасывает фокус на пилюлю Settings | P0 / S |
| 5 | Home и Back на домашнем экране сворачивают сетку к доку; Down с пилюли возвращает на тот док-тайл, откуда ушли | Это ожидаемое поведение любого TV-лаунчера, сейчас Back ничего не делает, а Home оставляет в 5-й строке сетки | P1 / S |
| 6 | Скрывать пилюлю Inputs, когда нет passthrough-входов и Live TV; граница фокуса по первой/последней видимой пилюле | На донгле нет HDMI-входов и тюнера: кнопка только показывает тост «No inputs found» | P0 / S |
| 7 | Профиль конфига для стика: пустой `live_tv_package`, пустой `hidden_apps`, дока без Hotstar; размер дока = число реально найденных избранных | Сейчас четвёртый слот дока молча занимает первое по алфавиту приложение без подписи | P0 / S |
| 8 | Шрифт с кириллицей (Manrope / Golos Text / Inter) + `values-ru` + вынос строк из Java | В Outfit 0 кириллических глифов: «СУББОТА 18 ОКТЯБРЯ», «Кинопоиск», «Иви» рисуются Roboto рядом с Outfit-часами | P0 / S |
| 9 | Защита от краш-цикла: try/catch вокруг загрузки каждого приложения и всего `reload()`; установка в два этапа с проверкой HOME | Стоковый лаунчер отключён, диалогов об ошибке на TV нет: падение в `onCreate` = чёрный экран навсегда без ADB | P0 / S |
| 10 | Меню по долгому нажатию: Открыть / В док / Из дока / Влево / Вправо / Скрыть / Удалить / О приложении, хранение в SharedPreferences поверх config.xml | Сейчас чтобы поменять док, нужен ноутбук с JDK и Android SDK | P1 / M |
| 11 | Пружины стекла 650 / 0.75 вместо 260 / 0.58, масштаб плитки 150 мс вместо 260 с овершутом 1.6 | Сейчас подсветка «доплывает» ~0,43 с и отстаёт от автоповтора D-pad | P1 / S |
| 12 | Скрипт измерений (`am start -W`, `dumpsys gfxinfo`, `dumpsys meminfo`) до и после | Без цифр нельзя понять, что из этого реально помогло именно на этом железе | P1 / S |

---

## 2. Профиль устройства: что подтверждено, что нет

| Факт | Уверенность | Источник / как проверить |
|------|-------------|--------------------------|
| Модель MDZ-27-AA, `ro.product.model=MiTV-AYFR0`, кодовое имя `soul`, платформа Amlogic S4 | высокая | гайд по восстановлению RodrigoDeveloperX; `getprop ro.product.device` |
| SoC Amlogic S905Y4: 4×Cortex-A35 ~2 ГГц, Mali-G31 MP2 | высокая | спецификация mi.com, обзоры |
| 2 ГБ RAM, 8 ГБ eMMC (~5 ГБ свободно). Один агрегатор (tvsfaq) пишет 1 ГБ для того же кода модели | средняя | `cat /proc/meminfo` |
| Android TV 11 (API 30), прошивка RTT0.211222.001.1440; оболочка Android TV Home (`com.google.android.tvlauncher`), не Google TV | высокая для 11; HOME-пакет подтвердить | `getprop ro.build.display.id`, `cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME` |
| Существует OTA на Android 14 для MDZ-27-AA (V816.0.26.x), после которого остаётся Android TV Home, а не Google TV. Известен сценарий «кирпича» при обрыве обновления | низкая: единственный источник — сторонний гайд по восстановлению, официальных анонсов нет | `getprop ro.build.version.release` |
| Донгл: один HDMI-выход, micro-USB питание; HDMI-входов, AV и тюнера нет, значит `TvInputManager.getTvInputList()` не вернёт passthrough-входов | высокая | `dumpsys tv_input` |
| Пульт Bluetooth: мик Google Assistant, D-pad, Back, Home, громкость, питание, горячие клавиши Netflix и Prime Video (не переназначаются). В Индии ещё Hotstar и/или PatchWall. Кнопок Input, Menu, Apps нет | средняя (зависит от региона) | `getevent -l` при нажатии каждой кнопки |
| Кадровый буфер UI 1920×1080 при density 320 (960×540 dp) | **не подтверждено** ни одним источником; типично для Amlogic | `wm size`, `wm density` |
| 4K60 на выходе, Dolby Vision, AV1. HDR10+ есть только у 2nd Gen (MDZ-33-AA, Google TV, другое устройство) | высокая | обзоры GSMArena, cnx-software |
| ADB по сети поднимается одним переключателем «USB debugging» (как на телевизорах Xiaomi) | **не подтверждено** для этой прошивки | попробовать `adb connect <ip>:5555` |
| Предустановлено: PatchWall (вне Индии показывает пустой экран), Xiaomi TV+ (регион-лок), Miracast, обои Xiaomi; точные имена пакетов не опубликованы | средняя | `pm list packages \| grep -iE 'xiaomi\|mitv\|miui'` |
| Включён ли переключатель Location (от него зависит чтение SSID) | неизвестно | Settings → Device Preferences → Location |

Платформенные факты, проверенные по исходникам android11-release и важные для предложений ниже:

- Нажатие Home, когда лаунчер уже на экране, доставляет HOME-intent в тот же `singleTask`-activity и прогоняет
  `onPause → onNewIntent → onResume` (ActivityStarter.deliverToCurrentTopIfNeeded, TransactionExecutorHelper).
  Значит всё, что лежит в `onResume`, выполняется на каждое нажатие Home.
- На TV система не показывает диалоги «приложение остановлено» и ANR (ActivityTaskManager.currentUiModeSupportsErrorDialogs
  возвращает false для UI_MODE_TYPE_TELEVISION); упавший HOME просто перезапускается.
- Для debuggable-приложений ART игнорирует AOT-код и исполняет интерпретатор, пока JIT не прогреется
  (ClassLinker::ShouldUseInterpreterEntrypoint). Сторонняя установка не запускает компиляцию до фонового dexopt «ночью».
- `KEYCODE_ASSIST` / `KEYCODE_VOICE_ASSIST` перехватываются PhoneWindowManager до приложения; `KEYCODE_SEARCH`
  доходит до activity, и если `onSearchRequested()` возвращает false (на TV так по умолчанию), PhoneWindow сам
  запускает ассистента. Лаунчеру ничего не нужно делать с кнопкой микрофона, только не перехватывать её.
- Фильтрация видимости пакетов действует только при targetSdk ≥ 30. При текущем targetSdk 29 лаунчер видит все
  приложения; при поднятии targetSdk без `<queries>` сетка станет пустой.
- `WifiInfo.getSSID()` на Android 10/11 возвращает `<unknown ssid>`, если нет ACCESS_FINE_LOCATION **и** выключен
  системный переключатель Location. Иного пути без привилегий на API 30 нет.
- `android.permission.READ_TV_LISTINGS` объявлен TvProvider как `dangerous`, то есть выдаётся через `pm grant`
  или runtime-диалог; с ним сторонний лаунчер читает `watch_next_program` всех приложений с `searchable=1`
  (TvProvider.createSqlParams). Это открывает «Продолжить просмотр» (см. 4.5).

---

## 3. Что в лаунчере уже сделано правильно

Это стоит сохранить при любом рефакторинге.

- Одна плавающая подсветка (`GlassView`) двигается пружинами по TRANSLATION_X/Y (`MainActivity.java:138-142`):
  это свойства RenderNode, без relayout; `GlassView.onDraw` перерисовывается только при смене размера.
- RecyclerView настроен для статичной сетки: `setHasFixedSize(true)`, `setItemAnimator(null)` (`MainActivity.java:108-109`),
  скругление плиток через `clipToOutline` (`AppAdapter.java:242-249`), полоса дока рисуется в ItemDecoration, а не
  лишними view.
- Тема даёт мгновенный чёрный первый кадр и не анимирует окно (`styles.xml:3-7`); `configChanges` покрывает
  смену режима HDMI без пересоздания activity (`AndroidManifest.xml:26`); флаги HOME-activity (`singleTask`,
  `stateNotNeeded`, `excludeFromRecents`, `clearTaskOnLaunch`) корректны.
- Детерминированные Up/Down: ровно одна строка, дока → верхняя панель, края панели не проваливаются
  (`MainActivity.java:306-320`, `152-160`).
- Запуск приложений явным `ComponentName` с `CATEGORY_LEANBACK_LAUNCHER` и `NEW_TASK|RESET_TASK_IF_NEEDED`
  (`AppAdapter.java:155-165`), всё падает мягко в тосты, а не в краш.
- Зависимости минимальны (RecyclerView + DynamicAnimation), один вариативный шрифт 110 КБ, нет сервисов,
  сети и wake-lock'ов. Всё обратимо: `pm disable-user`, никаких удалений.
- Контраст текста на чёрном везде выше порогов WCAG для основного текста; радиусы в основном концентричны.

---

## 4. Находки по темам

### 4.1 Скорость: холодный старт и возврат на Home

**P0 · M — Каталог приложений строится синхронно в главном потоке на каждом `onResume`**

- Сейчас: `MainActivity.java:177` вызывает `adapter.reload()`; `AppAdapter.java:86-113` делает
  `queryIntentActivities`, затем для каждого приложения `loadBanner` (дважды, строки 95-96, второй вызов избыточен:
  `PackageItemInfo.loadBanner` уже сам откатывается на баннер приложения), `loadIcon` (97, даже когда баннер есть),
  `loadLabel`, сортирует и делает `notifyDataSetChanged()`. Ни одного Executor/Thread в модуле нет.
- Уточнение после проверки: на тёплом возврате декодирования картинок почти нет, потому что
  `ApplicationPackageManager` держит статический WeakReference-кэш ConstantState, а старый список ещё жив до
  строки 110. Поэтому стоимость тёплого возврата — binder-вызов + N обращений к ресурсам чужих APK + полный
  rebind, ориентировочно десятки–150 мс, а не сотни. На холодном старте декодируются все баннеры целиком.
- Эффект: первый кадр после Home задерживается, затем поверх этого проигрывается анимация (см. ниже), и
  домашний экран «устаканивается» через 0,6–1 с. Это главное ощущение «медленно».
- Предложение, минимальная версия: (1) строить каталог один раз на `Executors.newSingleThreadExecutor()`,
  стартуя из `onCreate`, результат постить в main; (2) убрать `reload()` из `onResume` совсем; (3) обновлять только
  из `LauncherApps.registerCallback()` (API 21, зарегистрировать в `onCreate`, снять в `onDestroy`): это
  callback, а не фоновый сервис, правило CONTRIBUTING не нарушается; (4) `loadIcon` вызывать только если баннера
  нет; (5) цвет для ambient считать там же в фоне и хранить в `Entry` (сейчас кэш теряется при каждом reload,
  `AppAdapter.java:177-182`, пересчёт на первом фокусе дока). Двухфазная загрузка (сначала 4 приложения дока,
  потом остальные) — если холодный старт после этого всё ещё > 400 мс.
- Не делать сразу: DiffUtil и дисковый снапшот каталога. Процесс HOME имеет oom_adj 600 и вытесняется позже
  всех кэшированных приложений, частота холодных стартов неизвестна. Сначала замер.

**P0 · S — Анимация «въезда» и затухания повторяются на каждом возврате**

- Сейчас: `grid.scheduleLayoutAnimation()` в `onResume` (`MainActivity.java:178`), сброс `glass`, `heroName`,
  `ambient` в alpha 0 (`172-176`); `tile_enter.xml` длится 420 мс (alpha+translate+scale) с шагом 25 мс на плитку;
  затем герой 260 мс и ambient 600 мс заново. Устаревшие view-анимации ещё и инвалидируют весь RecyclerView
  каждый кадр.
- Предложение: флаг `entered`; `scheduleLayoutAnimation()` только при первом заполнении (холодный старт).
  Укоротить `tile_enter` до 260 мс, translate 12 %, без scale. Не сбрасывать alpha в `onResume`, если фокус
  всё ещё на плитке и список не изменился. tvOS и Google TV никогда не переанимируют домашний экран при возврате.

**P1 · S — Debug-сборка без AOT**

- Сейчас: `build-and-install.sh:11` собирает `assembleDebug`, `:16` ставит `pm install -r` без `pm compile`;
  в `app/build.gradle:17-21` у release нет signingConfig, то есть `assembleRelease` сегодня даёт неподписанный APK.
- Эффект: RecyclerView, GridLayoutManager, DynamicAnimation и собственный код исполняются интерпретатором до
  прогрева JIT на in-order ядрах A35; плюс JDWP-поток и CheckJNI. Порядок величины: холодный старт и первые кадры
  в 1,5–3 раза медленнее release. **[проверить на устройстве]**
- Предложение: `signingConfigs.release`, переиспользующий `~/.android/debug.keystore` (или ключ из репозитория,
  см. 4.8); `release { debuggable false; minifyEnabled true; shrinkResources true; proguardFiles
  getDefaultProguardFile('proguard-android-optimize.txt') }`; `<profileable android:shell="true"/>` в манифесте,
  чтобы `am start -W` и perfetto продолжали работать; в скрипте после установки `pm compile -m speed -f
  com.gathin.tvhome` (несколько секунд, пара сотен КБ odex). Проверка: `dumpsys package com.gathin.tvhome | grep
  -A3 Dexopt` должен показывать `[status=speed]`. Baseline profiles / profileinstaller не нужны.

**P1 · S — Полноэкранный ambient рисуется через offscreen-слой на время фейдов**

- Сейчас: `ambient` — обычный `View` на весь экран (`activity_main.xml:9-13`) с радиальным GradientDrawable
  (`MainActivity.java:97-102`); `View.animate().alpha()` (`:265`, `:275-278`, `:295`) при 0 < alpha < 1 заставляет
  HWUI рисовать 1920×1080 через saveLayer каждый кадр 0,2–1,1 с после каждого шага по доку, ровно когда идут
  пружина, масштаб плитки и слайд героя.
- Предложение (3 строки): `AmbientView extends View` с `hasOverlappingRendering() { return false; }` (тогда alpha
  применяется к drawable без слоя). Укоротить кроссфейд 200+500 → 150+300 мс. Плюс `ambientGlow.setDither(true)`
  против колец на 8-битных панелях (у GradientDrawable дизеринг по умолчанию выключен; Skia дизерит
  непостоянные шейдеры при флаге). Pre-render в битмап — только если полосы останутся.

**P2 · S — Остальное по мелочи**

- Два непрозрачных чёрных слоя на весь экран: `styles.xml:4` + `activity_main.xml:6`. Убрать фон у root,
  оставить windowBackground (он даёт мгновенный первый кадр). Под непрозрачным баннером не нужен `tile_bg`:
  `h.card.setBackground(e.banner != null ? null : tileBg)`.
- `setHasStableIds(true)` с `getItemId()` из постоянной `HashMap<String, Long>` по `component.flattenToString()`:
  RecyclerView переиспользует тот же ViewHolder, фокус не покидает плитку (см. 4.2).
- Поллинг раз в минуту (`MainActivity.java:75-83`) заменить на receiver `ACTION_TIME_TICK` + 
  `ConnectivityManager.registerDefaultNetworkCallback` (регистрация в `onStart`, снятие в `onStop`): ноль
  поллинга, мгновенная реакция на смену сети. CPU-стоимость сегодняшнего тика ничтожна, дело в правиле проекта
  «no polling» и в лаге до 60 с.
- Не трогать: механику анимаций фокуса/пружин (они дёшевы, слои и `withLayer()` только навредят), inflate-путь
  layout'а (двойной layout из-за `setPadding` внутри layout-листенера — единицы мс), `setItemViewCacheSize`
  (LinearLayoutManager заполняет padding, холдеры почти не переиспользуются на шаг строки).

**P1 · S — Скрипт измерений** — см. раздел 7. Его стоит сделать первым: все оценки выше — порядок величины.

### 4.2 Навигация и фокус

**P0 · S — «Вправо» на правом столбце прыгает на пилюлю Settings**

- Сейчас: `AppAdapter.java:251-256` перехватывает только Up/Down; Left/Right отдаются фреймворку.
  `GridLayoutManager.onFocusSearchFailed` для FOCUS_RIGHT в вертикальном списке возвращает null, RecyclerView
  падает в оконный FocusFinder, а его критерий для FOCUS_RIGHT — лишь `src.left < dest.left && src.right < dest.right`.
  При 960 dp: третий столбец заканчивается на ~888 dp, пилюля Settings начинается ~775 dp и заканчивается 904 dp,
  то есть единственный кандидат. Стекло и герой исчезают, сетка остаётся прокрученной, Down с пилюли потом
  попадает на первый дочерний элемент RecyclerView (см. ниже). **[проверить геометрию на устройстве]**
- Предложение: обобщить `onTileVerticalKey` до `onTileKey(pos, keyCode)` на все четыре направления.
  Left: `if (pos % COLUMNS == 0) return true; focusTile(pos-1)`. Right: `if (pos % COLUMNS == COLUMNS-1 || pos ==
  count-1) return true; focusTile(pos+1)`. Без переноса на следующую строку (так делают tvOS и стоковый ATV).
  ~15 строк.

**P1 · S — Home на домашнем экране не возвращает к доку, Back ничего не делает**

- Сейчас: `onNewIntent` не переопределён (манифест: `singleTask`, `clearTaskOnLaunch`), `onBackPressed()`
  пустой (`MainActivity.java:197-200`). Home из 5-й строки сетки оставляет вас в 5-й строке, Back — тоже.
- Предложение: поле `lastDockFocused` (обновлять в `onTileFocused` при `position < COLUMNS`) и хелпер
  `collapseToDock()` = `focusTile(lastDockFocused)` (ветка дока в `onTileFocused` сама паркует сетку).
  `onNewIntent`: `if (intent.hasCategory(CATEGORY_HOME)) { dismissPopup(); lastFocused = lastDockFocused; }`,
  дальше существующий `post` в `onResume` сам сфокусирует. `onBackPressed`: если открыт popup — закрыть; если
  фокус не на доке — `collapseToDock()`; на доке — ничего, `super` не вызывать (HOME не должен финишировать).
  Справочно: гайд Android TV прямо называет «Back сбрасывает фокус к началу длинного списка» ожидаемым поведением.

**P1 · S — Down с пилюли попадает на первый дочерний элемент, а не туда, откуда ушли**

- Сейчас: `nextFocusDown="@+id/grid"` на всех трёх пилюлях (`activity_main.xml:136,143,150`); RecyclerView с
  `afterDescendants` отдаёт фокус дочернему элементу с индексом 0 (`ViewGroup.onRequestFocusInDescendants`
  перебирает с нуля для FOCUS_DOWN). Up с Prime Video → Wi-Fi → Down → Netflix.
- Предложение: в `pillKeys` обработать `KEYCODE_DPAD_DOWN` → `focusTile(lastDockFocused); return true;`,
  атрибуты `nextFocusDown` убрать. 5 строк, поле общее с предыдущим пунктом.

**P0 · S — Возврат из приложения: фокус «прыгает» через пилюлю Inputs, стекло и герой не восстанавливаются корректно**

- Сейчас: без stable ids `notifyDataSetChanged()` удаляет сфокусированный item из дерева
  (`LayoutManager.scrapOrRecycleView`: `removeViewAt` для invalid-холдеров без stable ids), `ViewGroup.removeViewInternal`
  вызывает `rootViewRequestFocus()` → фокус на один кадр уходит на первый focusable окна — `btnInputs`
  (`activity_main.xml:131`, он раньше `grid` в дереве) — пилюля вспыхивает белым; затем `post` в `onResume`
  (`MainActivity.java:184-188`) возвращает фокус на плитку с повторной анимацией овершута. `onBindViewHolder`
  при этом сбрасывает scale и alpha имени (`AppAdapter.java:127-129`).
- Ловушка: если просто включить stable ids, фокус останется на плитке, и ранний `return` в `post` оставит стекло
  и героя невидимыми (их alpha уже сброшена в 0 на строках 172-176).
- Предложение (вместе): (1) `setHasStableIds(true)`; (2) в `onBindViewHolder` выводить визуальное состояние из
  `h.card.isFocused()`, а не сбрасывать его; (3) в `post` вместо раннего возврата: `vh = grid.findContainingViewHolder(focused);
  if (vh != null) { onTileFocused(vh.getBindingAdapterPosition(), focused); return; }` — существующая ветка
  `!glassShown` сама поставит стекло на место и проявит его за 180 мс; (4) `android:focusedByDefault="true"` на
  RecyclerView, чтобы и на холодном старте первый кадр не показывал белую пилюлю; (5) после пункта «reload не в
  onResume» лишних rebind'ов не будет вовсе.

**P1 · M — В сетке сфокусированная строка всегда припаркована к низу экрана: следующая строка не видна**

- Сейчас: `paddingTop = h − dockRow − 30dp` (`MainActivity.java:117-124`), `paddingBottom` 40 dp; ветка сетки
  вызывает `grid.requestChildRectangleOnScreen(item, want, false)` (`:244-247`), а `getChildRectangleOnScreenScrollAmount`
  считает «видимой областью» полосу между padding'ами (~153 dp при высоте строки 146 dp). Вниз листаешь вслепую:
  что в следующей строке — видно только после входа в неё. **[проверить при 960×540 dp]**
- Предложение: явная политика якоря. В ветке сетки `int dy = item.getTop() − anchorY; if (|dy| > 1)
  grid.smoothScrollBy(0, dy)` с `anchorY` ≈ высота RecyclerView минус две строки; `paddingBottom` поднять до
  ~170 dp, чтобы последние строки могли дойти до якоря (парковка дока зависит только от `paddingTop`, она не
  сдвинется). Подкласс `GridLayoutManager` с `onRequestChildFocus(...) → true`, чтобы RecyclerView не выдавал
  собственный скролл первым. Заодно: держать `glassTargetY` в координатах контента и компенсировать скролл в
  `onScrolled` без `animateToFinalPosition`, тогда стекло не гонится за движущейся целью (сейчас пружина 0.58
  догоняет цель, которая едет весь скролл). Делать одним изменением, проверять ощущение на устройстве.

**P2 · S — Автоповтор клавиши**

- Сейчас: `AppAdapter.java:252` принимает любой ACTION_DOWN без проверки `getRepeatCount()`; каждый повтор
  (~50 мс) перезапускает smooth-scroll. На стике после дебло́ута сетка — 2–5 строк, окно дрожания короткое.
- Предложение: `if (repeatCount > 0 && grid.getScrollState() != SCROLL_STATE_IDLE) return true;` — удержание
  даёт одну строку на завершённый скролл (~3–4 строки/с), детерминированно. Без «мгновенного режима» и
  постраничных прыжков. Ошибочная гипотеза из разбора: `withEndAction` у ViewPropertyAnimator **не** срабатывает
  при отмене (`onAnimationCancel` снимает действие), так что «устаревшее имя героя» при быстром пролистывании не
  воспроизводится.

**P2 · S — Запасной путь `focusTile` не срабатывает**

- Сейчас: если холдера ещё нет (`findViewHolderForAdapterPosition` возвращает null всё время между
  `notifyDataSetChanged` и следующим layout), вешается OnScrollListener в ожидании SCROLL_STATE_IDLE
  (`MainActivity.java:331-341`). Если цель уже на экране, `smoothScrollToPosition` не создаёт Action, состояние
  скролла не меняется, слушатель висит навсегда и выстрелит при следующем постороннем скролле; запасной
  `rv.getChildAt(0)` (`:337`) фокусирует произвольную плитку. Сегодняшние вызовы в это окно не попадают, но новые
  (Home/Back → dock) могут.
- Предложение: подкласс `LinearSmoothScroller` с `onStop()`, который делает `requestFocus` на целевой карточке;
  запускать через `layoutManager.startSmoothScroll(s)`; `getChildAt(0)` убрать; вызовы до первого layout после
  reload оборачивать в `grid.post`.

**P2 · S — Индекс `lastFocused` ломается после установки/удаления**

- Сейчас: `lastFocused` — позиция в адаптере (`MainActivity.java:72`, `:206`, `:187`, clamp на `:324`); после
  удаления приложения через App info фокус возвращается на соседа по алфавиту с героем и стеклом.
- Предложение: хранить `ComponentName lastFocusedComponent` и `lastDockComponent`, после реального reload брать
  `indexOf(component)`, иначе clamp. Без сохранения в SharedPreferences: холодный старт пусть открывается на доке
  (это согласуется с решением «Home = дока»).

**P3 · S — Popup Inputs переживает onPause/onNewIntent**

- Сейчас: PopupWindow — локальная переменная (`MainActivity.java:399`), ничего не закрывает его при Home.
  На стике он не откроется вовсе, но для телевизоров и для корректности Home/Back: сделать полем и закрывать в
  `onPause()`, `onNewIntent()`, `onBackPressed()`.

### 4.3 Специфика стика и Android TV 11

**P0 · S — Пилюля Inputs мертва на донгле**

- Сейчас: `showInputs()` (`MainActivity.java:392-449`) оставляет только `isPassthroughInput()` (`:416`), затем
  `live_tv_package` = `com.mediatek.wwtv.tvcenter` (`config.xml:23`, приложение MediaTek-телевизора, которого на
  Amlogic-донгле нет) → тост `No inputs found` (`:435-438`). Это первая пилюля в панели, естественная цель Up с
  доковых плиток 0–2. Граница фокуса слева привязана к `R.id.btnInputs` (`:155`): если пилюлю просто скрыть,
  Left с Wi-Fi провалится в FocusFinder.
- Предложение: в `onCreate`/`onResume` `boolean has = anyPassthrough(tim.getTvInputList()) ||
  getLeanbackLaunchIntentForPackage(liveTvPkg) != null; btnInputs.setVisibility(has ? VISIBLE : GONE)`;
  граничные проверки в `pillKeys` — по первому/последнему **видимому** ребёнку `topButtons`. Если Live TV есть, а
  HDMI нет — подписывать пилюлю «Live TV», не «Inputs». Пилюлю-замену (Sleep, Apps, Storage) не добавлять:
  две пилюли на стике — нормально. `TvInputCallback` не нужен: passthrough-вход на донгле не появится.

**P0 · S — config.xml — профиль Mi TV 4A, и дока молча «добирается» случайным приложением**

- Сейчас: дока Netflix / Prime / YouTube / `in.startv.hotstar` (`config.xml:9-14`), скрытые — пакеты MediaTek
  (`:17-20`), Live TV — MediaTek (`:23`). Комментарий «Missing apps are skipped» (`:8`) вводит в заблуждение:
  сортировка ставит избранные первыми, остальные по алфавиту (`AppAdapter.java:101-109`), а «дока» — это просто
  `position < COLUMNS` (`AppAdapter.java:126`, `MainActivity.java:237`, `:311`, `DockDecoration.java:34,46`).
  Без Hotstar четвёртым в доке окажется, например, «Amazon Music» — без подписи, с героем и ambient.
- Предложение, сейчас: `examples/xiaomi-tv-stick-4k/launcher-config.xml` (дока 3–4 реальных приложения,
  `live_tv_package` пустой, `hidden_apps` пустой), тракуемый `config.xml` привести к нейтральным значениям,
  специфику 4A перенести в `examples/xiaomi-mi-tv-4a/launcher-config.xml`; `build-and-install.sh` принимает
  `LAUNCHER_CONFIG=<путь>`, копирует поверх и возвращает файл через `git checkout --` в `trap`. Flavors Gradle не
  нужны. Скрипт перед сборкой проверяет `pm path <pkg>` для каждого `dock_apps` и предупреждает о
  недостающих. Исправить комментарий на строке 8.
- Предложение, вместе с редактированием дока с пульта: `dockCount = min(columns, найденных избранных)`; при
  `dockCount < columns` добавлять нефокусируемые элементы-заглушки типа SPACER, чтобы первая строка по-прежнему
  содержала ровно `columns` ячеек и все проверки `< COLUMNS` остались рабочими; полосу дока рисовать по
  реальным плиткам. Up из четвёртого столбца тогда клампить на последний реальный док-элемент.

**P0 · M — Краш-цикл без пути восстановления и обновление до Android 14**

- Сейчас: `build-and-install.sh:19-20` ставит наш HOME и сразу отключает стоковый. На TV нет диалогов об
  ошибках; HOME-activity при падении не финишируется, а перезапускается (ActivityStack «Home activities should
  not be force-finished»), флаг bad-process сбрасывается при следующем запуске HOME. Результат падения в
  `onCreate`/`onResume` — чёрный мигающий экран без выхода с пульта. Уточнение: `loadBanner`/`loadIcon` сами
  глотают исключения ресурсов (ApplicationPackageManager.getDrawable), поэтому реальные незащищённые места —
  `vividColor` (`AppAdapter.java:184-224`: `d.draw(c)` чужого Drawable без catch, использование «живого»
  drawable при null ConstantState) и всё внутри `onTileFocused`/`showHero`.
- Предложение, обязательно: try/catch(Throwable) вокруг блока по одному приложению в `reload()` (пропустить,
  `Log.w`), вокруг всего `reload()` в точке вызова (оставить прежний список + тост), вокруг `vividColor`
  (вернуть нейтральный `0xFF444444`). Установка в два этапа: сначала `pm install` отдельным вызовом с проверкой
  `Success`, `set-home-activity`, затем `cmd package resolve-activity --brief -a android.intent.action.MAIN -c
  android.intent.category.HOME` должен вернуть `com.gathin.tvhome/.MainActivity`, и только тогда
  `pm disable-user` стокового (по умолчанию отключать `com.google.android.tvrecommendations`, стоковый HOME —
  по явному флагу `--disable-stock` или автоматически после переживания перезагрузки). Пока стоковый HOME просто
  включён, но не выбран, Android при краше нашего лаунчера сбросит preferred-activity и вернётся к нему — это
  бесплатная страховка ценой ~десятков МБ RSS простаивающего процесса.
- Предложение, желательно: раздел README «Stick 4K»: держать USB debugging включённым и Wi-Fi сохранённым;
  резервный ADB по micro-USB через Y/OTG-кабель с питанием (порт — настоящий USB gadget: загруженный стик
  определяется как VID 18D1 PID 4EE1, MTP; при включённой отладке должен появиться 4EE2 MTP+ADB
  **[проверить]**); не питать стик от USB телевизора во время OTA. Расширить `tv.py --probe` (см. 4.8), чтобы
  diff «до/после OTA» был тривиален. Safe-mode с `UncaughtExceptionHandler` и счётчиком падений — опционально,
  когда per-app catch уже есть.
- Не делать: ветвить скрипт под Android 14 / Google TV (`launcherx` + `setupwraith`) заранее. Единственный
  источник про OTA — сторонний гайд, и он же сообщает, что после 14 загружается Android TV Home. Проверка HOME
  после установки покрывает любую версию.

**P1 · S — Детектировать стоковый HOME, а не предполагать**

- Сейчас: `STOCK_LAUNCHER` по умолчанию `com.google.android.tvlauncher` (`build-and-install.sh:6`), отключается
  только он; `tv.py --batch` (`tools/tv.py:57-67`) не проверяет вывод команд, поэтому `set -e` не срабатывает и
  «Installed» печатается всегда. На Android TV 11 пакет верный, но `com.google.android.tvrecommendations`
  (агрегация Discover/Play Next, память на 2 ГБ) остаётся работать, хотя список 4A его отключает.
- Предложение: перед изменениями печатать `cmd package query-activities --brief -a android.intent.action.MAIN -c
  android.intent.category.HOME`; отключать `STOCK_LAUNCHER` + `tvrecommendations` (opt-out переменной);
  после `set-home-activity` проверять resolve-activity и выходить с ненулевым кодом и подсказкой restore;
  `--strict` у `tv.py --batch` (ненулевой выход при `Failure|Error`). `cmd role add-role-holder
  android.app.role.HOME` — только после проверки, что команда есть на этой сборке.

**P1 · S — Wi-Fi: имя сети зависит от переключателя Location**

- Сейчас: `wm.getConnectionInfo().getSSID()` (`MainActivity.java:369-375`), фильтр `unknown` (`:374`);
  ACCESS_FINE_LOCATION объявлен (`AndroidManifest.xml:10`) и выдаётся скриптом (`build-and-install.sh:18`), но
  без включённого Location пилюля навсегда покажет «Wi-Fi». Включён ли Location на стике по умолчанию —
  неизвестно.
- Предложение: событийная модель (см. 4.1), SSID оставить как best effort, иначе «Wi-Fi» / «Ethernet» /
  «Offline»; в README одна строка, что имя сети требует Location. Рисовать «палочки» уровня сигнала не стоит
  (`getRssi` на 11 тоже location-gated, `NetworkCapabilities.getSignalStrength` даёт мало). `btnWifi`: `maxWidth`
  160 dp + `ellipsize end`, иначе длинный SSID перекроет блок часов и обрежет Settings (последний ребёнок).

**P1 · S — `<queries>` сейчас, targetSdk — отдельно**

- Сейчас: `targetSdk 29` (`app/build.gradle:12`), `<queries>` нет; `queryIntentActivities` (`AppAdapter.java:88-89`)
  и `getLeanbackLaunchIntentForPackage` (`MainActivity.java:430`) попадут под фильтрацию при targetSdk ≥ 30, и
  сетка опустеет молча. `TvInputManager.getTvInputList` на Android 11 не фильтруется.
- Предложение: добавить блок `<queries>` с intent'ами MAIN+LEANBACK_LAUNCHER, MAIN+LAUNCHER и
  `android.media.tv.TvInputService` уже сейчас (на 29 он инертен). targetSdk поднимать отдельным проверенным
  коммитом; `onBackPressed` продолжает работать на target 33/34, пока не включён `enableOnBackInvokedCallback`.
  Плюс пустое состояние «Приложения не найдены» при нулевом списке (см. 4.6).

**P2 · S — Остальное**

- Overscan: верхний отступ 26 dp при рекомендуемых 27–28 (`activity_main.xml:36`); поднять до 28 dp. Боковые 56 dp
  уже с запасом. Литерал `(145 + 6 + 12) * density` (`MainActivity.java:119`) расходится с реальными 146 dp
  (`item_app.xml`: 8 + 108 + 22 + 8); заменить на `lm.getDecoratedMeasuredHeight(grid.getChildAt(0))`, тогда
  padding никогда не разойдётся с layout'ом. Ресурсы `values-sw720dp` — только если `wm density` покажет 240.
- Intent'ы настроек: AOSP TvSettings 11 экспортирует SETTINGS, WIFI_SETTINGS, INTERNAL_STORAGE_SETTINGS,
  DEVICE_INFO_SETTINGS, LOCATION_SOURCE_SETTINGS; **не** объявляет BLUETOOTH_SETTINGS, DREAM_SETTINGS,
  CAST_SETTINGS. Для Bluetooth: `com.google.android.intent.action.CONNECT_INPUT` (AddAccessoryActivity), затем
  явный компонент `com.android.tv.settings/.accessories.BluetoothAccessoryActivity`. Любая новая пилюля или
  строка меню — только после `pm.resolveActivity(...) != null`, иначе GONE. **[проверить оверлеи Xiaomi]**
- Дебло́ут-лист стика: создать `examples/xiaomi-tv-stick-4k/debloat.txt` только из реального дампа
  (`pm list packages -f | grep -iE 'xiaomi|mitv|miui|patchwall'`), кандидаты из 4A — PatchWall
  (`com.mitv.tvhome.atv`), Xiaomi TV+ (`com.mitv.tvhome.mitvplus`), аналитика (`com.miui.tv.analytics`,
  `com.xiaomi.statistic`), smartshare, milink, tvmanager. Не отключать OTA-сервис и `com.google.android.katniss`,
  `com.google.android.apps.mediashell` (Cast), `com.android.providers.tv`, `com.google.android.backdrop`.
  Правило CONTRIBUTING «только то, с чем прожили несколько дней» — остаётся.
- Пульт: на устройстве снять `getevent -l` для микрофона, Netflix, Prime и регионального PatchWall/TV+-ключа и
  записать в пример. Переназначать ничего не нужно: ASSIST/SEARCH уже работают через систему.
- Имя устройства (`Settings.Global.DEVICE_NAME`, без разрешений) в приветствии — только если пользователь
  переименовал стик; по умолчанию не показывать.

### 4.4 Визуальное обновление

Ограничения: RenderEffect-блюра на API 30 нет; Mali-G31 MP2 на 1080p даёт реалистично 6–8 полноэкранных
проходов на кадр; все предложения ниже укладываются в бюджет и не добавляют битмапов, кроме опциональных обоев.

**P1 · S — Моушн: пружины слишком «плавают»**

- Сейчас: stiffness 260 / damping 0.58 (`MainActivity.java:141`): ω≈16 рад/с, первое прибытие ~0,24 с,
  овершут 10,7 %, установление в 2 % ~0,43 с. Масштаб плитки 260 мс с `OvershootInterpolator(1.6)`
  (`AppAdapter.java:259-263`), имя 220 мс. При автоповторе 50 мс стекло хронически отстаёт.
- Предложение: стекло — stiffness ~650, damping 0.75 (установление ~0,2 с, овершут ~3 %, «жидкость» остаётся).
  Масштаб — две `SpringAnimation(SCALE_X/SCALE_Y)` в холдере (stiffness ~800, damping 0.7, ретаргетятся на лету),
  либо 150 мс с `OvershootInterpolator(1.0)` и 120 мс линейно на расфокус. Имя 160 мс. Герой/ambient оставить
  медленным слоем. Ручки в config.xml не выносить. **[оценить ощущение на устройстве]**

**P1 · S — Типографика для 3 метров**

- Сейчас: приветствие 12 sp wght 500 alpha 0x8C с трекингом 0.22 (`activity_main.xml:54-59`), дата 14 sp,
  подпись иконочной плитки 13 sp (`item_app.xml:47`), имя плитки 14 sp (`:64`), статус в popup 11 sp. На 55" с 3 м
  12 sp — ~12 угловых минут, на грани; Amazon даёт 28 px при 1080p (= 14 sp) как минимум, M3 bodyMedium — 14 sp.
- Предложение одним проходом: всё ≤ 16 sp → wght 500 и alpha ≥ 0xA6; приветствие 13 sp / трекинг 0.18; дата 15 sp;
  подпись иконочной плитки 15 sp; имя плитки 15 sp. Часы 52 / герой 48 на 300 не трогать. Высоту пилюль и
  формулу padding дока не менять.

**P1 · S — Подсветка на белых баннерах**

- Сейчас: белая заливка, белый ободок 0xCC 1,5 dp (`GlassView.java:33-34, 46-47`) лежит ровно на краю
  отмасштабированной плитки (`MainActivity.java:210-212` + inset `GlassView.java:52`): на Netflix/YouTube ободок
  исчезает, остаются масштаб, слабое свечение 0x30 и имя. Material для TV использует 3 dp рамку именно поэтому.
- Предложение: одна дополнительная окружность 1 dp 0x4D000000 на 1,5 dp внутрь от белого ободка (на тёмных
  плитках невидима), ободок 0xE0 и 2 dp, внешнее свечение до 0x40. Без выборки яркости и тонированной заливки.
  Заодно убрать `clipPath` у блика: `drawRoundRect(rect, radius, radius, specular)` с CLAMP-градиентом до 0x00 на
  45 % высоты даёт тот же результат меньшим числом операций (`GlassView.java:65-70`).

**P2 · S — Мелочи с нулевой стоимостью**

- Радиус полосы дока 26 dp → 30 dp (16 dp плитка + 14 dp отступ, `DockDecoration.java:61-63`). Пилюли оставить
  капсулами.
- Полоса дока `0xFF141416` → `0xE6141416`: ambient под ней просвечивает на 2–3 % и следует существующим фейдам
  без нового аниматора (геометрия: центр градиента 0.42 h, радиус 0.62 w — полоса не на «неосвещённой» части,
  она просто непрозрачно перекрывает свечение).
- Клипы HSV в `vividColor` (S ≤ 0.9, V ≤ 0.85) и, посмотрев в тёмной комнате, внутренняя точка ambient 0x5C → 0x4D.
- Пилюли: `enterFadeDuration`/`exitFadeDuration` 120 мс в `pill_bg.xml` и `input_row_bg.xml`, StateListAnimator
  масштаб 1.06 на фокусе — единый язык движения с плитками.
- Плитки без баннера (`AppAdapter.java:135-141`): вместо плоского `#1C1C1E` — кэшированный в `Entry`
  GradientDrawable TL→BR из `vividColor` (HSV(s·0.85, v 0.40) → HSV(s, v 0.20)), иконка 56 dp, подпись 15 sp
  wght 500; при `bestScore < 0.25` нейтральный `#2A2A2E → #17171A`. На стике таких плиток будет меньшинство
  (Leanback-приложения из Play обязаны иметь баннер 320×180), поэтому не P1.
- Точка «новое» 10 dp в углу плитки, если `firstInstallTime` < 48 ч и пакет ещё не запускался (prefs); без
  автоскролла и автофокуса.

**P2 · M — Обои (опционально, конфликтует с «OLED-чёрной» идентичностью)**

- README (`:88`) и PROMPT (`:61-62`) обещают смену обоев, но лаунчер их не рисует; `WallpaperManager` на
  Android TV не реализован. Заявление «сторонние лаунчеры берут деньги за обои» верно для Projectivy Premium и
  ATV Launcher Pro, неверно для FLauncher (GPL).
- Если делать: флаг `wallpaper_enabled`; файл `getExternalFilesDir()/wallpaper.jpg` (без разрешений, кладётся
  `tools/tv.py --push`); декод один раз в фоне с `inSampleSize` до ~480 px, два прохода `createScaledBitmap`
  вниз/вверх (бесплатный блюр, одноразовый, правило «no blur» про покадровую стоимость); ImageView centerCrop
  под скримом 0x99→0xD9; в режиме обоев ambient на 40 % слабее, полоса дока 0xE6. Без SAF-пикера и MediaStore
  (на Xiaomi пикер может быть заглушкой). Минимум сейчас: поправить формулировки README/PROMPT.
- Не делать: «уголь» вместо чёрного для LCD, дневной вариант, idle-dim внутри MainActivity (системный
  скринсейвер и таймер сна это уже делают).

**P3 · M — Размер плитки от числа колонок**

- `columns` настраиваемый (`config.xml:26`), а плитка фиксирована 180×101 dp: при 5 колонках ячейка 169,6 dp < 180,
  плитки перекроются. Либо убрать ручку (hard-code 4 с комментарием: PROMPT.md задаёт 4-колоночную сетку), либо
  считать ширину в `onCreateViewHolder` из ширины родителя. Аргумент «пиксель-в-пиксель 160×90» не работает:
  плитка всё равно масштабируется 1.10 и 1080p-кадр растягивается до 4K.

### 4.5 Удобство ежедневного использования

**P1 · M — Редактирование дока и скрытых приложений с пульта**

- Сейчас: `dock_apps`/`hidden_apps` читаются из ресурсов один раз (`AppAdapter.java:82-83`); долгое нажатие —
  только App info (`:144-152`); `config.xml:3`: «Edit this file, then run build-and-install.sh». CONTRIBUTING
  (`:41-42`) сам называет «settings screen to edit the dock from the TV» желанной идеей.
- Предложение, минимум (S→M): класс `LauncherPrefs` (SharedPreferences): `dock` (упорядоченный список),
  `hidden` (StringSet); геттеры падают на `R.array.*`, если ключа нет — config.xml остаётся дефолтом устройства,
  «Сбросить» просто чистит prefs. Долгое нажатие → стеклянный popup у плитки (переиспользовать `popup_inputs.xml`
  / `item_input.xml`, код `showInputs()`): Открыть; В док / Из дока; Влево / Вправо (только для дока, `Collections.swap`
  + `notifyItemMoved`, popup остаётся открытым, без модального режима и перехвата клавиш); Скрыть; Удалить
  (только для не-системных: хранить `ApplicationInfo.flags` в `Entry`, `Intent.ACTION_DELETE` с `package:`-URI и
  `EXTRA_RETURN_RESULT` через Activity, в манифест `<uses-permission REQUEST_DELETE_PACKAGES>` — для targetSdk ≥ 28
  без него intent молча не сработает); О приложении. Если док полон — строка «Дока заполнена, сначала открепите».
  Нажатие OK: scale 0.96 как отклик. Требует заглушек дока из 4.3. Перетаскивание — потом.
- ADB-канал для скриптов и промптов: `<receiver android:exported="true"
  android:permission="android.permission.DUMP">` на `am broadcast -a com.gathin.tvhome.CONFIG --esa dock a,b,c,d
  --esa hidden x,y` (DUMP — development-разрешение, которое есть у adb shell и нельзя получить сторонним
  приложениям). Вариант с `/sdcard/tvhome.json` не годится из-за scoped storage.
- Первый запуск: вместо мастера — одна исчезающая стеклянная строка над доком «Удерживайте OK на приложении,
  чтобы закрепить его в доке или скрыть», прячется после первого долгого нажатия.

**P1 · S — Выпадающее меню у пилюли Settings**

- Сейчас: `btnSettings` открывает корень Settings (`MainActivity.java:147-148`). Повторное сопряжение потерянного
  пульта, освобождение 8 ГБ, скринсейвер — по шесть уровней меню.
- Предложение: OK на Settings открывает тот же стеклянный dropdown с предвыбранной первой строкой «Все
  настройки» (текущее поведение +1 нажатие); Down с пилюли по-прежнему в сетку. Строки (каждая только если
  `resolveActivity() != null`): раздел Лаунчер — Скринсейвер, Показать скрытые, Сбросить настройки, О лаунчере
  (версия + SHA, модель, Android, свободно памяти/накопителя — ~40 строк); раздел Система — Сопряжение пульта
  (`CONNECT_INPUT`), Bluetooth-устройства, Накопитель, Приложения, Play Store
  (`getLeanbackLaunchIntentForPackage("com.android.vending")`), Все настройки. Шторки уведомлений на Android TV 11
  нет (SystemUI не экспортирует панель), значки обновлений Play недоступны — не имитировать.

**P1 · S — Обновление списка по событиям установки/удаления**

- Сейчас: список обновляется только в `onResume`; установка из Play, завершившаяся после нажатия Home, или
  `pm install`/`pm disable-user` при открытом лаунчере невидимы; удалённое приложение оставляет плитку с тостом
  «Can't open».
- Предложение: `LauncherApps.registerCallback` (достаточно одного механизма) → `reload` в фоне, затем
  `focusTile(lastFocused)` без анимации; при реальном удалении чистить пакет из dock/hidden prefs.

**P2 · S — Скринсейвер-часы как DreamService**

- Стоковый — «Backdrop» (лента фото Google), после дебло́ута может исчезнуть. `AmbientDream extends DreamService`
  в том же APK: чёрный фон, тонкие часы и дата теми же стилями, дрейф позиции раз в 30–60 с против выгорания,
  слабое свечение последнего цвета героя из prefs; `setInteractive(false)`, `setScreenBright(false)`. Активация —
  одна строка «Скринсейвер» в меню Settings, открывающая `com.android.tv.settings/.device.display.daydream.DaydreamActivity`
  (на Android TV 11 пикер перечисляет все установленные dreams; `BIND_DREAM_SERVICE` ограничивает только того,
  кто биндит). WRITE_SECURE_SETTINGS и свой idle-оверлей — только если устройство окажется на 14 и пикера нет.

**P2 · S — Показывать «телефонные» приложения**

- Сейчас: только `CATEGORY_LEANBACK_LAUNCHER` (`AppAdapter.java:88-89`); sideload-APK без Leanback-activity
  ставится и никогда не появляется, владельцы стиков sideload'ят чаще. Переключатель «Показывать не-TV
  приложения» (по умолчанию выкл.): объединение LEANBACK_LAUNCHER и LAUNCHER, дедуп по пакету с приоритетом
  leanback, запуск с `CATEGORY_LAUNCHER`, рендер существующей иконочной плиткой.

**P2 · M — «Продолжить просмотр», только после пробы на устройстве**

- Механика проверена по исходникам: `READ_TV_LISTINGS` (dangerous) → `pm grant` или runtime-диалог; чтение
  `TvContract.WatchNextPrograms.CONTENT_URI` с пустым selection (непустой бросает SecurityException), сортировка на
  клиенте по `last_engagement_time_utc_millis`, фильтр `browsable == 1 && intent_uri != null`. Но по отчётам
  пользователей Netflix на Android TV строк Watch Next не публикует (Prime, YouTube, Apple TV+ — публикуют), так
  что главный сценарий «продолжить Netflix одним нажатием» может не сработать.
- Решение: 10-минутная проба до любого UI: `pm grant com.gathin.tvhome android.permission.READ_TV_LISTINGS`, затем
  `content query --uri content://android.media.tv/watch_next_program --projection package_name:title:last_engagement_time_utc_millis`.
  Если публикуют только YouTube/Prime — не строить (они и так в доке). Если строить: opt-in в меню Settings, одна
  горизонтальная строка над доком, существующая только когда непуста, без скачивания постеров и без INTERNET
  (заголовок + серия + баннер приложения + тонкий прогресс из `last_playback_position/duration`); Up с дока идёт
  в неё, если она есть.

**P3 · S — Порядок «недавние»**

- Полную пересортировку сетки по частоте не делать: ломает пространственную память, ради которой и существует
  алфавитная D-pad-сетка. Если нужно — опциональная первая строка сетки из `COLUMNS` последних запущенных не-доковых
  приложений (`prefs.putLong("last."+pkg)` в `launch()`), пересчёт только при reload. Без UsageStatsManager и
  appops. Предпосылка — меню Settings для переключателя.

**P3 · S — Пилюля «Поиск»**

- Кнопка-микрофон пульта уже открывает ассистента при любом лаунчере (см. раздел 2). Пилюля нужна только для
  обнаруживаемости: `startActivity(new Intent(Intent.ACTION_ASSIST).addFlags(NEW_TASK))` под `resolveActivity`,
  запасной `RecognizerIntent.ACTION_WEB_SEARCH`; GONE, если ничего не резолвится. `onSearchRequested` и
  `KEYCODE_SEARCH` не переопределять: системный путь уже правильный, переопределение может только сломать.

### 4.6 Отказоустойчивость

- **P0 · S** — Защита `reload()`/`vividColor` и двухэтапная установка: см. 4.3.
- **P1 · S** — Пустой список: сейчас `focusTile` молча выходит при `getItemCount() == 0` (`MainActivity.java:323`),
  фокус нигде, экран чёрный с одной панелью. Добавить скрытый TextView «Приложения не найдены. Нажмите OK, чтобы
  открыть Настройки → Приложения», фокус на `btnSettings`, `Log.i` с сырым числом из `queryIntentActivities`.
  Первая загрузка до восстановления Play, кривой `hidden_apps` и будущий targetSdk-случай перестают выглядеть как
  «сломалось».
- **P1 · S** — Логирование: в модуле ни одного `Log.*`; три широких `catch (Exception)` (`MainActivity.java:377`,
  `AppAdapter.java:149, 162`). Сузить до `ActivityNotFoundException | SecurityException` + `Log.w("TvHome", ...)`;
  в `updateWifiLabel` один раз залогировать, что именно блокирует SSID (разрешение или Location). Без BuildConfig
  и хелперов.
- **P2 · S** — Доступность: у `card` нет `contentDescription`, у баннера `@null`, `name` — сосед, а не потомок
  (`item_app.xml`). TalkBack читает каждую плитку как «кнопка без названия». `h.card.setContentDescription(e.label)`
  в bind; `importantForAccessibility="no"` на `ambient` и `heroName`.
- **P2 · S** — Смена режима дисплея без пересоздания (`configChanges` содержит `screenSize`): `density`, координаты
  стекла и Y героя не пересчитываются до следующего движения фокуса. В layout-листенере сетки при реальном
  изменении размера: `hideGlass()/hideHero()` и `post(onTileFocused(pos, card))`, если плитка в фокусе.
  **[проверить переключением режимов HDMI]**
- **P3** — `tim.getInputState(id)`: на 11 для неизвестного id возвращает DISCONNECTED с предупреждением, а не бросает
  (не уверен на 100 %; на стике popup не открывается, так что защищать не обязательно).

### 4.7 Русский язык и типографика

**P0 · S — В Outfit нет кириллицы** (проверено локально: `res/font/outfit.ttf`, 416 глифов, 360 кодпоинтов,
0 в диапазоне U+0400–U+04FF, одна ось wght 100–900)

- Эффект: дата «СУББОТА 18 ОКТЯБРЯ», герой «Кинопоиск», «Иви», «Okko», подписи плиток рисуются системным
  Roboto; `fontVariationSettings 'wght' 300` на запасной шрифт не переносится, поэтому 48 sp герой на глазах
  меняет начертание между Netflix и Кинопоиском. Приветствие остаётся английским.
- Предложение, предпочтительное: заменить Outfit на геометрический OFL-шрифт с кириллицей и переменной толщиной —
  **Manrope** (ближе всего по характеру), **Golos Text**, **Onest** или Inter; сабсет Latin + Cyrillic + цифры через
  `pyftsubset` (~110–130 КБ, как сейчас). XML не меняется. Альтернатива — оставить Outfit для латиницы и добавить
  кириллический сиблинг через `Typeface.CustomFallbackBuilder` (API 29+), но это код на каждый TextView.
  Обновить `FONT-LICENSE-OFL.txt`. Для даты в кириллице `textAllCaps` с трекингом 0.14 читается хуже — сравнить
  скриншотом.

**P1 · S — Строки и дата**

- Сейчас: `"Not available on this TV"` (`MainActivity.java:387`), `"HDMI " + label` (`:420`), `"Can't open " +
  e.label` (`AppAdapter.java:163`); только `res/values`; `choose_input` и `standby` в `strings.xml` не используются;
  шаблон даты `EEEE  d MMMM` общий для 12/24 ч (`activity_main.xml:111-112`).
- Предложение: вынести три строки в `strings.xml` с `%1$s`; добавить `res/values-ru/strings.xml` (приветствия,
  Inputs/Wi-Fi/Settings, Live TV, Connected/No device, Offline/Ethernet, cant_open, not_available, no_apps);
  шаблон даты сделать строковым ресурсом (`@string/date_pattern`) с переопределением только там, где порядок
  другой (`values-en-rUS`: `EEEE  MMMM d`) — для ru текущий шаблон уже даёт «суббота 18 октября». Удалить
  неиспользуемые строки; `label.toUpperCase()` без Locale на `:420` → `toUpperCase(Locale.ROOT)`.

### 4.8 Сборка, установка, инструменты

**P0 · S — `build-and-install.sh` работает только на macOS с Homebrew и может оставить стик без лаунчера**

- Сейчас: `:7-8` безусловно экспортируют `/opt/homebrew/...` (на Linux/WSL gradlew падает «JAVA_HOME is set to an
  invalid directory», хотя `java` в PATH); `:10` пишет `sdk.dir` в `local.properties` один раз — ошибка первого
  запуска «залипает»; `:15-21` ставят APK, выдают разрешение, назначают HOME и отключают стоковый одним
  `--batch`, который не проверяет вывод: при провале `pm install` стоковый всё равно отключится. Debug-ключ —
  `~/.android/debug.keystore` конкретной машины: сборка с другого ноутбука даст `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
- Предложение: `[ -d "$d" ] && export JAVA_HOME="$d"` для обоих путей; не писать `local.properties` без
  `ANDROID_HOME`; `pm install -r` отдельным вызовом с `grep -q Success || exit 1`, при `UPDATE_INCOMPATIBLE`
  печатать точный откат и останавливаться; только после успеха `set-home-activity`, проверка resolve-activity,
  `pm disable-user`; печатать команду восстановления. Если есть рабочий `adb` и `adb connect <ip>:5555` отвечает
  `connected` — использовать `adb install -r` (Linux/Windows не имеют macOS-блокировки Local Network; b64push с
  48 КБ-чанками остаётся запасным путём). Стабильность подписи: закоммитить `launcher/debug.keystore` со
  стандартными `androiddebugkey`/`android` и указать его в `signingConfigs.debug.storeFile`, чтобы каждый клон
  давал одну подпись; release использовать ту же подпись. Полный pipeline с секретами — не сейчас.

**P1 · S — Release + R8 + `pm compile`**: см. 4.1. Ожидаемо APK 1,6–2,2 МБ → 350–500 КБ (оценка, собрать в
песочнице не удалось: `dl.google.com` закрыт); измеримый выигрыш — в скорости b64-пуша и отсутствии debuggable,
а не в размере на 8 ГБ.

**P1 · S — `tv.py --probe` расширить** до `ro.board.platform`, `ro.build.display.id`, `wm size`, `wm density`,
`/proc/meminfo`, `settings get global device_name`, resolve-activity для HOME, `pm list packages -d`, с
`--save examples/<device>/probe.txt`. Это нужно и для заголовка примера по CONTRIBUTING, и для диффа до/после OTA.
Плюс `tools/health.sh` (`dumpsys meminfo/gfxinfo framestats`, `df /data`, `top -n 1`, `getprop ro.build.*`).

**P2 · S — Гигиена Gradle и зависимостей**: `dynamicanimation 1.0.0` тянет весь `androidx.legacy:legacy-support-core-utils`
(loader, documentfile, localbroadcastmanager, print, lifecycle); `1.1.0` зависит только от core/collection — одна
строка. RecyclerView оставить 1.3.2 (1.4.0 требует compileSdk 35 / AGP 8.6). В `gradle.properties` добавить
`org.gradle.caching=true`, `org.gradle.configuration-cache=true`, `org.gradle.parallel=true`; `-q` заменить на
`--console=plain`, чтобы видеть предупреждения. Version catalog для двух зависимостей не нужен.

**P2 · M — CI и тесты**: один workflow на push/PR (`setup-java 17` + `setup-android` + `./gradlew lint
testDebugUnitTest assembleDebug`, APK как артефакт, без секретов). Подписанный релиз по тегам — только после того,
как док станет редактируемым с пульта (иначе готовый APK бесполезен). Тесты: JUnit 4 для вынесенных чистых
функций `HomeOrder.arrange` (дока/алфавит/dockCount), `DpadMath.target`, `Greeting.forHour`, очистки SSID;
`lint { abortOnError true; baseline }`. Espresso на TV-эмуляторе в CI — нет: 10+ минут и флаки ради четырёх
нажатий, которые CONTRIBUTING всё равно требует проверять на реальном TV; вместо этого `tools/smoke.sh`, который
инжектит DOWN/UP/LEFT/RIGHT с каждого края и снимает скриншоты.

**P2 · M — Структура кода, только по необходимости**: не рефакторить ради рефакторинга (484-строчный Activity тут
нормален). Выделить ровно три части, когда их пункты реализуются: `AppRepository` (фоновая загрузка +
LauncherApps-callback), `InputsMenu` (`MainActivity.java:392-483`, нужен для проверки возможностей донгла) и
чистую пару `HomeOrder`/`DpadMath` для JVM-тестов. `static int COLUMNS` уйдёт побочным эффектом. Java 17 + Views
остаются: Leanback официально deprecated, Compose for TV стоит мегабайтов и сотен мс холодного старта на A35,
Kotlin ничего не добавляет при одном Executor. Записать это решение в CONTRIBUTING, чтобы не пересматривать в
каждом PR.

---

## 5. Что делать не стоит (и почему)

| Идея | Почему нет |
|------|------------|
| Категории/секции в сетке | `ApplicationInfo.category` почти никто на TV не задаёт, всё упадёт в «Прочее»; заголовки ломают арифметику `pos ± COLUMNS`. Скрытие ненужного решает проблему без нового layout'а |
| Память столбца при Down в короткую последнюю строку; память «какая пилюля» при Up | Текущее поведение — платформенная конвенция (Leanback, tvOS); скрытое состояние делает D-pad менее предсказуемым |
| Таймер сна через device admin | `lockNow()` молча ничего не делает без `android.software.device_admin` (Android TV его обычно не объявляет), admin блокирует `pm uninstall`; у Android TV 11 есть системный «sleep after idle» |
| Idle-dim, «угольный» фон, дневной вариант внутри MainActivity | Дублируют системный скринсейвер/таймер; «уголь» не помогает LCD-ореолу (он от яркости плиток); лишнее состояние |
| Радиус 14 dp у пилюль вместо капсул | Капсулы — идиома верхней панели tvOS/Google TV, которую лаунчер осознанно копирует |
| `TvInputCallback`, productFlavors для профилей, Gradle-ветки под Android 14 | Passthrough-вход на донгле не появится; одна строка `sourceSets`/копирование файла проще flavors; A14-сценарий не подтверждён |
| Полноценный мастер первого запуска | Второй UI со своими правилами навигации ради того, что делает одна подсказка + долгое нажатие |
| Значки обновлений из Play | Публичного API нет; неверный бейдж хуже отсутствия |
| Самописная пружина на Choreographer вместо DynamicAnimation | Теряется проверенный `animateToFinalPosition`, экономия — килобайты, которые R8 и так уберёт |
| Жёсткие битмапы (`Config.HARDWARE`), RGB_565, дисковый кэш каталога, DiffUtil — до замеров | Процесс HOME защищён (oom_adj 600); RGB_565 даст полосы на тёмных баннерах; после ухода `reload` из `onResume` нужды нет, пока `dumpsys` не покажет обратное |
| Luminance-aware стекло, специальные анимации блика по диагонали, тонирование полосы дока аниматором | Простые константы (тёмное кольцо, полупрозрачная полоса) дают тот же эффект; `invalidateItemDecorations()` в аниматоре = relayout всей сетки 24 раза за ход |
| Глобальный `UncaughtExceptionHandler` + safe mode как первая мера | Per-app try/catch закрывает реальный риск за 8 строк; safe mode — второй слой, когда будет время |

---

## 6. Дорожная карта

**Фаза 0 — Измерить и узнать устройство (один вечер).**
`launcher/measure.sh` (раздел 7), расширенный `tv.py --probe`, снять `wm size/density`, HOME-пакет, версию
Android, список пакетов, `getevent` пульта, статус Location, `dumpsys tv_input`. Зафиксировать базовые цифры:
`am start -W` TotalTime, 95-й перцентиль кадра за 20 ходов по доку, PSS.

**Фаза 1 — Быстро и корректно на стике (2–3 вечера).** Все P0 и дешёвые P1:
`reload` вон из `onResume` + LauncherApps-callback + фоновая загрузка; анимация только на холодном старте; без
сброса alpha; stable ids + пересинхронизация фокуса + `focusedByDefault`; Left/Right на плитках; Home/Back →
дока; Down с пилюли → `lastDockFocused`; скрыть Inputs + граница по видимым пилюлям; профиль конфига стика +
проверка `pm path`; try/catch в `reload`/`vividColor`; двухэтапный скрипт с проверкой HOME, `--strict`,
Linux-пути, стабильная подпись; `<queries>`; пустое состояние; шрифт с кириллицей + `values-ru` + вынос строк;
release + R8 + `pm compile`; `AmbientView` без слоя + dither; `maxWidth` у Wi-Fi. Ожидаемый результат: Home
из Netflix → стабильный кадр < 150 мс на тёплом процессе, холодный старт < 400 мс, ни одного «мёртвого» или
непредсказуемого нажатия.

**Фаза 2 — Удобство (3–4 вечера).** `LauncherPrefs` + меню долгого нажатия (закрепить/открепить/влево/вправо/
скрыть/удалить/о приложении) + заглушки дока + DUMP-receiver для ADB; dropdown у Settings с «О лаунчере»;
подсказка первого запуска; DreamService-часы; переключатель не-TV приложений; проба Watch Next и, по её
итогам, строка «Продолжить просмотр».

**Фаза 3 — Визуал (2 вечера).** Пружины 650/0.75 и масштаб 150 мс; типографика ≤ 16 sp; кольцо и свечение
стекла, блик без clipPath; радиус полосы 30 dp и её полупрозрачность; фейды и масштаб пилюль; градиентные плитки
для приложений без баннера; якорная прокрутка сетки с просмотром следующей строки и компенсацией стекла; точка
«новое». Каждое изменение сверять скриншотом со стика в тёмной комнате.

**Фаза 4 — Опционально.** Обои из файла; строка «недавние»; размер плитки от `columns`; CI с подписанным
релизом; JVM-тесты и lint-baseline; `tools/health.sh`; пилюля «Поиск», если резолвится.

---

## 7. Как измерять результат

Все команды через `tools/tv.py` (или `adb shell`, если он работает). Снимать до любых изменений и после каждого
PR; цели — для release-сборки с AOT.

```bash
TV="tools/venv/bin/python -I tools/tv.py"   # или TV="adb shell"; по одной команде на вызов (см. README, gotchas)

# Холодный старт, 5 раз, брать медиану TotalTime (цель < 400 мс; < 250 мс с двухфазной загрузкой)
for i in 1 2 3 4 5; do
  $TV 'am force-stop com.gathin.tvhome'
  $TV 'am start -W -n com.gathin.tvhome/.MainActivity' | grep -E 'TotalTime|WaitTime'
done

# Плавность: сброс статистики, 20 ходов по доку, затем гистограмма кадров
# (цель: 95-й перцентиль < 16 мс, janky < 5 %)
$TV 'dumpsys gfxinfo com.gathin.tvhome reset'
for i in $(seq 1 10); do
  printf '%s\n' 'input keyevent KEYCODE_DPAD_RIGHT' 'sleep 0.4' 'input keyevent KEYCODE_DPAD_LEFT' 'sleep 0.4' | $TV --batch
done
$TV 'dumpsys gfxinfo com.gathin.tvhome' | sed -n '1,40p'

# Память (цель: TOTAL PSS < 40 МБ с загруженными баннерами, < 60 МБ в худшем случае)
$TV 'dumpsys meminfo com.gathin.tvhome' | grep -E 'TOTAL|Graphics|Native Heap|Dalvik Heap'

# Компиляция (ожидается [status=speed] после pm compile)
$TV 'dumpsys package com.gathin.tvhome' | grep -A4 Dexopt

# Возврат из приложения: запись экрана 8 с, нажать Home из Netflix, затем покадрово
# считать от нажатия до стабильного кадра
$TV 'screenrecord --time-limit 8 /sdcard/home.mp4' &
sleep 2; $TV 'input keyevent KEYCODE_HOME'; wait
$TV --pull /sdcard/home.mp4 home.mp4
```

Дополнительно: Developer options → Debug GPU overdraw (цель: в основном синий/зелёный), Profile HWUI rendering
(«On screen as bars») при ходьбе по доку.

---

## 8. Открытые вопросы к владельцу стика

1. Какая версия Android на вашем стике сейчас (`getprop ro.build.version.release`): 11 или уже 14? От этого
   зависит пакет HOME и наличие пикера скринсейвера.
2. Какие 3–4 приложения должны быть в доке и в каком порядке? Учтите, что Netflix и Prime Video уже висят на
   кнопках пульта; российские сервисы (Кинопоиск, Иви, Okko, Wink) имеет смысл проверить командой
   `pm list packages | grep -iE 'kinopoisk|ivi|okko|wink|kodi|plex'`.
3. Интерфейс на русском (приветствие, подписи, дата) — да? Предлагаю Manrope как шрифт с кириллицей; можно
   прислать два скриншота на выбор (Manrope / Golos Text).
4. Обои (семейное фото под затемнением) нужны, или оставляем чистый чёрный под OLED/тёмную комнату?
5. Оставить стоковый лаунчер включённым, но не выбранным (страховка от краш-цикла ценой памяти), или отключать
   после того, как наш переживёт перезагрузку?
6. Нужна ли пилюля «Поиск» на экране, учитывая кнопку микрофона на пульте?
7. Хотите ли строку «Продолжить просмотр», если проба покажет, что ваши приложения публикуют Watch Next?

---

## 9. Источники

Устройство

- Xiaomi TV Stick 4K, спецификации: https://www.mi.com/global/product/xiaomi-tv-stick-4k/
- Android Authority, обзор (пульт, горячие клавиши): https://www.androidauthority.com/xiaomi-tv-stick-4k-review-3086999
- GSMArena, обзор (память, HDR, предустановленное): https://www.gsmarena.com/xiaomi_tv_stick_4k_review-news-57961.php
- 9to5Google: рефреш 2023 остаётся на Android TV 11: https://9to5google.com/2023/02/14/xiaomi-tv-stick-4k-2023/
- Android TV Guide (MDZ-27-AA): https://www.androidtv-guide.com/streaming-gaming/xiaomi-tv-stick-v2/
- Гайд по восстановлению MDZ-27-AA (кодовое имя soul, прошивки, OTA 11→14, USB-порт):
  https://github.com/RodrigoDeveloperX/xiaomi-mi-tv-stick-4k-mdz-27-aa-recovery
- cnx-software о S905Y4: https://www.cnx-software.com/2021/01/26/4k-av1-android-tv-dongles-surface-with-amlogic-s905y4-cortex-a35-processor/
- The Week, обзор (TV+, Miracast): https://www.theweek.in/review/gadgets/2023/03/09/xiaomi-tv-stick-4k-review-a-compact--feature-loaded-streaming-st.amp.html

Платформа (AOSP android11-release, если не указано иное)

- PhoneWindowManager (Home, ASSIST/SEARCH, long-press): https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android11-release/services/core/java/com/android/server/policy/PhoneWindowManager.java
- PhoneWindow.launchDefaultSearch: https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android11-release/core/java/com/android/internal/policy/PhoneWindow.java
- ActivityStarter.deliverToCurrentTopIfNeeded, NewIntentItem, TransactionExecutorHelper (Home-while-home):
  https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android11-release/services/core/java/com/android/server/wm/ActivityStarter.java
- AppErrors / ActivityTaskManager (нет диалогов на TV, перезапуск HOME): https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android11-release/services/core/java/com/android/server/am/AppErrors.java
- ViewGroup.removeViewInternal / FocusFinder / ViewRootImpl (фокус): https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android11-release/core/java/android/view/ViewGroup.java ,
  https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android11-release/core/java/android/view/FocusFinder.java ,
  https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android11-release/core/java/android/view/ViewRootImpl.java
- Разрешения ядра (DUMP, WRITE_SECURE_SETTINGS, REQUEST_DELETE_PACKAGES, QUERY_ALL_PACKAGES, BIND_DREAM_SERVICE):
  https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android11-release/core/res/AndroidManifest.xml
- ART: debuggable опирается на JIT: https://android.googlesource.com/platform/art/+/a0619e2%5E!/
- TvProvider (READ_TV_LISTINGS, createSqlParams): https://raw.githubusercontent.com/LineageOS/android_packages_providers_TvProvider/lineage-17.1/src/com/android/providers/tv/TvProvider.java ,
  https://raw.githubusercontent.com/LineageOS/android_packages_providers_TvProvider/lineage-17.1/AndroidManifest.xml
- TvSettings 11, экспортированные activity: https://raw.githubusercontent.com/LineageOS/android_packages_apps_TvSettings/lineage-18.1/Settings/AndroidManifest.xml
- Видимость пакетов: https://developer.android.com/training/package-visibility , https://developer.android.com/training/package-visibility/declaring
- WifiInfo и ограничения Android 10: https://developer.android.com/reference/android/net/wifi/WifiInfo , https://developer.android.com/about/versions/10/privacy/changes
- LauncherApps.Callback: https://developer.android.com/reference/android/content/pm/LauncherApps.Callback
- SpringForce: https://developer.android.com/reference/androidx/dynamicanimation/animation/SpringForce
- Навигация и layout для TV: https://developer.android.com/training/tv/start/navigation , https://developer.android.com/training/tv/start/layouts , https://developer.android.com/design/ui/tv/guides/styles/layouts
- Compose for TV, CardDefaults (рамка фокуса 3 dp): https://developer.android.com/reference/kotlin/androidx/tv/material3/CardDefaults
- Baseline profiles / установка вне Play: https://developer.android.com/topic/performance/baselineprofiles/overview
- Время запуска: https://developer.android.com/topic/performance/vitals/launch-time
- AGP 8.5 (совместимость, localeFilters появился в 8.8): https://developer.android.com/build/releases/past-releases/agp-8-5-0-release-notes
- Релизы recyclerview / dynamicanimation / leanback: https://developer.android.com/jetpack/androidx/releases/recyclerview ,
  https://developer.android.com/jetpack/androidx/releases/dynamicanimation , https://developer.android.com/jetpack/androidx/releases/leanback

Конкуренты (планка «удобно»)

- Projectivy Launcher: https://play.google.com/store/apps/details?id=com.spocky.projengmenu , обсуждение на XDA: https://xdaforums.com/t/app-android-tv-projectivy-launcher.4436549/
- FLauncher (GPL-3, обои без WallpaperManager): https://gitlab.com/flauncher/flauncher
- LtvLauncher (Continue Watching через READ_TV_LISTINGS, issues #133/#134): https://github.com/LeanBitLab/LtvLauncher
- Редизайн Google TV 2025: https://www.androidauthority.com/google-tv-home-screen-redesign-testing-3599399

---

## 10. Чек-лист команд для проверки на устройстве

```bash
TV="tools/venv/bin/python -I tools/tv.py"   # или TV="adb shell"

# Железо и прошивка
$TV 'getprop ro.product.model; getprop ro.product.device; getprop ro.board.platform; getprop ro.build.version.release; getprop ro.build.display.id; getprop ro.build.version.security_patch'
$TV 'cat /proc/meminfo | head -3; df -h /data'
$TV 'wm size; wm density; getprop ro.sf.lcd_density'
$TV 'dumpsys SurfaceFlinger | grep -iE "hdr|dataspace|display" | head -20'
$TV 'getprop | grep -E "dalvik.vm.heap"'

# Домашний экран и пакеты
$TV 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
$TV 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
$TV 'pm list packages -f | grep -iE "xiaomi|mitv|miui|patchwall|tvlauncher|tvrecommendations"'
$TV 'pm list packages -d'
$TV 'pm list features | grep -iE "live_tv|leanback|device_admin"'
$TV 'dumpsys tv_input | head -40'

# Пульт: нажать по очереди микрофон, Netflix, Prime, региональную кнопку; Ctrl+C для выхода
$TV 'getevent -l'
$TV 'dumpsys window policy | grep -iE "LongPressOnHome|DoubleTapOnHome"'

# Настройки, которые влияют на лаунчер
$TV 'settings get secure location_mode; settings get global device_name; settings get secure screensaver_components; settings get secure sleep_timeout'
for a in SETTINGS WIFI_SETTINGS BLUETOOTH_SETTINGS INTERNAL_STORAGE_SETTINGS DREAM_SETTINGS DEVICE_INFO_SETTINGS APPLICATION_SETTINGS; do
  $TV "cmd package resolve-activity --brief -a android.settings.$a"
done
$TV 'cmd package resolve-activity --brief -a com.google.android.intent.action.CONNECT_INPUT'
$TV 'cmd package resolve-activity --brief -a android.intent.action.ASSIST'

# Проба Watch Next (безопасно, обратимо: pm revoke ... чтобы снять)
$TV 'pm grant com.gathin.tvhome android.permission.READ_TV_LISTINGS'
$TV 'content query --uri content://android.media.tv/watch_next_program --projection package_name:title:last_engagement_time_utc_millis'

# USB-режим стика при подключении к ПК (на ПК): lsusb | grep 18d1   # 4EE1 = MTP, 4EE2 = MTP+ADB
```
