package et.tibeb.admin

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun fixture(name: String) = AdminTest::class.java.getResource("/fixtures/$name.json")!!.readText()

class AdminTest {
    @Test fun parsesTheRealServerResponses() {
        val d = json.decodeFromString<Devices>(fixture("devices"))
        assertEquals("Brother's phone", d.devices.single().label)
        assertEquals(341.5, d.devices.single().latest!!.totals.best_mock_total)
        val h = json.decodeFromString<History>(fixture("history"))
        assertEquals(6, h.history.size)
        assertTrue(h.history.zipWithNext().all { (a, b) -> a.day < b.day }, "history is oldest first")
        val u = json.decodeFromString<Usage>(fixture("usage"))
        assertEquals("/v1/mock-paper", u.by_feature.first().endpoint)
        assertTrue(u.total_usd > 0)
    }

    @Test fun storeRoundTripsAndIsPrivate() {
        val dir = Files.createTempDirectory("tibeb-admin").toFile()
        val s = Store(dir)
        assertNull(s.load())
        s.save("https://tibeb.example.com", "k".repeat(40))
        assertEquals("https://tibeb.example.com" to "k".repeat(40), s.load())
        val file = File(dir, "config.json")
        assertTrue(!file.readText().contains("k".repeat(40)), "key is not stored as plain text")
        if (!System.getProperty("os.name").lowercase().contains("win"))
            assertEquals("rw-------", Files.getPosixFilePermissions(file.toPath()).let { java.nio.file.attribute.PosixFilePermissions.toString(it) })
        s.forget(); assertNull(s.load())
    }

    @Test fun niceScales() {
        assertEquals(20.0, niceCeil(16.8)); assertEquals(1.0, niceCeil(0.98)); assertEquals(250.0, niceCeil(201.0))
        assertEquals(0.5, niceCeil(0.47)); assertEquals(1.0, niceCeil(0.0))
    }

    @Test fun refusesPlainHttpServers() {
        assertFailsWith<IllegalArgumentException> { AdminApi("http://tibeb.example.com", "k".repeat(40)) }
    }

    @Test fun mapsServerErrorsToClearMessages() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/admin/devices") { ex ->
            val code = if (ex.requestHeaders.getFirst("Authorization") == "Bearer " + "g".repeat(40)) 200 else 401
            val body = if (code == 200) fixture("devices").toByteArray() else "{}".toByteArray()
            ex.sendResponseHeaders(code, body.size.toLong()); ex.responseBody.use { it.write(body) }
        }
        server.createContext("/v1/admin/devices/x/usage") { ex -> ex.sendResponseHeaders(429, -1); ex.close() }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            assertEquals(1, AdminApi(base, "g".repeat(40)).devices().devices.size)
            assertTrue(assertFailsWith<AdminException> { AdminApi(base, "b".repeat(40)).devices() }.message!!.contains("rejected"))
            assertTrue(assertFailsWith<AdminException> { AdminApi(base, "g".repeat(40)).usage("x") }.message!!.contains("locked"))
        } finally { server.stop(0) }
    }
}
