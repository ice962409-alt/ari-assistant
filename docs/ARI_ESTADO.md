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

## Firma de depuración fija
- Al instalar el APK nuevo encima del anterior, Android mostró "No se instaló la app debido a un conflicto con un paquete": cada ejecución de GitHub Actions generaba una clave de depuración distinta.
- Se añadió `app/debug.keystore` (clave de depuración pública, contraseña estándar `android`) y `signingConfigs.debug` en `app/build.gradle.kts`. Desde este APK, las siguientes actualizaciones se instalan encima sin desinstalar.
- Hace falta desinstalar Ari una sola vez para pasar a la firma fija. Esto borra el modelo copiado y hay que volver a seleccionarlo.
- Esta clave es solo para depuración; una versión de publicación necesitará su propia clave, guardada como secreto y no en el repositorio.

## Estado tras el PR #3
- PR #3 (memoria persistente y firma de depuración fija) fusionado en `main`; el usuario confirmó que la app instalada funciona.
- PR #4 (modo voz, documentación y CLAUDE.md) fusionado en `main`; el usuario confirmó que el modo voz funciona. Compilación de main correcta (run 38354067159).

## Modo voz (en desarrollo)
- Nuevo `VoiceController.kt`: `SpeechRecognizer` (idioma del sistema, resultados parciales, prefiere sin conexión) y `TextToSpeech` (idioma del sistema, o español si no está disponible).
- Botón "🎤 Voz" junto a "Enviar": activa un ciclo escuchar → responder con Gemma → leer la respuesta en voz alta → volver a escuchar. "⏹ Detener" lo apaga.
- Los comandos (`/recuerda`, etc.) también se pueden decir en voz alta y su respuesta se lee.
- Se pausa tras 3 intentos seguidos sin escuchar nada, ante un error del micrófono o al salir de la app.
- La respuesta se limpia de Markdown antes de leerla y se divide en frases si es muy larga.
- Permiso `RECORD_AUDIO` (se pide al tocar Voz por primera vez) y `<queries>` para ver los servicios de voz en Android 11+.
- Compila correctamente en Actions (run 38352930025). Pendiente: prueba en el dispositivo.

## Documentación en Google Drive
- El usuario pidió una copia legible de la documentación en su Drive, actualizada siempre que se termine un cambio.
- Fuente: `docs/ARI_DOCUMENTACION.md`. Carpeta de Drive: "Ari - Documentación de Claude". El procedimiento está en `CLAUDE.md`.

## Ari como CEO de agentes (en desarrollo)
Objetivo del usuario: que Ari coordine bots y agentes en el futuro. Esta es la primera fase, solo local.

- Paquete `com.ari.assistant.agents`:
  - `Agent`: contrato común (id, nombre, descripción, ejemplos, `match` y `execute`). Pensado para que también lo implementen bots externos.
  - `AriCeo`: recibe cada mensaje antes que Gemma, elige el primer agente que sabe atenderlo, ejecuta la tarea y la registra en la bitácora. Si ningún agente lo atiende, responde Gemma.
  - `MemoryAgent`: frases normales sin "/": "recuerda que…", "anota que…", "guarda que…", "olvida…", "qué recuerdas de mí". "Recuérdame…" no entra, porque suele pedir un aviso.
  - `ClockAgent`: hora y fecha del teléfono, que Gemma no conoce.
- Base de datos versión 2: tabla `agent_tasks` (bitácora de delegaciones), con migración desde la versión 1.
- Comandos nuevos: `/agentes` (equipo y últimas tareas) y `/delega <agente> <tarea>`.
- Funciona con texto y con voz, y aunque el modelo no haya cargado.
- Las expresiones de enrutamiento se probaron con jshell (misma sintaxis de regex que Kotlin/Java).
- Compila correctamente en Actions (run 38354689116). Pendiente: prueba en el dispositivo y PR.

Siguientes fases propuestas:
1. Enrutamiento con Gemma cuando las reglas no basten (comprobar antes si LiteRT-LM permite una segunda conversación por motor).
2. Agentes con acciones en el teléfono (recordatorios, alarmas), pidiendo confirmación al usuario.
3. Bots externos (HTTP), con permisos explícitos por agente y sin credenciales en el repositorio.

## Primer subordinado: modelo de IA en la laptop (en desarrollo)
- Petición del usuario: que Ari, como CEO, consiga un subordinado lo antes posible y con autonomía. El subordinado será otro modelo de IA corriendo en una laptop.
- `LaptopAgent`: habla con servidores compatibles con la API de OpenAI (`/v1/models` y `/v1/chat/completions`), como Ollama (puerto 11434) o LM Studio (puerto 1234).
- Autonomía: al abrir Ari, si no tiene laptop guardada, recorre la subred /24 de la Wi-Fi buscando esos puertos y se conecta al primer servidor que responda. La dirección y el modelo se guardan en SharedPreferences (`ari_agentes`).
- Uso: "laptop, <pregunta>", "pregúntale a la laptop …", `/delega laptop <pregunta>`, "busca la laptop", `/laptop` (estado), `/laptop http://IP:PUERTO`, "desconecta la laptop".
- A la laptop se le pasan los recuerdos del usuario como contexto.
- `AriCeo` ahora ejecuta los agentes en un hilo en segundo plano y entrega el resultado en el hilo principal.
- Permiso `INTERNET` añadido.
- **Pendiente de decisión del usuario:** Android bloquea HTTP sin cifrar. Hace falta `usesCleartextTraffic` (o HTTPS en la laptop) para que funcione. Propuesta: permitirlo y limitar el agente a direcciones de red privada. Hasta entonces, las conexiones a la laptop fallarán en el teléfono.
