package com.bidscube.sdk.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class VastParserInlineMp4Test {

    private static final String DOORDASH_VAST =
            "<VAST version=\"3.0\">"
                    + "   <Ad id=\"20\">"
                    + "     <InLine>"
                    + "        <AdSystem version=\"3.0\">Bidscube</AdSystem>"
                    + "        <AdTitle><![CDATA[Doordash-35min-burger-3-1x1.mp4]]></AdTitle>"
                    + "        <Creatives>"
                    + "         <Creative>"
                    + "           <Linear>"
                    + "              <Duration>00:00:12.867</Duration>"
                    + "              <MediaFiles>"
                    + "               <MediaFile delivery=\"progressive\" type=\"video/mp4\""
                    + "                 bitrate=\"800\" width=\"1024\" height=\"1024\">"
                    + "                 <![CDATA[ https://assets.remerge.io/ad_assets/files/003/411/782/"
                    + "1024x1024_800_mp4/Doordash-35min-burger-3-1x1.mp4 ]]>"
                    + "               </MediaFile>"
                    + "             </MediaFiles>"
                    + "            </Linear>"
                    + "         </Creative>"
                    + "       </Creatives>"
                    + "      </InLine>"
                    + "   </Ad>"
                    + " </VAST>";

    @Test
    public void getMediaFileUrl_extractsInlineMp4FromDoordashVast() {
        String url = VastParser.getMediaFileUrl(DOORDASH_VAST);
        assertNotNull(url);
        assertEquals(
                "https://assets.remerge.io/ad_assets/files/003/411/782/"
                        + "1024x1024_800_mp4/Doordash-35min-burger-3-1x1.mp4",
                url);
    }

    @Test
    public void getMediaFileUrl_returnsNullForWrapperOnlyVast() {
        String wrapper =
                "<VAST version=\"3.0\"><Ad><Wrapper>"
                        + "<VASTAdTagURI><![CDATA[https://example.com/vast.xml]]></VASTAdTagURI>"
                        + "</Wrapper></Ad></VAST>";
        assertNull(VastParser.getMediaFileUrl(wrapper));
    }
}
