package dev.tidytv.app

import dev.tidytv.app.ui.screens.TouchpadMath
import dev.tidytv.core.ops.RemoteKey
import org.junit.Test
import org.junit.Assert.assertEquals

class TouchpadMathTest {
    private fun pad() = TouchpadMath(step = 100f)

    @Test fun tinyDragEmitsNothing() {
        val p = pad()
        assertEquals(emptyList<RemoteKey>(), p.drag(30f, 10f))
        assertEquals(emptyList<RemoteKey>(), p.drag(30f, -10f))
    }

    @Test fun crossingTheThresholdEmitsOneKey() {
        val p = pad()
        assertEquals(emptyList<RemoteKey>(), p.drag(99f, 0f))
        assertEquals(listOf(RemoteKey.RIGHT), p.drag(1f, 0f))
    }

    @Test fun eachDirection() {
        assertEquals(listOf(RemoteKey.LEFT), pad().drag(-120f, 0f))
        assertEquals(listOf(RemoteKey.UP), pad().drag(0f, -120f))
        assertEquals(listOf(RemoteKey.DOWN), pad().drag(0f, 120f))
    }

    @Test fun dominantAxisWins() {
        assertEquals(listOf(RemoteKey.RIGHT), pad().drag(140f, 90f))
        assertEquals(listOf(RemoteKey.DOWN), pad().drag(90f, 140f))
    }

    @Test fun diagonalDoesNotLeakTheOtherAxis() {
        val p = pad()
        // Mostly horizontal with a wobble: one Right, no Down afterwards from the leftover wobble.
        assertEquals(listOf(RemoteKey.RIGHT), p.drag(110f, 60f))
        assertEquals(emptyList<RemoteKey>(), p.drag(0f, 30f))
    }

    @Test fun longContinuousDragEmitsOneKeyPerStep() {
        val p = pad()
        val keys = (1..100).flatMap { p.drag(10f, 0f) }
        assertEquals(List(10) { RemoteKey.RIGHT }, keys)
    }

    @Test fun oneBigJumpEmitsSeveralKeys() {
        assertEquals(List(3) { RemoteKey.UP }, pad().drag(0f, -350f))
    }

    @Test fun reversingDirectionCancelsProgress() {
        val p = pad()
        assertEquals(emptyList<RemoteKey>(), p.drag(80f, 0f))
        assertEquals(emptyList<RemoteKey>(), p.drag(-60f, 0f))
        assertEquals(listOf(RemoteKey.LEFT), p.drag(-130f, 0f))
    }

    @Test fun resetForgetsPartialDrag() {
        val p = pad()
        p.drag(90f, 0f)
        p.reset()
        assertEquals(emptyList<RemoteKey>(), p.drag(20f, 0f))
    }
}
