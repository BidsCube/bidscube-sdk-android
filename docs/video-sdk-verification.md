# Bidscube video SDK verification (any platform)

Universal checklist for **Android, iOS, Unity, and mediation wrappers**. Logic is the same: inspect the creative, see which player the SDK chose, and confirm the host app has the required dependencies.

**Android SDK version:** 1.2.11 (`version.properties`).

---

## 1. What to look for

Errors such as:

```
Ad display error / code 1006
Failed resolution / class not found … VideoAdPlayer / IMA / …
```

usually mean:

1. Bid response arrived (HTTP 200, non-empty `adm` or pod JSON)
2. **Display failed** — player could not start

| Cause | Summary |
|-------|---------|
| Missing optional dependency | IMA / Media3 not in final build (common with manual AAR) |
| Wrong SDK variant | `liteNoVideo` for video placement |
| Wrong VAST type | Inline MP4 present but IMA path chosen |
| No UI context | Show without Activity |
| MP4 unreachable | URL blocked, ATS, network |

---

## 2. Step 1 — Inspect VAST / JSON in the response

### 2.1 Inline progressive MP4

```xml
<MediaFile delivery="progressive" type="video/mp4" …>https://…/video.mp4</MediaFile>
```

**Expected (Android 1.2.6+ / fullVideo):** `Media3VideoAdPlayer` via `VideoSlotPlayer` — **no IMA required** for inline MediaFile playback.

Log: `VideoAdPlayerFactory: Selected Media3 player for inline MediaFile`

### 2.2 Wrapper / redirect VAST

```xml
<Wrapper><VASTAdTagURI>…</VASTAdTagURI></Wrapper>
```

**Expected:** `IMAPlayerHandler` — IMA on classpath.

Log: `VideoAdPlayerFactory: Selected IMA player (no inline MediaFile in adm)`

### 2.3 OpenRTB-like pod JSON

JSON with `bids[]`, `openrtb.video`, `slotinpod`, etc. — **fullVideo** only. Response parsing only; SDK does not POST OpenRTB bid requests.

### 2.4 Not VAST

`adm` may be HTML, JSON native, or image — use the correct ad API.

---

## 3. Step 2 — SDK artifact / flavor

| Variant | Maven artifact | Video |
|---------|----------------|-------|
| **fullVideo** | `bidscube-sdk-full-video` | Media3, VAST pods, OpenRTB-like pods; IMA for wrapper |
| **liteNoVideo** | `bidscube-sdk-lite-no-video` | Banner / image / native only |

- [ ] Video placement uses `fullVideo`
- [ ] No conflicting duplicate SDK artifacts

---

## 4. Step 3 — Host app dependencies

**Maven:** transitive deps resolve automatically.

**Manual fullVideo AAR** — host must add, for example:

```groovy
implementation 'com.google.ads.interactivemedia.v3:interactivemedia:3.37.0'
implementation 'androidx.media3:media3-common:1.4.1'
implementation 'androidx.media3:media3-exoplayer:1.4.1'
implementation 'androidx.media3:media3-ui:1.4.1'
```

Inline MP4 does **not** use IMA for playback, but wrapper VAST still requires IMA.

```bash
./gradlew :app:dependencies | grep -iE 'bidscube|interactivemedia|media3'
```

---

## 5. Step 4 — Logs and callbacks

Success chain:

1. Video ad request
2. Bid OK
3. Player: Media3 vs IMA (single ad) or `VastAdPodPlayer` (pod)
4. `onAdDisplayed` → `onVideoAdStarted`
5. Pods: indexed + OpenRTB callbacks when applicable

**End card:** only with companion image — `onEndCardShown`.

**Rewarded:** `onUserRewarded` only after full video/pod completion; never on skip.

---

## 6. Symptom → fix matrix

| Symptom | Action |
|---------|--------|
| Inline MP4 + class not found | `fullVideo` artifact; add Media3 if manual AAR |
| Wrapper VAST | Add IMA |
| OpenRTB JSON, no play | `videoAdsEnabled`, `openRtbPodMetadataEnabled`, per-bid `adm` |
| `Video playback is not supported` | Replace `liteNoVideo` |
| Rewarded pod no reward | Must complete all slots without skip |

---

## 7. Android SDK appendix (this repo)

| Gradle flavor | Maven artifact | Inline MP4 / pod | IMA path |
|---------------|----------------|------------------|----------|
| `fullVideo` | `bidscube-sdk-full-video` | `Media3VideoAdPlayer`, `VastAdPodPlayer` | `IMAPlayerHandler` |
| `liteNoVideo` | `bidscube-sdk-lite-no-video` | Not supported | Stub |

**Initialize:**

```java
SDKConfig config = new SDKConfig.Builder(context)
    .videoAdsEnabled(true)
    .build();
BidscubeSDK.initialize(context, config);
```

**`VideoAdPlayerFactory` routing:**

- Inline `<MediaFile type="video/mp4">` → Media3
- Wrapper / ad tag / no MediaFile → IMA

**OpenRTB-like pods:** `OpenRtbPoddedResponseNormalizer` → `VideoExperienceHelper.showAdPodWithPlan`.

**Tests:**

```bash
./gradlew :sdk:testFullVideoDebugUnitTest
./gradlew :sdk:testLiteNoVideoDebugUnitTest
```

---

## 8. Five-minute checklist

```
1. adm — InLine + MediaFile mp4 OR OpenRTB bids with adm?
2. SDK — fullVideo 1.2.11+?
3. BidscubeSDK.initialize + videoAdsEnabled(true)?
4. Logs — Media3 vs IMA vs VastAdPodPlayer?
5. Manual AAR — Media3 + IMA deps present?
6. MP4 URL live on device?
7. End card? — companion StaticResource?
8. Rewarded pod — all slots completed?
```
