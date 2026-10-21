# Livraison Bilal27000diag 0.6.0

Version graphique vérifiée le 21 octobre 2026. Source : [8c08de0](https://github.com/sosowass/Test-apk-/commit/8c08de0a82b73649dfbf89bd11eaf100c2888052).

- [Compilation GitHub Actions réussie](https://github.com/sosowass/Test-apk-/actions/runs/39614145287)
- [APK Android de test, signé debug — archive ZIP](https://github.com/sosowass/Test-apk-/actions/runs/39614145287/artifacts/12351828225)
- [Journaux de compilation, tests et 32 captures](https://github.com/sosowass/Test-apk-/actions/runs/39614145287/artifacts/12351204198)
- [Modifications proposées dans la PR nº 1](https://github.com/sosowass/Test-apk-/pull/1)

## Résultats de vérification

La compilation exécute `:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` avec Java 17, Gradle 8.10.2 et compileSdk 35. Résultat : **BUILD SUCCESSFUL** ; Lint sans erreur bloquante. Les avertissements restants concernent notamment la longueur de tracés vectoriels, les versions des bibliothèques de test et les chaînes de texte.

Les deux tests instrumentés Android ont réussi sur chacune des quatre configurations, soit **8 tests réussis, 0 échec**. Chaque configuration produit `OK (2 tests)` dans son journal `instrumentation-*.txt`.

| Configuration Android 10 (API 29) | Écran | Densité | Police | Résultat |
| --- | --- | --- | --- | --- |
| Téléphone Pixel 2 | 1080 × 1920 px | 420 dpi | 100 % | 2/2 |
| Compact | 640 × 1136 px (320 dp de large) | 320 dpi | 100 % | 2/2 |
| Tablette | 1600 × 2560 px (800 dp de large) | 320 dpi | 100 % | 2/2 |
| Texte agrandi | 1080 × 1920 px | 420 dpi | 150 % | 2/2 |

Contrôles réalisés : navigation des cinq écrans, état sélectionné, cibles tactiles, absence de débordement horizontal, hauteur des cartes y compris dans les lignes incomplètes, absence de mesures inventées, compteurs en attente, profil manuel, recréation de l’activité et **rendu effectif des 23 logos locaux** sur un bitmap Android. Les captures sont prises après stabilisation du rendu de l’émulateur.

Les cinq écrans téléphone ont été examinés visuellement. Les variantes compactes, tablette et gros caractères ont aussi été inspectées ; les dernières corrections des logos et lignes incomplètes ont été revérifiées sur les captures du commit ci-dessus.

## Captures des cinq écrans

| Écran | Téléphone | Compact | Tablette | Police 150 % |
| --- | --- | --- | --- | --- |
| Accueil | [Voir](phone_1_accueil.webp) | [Voir](compact_1_accueil.webp) | [Voir](tablet_1_accueil.webp) | [Voir](large_text_1_accueil.webp) |
| Véhicule | [Voir](phone_2_vehicule.webp) | [Voir](compact_2_vehicule.webp) | [Voir](tablet_2_vehicule.webp) | [Voir](large_text_2_vehicule.webp) |
| Mesures | [Voir](phone_3_mesures.webp) | [Voir](compact_3_mesures.webp) | [Voir](tablet_3_mesures.webp) | [Voir](large_text_3_mesures.webp) |
| Rapport | [Voir](phone_4_rapport.webp) | [Voir](compact_4_rapport.webp) | [Voir](tablet_4_rapport.webp) | [Voir](large_text_4_rapport.webp) |
| Paramètres | [Voir](phone_5_parametres.webp) | [Voir](compact_5_parametres.webp) | [Voir](tablet_5_parametres.webp) | [Voir](large_text_5_parametres.webp) |

Bas des écrans longs :

| Écran | Téléphone | Compact | Tablette | Police 150 % |
| --- | --- | --- | --- | --- |
| Accueil | [Voir](phone_1_accueil_bas.webp) | [Voir](compact_1_accueil_bas.webp) | [Voir](tablet_1_accueil_bas.webp) | [Voir](large_text_1_accueil_bas.webp) |
| Véhicule | [Voir](phone_2_vehicule_bas.webp) | [Voir](compact_2_vehicule_bas.webp) | [Voir](tablet_2_vehicule_bas.webp) | [Voir](large_text_2_vehicule_bas.webp) |
| Mesures | [Voir](phone_3_mesures_bas.webp) | [Voir](compact_3_mesures_bas.webp) | [Voir](tablet_3_mesures_bas.webp) | [Voir](large_text_3_mesures_bas.webp) |

## Finition graphique

Autoévaluation de finition après examen des captures ; ces pourcentages expriment une appréciation visuelle, séparée des résultats des tests automatisés.

| Catégorie | Finition estimée | Éléments livrés |
| --- | --- | --- |
| Identité automobile | 90 % | Noir, bleu nuit et cyan ; marque persistante, accents cohérents |
| Illustration | 90 % | Visuel automobile original embarqué, compression WebP, composition adaptée |
| Présentation des marques | 85 % | 23 véritables logos vectoriels hors connexion, nom lisible, pictogramme générique pour les autres marques |
| Compteurs | 85 % | Cadrans gradués, unités, dernier relevé réel, état « — / EN ATTENTE » |
| Boutons et cartes | 85 % | Pictogrammes natifs, surfaces dégradées, contours, hauteurs cohérentes, retours tactiles |
| Animations | 80 % | Transition d’onglet 200 ms, animation de pression et effet ripple ; désactivation système respectée pour les transitions |
| Typographie et espacements | 85 % | Textes en sp, marges en dp, hiérarchie de titres et journal monospace |
| Adaptation des écrans | 85 % | Contenu défilant, navigation persistante, grilles adaptatives et lignes incomplètes visibles, police 150 % |
| Cohérence des cinq écrans | 85 % | Accueil, garage, instrumentation, rapport et paramètres partagent les mêmes composants |

## Données et périmètre OBD

Le bloc de `log()` jusqu’à la fin de `MainActivity` est conservé à l’identique : connexion Bluetooth, appairage, sockets, commandes AT, décodage et exécution OBD. Comparaison locale réussie après les dernières retouches. Une annotation documente le contrôle de permission Bluetooth existant que Lint ne suit pas à travers sa liste dynamique. Le manifeste inclut la déclaration de localisation approximative pour les anciennes versions d’Android ; la cible Android 32 héritée est conservée.

Sans véhicule connecté, aucun VIN, régime, vitesse, température ou état connecté fictif n’est affiché. Le profil est explicitement manuel. Les mesures sont annoncées comme dernières lectures à la demande. Les capacités ABS, airbag et constructeur ne sont pas présentées comme disponibles.

Le diagnostic Bluetooth avec un véritable ELM327 et un véhicule n’a pas été testé par cet émulateur. L’APK livré est une version debug destinée à l’installation et à l’essai sur téléphone.

Les crédits et licences sont dans [ASSETS.md](https://github.com/sosowass/Test-apk-/blob/codex/premium-automobile/docs/ASSETS.md). L’audit des causes de l’ancienne interface est dans [DESIGN-AUDIT.md](https://github.com/sosowass/Test-apk-/blob/codex/premium-automobile/docs/DESIGN-AUDIT.md).
