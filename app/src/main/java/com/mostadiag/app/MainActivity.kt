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
    private val adapter by lazy { (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bg = Color.rgb(7, 13, 25)
        val panel = Color.rgb(19, 30, 49)
        val accent = Color.rgb(36, 211, 231)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 30, 24, 16)
            setBackgroundColor(bg)
        }
        val heading = TextView(this).apply {
            text = "R  /  RACHID DIAGNOSTIC"
            textSize = 23f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(4, 12, 4, 6)
        }
        root.addView(heading)
        root.addView(TextView(this).apply {
            text = "CENTRE DE CONTRÔLE  •  OBD-II"
            textSize = 11f
            letterSpacing = 0.18f
            setTextColor(accent)
            setPadding(4, 0, 4, 24)
        })
        status = TextView(this).apply {
            text = "●  HORS LIGNE     |     ELM327"
            textSize = 16f
            setTextColor(accent)
            setBackgroundColor(panel)
            setPadding(20, 22, 20, 22)
        }
        root.addView(status, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 20 })
        val scroll = ScrollView(this).apply { fillViewport = false }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        fun section(title: String) {
            content.addView(TextView(this).apply {
                text = title
                textSize = 12f
                letterSpacing = 0.15f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.rgb(151, 174, 199))
                setPadding(4, 16, 4, 12)
            })
        }
        fun action(title: String, subtitle: String, fn: () -> Unit) {
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(panel)
                setPadding(22, 18, 22, 18)
                isClickable = true
                isFocusable = true
                setOnClickListener { fn() }
            }
            item.addView(TextView(this).apply {
                text = title
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.WHITE)
            })
            item.addView(TextView(this).apply {
                text = subtitle
                textSize = 13f
                setTextColor(Color.rgb(160, 186, 207))
                setPadding(0, 6, 0, 0)
            })
            content.addView(item, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 })
        }
        section("CONNEXION")
        action("◈  Connecter le véhicule", "Associer un adaptateur ELM327 Bluetooth Classic") { connect() }
        section("DIAGNOSTIC")
        action("⌁  Codes défauts", "Lecture des réponses DTC du calculateur") { execute("03", "Codes défauts (réponse brute)") }
        action("◉  Régime moteur", "Mesure instantanée • tr/min") { execute("010C", "Régime moteur") }
        action("➤  Vitesse", "Vitesse du véhicule • km/h") { execute("010D", "Vitesse") }
        action("◌  Température moteur", "Liquide de refroidissement • °C") { execute("0105", "Température moteur") }
        section("MAINTENANCE")
        action("⚠  Effacer les défauts", "Action sensible • confirmation obligatoire") {
            AlertDialog.Builder(this).setTitle("Confirmer l'effacement")
                .setMessage("Effacer les codes peut réinitialiser des données de diagnostic. Effectuez cette action uniquement après réparation.")
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Effacer") { _, _ -> execute("04", "Effacement") }.show()
        }
        section("JOURNAL DE DIAGNOSTIC")
        results = TextView(this).apply {
            text = "Prêt pour le diagnostic. Connectez un boîtier ELM327 Bluetooth Classic déjà associé au téléphone."
            textSize = 14f
            setTextColor(Color.rgb(213, 229, 243))
            setBackgroundColor(panel)
            setPadding(20, 20, 20, 20)
        }
        content.addView(results)
        setContentView(root)
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