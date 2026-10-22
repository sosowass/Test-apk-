# Mode facile — 0.7.0

Le mode facile s’ouvre par défaut. Il propose trois onglets : Accueil, Vérifier,
Résultat. Les boutons ont une hauteur minimale de 64 dp ; les consignes et les
actions utilisent des caractères plus grands et des mots courants.

Le choix de la voiture pose trois questions successives (marque, modèle, année)
avec suggestions hors connexion. Une année invalide garde la question ouverte.
Le Bluetooth peut être activé depuis la demande Android ; après autorisation,
la recherche du boîtier reprend automatiquement.

Le parcours regroupe les neuf lectures OBD dans une première étape automatique,
puis affiche six contrôles manuels, un par écran, avec un pictogramme. Les lectures
restent uniquement des lectures : le mode facile ne propose pas d’effacer les
codes défauts. Une réponse valide coche le point ; une erreur ne le coche pas.
Arrêter la lecture empêche de lancer la suivante et conserve les points reçus.

C’est fait enregistre le contrôle manuel. Plus tard passe à la suite sans le
valider. Étape d’avant permet de revenir ; Remettre à faire décoche un point.
La rotation conserve l’étape affichée. À la réouverture, Reprendre retourne au
premier point incomplet. Le résultat indique ce qui est fait/restant, sans
conclure à l’absence de panne. Le suivi par profil et le partage sont conservés.

Écouter lit une consigne en français avec la synthèse vocale Android. Une voix
française doit être disponible sur le téléphone ; sinon un message le signale et
le texte reste affiché. L’application ne lance pas l’installation d’une voix.

Les réglages donnent accès à Tous les outils. Le bouton Revenir au mode facile
permet de retrouver le parcours guidé sans perdre les contrôles enregistrés.

La suite Android comporte sept tests, exécutés sur quatre formats. Le nouveau
test couvre le mode facile par défaut, les trois questions, la validation de
l’année, les contrôles différés, la sauvegarde, la rotation, les gros boutons,
les limites de l’écran et les passages entre modes. Les fonctions Bluetooth et
la disponibilité réelle d’une voix française restent à vérifier sur téléphone.
