package et.tibeb.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/** Renders every dashboard tab (light + dark) from the server fixtures to PNGs for design review. */
class RenderScreens {
    private val data = Loaded(
        json.decodeFromString(AdminTest::class.java.getResource("/fixtures/devices.json")!!.readText()),
        null, json.decodeFromString(AdminTest::class.java.getResource("/fixtures/history.json")!!.readText()),
        json.decodeFromString(AdminTest::class.java.getResource("/fixtures/usage.json")!!.readText()), System.currentTimeMillis(),
    ).let { it.copy(selected = it.devices.devices.first()) }

    private fun render(name: String, dark: Boolean, content: @androidx.compose.runtime.Composable () -> Unit) {
        val scene = ImageComposeScene(1280, 860, Density(1f)) {
            AdminTheme(dark) { androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() } }
        }
        scene.render(); scene.render()
        val out = File("../docs/screenshots/admin").apply { mkdirs() }
        File(out, "$name${if (dark) "_dark" else ""}.png").writeBytes(scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
    }

    @Test fun renderAll() {
        Tab.entries.forEach { t -> render("${t.ordinal + 1}_${t.name.lowercase()}", false) { Dashboard(data, null, false, {}, {}, { _, _ -> }, {}, t) } }
        render("1_overview", true) { Dashboard(data, null, false, {}, {}, { _, _ -> }, {}, Tab.OVERVIEW) }
        render("0_connect", false) { ConnectScreen(null, false) { _, _ -> } }
    }
}

