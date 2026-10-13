# Ari — Archivo maestro de continuidad

## Propósito
Ari es un asistente Android local que utiliza Gemma 4 E4B-it mediante LiteRT-LM. El objetivo es mantener una aplicación funcional, privada y ampliable sin depender de APIs de pago.

## Entorno confirmado por el usuario
- Repositorio previsto: javierhedzd-ops/ari-assistant
- Rama principal: main
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

## Actualización: memoria local en SQLite (Claude)

### Qué se modificó
- Nuevo `AriDatabase.kt` (SQLiteOpenHelper, base `ari.db` en almacenamiento privado):
  - Tabla `messages(id, role, text, created_at)`: historial del chat (`role` = `user` o `model`).
  - Tabla `memories(id, text UNIQUE, created_at)`: recuerdos explícitos.
- `MainActivity.kt`:
  - Restaura los últimos 200 mensajes en pantalla al abrir la app.
  - Guarda cada mensaje del usuario al enviarlo y la respuesta de Ari al terminar (`onDone`).
  - Carga automáticamente `filesDir/gemma-model.litertlm` al iniciar si existe, en segundo plano; si falla, muestra el error y deja usar el selector.
  - La conversación se crea con `ConversationConfig`: `systemInstruction` = instrucciones de Ari + lista de recuerdos; `initialMessages` = últimos 20 mensajes (máx. 4000 caracteres en total, 1200 por mensaje).
  - Comandos locales (no pasan por el modelo y funcionan sin modelo cargado): `/recuerda <texto>`, `/memoria` (o `/memorias`), `/olvida <texto>` (borra los recuerdos que contienen el texto).
  - Botón "Recuerdos": lista los recuerdos, permite agregar uno nuevo y borrar uno tocándolo (con confirmación).
  - Al cambiar los recuerdos se recrea la conversación para que Gemma los vea; si está respondiendo, se hace al terminar.
  - Al elegir otro modelo se libera el anterior antes de copiar el archivo. No se puede cambiar de modelo mientras Ari responde.
- `AndroidManifest.xml`: `configChanges` en la actividad para que girar la pantalla no reinicie la actividad ni recargue el modelo.
- `.github/workflows/android.yml`: compila en push a cualquier rama y en pull requests a main.
- Eliminado `build-apk.yml` de la raíz (YAML inválido y fuera de `.github/workflows`, nunca se ejecutaba).

### Estado de verificación
- Compilación confirmada en GitHub Actions: run #10 (commit e29b123) terminó con éxito y generó el artefacto `ari-debug-apk`. El entorno de Claude no tiene Android SDK, así que GitHub Actions es la única verificación de compilación.
- Pendiente de prueba en el dispositivo: restauración del historial, carga automática, comandos y ventana de recuerdos.

### Notas
- `core/assistant.py` y `memory/memory.py` son un prototipo en Python que la app Android no usa; las instrucciones del sistema de Ari se tomaron de ahí.
- La búsqueda de `/olvida` no distingue mayúsculas solo en letras sin acento (comportamiento de `LIKE` en SQLite).
- Aún no hay opción para borrar el historial del chat.
