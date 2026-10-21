# Catalogue de suggestions hors connexion — 0.6.2

La version 0.6.1 proposait 139 marques environ mais des modèles pour seulement 14 marques. La version 0.6.2 embarque 1 969 marques, 48 614 modèles et 193 662 couples modèle/année référencés. Voitures, utilitaires, camions, bus et deux-roues sont recherchables. Les suggestions fonctionnent sans réseau, ignorent accents, casse, espaces et ponctuation et comprennent quelques alias usuels, dont VW.

Les noms viennent de trois instantanés publics épinglés par commit :

- [MeterApp/vehicle-db](https://github.com/MeterApp/vehicle-db/tree/ae8a9138c2d3f92b39e199cc1238cc642042417e), avec les notices de ses 23 sources (NHTSA, DfT/DVLA, NZTA, JPJ, EEA, RDW, SENATRAN et catalogues de constructeurs).
- [VehiclesDB 2026.10.4](https://github.com/vehiclesdb/vehiclesdb/tree/d0e40c9661e36931089db1213ac383d2d7ba5a7a), CC BY 4.0. Extraction des noms ; attribution de VehiclesDB et de ses registres amont conservée.
- [Global Car Models](https://github.com/serhatkildaci/global-car-models/tree/44da5c9e5e0f3162d65579033f7a641473308b11), MIT. Extraction des noms ; licence conservée.

Les suggestions existantes sont conservées dans `data/vehicle-catalog/additions.json`. Des noms de modèles pour Lifan et neuf marques insuffisamment couvertes sont documentés, avec leurs liens, dans `reviewed-models.json`. Ces compléments ne créent aucune année de production.

Le catalogue n’est pas exhaustif et sa présence ne prouve aucune compatibilité OBD-II. Le choix reste un profil manuel. La logique de connexion, les commandes et le décodage OBD ne changent pas.

Les années référencées de 1990 à 2027 mélangent des millésimes et des années de fabrication, d’immatriculation ou de catalogue selon leur source. Elles sont proposées en premier pour un modèle connu, puis toutes les années de 1886 à l’année suivant l’année courante. Elles ne sont jamais présentées comme une période de production certifiée. Aucune interpolation ne comble les trous de la source. Toute marque, tout modèle et toute année de cette plage restent saisissables, même sans référence au catalogue. Aucun millésime n’est choisi automatiquement pour un nouveau profil.

L’APK contient un index et 32 fichiers JSON. Seul le groupe de la marque choisie est chargé, avec un cache limité à huit marques. Les crédits et les liens des licences se consultent dans Paramètres → Catalogue véhicules, y compris hors connexion pour leur texte.

Reconstruction : `python3 scripts/build-vehicle-catalog.py` télécharge les instantanés épinglés et régénère les fichiers embarqués. Pour une reconstruction sans réseau, passer `--source-dir <dossier>` avec les six fichiers source décrits dans le script. La compilation Android utilise uniquement les fichiers déjà embarqués.

Vérification : `python3 scripts/verify-vehicle-catalog.py` contrôle tous les groupes, les doublons, les compteurs, les plages d’années, la conservation des anciens modèles et les notices. L’instrumentation Android vérifie les recherches, les alias, les suggestions Lifan, les années anciennes, la validation, la sauvegarde/restauration et la saisie libre. Elle prend des captures des listes de modèles et d’années sur téléphone, écran compact, tablette et texte agrandi.
