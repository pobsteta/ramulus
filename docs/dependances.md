# Dépendances qui demandent une source et une licence

Les versions sont dans `gradle/libs.versions.toml`, qui dit pour chacune d'où elle vient. Ce fichier garde, pour les
dépendances ajoutées par une mission, leur source et leur licence.

| Dépendance | Version | Mission | Source | Licence | Pourquoi |
|---|---|---|---|---|---|
| `androidx.camera:camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view` (CameraX) | 1.6.2 | 008 | Google Maven (`https://dl.google.com/dl/android/maven2/androidx/camera/`), version stable listée le 2026-10-29 ; documentation : <https://developer.android.com/media/camera/camerax> | Apache-2.0 | Prendre la photo du houppier dans Ramulus, sans passer par l'appareil photo du téléphone (`ACTION_IMAGE_CAPTURE`) qui peut la ranger en galerie ou l'envoyer dans un nuage (ADR 0006). Aucune donnée n'est envoyée : CameraX travaille en local. |

Le test du successeur (fiche n° 11) : CameraX est remplaçable par Camera2 seul, au prix d'un écran de prise de vue
réécrit ; la photo reste un JPEG ordinaire dans `photos/`, lisible partout.

# Dépendances et outils hors APK

| Composant | Version | Licence | Source et usage |
|---|---|---|---|
| New Rocker Regular (police de l'icône) | – | OFL-1.1 | `google/fonts`, `ofl/newrocker` ; téléchargée par `app/icone/generer_icone.py`, non versionnée (voir `.gitignore`), comme Surculus. Outil de génération (avec Pillow), absent de l'APK |
| Pillow (génération de l'icône) | 10.x ou plus | HPND | PyPI ; lancé à la main (`python app/icone/generer_icone.py`), absent de l'APK |
| terrain-style (bb2b519, tag v0.7.1) | bb2b519 | GPL-3.0 (LICENSE de `pobsteta/terrain`) | `github.com/pobsteta/terrain`, construit par JitPack ; le style de Marculus (couleurs, formes) recopié dans le thème de l'application, comme Surculus (mission 019) |
| Compose Material Icons (core et extended) | BOM 2026.02.01 | Apache-2.0 | Google Maven ; icônes de la bascule liste/kanban et du menu ⋮ (mission 019), élaguées par R8 |
