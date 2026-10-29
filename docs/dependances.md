# Dépendances qui demandent une source et une licence

Les versions sont dans `gradle/libs.versions.toml`, qui dit pour chacune d'où elle vient. Ce fichier garde, pour les
dépendances ajoutées par une mission, leur source et leur licence.

| Dépendance | Version | Mission | Source | Licence | Pourquoi |
|---|---|---|---|---|---|
| `androidx.camera:camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view` (CameraX) | 1.6.2 | 008 | Google Maven (`https://dl.google.com/dl/android/maven2/androidx/camera/`), version stable listée le 2026-10-29 ; documentation : <https://developer.android.com/media/camera/camerax> | Apache-2.0 | Prendre la photo du houppier dans Ramulus, sans passer par l'appareil photo du téléphone (`ACTION_IMAGE_CAPTURE`) qui peut la ranger en galerie ou l'envoyer dans un nuage (ADR 0006). Aucune donnée n'est envoyée : CameraX travaille en local. |

Le test du successeur (fiche n° 11) : CameraX est remplaçable par Camera2 seul, au prix d'un écran de prise de vue
réécrit ; la photo reste un JPEG ordinaire dans `photos/`, lisible partout.
