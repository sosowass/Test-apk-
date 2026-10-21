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
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull("Android screenshot available", bitmap)
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
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
                    all.filter { it.visibility == View.VISIBLE && it.width > 0 }.forEach { view ->
                        val location = IntArray(2); view.getLocationOnScreen(location)
                        assertTrue("Horizontal overflow: ${view.javaClass.simpleName}", location[0] >= -1 && location[0] + view.width <= screenWidth + 1)
                    }
                }
                capture("${profile}_${index + 1}_$name")
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
                assertNotNull(activity.getDrawable(R.drawable.brand_peugeot))
                val texts = descendants(activity.window.decorView).filterIsInstance<TextView>().joinToString(" ") { it.text.toString() }
                assertTrue(texts.contains("Peugeot 308"))
                assertTrue(texts.contains("Profil renseigné manuellement"))
            }
        }
        context.getSharedPreferences("vehicle", 0).edit().clear().commit()
    }
}
