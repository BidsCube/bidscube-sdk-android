package com.bidscube.sdk.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VastSkipOffsetTest {

    @Test
    public void parseValue_timeAndZero() {
        assertEquals(VastSkipOffset.Kind.TIME_SECONDS, VastSkipOffset.parseValue("00:00:05").kind);
        assertEquals(5, VastSkipOffset.parseValue("00:00:05").value);
        assertEquals(VastSkipOffset.Kind.TIME_SECONDS, VastSkipOffset.parseValue("00:00:00").kind);
        assertEquals(0, VastSkipOffset.parseValue("00:00:00").value);
        assertEquals(0, VastSkipOffset.parseValue("0").value);
    }

    @Test
    public void parseValue_percent() {
        assertEquals(VastSkipOffset.Kind.PERCENT, VastSkipOffset.parseValue("25%").kind);
        assertEquals(25, VastSkipOffset.parseValue("25%").value);
    }

    @Test
    public void parseValue_invalid() {
        assertEquals(VastSkipOffset.Kind.INVALID, VastSkipOffset.parseValue("not-a-skip").kind);
    }

    @Test
    public void parseFromVast_absentAndPresent() {
        String noSkip = "<VAST><Ad><InLine><Creatives><Creative><Linear>"
                + "<Duration>00:00:10</Duration></Linear></Creative></Creatives></InLine></Ad></VAST>";
        assertEquals(VastSkipOffset.Kind.ABSENT, VastSkipOffset.parseFromVast(noSkip).kind);

        String withSkip = "<VAST><Ad><InLine><Creatives><Creative>"
                + "<Linear skipoffset=\"00:00:00\"><Duration>00:00:10</Duration></Linear>"
                + "</Creative></Creatives></InLine></Ad></VAST>";
        VastSkipOffset parsed = VastSkipOffset.parseFromVast(withSkip);
        assertEquals(VastSkipOffset.Kind.TIME_SECONDS, parsed.kind);
        assertEquals(0, parsed.value);
        assertEquals(10, VastSkipOffset.parseLinearDurationSeconds(withSkip));
    }

    @Test
    public void parseFromVast_percentUsesDuration() {
        String vast = "<VAST><Ad><InLine><Creatives><Creative>"
                + "<Linear skipoffset=\"50%\"><Duration>00:00:20</Duration></Linear>"
                + "</Creative></Creatives></InLine></Ad></VAST>";
        assertEquals(VastSkipOffset.Kind.PERCENT, VastSkipOffset.parseFromVast(vast).kind);
        assertEquals(50, VastSkipOffset.parseFromVast(vast).value);
        assertEquals(20, VastSkipOffset.parseLinearDurationSeconds(vast));
    }
}
