package et.tibeb.examprep.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The backup file is encrypted on the phone with a key derived from the student's recovery code, so
 * Supabase, the server and the admin can't read it. Nothing here needs a library: PBKDF2 is written
 * out because PBKDF2WithHmacSHA256 only exists from Android 8 and this app supports Android 7.
 *
 * File layout (all integers big-endian):
 *   "TIBEBK" | format u8 = 1 | schema i32 | createdAt i64 | iterations i32 | salt[16] | iv[12] | AES-256-GCM(ciphertext+tag)
 * The whole header is authenticated (GCM associated data), so it can't be altered without detection.
 */
object BackupCrypto {
    private const val MAGIC = "TIBEBK"
    private const val FORMAT = 1
    const val ITERATIONS = 200_000

    // No 0/O, 1/I/L or U/V look-alikes: 29 symbols, 25 of them = ~121 bits, typed in five groups of five.
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTWXYZ23456789"
    private const val CODE_LENGTH = 25
    private val random = SecureRandom()

    fun newRecoveryCode(): String =
        (1..CODE_LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.chunked(5).joinToString("-") { it.joinToString("") }

    /** Accepts what a person types: lower case, spaces or missing dashes. Null if it can't be a code. */
    fun normalize(input: String): String? {
        val raw = input.uppercase().filter { it.isLetterOrDigit() }
        if (raw.length != CODE_LENGTH || raw.any { it !in ALPHABET }) return null
        return raw.chunked(5).joinToString("-")
    }

    fun newSalt(): ByteArray = ByteArray(16).also(random::nextBytes)

    /** PBKDF2-HMAC-SHA256 with a 32-byte output (one block), as in RFC 8018. */
    fun deriveKey(code: String, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(code.toByteArray(Charsets.UTF_8), "HmacSHA256")) }
        var u = mac.doFinal(salt + byteArrayOf(0, 0, 0, 1))
        val out = u.copyOf()
        repeat(iterations - 1) {
            u = mac.doFinal(u)
            for (i in out.indices) out[i] = (out[i].toInt() xor u[i].toInt()).toByte()
        }
        return out
    }

    data class Header(val schema: Int, val createdAt: Long, val iterations: Int, val salt: ByteArray)

    class WrongCodeException : Exception("wrong recovery code or damaged backup")
    class NotABackupException : Exception("not a Tibeb backup")

    fun encrypt(plain: ByteArray, key: ByteArray, salt: ByteArray, schema: Int, createdAt: Long, iterations: Int = ITERATIONS): ByteArray {
        val iv = ByteArray(12).also(random::nextBytes)
        val header = header(schema, createdAt, iterations, salt, iv)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        c.updateAAD(header)
        return header + c.doFinal(plain)
    }

    /** Reads the header without the key: when the backup was made, and from which database version. */
    fun readHeader(file: ByteArray): Header {
        val (h, _, _) = parse(file)
        return h
    }

    fun decrypt(file: ByteArray, key: ByteArray): ByteArray {
        val (_, iv, headerLen) = parse(file)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        c.updateAAD(file, 0, headerLen)
        return try { c.doFinal(file, headerLen, file.size - headerLen) } catch (e: AEADBadTagException) { throw WrongCodeException() }
    }

    private fun header(schema: Int, createdAt: Long, iterations: Int, salt: ByteArray, iv: ByteArray): ByteArray =
        ByteArrayOutputStream().also { b ->
            DataOutputStream(b).run {
                write(MAGIC.toByteArray()); writeByte(FORMAT); writeInt(schema); writeLong(createdAt); writeInt(iterations)
                write(salt); write(iv)
            }
        }.toByteArray()

    private fun parse(file: ByteArray): Triple<Header, ByteArray, Int> {
        val len = MAGIC.length + 1 + 4 + 8 + 4 + 16 + 12
        if (file.size < len + 16 || String(file, 0, MAGIC.length) != MAGIC) throw NotABackupException()
        DataInputStream(ByteArrayInputStream(file, MAGIC.length, len - MAGIC.length)).run {
            if (readUnsignedByte() != FORMAT) throw NotABackupException()
            val schema = readInt(); val createdAt = readLong(); val iterations = readInt()
            if (iterations !in 1_000..5_000_000) throw NotABackupException()
            val salt = ByteArray(16).also(::readFully); val iv = ByteArray(12).also(::readFully)
            return Triple(Header(schema, createdAt, iterations, salt), iv, len)
        }
    }
}
