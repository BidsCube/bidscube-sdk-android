package com.bidscube.sdk.view;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VideoDialogRemoteUxTest {

    @Test
    public void back_locked_consumesWithoutCallbacks() {
        CountingActions actions = new CountingActions();
        assertTrue(VideoDialogRemoteUx.dispatchBack(false, false, actions));
        assertEquals(0, actions.skips);
        assertEquals(0, actions.closes);
        assertEquals(0, actions.abandons);
    }

    @Test
    public void back_skipEnabled_invokesSkip() {
        CountingActions actions = new CountingActions();
        assertTrue(VideoDialogRemoteUx.dispatchBack(false, true, actions));
        assertEquals(1, actions.skips);
        assertEquals(0, actions.closes);
    }

    @Test
    public void back_closeAvailable_invokesClose() {
        CountingActions actions = new CountingActions();
        assertTrue(VideoDialogRemoteUx.dispatchBack(true, false, actions));
        assertEquals(0, actions.skips);
        assertEquals(1, actions.closes);
    }

    @Test
    public void back_closePreferredOverSkipWhenBothTrue() {
        CountingActions actions = new CountingActions();
        assertTrue(VideoDialogRemoteUx.dispatchBack(true, true, actions));
        assertEquals(0, actions.skips);
        assertEquals(1, actions.closes);
    }

    @Test
    public void requestSkip_nonRewarded_skipsImmediately() {
        CountingActions actions = new CountingActions();
        VideoDialogRemoteUx.requestSkip(false, actions, null, null);
        assertEquals(1, actions.skips);
        assertEquals(0, actions.abandons);
    }

    @Test
    public void requestSkip_rewarded_doesNotSkipWithoutActivity() {
        // Without Activity confirm cannot show; must not call skip.
        CountingActions actions = new CountingActions();
        VideoDialogRemoteUx.requestSkip(true, actions, null, null);
        assertEquals(0, actions.skips);
        assertEquals(0, actions.abandons);
    }

    @Test
    public void back_rewarded_locked_needsActivityForAbandonConfirm() {
        CountingActions actions = new CountingActions();
        // rewarded=true but activity=null → confirm not shown; no skip/abandon side effect
        assertTrue(VideoDialogRemoteUx.dispatchBack(false, false, true, actions, null, null));
        assertEquals(0, actions.skips);
        assertEquals(0, actions.abandons);
    }

    @Test
    public void back_rewarded_canSkipTrue_stillDoesNotSkipWithoutActivity() {
        CountingActions actions = new CountingActions();
        assertTrue(VideoDialogRemoteUx.dispatchBack(false, true, true, actions, null, null));
        assertEquals(0, actions.skips);
        assertEquals(0, actions.abandons);
    }

    private static final class CountingActions implements VideoDialogRemoteUx.BackActions {
        int skips;
        int closes;
        int abandons;

        @Override
        public void onSkipFromBack() {
            skips++;
        }

        @Override
        public void onCloseFromBack() {
            closes++;
        }

        @Override
        public void onAbandonFromBack() {
            abandons++;
        }
    }
}
