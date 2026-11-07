package et.tibeb.admin

import java.net.ConnectException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration

class AdminException(message: String) : Exception(message)

/** Read-only client for the server's admin API, using the owner's admin key. HTTPS only. */
open class AdminApi(private val baseUrl: String, private val key: String) {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()

    init {
        require(baseUrl.startsWith("https://") || baseUrl.startsWith("http://localhost") || baseUrl.startsWith("http://127.0.0.1")) {
            "The server address must start with https://"
        }
    }

    open fun devices(): Devices = json.decodeFromString(get("/v1/admin/devices"))
    open fun history(device: String, days: Int = 120): History = json.decodeFromString(get("/v1/admin/devices/${enc(device)}/progress?days=$days"))
    open fun usage(device: String, days: Int = 30): Usage = json.decodeFromString(get("/v1/admin/devices/${enc(device)}/usage?days=$days"))

    open fun rename(device: String, label: String) {
        send(HttpRequest.newBuilder(URI(baseUrl.trimEnd('/') + "/v1/admin/devices/${enc(device)}"))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString("""{"label":${json.encodeToString(kotlinx.serialization.serializer<String>(), label)}}""")))
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun get(path: String): String = send(HttpRequest.newBuilder(URI(baseUrl.trimEnd('/') + path)).GET())

    private fun send(b: HttpRequest.Builder): String {
        val req = b.header("Authorization", "Bearer $key").header("Accept", "application/json").timeout(Duration.ofSeconds(30)).build()
        val res = try { http.send(req, HttpResponse.BodyHandlers.ofString()) }
        catch (e: HttpTimeoutException) { throw AdminException("The server took too long to answer.") }
        catch (e: ConnectException) { throw AdminException("Can't reach the server. Check the address and your internet connection.") }
        catch (e: java.io.IOException) { throw AdminException("Connection problem: ${e.message ?: "unknown"}") }
        return when (res.statusCode()) {
            in 200..299 -> res.body()
            401 -> throw AdminException("Admin key rejected. Check the key in the server's ADMIN_TOKEN setting.")
            404 -> throw AdminException("No progress from this phone yet. It is sent when the phone is online.")
            429 -> throw AdminException("Too many wrong admin keys: locked for 15 minutes.")
            else -> throw AdminException("Server error (${res.statusCode()}).")
        }
    }
}
