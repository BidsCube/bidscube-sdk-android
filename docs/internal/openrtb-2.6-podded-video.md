# OpenRTB 2.6 Podded Video (Android SDK)

Internal reference for **OpenRTB-like podded response** support in the Bidscube Android SDK (**1.2.11**).

> **Scope:** response parsing + podded VAST playback only. The SDK does **not** build or `POST` OpenRTB bid requests (`imp`, `device`, `app`, etc.). `VideoAdUrlBuilder` still issues the legacy `GET` to the SSP; when the server returns JSON with `bids[]`, `openrtb.video`, `slotinpod`, etc., the SDK normalizes and plays the pod.

## What is OpenRTB 2.6 podded video?

OpenRTB 2.6 extends the `video` object with pod fields that describe an ad break **before** rendering:

| Field | Meaning |
|-------|---------|
| `podid` | Identifier for the ad pod / break |
| `poddur` | Total pod duration (seconds) |
| `podseq` | Pod sequence in content |
| `maxseq` | Max ads in pod |
| `slotinpod` | Slot position within pod |
| `rqddurs` / `rqdDurs` | Required slot durations |
| `duration` / `minduration` / `maxduration` | Slot duration hints |
| `mincpmpersec` | Floor hint |

These may arrive under root `video`, `openrtb.video`, `openRtb.video`, `bids[]`, or `seatbid[].bid[].ext`.

## OpenRTB pod vs VAST ad pod

| Layer | Source | Ordering |
|-------|--------|----------|
| **OpenRTB pod** | Auction / delivery JSON | `slotinpod`, `poddur`, `maxseq`, `rqddurs` |
| **VAST ad pod** | Inline VAST XML | `<Ad sequence="N">` |

The SDK connects both via `PoddedAdResponse` → `PoddedPlaybackPlanBuilder` → `VideoPlaybackPlan`.

## Supported response shapes

- Root `adm` + OpenRTB metadata (`openrtb.video`, `openRtb.video`, root `video`)
- Root `bids[]` with per-bid `adm`, `slotinpod`, `impid`
- `seatbid[].bid[]` with bid-level `ext`
- Single adm + `openrtb.video` / `openRtb.video` / `video` object

**Example — multiple bids:**

```json
{
  "podid": "break-1",
  "poddur": 90,
  "bids": [
    { "impid": "slot-1", "slotinpod": 1, "adm": "<VAST>…</VAST>" },
    { "impid": "slot-2", "slotinpod": 2, "adm": "<VAST>…</VAST>" }
  ]
}
```

## Pod classification

| Type | Signals | Playback |
|------|---------|----------|
| **STRUCTURED** | `slotinpod`, `rqddurs`, `maxseq` | Sort by `slotinpod`, validate durations |
| **DYNAMIC** | `poddur`, flexible slots | Fill queue until `poddur` / `maxseq`; skip slots over budget alone |
| **HYBRID** | Some `slotinpod` + `poddur` | Fixed slots first, then dynamic fill |
| **UNKNOWN** | `podid` only | Fallback to VAST sequence or response order |

## Ordering priority

1. OpenRTB `slotinpod`
2. VAST `@sequence` (only when attribute present)
3. Response order

Mismatches are logged; OpenRTB ordering wins when present.

## Duration validation

Configured via `SDKConfig.Builder.videoPodDurationValidationMode`:

- **LENIENT** (default) — log mismatches, continue
- **STRICT** — may reject slots / produce empty plan

## Playback wiring

```
BidscubeResponseParser
→ OpenRtbPoddedResponseNormalizer (when pod metadata present)
→ BidscubeResponse / PoddedAdResponse
→ VideoPodResponseResolver → VideoPlaybackPlan
→ AdDisplayManager → VideoExperienceHelper.showAdPodWithPlan
→ VastAdPodPlayer → PodPlaybackController → Media3VideoSlotPlayer
```

Single (non-pod) ads use `VideoAdPlayerFactory` → `Media3VideoAdPlayer` or `IMAPlayerHandler`.

## SDKConfig (publisher-facing)

```java
import com.bidscube.sdk.openrtb.PodDurationValidationMode;
import com.bidscube.sdk.video.PodSkipPolicy;

new SDKConfig.Builder(context)
    .videoAdsEnabled(true)
    .openRtbPodMetadataEnabled(true)
    .videoPodDurationValidationMode(PodDurationValidationMode.LENIENT)
    .videoPodSkipPolicy(PodSkipPolicy.SKIP_CURRENT_AND_CONTINUE) // or SKIP_ENTIRE_POD
    .videoPodContinueOnSlotError(true)
    .videoPodShowCounter(true)
    .build();
```

## Callback order

1. `onVideoPodStarted` (OpenRTB-enriched plans only)
2. Per slot: `onVideoPodSlotStarted` → indexed `onVideoAdStarted` → … → `onVideoPodSlotCompleted`
3. `onVideoPodDurationMismatch` (when applicable)
4. `onVideoPodCompleted` + legacy `onVideoAdCompleted` / skip
5. `onUserRewarded` — rewarded format only, full pod complete, no skipped slots

## Reward rules

- Interstitial pod: no `onUserRewarded`
- Rewarded pod: reward only if every slot completed (no skip, no early dismiss)
- `videoPodContinueOnSlotError(false)` stops pod on slot error

## End card (pods)

After pod completes, end card uses `VideoPlaybackPlan.getEndCardPreview()`:

- **OpenRTB path:** first slot in plan order with companion `StaticResource`
- **VAST pod path:** last `<Ad>` with companion (reverse document scan)

No companion → no end card (`Skipping end card — no companion preview in VAST`).

## Known limitations

- Multiple `seatbid` pod groups: first pod group only (lexicographic `podid`); others logged as unsupported
- Wrapper / ad-tag URL per bid requires resolvable inline VAST in `adm` for pod builder
- Non-sequenced multi-`<Ad>` VAST without OpenRTB grouping → single best ad (AD_BUFFET), not a pod
- Multiple `<MediaFile>` in one Linear = one rendition, not multiple videos

## Tests

Unit tests: `sdk/src/test/java/com/bidscube/sdk/openrtb/`

- `OpenRtbPoddedResponseNormalizerTest`
- `PoddedPlaybackPlanBuilderTest`
- `PodDurationValidationTest`
- `VideoPodResponseResolverTest`

```bash
./gradlew :sdk:testFullVideoDebugUnitTest
./gradlew :sdk:testLiteNoVideoDebugUnitTest
```

Test app QA (sibling `bidscube-testapp-android`): `OpenRtbPodQaActivity`, assets `openrtb/*.json`.

## Related

- Public README — OpenRTB-like podded response parsing (not full OpenRTB integration)
- Internal `doc/video-interstitial.md` — full video UX spec
