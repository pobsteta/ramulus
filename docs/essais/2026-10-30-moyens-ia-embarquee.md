# Moyens de l'IA embarquée : photos du DSF, droits, extracteur, moteur Android

Mission 025 (`pilotage/missions/025-sources-ia-embarquee.md`). Rapport de l'acquéreur, consultations du **2026-10-30**.
Propriétaire : Pascal. Aucune photo envoyée à un service distant ; aucune photo ni poids commité.

**Redite de la mission.** Réunir hors du dépôt les 250 photos notées des fiches DSF 2025 rattachées à leurs notes et
choisir en comparant un extracteur d'images libre, petit, exécutable sur Android, pour que la mission d'IA embarquée
(ADR 0007) parte avec ses moyens.

**Décision éclairée.** (1) Peut-on et comment amorcer l'IA embarquée sur les photos du DSF (droits à trancher par
Pascal) ; (2) quel extracteur et quel moteur retenir pour la mission d'IA embarquée.

**Statut du jour (important).** Mon environnement n'a pas de shell : **les 250 photos n'ont PAS été extraites** et le
manifeste n'existe pas. Livré : accès aux PDF vérifié (échantillon), droits cités, comparaison des extracteurs,
moteur, et la procédure exacte d'extraction (section 5) que l'orchestrateur fera exécuter.

## 1. Les 25 PDF : URL et accessibilité

Source des URL : `docs/referentiels/deperis/essences.csv` (main), 19 lignes = **25 liens** (comptés : 6 essences avec
fiche été et fiche hiver = 12 liens, 13 essences avec un seul lien ; total 25). Les ids du fixture
`dsf2025-NN_MM` : NN = 00 à 24 = 25 fiches, dans cet ordre : 00 Cèdre, 01 Chêne liège, 02 pédonculé été, 03 pédonculé
hiver, 04 pubescent été, 05 pubescent hiver, 06 sessile été, 07 sessile hiver, 08 Charme, 09 Douglas, 10 Épicéa,
11 Frêne été, 12 Frêne hiver, 13 Hêtre été, 14 Mélèze, 15 Pin à crochets, 16 Pin d'Alep, 17 Pin laricio, 18 Pin
maritime, 19 Pin sylvestre, 20 Sapin, 21 Châtaignier été, 22 Châtaignier hiver, 23 Chêne vert, 24 Hêtre hiver.
(Cette correspondance NN -> URL est lue dans le champ `essence`/`saison` du fixture, rang de première apparition ;
l'orchestrateur la recalcule dans le script.)

Les 25 URL ont la forme `https://agriculture.gouv.fr/telecharger/<numéro>` (numéros : 126311, 147785, 126312, 126313,
149149, 126314, 126315, 128044, 128045, 126316, 126317, 128046, 126318, 126322, 126325, 126326, 126328, 126329, 137736,
150910, 128047, 128048, 126330, 126331, 126332).

- **Concordance de deux sources** : la page du ministère « La méthode DEPERIS : comment quantifier et mesurer l'état
  de santé d'une forêt et son évolution » (<https://agriculture.gouv.fr/la-methode-deperis-comment-quantifier-et-mesurer-letat-de-sante-dune-foret-et-son-evolution>,
  datée « 08 septembre 2025 », consultée le 2026-10-30) liste les mêmes 25 numéros que `essences.csv` : aucun écart.
  Elle liste en plus le guide « DEPERIS : Comment évaluer l'état du houppier ? » (2.37 Mo, n° 149170), absent du CSV :
  c'est un guide, pas une fiche essence, hors des 250 photos.
- Poids annoncés par la page : environ 45 Mo au total (somme que j'ai faite des 25 tailles affichées, de 874,77 Ko à
  3,48 Mo).
- **Accessibilité testée (3 sur 25)** : 126314 (Chêne pédonculé été, PDF 1.7, 5 pages, 1,9 Mo), 126315 (pédonculé hiver,
  4 pages, 1,8 Mo), 150910 (Pin à crochets, 5 pages, 2 Mo) : téléchargés sans authentification. **Les 22 autres
  n'ont pas été testés.** La fiche 126314 a été lue en entier (5 pages) : la page 1 est l'affiche d'abaque, les photos
  notées sont aux pages 3 à 5, conformément au fixture (`page` 3, 4, 5).

## 2. Droits (cités, non tranchés)

Les citations 2.1 et 2.2 m'ont été rendues par l'outil de lecture de pages web (résumeur) : **à relire à la lettre
par le relecteur sur la page à jour** avant tout usage. La citation 2.3 vient de la licence lue en image (3 pages).

### 2.1 Page « Mentions légales » du ministère
URL : <https://agriculture.gouv.fr/mentions-legales> (page datée du 05 octobre 2026 ; consultée le 2026-10-30).
- « Tous les contenus présents sur le site du ministère sont couverts par le droit d'auteur » (rapporté avec une
  exception pour les documents officiels : formulation exacte à relire).
- « Sauf mention contraire, tous les textes de ce site sont sous licence etalab-2.0 »
- « Toutes les photos, illustrations et vidéos diffusées sur ce site sont la propriété du ministère »
- « Tous les droits de reproduction sont réservés, en France comme à l'étranger. »
- « Pour obtenir des photos ou des vidéos, merci d'envoyer votre demande via le formulaire »
- « toute reprise est conditionnée à l'accord de l'auteur en vertu de l'article L.122-4 »
- « Toute utilisation, reproduction ou réutilisation des données du site à des fins commerciales ou publicitaires »
  (suite de la phrase : accord préalable ; texte exact à relire).

### 2.2 Page DEPERIS du ministère
Même URL qu'en section 1 (datée 08 septembre 2025). Mention de licence : « Sauf mention contraire, tous les textes de ce
site sont sous licence etalab-2.0 ». Crédits photo : « Pascal Xicluna/agriculture.gouv.fr » (photo d'en-tête) ;
« DSF / agriculture.gouv.fr » (la plupart des autres photos). Pas de mention de copyright distincte sur cette page.

### 2.3 Fiche PDF lue (126314 Chêne pédonculé été)
Aucune mention de licence ni de droits aux 5 pages. Seuls textes de provenance : « Département de la santé des forêts »
et logo du ministère (page 1), « Source : Archi » sous les dessins de la page 2, date « 02/2025 » en pied de la page 5.
Pas de crédit photographe sous les photos des pages 3 à 5. **Les 24 autres PDF n'ont pas été lus** pour ce point.

### 2.4 Licence Ouverte / Open Licence version 2.0 (etalab-2.0), avril 2017
Texte lu : `https://static.data.gouv.fr/resources/licence-ouverte-2-0/20260909-072646/etalab-licence-ouverte-v2.0.pdf`
(atteint par redirection depuis `https://www.etalab.gouv.fr/wp-content/uploads/2017/04/ETALAB-Licence-Ouverte-v2.0.pdf`),
consulté le 2026-10-30.
- « Le « Concédant » concède au « Réutilisateur » un droit non exclusif et gratuit de libre « Réutilisation » de
  l'« Information » objet de la présente licence, à des fins commerciales ou non, dans le monde entier et pour une durée
  illimitée, dans les conditions exprimées ci-dessous. »
- « Sous réserve de : mentionner la paternité de l'« Information » : sa source (au moins le nom du « Concédant ») et
  la date de dernière mise à jour de l'« Information » réutilisée. »
- Définition : « L'« Information » : toute information publique figurant dans des documents communiqués ou publiés par
  une administration mentionnée au premier alinéa de l'article L.300-2 du CRPA ; toute information mise à disposition
  par toute personne selon les termes et conditions de la présente licence. »
- « Il est garanti au « Réutilisateur » que les éventuels « Droits de propriété intellectuelle » détenus par des tiers ou
  par le « Concédant » sur l'« Information » ne font pas obstacle aux droits accordés par la présente licence. »
- « Lorsque le « Concédant » détient des « Droits de propriété intellectuelle » cessibles sur l'« Information », il les
  cède au « Réutilisateur » de façon non exclusive, à titre gracieux, pour le monde entier, pour toute la durée des
  « Droits de propriété intellectuelle »… »

### 2.5 Écart relevé entre sources (à porter à Pascal, sans conclusion de ma part)
Le pied de page du ministère limite la licence etalab-2.0 aux « textes » « sauf mention contraire », et dit des photos
qu'elles sont « la propriété du ministère », droits de reproduction réservés. La licence etalab-2.0 elle-même dit
garantir que les droits du Concédant ne font pas obstacle à la réutilisation de l'« Information » publiée sous cette
licence. **Les photos des fiches PDF sont-elles sous etalab-2.0 ?** Aucune source consultée ne le dit expressément
pour les photos des fiches. Cela relève de Pascal (éventuellement par demande au DSF via le formulaire du ministère).

## 3. Extracteurs d'images (candidats)

Consultations du 2026-10-30. « non vérifié » = non lu dans une source.

| Candidat | Licence code / poids | Taille | Format | Source |
|---|---|---|---|---|
| **A. MobileNetV3-Small, `mobilenet_v3_small.tflite` (MediaPipe image_embedder, float32)** | Métadonnées du fichier : « Apache License. Version 2.0 » ; poids et licence de l'entraînement initial : non vérifié (voir note) | 3,9 Mo (taille reportée par l'outil de téléchargement) | **TFLite déjà prêt**, entrée 224x224 RGB 0 à 1, description « Creates the feature vector of the input image » ; dimension du vecteur : non lue (576 ou 1024 selon la tête, à lire dans le fichier) | <https://developers.google.com/edge/mediapipe/solutions/vision/image_embedder> ; <https://storage.googleapis.com/mediapipe-models/image_embedder/mobilenet_v3_small/float32/latest/mobilenet_v3_small.tflite> |
| **B. EfficientNet-Lite0** | Dépôt `tensorflow/tpu` : Apache 2.0 (« Copyright 2017 The TensorFlow Authors », <https://raw.githubusercontent.com/tensorflow/tpu/master/LICENSE>) ; licence propre aux poids : non vérifiée | 4,7 M de paramètres ; taille du .tflite : non vérifiée | point de contrôle `https://storage.googleapis.com/cloud-tpu-checkpoints/efficientnet/lite/efficientnet-lite0.tar.gz` ; export `export_model.py` ; .tflite « feature-vector » sur Kaggle Models : page illisible par mon outil, non vérifié | <https://raw.githubusercontent.com/tensorflow/tpu/master/models/official/efficientnet/lite/README.md> ; précision ImageNet top-1 75,1 % (FP32), 74,4 % (INT8) |
| **C. MobileNetV4-Conv-Small (timm)** | Apache-2.0 (fiche modèle) ; entraîné sur ImageNet-1k | 3,8 M de paramètres ; vecteur 960 | PyTorch/safetensors : **conversion nécessaire** vers LiteRT (non essayée) | <https://huggingface.co/timm/mobilenetv4_conv_small.e2400_r224_in1k> |
| **D. MobileNetV3-Small (timm `lamb_in1k`)** | Apache-2.0 (fiche modèle) ; ImageNet-1k | 2,5 M de paramètres (environ 10 Mo en F32, calcul de ma part) ; vecteur 576 | PyTorch : conversion nécessaire | <https://huggingface.co/timm/mobilenetv3_small_100.lamb_in1k> |

Notes et limites :
- La page MediaPipe ne donne ni licence des modèles (ses licences CC BY 4.0 / Apache 2.0 couvrent la documentation et les
  exemples), ni dimension du vecteur, ni taille. La licence « Apache 2.0 » du candidat A vient des métadonnées embarquées
  dans le .tflite, lues dans les premiers octets du fichier.
- Les modèles A à D sont entraînés sur ImageNet-1k (A : non lu, supposé de même famille). **Les conditions d'usage du jeu
  ImageNet ne sont pas sourcées ici** : à vérifier avant d'écrire « libre » sans réserve (question pour la relecture).
- Aucune mesure de qualité sur les photos de houppier n'a été faite : je ne mesure rien, je réunis les moyens.
- Autres pistes non explorées : `litert-community` sur Hugging Face (420 modèles ; page non triable par mon outil),
  Kaggle Models filtré LiteRT classification (<https://www.kaggle.com/models?framework=tfLite&task=16686>).

**Recommandation (à trancher par Pascal / l'ADR 0007).** Partir du candidat **A** : seul à être déjà au format LiteRT, à
3,9 Mo, avec licence Apache 2.0 inscrite dans le fichier, sans conversion ; garder **B** comme second comparant (autre
famille d'architecture), **D** et **C** seulement si la conversion s'avère simple. Arbitrage final : par une mesure sur les
250 photos (séparabilité des notes), pas par la fiche technique.

## 4. Moteur Android : LiteRT

| Point | Valeur | Source |
|---|---|---|
| Coordonnées Maven | `com.google.ai.edge.litert:litert` ; accélérateur GPU facultatif `com.google.ai.edge.litert:litert-gpu` | <https://developers.google.com/edge/litert/android> (le démarrage rapide cite 2.3.0, la page marque 2.3.2 « Latest ») |
| Dernière version | v2.3.2 (« 26 Oct », année non affichée sur la page) ; v2.3.0 le « 07 Oct » | <https://github.com/google-ai-edge/LiteRT/releases> |
| Licence code | Apache 2.0 (fichier LICENSE du dépôt ; POM de `litert-2.3.2` : « The Apache Software License, Version 2.0 ») | <https://raw.githubusercontent.com/google-ai-edge/LiteRT/main/LICENSE> ; <https://dl.google.com/dl/android/maven2/com/google/ai/edge/litert/litert/2.3.2/litert-2.3.2.pom> |
| Dépendance | `litert` 2.3.2 dépend de `litert-api:2.3.2` (compile) | même POM |
| Taille | AAR `litert-2.3.2.aar` : 7,3 Mo (taille de fichier reportée à l'outil de téléchargement ; l'archive contient `jni/arm64-v8a/libLiteRt.so`, autres ABI non vérifiées) ; `litert-api-2.3.2.aar` : 923,5 Ko. **Poids ajouté à l'APK : non mesuré** (à mesurer par un build, après compression et filtre d'ABI) | <https://dl.google.com/dl/android/maven2/com/google/ai/edge/litert/litert/2.3.2/litert-2.3.2.aar> |
| minSdk | Voir écart ci-dessous ; **toutes les valeurs relevées sont inférieures ou égales à 26** : compatible avec minSdk 26 | idem + <https://developers.google.com/edge/litert/android> |

**Écart relevé (minSdk).** La page Android de Google donne : v2.3.x, v2.2.0 à v2.1.0 : minSdk 23 ; v2.0.3 : 26 ;
v1.4.2 (Interpreter seul) : 21. Les notes de version GitHub disent pour v2.3.0 « minSdk raised to 24 » et pour v2.1.0
« 23 ». Une page tierce et un commentaire de la page de présentation parlent de 24. Cause non établie (documentation non
mise à jour, ou changement entre 2.2 et 2.3). La mesure faisant foi serait le `AndroidManifest.xml` de l'AAR (non décodé
ici : illisible sans outil). Conséquence pour Ramulus : aucune, 23 et 24 sont sous 26.
Autre point : la v2.x propose l'API `CompiledModel` ; l'API `Interpreter` (CPU) est apportée dans les paquets Maven
v2.1.0+. Sans `litert-gpu`, une demande d'accélérateur GPU échoue (source tierce : <https://skills.sh/android/skills/litert-runtime>, non officielle).

## 5. Procédure exacte d'extraction des 250 photos (à faire exécuter)

Outils libres : `poppler-utils` (`pdfimages`, GPL-2/3), et `PyMuPDF` (`pymupdf`, AGPL-3, outil local non distribué) ou, si
Pascal préfère une licence permissive, `pdfplumber` (MIT). Dossier de sortie hors dépôt : `~/dev/donnees-ramulus/dsf-2025/`.

Pourquoi pas un simple `pdfimages` : il sort les images dans l'ordre du flux du PDF, **pas dans l'ordre de lecture** ni
avec leur position, alors que le rattachement à une note exige de savoir quelle image est au-dessus de quelle légende.
**Constat sur le fichier 02 (126314), page 3** : l'id `dsf2025-02_01` porte MB 1 / MR 0 / B (photo de droite) et
`dsf2025-02_02` porte la note A (photo de gauche, « Arbre de référence »). **L'ordre des ids n'est donc pas
l'ordre gauche-droite.** Le rattachement doit se faire par lecture de la légende, jamais par rang.

Étapes :
1. `mkdir -p ~/dev/donnees-ramulus/dsf-2025/pdf ~/dev/donnees-ramulus/dsf-2025/photos` ; `curl -fL -o pdf/NN.pdf https://agriculture.gouv.fr/telecharger/<n°>`
   pour les 25 liens, NN = 00 à 24 selon la table de la section 1 ; poser `pdf/SHA256SUMS` (`sha256sum pdf/*.pdf > pdf/SHA256SUMS`)
   et la date de téléchargement.
2. Par PDF et par page citée dans le fixture, avec PyMuPDF : `page.get_image_info(xrefs=True)` donne, pour chaque image,
   son `bbox` et son `xref` ; ne garder que celles dont la hauteur affichée dépasse environ 150 points (écarte logos et
   dessins) ; extraire avec `doc.extract_image(xref)` (octets natifs, **sans ré-encodage**) et écrire l'extension
   d'origine.
3. Lire par `page.get_text("words")` les légendes « MB = x / MR (ou MA) = y / DEPERIS = Z » situées juste sous chaque
   bbox (légende dans la verticale de l'image, la plus proche en dessous), et les comparer à `mb`, `second`, `classe`
   du fixture : trouver l'id dont (page, mb, second, classe) correspond.
   - Cas ambigus (deux photos de même page avec mêmes notes, p. ex. `dsf2025-02_03` et `02_04`, MB 0 / MR 2 / C) : départager
     par l'ordre de lecture (haut vers bas, puis gauche vers droite) et inscrire « rattachement par rang » dans le manifeste.
   - Photos à notes `null` (**29 photos** avec `mb` null, **29** avec `second` null ; comptées par `grep -c`) : rattacher par la
     seule `classe` si elle est imprimée, sinon par le rang ; inscrire le motif.
   - Le fixture dit `null` quand la fiche n'imprime pas les notes ; **or `dsf2025-02_02` imprime « MB = 0, MR = 0,
     DEPERIS = A »** (page 3, « Arbre de référence ») : écart à lister, pas à corriger en silence.
4. Nommer chaque fichier `<id>.<ext>` (p. ex. `dsf2025-02_02.jpg`) dans `photos/`.
5. Manifeste `manifeste.csv` (UTF-8, séparateur virgule) : `id,fichier,sha256,page,mb,second,classe,pdf_source,url,bbox,
   legende_lue,methode_rattachement,ecart`. SHA-256 de chaque fichier extrait (`sha256sum`) ; `ecart` vide ou texte.
6. Contrôles : exactement **250 lignes** et 250 fichiers ; 250 ids uniques égaux à l'ensemble des ids du fixture ; aucun
   fichier orphelin ; liste des écarts (légende lue différente du fixture) rendue à part, dont les **quatre incohérences
   connues** (`dsf2025-16_08`, `17_08`, `19_07`, `19_08`) toujours listées, jamais corrigées ; échantillon de dix photos
   tirées au hasard (graine notée) avec page et note, pour la vérification de Pascal.
7. Vérifier à l'œil qu'un trait vert « houppier fonctionnel » est dessiné sur les photos des PDF (visible dans la fiche 126314,
   pages 3 à 5) : il peut être **incrusté dans l'image** ou être un tracé vectoriel séparé. S'il est incrusté, l'IA
   l'apprendra ; à décider avec Pascal (recadrage, masquage, ou assumer). Ne rien corriger sans décision.
8. Ne rien commiter : `~/dev/donnees-ramulus/` est hors dépôt ; ne pas envoyer ces images à un service distant.

## 6. Écarts relevés (récapitulatif)

1. Droits : « textes » sous etalab-2.0 vs photos « propriété du ministère » (2.5) : à Pascal.
2. `essences.csv` vs page du ministère : concordants pour les 25 liens ; guide 149170 en plus sur la page (hors périmètre).
3. Fixture : `dsf2025-02_02` a `mb`/`second` null alors que la fiche imprime MB 0 et MR 0 (page 3).
4. Fixture : l'ordre des ids n'est pas l'ordre de lecture sur la page 3 du fichier 02.
5. minSdk de LiteRT : 23 (page Google), 24 (notes de version v2.3.0, page tierce) ; sans effet sous 26.

## 7. Trois points au sol (pour Pascal)

1. **Fiche Chêne pédonculé été, page 3** : ouvrir le PDF 126314, comparer les deux photos et les légendes à
   `dsf2025-02_01` (B, MB 1) et `02_02` (A, référence) ; vérifier que le rattachement suit la légende.
2. **Les quatre incohérences connues** (`dsf2025-16_08`, `17_08`, `19_07`, `19_08`) : sur les fiches Pin d'Alep, Pin laricio,
   Pin sylvestre, vérifier que la légende et la classe de l'abaque divergent bien comme dit, et décider si ces photos
   entrent dans l'amorçage de l'IA.
3. **Un contre-jour ou une photo du trait vert** : prendre trois photos des pages 3 à 5 de la fiche 126314 et dire si le trait
   vert « houppier fonctionnel » fait partie de l'image ; c'est ce que l'IA verrait.

## 8. Question de contrôle

Quelle décision cette donnée va-t-elle éclairer ? Celle de lancer, ou non, la mission d'IA embarquée de l'ADR 0007 :
avec quelles photos (droits à trancher), quel extracteur et quel moteur.

## 9. Journal proposé

2026-10-30 : mission 025, moyens de l'IA embarquée : 25 PDF du DSF localisés (concordance CSV / page du ministère), droits
cités (photos « propriété du ministère », textes sous etalab-2.0 : à trancher par Pascal), extracteur recommandé
MobileNetV3-Small TFLite (Apache 2.0) et moteur LiteRT 2.3.2 ; extraction des 250 photos non faite (pas de shell),
procédure écrite.

## 10. Liste de contrôle

1. Sources et emplacements : URL consultées inscrites ; versions datées (LiteRT 2.3.2, licence v2.0 d'avril 2017,
   pages du ministère datées) ; **point non coché** : l'année de la date « 26 Oct » de la v2.3.2 n'est pas affichée ; les
   citations 2.1 et 2.2 sont passées par un résumeur et doivent être relues à la lettre sur la page à jour.
2. Décomptes : 25 liens et 250 ids comptés (`grep -c`), 29 notes nulles comptées ; **point non coché** : 3 PDF sur 25
   testés et 1 sur 25 lu pour les droits ; 0 photo extraite sur 250 ; manifeste non produit.
3. Termes exacts : citations recopiées ; limites dites (taille APK non mesurée, dimension du vecteur non lue,
   licences des poids de B non vérifiées, conditions d'ImageNet non sourcées).
4. Statut dit tel qu'il est : extraction non faite, dépend d'un shell et des outils ci-dessus.
5. Questions de Pascal posées, non tranchées : droits des photos, usage des fiches à l'amorçage, trait vert, choix de
   l'extracteur.

Liste de contrôle non passée en entier : points non cochés ci-dessus (extraction, manifeste, relecture des citations 2.1
et 2.2, 22 PDF non testés).
