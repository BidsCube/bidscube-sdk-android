# Bidscube video SDK verification (any platform)

Universal checklist for **Android, iOS, Unity, and mediation wrappers**. Logic is the same: inspect the creative, see which player the SDK chose, and confirm the host app has the required dependencies.

---

## 1. What to look for

Errors such as:

```
Ad display error / code 1006
Failed resolution / class not found … VideoAdPlayer / IMA / …
```

usually mean:

1. Bid response arrived (HTTP 200, non-empty `adm`)
2. **Display failed** — the SDK could not start the video player

| Cause | Summary |
|-------|---------|
| Missing optional dependency | SDK used IMA / Media3 / AVKit stack not in the final build |
| Wrong SDK variant | Lite / no-video artifact used for a video placement |
| Wrong VAST type | Inline MP4 present but SDK still chose wrapper/IMA path |
| No UI context | Show without Activity / UIViewController |
| MP4 unreachable | URL blocked, ATS / cleartext, network |

---

## 2. Step 1 — Inspect VAST in the response

Read raw `adm` from logs, Charles, or mock SSP.

### 2.1 Inline progressive MP4 (e.g. DoorDash)

```xml
<VAST …>
  <InLine>
    …
    <MediaFiles>
      <MediaFile delivery="progressive" type="video/mp4" …>
        https://…/video.mp4
      </MediaFile>
    </MediaFiles>
  </InLine>
</VAST>
```

**Expected (Android 1.2.6+ / fullVideo):** `Media3VideoAdPlayer` via `VideoSlotPlayer` (Media3 ExoPlayer) — direct inline MP4 playback, **no IMA required**.

**Wrapper / ad tag URL / complex VAST:** IMA path (`IMAPlayerHandler`) — `VideoAdPlayer` / Google IMA must be on classpath.

### 2.2 Wrapper / redirect VAST

```xml
<Wrapper>
  <VASTAdTagURI>https://…/vast.xml</VASTAdTagURI>
</Wrapper>
```

**Expected:** IMA (Android/iOS) or custom VAST resolver — not the inline Media3 path.

### 2.3 Not VAST

`adm` may be HTML, JSON native, or image — use the correct ad API.

### VAST checklist

- [ ] Valid `<VAST>` XML
- [ ] `<InLine>` with `<MediaFile type="video/mp4">` and HTTPS URL
- [ ] URL opens from device (`curl` / browser)
- [ ] Not wrapper-only without `MediaFile`

---

## 3. Step 2 — SDK artifact / flavor

| Variant | Video | Stack |
|---------|-------|-------|
| **fullVideo** | Yes | Media3 inline MP4, VAST pods, OpenRTB pods; IMA for wrapper/ad tag |
| **liteNoVideo** | No | Banner / image / native only |

- [ ] Single SDK artifact (no conflicting duplicates)
- [ ] Video placement uses video-enabled artifact (`fullVideo`, not `liteNoVideo`)
- [ ] AAR + adapter from the same release

---

## 4. Step 3 — Host app dependencies

**Inline MP4 / pod playback (fullVideo):** requires Media3 (bundled in fullVideo artifact).

**IMA path only** (wrapper VAST, ad tag URL, no inline MediaFile):

```groovy
implementation 'com.google.ads.interactivemedia.v3:interactivemedia:3.37.0'
```

**Gradle check:**

```bash
./gradlew :app:dependencies | grep -iE 'bidscube|interactivemedia|media3|ima'
```

**Note:** Inline MP4 on Android **1.2.6+** does **not** require IMA when `adm` contains a playable `<MediaFile type="video/mp4">`.

---

## 5. Step 4 — Logs and callbacks

Success chain:

1. Video ad request
2. Bid OK (`adm` length > 0)
3. Player choice (`Media3VideoAdPlayer` / `VideoSlotPlayer` vs `IMAPlayerHandler`)
4. `playVast` / playback start
5. `onAdDisplayed` → `onVideoAdStarted` (once per single video; pod uses indexed + OpenRTB callbacks)

**Inline MP4:** look for Media3 / `VideoSlotPlayer` playback logs.

**End card:** only if VAST has companion image — `onEndCardShown`. No companion → dialog closes without preview.

---

## 6. Symptom → fix matrix

| Symptom | Action |
|---------|--------|
| Inline MP4 + `VideoAdPlayer` not found | Use **fullVideo** artifact; add IMA only if using wrapper/ad tag path |
| Wrapper VAST, no MediaFile | Add IMA or resolve wrapper chain |
| MP4 network error | Fix URL / ATS / firewall |
| `Video playback is not supported` | Replace lite artifact |
| Works in test app, fails in production | Compare AAR vs Maven, release ProGuard, IMA in release (IMA path only) |

---

## 7. Minimal test case

1. Bid on video placement with inline MP4 in `adm`
2. Show interstitial / rewarded
3. Log player class and callbacks
4. Repeat on **release** build

**Success:** video plays, skip/close works, `onAdClosed` (or reward for rewarded).

### Sample inline VAST (MP4 + companion)

See `sdk/src/main/res/raw/vast.xml` in this repo.

---

## 8. Android SDK appendix (this repo)

| Gradle flavor | Maven artifact | Inline MP4 / pod | IMA path |
|---------------|----------------|------------------|----------|
| `fullVideo` | `bidscube-sdk-full-video` | `Media3VideoAdPlayer`, `VastAdPodPlayer` | `IMAPlayerHandler` |
| `liteNoVideo` | `bidscube-sdk-lite-no-video` | Not supported | Stub |

**Runtime:**

```java
SDKConfig.Builder()
    .videoAdsEnabled(true)
    .build();
```

**`VideoAdPlayerFactory` routing (1.2.6+):**

- Inline `<MediaFile type="video/mp4">` → Media3 (`Media3VideoAdPlayer` / `VideoSlotPlayer`)
- Wrapper / ad tag URL / no MediaFile → `IMAPlayerHandler`

**End card:** shown only when `VastParser.getCompanionImageUrl(adm)` returns a URL.

---

## 9. Five-minute checklist

```
1. adm — InLine + MediaFile mp4?     → Media3 direct play (fullVideo)
2. SDK — fullVideo variant?          → if no, switch artifact
3. Logs — Media3 vs IMA?             → IMA only for wrapper/ad tag
4. IMA in release (IMA path only)?   → add dependency if missing
5. MP4 URL live on device?           → if no, network not SDK
```
