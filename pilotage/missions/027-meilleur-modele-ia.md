# Mission 027 — Chercher un modèle embarquable qui voie l'état du houppier

## Mission

**En une phrase (quoi, et pourquoi) :** essayer, sur les photos du DSF et avec la même mesure par fiche laissée de
côté que 026, d'autres extracteurs et un ajustement fin du réseau, pour trouver un modèle exécutable sur le téléphone
qui fasse nettement mieux qu'une réponse sans image et trouve des arbres morts, avant de l'intégrer à l'application.

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui | chaîne et mesure de 026 (`outils/ia/`, branche `mission/026-amorce-ia-dsf`) ; rapport `docs/essais/2026-10-30-amorce-ia.md` |
| A-t-il les moyens ? | oui, vérifié par 026 le 2026-10-30 | photos sous `~/dev/donnees-ramulus/dsf-2025/`, venv ; un GPU n'est pas garanti : à vérifier, sinon réseaux petits et peu d'époques |

**Temps de la main :** appuyer.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `dev`
- **Modèle :** opus
- **Ce qui ne lui est pas demandé :** l'intégration dans l'application (mission 028, après celle-ci) ; envoyer une photo
  à un service distant.

## Décisions qui s'imposent

- **Pascal, 2026-10-30 :** « Chercher un meilleur modèle » (après l'échec de l'amorce de 026 : 29,7 % de classes
  identiques contre 30,1 % sans image, F 0/26). Cela sort de l'ADR 0007 (« extracteur gelé + voisins ») : l'ADR reçoit
  un ajout daté qui le dit, rédigé par l'orchestrateur à la fin de la mission.
- **Contraintes (arbitrages de l'orchestrateur, par délégation) :**
  - **embarquable** : exécutable par LiteRT sur Android, 50 Mo au plus, licence libre des poids et du code, sourcée ;
  - **même mesure que 026** : validation croisée imbriquée par fiche laissée de côté, 246 photos (quatre
    incohérences exclues), comparée à « sans image » (30,1 %) et à l'essai du 2026-10-28 ; aucune photo d'une fiche ne
    sert à régler ou à entraîner ce qui prédit cette fiche ;
  - **cibles** : MB et MR/MA (régression ordinale ou classement), la classe toujours par l'abaque ;
  - **données** : le fixture n'est pas modifié ; les 19 notes de référence nulles et les 3 photos imprimées absentes
    peuvent être ajoutées dans un fichier à part (`outils/ia/complements.csv`), chaque ligne avec fiche, page et
    légende relue, et la mesure est donnée avec et sans ces compléments.

## Pistes à essayer (dans l'ordre, s'arrêter dès qu'une piste passe le critère)

1. Autres extracteurs gelés, plus riches mais embarquables (par exemple EfficientNet-Lite, MobileNetV4, DINOv2-small
   ou un modèle de type CLIP petit, si licence et taille le permettent) + têtes simples.
2. Recadrage sur le houppier (moitié haute, ou plusieurs tuiles moyennées) avant l'extracteur.
3. Ajustement fin des dernières couches d'un petit réseau, avec augmentations (retournement, luminosité, recadrage),
   régularisation forte, arrêt précoce réglé sans la fiche testée.

## Critère (fixé avant la mesure)

Une piste « passe » si, sur la mesure par fiche laissée de côté : classe identique ≥ 40 % **et** au moins 10 arbres F
sur 26 trouvés **et** rappel D-E-F ≥ 60 %. Sinon, la mission rend la meilleure piste et son échec, sans l'habiller.

## Harnais

- **Peut :** étendre `outils/ia/` dans une branche partie de `mission/026-amorce-ia-dsf` ; télécharger des poids sous
  `~/dev/donnees-ramulus/` ; écrire le rapport `docs/essais/2026-10-30-meilleur-modele-ia.md`.
- **Ne doit pas :** commiter une photo ou un poids ; modifier `fixtures/fiches-dsf-2025.json` ; toucher à
  l'application ; envoyer une photo hors de la machine ; toucher à `main`.
- **Sources autorisées :** ADR 0001, 0002, 0007 ; rapports d'essai des 2026-10-28 et 2026-10-30 ; Kälin et al. ;
  documentation des modèles et de LiteRT.

## Valeur provisoire (chiffre, réglage)

- Le critère ci-dessus (40 %, 10 F sur 26, D-E-F 60 %) : provisoire, fixé par l'orchestrateur, à confirmer par Pascal.

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** 026 (branche `mission/026-amorce-ia-dsf`, ee87010).
- **CI rouge attendue :** aucune.

## Tests

- **Comment saura-t-on que c'est juste :** les tests de 026 restent verts ; un test vérifie l'absence de fuite par fiche
  pour chaque piste ; la mesure est rejouable par une commande.

## Terrain

- **Échantillon que Pascal vérifiera :** le tableau des pistes, et dix photos F avec la proposition du meilleur modèle.

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis l'orchestrateur ; la suite (intégrer ou non) : Pascal.
- **Critère d'arrêt de la relecture :** 3 allers-retours.

## Comment saura-t-on que ça marche ?

Un modèle embarquable passe le critère sur la mesure sans fuite, ou l'on sait, chiffres à l'appui, que les photos du
DSF ne suffisent pas.
