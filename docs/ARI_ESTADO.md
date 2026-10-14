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
- Al abrir la app se muestran los últimos 30 mensajes guardados (antes 100); "Ver mensajes anteriores" carga 30 más con `messagesBefore`.
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
- Decisión del usuario: permitir HTTP (`usesCleartextTraffic`) con estas protecciones:
  - `NetworkGuard`: el subordinado solo se usa en el hotspot del teléfono (siempre permitido) o en redes Wi-Fi aprobadas por el usuario (diálogo "¿Usar el subordinado en esta red?"). Para leer el nombre de la Wi-Fi, Android exige el permiso de ubicación; se pide solo al usar la laptop.
  - La laptop se guarda por red (`laptop_url|<red>`), para no mandar datos a otro equipo con la misma IP en otra red.
  - `/laptop http://...` solo acepta IPv4 privadas (10/8, 172.16/12, 192.168/16).
  - La búsqueda automática al abrir Ari solo corre en redes ya aprobadas.
- Contexto de redes del usuario: casa (privada, con contraseña) y trabajo (restaurante; la Wi-Fi solo la usan empleados). Se recomendó el hotspot del teléfono para redes que no son suyas.

## Personalidad y equipo de modelos (en desarrollo)
- Pedido del usuario (lista de 8 puntos): autonomía; Ari trabaja para el usuario; personalidad; Ari da puestos a los modelos según su capacidad y los intereses del usuario; conectar con modelos locales en PC o celulares; interfaz como Claude; optimización y estabilidad; conexión cuanto antes. La laptop tendrá Linux.
- Personalidad: Ari es mujer, cercana, con buen humor, seria cuando hace falta, directa y detallada cuando el usuario no entiende. Constante `PERSONALITY` en MainActivity; va en el contexto privado del primer mensaje tras cargar el modelo. Textos de la interfaz en femenino ("Ari está lista").
- La empresa: todavía sin nombre; se dedica a la autonomía de modelos de IA locales, con funciones específicas a través de Ari.
- `LaptopAgent` se reemplazó por `TeamAgent` (id "equipo"): busca todos los servidores compatibles con OpenAI en la red (puertos 11434 Ollama, 1234 LM Studio, 8080 llama.cpp), contrata todos sus modelos de conversación (máx. 12) y los guarda por red (`team|<red>` en SharedPreferences).
- `Role`: puestos asignados por el nombre del modelo (coder → Desarrollo de software; 7B o más → Análisis y estrategia; menos de 4B → Respuestas rápidas; resto → Asistencia general; embeddings no se contratan). Probado con jshell.
- Autonomía: los mensajes con palabras de una especialidad (código, Python, Linux; analiza, estrategia, redacta…) se delegan solos al modelo con ese puesto, si existe en la red actual aprobada. Limitación conocida: si ese modelo no responde, no hay respaldo con Gemma todavía.
- Comandos: /equipo, /equipo buscar, /equipo http://IP:PUERTO, "equipo, …", "busca modelos", "despide al equipo". /laptop y "laptop, …" siguen funcionando como alias.
- El usuario preguntó si Claude puede hablar con Ari directamente: hoy no (Ari está en el teléfono y Claude en la nube). Idea anotada: canal de reportes vía Drive.

## Objetivo de la empresa: lanzar Ari oficialmente
- El usuario quiere lanzar Ari pronto; será el primer objetivo de la empresa. El nombre de la empresa se decidirá cuando Ari esté bien diseñada.
- Seguridad y nube privada (pendiente, a la espera de la laptop con Linux): repositorio privado (lo hace el usuario), la laptop como casa principal de Ari, túnel cifrado con Tailscale/WireGuard, respaldos 3-2-1 (laptop, teléfono, copia cifrada en Drive) y migración a otra PC copiando la memoria de Ari. Primer paso propuesto: exportar e importar la memoria de Ari en un archivo cifrado.
- Requisitos de lanzamiento a revisar: descarga guiada del modelo en el primer uso, personalización por usuario (hoy la personalidad habla de "tu creador"), firma de publicación, política de privacidad, justificación de permisos en Google Play (ubicación, micrófono) y licencia de Gemma.

### Estabilidad (primera ronda)
- `configChanges` en MainActivity: girar el teléfono, cambiar el tema o el tamaño de letra ya no recrea la Activity (eso cargaba un segundo Gemma y cerraba la app por falta de memoria).
- `releaseModel()`: cierra el motor anterior antes de cargar otro y al destruir la Activity.
- `initializeModel` atrapa `Throwable` (incluye OutOfMemoryError y errores nativos) y avisa si el teléfono tiene menos de 6 GB de RAM.
- La copia del modelo va a un archivo temporal y se renombra al terminar; no se puede cambiar de modelo mientras Ari responde.
- Los callbacks de Gemma y de los agentes no tocan la interfaz ni la base de datos si la Activity ya se destruyó.
- `sendMessageAsync` protegido con try/catch.
- Respaldo con Gemma: si Ari delegó por su cuenta (`AgentTask.automatic`) y el equipo falla, contesta ella con Gemma.

### Revisión de seguridad (security-review)
- Hallazgo: `TeamAgent.discover()` contrataba a cualquier servidor de la red sin confirmación y `systemPrompt` le enviaba todos los recuerdos del usuario; cualquier equipo de una red aprobada podía hacerse pasar por subordinado y recibirlos.
- Corrección: los modelos encontrados quedan como candidatos (`candidates|<red>`) y `MainActivity.offerCandidates()` pregunta al usuario a quién contratar; `/equipo http://...` (dirección escrita por el usuario) contrata directo. Los subordinados ya no reciben recuerdos. `instanceFollowRedirects = false` en las peticiones.

### Revisión de código (code-review)
Corregido:
- Si falla la copia de un modelo nuevo, el anterior sigue usable (`modelReady` se restaura).
- Permiso de ubicación: se exige la precisa; con la aproximada ya no se repite la solicitud en bucle.
- "olvida eso", "olvida lo que te dije" y "olvida todo" ya no borran recuerdos (van a Gemma); el borrado busca palabras completas ("café" no borra "cafetería").
- Las respuestas de los equipos de la red se leen con un tope de 2 MB, y los errores se atrapan como `Throwable`.
- `AriCeo.run` atrapa `Throwable` y la bitácora no puede impedir que llegue el resultado.
- `NetworkGuard` tolera que la lista de interfaces sea null o falle.
- Las redes Wi-Fi sin contraseña no se pueden aprobar (en Android 12+, donde se puede saber).
- El tope de 12 modelos se aplica después de quitar a los conocidos.
- `/equipo` consulta cada servidor una sola vez.
- El diálogo de aprobación ya no dice que se envían recuerdos.
- Las palabras clave de cada puesto se compilan una sola vez.
Limitación conocida: la aprobación de una red se guarda por nombre (SSID). En una red con contraseña, otra red con el mismo nombre necesitaría esa contraseña para que el teléfono se conecte solo; la solución completa es el túnel cifrado (Tailscale) previsto en la nube privada.

## Widget de Ari (pantalla de inicio)
- `AriWidget` (AppWidgetProvider) con `res/layout/widget_ari.xml`: colores de Ari, un `ViewFlipper` que rota 4 mensajes cada 4.5 s con animación (deslizar y aparecer), y dos botones: "Hablar con Ari" (abre la app) y "Pendientes" (abre el tablero en el navegador).
- Muestra solo datos del teléfono: si el modelo está cargado, cuántos recuerdos hay y cuántos modelos tiene el equipo. Por privacidad no muestra texto de conversaciones ni recuerdos. No se conecta a internet.
- Se actualiza cada 30 minutos (mínimo de Android) y cada vez que el usuario sale de la app (`onPause`).
- Aviso de "sesión cerrada": Claude no puede detectar el cierre de su sesión. En su lugar, al terminar o detenerse un bloque de trabajo, manda una notificación y deja `estado/actual` del tablero en "En pausa: escribe 'sigue'".

## App Ari Estado (widget del progreso de Claude)
- Módulo `estado/` (applicationId `com.ari.estado`), segunda app del proyecto; GitHub Actions sube su APK como `ari-estado-apk`. Firmada con la misma clave de depuración.
- Lee `estado.json` de `javierhedzd-ops/Ari-estado` (privado) con la API de GitHub por HTTPS y una llave fine-grained de solo lectura limitada a ese repositorio. La llave se guarda cifrada con AES-GCM y una clave del Android Keystore (`TokenVault`).
- Widget "Claude trabajando": estado (Trabajando, En pausa, Sin noticias), tarea, barra de progreso, barra fina animada mientras trabaja, "Paso X de Y · N% · hace N min · N pendientes", y botones Actualizar y Tablero.
- `RefreshJob` (JobScheduler, cada 15 minutos y al tocar Actualizar) redibuja el widget y avisa con una notificación cuando Claude se detiene o lleva más de 45 minutos sin actualizar estando activo.
- Ari (la app principal) sigue sin conectarse a internet.
- Nota: `ari-assistant` ya era privado; la comprobación anterior con curl pasaba por el proxy autenticado de la sesión. Para saber la visibilidad hay que leer el campo `private` de la API.
- Corrección 0.1.1: Ari Estado se cerraba al programar la revisión, porque Android 14+ exige `ACCESS_NETWORK_STATE` para `setRequiredNetworkType`. Añadido el permiso; programar va protegido con try/catch; `AriEstadoApp` guarda el último cierre inesperado y la pantalla de la app lo muestra.
- `estado.json` tiene `sesion` (enlace a la sesión de Claude Code). La notificación y el aviso naranja del widget lo abren. El usuario había escrito "sigue" en un chat normal de Claude por error.

## Interfaz estilo Claude, color hielo (versión 0.2.0)
- Decisión del usuario: interfaz parecida a la de Claude, pero con color hielo en lugar del naranja.
- `ui/AriTheme.kt` (colores: fondo #16181C, superficie #1F2227, burbuja #2A2F36, hielo #9ED8EE, hielo profundo #5FB8DA), `ui/Markdown.kt` (títulos, listas, negritas, cursivas y código) y `ui/ChatView.kt` (burbujas del usuario a la derecha, Ari sin burbuja con su etiqueta, "Pensando…" animado, respuesta en vivo, pantalla de bienvenida, notas).
- `MainActivity`: barra superior (punto hielo, "Ari", estado, botones Memoria y Modelo), campo redondeado con micrófono y botón de enviar circular. Tema oscuro (los diálogos también), barras del sistema oscuras y márgenes para el borde a borde de Android 15 y el teclado.
- Iconos adaptativos: Ari (anillo hielo, punto central y un "satélite") y Ari Estado (anillo convertido en barra de progreso). Los widgets pasaron de verde a hielo.

## Respaldo cifrado de la memoria (versión 0.3.0)

- `AriBackup.kt`: archivo `.ari` = "ARI1" + sal 16 B + IV 12 B + JSON cifrado con AES-256-GCM (AAD "ARI1"); llave con PBKDF2WithHmacSHA256, 210 000 iteraciones. Contraseña mínima de 8 caracteres.
- Contenido: tablas memories, messages y agent_tasks (`AriDatabase.exportData/importData`, en una transacción), claves `team|<red>` y `approved_networks` de las preferencias `ari_agentes`. Los candidatos no viajan.
- Al importar: se reemplaza todo; solo se aceptan equipos con IP privada; archivos de más de 64 MB se rechazan; contraseña mala o archivo alterado → no cambia nada. Se abre una conversación nueva de Gemma y se reenvían personalidad, recuerdos e historial.
- UI: Memoria → Respaldo; selector del sistema (ACTION_CREATE_DOCUMENT / ACTION_OPEN_DOCUMENT). La contraseña solo vive en memoria y se borra al usarla.
- Ari Estado 0.1.2: muestra prefijo y longitud de la llave guardada y quita espacios al pegarla (ayuda con el error 401).

## Estabilidad, segunda ronda

- Pruebas JVM en `app/src/test/` (JUnit 4.13.2): `AgentRulesTest` (normalización, MemoryAgent con `FakeStore`, ClockAgent, Role, NetworkGuard.isPrivateAddress) y `BackupCryptoTest`. El workflow corre `:app:testDebugUnitTest` antes de `assembleDebug`.
- `MemoryAgent` depende de la interfaz `agents/MemoryStore` (la implementa `AriDatabase`). El cifrado del respaldo está en `BackupCrypto.kt`.
- `MainActivity.saveMessage()` guarda en el executor `storage` (un hilo, en orden); `onDestroy` cierra la base de datos en ese hilo. `AriDatabase` usa WAL.
- `buildPrompt` ya no repite el mensaje actual en el historial; si `sendMessageAsync` falla, se restauran `historyPending`/`memoriesPending`.
- Personalidad (0.3.1): el usuario eligió "Franca, con groserías de vez en cuando"; se agregó a `PERSONALITY`. La descarga guiada del modelo espera la decisión de si Ari puede usar internet una vez (tablero, `descarga-modelo`).

## Una Ari para cada usuario (versión 0.4.0)

- `Profile.kt`: `Profile(userName, tone, creator)` y `Tone` (FRANK_SWEARING, FRANK, PROFESSIONAL). `personality()` arma el texto que antes era la constante `PERSONALITY`. Se guarda en las preferencias `ari_perfil`.
- Primer uso: si ya había mensajes, recuerdos o modelo (instalación del creador), `creator = true` y tono FRANK_SWEARING (su elección); si no, tono FRANK. Diálogo "¿Cómo te llamas?".
- Botón Ajustes: nombre, tono, respaldo. Cambiar el perfil pone `historyPending = true` para reenviar la personalidad.
- El respaldo incluye `profile` (nombre, tono, creador). Pruebas en `ProfileTest`.

## Revisión y recordatorio de respaldo (versión 0.4.1)

- `BackupReminder.kt`: aviso al abrir si hay recuerdos y no hay respaldo en 7 días (máximo un aviso al día). `markDone` al exportar. Pruebas en `BackupReminderTest`.
- Exportar: `openOutputStream(uri, "wt")` con respaldo a `"w"` (Drive no siempre acepta "wt").
- `ChatView.prependHistory` ajusta el scroll con un `OnLayoutChangeListener` de una vez (antes `post`, que podía correr antes de medir).
- Play Protect bloquea la instalación por "desarrollador desconocido" (APK de depuración por fuera de Play). Solución del usuario: Más detalles → Instalar de todas formas. Definitiva: publicar en Google Play.

## Preparación para Google Play (versión 0.4.2)

- Ajustes → "Acerca de Ari y licencias": versión, resumen de privacidad y licencias; botón con el texto oficial de Apache 2.0 (`res/raw/apache_2_0.txt`, sha256 cfc7749b…).
- Gemma 4 es Apache 2.0 (Google Open Source Blog, marzo de 2026); LiteRT-LM también.
- `docs/GOOGLE_PLAY.md` (pasos) y `docs/POLITICA_PRIVACIDAD.md` (borrador con datos por completar).
- Verificación de desarrolladores: vigente desde el 1 de octubre de 2026 en Brasil, Indonesia, Singapur y Tailandia; global en 2027. Existe cuenta de distribución limitada (hasta 20 dispositivos).

## Recordatorios (versión 0.5.0)

- `agents/ReminderParser.kt` (puro, probado en `ReminderParserTest`): prefijo recuérdame/recordame/avísame; "en N minutos/horas", "en media hora"; hoy/mañana/pasado mañana/el <día>; "a las H[:MM] [am/pm] [y media/cuarto]", "a la una"; "de la mañana/tarde/noche". Sin periodo: hoy, la siguiente hora futura entre H y H+12; otro día, 1-6 → tarde. Solo día → 9:00. Máximo un año.
- `agents/ReminderAgent.kt` (ReminderStore + ReminderAlarms, probado en `ReminderAgentTest`): crear, "mis recordatorios", "cancela el recordatorio de X" (sin acentos) y "cancela todos los recordatorios". Va primero en el CEO.
- Base de datos v3: tabla `reminders` (done 0/1/2), en el respaldo. `ReminderNotifier.kt`: `AlarmReminders` (exacta si `canScheduleExactAlarms`, si no `setAndAllowWhileIdle`), `ReminderReceiver`, `ReminderBootReceiver` (BOOT_COMPLETED, MY_PACKAGE_REPLACED). `rescheduleAll` al abrir, al reiniciar y tras recuperar un respaldo; los vencidos se avisan como atrasados.
- Permisos nuevos: POST_NOTIFICATIONS (se pide tras el primer recordatorio), SCHEDULE_EXACT_ALARM (se ofrece una vez), RECEIVE_BOOT_COMPLETED. Icono `drawable/ic_notification.xml`.
- Pendiente (tablero `mensajes-de-ari`): qué mensajes propios manda Ari (aviso de respaldo, saludo de la mañana, equipo caído).

## Ari usa el teléfono (versión 0.6.0)

- `agents/PhoneCommands.kt` (puro, `PhoneCommandsTest`): `PhoneAction` = OpenApp, Dial, WhatsApp, Alarm, Timer, WebSearch, Navigate, Camera, Settings, Missing. `pickApp` elige por nombre (igual, empieza con, contiene, todas las palabras); si hay varias, pregunta.
- `agents/PhoneAgent.kt` (`PhoneAgentTest`): va después del agente de recordatorios y antes de memoria. Nunca completa llamadas ni envíos: ACTION_DIAL, wa.me con texto o ACTION_SEND, ACTION_SET_ALARM/SET_TIMER, ACTION_WEB_SEARCH, google.navigation/geo, cámara, ajustes.
- `AndroidPhone.kt`: lanza en el hilo principal (el agente espera con un CountDownLatch); prueba intents alternativos; número a E.164 con el país de la red/SIM.
- Manifiesto: `<queries>` MAIN/LAUNCHER y paquetes de WhatsApp; permiso `SET_ALARM` (normal).
- Decisión pendiente (tablero `control-total`): Accesibilidad para tocar botones en otras apps. Riesgos: ve toda la pantalla, inyección de instrucciones, restricción de Google Play.

## Descarga del modelo y avisos (versión 0.7.0)

- Decisión del usuario (tablero `descarga-modelo`): Ari puede usar internet solo para descargar el modelo.
- `ModelDownload.kt`: DownloadManager, solo Wi-Fi (`setAllowedOverMetered(false)`), destino `getExternalFilesDir("modelos")/gemma-model.litertlm.descarga`; al terminar, si mide más de 1 GB, se renombra a `gemma-model.litertlm`. Revisa espacio (3.66 GB + 0.5 GB). URL: `https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm` (3 659 530 240 bytes según mirrors; no se valida el SHA-256 porque solo viene de terceros). Si el repositorio pide sesión (401/403), se explica cómo bajarlo a mano.
- `MainActivity`: Modelo → descargar / ver o cancelar / elegir archivo. `savedModelFile()` prefiere el archivo interno (elegido a mano) y si no, el descargado. Al elegir a mano se borra el descargado; al terminar la descarga se borra el interno. Avance cada 2 s mientras Ari está visible (`onResume`/`onStop`).
- Avisos (tablero `mensajes-de-ari`, "avísame cuando termine una tarea"): `TaskNotifier` en `ReminderNotifier.kt`, canal "avisos". Se usa al terminar Gemma, un agente (menos el del teléfono) o la carga del modelo, solo si Ari no está en primer plano.
- Control total con Accesibilidad: el usuario eligió "solo acciones seguras por ahora", pero quiere que se siga buscando una forma segura (tarea de investigación en BACKLOG).
- Google Play: más adelante; primero pruebas.

## Contactos (versión 0.8.0)

- `PhoneAction.DialContact(name)` ("llama a mamá", hasta 3 palabras) y `WhatsAppContact(name, text)` ("manda un WhatsApp a Juan que diga …"). `PhoneCommands.pickContact` usa la misma búsqueda que las apps (igual, empieza con, contiene, todas las palabras), sin acentos; un contacto con varios números aparece una vez (gana el principal: IS_SUPER_PRIMARY, IS_PRIMARY).
- `PhoneLauncher.contacts()` devuelve null sin permiso: `PhoneAgent.needsContactsPermission = true` y `MainActivity` pide READ_CONTACTS (REQUEST_CONTACTS = 108); al aceptarlo repite la orden con `sendMessage()`.

## Control de apps: investigación y acciones nuevas (versión 0.9.0)

- Investigación en `docs/CONTROL_APPS.md`: la política de Google Play prohíbe usar Accesibilidad para que una app planee y ejecute acciones de forma autónoma (salvo herramientas de accesibilidad; sí permite automatización con guion fijo escrito por una persona). AppFunctions (Android 16+) está en vista previa y por ahora solo para apps privilegiadas. Plan: etapa 1 (intents, hecha), etapa 2 (AppFunctions cuando abra), etapa 3 (rutinas del usuario con Accesibilidad, versión aparte, solo si el usuario lo decide; tablero `control-etapas`).
- Acciones nuevas en `PhoneCommands`: `Sms`/`SmsContact`, `Email`, `PlayMusic` (MEDIA_PLAY_FROM_SEARCH con paquete opcional), `YouTube`, `CalendarEvent` (ACTION_INSERT; fecha con `ReminderParser.parse(…, order = CALENDAR)`).
- WhatsApp y SMS comparten `message()`: sin número ni "que diga" devuelven null para que Gemma redacte. Correo igual sin dirección ni "que diga".

## Ari con cara y voz fluida (versión 0.10.0)

- `ui/AriOrbView.kt`: Canvas animado (moods IDLE, LISTENING con `level` de RMS, THINKING, SPEAKING, HAPPY); parpadeo aleatorio; se usa en el modo live y en la bienvenida del chat.
- Modo live: overlay a pantalla completa en `MainActivity` (`buildLive`, `openLive`, `closeLive`, `onLiveOrbTapped`). Lo abren el botón de micrófono y el widget (`ACTION_LIVE`, `launchMode=singleTop`, `onNewIntent`). Saludo hablado al abrir (`liveGreeting`).
- Voz por frases: `SpeechChunker` (probado) + `VoiceController.beginStream/streamSentence/endStream`; `askGemma` habla cada frase al llegar si el modo voz estaba activo al empezar. `VOICE_STYLE` pide respuestas cortas. Interrumpir: `stopSpeaking()`; si Gemma sigue escribiendo, `endStream()` devuelve false y se vuelve a escuchar al terminar. Silencio de fin de frase pedido: 1000 ms.
- Widget: `ViewFlipper` con 5 cuadros vectoriales (`ari_face_open/glow/blink`) cada 900 ms; tocar mensajes envía `WIDGET_POKE` → `ari_face_happy` y saludo 4 s (`goAsync`). Detecta también el modelo descargado.
- Icono: `ic_launcher_foreground` con la cara (zona segura 66 dp), `ic_launcher_monochrome` con ojos y sonrisa recortados, fondo con brillo radial.

## Modo live más natural y accesos rápidos (versión 0.10.2)

- `SpeechChunker`: si lo pendiente pasa de 70 caracteres sin fin de frase, corta en la última ", " (si está después del carácter 25). Pruebas nuevas.
- `askGemma`: muletilla (`THINKING_FILLERS`) a los 1800 ms si aún no se dijo ninguna frase; se cancela en `onDone`/`onError`.
- Modo live: `FLAG_KEEP_SCREEN_ON` mientras está abierto; `onBackPressed` lo cierra (la app no usa el gesto atrás predictivo); vibración `CONTEXT_CLICK` al empezar a escuchar.
- Acceso directo estático (`res/xml/shortcuts.xml`) que apunta a `LiveShortcutActivity`, un puente invisible con `taskAffinity=""`: Android abre los accesos directos con CLEAR_TASK y, sin el puente, Ari recargaría a Gemma.
- `AriLiveTile`: TileService "Hablar con Ari"; en Android 14+ usa `startActivityAndCollapse(PendingIntent)`; con el teléfono bloqueado pide desbloquear primero.
- `ic_notification` ahora es la carita en un solo color (también la usa el botón de ajustes rápidos).

## Seguridad del modo live (versión 0.10.3)

- `MainActivity` es exportada (la abre el lanzador), así que cualquier app podía mandarle `ACTION_LIVE` y encender el micrófono. Ahora el widget, el botón de ajustes rápidos y el acceso directo pasan por `LiveShortcutActivity` (no exportada), que deja un boleto en memoria (`consumeTicket`, vence en 10 s). `handleLaunch` solo abre el modo live si hay boleto; si no, la app abre normal.

## Ari en segundo plano (versión 0.11.0)

- `LiveSessionService`: servicio en primer plano tipo `microphone` (permisos FOREGROUND_SERVICE y FOREGROUND_SERVICE_MICROPHONE). Se inicia en `startVoiceMode` (con Ari en pantalla, como exige Android 14) y se detiene en `stopVoiceMode`/`onDestroy`. Notificación fija "Ari en modo live" con acciones Hablar/Terminar (PendingIntent al propio servicio → `Controller` registrado por MainActivity). `START_NOT_STICKY`: si Android mata el proceso, el micrófono no vuelve solo.
- `onPause` ya no detiene el modo voz si el servicio corre. La lógica de voz y Gemma sigue en MainActivity (detenida pero viva; el servicio mantiene el proceso).
- Fuera de Ari, `listen()` espera 1.2 s y, si `AudioManager.isMusicActive`, pasa a `standby` (no escucha). Tras 3 silencios fuera de Ari, `standby`; a los 5 min en pausa, termina.
- Falta probar en el POCO que el reconocedor de Google acepte escuchar con la app en segundo plano.

## Adjuntar imágenes y documentos (versión 0.12.0)

- Motor: `EngineConfig(visionBackend = Backend.CPU(), maxNumImages = 4)`; si falla, se reintenta solo texto (`visionReady`). `startEngine` cierra el motor si `initialize()` falla. Falta confirmar en el POCO que gemma-4-E4B-it.litertlm trae el codificador de visión.
- `Attachments.kt`: `AttachmentReader.read` (imágenes reducidas a 1024 px con EXIF, PDF con `PdfRenderer` hasta 4 páginas como JPEG, .docx desde `word/document.xml`, texto hasta 512 KB), copias en `cacheDir/adjuntos` que se borran al terminar y al abrir la app. `DocumentText` es puro y tiene pruebas.
- `sendWithAttachment` salta a los agentes y va directo a `askGemma(text, attachment, savedAs)`; con imágenes usa `sendMessageAsync(Contents.of(ImageFile…, Text), callback)`. El contenido del documento va entre <<< >>> marcado como datos (defensa contra inyección de instrucciones).
- Selector: `ACTION_PICK_IMAGES` (Android 13+, sin permisos) o `ACTION_GET_CONTENT`; documentos con `ACTION_OPEN_DOCUMENT`. Sin cámara directa todavía (no hay FileProvider sin androidx).

## Tomar foto y compartir con Ari (versión 0.12.1)

- `CaptureProvider` (no exportado, `grantUriPermissions`): presta `content://com.ari.assistant.camara/foto-<uuid>.jpg` a la cámara con `ACTION_IMAGE_CAPTURE` + `EXTRA_OUTPUT`. Solo acepta nombres `foto-…jpg` (sin rutas). Ari no declara CAMERA, así que no pide permiso. `pendingPhoto` se guarda en `onSaveInstanceState` por si Android cierra a Ari mientras la cámara está abierta. Tras leerla, se borra el original.
- `ShareActivity` (exportada, ACTION_SEND con image/*, text/*, PDF, docx, JSON): copia el adjunto mientras tiene el permiso temporal (`AttachmentReader.read` o `fromText`), lo deja en memoria (`take()`) y abre MainActivity con `ACTION_SHARED`, que lo muestra con `showAttachment`. Nada se envía solo.

## Pláticas largas (versión 0.12.2)

- LiteRT-LM toma el límite de tokens de los metadatos del modelo (o 4096) y la `Conversation` acumula toda la plática; al pasarse, `Prefill` falla con "Input token ids are too long". Antes, Ari quedaba fallando hasta recargar.
- Ahora `askGemma(..., retried)`: si el error es de contexto lleno (`isContextFull`) y no llegó nada de respuesta, `freshConversation` cierra la conversación, abre otra (historyPending/memoriesPending = true) y reintenta una vez.
- `DocumentText.MAX_CHARS` bajó a 6 000 caracteres (unas 1 000 palabras) para caber en 4096 tokens con la personalidad, recuerdos e historial.
- `LiveSessionService`: "Hablar" en una notificación vieja ya no deja el servicio encendido.

## Revisión de código 0.10.0 a 0.12.2 (versión 0.12.3)

Correcciones de la revisión (10 hallazgos):
- `AttachmentReader.clean` y `CaptureProvider.clean` solo borran archivos de más de 30 minutos (antes borraban la foto recién compartida al abrir Ari en frío).
- `VoiceController`: si el motor de voz no arranca (`ttsFailed`), `speak` y `streamSentence` no se quedan esperando; el modo live sigue escuchando.
- `ShareActivity`: solo acepta `content://` de otras apps (nada de `file://` ni direcciones de Ari: era un "ayudante confundido" que leía archivos privados).
- `LiveSessionService.stop` manda `ACTION_STOP` con `startService` en vez de `stopService`, para no apagarlo antes de `startForeground` (cierre de la app en algunas versiones).
- `listen()` y "Hablar" cancelan `listenInBackground` pendiente (evita doble arranque del reconocedor).
- `stopVoiceMode` quita `FLAG_KEEP_SCREEN_ON`; `startVoiceMode` lo vuelve a poner si el modo live está a la vista.
- `freshConversation` fallido: detiene el modo voz, avisa en el chat y libera el motor.
- `AriOrbView`: el degradado del cuerpo se crea una vez y se escala el lienzo; la carita de la bienvenida se pausa (`paused`) mientras el modo live la tapa.
- El subtítulo del modo live limpia solo los últimos 600 caracteres por pedazo (antes, toda la respuesta cada vez).
- `DocumentText.prompt` desarma "<<<" y ">>>" dentro del documento (prueba nueva).

## Libreta de personas (versión 0.13.0)

Pedido del usuario: "Ari puede conocer a personas nuevas a través de chats" → eligió "que recuerde a quien le menciono".
- `agents/PeopleParser.kt` (puro, probado en `PeopleAgentTest`): "mi <relación> [Nombre] <verbo> …", "mi <relación> se llama X", "X es mi <relación>", "te presento a X, mi <relación>", "X cumple el 5 de mayo", "qué sabes de X", "olvida a X", "a quién conoces", "quién cumple años". Ignora frases con pregunta, "pero", "qué hago", etc., o de más de 18 palabras (van a Gemma).
- `agents/PeopleAgent.kt`: junta datos de la misma persona (por nombre o, en relaciones únicas como mamá/jefe, por relación; la ficha "Mamá" se vuelve "Rosa"). Hasta 10 datos por persona. Preguntar/olvidar solo si la persona existe (si no, sigue la memoria o Gemma).
- Contratado antes del agente del teléfono y del de memoria ("recuerda que mi primo…" y "olvida a Luis" son de la libreta).
- `AriDatabase` versión 4: tabla `people` (name, relation, facts por línea, birthday "MM-DD"); entra en el respaldo (`TABLES`).
- `buildPrompt` agrega hasta 25 personas (160 caracteres cada una) junto con los recuerdos.

## Más rápida: Gemma en la GPU (versión 0.13.1)

El usuario probó 0.12.3 y dijo que responde lento. Hasta aquí Gemma corría con `Backend.CPU()`.
- `startFastestEngine`: intenta GPU+visión (visión en CPU), GPU solo texto, CPU+visión, CPU solo texto. `engineLabel` se muestra al cargar.
- Protección contra cierres nativos (no se pueden atrapar): preferencia `ari_motor/gpu` = "probando" (escrita con `commit` antes de tocar la GPU) → "ok" o "falla". Si al abrir dice "probando", la vez anterior se cerró: se marca "falla" y ya no se intenta.
- Ajustes → Velocidad de Gemma: "Rápida (GPU)" borra la marca; "Segura (CPU)" la pone en "falla"; recarga el modelo.
- Revisado en LiteRT-LM: el modo "pensar" de Gemma 4 solo se activa si la plantilla recibe `enable_thinking`; Ari no lo manda, así que no hay razonamiento oculto que la haga lenta.

## Esfera más elaborada (versión 0.13.2)

Decisión del usuario en el tablero: "Seguir con la esfera, más elaborada".
- `AriOrbView`: remolino de luz recortado al círculo (más rápido al pensar), tinte por ánimo, sombra radial cacheada, brillo de orilla, brillo en los ojos, cejas al pensar, mejillas (más intensas feliz/hablando), destellos de cuatro puntas cuando está feliz.
- Widget: `ari_face_*` con sombra, brillo de orilla, mejillas y brillo en los ojos.

## Reconocer voces (versión 0.14.0)

Decisión del usuario: "Sí, mi voz y la de otras personas". Detalle técnico en docs/RECONOCER_VOCES.md ("Cómo quedó").
- Paquete `voz/` (VoiceCapture, SpeechEndDetector, VoiceIdentity, VoicePrints, VoiceVault) y `VoiceController.speakerMode`.
- Ajustes → Reconocer voces: registrar la propia voz, agregar a otra persona con confirmación de permiso, borrar, activar/desactivar.
- Pruebas puras en `voz/VoiceTest`. Lo que solo se ve en el teléfono: si el reconocedor de Google acepta `EXTRA_AUDIO_SOURCE`, y el umbral 0.55.
- APK solo arm64-v8a (abiFilters) por el peso de onnxruntime.

## Ari ve tu pantalla y chat de notificaciones (versión 0.15.0)

Pedidos del usuario: "Quiero que Ari vea mi pantalla cuando le diga" y "Quiero que me mande mensajes Ari cuando algo esté listo" (eligió "Mejor sin internet").
- `ScreenRequest` (pruebas en `ScreenRequestTest`) detecta "mira mi pantalla" y descarta brillo, fondo, apagar, tiempo de pantalla, grabar.
- `ScreenGlance.kt`: aviso "Ari quiere ver tu pantalla" (Android no deja abrir actividades desde atrás) → `ScreenConsentActivity` (no exportada, translúcida) lanza `createScreenCaptureIntent` (pantalla completa en Android 14+) → `ScreenGlanceService` (FGS `mediaProjection`) toma una imagen con `ImageReader`/`VirtualDisplay` tras 900 ms, la guarda como JPEG de 1024 px y para la proyección. MainActivity la manda a Gemma como `Attachment.Kind.SCREEN` con la pregunta. Solo dueño; con Ari en pantalla da una indicación; la pregunta vale 2 minutos.
- `AriChat.kt`: reemplaza a `TaskNotifier`. `notifyIfAway(text)` escribe en un chat `MessagingStyle` (Persona "Ari", acceso directo de conversación en Android 11+, últimas 8 líneas). "Responder" con `RemoteInput` → `AriChatReceiver` (no exportado; `PendingIntent` mutable, `setAuthenticationRequired` en Android 12+) → `AriChat.controller` en MainActivity, que lo trata como mensaje escrito. Silencioso en modo live; se limpia en `onResume`. Canal viejo "avisos" se borra.

## Ari elige su modelo según el teléfono (versión 0.16.0)

Pedido del usuario: instalar una versión básica en el teléfono de otra persona; sin preferencia de forma, se hizo "misma app que se adapta".
- `ModelChoice` (pruebas en `ModelChoiceTest`): modelos E4B (3,659,530,240 bytes) y E2B (2,588,147,712 bytes, `gemma-4-E2B-it.litertlm` de litert-community, tamaño revisado en Actions). `recommend(ram, libre)`: RAM >= 7 GiB y espacio → E4B; sin espacio → E2B; RAM >= 3 GiB → E2B básica; menos → E2B con aviso.
- `ModelChoice.basic(ram)`: RAM < 7 GiB → `startFastestEngine` no intenta la vista.
- `ModelDownload.start(model)`, `freeSpace()`, `downloadedModel()` (prefs `modelo` y `bajando`). Botón Modelo con el recomendado primero.
- Actions: paso "Revisar enlaces de los modelos" (no detiene la compilación) muestra el tamaño de ambos archivos.

## Informe de fallos (versión 0.17.0)

Pedido del usuario: que la app de la otra persona (tester) avise cuando falle. Respeta "sin internet": el informe se manda con Compartir, decidido por quien prueba.
- `FailureReport.kt`: `AriApp` (Application) instala un `UncaughtExceptionHandler` que guarda `files/fallos/ultimo.txt` y deja seguir al de Android. `checkSystemExit` (API 30+) lee `getHistoricalProcessExitReasons`: nativo (GPU), ANR (con traza) y falta de memoria; prefs `ari_fallos/salida_vista`.
- Texto con versión, teléfono, Android, RAM, versión básica, modelo, estado de la GPU y la traza (mensajes recortados a 300 caracteres, máximo 6000 caracteres). Pruebas en `FailureReportTest`.
- MainActivity: `offerFailureReport` al abrir; `showReport` (vista previa + ACTION_SEND); Ajustes → Reportar un problema (`askProblemReport`).

## Contexto de Gemma con ConversationConfig (versión 0.18.0)

Propuesta de otra sesión (rama ccr-3d48bcea-ddppo3, commit bd7e6a2), adaptada a esta rama.
- `openConversation(engine, guest)`: `ConversationConfig(systemInstruction = personalidad + recuerdos + libreta, initialMessages = GemmaContext.trimHistory(...))`. Invitados: solo `guestPreface()`, sin historial.
- `GemmaContext.trimHistory` (pruebas en `GemmaContextTest`): últimos 20 mensajes, 4000 caracteres en total, 1200 por mensaje; empieza con el usuario y termina con Ari (se quita el mensaje del usuario sin respuesta, que es el que se está enviando).
- `buildPrompt` ya no pega "[Contexto privado…]": devuelve el texto (con "[Habla X]" para invitados).
- `historyPending`/`memoriesPending` ahora significan "hay que recrear la conversación": `askGemma` llama a `freshConversation` antes de enviar. La carga del modelo, `freshConversation` y la recuperación del respaldo usan `openConversation`. Todo pasa por el `executor` de un solo hilo.

## Corrección "Status Code: 13" (versión 0.18.1)

Reportado por el usuario (captura vista por otra sesión, en 0.17.0): después de una respuesta muy larga, "Error de respuesta: Status Code: 13" en cada mensaje, incluso tras recargar el modelo (GPU, con vista). `isContextFull` no reconocía el 13 (INTERNAL), así que no había recuperación.
- `askGemma.onError`: cualquier error con la respuesta vacía → `freshConversation` y un reintento. Si el reintento también falla en la GPU → `offerCpu()` (diálogo para usar la CPU).
- `startFastestEngine`: intenta `maxNumTokens = 8192` (LARGE_CONTEXT_TOKENS) fuera de la versión básica y, si el motor no lo acepta, el valor del modelo. `largeContext` decide el historial inicial (4000 o 2000 caracteres).
- Causa exacta sin confirmar: probablemente la memoria de la plática (KV) llena en la GPU con un mensaje de error distinto.

## Primer uso con la recomendación de modelo (versión 0.19.0)

Pedido del usuario (vía otra sesión): que quien reciba Ari vea sola la recomendación de modelo.
- `offerModelOnce()`: sin modelo y sin descarga en curso, una sola vez (prefs `ari_descarga/ofrecida`): diálogo con `ModelChoice.recommend` y botones "Descargar ahora" (o "Descargar de todos modos" si `tooWeak`), "Más tarde" (aviso en la barra de estado) y "Ya tengo el archivo". Se llama después del nombre en el primer uso, o al abrir en instalaciones que aún no tienen modelo.

## Ari se presenta (versión 0.20.0)

Pedido del usuario: "Para que las personas conozcan y sepan qué Ari puede hacer por ellos. Debe decirles".
- `AriAbilities` (pruebas en `AriAbilitiesTest`): `asks(text)` reconoce "¿qué puedes hacer?", "ayuda", "¿para qué sirves?"; `intro(nombre, Phone(vision, voices))` arma la lista con ejemplos reales de los agentes; `spoken()` es la versión corta para voz.
- MainActivity: `introduce()` responde localmente (sin Gemma) antes del CEO; `introduceOnce()` después del nombre en el primer uso (prefs `ari_descarga/presentada`), antes de la oferta del modelo.
- ChatView: botón "¿Qué puedes hacer?" en la bienvenida (`onSuggestion`).

## Color de Ari (versión 0.21.0)

Pedido del usuario: "Se puede incluir cambiar el color de Ari".
- `AriColors` (pruebas en `AriColorsTest`): 6 paletas (hielo, rosa, lila, menta, durazno, dorado) con claro, principal y orilla; `requested(text)` entiende "ponte rosa", "cambia tu color a lila", "píntate de verde".
- `AriTheme.ice/iceDeep/iceLight` ahora cambian con `setColors` (y `colorVersion`); `AriOrbView` rehace sus degradados cuando cambia la versión.
- MainActivity: `applyColor` (prefs `ari_color/color`), Ajustes → Color de Ari, respuesta local al pedirlo.
- Widget: si el color no es hielo, cada carita se dibuja en un bitmap de 128 px con el tono girado (ColorMatrix de rotación de tono + saturación).
- Informes de fallo por correo (decisión del usuario: "Por correo a tu Gmail"): `sendReport` usa `ACTION_SENDTO mailto:` a `R.string.report_email` con asunto "Informe de fallo de Ari <versión>"; "Otra app" usa Compartir. Claude los busca en Gmail al empezar cada sesión (CLAUDE.md).

## Solo la Ari del dueño es la CEO (versión 0.22.0)

Decisión del usuario: "Dos versiones del APK".
- `productFlavors` en app/build.gradle.kts: `completa` y `otros` (versionNameSuffix "-otros"). Mismo applicationId.
- `src/completa`: `TeamAgent`, `NetworkGuard`, `CeoTeam` (buscar, contratar, red aprobada, permiso de ubicación) y un manifiesto con los permisos de Wi-Fi/ubicación y `usesCleartextTraffic`.
- `src/otros`: `CeoTeam` vacío (`enabled = false`; con el equipo dice que no trabaja con uno).
- main: `CeoHost` (lo que CeoTeam usa de MainActivity) y `PrivateAddress` (antes `NetworkGuard.isPrivateAddress`, lo usa el respaldo).
- Actions: pruebas de ambas versiones; artefactos `ari-debug-apk` (completa) y `ari-otros-apk` (desde 0.26.0: `ari-apk` y `ari-otros-apk` son release).

## Ari enseña y ayuda en el día a día (versión 0.23.0)

Pedido del usuario: la Ari para otras personas debe mejorar su vida cotidiana, hacerles fácil las cosas y enseñarles a usarla.
- `AriLessons` (pruebas en `AriLessonsTest`): 8 lecciones (platicar, recordatorio, recuerdo, personas, teléfono, voz, foto, color) con algo concreto para probar; `completedBy(handler)` según el agente que atendió, "gemma", "voz", "foto" o "color"; `asks()` reconoce "enséñame", "¿cómo te uso?", "tutorial".
- MainActivity: `startLessons`, `lessonDone` (prefs `ari_lecciones`: hechas, activas, consejo_dia), `dailyTip` (solo versión para otros). Se marcan al terminar delegaciones, respuestas de Gemma, modo voz y cambio de color. Primer uso: empiezan solas tras la presentación.
- `Profile.personality(ceo)`: con `ceo = false` (versión para otros) no habla de ser CEO; se enfoca en la vida diaria y en ofrecer la frase exacta de las funciones.
- Bienvenida: sugerencias "¿Qué puedes hacer?", "Enséñame a usarte", "Ayúdame a organizar mi día".

## Recados entre Ari por SMS (versión 0.24.0)

Decisión del usuario: "Por SMS, sin internet", y "no es necesario que uno confirme el envío, que Ari lo envíe directo" (excepción a la regla de confirmar el último paso, solo para recados).
- `AriMessages` (pruebas en `AriMessagesTest`): `request` ("dile/avísale/mándale decir a X que Y"), `encode` ("[Ari de Nombre] texto", máx. 280), `decode`, `announce` (nombre de tus contactos; si no está, lo advierte).
- `AriMessenger.kt`: `AriMessageAgent` (id "mensajes", primero en el CEO, fuera de GUEST_AGENTS) manda con `SmsManager` a un contacto; `AriSmsReceiver` (exportado con BROADCAST_SMS) solo procesa SMS con la marca; `AriMessenger.deliver` → chat (listener) o bandeja `ari_recados` + chat de notificaciones.
- Permisos SEND_SMS, RECEIVE_SMS (y READ_CONTACTS), pedidos al usarlo o en Ajustes → Recados de otras Ari. `uses-feature telephony` no obligatorio.
- Google Play restringe SMS a apps de mensajes predeterminadas: revisar antes de publicar.

## Tutora personal (versión 0.25.0)

Pedido del usuario: tutora personal en cualquier tema (ejemplo: inglés), en las dos Ari.
- `AriTutor` (pruebas en `AriTutorTest`): `startTopic` ("quiero aprender X", "sé mi maestra de X", "dame clases de X", "enséñame X" salvo "enséñame a usarte"), `resumeTopic` ("sigamos con X" si ya hay clases), `stops` ("terminar clase"), `instruction` (va en las instrucciones de sistema), `summaryIn` (línea "RESUMEN DE LA CLASE:").
- MainActivity: `handleTutor` antes de lecciones y agentes (solo el dueño); prefs `ari_tutora` (clase activa, temas, resumen_<tema>, clases_<tema>). Al empezar o terminar marca `historyPending` para recrear la conversación con o sin la tutora. El resumen se guarda en `onDone`.
- Pendiente: TTS en el idioma que se aprende.

## Ari protegida, no es código abierto (versión 0.26.0)

Pedido del usuario: que nadie que sepa programar pueda modificarla; no código abierto.
- `release` con `isMinifyEnabled`/`isShrinkResources` (R8) y `proguard-rules.pro`: se conservan LiteRT-LM y sherpa-onnx (JNI), los nombres de los enum (se guardan en prefs y respaldo) y archivo/línea para los informes. Firmada con la misma clave (`debug.keystore`) para instalarse encima. Release no es depurable.
- `AppIntegrity`: compara el SHA-256 del certificado de firma con `OFFICIAL_CERT_SHA256`; si no coincide, MainActivity muestra "Esta copia de Ari no es la original" y no se arma (`tampered` corta onResume/onPause/onStop/onDestroy/onNewIntent). `AppIntegrityTest` revisa que la huella sea la de `app/debug.keystore`.
- Actions: `ari-apk` (completa release), `ari-otros-apk` (otros release), `ari-debug-apk` (respaldo), `ari-mapa` (mapping.txt para leer trazas).
- `LICENSE`: todos los derechos reservados, con los componentes de terceros y sus licencias.
- Riesgo: R8 puede romper algo que solo se ve en el teléfono; por eso se guarda también la de depuración.

## Corrección: la app no se instalaba (versión 0.26.1)

El usuario reportó "No se instaló la app" con 0.26.0 (release y debug) y 0.24; 0.23 sí se instala. La revisión en Actions mostró APK válidas (firma v2 con la huella oficial, zipalign, arm64). Causa: los permisos SEND_SMS/RECEIVE_SMS (agregados en 0.24); el POCO (HyperOS/Android nuevo) bloquea instalar apps de fuera de la tienda que los piden.
- Se quitaron SEND_SMS, RECEIVE_SMS, `AriSmsReceiver` y `uses-feature telephony`.
- `AriMessageAgent` abre la app de mensajes con `PhoneAction.Sms` (el usuario toca enviar); solo pide READ_CONTACTS.
- Recibir: `ShareActivity` reconoce texto con la marca y lo guarda con `AriMessenger.keep` (se muestra al abrir); pegar el recado en el chat también lo anuncia (`AriMessages.announceShared`).
- Actions: paso "Revisar APK" (apksigner, aapt2, zipalign) para diagnosticar.

## Mejoras pedidas el 14/10 (versiones 0.27.0 y 0.28.0)

Lista del usuario en docs/BACKLOG.md ("Mejoras pedidas el 14 de octubre de 2026").
- 0.27.0: `CalendarAgent` + `AndroidCalendar` (READ_CALENDAR, REQUEST_CALENDAR 115); `AriNews` (novedades por versionCode en prefs `ari_novedades/vista`; comandos de voz con `asksCommands`).
- 0.28.0: `AriBrain` (línea "APRENDÍ:" en la respuesta → recuerdo "(Aprendido) …", máx. 50, sin recrear la conversación; se quita de lo que se dice en voz). `Profile.personality`: conversación natural y adaptarse al usuario; con `ceo = true`, franqueza total y proponer mejoras. `notifyIfAway` dice en voz el primer enunciado ("Listo. …") si `hablar_al_terminar` (por defecto sí en la CEO, no en la de otros).
- Límites explicados al usuario: no se quitan las reglas de seguridad propias de Gemma; la "autonomía total" se hace como proactividad, y lo que afecta a otros sigue con confirmación.
- 0.29.0: `AriBubble.kt` (servicio en primer plano `specialUse` + ventana `TYPE_APPLICATION_OVERLAY` con `AriOrbView` de 56 dp; toque → `onTalkRequested` o `LiveShortcutActivity`; arrastrar; mantener = esconder; se enciende en `onPause` y se apaga en `onResume`; prefs `ari_burbuja`). Se quitó el receptor del widget del manifiesto. `voz/BargeIn.kt`: `BargeInDetector` (pruebas en `BargeInTest`) y `BargeInMonitor` (AudioRecord VOICE_COMMUNICATION + AcousticEchoCanceler mientras Ari habla; `stop()` espera 0.2 s para soltar el micrófono antes del reconocedor). Ajuste `interrumpir_hablando`.

## Informe de fallo atendido (versión 0.29.1)

Primer informe por correo (14/10, de contrerasasael33@gmail.com, tester): "Ari se cerró por un error del motor (GPU)", 0.23.0-otros, Samsung SM-S918B (S23 Ultra), Android 16, RAM 11.6 GB, Gemma 4 E4B, GPU: falla (la protección ya pasó a CPU).
- Sospecha: pedir 8192 tokens en la GPU (desde 0.18.1). Ahora hay una marca aparte `gpu_memoria_grande`: si Ari se cierra probando la memoria grande en la GPU, la siguiente vez la GPU se prueba con la memoria normal en lugar de pasar directo a CPU.
- Ajustes → Velocidad → Rápida (GPU) después de una falla vuelve a probar la GPU con la memoria normal.
- 0.30.0: boceto del usuario para la burbuja: `AriBubbleService.openPanel` (ventana enfocable con FLAG_NOT_TOUCH_MODAL + WATCH_OUTSIDE_TOUCH; campo que manda por `AriChat.controller`, o abre Ari con `ACTION_WRITE` + `EXTRA_TEXT`; botón Claude según `R.bool.ari_ceo`, true solo en `src/completa/res/values/ceo.xml`).

## Ari CEO exclusiva del dueño (versión 0.31.0)

Pedido del usuario: "La Ari CEO es exclusiva para mí. Más nadie puede tenerla".
- `DeviceLock` (pruebas en `DeviceLockTest`): código del teléfono = primeros 8 hex de SHA-256("ari-ceo:" + ANDROID_ID). Se ve en Acerca de Ari.
- `R.array.ari_ceo_telefonos` en `src/completa/res/values/ceo.xml`: si tiene códigos, la Ari CEO solo abre en esos teléfonos ("Esta Ari es exclusiva de su dueño"). Vacío mientras el dueño no mande su código (arranque). En la Ari para otros, la lista está vacía y `ari_ceo` es false: sin candado.
- PENDIENTE: el código del POCO del dueño (lo lee en Ajustes → Acerca de Ari de la 0.31.0) y agregarlo a la lista.

## Ari lee sola los recados (versión 0.32.0)

Decisión del usuario: "Sí, que Ari lea sola los recados".
- `AriRecadosListener` (NotificationListenerService, BIND_NOTIFICATION_LISTENER_SERVICE): solo apps de mensajes (la predeterminada de SMS + Google Mensajes, Samsung, MIUI); textos de EXTRA_MESSAGES o EXTRA_BIG_TEXT/EXTRA_TEXT; `AriMessages.decode`; nombre = título de la notificación (contacto de la app de mensajes); `AriRecados.firstTime` evita repetir. Entrega con `AriMessenger.deliver`.
- Ajustes → Recados entre Ari: explica el permiso (incluida "Configuración restringida") y abre `ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS`.

## Contactos de recados (versión 0.33.0)

Pedido del usuario: máximo 4 contactos de recados en la Ari para otros (Ari pide guardarlos), sin límite en la CEO; el envío desde la burbuja.
- `QuickContacts` (prefs `ari_recados_contactos`, JSON) y `QuickContactRules` (pruebas en `QuickContactsTest`; compara los últimos 10 dígitos). Límite por `R.bool.ari_ceo`.
- `AriMessageAgent`: si el contacto no está guardado → `pendingSave` y MainActivity pregunta (`askSaveQuickContact`: Guardar y enviar; en la CEO también Solo enviar). Lleno → explica cómo quitar uno.
- Burbuja: fila "Recados" con los contactos; el campo manda el recado con `ACTION_SENDTO smsto:` + `sms_body` (el usuario toca enviar en su app de mensajes).
- 0.34.0: pedido del usuario "no quiero escribir el recado, quiero decírselo por voz". `DictateRecadoActivity` (translúcida, no exportada): `ACTION_RECOGNIZE_SPEECH` con "¿Qué le digo a X?" y abre `smsto:` con el recado. En la burbuja: tocar el contacto = voz; mantener = escribir.

## Ari CEO envía sola los recados (versión 0.35.0)

Decisión del usuario: "Accesibilidad, solo Ari CEO".
- `AutoSend` (main; pruebas en `AutoSendTest`): `arm(texto, nombre)` antes de abrir `smsto:` (agente, burbuja, dictado), válido 20 s; `matches` exige el texto exacto con "[Ari".
- `AriSendService` (solo `src/completa`, AccessibilityService limitado a paquetes de mensajes por `xml/ari_send_service.xml`): busca la caja editable con el recado y el botón enviar (ids conocidos de Google Mensajes, Samsung, MIUI, o texto "Enviar/Send"); toca y avisa "Listo, le mandé el recado a X".
- Ajustes → Recados entre Ari ahora es un menú: cómo funciona, leer solos, enviar solos (solo CEO), quitar contacto. Novedades con `ceoOnly`.

## Leer con calma mientras Ari escribe (versión 0.36.0)

Pedido del usuario: poder leer sin que el chat se vaya hacia abajo.
- `ChatView`: `follow` (solo un desplazamiento hacia arriba hecho con el dedo deja de seguir; volver al fondo lo reactiva); `scrollToEnd` solo si `follow`; `addUser` usa `jumpToEnd`. `onFollowChanged` muestra el botón "↓ Ir al final" (FrameLayout sobre el chat en MainActivity).

## Ari responde a su nombre (versión 0.37.0)

Decisión del usuario en el tablero (palabra-ari): "A: siempre, con modelo de voz".
- Se probaron en el contenedor los modelos de palabra clave de sherpa-onnx con voz sintética en español: el de inglés (gigaspeech 3.3M) casi no detecta "Ari" y el reconocedor en español (Kroko, 155 MB) tampoco lo transcribe bien. Por eso se eligió una palabra **personal**: el usuario dice "Ari" 3 veces y Ari compara por forma de sonido. Sin modelo extra (0 MB).
- `voz/WakeWord.kt` (puro; pruebas en `WakeWordTest`): MFCC (12 coeficientes cada 10 ms), DTW con distancia coseno y final abierto (la palabra puede durar 0.6–1.6 veces la muestra), `WakeWordModel.learn` (límite = 1.7 × parecido medio entre muestras, entre 0.10 y 0.20; null si no se parecen) y `WakeSegmenter` (palabra suelta de 0.2–1.3 s con 0.35 s de silencio; las frases largas no cuentan).
- `WakeWordService` (servicio en primer plano tipo micrófono, notificación fija "Di Ari para llamarme" con Apagar): suelta el micrófono mientras Ari está en pantalla (`AriWakeWord.appVisible`) o en modo live. Al oír "Ari": tonito y abre Ari en modo live por `LiveShortcutActivity` (si hay permiso de superposición; si no, notificación "¿Me llamaste?").
- `AriWakeWord`: guarda solo los MFCC en `files/ari_nombre.bin`, sensibilidad (más fácil 1.25, normal 1.0, más estricta 0.8). Se enciende al abrir Ari (Android no deja encender el micrófono desde atrás); si Android cierra el proceso, vuelve al abrir Ari.
- Ajustes → Responder a "Ari": enseñar (3 grabaciones con `VoiceCapture`), encender/apagar, sensibilidad, olvidar. Ambas versiones.
- Pendiente de [prueba] en el teléfono: ajustar el límite con voz real (la prueba con voz sintética separó "Ari" de otras palabras, pero con poco margen en "Ahí" y "Oye").

## Rutinas: Ari aprende órdenes nuevas sin reinstalar (versión 0.38.0)

Decisión del usuario sobre "Ari se mejora sola": las 3 (A, B y D). Esta es la B.
- `AriRoutines.kt` (lógica pura; pruebas en `AriRoutinesTest`): "cuando te diga X, paso1 y paso2, luego paso3" → `Routine(trigger, steps)`. Los pasos se parten en comas, "y", "luego" y "después", pero un pedazo que no empieza con una orden conocida se pega al anterior ("pon música de Juan y Juanes"). Protecciones: el primer paso debe ser una orden, el disparador tiene máximo 6 palabras y no puede ser una palabra común ("sí", "no", "ok"…). Máximo 30 rutinas y 6 pasos.
- "mis rutinas" / "qué rutinas tienes" las lista; "olvida la rutina X" la borra. La rutina se dispara solo si el mensaje entero es la frase.
- MainActivity: `handleRoutine` (solo el dueño, no invitados) antes de la tutora; `runNextRoutineStep` mete cada paso por `sendMessage` como si lo escribieras, esperando a que Ari termine el anterior (máximo 2 minutos por paso). Un paso no puede disparar otra rutina.
- Se guardan en los ajustes `ari_agentes` (clave `rutinas`) y viajan en el respaldo (`routines`).

## Ari CEO se propone mejoras (versión 0.39.0)

Parte A de "Ari se mejora sola". Solo la Ari CEO (`R.bool.ari_ceo`) y solo con el dueño.
- `AriImprovements.kt` (puro; pruebas en `AriImprovementsTest`): Gemma marca con una línea "MEJORA: …" lo que a la app le falta (instrucción en el sistema, como "APRENDÍ:"); también "anota una mejora: …". Se guardan sin repetir (máximo 30) en `ari_agentes`/`mejoras`.
- "propón mejoras" / "¿qué mejorarías?" / "tus mejoras": arma un pedido para Claude con versión, teléfono, fecha, la lista y las reglas del proyecto; diálogo "Copiar y abrir Claude" (portapapeles + app de Claude o claude.ai/code). "borra las mejoras" las limpia.
- `AriBrain.withoutNotes` también quita las líneas "MEJORA:" al hablar.
- Ari no cambia su propio código: Claude programa, prueba y compila. La parte D (programar con la laptop) espera a que la laptop tenga Linux.

## Carita de Ari que habla y piensa (versión 0.40.0)

Pedido del usuario: que mueva la boca al hablar y al pensar, con amor en el diseño y la interfaz.
- `ui/AriOrbView`: boca por sílabas (a, e, i, o, u, m, s) con lengua y dientes, comisuras que sonríen; al pensar murmura con la boca de lado, mira arriba y sube las cejas; parpadeo suave (a veces doble); mirada, cejas, sonrisa y entrecerrar los ojos cambian con suavidad. `talk(chars)` abre la boca y la deja "hablando" un rato según el texto. Quieta dibuja a 20 cuadros por segundo para gastar menos. Las caritas chicas llevan menos adornos.
- `VoiceController.onRangeStart` → `Listener.onWordSpoken`: cada palabra de la voz del teléfono mueve la boca (modo live, carita de arriba y burbuja).
- Barra superior: la carita (52dp) en lugar del puntito; piensa mientras Gemma prepara la respuesta y mueve la boca con cada pedazo de texto. Tocarla abre el modo live.
- Chat: cada mensaje de Ari lleva su carita; la del mensaje en curso piensa y habla, y se queda quieta 3 s después (sin animarse). Los mensajes aparecen con una animación suave.
- Vista previa del diseño: `docs/ari_carita_preview.png` y `docs/ari_carita_hablando.gif` (dibujadas en Python con la misma geometría).

## Detalles de la interfaz (versión 0.41.0)

- El modo live aparece y se va con un fundido suave (`openLive`/`closeLive`, cancelando la animación si se abre mientras se cierra).
- Bienvenida: la carita se pone feliz y luego "dice" el saludo.
- "Enviar" se ve apagado (alfa 0.45) sin texto ni adjunto (`refreshSendButton`) y vibra suave al mandar; el campo de texto se enmarca con el color de Ari al escribir.

## Ari CEO maneja tus apps (versión 0.42.0)

Decisión del usuario: "Control total con pausa". Solo la Ari CEO.
- `AppControl.kt` (main, puro; pruebas en `AppControlTest`): "toca/dale a/presiona X" (no "toca música…"), "teclea …" / "escribe aquí …" (respeta mayúsculas y acentos), "desliza/baja/sube la pantalla", "atrás", "ve al inicio", "apps recientes", "notificaciones", "ajustes rápidos", "lee mi pantalla". `isSensitive` (enviar, pagar, comprar, borrar, confirmar, llamar, instalar…) → Ari pregunta "¿Toco X? Dime sí o no" (`pendingControl`).
- `AriSendService` (src/completa) ahora es "Ari CEO: manejar tus apps": implementa `AppControl.Controller` (se registra al conectarse). Ventana de la app que usas (nunca la de Ari, `flagRetrieveInteractiveWindows`); tocar por texto/descripción con `matchScore` (clic al ancestro tocable o gesto en el centro), escribir con `ACTION_SET_TEXT`, desplazar la lista más grande o gesto, acciones globales, leer textos visibles (`summarize`). Los recados siguen igual, solo en apps de mensajes. Ya no se limita por `packageNames`; `canPerformGestures`.
- MainActivity: `handleAppControl` tras las rutinas; si Ari está en pantalla se hace a un lado (`moveTaskToBack`) y actúa 0.8 s después; responde en el chat y en voz. Ajustes ahora es una lista de acciones; "Manejar mis apps" solo aparece en la CEO.
- Sin el permiso, "lee mi pantalla" sigue usando la vista de pantalla (proyección).
- Aviso "Ari se muestra sobre otras apps": es de Android (Sistema), no de Ari; se apaga en sus ajustes de notificación.
