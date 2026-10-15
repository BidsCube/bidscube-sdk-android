# Android TV / CTV ads — пропозиція форматів (BidsCube SDK)

Документ оновлено після технічного аудиту pass 2 (2026-10-14).  
Деталі імплементації, skip/Back/decoder, runtime і тести: [`ANDROID_TV_TECHNICAL_AUDIT.md`](ANDROID_TV_TECHNICAL_AUDIT.md).

**Scope:** прямий BidsCube SDK + testapp. **AppLovin / MAX = OUT OF SCOPE** (не blocker для TV readiness прямого SDK).

Легенда:
- **ФАКТ** — підтверджено кодом / unit-тестами / логами
- **ГІПОТЕЗА** — спостереження без real-device acceptance
- **МАЙБУТНЄ** — продуктова / Ad Ops / окрема інженерна задача

---

## 1. Контекст

**ФАКТ:** SDK + testapp мають Leanback launcher, TV detection і AUTO fullscreen для video на TV.  
**ФАКТ:** Unit suite після pass 3: **127** tests PASS / 0 FAIL.  
**ФАКТ:** Skip priority: format (rewarded=never) → publisher ABSENT→15s → VAST → IMA (interstitial only). INVALID / percent-без-Duration = engineering fallback 15s (не README).  
**ФАКТ:** Rewarded: немає Skip; Back → попередження про втрату нагороди; reward лише після complete (`RewardState`, once).  
**ФАКТ:** Media3 interstitial VAST tracking підключено; local collector QA path існує (`tools/local_tracking_collector.py`).  
**ФАКТ:** Playback start stall (15s без playing) закриває сесію з error — обґрунтовано publisher skip window.  
**ГІПОТЕЗА:** Мобільні banner sizes і mid-res VAST дають слабкий 10-ft UX / viewability на TV.  
**ГІПОТЕЗА:** Відсутність `Rendered first frame` на TV AVD може бути emulator Surface/EGL; на real TV **не підтверджено**. Не називати чорний екран «виправленим».

---

## 2. Формати (розуміння)

| Формат | ФАКТ зараз | МАЙБУТНЄ для конкурентоздатності |
|--------|------------|-----------------------------------|
| **Video** | Interstitial/rewarded dialog; на TV AUTO → fullscreen; Back/Skip/Close policy; Media3; SkipSchedule | Окремі TV placements, ≥720p 16:9 creatives, **real-device** QA (visual first frame) |
| **Banner / image** | Існуючі sizes показуються як є; на TV WebView у focus chain; **без** auto-stretch і **без** глобальної заборони 320×50. Runtime banner display на TV **не перевірявся** як creative | TV overlay / pause creatives + Ad Ops rejection mobile sizes на TV inventory |
| **Native** | Парсинг/показ існують; TV Leanback template відсутній. Наявність парсера ≠ runtime TV support | Окремий native TV template або deprioritize |

**Ключова ідея (продукт):** конкурентоздатність = inventory + creative specs під CTV, не лише «SDK покаже будь-який adm».

---

## 3. Конкурентна рамка (ціль)

1. **Video first на TV** — fullscreen, 16:9, VAST tracking, remote Skip/Close.  
2. **Окремі placement ID** для TV.  
3. **Banner на TV** — лише з TV-suitable creative (продуктове правило, не silent SDK ban).  
4. **Remote UX** — видимий focus, OK/Back.  
5. **Real device acceptance** — emulator ≠ production proof. Physical TV checklist — аудит §9.

---

## 4. Що вже є в SDK (факт)

### Реалізовано в коді
- `VideoUiDefaults.isTelevision` / `detectTelevision` (Leanback, FEATURE_TELEVISION, Fire TV feature, AFT* heuristic).  
- FullscreenPolicy AUTO / FORCE_*; на TV AUTO → fullscreen.  
- `VastSkipOffset` + `VideoUiDefaults.SkipSchedule` — permission vs timing; absent→15s; zero→immediate.  
- `VideoDialogRemoteUx` — non-cancelable dialog, Back locked / skip / close / rewarded confirm.  
- `RewardedExitConfirm` — early exit warning; cancel → `restoreRemoteFocus`.  
- Overlay focus лише коли дія дозволена; більший контроль на TV.  
- Media3: **emulator-only** decoder preference (non-goldfish first); `texture_view` (SurfaceView experiment reverted); PlayerView `FOCUS_BLOCK_DESCENDANTS`; first-frame / STATE_ENDED logs.  
- VAST inline + trackers + pods (response-side); wrapper — limited/IMA (IMA може армить Skip окремо від overlay countdown).  
- Testapp: LEANBACK_LAUNCHER, focusable controls, video QA variants.  
- **MAX adapter** у цьому дереві немає → **OUT OF SCOPE**.

### Перевірено unit-тестами
- SkipSchedule сценарії (absent / time / percent / zero / invalid / duration).  
- VideoDialogRemoteUx Back / skip / rewarded / abandon.  
- TV detection helpers.  
- Suite: **122** PASS.

### Перевірено на пристрої (TV AVD `emulator-5554`)
- D-pad focus Init + VIDEO AUTO; dialog `tv=true`; `skipAfter=5` з VAST.  
- Emulator decoder preference log.  
- Timeline `STATE_ENDED` ~10s (visual first frame **не** підтверджено).  
- Pixel 8 / physical TV: **NOT TESTED**.

---

## 5. Пропозиція (рішення на погодження)

### Phase A — Product / Ad Ops (**МАЙБУТНЄ**, без великого SDK rewrite)

1. TV placements: `tv_video_fs`, опційно `tv_overlay` / `tv_pause`.  
2. Блокувати mobile-only sizes на TV inventory на стороні SSP.  
3. TV Creative Spec (1 сторінка).  
4. Узгодити CTV query fields для legacy GET tag (`device_ui`, make/model/os/osv) — backend **не** аудитили; відсутність явних query fields ≠ доведена відсутність серверної класифікації.

### Phase B — SDK hardening (**частково зроблено**)

| Пункт | Статус |
|-------|--------|
| TV fullscreen для interstitial/rewarded (AUTO) | Зроблено + unit |
| Skip/Close/Back remote policy + SkipSchedule | Зроблено + unit; TV AVD skipAfter log |
| Rewarded exit confirm | Зроблено + unit; device NOT TESTED |
| Focus ring / non-focusable unavailable controls | Зроблено |
| Banner D-pad focus на TV | Мінімум у коді; creative runtime NOT TESTED |
| Decoder reorder | Emulator-only; visual fix **не** підтверджено |
| Real-device playback acceptance | **NOT TESTED** (physical TV) |
| Request-side CTV signals | Потрібен backend контракт |
| MAX adapter | **OUT OF SCOPE** |

### Phase C — диференціатор (**МАЙБУТНЄ**, out of scope аудиту)

Pause-ads, L-banner, новий native TV template, SSAI, розширений pod API.

---

## 6. Рекомендована позиція для тімліда

> На Android TV не масштабуємо mobile banner/video 1:1 на рівні продукту.  
> **Video CTV fullscreen + окремі TV creatives** — must-have для конкурентоздатності.  
> Mobile banner sizes на TV — Ad Ops rule; SDK їх глобально не блокує.  
> Базовий remote/Skip/Back hardening у прямому SDK зроблено (122 unit PASS; TV AVD partial).  
> **Production CTV visual playback** — після physical TV QA.  
> **MAX — out of scope**, не blocker прямого SDK.

---

## 7. Питання на рішення

1. TV для нас — **CTV video priority** чи також banner inventory?  
2. Чи готові Ad Ops завести окремі placement + creative rejection rules?  
3. Який дедлайн / пріоритет vs mobile roadmap?  
4. Чи є доступ до real Android TV / Google TV / Fire TV для acceptance?  
5. Чи узгоджуємо нові GET params для device classification (після backend review)?
