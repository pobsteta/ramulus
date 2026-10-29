# Dépendances et outils hors APK

| Composant | Version | Licence | Source et usage |
|---|---|---|---|
| New Rocker Regular (police de l'icône) | – | OFL-1.1 | `google/fonts`, `ofl/newrocker` ; téléchargée par `app/icone/generer_icone.py`, non versionnée (voir `.gitignore`), comme Surculus. Outil de génération (avec Pillow), absent de l'APK |
| Pillow (génération de l'icône) | 10.x ou plus | HPND | PyPI ; lancé à la main (`python app/icone/generer_icone.py`), absent de l'APK |
| terrain-style (bb2b519, tag v0.7.1) | bb2b519 | GPL-3.0 (LICENSE de `pobsteta/terrain`) | `github.com/pobsteta/terrain`, construit par JitPack ; le style de Marculus (couleurs, formes) recopié dans le thème de l'application, comme Surculus (mission 019) |
| Compose Material Icons (core et extended) | BOM 2026.02.01 | Apache-2.0 | Google Maven ; icônes de la bascule liste/kanban et du menu ⋮ (mission 019), élaguées par R8 |
