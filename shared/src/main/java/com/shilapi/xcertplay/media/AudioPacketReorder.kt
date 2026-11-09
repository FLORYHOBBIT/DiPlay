package com.shilapi.xcertplay.media

/** Bounded RTP ordering for a music stream. Owned by the decoder worker, never the receive thread. */
internal class AudioPacketReorder<T>(private val sequence: (T) -> Int) {
    private val pending = mutableMapOf<Int, T>()
    private var expected: Int? = null
    private var gapSince: Long? = null
    private var lastOutputNs = 0L
    var lateDropped = 0L; private set
    var reordered = 0L; private set
    var missing = 0L; private set
    var resets = 0L; private set
    val size get() = pending.size

    fun offer(packet: T, nowNs: Long): List<T> {
        val seq = sequence(packet) and 0xffff
        val next = expected
        if (next == null) expected = seq
        else {
            val delta = (seq - next) and 0xffff
            if (delta >= 0x8000) {
                // A sender may restart its sequence after a pause. Never remain stuck until wrap.
                if (nowNs - lastOutputNs < RESET_AFTER_NS) { lateDropped++; return drain(nowNs) }
                pending.clear(); gapSince = null; expected = seq; resets++
            } else if (delta > MAX_PACKETS) {
                missing += delta; pending.clear(); gapSince = null; expected = seq; resets++
            } else if (delta > 0) reordered++
        }
        if (pending.putIfAbsent(seq, packet) != null) lateDropped++
        return drain(nowNs)
    }

    fun drain(nowNs: Long): List<T> {
        val ready = mutableListOf<T>()
        while (pending.isNotEmpty()) {
            val next = expected ?: break
            val packet = pending.remove(next)
            if (packet != null) {
                ready += packet; expected = (next + 1) and 0xffff
                lastOutputNs = nowNs; gapSince = null
                continue
            }
            val since = gapSince ?: nowNs.also { gapSince = it }
            if (nowNs - since < MAX_WAIT_NS && pending.size < MAX_PACKETS) break
            val nearest = pending.keys.minBy { (it - next) and 0xffff }
            missing += (nearest - next) and 0xffff
            expected = nearest; gapSince = null
        }
        return ready
    }

    companion object {
        const val MAX_PACKETS = 64
        const val MAX_WAIT_NS = 80_000_000L
        const val RESET_AFTER_NS = 500_000_000L
    }
}
