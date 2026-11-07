package et.tibeb.examprep.net

import et.tibeb.examprep.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

@Serializable data class ApiQuestion(
    val type: String, val stem: String, val options: List<String> = emptyList(), val answer: String,
    val numeric_answer: Double = 0.0, val tolerance: Double = 0.0, val unit: String = "",
    val explanation: String = "", val topic: String = "", val difficulty: String = "standard", val marks: Int = 1,
)
@Serializable data class ApiChunk(val unit: String, val topic: String, val text: String)
@Serializable data class ExtractRes(val summary: String, val chunks: List<ApiChunk>, val questions: List<ApiQuestion>)
@Serializable data class NotesRes(val title: String, val markdown: String, val key_formulas: List<String> = emptyList())
@Serializable data class ApiCard(val front: String, val back: String, val topic: String = "")
@Serializable data class FlashRes(val cards: List<ApiCard>)
@Serializable data class ExamRes(val title: String, val minutes: Int, val questions: List<ApiQuestion>)
@Serializable data class GradeRes(val score: Double, val verdict: String, val feedback: String, val mistakes: List<String> = emptyList())

@Serializable data class ExtractReq(val mime: String, val data_b64: String, val subject: String, val title: String, val doc_type: String, val grade: Int)
@Serializable data class NotesReq(val subject: String, val topic: String, val grade: Int, val context: List<String>, val language: String, val style: String)
@Serializable data class FlashReq(val subject: String, val topic: String, val grade: Int, val context: List<String>, val count: Int)
@Serializable data class ExamReq(
    val subject: String, val topic: String, val grade: Int, val context: List<String>, val count: Int,
    val difficulty: String, val types: List<String>, val past_questions: List<String>, val weak_topics: List<String>,
)
@Serializable data class MockPaperReq(
    val subject: String, val stream: String, val count: Int, val context: List<String>,
    val past_questions: List<String>, val weak_topics: List<String>,
)
@Serializable data class GradeReq(
    val subject: String, val question: String, val reference_answer: String, val marks: Int,
    val student_text: String, val image_b64: String? = null, val image_mime: String = "image/jpeg",
)

/** `retryable` = worth trying again later (no signal, timeout, server busy); false = needs the user to fix something. */
class ApiException(message: String, val retryable: Boolean = false) : Exception(message)

typealias Str = (Int, Array<out Any>) -> String

val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

/** Tiny HTTPS client on the platform HttpURLConnection: no OkHttp/Retrofit, so a smaller APK. */
open class Api(private val baseUrl: () -> String, private val token: () -> String?, private val str: (Int, Array<out Any>) -> String) {
    private fun s(id: Int, vararg a: Any) = str(id, a)

    open suspend fun extract(r: ExtractReq): ExtractRes = post("/v1/extract", json.encodeToString(r), 600)
    suspend fun notes(r: NotesReq): NotesRes = post("/v1/notes", json.encodeToString(r), 240)
    suspend fun flashcards(r: FlashReq): FlashRes = post("/v1/flashcards", json.encodeToString(r), 180)
    suspend fun exam(r: ExamReq): ExamRes = post("/v1/exam", json.encodeToString(r), 600)
    suspend fun mockPaper(r: MockPaperReq): ExamRes = post("/v1/mock-paper", json.encodeToString(r), 900)
    suspend fun grade(r: GradeReq): GradeRes = post("/v1/grade", json.encodeToString(r), 240)

    suspend fun ping(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val c = open("/healthz", 15)
            c.responseCode == 200
        }.getOrDefault(false)
    }

    private fun open(path: String, readSeconds: Int): HttpURLConnection {
        val base = baseUrl().trimEnd('/')
        if (!isAllowedUrl(base)) throw ApiException(s(R.string.err_https))
        return (URL(base + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = readSeconds * 1000
            setRequestProperty("Accept", "application/json")
            token()?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
    }

    private suspend inline fun <reified T> post(path: String, body: String, readSeconds: Int): T =
        withContext(Dispatchers.IO) {
            if (token().isNullOrBlank()) throw ApiException(s(R.string.err_need_token))
            val c = open(path, readSeconds)
            try {
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                val bytes = body.toByteArray()
                c.setFixedLengthStreamingMode(bytes.size)
                c.outputStream.use { it.write(bytes) }
                val code = c.responseCode
                val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
                if (code !in 200..299) throw ApiException(errorMessage(code, text), retryable = code == 408 || code == 429 || code >= 500)
                json.decodeFromString<T>(text)
            } catch (e: SocketTimeoutException) {
                throw ApiException(s(R.string.err_timeout), retryable = true)
            } catch (e: IOException) {
                throw ApiException(s(R.string.err_offline), retryable = true)
            } finally {
                c.disconnect()
            }
        }

    private fun errorMessage(code: Int, body: String): String {
        // Known statuses get a translated message; the server's English detail is the fallback.
        val detail = runCatching { json.parseToJsonElement(body).jsonObject["detail"]?.jsonPrimitive?.content }.getOrNull()
        return when (code) {
            401 -> s(R.string.err_401)
            413 -> s(R.string.err_413)
            422 -> s(R.string.err_422)
            429 -> s(R.string.err_429)
            503 -> s(R.string.err_503)
            else -> detail?.takeIf { code == 400 } ?: s(R.string.err_server, code)
        }
    }

    companion object {
        fun isAllowedUrl(u: String) = u.startsWith("https://") || u.startsWith("http://10.0.2.2")
    }
}
