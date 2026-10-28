package com.gathin.tvhome;

import static android.view.KeyEvent.*;
import static org.junit.Assert.*;

import org.junit.Test;

public class KonamiCodeTest {
    private static boolean feed(KonamiCode k, int... codes) {
        boolean hit = false;
        for (int c : codes) hit = k.onKeyCode(c);
        return hit;
    }

    @Test public void exactSequenceFiresOnTheLastKeyOnly() {
        KonamiCode k = new KonamiCode();
        assertFalse(feed(k, KEYCODE_DPAD_UP, KEYCODE_DPAD_UP, KEYCODE_DPAD_DOWN, KEYCODE_DPAD_DOWN, KEYCODE_DPAD_LEFT, KEYCODE_DPAD_RIGHT, KEYCODE_DPAD_LEFT));
        assertTrue(k.onKeyCode(KEYCODE_DPAD_RIGHT));
    }

    @Test public void sameSequenceAgainFiresAgain() {
        KonamiCode k = new KonamiCode();
        int[] s = KonamiCode.SEQUENCE;
        assertTrue(feed(k, s));
        assertTrue(feed(k, s));
    }

    @Test public void wrongKeyRestarts() {
        KonamiCode k = new KonamiCode();
        assertFalse(feed(k, KEYCODE_DPAD_UP, KEYCODE_DPAD_UP, KEYCODE_DPAD_DOWN, KEYCODE_DPAD_CENTER, KEYCODE_DPAD_DOWN, KEYCODE_DPAD_LEFT, KEYCODE_DPAD_RIGHT, KEYCODE_DPAD_LEFT, KEYCODE_DPAD_RIGHT));
    }

    @Test public void extraUpBeforeStartStillWorks() {
        KonamiCode k = new KonamiCode();
        feed(k, KEYCODE_DPAD_UP);
        assertTrue(feed(k, KonamiCode.SEQUENCE));
    }

    @Test public void wrongOrderDoesNotFire() {
        KonamiCode k = new KonamiCode();
        assertFalse(feed(k, KEYCODE_DPAD_UP, KEYCODE_DPAD_UP, KEYCODE_DPAD_DOWN, KEYCODE_DPAD_DOWN, KEYCODE_DPAD_RIGHT, KEYCODE_DPAD_LEFT, KEYCODE_DPAD_RIGHT, KEYCODE_DPAD_LEFT));
    }
}
