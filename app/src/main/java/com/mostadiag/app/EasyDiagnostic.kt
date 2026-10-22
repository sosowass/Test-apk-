package com.mostadiag.app

/** Plain-language instructions; completion always comes from the saved checklist. */
object EasyDiagnostic {
    data class Step(val id: String?, val title: String, val instruction: String, val picture: String)
    val steps = listOf(
        Step(null, "Lire la voiture", "Branche le petit boîtier sous le volant. Garde la voiture à l’arrêt. Le téléphone va lire les informations de la voiture.", "car"),
        Step("oil", "L’huile du moteur", "Regarde si le niveau d’huile est entre les deux repères. Suis les indications du carnet de la voiture.", "oil"),
        Step("coolant", "Le liquide du moteur", "Attends que le moteur soit froid. Regarde si le liquide est entre MIN et MAX. N’ouvre pas le bouchon quand le moteur est chaud.", "temperature"),
        Step("tyres", "Les pneus", "Regarde les quatre pneus. Vérifie leur pression et cherche une coupure ou un pneu très usé.", "tyre"),
        Step("brakes", "Les freins", "Regarde le niveau du liquide de frein et les pièces visibles. Si tu ne sais pas vérifier, choisis Plus tard et demande de l’aide.", "brakes"),
        Step("lights", "Les lumières", "Allume les feux et les clignotants. Fais-toi aider pour regarder les feux à l’arrière.", "lights"),
        Step("leaks", "Les fuites", "Regarde sous la voiture, sans te glisser dessous. Cherche des traces de liquide au sol.", "leak")
    )
    fun firstUnfinished(entries: Map<String, DiagnosticChecklist.Entry>): Int {
        if (DiagnosticChecklist.ITEMS.any { it.command != null && entries[it.id]?.checked != true }) return 0
        return steps.indexOfFirst { it.id != null && entries[it.id]?.checked != true }.let { if (it < 0) steps.size else it }
    }
}
