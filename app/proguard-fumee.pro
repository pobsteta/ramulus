# Règles R8 du seul type de build `fumee` (essai de fumée de la release, mission 013), reprises de Surculus
# app/proguard-fumee.pro (commit 3c01f4b). Elles n'atteignent jamais l'APK release distribué.

# :core : l'application n'appelle pas encore EchangeDeperis (écrans en mission 006), donc R8 retire ses classes, et
# l'essai n'éprouverait plus les natifs de sqlite-jdbc. On garde les points d'entrée publics de l'échange.
# À retirer quand l'application appelle l'échange.
-keep class io.github.pobsteta.ramulus.core.echange.** { public *; }

# Le runner androidx.test de l'APK de test charge androidx.tracing et la bibliothèque standard de Kotlin depuis l'APK
# de l'application ; R8 les retirait, et le runner plantait avant le premier test.
-keep class androidx.tracing.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }

# Gson : le test de fumée lit les résultats de l'échange avec JsonParser, que le code de l'application n'appelle pas.
-keep class com.google.gson.** { *; }
