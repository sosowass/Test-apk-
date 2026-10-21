package com.mostadiag.app

import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.AutoCompleteTextView
import android.view.inspector.WindowInspector
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
        context.getSharedPreferences("vehicle", 0).edit().putString("make", "Lifan").putString("model", "520").putInt("year", 2020).commit()
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
                assertTrue(texts.contains("Lifan 520"))
                assertTrue(texts.contains("Profil renseigné manuellement"))
            }
            val profile = InstrumentationRegistry.getArguments().getString("profile") ?: "phone"
            scenario.onActivity { activity -> activity.window.decorView.findViewWithTag<View>("tab_1").performClick() }
            capture("${profile}_lifan_520_profile")
        }
        context.getSharedPreferences("vehicle", 0).edit().clear().commit()
    }

    @android.annotation.TargetApi(29)
    @Test fun offlineCatalogSuggestionsAndManualEntry() {
        val context = instrumentation.targetContext
        val catalog = VehicleCatalog(context.assets)
        assertTrue("International make coverage", catalog.makeCount >= 1900)
        assertTrue("International model coverage", catalog.modelCount >= 48000)
        assertEquals("Citroën", catalog.canonicalMake("  CITROEN  "))
        assertEquals("Volkswagen", catalog.canonicalMake("VW"))
        assertTrue(catalog.models("Renault").size > 100)
        assertTrue(catalog.models("lifan").map { it.name }.containsAll(listOf("520", "520i", "620", "X50", "X60")))
        assertTrue("No invented production years for reviewed names", catalog.referencedYears("Avatr", "06").isEmpty())
        val years = catalog.yearSuggestions("Lifan", "520", 2026)
        assertEquals(142, years.size)
        assertTrue(years.containsAll(listOf("1886", "1950", "2008", "2026", "2027")))
        assertTrue(catalog.models("Marque personnalisée absente").isEmpty())
        assertEquals(years.toSet(), catalog.yearSuggestions("Marque personnalisée absente", "Modèle libre", 2026).toSet())

        val filtered = VehicleSuggestionsAdapter(context, catalog.makes, VehicleCatalog.aliases)
        for ((query, expected) in listOf("citroen" to "Citroën", "VW" to "Volkswagen", "MERCEDES" to "Mercedes-Benz")) {
            val ready = CountDownLatch(1)
            instrumentation.runOnMainSync { filtered.filter.filter(query) { ready.countDown() } }
            assertTrue("Search filter completed", ready.await(10, TimeUnit.SECONDS))
            assertTrue("Accent/case/alias search: $query", (0 until filtered.count).any { filtered.getItem(it) == expected })
        }

        context.getSharedPreferences("vehicle", 0).edit().clear().commit()
        val profile = InstrumentationRegistry.getArguments().getString("profile") ?: "phone"
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            lateinit var make: AutoCompleteTextView
            lateinit var model: AutoCompleteTextView
            lateinit var year: AutoCompleteTextView
            fun bindDialog() {
                val root = WindowInspector.getGlobalWindowViews().first { it.findViewWithTag<View>("vehicle_make") != null }
                make = root.findViewWithTag("vehicle_make")
                model = root.findViewWithTag("vehicle_model")
                year = root.findViewWithTag("vehicle_year")
            }
            fun complete(field: AutoCompleteTextView, query: String, choice: String) {
                val ready = CountDownLatch(1)
                scenario.onActivity {
                    field.setText(query, false)
                    (field.adapter as VehicleSuggestionsAdapter).filter.filter(query) { ready.countDown() }
                }
                assertTrue("Suggestion results ready: $query", ready.await(10, TimeUnit.SECONDS))
                scenario.onActivity {
                    field.requestFocus(); field.showDropDown()
                    val position = (0 until field.adapter.count).first { field.adapter.getItem(it) == choice }
                    field.listSelection = position
                    field.performCompletion()
                }
            }
            scenario.onActivity { activity ->
                activity.window.decorView.findViewWithTag<View>("choose_vehicle").performClick()
                bindDialog()
            }
            complete(make, "lif", "Lifan")
            capture("${profile}_catalogue_modeles")
            complete(model, "52", "520")
            capture("${profile}_catalogue_annees")
            scenario.onActivity { activity ->
                assertEquals("Lifan", make.text.toString())
                assertEquals("520", model.text.toString())
                val save = year.rootView.findViewById<View>(android.R.id.button1)
                year.dismissDropDown(); year.setText("9999", false); save.performClick()
                assertNotNull("Invalid year keeps the form open", year.error)
                assertEquals(0, activity.getSharedPreferences("vehicle", 0).getInt("year", 0))
                year.setText("2008", false); save.performClick()
                assertEquals(2008, activity.getSharedPreferences("vehicle", 0).getInt("year", 0))
                assertEquals("520", activity.getSharedPreferences("vehicle", 0).getString("model", ""))
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertTrue(descendants(activity.window.decorView).filterIsInstance<TextView>().any { it.text.contains("Lifan 520") })
                activity.window.decorView.findViewWithTag<View>("choose_vehicle").performClick()
                bindDialog()
                make.setText("Marque locale", false)
                assertEquals("Changing make clears the previous model", "", model.text.toString())
                model.setText("Modèle personnalisé", false); year.setText("1972", false)
                year.rootView.findViewById<View>(android.R.id.button1).performClick()
                val prefs = activity.getSharedPreferences("vehicle", 0)
                assertEquals("Marque locale", prefs.getString("make", ""))
                assertEquals("Modèle personnalisé", prefs.getString("model", ""))
                assertEquals(1972, prefs.getInt("year", 0))
            }
        }
        context.getSharedPreferences("vehicle", 0).edit().clear().commit()
    }
}
