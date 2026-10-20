package com.mostadiag.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
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
            background = rounded(panel, color, 25f)
            setPadding(12, 20, 12, 20)
            isClickable = true
            isFocusable = true
            setOnClickListener { fn() }
            addView(text(symbol, 31f, color, true).apply { gravity = Gravity.CENTER })
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

    private fun rowAction(title: String, detail: String, color: Int = cyan, action: () -> Unit) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 19, 20, 19)
            background = rounded(panel, color, 22f)
            isClickable = true
            setOnClickListener { action() }
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
                section("IDENTIFICATION MULTIMARQUE")
                rowAction("Lire le VIN", "Mode OBD 09 PID 02 • selon compatibilité") { execute("0902", "VIN (réponse brute)") }
                rowAction("Protocole du véhicule", "Interroger l'interface ELM327") { execute("ATDP", "Protocole actif") }
                rowAction("Connexion Bluetooth", "Sélectionner un adaptateur déjà associé") { connect() }
                section("COMPATIBILITÉ")
                rowAction("Toutes marques OBD-II", "Fonctions génériques uniquement. L'accès ABS, airbag et constructeur nécessite des protocoles spécifiques.") {}
            }
            2 -> {
                section("MESURES MOTEUR")
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
                section("INFORMATIONS")
                rowAction("Valeurs en réponse brute", "Décodage et graphiques temps réel prévus pour une version ultérieure.") {}
            }
            3 -> {
                section("RAPPORT DE DIAGNOSTIC")
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
                rowAction("Sécurité", "Ne pas utiliser l'application pendant la conduite.") {}
            }
        }
        scroll.post { scroll.scrollTo(0, 0) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(12, 20, 12, 12)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(text("◈", 40f, cyan, true))
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12, 0, 0, 0) }
        brand.addView(text("BILAL27000DIAG", 24f, Color.WHITE, true))
        brand.addView(text("DIAGNOSTIC AUTO • OBD-II", 11f, cyan).apply { letterSpacing = 0.1f })
        header.addView(brand)
        root.addView(header)
        status = text("●  HORS LIGNE  •  Connecter ELM327", 15f, Color.rgb(27, 227, 158), true).apply {
            background = rounded(panel, Color.rgb(27, 227, 158))
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
    private fun log(message: String) = runOnUiThread { results.text = message + "\n\n" + results.text }
    private fun connect() {
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1); return
        }
        val devices = adapter?.bondedDevices?.toList() ?: emptyList()
        if (devices.isEmpty()) { log("Associez d'abord votre boîtier OBD2 dans les paramètres Bluetooth."); return }
        val names = devices.map { "${it.name ?: "Bluetooth"} — ${it.address}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Choisir un boîtier OBD2").setItems(names) { _, index ->
            thread {
                try {
                    socket?.close()
                    val device = devices[index]
                    val s = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
                    adapter?.cancelDiscovery()
                    s.connect()
                    socket = s; input = s.inputStream; output = s.outputStream
                    command("ATZ"); command("ATE0"); command("ATL0"); command("ATS0"); command("ATSP0")
                    runOnUiThread { status.text = "● Connecté : ${device.name}" }
                    log("Connexion établie. Protocole automatique activé.")
                } catch (e: Exception) { log("Échec de connexion : ${e.message}") }
            }
        }.show()
    }
    @Synchronized private fun command(cmd: String): String {
        val out = output ?: error("Boîtier non connecté")
        val inp = input ?: error("Boîtier non connecté")
        out.write((cmd + "\r").toByteArray()); out.flush()
        val sb = StringBuilder(); val until = System.currentTimeMillis()+6000
        while (System.currentTimeMillis() < until) {
            if (inp.available()>0) { val b = inp.read(); if (b<0 || b==62) break; sb.append(b.toChar()) }
            else Thread.sleep(25)
        }
        return sb.toString().trim()
    }
    private fun execute(cmd: String, label: String) {
        thread {
            try { log("$label\n${command(cmd)}") }
            catch (e: Exception) { log("Erreur : ${e.message}") }
        }
    }
    override fun onDestroy() { try { socket?.close() } catch (_:Exception) {}; super.onDestroy() }
}