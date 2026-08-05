package com.bidscube.sdk.video;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PostVideoPolicyTest {

    @Test
    public void defaultAutoCloseFalse_withoutCompanion_keepsLastFrame() {
        assertEquals(
                PostVideoPolicy.Action.KEEP_LAST_FRAME,
                PostVideoPolicy.resolve(false, false, false));
    }

    @Test
    public void autoCloseFalse_withCompanion_showsEndCard() {
        assertEquals(
                PostVideoPolicy.Action.SHOW_END_CARD,
                PostVideoPolicy.resolve(false, true, false));
    }

    @Test
    public void autoCloseTrue_withoutCompanion_autoCloses() {
        assertEquals(
                PostVideoPolicy.Action.AUTO_CLOSE,
                PostVideoPolicy.resolve(true, false, false));
    }

    @Test
    public void autoCloseTrue_withCompanion_autoCloses_noEndCard() {
        assertEquals(
                PostVideoPolicy.Action.AUTO_CLOSE,
                PostVideoPolicy.resolve(true, true, false));
    }

    @Test
    public void miniGameActive_notInterrupted_evenWhenAutoCloseTrue() {
        assertEquals(
                PostVideoPolicy.Action.CONTINUE_POST_VIDEO,
                PostVideoPolicy.resolve(true, true, true));
        assertEquals(
                PostVideoPolicy.Action.CONTINUE_POST_VIDEO,
                PostVideoPolicy.resolve(false, false, true));
    }
}
