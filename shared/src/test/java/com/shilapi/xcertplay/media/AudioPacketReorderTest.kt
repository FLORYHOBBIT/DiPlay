package com.shilapi.xcertplay.media

import org.junit.Assert.*
import org.junit.Test

class AudioPacketReorderTest {
    private fun order() = AudioPacketReorder<Int> { it }
    @Test fun continuousPacketsHaveNoExtraDelay() {
        val q = order()
        for (n in 10..30) assertEquals(listOf(n), q.offer(n, n * 1_000_000L))
    }
    @Test fun eightLatePacketsAreDecodedInSequence() {
        val q = order(); q.offer(4938, 0)
        assertTrue(q.offer(4947, 1).isEmpty())
        val output = (4939..4946).flatMap { q.offer(it, 2) }
        assertEquals((4939..4947).toList(), output)
        assertEquals(0, q.size)
    }
    @Test fun aMissingPacketCannotBlockTheStreamIndefinitely() {
        val q = order(); q.offer(10, 0);q.offer(12, 1)
        assertTrue(q.drain(AudioPacketReorder.MAX_WAIT_NS).isEmpty())
        assertEquals(listOf(12), q.drain(AudioPacketReorder.MAX_WAIT_NS + 1))
        assertEquals(1L, q.missing)
        assertTrue(q.offer(11, AudioPacketReorder.MAX_WAIT_NS + 2).isEmpty())
    }
    @Test fun duplicateAndSequenceWrapAreHandled() {
        val q=order();assertEquals(listOf(65535),q.offer(65535,0))
        assertTrue(q.offer(65535,1).isEmpty());assertEquals(listOf(0),q.offer(0,2))
        assertEquals(1L,q.lateDropped)
    }
    @Test fun capacityAndLargeSequenceJumpsStayBounded() {
        val q=order();q.offer(0,0)
        val output=(2..65).flatMap { q.offer(it,1) }
        assertEquals((2..65).toList(),output);assertEquals(0,q.size)
        assertEquals(listOf(400),q.offer(400,2));assertEquals(1L,q.resets)
    }
    @Test fun senderCanRestartSequenceAfterSilence() {
        val q=order();q.offer(3000,0)
        assertEquals(listOf(0),q.offer(0,AudioPacketReorder.RESET_AFTER_NS))
        assertEquals(1L,q.resets)
    }
}
