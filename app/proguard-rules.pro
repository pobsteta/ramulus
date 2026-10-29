# Règles R8 propres à Ramulus, reprises de Surculus app/proguard-rules.pro (commit 3c01f4b, ADR 0011).

# sqlite-jdbc. Le code natif (libsqlitejdbc.so, JNI) cherche par leur nom des classes, des champs et des
# méthodes de org.sqlite : NativeDB et ses rappels (Function, Collation, ProgressHandler, BusyHandler…), les
# exceptions, les structures de métadonnées. Si R8 les renomme ou les retire, l'appel natif plante à l'exécution,
# sans erreur à la compilation. L'essai de fumée (écrire puis relire une tournée sur émulateur, build « fumee »)
# existe pour attraper ce cas.
-keep class org.sqlite.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
# Classes optionnelles absentes sur Android (journalisation, compilation native GraalVM).
-dontwarn org.slf4j.**
-dontwarn org.graalvm.**
# java.sql.JDBCType n'existe pas dans l'API Android : seul JDBC3PreparedStatement.getParameterTypeName() la cite,
# et Ramulus ne l'appelle jamais (constat de Surculus, rapport R8 missing_rules.txt, 2026-10-17).
-dontwarn java.sql.JDBCType
