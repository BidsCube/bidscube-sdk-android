# Android TV — технічний аудит BidsCube SDK (оновлено)

**Дата проходу:** 2026-10-14 (pass 2 — skip/Back/decoder/runtime)  
**Scope:** прямий BidsCube SDK + testapp. **AppLovin / MAX = OUT OF SCOPE** (не blocker).  
**Commit SDK:** `43cc7406abe622fd30566fa4aa37ab351deda455` + локальні зміни (не закомічені).  
**SDK version file:** `1.2.11` (`version.properties`).  
**Testapp dependency:** `implementation project(':sdk')` + `missingDimensionStrategy "videoMode", "fullVideo"` (`bidscube-testapp-android/build.gradle`).  
**Installed package:** `com.bidscube.testapp`  
**Installed APK SHA-256:** `f455d604dc39ccdfaf468647d4871dd2a71d840dce11dfc8c2252360f4609d48`  
(локальний `bidscube-testapp-android-debug.apk` ≡ `adb pull` base.apk на `emulator-5554`; match підтверджено 2026-10-14).  
**Commit / push / release:** не виконувались.

---

## 1. Ідентичність збірки

| Поле | Значення | Доказ |
|------|----------|--------|
| SDK wiring | local module `:sdk`, не Maven AAR | `settings.gradle.kts` include + `implementation project(':sdk')` |
| Variant | fullVideo debug | `missingDimensionStrategy "videoMode", "fullVideo"` |
| Testapp versionName | 1.0.2 / versionCode 4 | `dumpsys package` |
| APK match | LOCAL == INSTALLED SHA-256 | `shasum` local + `adb pull` base.apk |
| Pixel 8 | **не підключений** у цьому проході | `adb devices` → лише `emulator-5554` |

---

## 2. Skip policy (питання 1)

### Фактична модель після фіксу

| Шар | Символ | Роль |
|-----|--------|------|
| Parse (permission source) | `VastSkipOffset.parseFromVast` / `parseValue` | `ABSENT` / `TIME_SECONDS` (вкл. 0) / `PERCENT` / `INVALID` |
| Schedule (when available) | `VideoUiDefaults.resolveSkipSchedule` | `skipAllowed` + `availableAfterSeconds` |
| UI | `VideoInterstitialOverlay.applySkipSchedule` | 0 → immediate Skip; >0 → countdown; !allowed → hide |
| Legacy int API | `resolveSkipOffsetSeconds(int)` | `<=0` → 15 (не розрізняє absent vs zero) — залишено для сумісності |

### Політика BidsCube / publisher

**ФАКТ (документовано):** README 1.2.9 / 1.2.11 — default **15s лише коли VAST omits `skipoffset`**.  
Це **явна publisher policy**, не «VAST non-skippable». Поширюється на rewarded (той самий `presentVideoVastInDialog` + `SkipSchedule`).

### Сценарії

| Вхід | Поведінка | Доказ |
|------|-----------|--------|
| skipoffset відсутній | skippable after **15s** | `SkipScheduleTest.absent_usesPublisherDefault15` |
| `00:00:05` | after **5s** | unit + runtime log `skipKind=TIME_SECONDS, skipAfter=5` |
| `00:00:00` / `0` | **immediate Skip** (було зламано → 15 + Close) | `SkipScheduleTest.explicitZero_isImmediateSkip`, `VastSkipOffsetTest` |
| `25%` + Duration 40s | after **10s** | `SkipScheduleTest.percent_ofDuration` |
| `25%` без Duration | fallback **15s**, skippable | `percent_withoutDuration_fallsBackToDefault` |
| некоректний | як absent → **15s** | `invalid_usesPublisherDefault` |
| rewarded | той самий schedule + confirm на early exit | `RewardedExitConfirm` + `VideoDialogRemoteUx` |

### Media3 vs IMA

| Path | Хто армить Skip | Доказ |
|------|-----------------|--------|
| Media3 (inline MP4) | лише BidsCube overlay schedule | `AdDisplayManager.onVideoStarted` → `applySkipSchedule` |
| IMA (wrapper / no inline) | overlay schedule **і** `onVideoSkippable` від IMA (може армить раніше) | `IMAPlayerHandler` → `onVideoSkippable` → `overlay.enableSkip()` |

Семантика **не ідентична**: IMA може увімкнути Skip незалежно від countdown.

---

## 3. Back / locked dialog / session end (питання 2)

| Вимога | Статус | Доказ |
|--------|--------|--------|
| `setCancelable(false)` | **Implemented** | `VideoDialogRemoteUx.install` |
| Locked Back (interstitial) | consume, no dismiss | `dispatchBack(false,false)` unit |
| Locked Back (rewarded) | confirm «Leave / Keep watching» → abandon без reward | `RewardedExitConfirm` + `onAbandonFromBack` → `closeAdSession` |
| Skip (rewarded) | confirm перед skip | `requestSkip(rewarded=true, …)` |
| Cancel confirm | restore focus | `overlay.restoreRemoteFocus()` |
| Playback / load error | dialog closes | `onVideoError` → `closeAdSession` (AdDisplayManager / VideoExperienceHelper) |
| Hang / no first frame | **немає** dedicated timeout | код: відсутній watchdog; не додавали довільний timeout |
| Unexpected dismiss | no fake skip/complete/reward | `setOnDismissListener` лише cleanup + `onAdClosed` |
| Callbacks once | AtomicBoolean / compareAndSet | `adClosedEmitted`, `sessionEnd`, `RewardState` |
| Resources released | overlay/blocker/player release | `closeAdSession` / dismiss listener |

**Runtime rewarded confirm:** NOT TESTED на пристрої (немає rewarded one-tap у видимій сесії цього проходу). Unit policy: PASS.

---

## 4. Decoder / SurfaceView (питання 3)

### Що змінено саме під час TV-аудиту

| Зміна | Було (HEAD `43cc7406`) | Стало після pass 2 | Рішення |
|-------|------------------------|--------------------|---------|
| Decoder order | default ExoPlayer | `preferNonEmulatorDecoders` | **ізольовано на emulator** (`isEmulatorBuild`) |
| `surface_type` | `texture_view` | тимчасово `surface_view`, потім **повернуто `texture_view`** | revert — visual fix не підтверджено |
| Focus block on PlayerView | — | `FOCUS_BLOCK_DESCENDANTS` | залишено (TV UX) |
| first-frame / STATE_ENDED logs | — | додано | залишено (діагностика) |

### Докази decoder order

- Log (TV AVD): `Using emulator decoder preference (non-goldfish first)`  
- `Decoder order for video/avc: first=c2.android.avc.decoder count=2`  
- На телефонах / physical TV: default ExoPlayer (без reorder).

### Visual black screen

**Не називаємо виправленим.** У цьому проході:  
`STATE_ENDED position=10006 duration=10000` + `KEEP_LAST_FRAME` — timeline дійшов до кінця.  
Лога `Rendered first frame` **не було** → кадр на екрані **не підтверджено**.  
Video visual playback на TV AVD = **NOT PASS** (timeline-only observation).

---

## 5. Runtime результати

### Пристрої

| Пристрій | Статус |
|----------|--------|
| Android_TV_720p_API_34 (`emulator-5554`) | використано |
| Pixel 8 | **NOT CONNECTED** → усі phone shared-code сценарії **NOT TESTED** |
| Physical Android TV / Fire TV | **NOT TESTED** |

### TV AVD

| Сценарій | Результат | Доказ |
|----------|-----------|--------|
| D-pad focus на Init | **PASS** | uiautomator `focused="true"` на `btn_init_sdk`; OK/CENTER ініціалізує SDK |
| Focusable video buttons | **PASS (UI)** | `VIDEO · AUTO` / IMAGE ADS `focusable=true` (частина кнопок нижче fold — потрібен scroll) |
| Video request + dialog | **PASS (code path)** | `display mode=fullscreen, tv=true`; `skipKind=TIME_SECONDS, skipAfter=5` |
| Emulator decoder path | **PASS (log)** | emulator preference + `c2.android.avc.decoder` |
| Visual frames | **NOT PASS** | немає `Rendered first frame` |
| Timeline complete | **OBSERVED** | `STATE_ENDED` 10s → `KEEP_LAST_FRAME` |
| Rewarded early exit confirm | **NOT TESTED** | — |
| Home → return | **NOT TESTED** | — |
| Tracking duplicates | **NOT TESTED** systematically | test VAST містить httpbin + ssp URLs |
| Banner/native runtime display | **NOT TESTED** | не зараховувати focusable WebView як runtime support |

### Pixel 8 shared-code matrix

Усі пункти (повний перегляд, Skip, rewarded, Back spam, error, Home, multi-show): **NOT TESTED** — пристрій відсутній.

---

## 6. Тести

| | Кількість |
|--|-----------|
| Unit suite total (цей прохід) | **122** PASS / 0 FAIL |
| Нові в цьому проході | `VastSkipOffsetTest`, `SkipScheduleTest`, розширення `VideoDialogRemoteUxTest` (+ попередні TV: `VideoTelevisionDetectionTest`, `VideoDialogRemoteUxTest`, `NativeAdParserEventTrackerTest`) |

---

## 7. Реалізовано в коді / unit / device

| Тема | У коді | Unit | Device |
|------|--------|------|--------|
| SkipSchedule + zero/percent | так | PASS | TV: skipAfter=5 з VAST |
| Rewarded exit confirm | так | policy unit | NOT TESTED |
| Back lock / no fake skip on dismiss | так | PASS | partial (Back з settings) |
| Error closes dialog | так | — | NOT TESTED |
| Decoder reorder emulator-only | так | — | TV log PASS |
| texture_view restored | так | — | — |
| TV focus testapp buttons | так | — | PASS Init + VIDEO AUTO |
| Visual video | — | — | NOT PASS |
| Banner/native TV UX | focus hooks only | — | NOT TESTED as creatives |
| Request CTV fields | не додавали | — | request log без `devicetype` (backend classification **не перевірялась**) |

---

## 8. Backend / Ad Ops (не реалізовано як факт відсутності класифікації)

У GET payload на TV AVD є `w/h=1280/720`, `ua` містить `sdk_google_atv64_arm64`, немає явних `devicetype` / `device_ui`.  
**Не стверджуємо**, що сервер не класифікує TV — backend не аудитили. Потрібен окремий контрактний review.

---

## 9. Потребує фізичного TV

1. Visual first frame + audio on real panel  
2. Rewarded confirm UX + focus restore after cancel  
3. Home → resume / audio focus  
4. Multi-show memory / surface lifecycle  
5. Fire TV heuristic acceptance (AFT* / `amazon.hardware.fire_tv`)  
6. Banner/native readability + external browser links  
7. IMA wrapper path skip vs overlay countdown on device  

---

## 10. Scope підтримки після цього проходу

Базова **remote/skip/session** логіка для video interstitial на Leanback покращена й покрита unit-тестами; на TV AVD підтверджено fullscreen dialog + коректний skipAfter з VAST + emulator decoder isolation.  
**Production-ready CTV visual playback — ні.**  
MAX — out of scope.

---

## 11. Pass 3 (2026-10-14) — rewarded / timeline / IMA / hang / runtime

**AppLovin/MAX:** OUT OF SCOPE.  
**Identity:** SDK HEAD `43cc7406` + dirty tree; testapp `implementation project(':sdk')` fullVideo; APK `testApp-debug.apk` SHA-256 `aba69fb695080e49caef88754cdafb6b2991feef3cdc71ba7e7d695e9e5678d8` LOCAL≡INSTALLED.  
**Unit:** **127** PASS / 0 FAIL (нові/оновлені: rewarded cases у `SkipScheduleTest`, `VideoDialogRemoteUxTest`).

### 11.1 Підтверджені дефекти → виправлення

| Дефект | Fix | Доказ |
|--------|-----|--------|
| Rewarded отримував Skip через VAST/default/IMA | `resolveSkipSchedule(..., REWARDED)` → `skipAllowed=false`; `onVideoSkippable` ігнорується якщо rewarded / !skipAllowed | unit + log `skipAllowed=false, rewarded=true`; UI без Skip |
| Rewarded Skip/Back міг викликати skip | `requestSkip`/`dispatchBack` для rewarded → лише abandon confirm | `VideoDialogRemoteUx` |
| Media3 interstitial не слав VAST trackers | `Media3VideoAdPlayer` → `VastVideoTracker` impression/start/quartiles/complete/skip | log + local collector |
| `onRenderedFirstFrame` лише лог | проброс `onVideoFirstFrame` | log `Video first frame callback` |
| Hang без playing state → locked dialog | `PLAYBACK_START_STALL_MS` (=15s, = publisher skip window) → `onError` → close | `Media3VideoSlotPlayer.onStartStall` |
| Немає debug rewarded path | `BidscubeSDK.debugPresentRewardedVast` (debugMode) | testapp buttons |

### 11.2 Skip priority (єдина політика)

1. **Format** — REWARDED: Skip ніколи  
2. **Publisher omit (ABSENT)** — interstitial skippable after 15s (README)  
3. **VAST** time/percent (interstitial)  
4. **IMA `onVideoSkippable`** — може армить Skip раніше лише якщо interstitial і `skipAllowed`; не обходить rewarded  

Окремо (не README): **INVALID** і **PERCENT без Duration** — engineering fallback skippable after 15s (`SkipScheduleTest`).

IMA обмеження: native IMA Skip chrome покривається `ImaNativeSkipBlocker`; якщо IMA все ж емітить `SKIPPED`, reward не видається (`RewardState`).

### 11.3 Timeline (TV AVD, control VAST → local collector `127.0.0.1:8765`)

Фактична послідовність (interstitial DEBUG SDK path):

```
dialog shown (skipAllowed=true, skipAfter=5)
→ Playing slot
→ Rendered first frame / onVideoFirstFrame
→ VastVideoTracker impression + start / onVideoAdStarted
→ firstQuartile (collector + log)
→ skip (user) / onVideoAdSkipped
→ KEEP_LAST_FRAME → Close → onAdClosed
```

Collector dump (rewarded run, після фіксу HTTP 200): `impression`, `start`, `firstQuartile` (Dalvik UA).  
**Не** зараховано decoder-selection log як visual proof; у цьому проході first-frame callback **був**.

Контракт: tracking **не** gated на first frame (impression/start від `onStarted`/playing). Відсутність first-frame раніше не була дефектом wiring — callback підключений; на цьому прогоні спрацював.

### 11.4 Runtime PASS/FAIL (TV AVD only)

| Сценарій | Result | Evidence |
|----------|--------|----------|
| Rewarded: no Skip button | **PASS** | UI Skip texts=[]; log `skipAllowed=false` |
| Rewarded Back → leave warning | **PASS** | UI `Leave video?` / lose reward |
| Rewarded early exit → no reward | **PASS** | `onAdClosed` без `onUserRewarded` |
| Rewarded cancel (Keep) | **PARTIAL** | confirm зникає; button text у dump іноді порожній; resume/focus не доведено ідеально |
| Interstitial Skip | **PASS** | Skip UI + `onVideoAdSkipped` + tracker skip |
| Playback error closes dialog | **PASS** | `onAdFailed Source error` → `onAdClosed`; back to DEBUG buttons |
| Home → return | **PARTIAL** | Home + reopen InterstitialTest; focus window restored; audio/lifecycle deep check limited |
| Multi consecutive shows | **PASS** | two `dialog shown` (~03:59:40, ~03:59:47) |
| First-frame callback wired | **PASS** | log on interstitial + rewarded |
| Local collector events | **PASS** (after HTTP 200 fix) | `/dump` impression/start/firstQuartile |
| Pixel 8 / physical TV | **NOT TESTED** | |

### 11.5 Залишається лише для physical TV

1. Visual/audio acceptance на реальному панелі (не AVD)  
2. Rewarded Keep-watching focus restore + pause/resume audio feel  
3. Home during playback → exact audio focus restore  
4. IMA wrapper skippable creative vs rewarded policy on device  
5. Fire TV hardware heuristic  

### 11.6 Diff цього проходу (ключове)

- `VideoUiDefaults` format-aware SkipSchedule + stall constant + policy docs  
- `VideoDialogRemoteUx` / `RewardedExitConfirm` pause-resume hooks; rewarded never skips  
- `AdDisplayManager` / `VideoExperienceHelper` IMA gate + debug present  
- `Media3VideoAdPlayer` tracking + first frame + pause/resume  
- `Media3VideoSlotPlayer` start-stall watchdog + first-frame listener  
- `BidscubeSDK.debugPresent*` + testapp DEBUG buttons + `tools/local_tracking_collector.py`  
- `InterstitialTestActivity` exported for adb QA
