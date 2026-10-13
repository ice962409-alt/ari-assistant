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
