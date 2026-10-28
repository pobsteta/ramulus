# Mission 000 — Socle

- **Phrase** : poser le dépôt Ramulus sur le modèle de Surculus, avec un domaine DEPERIS testé et un premier écran
  de notation manuelle, sans rien décider à la place de Pascal.
- **Homme** : orchestrateur (Claude), à la demande de Pascal (2026-10-28).
- **Moyens** : versions et découpage de Surculus (commit b9cf9d6) ; fiches DSF 2025 fournies par Pascal.
- **Temps de la main** : tenir — Pascal valide la PR.

## Critères d'acceptation

- `:core:test` vert : abaque (36 cases contre `abaque.csv`), bornes des notes, seuils de peuplement à 20 % strict,
  référentiel des 19 essences, 250 photos des fiches hors quatre incohérences connues, état de session.
- `:app:assembleDebug` et `:app:lintDebug` verts en CI.
- Brief, spec et ADR 0001 marqués brouillon, chaque point ouvert marqué [à trancher].
