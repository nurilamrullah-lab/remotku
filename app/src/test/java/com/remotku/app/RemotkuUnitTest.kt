package com.remotku.app

import com.remotku.app.core.proto.ProtoFraming
import com.remotku.app.core.proto.ProtoMessage
import com.remotku.app.core.proto.ProtoWriter
import com.remotku.app.data.ir.IrDatabaseParser
import com.remotku.app.data.ir.ac.AcChange
import com.remotku.app.data.ir.ac.AcEncoder
import com.remotku.app.data.ir.ac.AcState
import com.remotku.app.data.ir.protocol.IrEncoders
import com.remotku.app.data.ir.protocol.IrProtocol
import com.remotku.app.data.tv.atv.AtvPairingSession
import com.remotku.app.data.tv.atv.InvalidPairingCodeException
import com.remotku.app.domain.ir.IrCategory
import com.remotku.app.domain.ir.IrKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey

class ProtoTest {
    @Test
    fun roundTripsNestedMessagesThroughFraming() {
        val payload = ProtoWriter()
            .int(1, 2).int(2, 200)
            .message(10) { string(1, "atvremote"); string(2, "Remotku") }
            .int(3, 300_000)
            .toByteArray()
        val out = ByteArrayOutputStream()
        ProtoFraming.write(out, payload)
        val parsed = ProtoMessage.parse(ProtoFraming.read(ByteArrayInputStream(out.toByteArray())))
        assertEquals(200, parsed.int(2))
        assertEquals(300_000, parsed.int(3))
        assertEquals("Remotku", parsed.message(10)?.string(2))
    }

    @Test
    fun encodesKeyInjectLikeReferenceImplementation() {
        // remote_key_inject { key_code: KEYCODE_HOME (3), direction: SHORT (3) } == 52 04 08 03 10 03
        val bytes = ProtoWriter().message(10) { int(1, 3); int(2, 3) }.toByteArray()
        assertArrayEquals(byteArrayOf(0x52, 0x04, 0x08, 0x03, 0x10, 0x03), bytes)
    }
}

class PairingSecretTest {
    private fun rsa() = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().public as RSAPublicKey

    private fun BigInteger.unsigned() = toByteArray().let { if (it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it }

    @Test
    fun acceptsCodeWhoseFirstByteMatchesTheHash() {
        val client = rsa()
        val server = rsa()
        val tail = "A1B2"
        val digest = MessageDigest.getInstance("SHA-256").apply {
            update(client.modulus.unsigned()); update(client.publicExponent.unsigned())
            update(server.modulus.unsigned()); update(server.publicExponent.unsigned())
            update(byteArrayOf(0xA1.toByte(), 0xB2.toByte()))
        }.digest()
        val code = "%02X".format(digest[0]) + tail
        assertArrayEquals(digest, AtvPairingSession.computeSecret(code.lowercase(), client, server))
    }

    @Test(expected = InvalidPairingCodeException::class)
    fun rejectsNonHexCode() {
        AtvPairingSession.computeSecret("ZZ1234", rsa(), rsa())
    }
}

class IrEncoderTest {
    @Test
    fun necFrameHasLeaderThirtyTwoBitsAndRepeat() {
        val s = IrEncoders.encode(IrProtocol.NEC, 0x04, 0x08)
        assertEquals(38_000, s.frequencyHz)
        assertEquals(9_000, s.pattern[0])
        assertEquals(4_500, s.pattern[1])
        // leader(2) + 32 bits*2 + stop mark + frame gap = 68, then repeat mark/space/mark = 3
        assertEquals(71, s.pattern.size)
        // LG power: address 0x04 LSB-first -> bits 0,0,1 => third bit has long space.
        assertEquals(562 * 3, s.pattern[7])
        assertEquals(108_000, s.pattern.take(68).sum())
    }

    @Test
    fun rc5StartsWithMarkAndTogglesChangePattern() {
        val a = IrEncoders.encode(IrProtocol.RC5, 0, 0x0C, toggle = false)
        val b = IrEncoders.encode(IrProtocol.RC5, 0, 0x0C, toggle = true)
        assertTrue(a.pattern.all { it > 0 })
        assertTrue(!a.pattern.contentEquals(b.pattern))
    }

    @Test
    fun sircRepeatsThreeFramesAt45ms() {
        val s = IrEncoders.encode(IrProtocol.SIRC12, 1, 0x15)
        assertEquals(135_000L, s.durationMicros)
        assertEquals(3, s.pattern.count { it == 2_400 })
    }

    @Test
    fun greeChecksumMatchesKnownState() {
        // Known Gree frame: cool, power on, 24°C (IRremoteESP8266 test vector style)
        val bytes = intArrayOf(0x09, 0x08, 0x20, 0x50, 0x00, 0x20, 0x00, 0x00)
        val expected = (10 + 0x9 + 0x8 + 0x0 + 0x0 + 0x0 + 0x2 + 0x0) and 0xF
        assertEquals(expected, AcEncoder.Gree.checksum(bytes))
    }

    @Test
    fun everyAcEncoderProducesAPatternStartingWithAMark() {
        listOf("AC_GREE", "AC_DAIKIN", "AC_PANASONIC", "AC_LG", "AC_SHARP", "AC_AUX").forEach { id ->
            val encoder = AcEncoder.forProtocol(id)!!
            AcChange.entries.forEach { change ->
                val signal = encoder.encode(AcState(power = true), change)
                assertTrue("$id $change", signal.pattern.size > 20 && signal.pattern.all { it > 0 })
                assertTrue("$id under 2s", signal.durationMicros < 2_000_000)
            }
        }
    }

    @Test
    fun lgAcPowerOffIsTheFixedCommand() {
        assertEquals(0L, AcEncoder.Lg.checksum(0x88C0050L) - 1L)
    }
}

class IrDatabaseTest {
    @Test
    fun bundledDatabaseCoversRequiredBrands() {
        val asset = listOf("src/main/assets/ir_profiles.json", "app/src/main/assets/ir_profiles.json")
            .map(::File).first { it.exists() }
        val profiles = IrDatabaseParser.parse(asset.readText())
        val brands = profiles.groupBy({ it.category }, { it.brand })
        assertTrue(brands[IrCategory.STB]!!.containsAll(listOf("Matrix Apple", "Luby", "Evercoss", "Tanaka", "Polytron PDV", "Advance")))
        assertTrue(brands[IrCategory.TV]!!.containsAll(listOf("Polytron", "Samsung", "LG", "TCL", "Coocaa", "Sharp", "Xiaomi TV", "Akari")))
        assertTrue(brands[IrCategory.AC]!!.containsAll(listOf("Daikin", "Panasonic", "Sharp", "Gree", "LG", "Aux")))
        profiles.filter { !it.isAc }.forEach {
            assertTrue("${it.id} has power", IrKey.POWER in it.codes)
            IrProtocol.valueOf(it.protocol)
        }
        profiles.filter { it.isAc }.forEach { assertTrue(it.id, AcEncoder.forProtocol(it.protocol) != null) }
        assertEquals(profiles.size, profiles.map { it.id }.toSet().size)
    }
}
