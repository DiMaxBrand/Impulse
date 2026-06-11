package eu.siacs.conversations.utils

import android.os.Bundle
import android.util.Base64
import android.util.Pair
import androidx.annotation.StringRes
import eu.siacs.conversations.R
import eu.siacs.conversations.entities.Account
import eu.siacs.conversations.entities.Message
import eu.siacs.conversations.utils.Random.SECURE_RANDOM
import eu.siacs.conversations.xmpp.Jid
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.cert.CertificateEncodingException
import java.security.cert.CertificateParsingException
import java.security.cert.X509Certificate
import java.text.Normalizer
import java.util.regex.Pattern
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x500.style.IETFUtils
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder

object CryptoHelper {

    @JvmField
    val UUID_PATTERN: Pattern =
        Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")

    @JvmField
    val ONE: ByteArray = byteArrayOf(0, 0, 0, 1)

    private val CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz123456789+-/#\$!?".toCharArray()
    private const val PW_LENGTH = 12
    private val VOWELS = "aeiou".toCharArray()
    private val CONSONANTS = "bcfghjklmnpqrstvwxyz".toCharArray()
    private val hexArray = "0123456789abcdef".toCharArray()

    @JvmStatic
    fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }

    @JvmStatic
    fun createPassword(random: SecureRandom): String {
        val builder = StringBuilder(PW_LENGTH)
        for (i in 0 until PW_LENGTH) {
            builder.append(CHARS[random.nextInt(CHARS.size - 1)])
        }
        return builder.toString()
    }

    @JvmStatic
    fun pronounceable(): String {
        val rand = SECURE_RANDOM.nextInt(4)
        val output = CharArray(rand * 2 + (5 - rand))
        var vowel = SECURE_RANDOM.nextBoolean()
        for (i in output.indices) {
            output[i] = if (vowel)
                VOWELS[SECURE_RANDOM.nextInt(VOWELS.size)]
            else
                CONSONANTS[SECURE_RANDOM.nextInt(CONSONANTS.size)]
            vowel = !vowel
        }
        return String(output)
    }

    @JvmStatic
    fun hexToBytes(hexString: String): ByteArray {
        val len = hexString.length
        val array = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            array[i / 2] = ((Character.digit(hexString[i], 16) shl 4)
                    + Character.digit(hexString[i + 1], 16)).toByte()
        }
        return array
    }

    @JvmStatic
    fun hexToString(hexString: String): String = String(hexToBytes(hexString))

    @JvmStatic
    fun concatenateByteArrays(a: ByteArray, b: ByteArray): ByteArray {
        val result = ByteArray(a.size + b.size)
        System.arraycopy(a, 0, result, 0, a.size)
        System.arraycopy(b, 0, result, a.size, b.size)
        return result
    }

    /** Escapes usernames or passwords for SASL. */
    @JvmStatic
    fun saslEscape(s: String): String {
        val sb = StringBuilder((s.length * 1.1).toInt())
        for (c in s) {
            when (c) {
                ',' -> sb.append("=2C")
                '=' -> sb.append("=3D")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    @JvmStatic
    fun saslPrep(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFKC)

    @JvmStatic
    fun random(length: Int): String {
        val bytes = ByteArray(length)
        SECURE_RANDOM.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_PADDING or Base64.NO_WRAP or Base64.URL_SAFE)
    }

    @JvmStatic
    fun prettifyFingerprint(fingerprint: String?): String {
        if (fingerprint == null) return ""
        if (fingerprint.length < 40) return fingerprint
        val builder = StringBuilder(fingerprint)
        var i = 8
        while (i < builder.length) {
            builder.insert(i, ' ')
            i += 9
        }
        return builder.toString()
    }

    @JvmStatic
    fun prettifyFingerprintCert(fingerprint: String): String {
        val builder = StringBuilder(fingerprint)
        var i = 2
        while (i < builder.length) {
            builder.insert(i, ':')
            i += 3
        }
        return builder.toString()
    }

    @JvmStatic
    @Throws(CertificateEncodingException::class, IllegalArgumentException::class, CertificateParsingException::class)
    fun extractJidAndName(certificate: X509Certificate): Pair<Jid, String>? {
        val alternativeNames = certificate.subjectAlternativeNames
        val emails = mutableListOf<String>()
        if (alternativeNames != null) {
            for (san in alternativeNames) {
                val type = san[0] as Int
                if (type == 1) {
                    emails.add(san[1] as String)
                }
            }
        }
        val x500name = JcaX509CertificateHolder(certificate).subject
        if (emails.size == 0 && x500name.getRDNs(BCStyle.EmailAddress).isNotEmpty()) {
            emails.add(
                IETFUtils.valueToString(
                    x500name.getRDNs(BCStyle.EmailAddress)[0].first.value
                )
            )
        }
        val name = if (x500name.getRDNs(BCStyle.CN).isNotEmpty())
            IETFUtils.valueToString(x500name.getRDNs(BCStyle.CN)[0].first.value)
        else
            null
        if (emails.size >= 1) {
            return Pair(Jid.of(emails[0]), name)
        } else if (name != null) {
            try {
                val jid = Jid.of(name)
                if (jid.isBareJid && jid.local != null) {
                    return Pair(jid, null)
                }
            } catch (e: IllegalArgumentException) {
                return null
            }
        }
        return null
    }

    @JvmStatic
    fun extractCertificateInformation(certificate: X509Certificate): Bundle {
        val information = Bundle()
        try {
            val holder = JcaX509CertificateHolder(certificate)
            val subject: X500Name = holder.subject
            try {
                information.putString(
                    "subject_cn",
                    subject.getRDNs(BCStyle.CN)[0].first.value.toString()
                )
            } catch (e: Exception) {
                // ignored
            }
            try {
                information.putString(
                    "subject_o",
                    subject.getRDNs(BCStyle.O)[0].first.value.toString()
                )
            } catch (e: Exception) {
                // ignored
            }

            val issuer: X500Name = holder.issuer
            try {
                information.putString(
                    "issuer_cn",
                    issuer.getRDNs(BCStyle.CN)[0].first.value.toString()
                )
            } catch (e: Exception) {
                // ignored
            }
            try {
                information.putString(
                    "issuer_o",
                    issuer.getRDNs(BCStyle.O)[0].first.value.toString()
                )
            } catch (e: Exception) {
                // ignored
            }
            try {
                information.putString("sha1", getFingerprintCert(certificate.encoded))
            } catch (e: Exception) {
                // ignored
            }
            return information
        } catch (e: CertificateEncodingException) {
            return information
        }
    }

    @JvmStatic
    @Throws(NoSuchAlgorithmException::class)
    fun getFingerprintCert(input: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-1")
        val fingerprint = md.digest(input)
        return prettifyFingerprintCert(bytesToHex(fingerprint))
    }

    @JvmStatic
    fun getFingerprint(jid: Jid, androidId: String): String {
        return getFingerprint(jid.toString() + " " + androidId)
    }

    @JvmStatic
    fun getAccountFingerprint(account: Account, androidId: String): String {
        return getFingerprint(account.jid.asBareJid(), androidId)
    }

    @JvmStatic
    fun getFingerprint(value: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-1")
            bytesToHex(md.digest(value.toByteArray(StandardCharsets.UTF_8)))
        } catch (e: Exception) {
            ""
        }
    }

    @JvmStatic
    @StringRes
    fun encryptionTypeToText(encryption: Int): Int {
        return when (encryption) {
            Message.ENCRYPTION_OTR -> R.string.encryption_choice_otr
            Message.ENCRYPTION_AXOLOTL,
            Message.ENCRYPTION_AXOLOTL_NOT_FOR_THIS_DEVICE,
            Message.ENCRYPTION_AXOLOTL_FAILED -> R.string.encryption_choice_omemo
            Message.ENCRYPTION_PGP,
            Message.ENCRYPTION_DECRYPTED,
            Message.ENCRYPTION_DECRYPTION_FAILED -> R.string.encryption_choice_pgp
            else -> R.string.encryption_choice_unencrypted
        }
    }

    @JvmStatic
    fun isPgpEncryptedUrl(url: String?): Boolean {
        if (url == null) return false
        val u = url.lowercase()
        return !u.contains(" ")
                && (u.startsWith("https://") || u.startsWith("http://") || u.startsWith("p1s3://"))
                && u.endsWith(".pgp")
    }
}
