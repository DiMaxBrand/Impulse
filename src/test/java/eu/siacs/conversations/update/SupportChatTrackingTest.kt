package eu.siacs.conversations.update

import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Protocol
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = android.app.Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class SupportChatTrackingTest {
    private fun announcement(id: String = "BUG-AB12", kind: String = "bug") =
        SupportChatTracking.announcement(SupportTracking.Item(id, kind, "Scrolling fixed", "Исправление прокрутки", false))

    @Test fun allFourTypesRoundTripWithRussianAndPassiveWording() {
        listOf("BUG" to "bug", "IMP" to "improvement", "NEW" to "feature", "CHG" to "change").forEach { (prefix, kind) ->
            val text = announcement("$prefix-AB12", kind)
            val parsed = SupportChatTracking.parse(text, false)!!
            assertEquals(kind, parsed.kind)
            assertEquals("$prefix-AB12", parsed.id)
            assertEquals("Исправление прокрутки", parsed.titleRu)
            assertTrue(text.contains("будет показано уведомление"))
            assertTrue(text.contains("A notification will be shown"))
            assertFalse(text.contains("I’ve"))
        }
    }

    @Test fun onlyIncomingDocumentedReportsAreRecognizedAsReports() {
        val text = "Report ID: BUG-AB12\nUpdate channel: beta\n(instructions)\n\nCrash on startup"
        assertEquals("Crash on startup", SupportChatTracking.parse(text, true)!!.titleEn)
        assertNull(SupportChatTracking.parse(text, false))
        assertNull(SupportChatTracking.parse("A quote: BUG-AB12", true))
        assertNull(SupportChatTracking.parse(announcement(), true))
        assertNull(SupportChatTracking.parse("Tracking ID: BUG-AB12", false))
    }

    @Test fun historicalFixUsesEarliestExactReleaseAndNeverMatchesIdPrefixes() {
        val entry = SupportChatTracking.Entry(SupportChatTracking.parse(announcement(), false)!!, "c", "person", 1, "message")
        val matched = SupportChatTracking.match(listOf(entry), listOf(
            UpdateChecker.NewerRelease("1.15.0-beta.300", "BUG-AB12"),
            UpdateChecker.NewerRelease("1.14.0", "BUG-AB12X"),
            UpdateChecker.NewerRelease("1.15.0-beta.100", "Fixed **BUG–AB12**")))
        assertEquals("1.15.0-beta.100", matched.single().fixedIn)
    }

    @Test fun databaseReadsArchivedOldHistoryOnlyForSupportAccountAndDeduplicates() {
        SQLiteDatabase.create(null).use { db ->
            db.execSQL("CREATE TABLE accounts (uuid TEXT, username TEXT, server TEXT)")
            db.execSQL("CREATE TABLE conversations (uuid TEXT, accountUuid TEXT, contactJid TEXT, mode INT, status INT)")
            db.execSQL("CREATE TABLE messages (body TEXT, status INT, timeSent INT, conversationUuid TEXT, type INT, uuid TEXT)")
            db.execSQL("INSERT INTO accounts VALUES ('support','support','example.org'), ('personal','me','example.org')")
            db.execSQL("INSERT INTO conversations VALUES ('archived','support','person@example.org',0,1), ('private','personal','person@example.org',0,0), ('room','support','room@example.org',1,0)")
            fun insert(chat: String, body: String, status: Int, time: Long, type: Int = 0) {
                db.execSQL("INSERT INTO messages VALUES (?,?,?,?,?,?)", arrayOf<Any>(body, status, time, chat, type, "$chat-$time-$type"))
            }
            insert("archived", announcement(), 2, 1)
            insert("archived", announcement(), 2, 2, 3) // recovered resource duplicate
            insert("private", announcement("IMP-AB12", "improvement"), 2, 3)
            insert("room", announcement("NEW-AB12", "feature"), 2, 3)
            insert("archived", "Report ID: BUG-CD34\nUpdate channel: stable\n\nOld report", 0, 4)
            val rows = SupportChatTracking.readDatabase(db, "support", "example.org")
            assertEquals(setOf("BUG-AB12", "BUG-CD34"), rows.map { it.details.id }.toSet())
            assertEquals(2, rows.size)
            assertTrue(rows.all { it.conversationUuid == "archived" })
            // Separate reports in the same conversation must retain separate navigation targets.
            assertEquals("archived-4-0", rows.first { it.details.id == "BUG-CD34" }.messageUuid)
            assertEquals("archived-2-3", rows.first { it.details.id == "BUG-AB12" }.messageUuid)
        }
    }

    @Test fun oldSupportTrackingSurvivesExpiryAndInstalledFixDoesNotNotifyAgain() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("bug_reports", 0).edit().clear().commit()
        val registry = BugReportRegistry(context)
        registry.adopt("BUG-AB12", "beta", "bug", "Fix", "Исправление", 1)
        assertEquals(1, registry.waiting().size)
        assertEquals(1, registry.matchFixed(listOf(UpdateChecker.NewerRelease("1.0.0", "BUG-AB12"))).size)
        registry.dropInstalled("1.15.0")
        assertTrue(registry.fixed().isEmpty())
        assertFalse(registry.adopt("BUG-AB12", "beta", "bug", "Fix", "Исправление"))
        assertTrue(registry.matchFixed(listOf(UpdateChecker.NewerRelease("1.0.0", "BUG-AB12"))).isEmpty())
    }

    @Test fun releaseHistoryReadsBeyondFirstPageForOldFixes() {
        var pages = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            pages++
            val array = JSONArray()
            val count = if (pages == 1) 100 else 1
            repeat(count) { index ->
                array.put(JSONObject().put("tag_name", "1.15.0-beta.${if (pages == 1) index + 2 else 1}")
                    .put("body", if (pages == 2) "BUG-AB12" else "")
                    .put("assets", JSONArray().put(JSONObject().put("name", "Impulse.apk").put("browser_download_url", "https://example.org/app.apk"))))
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(array.toString().toResponseBody("application/json".toMediaType())).build()
        }.build()
        val history = UpdateChecker(client).trackingHistory()!!
        assertEquals(2, pages)
        assertEquals(101, history.size)
        assertEquals("BUG-AB12", history.last().notes)
    }

    @Test fun failedLaterReleasePageDoesNotClaimCompleteHistory() {
        var pages = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            pages++
            val body = if (pages == 1) JSONArray().apply { repeat(100) { put(JSONObject()) } }.toString() else "unavailable"
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(if (pages == 1) 200 else 503).message("response")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        assertNull(UpdateChecker(client).trackingHistory())
        assertEquals(2, pages)
    }
}
