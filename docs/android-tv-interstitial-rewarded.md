# Android TV, interstitial & rewarded video

Publisher-facing rules for **direct BidsCube SDK** (`fullVideo`).  
AppLovin / MAX adapters are out of scope for this document.

Related: [`../README.md`](../README.md) · [`video-sdk-verification.md`](video-sdk-verification.md) · [`android-tv-technical-audit.md`](android-tv-technical-audit.md) (engineering audit) · [`android-tv-formats-proposal.md`](android-tv-formats-proposal.md).

---

## 1. Formats at a glance

| | **Interstitial** | **Rewarded** |
|--|------------------|--------------|
| API | `showInterstitialVideoAd` / `showVideoAd` | `showRewardedVideoAd` |
| Skip button | Yes, when schedule allows | **Never** |
| Back while locked | Consumed (no dismiss) | Confirm: leave → **no reward** |
| Back when Skip armed | Skips creative | N/A (Skip never armed) |
| `onUserRewarded` | Never | Only after **full** complete (once) |
| Early close / skip / error | Session ends, no reward | No reward |

---

## 2. Interstitial video

### Skip schedule

Permission and timing are separate (`VastSkipOffset` → `VideoUiDefaults.SkipSchedule`).

| VAST `skipoffset` | Behavior |
|-------------------|----------|
| **Absent** | Skippable after **15s** (BidsCube publisher default; README 1.2.9 / 1.2.11) |
| `00:00:00` / `0` | Skip **immediately** |
| Time (e.g. `00:00:15`) | Skip after that many seconds |
| Percent (e.g. `25%`) + Duration | `floor(duration × percent)` |
| Percent **without** Duration | Engineering fallback: skippable after **15s** (not README omit policy) |
| Invalid value | Engineering fallback: skippable after **15s** (not README omit policy) |

### Skip source priority (interstitial)

1. Publisher / BidsCube omit policy (ABSENT → 15s)  
2. VAST time / percent  
3. IMA `onVideoSkippable` — may arm Skip **earlier** on the IMA path only; does not invent skip on Media3-only creatives  

Overlay countdown (`Skip in N` → `Skip`) is the BidsCube control. IMA native Skip chrome is covered by `ImaNativeSkipBlocker` where applicable.

### Session end

- Complete / skip → post-video policy (`autoClose`, companion end card, or keep last frame + Close).  
- Unexpected dismiss → cleanup + `onAdClosed` only (no fake skip / complete).  
- Playback error → `onAdFailed` + dialog close.  
- Playback never enters playing within **15s** (`PLAYBACK_START_STALL_MS`) → error + close (aligned with default skip window so the user is not trapped in a non-cancelable dialog).

---

## 3. Rewarded video

### Contract

- **No Skip control**, regardless of VAST `skipoffset`, default 15s, or IMA `onVideoSkippable`.  
- Format policy wins: `resolveSkipSchedule(..., VideoAdFormat.REWARDED)` → `skipAllowed=false`.  
- **Back** opens confirm: *Leave video? / If you leave now, you will not receive a reward.*  
  - **Leave** → abandon session (close, **no** `onUserRewarded`, no invented skip complete).  
  - **Keep watching** → pause/resume playback + restore remote focus when possible.  
- **`onUserRewarded(placementId)`** only after full linear (or full pod without skipped slots / gamified rules). Fires **at most once** (`RewardState`).

### Publisher checklist

1. Use a dedicated rewarded placement ID.  
2. Call `BidscubeSDK.showRewardedVideoAd(placementId, callback)`.  
3. Grant in-app reward **only** in `onUserRewarded`.  
4. Do not treat `onAdClosed` / `onVideoAdSkipped` as reward.

---

## 4. Android TV / Leanback (separate from phone)

### Detection

`VideoUiDefaults.isTelevision(context)` / `detectTelevision`:

- `FEATURE_LEANBACK` / `android.software.leanback`  
- `FEATURE_TELEVISION`  
- `UiModeManager` = television  
- `amazon.hardware.fire_tv`  
- Conservative Amazon **AFT\*** model heuristic  

Physical Fire TV / Vega OS acceptance may still need device QA (see audit).

### Display / fullscreen

| Policy | Behavior |
|--------|----------|
| `AUTO` (default) | On TV → **always fullscreen** dialog; on phone → FULL_SCREEN / UNKNOWN → fullscreen, HEADER/FOOTER/SIDEBAR → windowed |
| `FORCE_FULLSCREEN` | Always fullscreen |
| `FORCE_WINDOWED` | Always windowed (QA compare on phone **and** TV) |

```java
BidscubeSDK.setVideoFullscreenPolicy(VideoUiDefaults.FullscreenPolicy.AUTO);
// QA:
BidscubeSDK.setVideoFullscreenPolicy(VideoUiDefaults.FullscreenPolicy.FORCE_WINDOWED);
```

### Remote / D-pad UX

- Video dialog: `setCancelable(false)`.  
- Skip / Close focusable only when the action is armed; larger controls on TV.  
- Player surface does not steal D-pad (`FOCUS_BLOCK_DESCENDANTS` on Media3 `PlayerView`).  
- Rewarded Back → confirm dialog (see §3).

### Emulator note

Decoder preference reorder (non-goldfish first) runs **only on emulator builds**. Phones and physical TVs use default ExoPlayer decoder order. Do not treat decoder-selection logs as proof of visible frames.

### What TV support is *not*

- Banner/native Leanback templates are **not** a substitute for runtime creative QA.  
- Missing CTV query fields in the legacy GET tag ≠ proof the backend does not classify TV (backend contract is separate).

---

## 5. Public APIs (quick reference)

```java
BidscubeSDK.showInterstitialVideoAd(placementId, callback);
BidscubeSDK.showRewardedVideoAd(placementId, callback);

BidscubeSDK.setVideoFullscreenPolicy(VideoUiDefaults.FullscreenPolicy.AUTO);
```

Debug-only (requires `SDKConfig.Builder.enableDebugMode(true)`):

```java
BidscubeSDK.debugPresentInterstitialVast(activity, placementId, vastXml, callback);
BidscubeSDK.debugPresentRewardedVast(activity, placementId, vastXml, callback);
```

Testapp: **Interstitial test → QA videos** — pick sample MP4, Interstitial (15s skip) / Rewarded, and display mode, then **Show via SDK path**.

---

## 6. Tracking (Media3 inline)

For inline Media3 interstitial/rewarded, VAST beacons are fired from `Media3VideoAdPlayer` via `VastVideoTracker` (impression / start / quartiles / complete / skip).  
Tracking is **not** gated on first-frame paint. First-frame is logged/callback for diagnostics (`onVideoFirstFrame`).

Local QA collector (host): `tools/local_tracking_collector.py` + `adb reverse tcp:8765 tcp:8765` (test creatives may point at `127.0.0.1:8765`).
