# Ari en versión de entrega (release): el código se encoge y se revuelve (R8) para que
# sea difícil de leer y de modificar. Aquí van las piezas que NO se pueden renombrar.

# Motores nativos: su código en C++ busca estas clases y métodos por nombre.
-keep class com.google.ai.edge.litertlm.** { *; }
-keep class com.k2fsa.sherpa.onnx.** { *; }
-dontwarn com.google.ai.edge.litertlm.**
-dontwarn com.k2fsa.sherpa.onnx.**

# Los nombres de los enum se guardan en las preferencias y en el respaldo (tono, puestos…).
-keep enum com.ari.assistant.** { *; }

# Para poder leer los informes de fallo: se conservan archivo y línea (el mapa de nombres
# se guarda en GitHub Actions como "ari-mapa").
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute Ari
