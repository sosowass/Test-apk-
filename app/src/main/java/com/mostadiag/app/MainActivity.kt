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
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28,40,28,20); setBackgroundColor(Color.rgb(5,16,29)) }
        val title = TextView(this).apply { text = "◈ MOSTA DIAG"; textSize = 29f; setTextColor(blue); gravity = Gravity.CENTER }
        root.addView(title)
        status = TextView(this).apply { text = "● Déconnecté"; textSize = 17f; setTextColor(Color.WHITE); setPadding(0,24,0,20) }
        root.addView(status)
        fun action(label: String, fn: () -> Unit) { root.addView(Button(this).apply { text = label; isAllCaps = false; setOnClickListener { fn() } }) }
        action("Connexion Bluetooth OBD2") { connect() }
        action("Lire les codes défauts") { execute("03", "Codes défauts (réponse brute)") }
        action("Régime moteur") { execute("010C", "Régime moteur") }
        action("Vitesse") { execute("010D", "Vitesse") }
        action("Température moteur") { execute("0105", "Température moteur") }
        action("Effacer les codes défauts") {
            AlertDialog.Builder(this).setTitle("Confirmer l'effacement").setMessage("Effacer les codes peut réinitialiser des données de diagnostic. Effectuez cette action uniquement après réparation.").setNegativeButton("Annuler",null).setPositiveButton("Effacer") { _,_ -> execute("04","Effacement") }.show()
        }
        val scroll = ScrollView(this)
        results = TextView(this).apply { text = "Connectez un adaptateur ELM327 Bluetooth Classic déjà associé au téléphone."; textSize = 16f; setTextColor(Color.WHITE); setPadding(0,25,0,20) }
        scroll.addView(results); root.addView(scroll)
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