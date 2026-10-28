#!/usr/bin/env python3
"""Écrit les fichiers d'exemple du format .ramsync v1 (docs/formats/ramsync-v1.md), mission 005.

Bibliothèque standard seule : un .ramsync se lit et s'écrit avec gzip et json (test du successeur). Le gzip est
reproductible (date gzip nulle) : relancer ce script redonne les mêmes octets.

    python3 fixtures/ramsync/generer.py
"""

import gzip
import hashlib
import io
import json
import os

ICI = os.path.dirname(os.path.abspath(__file__))

CODE = "EXEMPLE-2027-01"
APPAREIL = "a3f9c2d1"
# 2027-07-06T07:00:00Z, 09:00 à Paris.
T0 = 1814857200000


def ligne(uuid, objet, type_, valeur, remplace, seq, dt_s, operateur="operateur-A", appareil=APPAREIL,
          gnss=False, demarrage=3):
    """Une saisie : les treize champs, dans l'ordre du format. `valeur` : texte JSON, ou None."""
    saisi = T0 + dt_s * 1000
    return {
        "uuid": uuid,
        "objet_uuid": objet,
        "type": type_,
        "valeur": valeur,
        "remplace_uuid": remplace,
        "operateur": operateur,
        "appareil": appareil,
        "sequence": seq,
        "saisi_le_utc_ms": saisi,
        "heure_gnss_ms": saisi - 800 if gnss else None,
        "monotone_ms": 5_000_000 + dt_s * 1000,
        "demarrage": demarrage,
        "age_fix_ms": 800 if gnss else None,
    }


def u7(ms, n):
    """UUID v7 d'exemple (RFC 9562) : instant `ms` en tête, `n` en fin, variante 10."""
    h = f"{ms:012x}"
    return f"{h[:8]}-{h[8:12]}-7000-8000-{n:012x}"


VEILLE = T0 - 86_400_000
CENTRE, ARBRE, ARBRE_B = u7(VEILLE, 0xA1), u7(VEILLE, 0xB1), u7(VEILLE, 0xC1)  # objets d'exemple
SAUT = 3_600_000  # l'heure du téléphone avance d'une heure ; l'horloge monotone, non

# Les valeurs sont des textes JSON d'exemple : leur contenu par type est fixé par les missions 006 et 008, pas ici.
V = "{\"exemple\":1}"
_S = [
    ligne(u7(T0, 1), CENTRE, "centre", V, None, 1, 0, gnss=True),
    ligne(u7(T0 + 60_000, 2), ARBRE, "notation", "{\"exemple\":\"Mélèze \\\"n° 12b\\\"\"}", None, 2, 60),
    ligne(u7(T0 + 120_000, 3), ARBRE, "photo", V, None, 3, 120),
    ligne(u7(T0 + 180_000, 4), ARBRE, "notation", V, u7(T0 + 60_000, 2), 4, 180),
    ligne(u7(T0 + 240_000, 5), ARBRE, "annulation", None, u7(T0 + 120_000, 3), 5, 240),
    # Saisie restaurée d'un autre appareil : elle garde son appareil et sa séquence d'origine.
    ligne(u7(T0 + 270_000, 8), ARBRE_B, "notation", V, None, 1, 270, operateur="operateur-B", appareil="0b5e11aa",
          demarrage=12),
]
# L'heure du téléphone a sauté d'une heure : la ligne saut_horloge précède la saisie qui l'a révélé ; l'heure brute
# est gardée telle quelle.
for _seq, _type, _valeur in ((6, "saut_horloge", None), (7, "placette_notee", V)):
    _l = ligne(u7(T0 + 300_000 + SAUT, _seq), CENTRE, _type, _valeur, None, _seq, 300)
    _l["saisi_le_utc_ms"] += SAUT
    if _type == "saut_horloge":
        _l["objet_uuid"] = _l["uuid"]
    _S.append(_l)

# Ordre total : (saisi_le_utc_ms, appareil, sequence, uuid).
SAISIES = sorted(_S, key=lambda l: (l["saisi_le_utc_ms"], l["appareil"], l["sequence"], l["uuid"]))


def texte(o):
    """Une ligne JSON compacte, UTF-8 sans échappement des accents (comme Gson)."""
    return json.dumps(o, ensure_ascii=False, separators=(",", ":"))


def contenu(saisies, version=1, code=CODE, pied=True, entete_en_plus=None):
    entete = {
        "format": "ramsync",
        "version": version,
        "code_tournee": code,
        "version_nemeton": "exemple",
        "version_ramulus": "exemple",
        "appareil": APPAREIL,
        "cree_le_utc_ms": T0 + 3_600_000,
        "nb_lignes": len(saisies),
    }
    if entete_en_plus:
        entete.update(entete_en_plus)
    lignes = [texte(entete)] + [s if isinstance(s, str) else texte(s) for s in saisies]
    corps = "".join(l + "\n" for l in lignes).encode("utf-8")
    if pied:
        corps += (texte({"sha256": hashlib.sha256(corps).hexdigest()}) + "\n").encode("utf-8")
    return corps


def gz(octets):
    tampon = io.BytesIO()
    with gzip.GzipFile(fileobj=tampon, mode="wb", mtime=0, filename="") as f:
        f.write(octets)
    return tampon.getvalue()


def ecrire(nom, octets):
    with open(os.path.join(ICI, nom), "wb") as f:
        f.write(octets)


def main():
    sain = contenu(SAISIES)
    ecrire("exemple-v1.jsonl", sain)
    ecrire("exemple-v1.ramsync", gz(sain))

    # Gzip coupé : les 60 derniers octets manquent.
    complet = gz(sain)
    ecrire("exemple-v1-tronque.ramsync", complet[:-60])

    # Un octet changé dans une saisie (operateur-A devient operateur-Z), pied d'origine gardé.
    ecrire("exemple-v1-empreinte-fausse.ramsync",
           gz(sain.replace(b"operateur-A", b"operateur-Z", 1)))

    # Une ligne qui n'est pas du JSON, une ligne au type hors liste : refusées, les autres lues.
    abime = list(SAISIES)
    abime[2] = "{\"uuid\":\"019ad2f0-3a80-7000-8000-000000000003\",\"objet_uuid\":"
    abime[6] = dict(SAISIES[6], type="classe")
    ecrire("exemple-v1-ligne-illisible.ramsync", gz(contenu(abime)))

    # Une ligne d'une autre tournée.
    autre = list(SAISIES)
    autre[1] = dict(SAISIES[1], code_tournee="EXEMPLE-2027-01-CN")
    ecrire("exemple-v1-autre-tournee.ramsync", gz(contenu(autre)))

    # Même uuid deux fois : une fois à l'identique (ignorée), une fois au contenu différent (les deux refusées).
    doubles = list(SAISIES) + [SAISIES[0], dict(SAISIES[1], operateur="operateur-B")]
    ecrire("exemple-v1-uuid-double.ramsync", gz(contenu(doubles)))

    # Version 2 : refusée par un lecteur v1 (version_future), lue par un lecteur v2 ne serait pas de la v1.
    ecrire("exemple-v2.ramsync", gz(contenu(SAISIES, version=2)))

    # Fichier de 0 octet.
    ecrire("exemple-vide.ramsync", b"")


if __name__ == "__main__":
    main()
