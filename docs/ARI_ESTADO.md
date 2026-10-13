# Ari — Archivo maestro de continuidad

## Propósito
Ari es un asistente Android local que utiliza Gemma 4 E4B-it mediante LiteRT-LM. El objetivo es mantener una aplicación funcional, privada y ampliable sin depender de APIs de pago.

## Entorno confirmado por el usuario
- Repositorio previsto: javierhedzd-ops/ari-assistant
- Rama de trabajo reportada: main
- Package Android: com.ari.assistant
- Lenguaje: Kotlin
- compileSdk: 36
- minSdk: 26
- Android Gradle Plugin: 8.13.2
- Kotlin plugin: 2.4.21
- Dependencia: com.google.ai.edge.litertlm:litertlm-android:latest.release (resolvió 0.18.0)
- Dispositivo: POCO X8 Pro Max, 12 GB RAM
- Modelo: Gemma 4 E4B-it, archivo gemma-4-E4B-it.litertlm, aproximadamente 3.41 GB
- El usuario indicó que copió el modelo mediante el selector de documentos a almacenamiento privado de Ari y confirmó que Gemma respondió correctamente.

## Arquitectura actual reportada
- MainActivity.kt contiene interfaz oscura de chat.
- Selección del modelo mediante Android document picker.
- Copia del modelo a filesDir/gemma-model.litertlm.
- Inicialización con Engine, EngineConfig, Backend.CPU() y Conversation.
- Respuestas mediante sendMessageAsync y MessageCallback.
- La interfaz actual no persiste el historial y no se ha confirmado la carga automática del modelo al iniciar.
- El código completo de MainActivity.kt se compartió en una conversación previa, pero debe recuperarse del repositorio o solicitarse al usuario antes de modificarlo.

## Objetivo inmediato: memoria local persistente
1. Guardar mensajes en SQLite.
2. Restaurar el historial al iniciar la aplicación.
3. Guardar recuerdos explícitos y recuperarlos en futuras conversaciones.
4. Implementar comandos /recuerda <texto>, /memoria y /olvida <texto>.
5. Añadir un botón de interfaz para consultar, agregar y borrar recuerdos.
6. Cargar automáticamente filesDir/gemma-model.litertlm si existe y gestionar errores sin bloquear la interfaz.

## Reglas de seguridad
- No reemplazar código funcional sin revisar primero el archivo actual.
- No afirmar que un cambio compila hasta que GitHub Actions termine correctamente.
- No subir archivos del modelo .litertlm ni .tflite al repositorio.
- No añadir claves, tokens ni credenciales al archivo de continuidad.
- Conservar el funcionamiento de Gemma y el selector de modelo.
- Hacer cambios pequeños y verificables.

## Flujo de trabajo entre ChatGPT y Claude
- GitHub es la referencia común para el código y este archivo.
- Antes de editar, leer el estado actual del repositorio y de la rama.
- Cada asistente debe actualizar este documento cuando cambie el estado del proyecto.
- Registrar claramente qué está confirmado, qué está pendiente, qué se modificó y qué pruebas pasaron.
- No asumir que ChatGPT y Claude comparten memoria automáticamente.

## Estado al crear este documento
- Gemma funcionó dentro de Ari según la prueba del usuario.
- La memoria persistente aún no está implementada ni verificada.
- La integración GitHub de ChatGPT devolvió 404 al intentar acceder al repositorio, aunque el usuario confirmó que puede verlo desde Android.
- Este documento es un punto de partida; debe verificarse contra el contenido real del repositorio.
- Próximo paso: confirmar el archivo en GitHub y después revisar el MainActivity.kt actual antes de implementar SQLite.

## Actualización: memoria local persistente (Claude, rama ccr-e45f2eee-wkjpdl)
El usuario confirmó que el APK anterior se instala y que Gemma responde.

Cambios implementados:
- Nuevo `AriDatabase.kt` (SQLiteOpenHelper, base `ari.db`) con tablas `messages` y `memories`.
- Cada mensaje del usuario y cada respuesta de Ari se guardan en SQLite.
- Al abrir la app se muestran los últimos 100 mensajes guardados.
- Si existe `filesDir/gemma-model.litertlm`, el modelo se carga automáticamente en segundo plano; si falla, se muestra el error y se puede volver a seleccionar.
- Comandos locales (funcionan aunque el modelo no haya cargado): `/recuerda <texto>`, `/memoria`, `/olvida <texto>` (borra los recuerdos que contienen el texto, sin distinguir mayúsculas).
- Botón "Memoria": lista los recuerdos, permite agregar uno nuevo y borrar uno tocándolo (con confirmación).
- Contexto para Gemma: en el primer mensaje tras cargar el modelo se envían los recuerdos y los últimos 8 mensajes guardados (máx. 300 caracteres cada uno); después, los recuerdos solo se reenvían cuando cambian.

Verificado:
- Compilación correcta en GitHub Actions (run 38349873628, workflow_dispatch sobre esta rama).

Pendiente de verificar:
- Prueba en el dispositivo: persistencia tras cerrar la app, carga automática del modelo, comandos y botón Memoria.
