# OpenRTB 2.6 Podded Video (Android SDK)

Internal reference for **OpenRTB-like podded response** support in the Bidscube Android SDK.

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
| `rqddurs` | Required slot durations |
| `mincpmpersec` | Floor hint |

These may arrive in the SDK JSON response under `openrtb.video`, root `video`, `bids[]`, or `seatbid[].bid[].ext`.

## OpenRTB pod vs VAST ad pod

| Layer | Source | Ordering |
|-------|--------|----------|
| **OpenRTB pod** | Auction / delivery JSON | `slotinpod`, `poddur`, `maxseq`, `rqddurs` |
| **VAST ad pod** | Inline VAST XML | `<Ad sequence="N">` |

The SDK connects both via `PoddedAdResponse` → `PoddedPlaybackPlanBuilder` → `VideoPlaybackPlan`.

## Supported response shapes

**A. Single adm + OpenRTB metadata**

```json
{ "adm": "<VAST>…</VAST>", "openrtb": { "video": { "podid": "break-1", "poddur": 90 } } }
```

**B. Multiple bids grouped by `podid`**

```json
{ "podid": "break-1", "bids": [ { "impid": "slot-1", "slotinpod": 1, "adm": "…" } ] }
```

## Pod classification

| Type | Signals | Playback |
|------|---------|----------|
| **STRUCTURED** | `slotinpod`, `rqddurs`, `maxseq` | Sort by `slotinpod`, validate durations |
| **DYNAMIC** | `poddur`, flexible slots | Fill queue, stop at `poddur` / `maxseq` |
| **HYBRID** | Some `slotinpod` + `poddur` | Fixed slots first, then dynamic fill |
| **UNKNOWN** | `podid` only | Fallback to VAST sequence or response order |

## Ordering priority

1. OpenRTB `slotinpod`
2. VAST `@sequence` (only when attribute present)
3. Response order

Mismatches between OpenRTB and VAST are logged; OpenRTB wins.

## Duration validation

Default: **LENIENT** (`VideoPodConfig.durationValidationMode`).

- Slot `rqddurs` vs VAST `Duration` → log mismatch
- Total played vs `poddur` → log overfill / underfill
- **STRICT** mode can reject the plan (empty plan)

## Playback wiring

```
BidscubeResponseParser → PoddedAdResponse
VideoPodResponseResolver → VideoPlaybackPlan
AdDisplayManager → VideoExperienceHelper.showAdPodWithPlan
VastAdPodPlayer.playPlan → PodPlaybackController (Media3 VideoSlotPlayer)
```

## Callback order

1. `onVideoPodStarted`
2. Per slot: `onVideoPodSlotStarted` → `onVideoAdStarted(index)` → … → `onVideoPodSlotCompleted` / skip
3. `onVideoPodDurationMismatch` (if applicable, before/during pod)
4. `onVideoPodCompleted` + legacy `onVideoAdCompleted` / skip
5. Reward: `onUserRewarded` only when pod completes without skipped slots (rewarded format)

## Reward rules

- Interstitial pod: no reward callback
- Rewarded pod: reward only if every slot completed (no skip, no early dismiss)
- Slot error with `continueOnSlotError=true` continues pod; `false` stops pod

## Known limitations

- Multiple `seatbid` pods: first pod group only
- VAST wrapper / tag URL pods require inline XML per bid
- Non-sequenced multi-`<Ad>` VAST without OpenRTB grouping → **AD_BUFFET** (single best ad), not a pod
- Multiple `<MediaFile>` in one Linear = one rendition, not multiple videos

## Routing

All pod playback (VAST-only or OpenRTB-enriched) uses a single path:

```
VideoPodResponseResolver → VideoPlaybackPlan
AdDisplayManager → VideoExperienceHelper.showAdPodWithPlan (always)
```

OpenRTB-specific callbacks (`onVideoPodStarted`, `onVideoPodSlot*`, `onVideoPodDurationMismatch`) fire only when `plan.isOpenRtbPodded()`.

## Test fixtures

- Unit tests: `sdk/src/test/java/com/bidscube/sdk/openrtb/`
- Test app QA: **OpenRTB 2.6 Podded Video** screen (`OpenRtbPodQaActivity`)
- Assets: `bidscube-testapp-android/src/main/assets/openrtb/*.json`
