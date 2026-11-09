package com.shilapi.xcertplay.airplay

import org.junit.Assert.*
import org.junit.Test

class Entertainment44100Test {
    @Test fun advertisedAacRateMatchesTheDecoderWithoutResampling() {
        val config=AirPlayConfig("test","02:00:00:00:00:02","02:00:00:00:00:02","366.0",
            main=AirPlayDisplayConfig(1280,720), entertainmentSampleRate=44100, mainBufferedAudio=true)
        val formats=AirPlayInfoPlist.build(config)["audioFormats"] as List<*>
        val media=formats.map { it as Map<*,*> }.filter { it["audioType"]=="media" && it["type"]!=100 }
        assertEquals(2,media.size)
        for (format in media) {
            val bits=(format["audioOutputFormats"] as Number).toLong()
            assertEquals(0x400000L,bits)
            val decoded=AudioStreamCodec.fromFormatBits(bits,96,"media")
            assertEquals(AudioCodecKind.AAC_LC,decoded.codec)
            assertEquals(44100,decoded.sampleRate)
            assertEquals(2,decoded.channels)
        }
    }
}
