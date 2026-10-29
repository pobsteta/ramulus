# Dépendances et outils hors APK

| Composant | Version | Licence | Source et usage |
|---|---|---|---|
| New Rocker Regular (police de l'icône) | – | OFL-1.1 | `google/fonts`, `ofl/newrocker` ; téléchargée par `app/icone/generer_icone.py`, non versionnée (voir `.gitignore`), comme Surculus. Outil de génération (avec Pillow), absent de l'APK |
| Pillow (génération de l'icône) | 10.x ou plus | HPND | PyPI ; lancé à la main (`python app/icone/generer_icone.py`), absent de l'APK |
