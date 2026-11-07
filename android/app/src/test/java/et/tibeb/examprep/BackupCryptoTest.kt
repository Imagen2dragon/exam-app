package et.tibeb.examprep

import et.tibeb.examprep.backup.BackupCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoTest {
    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test fun pbkdf2MatchesTheStandardTestVectors() {
        // PBKDF2-HMAC-SHA256 vectors (RFC 7914 section 11 / widely published), 32-byte output.
        assertArrayEquals(hex("120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b"),
            BackupCrypto.deriveKey("password", "salt".toByteArray(), 1))
        assertArrayEquals(hex("ae4d0c95af6b46d32d0adff928f06dd02a303f8ef3c251dfd6e2d85a95474c43"),
            BackupCrypto.deriveKey("password", "salt".toByteArray(), 2))
        assertArrayEquals(hex("c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a"),
            BackupCrypto.deriveKey("password", "salt".toByteArray(), 4096))
    }

    @Test fun recoveryCodesAreLongRandomAndEasyToType() {
        val codes = (1..200).map { BackupCrypto.newRecoveryCode() }
        assertEquals(200, codes.toSet().size)
        codes.forEach { c ->
            assertTrue(c, Regex("[A-Z2-9]{5}(-[A-Z2-9]{5}){4}").matches(c))
            assertFalse(c, c.any { it in "01ILOUV" })     // no look-alike characters
        }
        val c = codes.first()
        assertEquals(c, BackupCrypto.normalize(c.lowercase().replace("-", " ")))
        assertEquals(c, BackupCrypto.normalize(c.replace("-", "")))
        assertNull(BackupCrypto.normalize(c.dropLast(1)))
        assertNull(BackupCrypto.normalize(c.dropLast(1) + "O"))
    }

    @Test fun encryptsAndOnlyTheRightCodeDecrypts() {
        val code = BackupCrypto.newRecoveryCode(); val salt = BackupCrypto.newSalt()
        val key = BackupCrypto.deriveKey(code, salt, 2_000)
        val plain = "Ohm's law: V = I R. Secret study notes.".toByteArray()
        val file = BackupCrypto.encrypt(plain, key, salt, schema = 4, createdAt = 1_794_000_000_000, iterations = 2_000)

        assertFalse(String(file, Charsets.ISO_8859_1).contains("Ohm's law"))
        val h = BackupCrypto.readHeader(file)
        assertEquals(4, h.schema); assertEquals(1_794_000_000_000, h.createdAt); assertEquals(2_000, h.iterations)
        assertArrayEquals(salt, h.salt)
        assertArrayEquals(plain, BackupCrypto.decrypt(file, BackupCrypto.deriveKey(code, h.salt, h.iterations)))

        val wrong = BackupCrypto.deriveKey(BackupCrypto.newRecoveryCode(), salt, 2_000)
        assertThrows(BackupCrypto.WrongCodeException::class.java) { BackupCrypto.decrypt(file, wrong) }
    }

    @Test fun anyChangeToTheFileIsDetected() {
        val salt = BackupCrypto.newSalt(); val key = BackupCrypto.deriveKey("K", salt, 1_000)
        val file = BackupCrypto.encrypt(ByteArray(500) { it.toByte() }, key, salt, 4, 1L, 1_000)
        listOf(8, 12, file.size - 1, file.size / 2).forEach { i ->        // schema, createdAt, tag, body
            val bad = file.copyOf().also { it[i] = (it[i].toInt() xor 1).toByte() }
            assertThrows(BackupCrypto.WrongCodeException::class.java) { BackupCrypto.decrypt(bad, key) }
        }
        assertThrows(BackupCrypto.NotABackupException::class.java) { BackupCrypto.readHeader("hello".toByteArray()) }
    }

    @Test fun derivingTheRealKeyIsQuickEnough() {
        val t = System.nanoTime()
        BackupCrypto.deriveKey(BackupCrypto.newRecoveryCode(), BackupCrypto.newSalt())
        val ms = (System.nanoTime() - t) / 1_000_000
        assertTrue("200k rounds took $ms ms", ms < 5_000)
    }
}
