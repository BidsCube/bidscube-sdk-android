package com.bidscube.sdk.utils;

import com.bidscube.sdk.models.PlayableAdConfig;
import com.bidscube.sdk.models.VastAdPodItem;

import com.bidscube.sdk.utils.SDKLogger;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class VastAdPodParserTest {

    @Before
    public void disableLogging() {
        SDKLogger.setLoggingEnabled(false);
    }

    private static final String AD_POD_VAST = ""
            + "<VAST version=\"3.0\">"
            + "  <Ad sequence=\"1\" id=\"bumper\">"
            + "    <InLine><AdTitle>Bumper 6s</AdTitle>"
            + "      <Creatives><Creative><Linear>"
            + "        <MediaFiles><MediaFile type=\"video/mp4\">https://example.com/a.mp4</MediaFile></MediaFiles>"
            + "      </Linear></Creative></Creatives>"
            + "    </InLine></Ad>"
            + "  <Ad sequence=\"2\" id=\"main\">"
            + "    <InLine><AdTitle>Main 15s</AdTitle>"
            + "      <Creatives><Creative><Linear skipoffset=\"00:00:03\">"
            + "        <VideoClicks><ClickThrough>https://example.com/click</ClickThrough></VideoClicks>"
            + "        <MediaFiles><MediaFile type=\"video/mp4\">https://example.com/b.mp4</MediaFile></MediaFiles>"
            + "      </Linear></Creative></Creatives>"
            + "    </InLine></Ad>"
            + "</VAST>";

    private static final String PLAYABLE_VAST = ""
            + "<VAST version=\"3.0\"><Ad><InLine>"
            + "  <Creatives><Creative><Linear>"
            + "    <MediaFiles><MediaFile type=\"video/mp4\">https://example.com/intro.mp4</MediaFile></MediaFiles>"
            + "    <Extensions><Extension type=\"bidscubePlayable\">"
            + "      <BidscubePlayable playableUrl=\"playables/hero-runner/index.html\" goalCollectibles=\"3\" maxSeconds=\"15\" heroEmoji=\"🚀\" hint=\"Collect stars\"/>"
            + "    </Extension></Extensions>"
            + "  </Linear></Creative></Creatives>"
            + "</InLine></Ad></VAST>";

    @Test
    public void isAdPod_trueForMultipleAds() {
        assertTrue(VastParser.isAdPod(AD_POD_VAST));
    }

    @Test
    public void parseAdPod_sortedBySequence() {
        List<VastAdPodItem> pod = VastParser.parseAdPod(AD_POD_VAST);
        assertEquals(2, pod.size());
        assertEquals(1, pod.get(0).getSequence());
        assertEquals("https://example.com/a.mp4", pod.get(0).getMediaUrl());
        assertEquals(2, pod.get(1).getSequence());
        assertEquals("https://example.com/click", pod.get(1).getClickThroughUrl());
        assertEquals(3, pod.get(1).getSkipOffsetSeconds());
    }

    @Test
    public void isAdPod_falseForSingleAd() {
        assertFalse(VastParser.isAdPod(PLAYABLE_VAST));
    }

    @Test
    public void parsePlayableConfig_readsExtensionWithHtmlUrl() {
        PlayableAdConfig config = VastParser.parsePlayableConfig(PLAYABLE_VAST);
        assertNotNull(config);
        assertEquals("playables/hero-runner/index.html", config.getPlayableUrl());
        assertTrue(config.hasWebPlayable());
        assertEquals(3, config.getGoalCollectibles());
        assertEquals(15, config.getMaxSeconds());
        assertEquals("🚀", config.getHeroEmoji());
        assertTrue(VastParser.hasPlayableExtension(PLAYABLE_VAST));
    }
}
