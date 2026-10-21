package com.mostadiag.app

import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Android rendering and interaction checks. Never injects vehicle readings. */
@RunWith(AndroidJUnit4::class)
class DesignVerificationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        // Idle on the UI thread does not mean the emulator compositor has presented
        // the new frame. Let rendering finish before reading the display surface.
        android.os.SystemClock.sleep(600)
        instrumentation.waitForIdleSync()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull("Android screenshot available", bitmap)
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(folder, "$name.webp").outputStream().use { bitmap.compress(Bitmap.CompressFormat.WEBP, 90, it) }
        bitmap.recycle()
    }
    @Test fun fiveScreensRemainUsableWithoutVehicleData() {
        val context = instrumentation.targetContext
        val profile = InstrumentationRegistry.getArguments().getString("profile") ?: "phone"
        context.getSharedPreferences("vehicle", 0).edit().clear().commit()
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            val names = listOf("accueil", "vehicule", "mesures", "rapport", "parametres")
            names.forEachIndexed { index, name ->
                scenario.onActivity { activity ->
                    val tab = activity.window.decorView.findViewWithTag<View>("tab_$index")
                    assertNotNull("Navigation: $name", tab)
                    assertTrue(tab.performClick())
                }
                instrumentation.waitForIdleSync()
                capture("${profile}_${index + 1}_$name")
                scenario.onActivity { activity ->
                    val all = descendants(activity.window.decorView)
                    val text = all.filterIsInstance<TextView>().joinToString("\n") { it.text.toString() }
                    assertFalse("No invented vehicle", text.contains("PEUGEOT 308"))
                    assertFalse("No invented connected state", text.contains("ELM327 CONNECTÉ"))
                    val tab = activity.window.decorView.findViewWithTag<View>("tab_$index")
                    assertTrue("Selected navigation: $name", tab.isSelected)
                    assertTrue("Visible touch area", tab.width > 0 && tab.height >= (48 * activity.resources.displayMetrics.density).toInt())
                    if (index == 2) {
                        assertTrue(text.contains("En attente"))
                        assertTrue(all.any { it.contentDescription?.contains("indisponible") == true })
                    }
                    if (index == 3) assertTrue(text.contains("Aucune lecture effectuée"))
                    val screenWidth = activity.resources.displayMetrics.widthPixels
                    all.filter { it.tag == "action_tile" || it.tag == "brand_card" }.forEach { card ->
                        assertTrue("Every card must remain visible, including incomplete grid rows", card.height >= (60 * activity.resources.displayMetrics.density).toInt())
                    }
                    all.filter { it.visibility == View.VISIBLE && it.width > 0 }.forEach { view ->
                        val location = IntArray(2); view.getLocationOnScreen(location)
                        assertTrue("Horizontal overflow: ${view.javaClass.simpleName}", location[0] >= -1 && location[0] + view.width <= screenWidth + 1)
                    }
                }
                if (index in 0..2) {
                    scenario.onActivity { activity ->
                        val scroll = descendants(activity.window.decorView).filterIsInstance<ScrollView>().first()
                        scroll.scrollTo(0, scroll.getChildAt(0).height)
                    }
                    capture("${profile}_${index + 1}_${name}_bas")
                }
            }
            scenario.recreate()
            scenario.onActivity { activity -> assertTrue(activity.window.decorView.findViewWithTag<View>("tab_4").isSelected) }
        }
    }
    @Test fun offlineBrandLogosAndVehicleProfile() {
        val context = instrumentation.targetContext
        context.getSharedPreferences("vehicle", 0).edit().putString("make", "Peugeot").putString("model", "308").putInt("year", 2020).commit()
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                val logos = listOf(
                    R.drawable.brand_audi,
                    R.drawable.brand_bmw,
                    R.drawable.brand_citroen,
                    R.drawable.brand_dacia,
                    R.drawable.brand_fiat,
                    R.drawable.brand_ford,
                    R.drawable.brand_honda,
                    R.drawable.brand_hyundai,
                    R.drawable.brand_jeep,
                    R.drawable.brand_kia,
                    R.drawable.brand_mazda,
                    R.drawable.brand_nissan,
                    R.drawable.brand_opel,
                    R.drawable.brand_peugeot,
                    R.drawable.brand_porsche,
                    R.drawable.brand_renault,
                    R.drawable.brand_seat,
                    R.drawable.brand_skoda,
                    R.drawable.brand_suzuki,
                    R.drawable.brand_tesla,
                    R.drawable.brand_toyota,
                    R.drawable.brand_volkswagen,
                    R.drawable.brand_volvo
                )
                logos.forEach { id ->
                    val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                    val drawable = requireNotNull(activity.getDrawable(id))
                    drawable.setBounds(0, 0, 96, 96)
                    drawable.draw(android.graphics.Canvas(bitmap))
                    val pixels = IntArray(96 * 96)
                    bitmap.getPixels(pixels, 0, 96, 0, 0, 96, 96)
                    assertTrue("Offline logo must render: ${activity.resources.getResourceEntryName(id)}", pixels.count { android.graphics.Color.alpha(it) > 0 } > 30)
                    bitmap.recycle()
                }
                val texts = descendants(activity.window.decorView).filterIsInstance<TextView>().joinToString(" ") { it.text.toString() }
                assertTrue(texts.contains("Peugeot 308"))
                assertTrue(texts.contains("Profil renseigné manuellement"))
            }
        }
        context.getSharedPreferences("vehicle", 0).edit().clear().commit()
    }
}
