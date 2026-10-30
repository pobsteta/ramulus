# Règles R8 de l'APK de test de la release (essai de fumée), reprises de Surculus (commit 3c01f4b).
# androidx.test cite des annotations errorprone qui n'existent pas à l'exécution ; elles sont sans effet.
-dontwarn com.google.errorprone.annotations.**
