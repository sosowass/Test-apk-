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

class MainActivity : Activity() {
    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private lateinit var status: TextView
    private lateinit var results: TextView
    private val blue = Color.rgb(0, 153, 255)
    private var selectedTab = 0
    @Volatile private var isConnected = false
    private val latestReadings = linkedMapOf<String, String>()
    private val history = StringBuilder()

    private var selectedMake = ""
    private var selectedModel = ""
    private var selectedYear = 0
    private val makeOptions = listOf("Abarth","Acura","Aiways","Alfa Romeo","Alpine","AMC","Ariel","Aston Martin","Audi","Austin","Avatr","BAIC","Baojun","Bentley","Bestune","BMW","Borgward","Brilliance","Bugatti","Buick","BYD","Cadillac","Changan","Chery","Chevrolet","Chrysler","Citroën","Cupra","Dacia","Daewoo","DAF","Daihatsu","Datsun","Denza","De Tomaso","Dodge","Dongfeng","DS Automobiles","Eagle","Exeed","FAW","Ferrari","Fiat","Fisker","Ford","Forthing","Foton","GAC","Geely","Genesis","GMC","Great Wall","Haval","Holden","Honda","Hongqi","Hummer","Hyundai","IM Motors","Infiniti","Isuzu","Iveco","JAC","Jaecoo","Jaguar","Jeep","Jetour","JMC","Karma","Kia","Koenigsegg","Lada","Lamborghini","Lancia","Land Rover","Leapmotor","Lexus","Li Auto","Lincoln","Lotus","Lucid","Lynk & Co","Mahindra","MAN","Maserati","Maxus","Maybach","Mazda","McLaren","Mercedes-Benz","Mercury","MG","Mini","Mitsubishi","Morgan","Neta","NIO","Nissan","Oldsmobile","Omoda","Opel","Ora","Pagani","Perodua","Peugeot","Plymouth","Polestar","Pontiac","Porsche","Proton","Ram","Renault","Rivian","Roewe","Rolls-Royce","Rover","Saab","Saturn","Scania","Seat","Seres","Skoda","Smart","SsangYong","Subaru","Suzuki","Tata","Tesla","Toyota","UAZ","Vauxhall","VinFast","Volkswagen","Volvo","Voyah","Wey","Wuling","Xiaomi Auto","XPeng","Yangwang","Zeekr","Zotye","Autre marque")
    private val modelSuggestions = mapOf(
        "Peugeot" to listOf("208","308","2008","3008","5008","508","206","207","307","407","Partner"),
        "Renault" to listOf("Clio","Megane","Twingo","Captur","Scenic","Kadjar","Austral","Kangoo","Trafic"),
        "Citroën" to listOf("C3","C4","C5","Berlingo","Jumpy","Saxo"),
        "Volkswagen" to listOf("Golf","Polo","Passat","Tiguan","Touareg","Touran"),
        "BMW" to listOf("Série 1","Série 3","Série 5","X1","X3","X5"),
        "Audi" to listOf("A1","A3","A4","A6","Q3","Q5","Q7"),
        "Toyota" to listOf("Yaris","Corolla","RAV4","C-HR","Prius","Hilux"),
        "Ford" to listOf("Fiesta","Focus","Kuga","Puma","Ranger"),
        "Hyundai" to listOf("i10","i20","i30","Tucson","Kona"),
        "Kia" to listOf("Picanto","Ceed","Sportage","Sorento"),
        "Dacia" to listOf("Sandero","Duster","Logan","Jogger"),
        "Seat" to listOf("Ibiza","Leon","Ateca"),
        "Tesla" to listOf("Model 3","Model Y","Model S","Model X")
    )
    private fun chooseVehicle() {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 12, 30, 8)
        }
        val make = AutoCompleteTextView(this).apply {
            hint = "Rechercher parmi 143 marques"
            threshold = 1
            setSingleLine(true)
            setText(selectedMake)
            setAdapter(ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line, makeOptions))
        }
        val model = AutoCompleteTextView(this).apply {
            hint = "Modèle (ex. 308, Clio, Golf)"
            setSingleLine(true)
            setText(selectedModel)
            threshold = 1
        }
        fun refreshModels() {
            val options = modelSuggestions[make.text.toString().trim()] ?: emptyList()
            model.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, options))
        }
        make.setOnItemClickListener { _, _, _, _ ->
            model.setText("")
            refreshModels()
            model.showDropDown()
        }
        refreshModels()
        val year = NumberPicker(this).apply {
            minValue = 1950
            maxValue = 2027
            value = if (selectedYear in 1950..2027) selectedYear else 2026
            wrapSelectorWheel = false
        }
        form.addView(text("MARQUE", 13f, cyan, true))
        form.addView(make)
        form.addView(text("MODÈLE", 13f, cyan, true))
        form.addView(model)
        form.addView(text("ANNÉE", 13f, cyan, true))
        form.addView(year)
        val formScroll = ScrollView(this).apply { addView(form) }
        AlertDialog.Builder(this).setTitle("Sélection du véhicule")
            .setView(formScroll)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Enregistrer") { _, _ ->
                val makeName = make.text.toString().trim()
                val modelName = model.text.toString().trim()
                if (makeName.isBlank() || modelName.isBlank()) {
                    Toast.makeText(this, "Renseignez la marque et le modèle", Toast.LENGTH_LONG).show()
                } else {
                    selectedMake = makeName
                    selectedModel = modelName
                    selectedYear = year.value
                    getSharedPreferences("vehicle", MODE_PRIVATE).edit()
                        .putString("make", selectedMake)
                        .putString("model", selectedModel)
                        .putInt("year", selectedYear).apply()
                    showTab(1)
                }
            }.show()
    }

    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var nav: LinearLayout
    private val bg = Color.rgb(5, 12, 26)
    private val panel = Color.rgb(16, 29, 51)
    private val cyan = Color.rgb(40, 205, 248)
    private val adapter by lazy { (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter }
    private fun rounded(color: Int, stroke: Int = color, radius: Float = 22f): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
            setStroke(2, stroke)
        }

    private fun text(value: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
        }

    private fun tile(title: String, subtitle: String, color: Int, symbol: String, fn: () -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(panel, Color.rgb(10, 23, 43))).apply { cornerRadius = 28f; setStroke(2, color) }
            setPadding(12, 25, 12, 25)
            elevation = 7f
            animate().alpha(1f).setDuration(350).start()
            isClickable = true
            isFocusable = true
            setOnClickListener { fn() }
            addView(text(symbol, 31f, color, true).apply { gravity = Gravity.CENTER; setShadowLayer(8f, 0f, 0f, color) })
            addView(text(title, 15f, Color.WHITE, true).apply {
                gravity = Gravity.CENTER
                setPadding(0, 12, 0, 5)
            })
            addView(text(subtitle, 11f, Color.rgb(166, 192, 213)).apply { gravity = Gravity.CENTER })
        }

    private fun addGrid(items: List<LinearLayout>) {
        items.chunked(2).forEach { pair ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { item ->
                line.addView(item, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    setMargins(5, 5, 5, 5)
                })
            }
            if (pair.size == 1) line.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            content.addView(line)
        }
    }

    private fun section(title: String) {
        content.addView(text(title, 13f, cyan, true).apply {
            letterSpacing = 0.11f
            setPadding(10, 25, 10, 12)
        })
    }

    private fun brandLogo(name: String): TextView {
        val monogram = when (name) {
            "Peugeot" -> "♞"
            "Renault" -> "◇"
            "Citroën" -> "⌃⌃"
            "Volkswagen" -> "VW"
            "BMW" -> "BMW"
            "Mercedes-Benz" -> "✦"
            "Audi" -> "◎◎"
            "Toyota" -> "Ⓣ"
            "Ford" -> "Ford"
            "Hyundai" -> "H"
            "Kia" -> "KIA"
            "Nissan" -> "N"
            else -> name.take(2).uppercase()
        }
        return text(monogram, 17f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(12, 98, 163), Color.rgb(11, 24, 48))).apply {
                cornerRadius = 18f
                setStroke(2, cyan)
            }
        }
    }
    private fun brandGallery() {
        section("CONSTRUCTEURS • ACCÈS RAPIDE")
        listOf("Peugeot","Renault","Citroën","Volkswagen","BMW","Mercedes-Benz","Audi","Toyota","Ford","Hyundai","Kia","Nissan").chunked(4).forEach { names ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            names.forEach { name ->
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(5, 12, 5, 12)
                    background = rounded(panel, blue, 17f)
                    setOnClickListener { selectedMake = name; chooseVehicle() }
                }
                card.addView(brandLogo(name), LinearLayout.LayoutParams(72, 60))
                card.addView(text(name, 10f, Color.WHITE, true).apply { gravity = Gravity.CENTER; setPadding(0, 7, 0, 0) })
                line.addView(card, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(3, 3, 3, 3) })
            }
            content.addView(line)
        }
    }
    private fun rowAction(title: String, detail: String, color: Int = cyan, action: () -> Unit) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 19, 20, 19)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(21, 43, 74), Color.rgb(9, 19, 40))).apply { cornerRadius = 24f; setStroke(2, color) }
            elevation = 5f
            isClickable = true
            setOnClickListener { action() }
            setOnTouchListener { view, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> view.animate().scaleX(.97f).scaleY(.97f).setDuration(110).start()
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
                }
                false
            }
        }
        card.addView(text(title + "   ›", 17f, Color.WHITE, true))
        card.addView(text(detail, 12f, Color.rgb(165, 189, 210)).apply { setPadding(0, 6, 0, 0) })
        content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { setMargins(5, 5, 5, 7) })
    }

    private fun showTab(tab: Int) {
        selectedTab = tab
        content.removeAllViews()
        for (i in 0 until nav.childCount) {
            val button = nav.getChildAt(i) as TextView
            button.setTextColor(if (i == tab) cyan else Color.rgb(155, 171, 194))
        }
        when (tab) {
            0 -> {
                section("MON GARAGE")
                rowAction(if (selectedMake.isBlank()) "▣  Choisir mon véhicule" else "▣  $selectedMake $selectedModel",
                    if (selectedMake.isBlank()) "143 marques • modèle et année" else "Année $selectedYear • Modifier",
                    Color.rgb(42, 192, 255)) { chooseVehicle() }
                brandGallery()
                section("TABLEAU DE BORD")
                rowAction("◉  Connexion ELM327", "Bluetooth Classic • appuyer pour choisir le boîtier", Color.rgb(18, 220, 156)) { connect() }
                section("DIAGNOSTIC RAPIDE")
                addGrid(listOf(
                    tile("Codes défauts", "Lecture DTC", Color.rgb(38, 144, 255), "⚙") { execute("03", "Codes défauts") },
                    tile("Mesures directes", "Capteurs moteur", Color.rgb(255, 85, 76), "▥") { showTab(2) },
                    tile("Identification", "VIN et protocole", Color.rgb(40, 221, 129), "▣") { showTab(1) },
                    tile("Rapport", "Historique session", Color.rgb(255, 166, 45), "▤") { showTab(3) },
                    tile("Régime moteur", "tr/min", Color.rgb(181, 89, 250), "◉") { execute("010C", "Régime moteur") },
                    tile("Température", "°C", Color.rgb(255, 78, 82), "♨") { execute("0105", "Température moteur") }
                ))
                section("ÉTAT DE LA SESSION")
                rowAction("Voir le journal", "Résultats et réponses du véhicule") { showTab(3) }
            }
            1 -> {
                section("MON VÉHICULE")
                rowAction("Marque • modèle • année",
                    if (selectedMake.isBlank()) "Sélectionner mon véhicule" else "$selectedMake $selectedModel • $selectedYear",
                    Color.rgb(29, 218, 147)) { chooseVehicle() }
                section("IDENTIFICATION MULTIMARQUE")
                rowAction("Lire le VIN", "Mode OBD 09 PID 02 • selon compatibilité") { execute("0902", "VIN (réponse brute)") }
                rowAction("Protocole du véhicule", "Interroger l'interface ELM327") { execute("ATDP", "Protocole actif") }
                rowAction("Connexion Bluetooth", "Sélectionner un adaptateur déjà associé") { connect() }
                section("COMPATIBILITÉ")
                rowAction("Toutes marques OBD-II", "Fonctions génériques uniquement. L'accès ABS, airbag et constructeur nécessite des protocoles spécifiques.") {}
            }
            2 -> {
                section("MESURES MOTEUR • DONNÉES DU VÉHICULE")
                rowAction(if (isConnected) "●  ELM327 CONNECTÉ" else "○  AUCUNE DONNÉE DISPONIBLE",
                    if (isConnected) "Appuyez sur une mesure pour interroger le calculateur" else "Connectez un boîtier OBD2 pour obtenir des valeurs réelles",
                    if (isConnected) Color.rgb(30, 218, 145) else Color.rgb(255, 156, 55)) { if (!isConnected) connect() }
                addGrid(listOf(
                    tile("Régime", "PID 010C", Color.rgb(173, 94, 255), "◉") { execute("010C", "Régime") },
                    tile("Vitesse", "PID 010D", Color.rgb(36, 160, 255), "➤") { execute("010D", "Vitesse") },
                    tile("Température", "PID 0105", Color.rgb(255, 85, 85), "♨") { execute("0105", "Température") },
                    tile("Charge", "PID 0104", Color.rgb(30, 218, 145), "▥") { execute("0104", "Charge moteur") },
                    tile("Admission", "PID 010B", Color.rgb(255, 156, 55), "◎") { execute("010B", "Pression admission") },
                    tile("Papillon", "PID 0111", Color.rgb(40, 212, 229), "◈") { execute("0111", "Papillon") },
                    tile("Tension", "PID 0142", Color.rgb(36, 213, 195), "ϟ") { execute("0142", "Tension module") },
                    tile("Défauts", "Mode 03", Color.rgb(255, 95, 70), "⚙") { execute("03", "Codes défauts") }
                ))
                if (latestReadings.isNotEmpty()) {
                    section("DERNIÈRES MESURES REÇUES")
                    latestReadings.forEach { (key, value) -> rowAction(key, value) {} }
                }
                section("INFORMATIONS")
                rowAction("Données réelles uniquement", "Les valeurs sont lues sur le véhicule, sans démonstration fictive.") {}
            }
            3 -> {
                section("RAPPORT DE DIAGNOSTIC")
                rowAction("Statut", if (isConnected) "Connecté au boîtier ELM327" else "Hors ligne • données indisponibles") {}
                content.addView(results.apply { (parent as? android.view.ViewGroup)?.removeView(this) },
                    LinearLayout.LayoutParams(-1, -2).apply { setMargins(5, 5, 5, 12) })
                rowAction("Partager le journal", "Exporter les résultats en texte vers une autre application") {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, results.text.toString())
                    }
                    startActivity(android.content.Intent.createChooser(send, "Partager le diagnostic"))
                }
                section("MAINTENANCE")
                rowAction("Effacer les défauts", "Action sensible • confirmation obligatoire", Color.rgb(255, 98, 75)) {
                    AlertDialog.Builder(this).setTitle("Confirmer l'effacement")
                        .setMessage("Cette opération peut supprimer des données de diagnostic. À effectuer après réparation uniquement.")
                        .setNegativeButton("Annuler", null)
                        .setPositiveButton("Effacer") { _, _ -> execute("04", "Effacement") }.show()
                }
            }
            else -> {
                section("PARAMÈTRES")
                rowAction("Adaptateur OBD2", "Bluetooth Classic • ELM327") { connect() }
                rowAction("Version", "Bilal27000diag • Diagnostic OBD-II générique") {}
                rowAction("Aide Bluetooth", "Contact mis • boîtier branché • appairage Android • connexion dans l’application") {}
                rowAction("Sécurité", "Ne pas utiliser l'application pendant la conduite.") {}
            }
        }
        scroll.post { scroll.scrollTo(0, 0) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vehiclePrefs = getSharedPreferences("vehicle", MODE_PRIVATE)
        selectedMake = vehiclePrefs.getString("make", "") ?: ""
        selectedModel = vehiclePrefs.getString("model", "") ?: ""
        selectedYear = vehiclePrefs.getInt("year", 0)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(12, 20, 12, 12)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(ImageView(this).apply {
            setImageResource(com.mostadiag.app.R.drawable.bilal_engine)
            contentDescription = "Logo Bilal27000diag moteur diagnostic"
        }, LinearLayout.LayoutParams(76, 76))
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12, 0, 0, 0) }
        brand.addView(text("BILAL27000DIAG", 24f, Color.WHITE, true))
        brand.addView(text("DIAGNOSTIC AUTO • OBD-II", 11f, cyan).apply { letterSpacing = 0.1f })
        header.addView(brand)
        root.addView(header)
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(18, 20, 18, 20)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(6, 55, 103), Color.rgb(8, 20, 41), Color.rgb(40, 18, 52))
            ).apply { cornerRadius = 30f; setStroke(2, Color.rgb(29, 128, 190)) }
        }
        val heroRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        heroRow.addView(ImageView(this).apply {
            setImageResource(R.drawable.bilal_engine)
            contentDescription = "Logo diagnostic moteur"
        }, LinearLayout.LayoutParams(120, 120))
        val heroText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heroText.addView(text("DIAGNOSTIC AUTOMOBILE", 17f, Color.WHITE, true))
        heroText.addView(text("MULTIMARQUE • OBD-II", 12f, cyan, true).apply {
            setPadding(0, 8, 0, 0)
        })
        heroRow.addView(heroText, LinearLayout.LayoutParams(0, -2, 1f))
        hero.addView(heroRow)
        hero.addView(text("Votre diagnostic, une longueur d’avance.", 14f, Color.rgb(173, 204, 228), true).apply {
            setPadding(8, 12, 0, 0)
        })
        val car = object : View(this) {
            private val brush = android.graphics.Paint(3)
            override fun onDraw(c: android.graphics.Canvas) {
                val w = width.toFloat(); val h = height.toFloat()
                val outline = android.graphics.Path().apply {
                    moveTo(w * .07f, h * .70f)
                    lineTo(w * .13f, h * .48f)
                    lineTo(w * .31f, h * .42f)
                    lineTo(w * .43f, h * .20f)
                    lineTo(w * .68f, h * .20f)
                    lineTo(w * .81f, h * .43f)
                    lineTo(w * .92f, h * .50f)
                    lineTo(w * .95f, h * .70f)
                    close()
                }
                brush.style = android.graphics.Paint.Style.FILL
                brush.shader = android.graphics.LinearGradient(0f, 0f, w, h,
                    Color.rgb(34, 110, 178), Color.rgb(4, 12, 30), android.graphics.Shader.TileMode.CLAMP)
                c.drawPath(outline, brush)
                brush.shader = null
                brush.style = android.graphics.Paint.Style.STROKE
                brush.strokeWidth = 3f
                brush.color = Color.rgb(76, 204, 255)
                brush.setShadowLayer(10f, 0f, 0f, Color.CYAN)
                c.drawPath(outline, brush)
                brush.strokeWidth = 7f
                c.drawLine(w * .12f, h * .58f, w * .29f, h * .58f, brush)
                c.drawLine(w * .73f, h * .58f, w * .90f, h * .58f, brush)
                brush.clearShadowLayer()
            }
        }
        hero.addView(car, LinearLayout.LayoutParams(-1, 140))
        root.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { setMargins(3, 14, 3, 0) })
        status = text("○  HORS LIGNE  •  Connecter ELM327", 15f, Color.rgb(255, 178, 91), true).apply {
            background = rounded(panel, Color.rgb(255, 178, 91))
            setPadding(19, 21, 19, 21)
            setOnClickListener { connect() }
        }
        root.addView(status, LinearLayout.LayoutParams(-1, -2).apply { setMargins(3, 20, 3, 10) })
        results = text("Journal prêt. Connectez un adaptateur ELM327 Bluetooth Classic déjà associé au téléphone.", 14f, Color.WHITE).apply {
            background = rounded(panel)
            setPadding(18, 18, 18, 18)
        }
        scroll = ScrollView(this).apply { isFillViewport = false }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(panel, Color.rgb(37, 74, 109))
            setPadding(2, 10, 2, 10)
        }
        listOf("⌂\nAccueil", "▣\nVéhicule", "▥\nMesures", "▤\nRapport", "⚙\nRéglages").forEachIndexed { i, label ->
            nav.addView(text(label, 11f).apply {
                gravity = Gravity.CENTER
                setPadding(2, 7, 2, 7)
                setOnClickListener { showTab(i) }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(nav)
        setContentView(root)
        showTab(0)
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
                else @Suppress("DEPRECATION") intent?.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
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
        adapter?.startDiscovery()
    }
    private fun connectDevice(target: BluetoothDevice) {
            thread {
                try {
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