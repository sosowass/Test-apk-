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
    private val checklist by lazy { DiagnosticChecklist(getSharedPreferences("diagnostic_checklist", MODE_PRIVATE)) }
    private var checklistFilter = 0
    private val pendingChecks = mutableSetOf<String>()
    private var easyMode = true
    private var easyStep = -1
    private var easyScopeId = ""
    private var activeBatch: String? = null
    private var easyReadMessage = ""
    private var speech: android.speech.tts.TextToSpeech? = null
    private var speechReady = false
    private var speechUnavailable = false
    private var pendingSpeech = ""

    private fun speakInstruction(message: String) {
        if (speechUnavailable) {
            Toast.makeText(this, "La voix française n’est pas disponible. Tu peux lire la consigne à l’écran.", Toast.LENGTH_LONG).show()
            return
        }
        pendingSpeech = message
        if (speechReady) {
            speech?.speak(message, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "instruction")
        } else if (speech == null) {
            speech = android.speech.tts.TextToSpeech(this) { result ->
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    val language = if (result == android.speech.tts.TextToSpeech.SUCCESS)
                        speech?.setLanguage(java.util.Locale.FRANCE) else null
                    speechReady = language != null && language >= android.speech.tts.TextToSpeech.LANG_AVAILABLE
                    speechUnavailable = !speechReady
                    if (speechReady) speech?.speak(pendingSpeech, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "instruction")
                    else Toast.makeText(this, "La voix française n’est pas disponible sur ce téléphone. La consigne reste écrite à l’écran.", Toast.LENGTH_LONG).show()
                }
            }
        } else Toast.makeText(this, "La voix se prépare. Tu peux aussi lire la consigne.", Toast.LENGTH_SHORT).show()
    }

    private fun easyButton(label: String, viewTag: String, accent: Int = cyan, action: () -> Unit): Button = Button(this).apply {
        tag = viewTag; text = if (viewTag.contains("listen") || viewTag == "easy_help") "🔊 $label" else label
        textSize = 20f; isAllCaps = false; minHeight = dp(64)
        setPadding(dp(16), dp(14), dp(16), dp(14)); setTextColor(bg)
        backgroundTintList = ColorStateList.valueOf(accent)
        setOnClickListener { action() }
    }

    private fun easyHeading(title: String, instruction: String) {
        add(text(title, 28f, Color.WHITE, true).apply { setPadding(0, dp(12), 0, dp(6)) })
        add(text(instruction, 17f, muted))
    }

    private fun easyAction(title: String, detail: String, kind: String, viewTag: String, action: () -> Unit) {
        val card = LinearLayout(this).apply {
            tag = viewTag; gravity = Gravity.CENTER_VERTICAL; background = surface()
            minimumHeight = dp(88); setPadding(dp(16), dp(18), dp(16), dp(18))
            addView(icon(kind, cyan, 36))
            addView(column().apply {
                setPadding(dp(16), 0, 0, 0)
                addView(text(title, 20f, Color.WHITE, true))
                addView(text(detail, 16f, muted).apply { setPadding(0, dp(6), 0, 0) })
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        actionable(card, cyan, action); add(card)
    }

    private fun showEasyHome() {
        easyHeading("On commence ?", "Je te guide, une chose à la fois.")
        add(easyButton("Commencer", "easy_start", green) {
            if (checklistScope() == null) chooseVehicle() else { easyStep = -1; showTab(3) }
        })
        val scope = checklistScope()
        easyAction("Ma voiture", scope?.vehicle ?: "Choisir la voiture", "car", "easy_vehicle") { chooseVehicle() }
        easyAction(if (isConnected) "Boîtier connecté" else "Brancher le boîtier",
            if (isConnected) "Le téléphone peut lire la voiture." else "Le petit appareil qui relie la voiture au téléphone.", "bluetooth", "easy_connect") { connect() }
        easyAction("Ce qui est fait", scope?.let { "${EasyDiagnostic.completedCount(checklist.entries(it))} étapes terminées sur 7" }
            ?: "Tes contrôles seront gardés ici.", "report", "easy_results") { showTab(5) }
        add(easyButton("Écouter l’aide", "easy_listen_home") {
            speakInstruction("Choisis la voiture. Branche le petit boîtier sous le volant. Puis touche Commencer. Je te guide, une chose à la fois.")
        })
    }

    private fun showEasyGuide() {
        val scope = checklistScope()
        if (scope == null) {
            easyHeading("Quelle voiture ?", "Choisis d’abord la voiture à vérifier.")
            add(easyButton("Choisir la voiture", "easy_choose_first", green) { chooseVehicle() })
            return
        }
        val entries = checklist.entries(scope)
        val scopeId = "${scope.key}:${scope.session}"
        if (scopeId != easyScopeId) { easyScopeId = scopeId; easyStep = -1; easyReadMessage = "" }
        if (easyStep < 0) easyStep = EasyDiagnostic.firstUnfinished(entries)
        if (easyStep >= EasyDiagnostic.steps.size) {
            easyHeading("Le tour est terminé", "Regarde ce qui est fait et ce qui reste à faire.")
            add(easyButton("Voir le résultat", "easy_finish", green) { showTab(5) })
            add(easyButton("Revenir au début", "easy_back_start") { easyStep = 0; showTab(3) })
            return
        }
        val step = EasyDiagnostic.steps[easyStep]
        val complete = EasyDiagnostic.isComplete(step, entries)
        add(text("Étape ${easyStep + 1} sur ${EasyDiagnostic.steps.size}", 18f, cyan, true).apply { tag = "easy_step"; setPadding(0, dp(12), 0, 0) })
        add(icon(step.picture, if (complete) green else cyan, if (wide) 100 else 64).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, 4)
        easyHeading(step.title, step.instruction)
        add(easyButton("Écouter la consigne", "easy_listen") { speakInstruction(step.title + ". " + step.instruction) })
        if (step.id == null) {
            val count = DiagnosticChecklist.ITEMS.count { it.command != null && entries[it.id]?.checked == true }
            add(text("$count informations reçues sur 9", 18f, if (complete) green else muted).apply { tag = "easy_read_progress"; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE })
            if (easyReadMessage.isNotBlank()) add(text(easyReadMessage, 17f, amber).apply { tag = "easy_read_message" })
            val reading = activeBatch != null
            add(easyButton(if (!isConnected) "Relier le boîtier" else if (reading) "Lecture en cours…" else "Lire la voiture", "easy_read", green) {
                if (!isConnected) connect() else readAllEasy(scope)
            }.apply { isEnabled = !reading })
            if (reading) add(easyButton("Arrêter après cette lecture", "easy_stop", amber) {
                activeBatch = null; easyReadMessage = "La lecture en cours va se terminer. Les informations reçues sont gardées."; showTab(3)
            })
            add(easyButton("Continuer", "easy_next") { easyStep++; easyReadMessage = ""; showTab(3) })
        } else {
            if (complete) add(text("Déjà fait · ${checklistDate(entries.getValue(step.id).timestamp)}", 17f, green))
            add(easyButton(if (complete) "C’est déjà fait · Continuer" else "C’est fait", "easy_done", green) {
                if (!complete) checklist.set(scope, step.id, true, "Contrôle manuel")
                easyStep++; showTab(3)
            })
            if (complete) add(easyButton("Remettre à faire", "easy_undo", amber) { checklist.set(scope, step.id, false); showTab(3) })
            add(easyButton("Plus tard", "easy_later") { easyStep++; showTab(3) })
        }
        if (easyStep > 0) add(easyButton("Étape d’avant", "easy_previous") { easyStep--; showTab(3) })
    }

    private fun readAllEasy(scope: DiagnosticChecklist.Scope) {
        if (activeBatch != null) return
        val token = UUID.randomUUID().toString()
        activeBatch = token
        val items = DiagnosticChecklist.ITEMS.filter { it.command != null }
        fun next(index: Int) {
            val current = checklistScope()
            if (isDestroyed || activeBatch != token) return
            if (current == null || current.key != scope.key || current.session != scope.session) {
                activeBatch = null; return
            }
            if (index >= items.size) {
                activeBatch = null
                val count = items.count { checklist.entries(scope)[it.id]?.checked == true }
                easyReadMessage = if (count == items.size) "Les informations sont enregistrées. Tu peux continuer."
                    else "Certaines informations manquent. Tu peux réessayer ou continuer."
                if (selectedTab == 3 && easyStep == 0) showTab(3)
                return
            }
            easyReadMessage = "Lecture ${index + 1} sur ${items.size}. Attends un peu…"
            execute(requireNotNull(items[index].command), items[index].title) { next(index + 1) }
        }
        next(0)
    }

    private fun showEasyResults() {
        val scope = checklistScope()
        if (scope == null) {
            easyHeading("Rien pour le moment", "Tes contrôles apparaîtront ici.")
            add(easyButton("Commencer", "easy_result_start", green) { chooseVehicle() })
            return
        }
        val entries = checklist.entries(scope)
        val done = EasyDiagnostic.completedCount(entries)
        easyHeading("Ce qui est fait", scope.vehicle)
        add(text("$done sur 7 étapes terminées", 24f, green, true).apply { tag = "easy_result_count" })
        add(text("${7 - done} encore à faire", 18f, amber))
        add(easyButton("Reprendre les contrôles", "easy_resume", green) { easyStep = -1; showTab(3) })
        add(easyButton("Partager le résultat", "easy_share") { shareReport() })
        add(text("Fait veut dire vérifié. Cela ne veut pas dire que la voiture n’a aucun problème.", 17f, muted))
        EasyDiagnostic.steps.forEachIndexed { index, step ->
            val checked = EasyDiagnostic.isComplete(step, entries)
            easyAction("${if (checked) "✓ Fait" else "À faire"} · ${step.title}",
                if (checked) "Tu peux revoir ce contrôle." else "Toucher pour reprendre ici.", step.picture, "easy_review_$index") {
                easyStep = index; easyScopeId = "${scope.key}:${scope.session}"; showTab(3)
            }
        }
        add(easyButton("Tout recommencer", "easy_reset", amber) {
            AlertDialog.Builder(this).setTitle("Tout recommencer pour cette voiture ?")
                .setMessage("Les cases seront remises à zéro. Partage le résultat avant si tu veux le garder.")
                .setNegativeButton("Garder mes cases", null).setPositiveButton("Recommencer") { _, _ ->
                    activeBatch = null; checklist.reset(scope); easyStep = -1; easyReadMessage = ""; showTab(3)
                }.show()
        })
    }

    private fun showEasySettings() {
        easyHeading("Un coup de main ?", "Les réglages utiles sont ici.")
        easyAction("Choisir la voiture", "Marque, modèle et année", "car", "easy_change_vehicle") { chooseVehicle() }
        easyAction("Relier le boîtier", "Branche-le sous le volant, puis choisis son nom.", "bluetooth", "easy_pair") { connect() }
        add(easyButton("Écouter l’aide", "easy_help") { speakInstruction("Garde la voiture à l’arrêt. Branche le petit boîtier sous le volant. Touche Relier le boîtier puis choisis son nom dans la liste.") })
        easyAction("Tous les outils", "Ouvrir les mesures et les réglages techniques.", "settings", "easy_advanced") { changeMode(false) }
    }

    private fun changeMode(simple: Boolean) {
        activeBatch = null; speech?.stop(); easyMode = simple
        getSharedPreferences("interface", MODE_PRIVATE).edit().putBoolean("easy", simple).apply()
        rebuildNavigation(); showTab(0)
    }

    private fun rebuildNavigation() {
        nav.removeAllViews()
        val routes = if (easyMode) listOf(Triple(0, "Accueil", "home"), Triple(3, "Vérifier", "shield"), Triple(5, "Résultat", "report"))
            else listOf(Triple(0, "Accueil", "home"), Triple(1, "Véhicule", "car"), Triple(2, "Mesures", "bars"), Triple(3, "Diagnostic", "report"), Triple(4, "Réglages", "settings"))
        routes.forEach { (route, label, picture) ->
            nav.addView(column().apply {
                gravity = Gravity.CENTER; setPadding(dp(2), dp(8), dp(2), dp(8)); minimumHeight = dp(64)
                addView(icon(picture, muted, if (easyMode) 28 else 23))
                addView(text(label, if (easyMode) 16f else 10f, muted, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(5), 0, 0) })
                tag = "tab_$route"; contentDescription = label
                actionable(this) { speech?.stop(); showTab(route) }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(2); marginEnd = dp(2) })
        }
    }

    // Use the saved profile: browsing a brand must not move the diagnostic to another vehicle.
    private fun checklistScope(): DiagnosticChecklist.Scope? {
        val profile = getSharedPreferences("vehicle", MODE_PRIVATE)
        return checklist.scope(profile.getString("make", "") ?: "", profile.getString("model", "") ?: "", profile.getInt("year", 0))
    }

    private fun checklistDate(timestamp: Long): String = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.FRANCE)
        .format(java.util.Date(timestamp))

    private fun checklistSummary(): String {
        val scope = checklistScope() ?: return "Choisir un véhicule pour commencer"
        val done = checklist.entries(scope).values.count { it.checked }
        return "$done/${DiagnosticChecklist.ITEMS.size} vérifiés · ${DiagnosticChecklist.ITEMS.size - done} à vérifier"
    }

    private fun checklistReport(): String {
        val scope = checklistScope() ?: return "CHECKLIST : aucun véhicule sélectionné."
        val entries = checklist.entries(scope)
        return buildString {
            append("BILAL27000DIAG — DIAGNOSTIC\n").append(scope.vehicle).append("\n")
            append(checklistSummary()).append("\n\n")
            DiagnosticChecklist.ITEMS.forEach { item ->
                val entry = entries[item.id] ?: DiagnosticChecklist.Entry()
                append(if (entry.checked) "[x] Vérifié : " else "[ ] À vérifier : ").append(item.title)
                if (entry.checked) {
                    append(" — ").append(checklistDate(entry.timestamp))
                    if (entry.detail.isNotBlank()) append("\n    ").append(entry.detail)
                }
                append("\n")
            }
            append("\nUne case cochée indique un contrôle réalisé, sans conclure à l’absence de défaut.\n")
        }
    }

    private fun showChecklist() {
        val scope = checklistScope()
        if (scope == null) {
            note("Sélectionnez votre véhicule", "Enregistrez sa marque, son modèle et son année pour retrouver ses contrôles à la prochaine ouverture.", cyan, "car")
            vehicleCard()
            return
        }
        val entries = checklist.entries(scope)
        val total = DiagnosticChecklist.ITEMS.size
        val done = entries.values.count { it.checked }
        add(column().apply {
            background = surface(cyan); setPadding(dp(16), dp(18), dp(16), dp(18))
            addView(text(scope.vehicle, 14f, muted, true))
            addView(text("$done/$total contrôles vérifiés · ${done * 100 / total} %", 21f, Color.WHITE, true).apply {
                tag = "checklist_progress"; setPadding(0, dp(10), 0, dp(10)); accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            })
            addView(ProgressBar(this@MainActivity, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = total; progress = done; progressTintList = ColorStateList.valueOf(green)
                progressBackgroundTintList = ColorStateList.valueOf(Color.rgb(34, 54, 74))
                contentDescription = "$done contrôles vérifiés sur $total"
            }, LinearLayout.LayoutParams(-1, dp(8)))
            addView(text("${total - done} à vérifier · progression sauvegardée sur ce téléphone", 12f, muted).apply { setPadding(0, dp(10), 0, 0) })
            addView(text("Pour un autre véhicule de même marque, modèle et année, lancez un nouveau diagnostic.", 11f, muted).apply { setPadding(0, dp(8), 0, 0) })
        })
        val filters = LinearLayout(this).apply { orientation = if (largeText) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL }
        listOf("Tous", "À vérifier", "Vérifiés").forEachIndexed { index, label ->
            filters.addView(text(label, 12f, if (checklistFilter == index) cyan else muted, true).apply {
                tag = "checklist_filter_$index"; gravity = Gravity.CENTER; minHeight = dp(48)
                isSelected = checklistFilter == index; background = rounded(panel, if (isSelected) cyan else panel, 12f)
                actionable(this) { checklistFilter = index; showTab(3) }
            }, if (largeText) LinearLayout.LayoutParams(-1, -2) else LinearLayout.LayoutParams(0, -2, 1f))
        }
        add(filters)
        val visible = DiagnosticChecklist.ITEMS.filter { item ->
            val checked = entries[item.id]?.checked == true
            checklistFilter == 0 || (checklistFilter == 1 && !checked) || (checklistFilter == 2 && checked)
        }
        if (visible.isEmpty()) note("Aucun contrôle dans ce filtre", if (done == total) "Tous les points ont été vérifiés. Le rapport reste disponible ci-dessous." else "Les points vérifiés apparaîtront ici.", green)
        visible.groupBy { it.group }.forEach { (group, items) ->
            section(group)
            items.forEach { item ->
                val entry = entries[item.id] ?: DiagnosticChecklist.Entry()
                val pending = "${scope.key}:${scope.session}:${item.command}" in pendingChecks
                val card = column().apply { background = surface(if (entry.checked) green else Color.rgb(34, 54, 74)); setPadding(dp(12), dp(10), dp(12), dp(12)) }
                card.addView(CheckBox(this).apply {
                    tag = "check_${item.id}"; text = item.title; textSize = 15f; setTextColor(Color.WHITE)
                    buttonTintList = ColorStateList.valueOf(if (entry.checked) green else muted)
                    minHeight = dp(48); isChecked = entry.checked
                    isEnabled = !pending && (item.command == null || entry.checked)
                    setOnCheckedChangeListener { _, checked ->
                        checklist.set(scope, item.id, checked, "Contrôle manuel")
                        showTab(3)
                    }
                }, LinearLayout.LayoutParams(-1, -2))
                card.addView(text(if (entry.checked) "Vérifié le ${checklistDate(entry.timestamp)}" else "À vérifier", 12f, if (entry.checked) green else amber))
                card.addView(text(item.help, 12f, muted).apply { setPadding(0, dp(6), 0, 0) })
                if (entry.detail.isNotBlank()) card.addView(text(entry.detail, 12f, muted).apply { setPadding(0, dp(6), 0, 0); setTextIsSelectable(true) })
                item.command?.let { command ->
                    card.addView(Button(this).apply {
                        tag = "read_${item.id}"; text = if (pending) "Lecture en cours…" else if (entry.checked) "Relire" else "Lire via OBD-II"
                        isAllCaps = false; minHeight = dp(48); isEnabled = !pending
                        contentDescription = "$text : ${item.title}"
                        setOnClickListener {
                            if (!isConnected) Toast.makeText(this@MainActivity, "Connectez un ELM327 avant cette lecture", Toast.LENGTH_LONG).show()
                            else execute(command, item.title)
                        }
                    }, LinearLayout.LayoutParams(-1, -2))
                }
                add(card)
            }
        }
        rowAction("Nouveau diagnostic", "Remettre à zéro la checklist de ce véhicule", "report", amber) {
            AlertDialog.Builder(this).setTitle("Recommencer le diagnostic ?")
                .setMessage("Les cases, dates et résultats de la checklist de ${scope.vehicle} seront effacés. Partagez le rapport pour les conserver. Les autres véhicules gardent leur suivi.")
                .setNegativeButton("Annuler", null).setPositiveButton("Recommencer") { _, _ ->
                    checklist.reset(scope); checklistFilter = 0; showTab(3)
                }.show()
        }
        note("Contrôle réalisé", "Une case cochée indique un point contrôlé. Elle ne garantit pas l’absence de défaut. Les lectures OBD se cochent après une réponse valide ; cochez les contrôles manuels après inspection.", amber)
    }

    private var selectedMake = ""
    private var selectedModel = ""
    private var selectedYear = 0
    private val vehicleCatalog by lazy { VehicleCatalog(assets) }
    private fun chooseEasyVehicle(values: MutableList<String> = mutableListOf(selectedMake, selectedModel, if (selectedYear > 0) selectedYear.toString() else ""), stage: Int = 0) {
        val prompts = listOf("Quelle est la marque ?", "Quel est le modèle ?", "De quelle année ?")
        val examples = listOf("Par exemple : Renault", "Par exemple : Clio", "Regarde sur la carte grise.")
        val form = column().apply { setPadding(dp(20), dp(12), dp(20), dp(12)) }
        form.addView(text("${stage + 1} sur 3 · ${examples[stage]}", 17f, muted))
        val options = when (stage) {
            0 -> vehicleCatalog.makes
            1 -> vehicleCatalog.models(values[0]).map { it.name }
            else -> vehicleCatalog.yearSuggestions(values[0], values[1])
        }
        val field = AutoCompleteTextView(this).apply {
            tag = "easy_vehicle_field_$stage"; textSize = 22f; minHeight = dp(64)
            setTextColor(Color.WHITE); setHintTextColor(muted); hint = examples[stage]
            setSingleLine(true); threshold = 1; dropDownHeight = dp(180)
            setDropDownBackgroundDrawable(rounded(panel)); setText(values[stage])
            if (stage == 2) inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setAdapter(VehicleSuggestionsAdapter(this@MainActivity, options, if (stage == 0) VehicleCatalog.aliases else emptyMap()))
            setOnClickListener { showDropDown() }
        }
        form.addView(field, LinearLayout.LayoutParams(-1, -2))
        form.addView(easyButton("Écouter la question", "easy_vehicle_listen") { speakInstruction(prompts[stage] + ". " + examples[stage]) })
        val dialog = AlertDialog.Builder(this).setTitle(prompts[stage])
            .setView(ScrollView(this).apply { addView(form) })
            .setNegativeButton(if (stage == 0) "Annuler" else "Retour", null)
            .setPositiveButton(if (stage == 2) "C’est ma voiture" else "Suivant", null).create()
        dialog.show(); dialog.window?.setBackgroundDrawable(rounded(panel))
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
            textSize = 18f; minHeight = dp(56)
            setOnClickListener {
                val answer = field.text.toString().trim()
                val maxYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) + 1
                if (answer.isBlank() || (stage == 2 && (answer.toIntOrNull() ?: 0) !in 1886..maxYear)) {
                    field.error = if (stage == 2) "Écris une année entre 1886 et $maxYear." else "Choisis un nom ou écris-le ici."
                    return@setOnClickListener
                }
                val previous = values[stage]
                values[stage] = if (stage == 0) vehicleCatalog.canonicalMake(answer) ?: answer else answer
                if (stage == 0 && !previous.equals(values[0], true)) { values[1] = ""; values[2] = "" }
                dialog.dismiss()
                if (stage < 2) chooseEasyVehicle(values, stage + 1)
                else {
                    selectedMake = values[0]; selectedModel = values[1]; selectedYear = values[2].toInt()
                    getSharedPreferences("vehicle", MODE_PRIVATE).edit().putString("make", selectedMake)
                        .putString("model", selectedModel).putInt("year", selectedYear).apply()
                    easyStep = -1; showTab(0)
                }
            }
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).apply {
            textSize = 18f; minHeight = dp(56)
            setOnClickListener {
                values[stage] = field.text.toString().trim(); dialog.dismiss()
                if (stage > 0) chooseEasyVehicle(values, stage - 1)
            }
        }
    }

    private fun chooseVehicle() {
        if (easyMode) { chooseEasyVehicle(); return }
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
        dialog.show()
        dialog.window?.setBackgroundDrawable(rounded(panel))
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

    private fun showCatalogCredits() {
        val credits = text(assets.open("vehicle-catalog/credits.txt").bufferedReader().use { it.readText() }, 12f, muted).apply {
            setPadding(dp(20), dp(12), dp(20), dp(16))
            setTextIsSelectable(true)
            android.text.util.Linkify.addLinks(this, android.text.util.Linkify.WEB_URLS)
            setLinkTextColor(cyan)
        }
        AlertDialog.Builder(this).setTitle("Sources du catalogue")
            .setView(ScrollView(this).apply { addView(credits) })
            .setPositiveButton("Fermer", null).show().window?.setBackgroundDrawable(rounded(panel))
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
        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT,
            checklistReport() + "\nJOURNAL OBD-II — SESSION ACTUELLE\n" + results.text.toString()) }
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
            val selected = button.tag == "tab_$tab"
            (button.getChildAt(0) as NeonIcon).apply { tint = if (selected) cyan else muted; invalidate() }
            (button.getChildAt(1) as TextView).setTextColor(if (selected) cyan else muted)
            button.background = if (selected) rounded(Color.rgb(9, 39, 56), Color.rgb(21, 76, 97), 14f) else null
            button.isSelected = selected
        }
        if (easyMode) {
            when (tab) { 3 -> showEasyGuide(); 4 -> showEasySettings(); 5 -> showEasyResults(); else -> showEasyHome() }
        } else when(tab) {
            0 -> {
                hero(); connectionCard(); vehicleCard()
                rowAction("Checklist du diagnostic", checklistSummary(), "report", green) { showTab(3) }
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
                pageTitle("03  /  DIAGNOSTIC", "Checklist du véhicule", "Retrouvez les points vérifiés et ceux qui restent à contrôler.")
                rowAction("Partager le rapport", "Checklist, dates des contrôles et journal OBD-II", "share") { shareReport() }
                showChecklist()
                note(if (isConnected) "Session connectée" else "En attente de connexion", if (history.isEmpty()) "Aucune lecture effectuée dans cette session." else "Le journal conserve les réponses et erreurs de cette session.", if (isConnected) green else cyan, "report")
                section("JOURNAL OBD-II", "Session actuelle")
                (results.parent as? android.view.ViewGroup)?.removeView(results); add(results, 12)
                section("MAINTENANCE")
                rowAction("Effacer les codes défauts", "Après réparation · une confirmation sera demandée", "engine", red) { confirmClear() }
                note("Conserver une trace", "Partagez le journal avant toute intervention pour garder les réponses du véhicule.", amber)
            }
            4 -> {
                pageTitle("04  /  PRÉFÉRENCES", "Paramètres", "Votre atelier, vos repères.")
                easyAction("Revenir au mode facile", "Gros boutons et une étape à la fois", "home", "enable_easy") { changeMode(true) }
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
        scroll.post { scroll.scrollTo(0, if (easyMode) 0 else previousY) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        easyMode = getSharedPreferences("interface", MODE_PRIVATE).getBoolean("easy", true)
        easyStep = savedInstanceState?.getInt("easy_step", -1) ?: -1
        easyScopeId = savedInstanceState?.getString("easy_scope", "") ?: ""
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
            addView(text("VÉRIFIER MA VOITURE", 10f, muted, true).apply { setPadding(0, dp(4), 0, 0) })
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
        rebuildNavigation()
        root.addView(nav)
        window.statusBarColor = bg; window.navigationBarColor = Color.rgb(7, 17, 29)
        setContentView(root)
        checklistFilter = savedInstanceState?.getInt("checklist_filter", 0) ?: 0
        showTab(savedInstanceState?.getInt("tab", 0) ?: 0)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("easy_step", easyStep); outState.putString("easy_scope", easyScopeId)
        outState.putInt("checklist_filter", checklistFilter)
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
        if (adapter == null) {
            Toast.makeText(this, "Ce téléphone n’a pas de Bluetooth. Les contrôles manuels restent disponibles.", Toast.LENGTH_LONG).show()
            return
        }
        if (adapter?.isEnabled != true) {
            startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 20)
            return
        }
        val devices = linkedMapOf<String, BluetoothDevice>()
        val labels = mutableListOf<String>()
        val listAdapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View =
                super.getView(position, convertView, parent).apply {
                    minimumHeight = dp(64)
                    if (this is TextView) textSize = 18f
                }
        }
        val list = ListView(this).apply { adapter = listAdapter }
        fun add(d: BluetoothDevice) {
            if (devices.containsKey(d.address)) return
            devices[d.address] = d
            labels.add((d.name ?: "Boîtier sans nom") + if (easyMode) " · " + d.address.takeLast(5) else " • " + d.address)
            listAdapter.notifyDataSetChanged()
        }
        adapter?.bondedDevices?.forEach { add(it) }
        val dialog = AlertDialog.Builder(this).setTitle(if (easyMode) "Choisis le boîtier" else "Rechercher un ELM327")
            .setMessage(if (easyMode) "Branche le boîtier dans la voiture. Touche son nom quand il apparaît dans la liste." else "Choisir un adaptateur Bluetooth Classic.")
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
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) connect()
            else Toast.makeText(this, "Autorise le Bluetooth pour relier le téléphone à la voiture. Tu peux faire les contrôles manuels en attendant.", Toast.LENGTH_LONG).show()
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 20 && resultCode == RESULT_OK) connect()
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
                    runOnUiThread {
                        status.text = "● Connecté : ${target.name}"
                        if (easyMode && selectedTab in listOf(0, 3)) showTab(selectedTab)
                    }
                    log("Connexion établie. Protocole automatique activé.")
                } catch (e: Exception) {
                    isConnected = false
                    latestReadings.clear()
                    try { socket?.close() } catch (_: Exception) {}
                    input = null; output = null; socket = null
                    runOnUiThread {
                        status.text = "● HORS LIGNE • ELM327"
                        easyReadMessage = "Le boîtier n’est pas relié. Vérifie qu’il est branché, puis réessaie."
                        if (easyMode && selectedTab in listOf(0, 3)) showTab(selectedTab)
                    }
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
    private fun execute(cmd: String, label: String, onComplete: (Boolean) -> Unit = {}) {
        val scope = checklistScope()
        val item = DiagnosticChecklist.ITEMS.firstOrNull { it.command == cmd }
        val pendingKey = if (scope != null && item != null) "${scope.key}:${scope.session}:$cmd" else null
        if (pendingKey != null && !pendingChecks.add(pendingKey)) { onComplete(false); return }
        if (selectedTab == 3 && (!easyMode || easyStep == 0)) showTab(3)
        thread {
            var accepted = false
            try {
                val raw = command(cmd)
                val reading = decode(cmd, raw)
                val valid = DiagnosticChecklist.validReply(cmd, raw)
                accepted = valid
                runOnUiThread {
                    if (scope != null && item != null && valid) checklist.set(scope, item.id, true, reading)
                    val currentScope = checklistScope()
                    if (cmd.startsWith("01") && valid && reading != raw.trim() &&
                        scope?.key == currentScope?.key && scope?.session == currentScope?.session) {
                        val measurement = when (cmd) {
                            "010C" -> "Régime"; "0105" -> "Température"; "0142" -> "Tension module"
                            "0104" -> "Charge moteur"; "010B" -> "Pression admission"; "0111" -> "Papillon"
                            else -> label
                        }
                        latestReadings[measurement] = reading.substringBefore("\n")
                    }
                    if (!isDestroyed && selectedTab in listOf(0, 2, 5)) showTab(selectedTab)
                }
                log("${scope?.vehicle ?: "Sans profil véhicule"}\n$label\n$reading")
            }
            catch (e: Exception) { log("Erreur : ${e.message}") }
            finally {
                runOnUiThread {
                    pendingKey?.let { pendingChecks.remove(it) }
                    if (!isDestroyed) {
                        if (selectedTab == 3 && (!easyMode || easyStep == 0)) showTab(3)
                        onComplete(accepted)
                    }
                }
            }
        }
    }
    override fun onDestroy() {
        activeBatch = null; speech?.stop(); speech?.shutdown(); speech = null
        receiver?.let { try { unregisterReceiver(it) } catch (_:Exception) {} }
        try { socket?.close() } catch (_:Exception) {}
        super.onDestroy()
    }
}

