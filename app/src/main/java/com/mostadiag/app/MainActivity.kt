package com.mostadiag.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.Gravity
import android.widget.*
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.concurrent.thread

import android.graphics.Typeface
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan

// Legacy Bluetooth entry point requests permissions centrally in connect().
// Lint cannot follow its dynamically assembled permission list; preserve the OBD implementation.
@android.annotation.SuppressLint("MissingPermission")
class MainActivity : Activity() {
    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private lateinit var status: TextView
    private lateinit var results: TextView
    private var selectedTab = 0
    @Volatile private var isConnected = false
    private val latestReadings = linkedMapOf<String, String>()
    private val history = StringBuilder()

    private var selectedMake = ""
    private var selectedModel = ""
    private var selectedYear = 0
    private val vehicleCatalog by lazy { VehicleCatalog(assets) }
    private fun chooseVehicle() {
        val form = column().apply { setPadding(dp(20), dp(12), dp(20), dp(8)) }
        fun field(label: String, fieldTag: String) = AutoCompleteTextView(this).apply {
            tag = fieldTag
            hint = label
            contentDescription = label
            threshold = 1
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(muted)
            textSize = 16f
            minHeight = dp(52)
            dropDownHeight = dp(200)
            setDropDownBackgroundDrawable(rounded(panel))
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        val make = field("Rechercher ou saisir la marque", "vehicle_make").apply {
            setText(selectedMake)
            setAdapter(VehicleSuggestionsAdapter(this@MainActivity, vehicleCatalog.makes, VehicleCatalog.aliases))
        }
        val model = field("Rechercher ou saisir le modèle", "vehicle_model").apply { setText(selectedModel) }
        val year = field("Rechercher ou saisir l’année", "vehicle_year").apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            if (selectedYear > 0) setText(selectedYear.toString())
        }
        val modelHelp = text("", 11f, muted).apply { setPadding(dp(4), dp(4), dp(4), dp(10)) }
        val yearHelp = text("Toutes les années restent disponibles. Vérifiez l’année sur la carte grise.", 11f, muted)
        var currentMake = vehicleCatalog.canonicalMake(make.text.toString()) ?: make.text.toString().trim()
        fun refreshYears() {
            val makeName = make.text.toString()
            val modelName = model.text.toString()
            val known = vehicleCatalog.referencedYears(makeName, modelName)
            year.setAdapter(VehicleSuggestionsAdapter(this, vehicleCatalog.yearSuggestions(makeName, modelName)))
            yearHelp.text = if (known.isEmpty())
                "Toutes les années de 1886 à l’année prochaine sont proposées. Vérifiez la carte grise."
            else "Années référencées en premier, puis toutes les autres. Vérifiez l’année sur la carte grise."
        }
        fun refreshModels() {
            val options = vehicleCatalog.models(make.text.toString())
            model.setAdapter(VehicleSuggestionsAdapter(this, options.map { it.name }))
            modelHelp.text = if (options.isEmpty()) "Saisie libre : indiquez le modèle de votre véhicule."
                else "${options.size} modèles proposés · saisie libre disponible"
            refreshYears()
        }
        fun watch(field: AutoCompleteTextView, changed: () -> Unit) {
            field.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { changed() }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        watch(make) {
            val nextMake = vehicleCatalog.canonicalMake(make.text.toString()) ?: make.text.toString().trim()
            if (!nextMake.equals(currentMake, ignoreCase = true)) {
                currentMake = nextMake
                model.setText("", false)
                refreshModels()
            }
        }
        watch(model) { refreshYears() }
        make.setOnClickListener { make.showDropDown() }
        model.setOnClickListener { model.showDropDown() }
        year.setOnClickListener { year.showDropDown() }
        make.setOnItemClickListener { _, _, _, _ ->
            refreshModels()
            model.requestFocus()
            model.showDropDown()
        }
        model.setOnItemClickListener { _, _, _, _ ->
            refreshYears()
            year.requestFocus()
            year.showDropDown()
        }
        refreshModels()
        form.addView(text("${vehicleCatalog.makeCount} marques · ${vehicleCatalog.modelCount} modèles hors connexion", 12f, cyan, true).apply {
            setPadding(0, 0, 0, dp(14))
        })
        form.addView(text("MARQUE", 11f, cyan, true)); form.addView(make)
        form.addView(text("MODÈLE", 11f, cyan, true).apply { setPadding(0, dp(12), 0, 0) }); form.addView(model)
        form.addView(modelHelp)
        form.addView(text("ANNÉE DU VÉHICULE", 11f, cyan, true)); form.addView(year)
        form.addView(yearHelp.apply { setPadding(dp(4), dp(6), dp(4), dp(6)) })
        val dialog = AlertDialog.Builder(this).setTitle("Votre véhicule")
            .setView(ScrollView(this).apply { addView(form) })
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Enregistrer", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val makeName = make.text.toString().trim()
                val modelName = model.text.toString().trim()
                val yearNumber = year.text.toString().trim().toIntOrNull()
                val maxYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) + 1
                when {
                    makeName.isBlank() -> { make.error = "Renseignez la marque"; make.requestFocus() }
                    modelName.isBlank() -> { model.error = "Renseignez le modèle"; model.requestFocus() }
                    yearNumber == null || yearNumber !in 1886..maxYear -> {
                        year.error = "Saisissez une année entre 1886 et $maxYear"; year.requestFocus()
                    }
                    else -> {
                        selectedMake = vehicleCatalog.canonicalMake(makeName) ?: makeName
                        selectedModel = vehicleCatalog.models(selectedMake).firstOrNull {
                            VehicleCatalog.normalize(it.name) == VehicleCatalog.normalize(modelName)
                        }?.name ?: modelName
                        selectedYear = yearNumber
                        getSharedPreferences("vehicle", MODE_PRIVATE).edit()
                            .putString("make", selectedMake).putString("model", selectedModel)
                            .putInt("year", selectedYear).apply()
                        dialog.dismiss()
                        showTab(1)
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showCatalogCredits() {
        val credits = text(assets.open("vehicle-catalog/credits.txt").bufferedReader().use { it.readText() }, 12f, muted).apply {
            setPadding(dp(20), dp(12), dp(20), dp(16))
            setTextIsSelectable(true)
            android.text.util.Linkify.addLinks(this, android.text.util.Linkify.WEB_URLS)
            setLinkTextColor(cyan)
        }
        AlertDialog.Builder(this).setTitle("Sources du catalogue")
            .setView(ScrollView(this).apply { addView(credits) })
            .setPositiveButton("Fermer", null).show()
    }

    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var nav: LinearLayout
    private val bg = Color.rgb(4, 10, 19)
    private val panel = Color.rgb(13, 24, 39)
    private val cyan = Color.rgb(48, 214, 255)
    private val muted = Color.rgb(154, 176, 199)
    private val green = Color.rgb(43, 226, 165)
    private val amber = Color.rgb(255, 182, 79)
    private val red = Color.rgb(255, 102, 112)
    private val purple = Color.rgb(184, 131, 255)
    private val adapter by lazy { (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter }
    private fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()
    private val wide get() = resources.configuration.screenWidthDp >= 600
    private val largeText get() = resources.configuration.fontScale > 1.25f
    private fun rounded(color: Int, stroke: Int = Color.rgb(34, 54, 74), radius: Float = 18f): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radius.toInt()).toFloat(); setStroke(dp(1), stroke) }
    private fun surface(accent: Int = Color.rgb(34, 54, 74)) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(17, 32, 51), Color.rgb(8, 17, 29))
    ).apply { cornerRadius = dp(18).toFloat(); setStroke(dp(1), accent) }
    private fun text(value: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(color)
            typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
            includeFontPadding = false
            setLineSpacing(dp(3).toFloat(), 1f)
        }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun icon(kind: String, color: Int = cyan, size: Int = 28, glow: Boolean = false) =
        NeonIcon(this, kind, color, glow).apply { layoutParams = LinearLayout.LayoutParams(dp(size), dp(size)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
    private fun actionable(view: View, accent: Int = cyan, action: () -> Unit) {
        view.isClickable = true; view.isFocusable = true
        view.foreground = RippleDrawable(ColorStateList.valueOf(Color.argb(40, Color.red(accent), Color.green(accent), Color.blue(accent))), null, rounded(Color.WHITE))
        view.setOnClickListener { action() }
        view.setOnTouchListener { v, e ->
            when(e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> v.animate().scaleX(.985f).scaleY(.985f).setDuration(100).start()
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
            }; false
        }
    }
    private fun add(view: View, gap: Int = 10) {
        content.addView(view, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(gap) })
    }
    private fun section(title: String, aside: String = "") {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(2), dp(14), dp(2), dp(12)) }
        row.addView(text(title, 11f, cyan, true).apply { letterSpacing = .12f }, LinearLayout.LayoutParams(0, -2, 1f))
        if (aside.isNotBlank()) row.addView(text(aside, 11f, muted))
        add(row, 0)
    }
    private fun pageTitle(kicker: String, title: String, subtitle: String) {
        add(column().apply {
            setPadding(dp(4), dp(18), dp(4), dp(12))
            addView(text(kicker, 10f, cyan, true).apply { letterSpacing = .2f })
            addView(text(title, if (wide) 34f else 29f, Color.WHITE, true).apply { setPadding(0, dp(8), 0, dp(8)) })
            addView(text(subtitle, 13f, muted))
        })
    }
    private fun note(title: String, body: String, color: Int = cyan, kind: String = "shield") {
        add(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; background = rounded(panel)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            addView(icon(kind, color, 30))
            addView(column().apply {
                setPadding(dp(14), 0, 0, 0)
                addView(text(title, 14f, Color.WHITE, true))
                addView(text(body, 12f, muted).apply { setPadding(0, dp(6), 0, 0) })
            }, LinearLayout.LayoutParams(0, -2, 1f))
        })
    }
    private fun connectionCard() {
        (status.parent as? android.view.ViewGroup)?.removeView(status)
        val card = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = surface(if (isConnected) green else Color.rgb(36, 90, 111))
            setPadding(dp(14), dp(15), dp(14), dp(15))
            addView(icon("bluetooth", if (isConnected) green else cyan, 36, true))
            addView(column().apply {
                setPadding(dp(12), 0, dp(8), 0)
                addView(status)
                addView(text("ELM327 · Bluetooth Classic · OBD-II", 11f, muted).apply { setPadding(0, dp(6), 0, 0) })
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(icon("arrow", muted, 20))
        }
        actionable(card) { connect() }; add(card)
    }
    private val brandResources = mapOf(
        "Peugeot" to R.drawable.brand_peugeot, "Renault" to R.drawable.brand_renault,
        "Citroën" to R.drawable.brand_citroen, "Volkswagen" to R.drawable.brand_volkswagen,
        "BMW" to R.drawable.brand_bmw, "Audi" to R.drawable.brand_audi,
        "Toyota" to R.drawable.brand_toyota, "Ford" to R.drawable.brand_ford,
        "Hyundai" to R.drawable.brand_hyundai, "Kia" to R.drawable.brand_kia,
        "Nissan" to R.drawable.brand_nissan, "Honda" to R.drawable.brand_honda,
        "Mazda" to R.drawable.brand_mazda, "Opel" to R.drawable.brand_opel,
        "Fiat" to R.drawable.brand_fiat, "Volvo" to R.drawable.brand_volvo,
        "Porsche" to R.drawable.brand_porsche, "Tesla" to R.drawable.brand_tesla,
        "Jeep" to R.drawable.brand_jeep, "Suzuki" to R.drawable.brand_suzuki,
        "Dacia" to R.drawable.brand_dacia, "Seat" to R.drawable.brand_seat, "Skoda" to R.drawable.brand_skoda
    )
    private fun brandLogo(name: String, size: Int = 36): View = brandResources[name]?.let { resource ->
        ImageView(this).apply {
            setImageResource(resource); scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = name; layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
        }
    } ?: icon("car", cyan, size)
    private fun vehicleCard() {
        val card = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; background = surface()
            setPadding(dp(16), dp(16), dp(14), dp(16))
            addView(brandLogo(selectedMake, 36))
            addView(column().apply {
                setPadding(dp(14), 0, dp(6), 0)
                addView(text(if (selectedMake.isBlank()) "Votre véhicule" else "$selectedMake $selectedModel", 16f, Color.WHITE, true))
                addView(text(if (selectedMake.isBlank()) "Choisir la marque, le modèle et l’année" else "$selectedYear · Profil renseigné manuellement", 11f, muted).apply { setPadding(0, dp(6), 0, 0) })
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(icon("arrow", muted, 20))
        }
        card.tag = "choose_vehicle"
        actionable(card) { chooseVehicle() }; add(card)
    }
    private fun brandGallery() {
        section("CONSTRUCTEURS", "23 logos hors connexion")
        val cards = (brandResources.keys + "Lifan").map { name ->
            column().apply {
                gravity = Gravity.CENTER; background = surface(); tag = "brand_card"
                minimumHeight = dp(92); setPadding(dp(6), dp(14), dp(6), dp(14))
                addView(brandLogo(name, 30))
                addView(text(name, 11f, Color.rgb(213, 227, 240), true).apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0) })
                actionable(this) { selectedMake = name; chooseVehicle() }
            }
        }
        grid(cards, if (wide) 6 else if (largeText) 2 else 3)
        rowAction("Rechercher toutes les marques", "${vehicleCatalog.makeCount} marques · ${vehicleCatalog.modelCount} modèles hors connexion", "car") { chooseVehicle() }
    }
    private fun grid(items: List<View>, columns: Int = if (wide) 4 else 2) {
        items.chunked(columns).forEach { group ->
            val row = object : LinearLayout(this) {
                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                    val cardHeight = (0 until childCount).maxOfOrNull { getChildAt(it).measuredHeight } ?: 0
                    for (i in 0 until childCount) {
                        val child = getChildAt(i)
                        child.measure(View.MeasureSpec.makeMeasureSpec(child.measuredWidth, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(cardHeight, View.MeasureSpec.EXACTLY))
                    }
                }
            }.apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false }
            group.forEachIndexed { i, v -> row.addView(v, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i > 0) marginStart = dp(8) }) }
            repeat(columns - group.size) { row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply { marginStart = dp(8) }) }
            add(row, 8)
        }
    }
    private fun tile(title: String, subtitle: String, accent: Int, kind: String, action: () -> Unit): View = column().apply {
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.argb(255, Color.red(accent)/7+8, Color.green(accent)/7+10, Color.blue(accent)/7+17), Color.rgb(8, 17, 29))).apply {
            cornerRadius = dp(16).toFloat(); setStroke(dp(1), Color.argb(150, Color.red(accent), Color.green(accent), Color.blue(accent)))
        }
        setPadding(dp(15), dp(15), dp(14), dp(14)); minimumHeight = dp(112); tag = "action_tile"
        val top = LinearLayout(this@MainActivity).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(icon(kind, accent, 32, true))
        top.addView(View(this@MainActivity), LinearLayout.LayoutParams(0, 1, 1f))
        top.addView(icon("arrow", accent, 16)); addView(top)
        addView(text(title, 14f, Color.WHITE, true).apply { setPadding(0, dp(11), 0, dp(5)) })
        addView(text(subtitle, 11f, muted))
        actionable(this, accent, action)
    }
    private fun rowAction(title: String, detail: String, kind: String = "pulse", color: Int = cyan, action: (() -> Unit)? = null) {
        val card = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; background = surface(); minimumHeight = dp(78)
            setPadding(dp(16), dp(17), dp(14), dp(17))
            addView(icon(kind, color, 27))
            addView(column().apply {
                setPadding(dp(14), 0, dp(8), 0)
                addView(text(title, 15f, Color.WHITE, true))
                addView(text(detail, 12f, muted).apply { setPadding(0, dp(6), 0, 0) })
            }, LinearLayout.LayoutParams(0, -2, 1f))
            if (action != null) addView(icon("arrow", muted, 18))
        }
        if (action != null) actionable(card, color, action)
        add(card)
    }
    private fun hero() {
        val frame = FrameLayout(this).apply { background = rounded(panel); clipToOutline = true }
        frame.addView(ImageView(this).apply {
            setImageResource(R.drawable.garage_hero); scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Illustration automobile : berline noire dans un atelier éclairé en cyan"
        }, FrameLayout.LayoutParams(-1, -1))
        frame.addView(View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.argb(140, 2, 10, 20), Color.TRANSPARENT, Color.argb(220, 2, 10, 20)))
        }, FrameLayout.LayoutParams(-1, -1))
        frame.addView(column().apply {
            setPadding(dp(18), dp(17), dp(18), 0)
            addView(text("VOTRE VÉHICULE, EN TOUTE CLARTÉ.", 10f, cyan, true).apply { letterSpacing = .12f })
            addView(text("L’exigence du diagnostic.", if (wide) 26f else 21f, Color.WHITE, true).apply { setPadding(0, dp(8), 0, 0) })
        }, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        frame.addView(text("MULTIMARQUE   /   OBD-II   /   BLUETOOTH", 9f, Color.rgb(190, 223, 240), true).apply {
            letterSpacing = .08f; setPadding(dp(18), 0, dp(12), dp(14))
        }, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        content.addView(frame, LinearLayout.LayoutParams(-1, dp(if (wide) 320 else if (largeText) 250 else 196)).apply { bottomMargin = dp(12) })
    }
    private fun reading(vararg labels: String): String? = if (isConnected) labels.firstNotNullOfOrNull { latestReadings[it] } else null
    private fun gauge(title: String, unit: String, accent: Int, value: String?, max: Float, action: () -> Unit): View = column().apply {
        gravity = Gravity.CENTER; background = surface(); setPadding(dp(8), dp(15), dp(8), dp(12))
        addView(text(title, 11f, Color.rgb(208, 225, 241), true).apply { gravity = Gravity.CENTER; letterSpacing = .04f })
        val dial = object : View(this@MainActivity) {
            private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(c: android.graphics.Canvas) {
                val density = resources.displayMetrics.density
                val cx = width / 2f; val cy = height * .50f; val radius = minOf(width * .40f, height * .40f)
                paint.style = android.graphics.Paint.Style.STROKE; paint.strokeCap = android.graphics.Paint.Cap.ROUND
                paint.strokeWidth = 5 * density; paint.color = Color.rgb(28, 46, 64)
                c.drawArc(cx-radius, cy-radius, cx+radius, cy+radius, 140f, 260f, false, paint)
                val numeric = value?.substringBefore(" ")?.toFloatOrNull()
                if (numeric != null) {
                    paint.color = accent
                    c.drawArc(cx-radius, cy-radius, cx+radius, cy+radius, 140f, 260f * (numeric/max).coerceIn(0f, 1f), false, paint)
                }
                paint.strokeWidth = density
                for (tick in 0..20) {
                    val angle = Math.toRadians((140 + tick * 13).toDouble())
                    val r1 = radius - 7*density; val r2 = r1 - (if (tick%5 == 0) 6 else 3)*density
                    paint.color = if (tick%5 == 0) muted else Color.rgb(41, 60, 78)
                    c.drawLine(cx + kotlin.math.cos(angle).toFloat()*r1, cy + kotlin.math.sin(angle).toFloat()*r1,
                        cx + kotlin.math.cos(angle).toFloat()*r2, cy + kotlin.math.sin(angle).toFloat()*r2, paint)
                }
                paint.style = android.graphics.Paint.Style.FILL; paint.textAlign = android.graphics.Paint.Align.CENTER
                paint.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); paint.color = if (value == null) muted else Color.WHITE
                paint.textSize = minOf(33*density*resources.configuration.fontScale, width*.26f)
                c.drawText(value?.substringBefore(" ") ?: "—", cx, cy + 8*density, paint)
            }
        }
        dial.contentDescription = "$title : ${value ?: "indisponible"} $unit"
        addView(dial, LinearLayout.LayoutParams(-1, dp(135)))
        addView(text(unit, 11f, accent).apply { gravity = Gravity.CENTER })
        addView(text(if (value == null) "EN ATTENTE" else "DERNIÈRE LECTURE", 9f, muted).apply { gravity = Gravity.CENTER; letterSpacing = .08f; setPadding(0, dp(7), 0, 0) })
        actionable(this, accent, action)
    }
    private fun shareReport() {
        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, results.text.toString()) }
        startActivity(Intent.createChooser(send, "Partager le diagnostic"))
    }
    private fun confirmClear() {
        AlertDialog.Builder(this).setTitle("Effacer les codes défauts ?")
            .setMessage("Cette opération peut supprimer des données de diagnostic. À effectuer après réparation uniquement.")
            .setNegativeButton("Annuler", null).setPositiveButton("Effacer") { _, _ -> execute("04", "Effacement") }.show()
    }
    private fun showTab(tab: Int) {
        val changed = selectedTab != tab
        val previousY = if (!changed) scroll.scrollY else 0
        selectedTab = tab
        content.removeAllViews()
        for (i in 0 until nav.childCount) {
            val button = nav.getChildAt(i) as LinearLayout
            (button.getChildAt(0) as NeonIcon).apply { tint = if (i == tab) cyan else muted; invalidate() }
            (button.getChildAt(1) as TextView).setTextColor(if (i == tab) cyan else muted)
            button.background = if (i == tab) rounded(Color.rgb(9, 39, 56), Color.rgb(21, 76, 97), 14f) else null
            button.isSelected = i == tab
        }
        when(tab) {
            0 -> {
                hero(); connectionCard(); vehicleCard()
                section("DIAGNOSTIC RAPIDE", "À portée de main")
                grid(listOf(
                    tile("Codes défauts", "Lire les DTC du moteur", cyan, "engine") { execute("03", "Codes défauts"); showTab(3) },
                    tile("Mesures moteur", "Capteurs et instrumentation", red, "bars") { showTab(2) },
                    tile("Identification", "Véhicule, VIN et protocole", green, "car") { showTab(1) },
                    tile("Rapport de session", "Consulter et partager", amber, "report") { showTab(3) }
                ))
                section("INSTRUMENTATION", "Données du véhicule")
                grid(listOf(
                    gauge("RÉGIME MOTEUR", "tr/min", purple, reading("Régime", "Régime moteur"), 8000f) { execute("010C", "Régime") },
                    gauge("VITESSE", "km/h", cyan, reading("Vitesse"), 240f) { execute("010D", "Vitesse") }
                ), 2)
                note("Un diagnostic connecté", "Les mesures apparaissent après une lecture OBD-II. Aucun signal n’est simulé.")
            }
            1 -> {
                pageTitle("01  /  LE GARAGE", "Mon véhicule", "Votre profil automobile et son identification.")
                vehicleCard()
                note("Profil du garage", "La sélection manuelle décrit votre véhicule. L’identification du calculateur se lit séparément.", green, "car")
                section("IDENTIFICATION OBD-II")
                rowAction("Lire le numéro VIN", "Identification du véhicule · selon compatibilité", "car", green) { execute("0902", "VIN (réponse brute)"); showTab(3) }
                rowAction("Protocole actif", "Consulter le protocole détecté par l’ELM327", "pulse") { execute("ATDP", "Protocole actif"); showTab(3) }
                brandGallery()
                note("Compatibilité multimarque", "Diagnostic OBD-II générique. Les fonctions ABS, airbag et constructeur nécessitent des protocoles spécifiques.", amber)
            }
            2 -> {
                pageTitle("02  /  INSTRUMENTATION", "Mesures moteur", "Une lecture précise. Une information essentielle.")
                connectionCard()
                grid(listOf(
                    gauge("RÉGIME MOTEUR", "tr/min", purple, reading("Régime", "Régime moteur"), 8000f) { execute("010C", "Régime") },
                    gauge("VITESSE", "km/h", cyan, reading("Vitesse"), 240f) { execute("010D", "Vitesse") }
                ), 2)
                section("CAPTEURS", "Toucher pour lire")
                grid(listOf(
                    tile("Température moteur", reading("Température", "Température moteur") ?: "— °C · En attente", red, "temperature") { execute("0105", "Température") },
                    tile("Tension du module", reading("Tension module") ?: "— V · En attente", green, "battery") { execute("0142", "Tension module") },
                    tile("Charge moteur", reading("Charge moteur") ?: "— % · En attente", purple, "bars") { execute("0104", "Charge moteur") },
                    tile("Pression admission", reading("Pression admission") ?: "— kPa · En attente", amber, "gauge") { execute("010B", "Pression admission") },
                    tile("Papillon", reading("Papillon") ?: "— % · En attente", cyan, "pulse") { execute("0111", "Papillon") },
                    tile("Codes défauts", "Interroger le calculateur", muted, "engine") { execute("03", "Codes défauts"); showTab(3) }
                ))
                note("Lectures à la demande", "Chaque carte interroge le véhicule. La valeur affichée est la dernière réponse reçue, sans acquisition continue.")
            }
            3 -> {
                pageTitle("03  /  DIAGNOSTIC", "Rapport de session", "Toutes les réponses, réunies dans un journal.")
                note(if (isConnected) "Session connectée" else "En attente de connexion", if (history.isEmpty()) "Aucune lecture effectuée dans cette session." else "Le journal conserve les réponses et erreurs de cette session.", if (isConnected) green else cyan, "report")
                section("JOURNAL OBD-II", "Session actuelle")
                (results.parent as? android.view.ViewGroup)?.removeView(results); add(results, 12)
                rowAction("Partager le rapport", "Exporter le journal au format texte", "share") { shareReport() }
                section("MAINTENANCE")
                rowAction("Effacer les codes défauts", "Après réparation · une confirmation sera demandée", "engine", red) { confirmClear() }
                note("Conserver une trace", "Partagez le journal avant toute intervention pour garder les réponses du véhicule.", amber)
            }
            4 -> {
                pageTitle("04  /  PRÉFÉRENCES", "Paramètres", "Votre atelier, vos repères.")
                connectionCard()
                section("CONNEXION")
                rowAction("Choisir un adaptateur", "Bluetooth Classic · ELM327", "bluetooth") { connect() }
                note("Prêt à diagnostiquer", "1. Branchez l’ELM327 et mettez le contact.\n2. Activez le Bluetooth du téléphone.\n3. Choisissez votre adaptateur dans l’application.", cyan, "bluetooth")
                section("L’APPLICATION")
                rowAction("Bilal27000diag", "Version ${BuildConfig.VERSION_NAME} · Édition automobile", "engine")
                rowAction("Identité visuelle", "Bleu nuit · cyan néon · noir", "settings")
                rowAction("Crédits des illustrations", "Photographie générée et 23 logos Simple Icons intégrés", "car") {
                    AlertDialog.Builder(this).setTitle("Illustrations et marques")
                        .setMessage("Illustration automobile générée pour Bilal27000diag. Logos vectoriels issus de Simple Icons (CC0), intégrés à l’APK. Les marques appartiennent à leurs propriétaires ; aucune affiliation n’est revendiquée. Pour les autres marques, un pictogramme automobile est utilisé.")
                        .setPositiveButton("Fermer", null).show()
                }
                rowAction("Catalogue véhicules", "Vehicle data by VehiclesDB · sources et licences", "car") { showCatalogCredits() }
                section("BONNES PRATIQUES")
                note("À utiliser à l’arrêt", "Préparez et consultez votre diagnostic véhicule stationné. N’utilisez pas l’application pendant la conduite.", amber)
            }
        }
        if (changed && android.provider.Settings.Global.getFloat(contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f) {
            content.alpha = .55f; content.translationY = dp(6).toFloat()
            content.animate().alpha(1f).translationY(0f).setDuration(200).start()
        }
        scroll.post { scroll.scrollTo(0, previousY) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("vehicle", MODE_PRIVATE)
        selectedMake = prefs.getString("make", "") ?: ""; selectedModel = prefs.getString("model", "") ?: ""; selectedYear = prefs.getInt("year", 0)
        val root = column().apply { setBackgroundColor(bg) }
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(18), dp(10), dp(12), dp(8)); minimumHeight = dp(64)
        }
        header.addView(ImageView(this).apply { setImageResource(R.drawable.bilal_engine); contentDescription = "Bilal27000diag" }, LinearLayout.LayoutParams(dp(34), dp(34)))
        header.addView(column().apply {
            setPadding(dp(10), 0, dp(4), 0)
            val name = SpannableString("BILAL27000DIAG").apply { setSpan(ForegroundColorSpan(cyan), 5, 10, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
            addView(text("", if (wide) 23f else 19f, Color.WHITE, true).apply {
                text = name; letterSpacing = -.02f; setSingleLine(true)
                setAutoSizeTextTypeUniformWithConfiguration(10, if (wide) 23 else 19, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            })
            addView(text("DIAGNOSTIC AUTOMOBILE", 8f, muted, true).apply { letterSpacing = .17f; setPadding(0, dp(4), 0, 0) })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(FrameLayout(this).apply {
            addView(NeonIcon(this@MainActivity, "settings", muted), FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
            contentDescription = "Ouvrir les paramètres"; actionable(this) { showTab(4) }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(header)
        status = text("Non connecté · Connecter ELM327", 13f, cyan, true)
        status.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                status.setTextColor(if (isConnected) green else cyan)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        results = text("Aucune lecture effectuée.\n\nConnectez un adaptateur ELM327 pour commencer votre diagnostic.", 13f, Color.rgb(196, 215, 233)).apply {
            typeface = Typeface.create("monospace", Typeface.NORMAL); background = rounded(Color.rgb(6, 16, 27))
            setPadding(dp(18), dp(20), dp(18), dp(20)); minHeight = dp(170); setTextIsSelectable(true)
        }
        scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false; isVerticalScrollBarEnabled = false }
        val holder = FrameLayout(this)
        content = column().apply { setPadding(dp(16), dp(4), dp(16), dp(14)) }
        holder.addView(content, FrameLayout.LayoutParams(if (wide) dp(minOf(resources.configuration.screenWidthDp, 900)) else -1, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        scroll.addView(holder); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        nav = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(6), dp(8), dp(6), dp(8))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.rgb(18, 33, 49), Color.rgb(7, 17, 29)))
            elevation = dp(12).toFloat()
        }
        val icons = listOf("home", "car", "bars", "report", "settings")
        listOf("Accueil", "Véhicule", "Mesures", "Rapport", "Réglages").forEachIndexed { i, label ->
            nav.addView(column().apply {
                gravity = Gravity.CENTER; setPadding(dp(2), dp(8), dp(2), dp(8)); minimumHeight = dp(56)
                addView(icon(icons[i], muted, 23))
                addView(text(label, 10f, muted, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(5), 0, 0) })
                tag = "tab_$i"; contentDescription = label
                actionable(this) { showTab(i) }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(2); marginEnd = dp(2) })
        }
        root.addView(nav)
        window.statusBarColor = bg; window.navigationBarColor = Color.rgb(7, 17, 29)
        setContentView(root)
        showTab(savedInstanceState?.getInt("tab", 0) ?: 0)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("tab", selectedTab); super.onSaveInstanceState(outState)
    }
    private fun log(message: String) = runOnUiThread {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.FRANCE).format(java.util.Date())
        history.append(timestamp).append("  ").append(message).append("\n\n")
        results.text = history.toString()
    }
    private var receiver: BroadcastReceiver? = null
    private fun connect() {
        val permissions = if (Build.VERSION.SDK_INT >= 31)
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) { requestPermissions(missing.toTypedArray(), 10); return }
        if (adapter?.isEnabled != true) {
            Toast.makeText(this, "Activez le Bluetooth puis relancez la recherche", Toast.LENGTH_LONG).show()
            return
        }
        val devices = linkedMapOf<String, BluetoothDevice>()
        val labels = mutableListOf<String>()
        val listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        val list = ListView(this).apply { adapter = listAdapter }
        fun add(d: BluetoothDevice) {
            if (devices.containsKey(d.address)) return
            devices[d.address] = d
            labels.add((d.name ?: "Bluetooth") + " • " + d.address)
            listAdapter.notifyDataSetChanged()
        }
        adapter?.bondedDevices?.forEach { add(it) }
        val dialog = AlertDialog.Builder(this).setTitle("Rechercher un ELM327")
            .setView(list).setNegativeButton("Fermer", null).create()
        fun cleanup() {
            try { adapter?.cancelDiscovery() } catch (_: Exception) {}
            receiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
            receiver = null
        }
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val device = if (Build.VERSION.SDK_INT >= 33)
                    intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                else intent?.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                if (device != null && intent?.action == BluetoothDevice.ACTION_FOUND) add(device)
                if (device != null && intent?.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED &&
                    device.bondState == BluetoothDevice.BOND_BONDED) {
                    cleanup(); dialog.dismiss(); connectDevice(device)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(receiver, filter)
        dialog.setOnDismissListener { cleanup() }
        list.setOnItemClickListener { _, _, position, _ ->
            val device = devices.values.elementAt(position)
            if (device.bondState == BluetoothDevice.BOND_BONDED) {
                cleanup(); dialog.dismiss(); connectDevice(device)
            } else {
                if (!device.createBond()) Toast.makeText(this, "Appairage impossible", Toast.LENGTH_LONG).show()
                else Toast.makeText(this, "Confirmez le code PIN si demandé", Toast.LENGTH_LONG).show()
            }
        }
        dialog.show()
        if (adapter?.startDiscovery() != true) {
            Toast.makeText(this, "Recherche impossible. Vérifiez les autorisations Bluetooth.", Toast.LENGTH_LONG).show()
        }
    }
    private fun connectDevice(target: BluetoothDevice) {
            thread {
                try {
                    runOnUiThread { status.text = "◌ Connexion Bluetooth en cours…" }
                    socket?.close()
                    val device = target
                    val s = target.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
                    adapter?.cancelDiscovery()
                    s.connect()
                    socket = s; input = s.inputStream; output = s.outputStream
                    val reset = command("ATZ")
                    if (reset.isBlank() || reset.contains("ERROR", ignoreCase = true)) error("Boîtier sans réponse valide")
                    command("ATE0"); command("ATL0"); command("ATS0"); command("ATSP0")
                    isConnected = true
                    runOnUiThread { status.text = "● Connecté : ${target.name}" }
                    log("Connexion établie. Protocole automatique activé.")
                } catch (e: Exception) {
                    isConnected = false
                    latestReadings.clear()
                    try { socket?.close() } catch (_: Exception) {}
                    input = null; output = null; socket = null
                    runOnUiThread { status.text = "● HORS LIGNE • ELM327" }
                    log("Échec de connexion : ${e.message}")
                }
            }
    }

    @Synchronized private fun command(cmd: String): String {
        val out = output ?: error("Boîtier non connecté")
        val inp = input ?: error("Boîtier non connecté")
        out.write((cmd + "\r").toByteArray()); out.flush()
        val sb = StringBuilder(); val until = System.currentTimeMillis()+6000
        var completed = false
        while (System.currentTimeMillis() < until) {
            if (inp.available()>0) { val b = inp.read(); if (b<0 || b==62) { completed = b==62; break }; sb.append(b.toChar()) }
            else Thread.sleep(25)
        }
        val reply = sb.toString().trim()
        if (!completed) error("Délai de réponse ELM327 dépassé")
        if (reply.isBlank()) error("Aucune réponse du boîtier")
        return reply
    }
    private fun decode(cmd: String, raw: String): String {
        if (raw.contains("NO DATA", true) || raw.contains("UNABLE TO CONNECT", true) ||
            raw.contains("ERROR", true) || raw.contains("STOPPED", true) || raw.trim() == "?")
            return "Donnée indisponible • réponse du véhicule : " + raw.trim()
        val normalized = raw.uppercase().replace(Regex("[^0-9A-F]"), "")
        val key = if (cmd.length == 4 && cmd.startsWith("01")) "41" + cmd.substring(2) else ""
        val idx = if (key.isNotEmpty()) normalized.indexOf(key) else -1
        if (idx < 0) return raw.trim()
        val data = normalized.substring(idx + key.length)
        if (data.length < 2) return raw.trim()
        val a = data.substring(0, 2).toIntOrNull(16) ?: return raw.trim()
        val b = if (data.length >= 4) data.substring(2, 4).toIntOrNull(16) else null
        val value: String? = when (cmd) {
            "010C" -> if (b != null) ((256 * a + b) / 4.0).toString() + " tr/min" else null
            "010D" -> a.toString() + " km/h"
            "0105" -> (a - 40).toString() + " °C"
            "0104" -> String.format(java.util.Locale.US, "%.1f %%", a * 100.0 / 255)
            "010B" -> a.toString() + " kPa"
            "0111" -> String.format(java.util.Locale.US, "%.1f %%", a * 100.0 / 255)
            "0142" -> if (b != null) String.format(java.util.Locale.US, "%.2f V", (256 * a + b) / 1000.0) else null
            else -> null
        }
        return if (value != null) value + "\nRéponse : " + raw else raw.trim()
    }
    private fun execute(cmd: String, label: String) {
        thread {
            try {
                val raw = command(cmd)
                val reading = decode(cmd, raw)
                if (cmd.startsWith("01") && !reading.startsWith("Donnée indisponible") &&
                    reading != raw.trim()) {
                    runOnUiThread {
                        latestReadings[label] = reading.substringBefore("\n")
                        if (selectedTab == 2) showTab(2)
                    }
                }
                log("$label\n$reading")
            }
            catch (e: Exception) { log("Erreur : ${e.message}") }
        }
    }
    override fun onDestroy() { receiver?.let { try { unregisterReceiver(it) } catch (_:Exception) {} }; try { socket?.close() } catch (_:Exception) {}; super.onDestroy() }
}
