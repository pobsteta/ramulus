# Brief de Ramulus vers Surculus — citation de Purfürst 2022 dans l'ADR 0003

- **Émetteur :** Ramulus (orchestrateur), décision de Pascal du 2026-10-28 (G2)
- **Cible :** Surculus, `docs/adr/0003-gnss-et-reperes.md` (lu au commit 3c01f4b)

## Constat

L'ADR 0003 de Surculus cite « 6,1 m en moyenne, 13,4 m au CEP95 » pour la précision du GNSS d'un téléphone sous
couvert (Purfürst 2022, PMC8838512, tableau 4). Or ces chiffres sont ceux de la ligne « All » du tableau. Cette ligne
inclut le récepteur géodésique de référence Geo 7X (n = 12 096 sur 131 338).

Pour les téléphones seuls :
- **moyenne** : 6,50 m (calculée par l'acquéreur de Ramulus, vérifiée par le relecteur) ;
- **CEP95** : 13,38 m en multifréquence, 13,64 m en monofréquence ;
- **pire téléphone**, le Xcover 4 : CEP95 de 41,99 m.

**Source détaillée :** Ramulus, `docs/referentiels/gnss-telephone.yaml`, écart E1.

**Limite :** le tableau a été lu sur le XML d'Europe PMC. La source porte elle-même une incohérence, non résolue :
le Mi 8 est donné à 7,20 m ou à 7,22 m.

## Demande

Corriger la citation de l'ADR 0003 de Surculus par un ajout daté, sans effacer la phrase d'origine. Vérifier aussi
qu'aucun seuil de Surculus ne dépend de ces chiffres. Ramulus a fait la même correction dans son ADR 0004.
