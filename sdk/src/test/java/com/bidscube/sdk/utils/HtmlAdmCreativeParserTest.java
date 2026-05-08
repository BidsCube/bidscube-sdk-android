package com.bidscube.sdk.utils;

import com.bidscube.sdk.models.natives.NativeAd;
import com.bidscube.sdk.models.natives.NativeAsset;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HtmlAdmCreativeParserTest {

    @Test
    public void parse_sampleAdm_mainImageClickAndImpressionTracker() {
        String adm =
                "<a href=\"https://www.google.com\" target=\"_blank\">\n"
                        + "  <img src=\"https://ftp.da-dsp.com/11060-82630d4c5e3cefcfb41960904b057cb1.jpeg\" alt=\"\">\n"
                        + "</a>\n"
                        + "<img src=\"https://da-dsp.com/?t=impr&bwpr=${AUCTION_PRICE}&uniq=abc\" width=\"1\" height=\"1\" alt=\"\">";

        NativeAd ad = HtmlAdmCreativeParser.parseToNativeAd(adm);
        assertNotNull(ad);
        assertEquals(1, ad.assets.size());
        assertNotNull(ad.assets.get(0).img);
        assertEquals(
                "https://ftp.da-dsp.com/11060-82630d4c5e3cefcfb41960904b057cb1.jpeg",
                ad.assets.get(0).img.url);
        assertNotNull(ad.link);
        assertEquals("https://www.google.com", ad.link.url);
        assertEquals(1, ad.imptrackers.size());
        assertTrue(ad.imptrackers.get(0).contains("t=impr"));
    }

    @Test
    public void parse_onlyOneVisibleImage_assetCount() {
        String adm =
                "<a href=\"https://www.google.com\"><img src=\"https://cdn.example.com/banner.png\"></a>"
                        + "<img src=\"https://t.example.com/pixel.gif?x=1\" width=\"1\" height=\"1\">";

        NativeAd ad = HtmlAdmCreativeParser.parseToNativeAd(adm);
        assertNotNull(ad);
        int imageAssets = 0;
        for (NativeAsset a : ad.assets) {
            if (a != null && a.img != null && a.img.url != null) {
                imageAssets++;
            }
        }
        assertEquals(1, imageAssets);
    }

    @Test
    public void parse_rejectsNonImageExtensionAsMain() {
        String adm = "<img src=\"https://da-dsp.com/?t=impr&x=1\" width=\"1\" height=\"1\">";
        assertNull(HtmlAdmCreativeParser.parseToNativeAd(adm));
    }

    @Test
    public void parse_tCheckGoesToImpressionTrackers() {
        String adm =
                "<img src=\"https://cdn.example.com/a.gif\">"
                        + "<img src=\"https://x.com/?t=check&id=1\" width=\"1\" height=\"1\">";
        NativeAd ad = HtmlAdmCreativeParser.parseToNativeAd(adm);
        assertNotNull(ad);
        assertEquals(1, ad.imptrackers.size());
        assertTrue(ad.imptrackers.get(0).contains("t=check"));
    }

    @Test
    public void parse_tCheckUrlInsideScript_goesToImpressionTrackers() {
        String adm =
                "<div id=\"w1\"><a href=\"https://www.google.com\"><img src=\"https://ftp.da-dsp.com/x.jpeg\"></a></div>"
                        + "<script>"
                        + "var newImg = document.createElement('img');"
                        + "newImg.setAttribute('src', 'https://da-dsp.com/?t=check&uniq=abc&u=' + encodeURIComponent('x'));"
                        + "</script>";

        NativeAd ad = HtmlAdmCreativeParser.parseToNativeAd(adm);
        assertNotNull(ad);
        assertEquals(1, ad.imptrackers.size());
        assertTrue(ad.imptrackers.iterator().next().contains("t=check"));
    }
}
