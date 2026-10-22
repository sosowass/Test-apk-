# Checklist du diagnostic — 0.6.3

L’onglet Diagnostic présente 15 points : 9 lectures OBD-II et 6 contrôles manuels.
L’accueil affiche le nombre de contrôles réalisés et restants. Les filtres Tous,
À vérifier et Vérifiés permettent de reprendre le diagnostic.

- Les contrôles manuels se cochent et se décochent après inspection.
- Une lecture OBD-II valide coche le point correspondant, avec date et réponse.
  Une erreur, une réponse partielle ou un PID indisponible ne coche aucun point.
  Une relecture échouée conserve la dernière lecture réussie et sa date.
- Coché signifie contrôlé ; la présence de DTC n’empêche pas de terminer leur
  lecture et aucune conclusion « véhicule sans défaut » n’est déduite.
- Le suivi reste enregistré localement après fermeture ou rotation. Il est lié au
  profil sauvegardé (marque, modèle, année), indépendamment de la marque parcourue
  dans le catalogue. Deux véhicules identiques partagent donc ce profil : utiliser
  Nouveau diagnostic pour une nouvelle inspection.
- Nouveau diagnostic demande confirmation, remet à zéro ce profil et ignore les
  réponses OBD d’un diagnostic antérieur encore en cours. Les autres profils restent
  enregistrés. Exporter le rapport avant de remettre à zéro pour conserver une trace.
- Le partage inclut les points vérifiés/non vérifiés, les dates, les dernières
  réponses sauvegardées et le journal OBD de la session en cours. Les lectures de ce
  journal portent le nom du profil utilisé au lancement de la commande.

La vérification GitHub Actions compile l’APK et les tests, lance lint et exécute
6 tests Android sur téléphone, écran compact, tablette et grande taille de texte.
Les tests ajoutés couvrent persistance, profils distincts, remise à zéro, réponse
retardée, filtrage, rotation et validation de réponses OBD. La communication avec
un véritable véhicule/ELM327 doit être vérifiée sur matériel.
