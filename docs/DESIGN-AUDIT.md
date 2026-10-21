# Audit et direction graphique — Bilal27000diag 0.6.0

## Pourquoi l’ancienne interface paraissait basique

L’activité Android contenait un bandeau permanent très haut sur les cinq onglets. La voiture était un polygone dessiné sur Canvas, les pictogrammes des caractères Unicode, et les logos distants étaient chargés dans 24 WebView via un CDN. Les dimensions mêlaient pixels bruts et sp. À haute densité les espacements se réduisaient ; le bandeau et la galerie de marques repoussaient les fonctions utiles en dessous du premier écran. Les compteurs traçaient toujours un arc complet et n’exploitaient pas les lectures reçues.

## Traitement des cinq écrans

1. Accueil : photographie automobile embarquée, liaison ELM327, profil manuel du véhicule, quatre raccourcis colorés et instrumentation.
2. Véhicule : profil, VIN et protocole, galerie de 23 logos locaux, recherche complète des marques, distinction entre sélection manuelle et identification OBD.
3. Mesures : deux cadrans gradués, cartes de capteurs, état d’attente explicite et dernières réponses réelles. Acquisition à la demande clairement annoncée.
4. Rapport : état de la session, journal monospace sélectionnable, export et effacement avec confirmation.
5. Paramètres : état de liaison, choix de l’adaptateur, aide lisible, version, crédits et usage véhicule stationné.

Palette : noir #040A13, panneaux bleu nuit #0D1827, cyan #30D6FF. Les accents vert, ambre, corail et violet distinguent les fonctions. Les surfaces, coins, espacements et cibles tactiles utilisent dp ; les textes utilisent sp. Les cartes passent de deux à quatre colonnes sur tablette, les logos de trois à six (deux avec gros caractères). Le contenu large reste centré et limité à 900 dp. La navigation demeure visible et le contenu défile sur chaque écran. Le titre de marque s’ajuste à la largeur disponible.

Les animations sont des transitions de 200 ms et des retours de pression ; la transition d’onglet respecte la désactivation système des animations. Aucun clignotement ou signal d’acquisition fictif.

## Intégrité des données et périmètre OBD

Le bloc de code de `log()` jusqu’à la fin de l’activité (connexion, appairage, socket, commandes AT, décodage et exécution OBD) est conservé à l’identique. Aucun faux VIN, kilométrage, état connecté ou valeur de capteur n’est introduit. Sans réponse réelle, un cadran affiche « — » et « EN ATTENTE » sans progression colorée. Les valeurs reçues sont libellées comme dernières lectures ; l’application n’annonce pas de suivi continu. Les raccourcis ne promettent pas de fonctions constructeur ABS, airbag ou FAP non implémentées.

## Vérification et preuve de livraison

La chaîne GitHub Actions compile l’APK et les tests Android, exécute Android Lint puis lance les vérifications sur émulateur Android 29 : téléphone, écran compact 320 dp, tablette 800 dp et police à 150 %. Deux tests par configuration vérifient navigation des cinq écrans, absence de données inventées, compteurs indisponibles, conservation de l’onglet après recréation, cibles tactiles, absence de débordement horizontal, ressource de logo locale et profil explicitement manuel. Des captures du haut des cinq écrans et du bas des trois écrans longs sont conservées.

La compilation, les résultats et les captures doivent être consultés avant de considérer la livraison comme validée. Le Bluetooth avec une voiture réelle n’est pas testable par cet émulateur.
