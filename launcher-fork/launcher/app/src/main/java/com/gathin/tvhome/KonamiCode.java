package com.gathin.tvhome;

import android.view.KeyEvent;

/**
 * Watches remote key presses for Up, Up, Down, Down, Left, Right, Left, Right. {@link #onKey} returns true
 * once, on the press that completes the sequence. Any other key breaks the match; extra presses before the sequence are fine. Key repeats from holding a button are ignored.
 */
final class KonamiCode {
    static final int[] SEQUENCE = {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT};

    private final int[] last = new int[SEQUENCE.length];
    private int count;

    boolean onKey(KeyEvent e) {
        if (e.getAction() != KeyEvent.ACTION_DOWN || e.getRepeatCount() > 0) return false;
        return onKeyCode(e.getKeyCode());
    }

    /** Fires when the most recent presses are exactly the sequence, so an extra leading Up never spoils it. */
    boolean onKeyCode(int code) {
        System.arraycopy(last, 1, last, 0, last.length - 1);
        last[last.length - 1] = code;
        if (count < last.length) count++;
        if (count < last.length) return false;
        for (int i = 0; i < last.length; i++) if (last[i] != SEQUENCE[i]) return false;
        count = 0;
        java.util.Arrays.fill(last, 0);
        return true;
    }
}
