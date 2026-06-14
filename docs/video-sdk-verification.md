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

**Expected (Android 1.2.6+):** `NativeMp4VideoPlayer` — direct `VideoView` playback, **no IMA required**.

**Older builds / other platforms:** may still route through IMA → `VideoAdPlayer` class must be on classpath.

### 2.2 Wrapper / redirect VAST

```xml
<Wrapper>
  <VASTAdTagURI>https://…/vast.xml</VASTAdTagURI>
</Wrapper>
```

**Expected:** full video stack (IMA on Android/iOS, or custom VAST resolver).

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
| Full / with video | Yes | Native MP4 and/or IMA / Media3 |
| Lite / no video | No | Banner / image / native only |

- [ ] Single SDK artifact (no conflicting duplicates)
- [ ] Video placement uses video-enabled artifact (`fullVideo`, not `liteNoVideo`)
- [ ] AAR + adapter from the same release

---

## 4. Step 3 — Host app dependencies

If the SDK chose **IMA**, IMA must be in the **release** APK.

**Android (when using IMA path):**

```groovy
implementation 'com.google.ads.interactivemedia.v3:interactivemedia:3.37.0'
```

**Gradle check:**

```bash
./gradlew :app:dependencies | grep -iE 'bidscube|interactivemedia|media3|ima'
```

**Note:** Inline MP4 on Android **1.2.6+** does not require IMA when `adm` contains `<MediaFile type="video/mp4">`.

---

## 5. Step 4 — Logs and callbacks

Success chain:

1. Video ad request
2. Bid OK (`adm` length > 0)
3. Player choice (`NativeMp4VideoPlayer` vs `IMAPlayerHandler`)
4. `playVast` / playback start
5. `onAdDisplayed` → `onVideoAdStarted`

**Inline MP4:** look for `Selected native MP4 player for inline MediaFile`.

**End card:** only if VAST has companion image — `onEndCardShown`. No companion → dialog closes without preview.

---

## 6. Symptom → fix matrix

| Symptom | Action |
|---------|--------|
| Inline MP4 + `VideoAdPlayer` not found | Upgrade to **1.2.6+** or add IMA dependency |
| Wrapper VAST, no MediaFile | Add IMA or resolve wrapper chain |
| MP4 network error | Fix URL / ATS / firewall |
| `Video playback is not supported` | Replace lite artifact |
| Works in test app, fails in production | Compare AAR vs Maven, release ProGuard, IMA in release |

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

| Gradle flavor | Maven artifact | Inline MP4 | IMA path |
|---------------|----------------|------------|----------|
| `fullVideo` | `bidscube-sdk-full-video` | `NativeMp4VideoPlayer` | `IMAPlayerHandler` |
| `liteNoVideo` | `bidscube-sdk-lite-no-video` | Not supported | Stub |

**Runtime:**

```java
SDKConfig.Builder()
    .videoAdsEnabled(true)
    .build();
```

**`VideoAdPlayerFactory` routing (1.2.6+):**

- Inline `<MediaFile type="video/mp4">` → `NativeMp4VideoPlayer`
- Wrapper / ad tag URL / no MediaFile → `IMAPlayerHandler`

**End card:** shown only when `VastParser.getCompanionImageUrl(adm)` returns a URL.

---

## 9. Five-minute checklist

```
1. adm — InLine + MediaFile mp4?     → direct play possible (Android 1.2.6+)
2. SDK — video-enabled variant?      → if no, switch artifact
3. Logs — which player?               → NativeMp4 vs IMA
4. IMA in release (if IMA path)?      → add dependency if missing
5. MP4 URL live on device?            → if no, network not SDK
```
