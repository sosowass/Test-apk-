package com.mostadiag.app

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

/** A checked item records work performed, never a verdict on the vehicle's condition. */
class DiagnosticChecklist(private val preferences: SharedPreferences) {
    data class Item(val id: String, val group: String, val title: String, val help: String, val command: String? = null)
    data class Scope(val key: String, val session: String, val vehicle: String)
    data class Entry(val checked: Boolean = false, val timestamp: Long = 0, val detail: String = "")

    fun scope(make: String, model: String, year: Int): Scope? {
        if (make.isBlank() || model.isBlank() || year <= 0) return null
        // JSON preserves field boundaries (e.g. model names containing separators).
        val identity = JSONArray(listOf(make.trim().lowercase(Locale.ROOT), model.trim().lowercase(Locale.ROOT), year)).toString()
        val key = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        val document = document(key)
        if (!document.has("session")) {
            document.put("session", UUID.randomUUID().toString())
            preferences.edit().putString(key, document.toString()).apply()
        }
        return Scope(key, document.getString("session"), "${make.trim()} ${model.trim()} · $year")
    }

    private fun document(key: String): JSONObject = try {
        JSONObject(preferences.getString(key, "{}") ?: "{}")
    } catch (_: org.json.JSONException) { JSONObject() }

    fun entries(scope: Scope): Map<String, Entry> {
        val document = document(scope.key)
        if (document.optString("session") != scope.session) return emptyMap()
        val items = document.optJSONObject("items") ?: return emptyMap()
        return ITEMS.associate { item ->
            val entry = items.optJSONObject(item.id)
            item.id to Entry(entry?.optBoolean("checked") ?: false, entry?.optLong("timestamp") ?: 0, entry?.optString("detail") ?: "")
        }
    }

    fun set(scope: Scope, id: String, checked: Boolean, detail: String = ""): Boolean {
        require(ITEMS.any { it.id == id })
        val document = document(scope.key)
        // A delayed OBD reply must not complete an item in a newly reset diagnostic.
        if (document.optString("session") != scope.session) return false
        val items = document.optJSONObject("items") ?: JSONObject()
        items.put(id, JSONObject().put("checked", checked)
            .put("timestamp", if (checked) System.currentTimeMillis() else 0)
            .put("detail", if (checked) detail else ""))
        document.put("items", items)
        preferences.edit().putString(scope.key, document.toString()).apply()
        return true
    }

    fun reset(scope: Scope) {
        preferences.edit().putString(scope.key, JSONObject()
            .put("session", UUID.randomUUID().toString()).toString()).apply()
    }

    companion object {
        val ITEMS = listOf(
            Item("vin", "LECTURES OBD-II", "Identification VIN", "Lire l’identifiant puis le comparer à la carte grise.", "0902"),
            Item("protocol", "LECTURES OBD-II", "Protocole du véhicule", "Lire le protocole actif de l’ELM327.", "ATDP"),
            Item("dtc", "LECTURES OBD-II", "Codes défauts moteur", "Lire les DTC ; un contrôle réalisé peut contenir des défauts.", "03"),
            Item("rpm", "LECTURES OBD-II", "Régime moteur", "Relever le régime moteur à l’arrêt.", "010C"),
            Item("temperature", "LECTURES OBD-II", "Température moteur", "Relever la température du liquide de refroidissement.", "0105"),
            Item("voltage", "LECTURES OBD-II", "Tension du module", "Relever la tension signalée par le calculateur.", "0142"),
            Item("load", "LECTURES OBD-II", "Charge moteur", "Lire la charge calculée du moteur.", "0104"),
            Item("intake", "LECTURES OBD-II", "Pression admission", "Lire la pression absolue d’admission.", "010B"),
            Item("throttle", "LECTURES OBD-II", "Position du papillon", "Lire la position du papillon.", "0111"),
            Item("oil", "CONTRÔLES MANUELS", "Niveau d’huile", "Vérifier le niveau selon les consignes du constructeur."),
            Item("coolant", "CONTRÔLES MANUELS", "Niveau de liquide de refroidissement", "Contrôler moteur froid, sans ouvrir un circuit chaud."),
            Item("tyres", "CONTRÔLES MANUELS", "Pneumatiques", "Contrôler pression, usure et état des pneus."),
            Item("brakes", "CONTRÔLES MANUELS", "Freinage", "Inspecter les éléments accessibles et le niveau de liquide."),
            Item("lights", "CONTRÔLES MANUELS", "Éclairage et signalisation", "Vérifier feux, clignotants et feux stop."),
            Item("leaks", "CONTRÔLES MANUELS", "Fuites visibles", "Inspecter visuellement le sol et les zones accessibles.")
        )

        /** Accept actual supported replies; timeouts, adapter acknowledgements and partial data do not count. */
        fun validReply(command: String, raw: String): Boolean {
            val reply = raw.trim().uppercase(Locale.ROOT)
            if (reply.isEmpty() || listOf("NO DATA", "ERROR", "UNABLE", "STOPPED", "BUS INIT", "CAN ERROR", "?").any { reply.contains(it) }) return false
            if (command == "ATDP") return Regex("(ISO\\s*9141|ISO\\s*14230|ISO\\s*15765|SAE\\s*J1850|SAE\\s*J1939|USER[12]\\s+CAN)")
                .containsMatchIn(reply)
            val hex = reply.lineSequence().map { it.trim().replace(Regex("^[0-9A-F]+:\\s*"), "") }
                .filter { it.matches(Regex("[0-9A-F ]+")) }.joinToString("").replace(" ", "")
            if (command == "03") return Regex("43(?:[0-9A-F]{4})+").containsMatchIn(hex)
            if (command == "0902") {
                val start = hex.indexOf("490201")
                if (start < 0) return false
                val data = hex.substring(start + 6)
                if (data.length < 34) return false
                val vin = data.take(34).chunked(2).map { it.toInt(16).toChar() }.joinToString("")
                return vin.matches(Regex("[A-HJ-NPR-Z0-9]{17}"))
            }
            if (!command.startsWith("01") || command.length != 4) return false
            val bytes = if (command in listOf("010C", "0142")) 2 else 1
            return Regex("41${command.takeLast(2)}[0-9A-F]{${bytes * 2}}").containsMatchIn(hex)
        }
    }
}
