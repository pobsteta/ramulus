package io.github.pobsteta.ramulus.core.deperis

/** Type d'essence : décide du second critère (fiches DEPERIS 2025, page 1). */
enum class TypeEssence(val secondCritere: SecondCritere) {
    FEUILLU(SecondCritere.MR),
    RESINEUX(SecondCritere.MA),
}

/** Second critère de l'abaque. */
enum class SecondCritere(val libelle: String) {
    MR("Manque de ramification"),
    MA("Manque d'aiguilles"),
}

/** Qui a donné la note. Une proposition de l'IA n'est jamais une notation tant qu'un opérateur ne l'a pas validée. */
enum class Origine {
    /** L'opérateur a noté sans aide. */
    OPERATEUR,

    /** L'opérateur a validé, tel quel ou corrigé, ce que l'IA proposait. */
    OPERATEUR_AIDE_IA,
}

/** Proposition de l'IA, gardée à côté de la note retenue pour mesurer l'écart sur le terrain. */
data class PropositionIa(
    val mb: Note,
    val second: Note,
    /** Confiance du modèle entre 0 et 1. */
    val confiance: Double,
    /** Identifiant et version du modèle qui a proposé. */
    val modele: String,
) {
    init {
        require(confiance in 0.0..1.0) { "La confiance va de 0 à 1 (reçu : $confiance)" }
    }

    val classe: Classe get() = Abaque.classe(mb, second)
}

/**
 * Notation DEPERIS d'un arbre, telle qu'enregistrée.
 *
 * La classe n'est jamais saisie : elle se déduit toujours de MB et du second critère par l'[Abaque].
 */
data class Notation(
    val uuid: String,
    val essence: String,
    val type: TypeEssence,
    val mb: Note,
    val second: Note,
    val origine: Origine,
    val proposition: PropositionIa? = null,
    /** Noms des fichiers photo de cette notation, relatifs au dossier de la tournée. */
    val photos: List<String> = emptyList(),
) {
    init {
        require(uuid.isNotBlank()) { "Une notation a toujours un UUID" }
        require(essence.isNotBlank()) { "Une notation a toujours une essence" }
        require((origine == Origine.OPERATEUR_AIDE_IA) == (proposition != null)) {
            "Une notation aidée par l'IA garde sa proposition, et seulement celle-là"
        }
    }

    val classe: Classe get() = Abaque.classe(mb, second)

    val estMort: Boolean get() = Abaque.estMort(mb, second)

    /** Écart de classe entre la note retenue et la proposition de l'IA (négatif : l'IA était plus clémente). */
    val ecartIa: Int? get() = proposition?.let { it.classe.ordinal - classe.ordinal }
}
