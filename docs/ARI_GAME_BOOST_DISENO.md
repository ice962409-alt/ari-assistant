# ARI Game Boost — Propuesta de diseño

- Estado: **propuesta de diseño, sin código**. Nada de esto está implementado ni probado.
- Actualización 16/10/2026: la Fase 1 está cerrada y el usuario respondió las decisiones de la sección 8 (ver abajo). Se empieza por GB-1 y GB-2 juntas (versión 0.68.0), porque GB-1 sola no tiene nada visible que probar en el teléfono.
- **GB-1 y GB-2 programadas en la 0.68.0** (ver sección 6): `agents/GameBoost.kt` (lógica pura y agente), `GameBoostDevice.kt` (lecturas de Android), Ajustes → Game Boost (pantalla de solo lectura) y la base v5. Cambio respecto a la sección 5: las mediciones se enlazan con su sesión por la hora de inicio (`session_started_at`), no por el id, porque el respaldo vuelve a numerar las filas; todas las columnas son `NOT NULL` por la misma razón.
- Fecha: 16 de octubre de 2026.
- Bloqueo: **no se programa nada de Game Boost hasta superar la Fase 1** (criterios A, B y C; ver `docs/ARI_HANDOFF.md`, sección 6). Varias piezas dependen directamente de B y C.
- Base: rama `ccr-e45f2eee-wkjpdl` @ 9cbcaa3 (0.66.0). Las rutas `archivo:línea` se refieren a esa rama.
- Fuentes: auditoría de solo lectura del repositorio, documentación oficial de Android (developer.android.com, consultada el 16/10/2026) y el código del emulador Eden (espejo oficial `github.com/eden-emulator/mirror`, commit d8c6c59). Lo que no se pudo verificar se marca como **NO VERIFICADO**.

## 1. Qué es y qué no es

- Es un módulo de **optimización asistida**: ARI observa el estado del teléfono, recomienda cómo preparar una sesión de juego en un emulador externo (Eden) y solo actúa sobre sí misma, siempre con la aprobación del usuario.
- **No es un "boost" del sistema.** La documentación oficial de Android 14 dice que "It isn't possible for a 3rd-party application to improve the memory, power, or thermal behavior of an Android device". ARI no puede acelerar a Eden ni cambiar el hardware. Lo único que puede hacer es:
  - dejar de ocupar memoria ella misma (liberar Gemma);
  - informar con datos reales del dispositivo;
  - recomendar ajustes que el usuario cambia a mano dentro de Eden;
  - registrar resultados para comparar sesiones.
- Nombre: se sugiere mostrarlo al usuario como "Preparar juego" o "Modo juego" para no prometer un aumento de rendimiento. La política de Google Play contra afirmaciones engañosas aplica si algún día se publica.

## 2. Auditoría del repositorio

### 2.1 Lo que ya existe y se puede reutilizar

- **Liberar Gemma:** `MainActivity.releaseModel()` (`MainActivity.kt:2779-2792`) cierra la `Conversation` y el `Engine`.
- **Abrir otra app:** `AndroidPhone` usa `getLaunchIntentForPackage` (`AndroidPhone.kt:129`, `:234`). El manifiesto ya declara la consulta `MAIN/LAUNCHER` en `<queries>`, así que Eden es visible si tiene icono en el lanzador.
- **Persistencia:** `AriDatabase` (`ari.db`, versión 4, WAL), con migraciones solo aditivas en `onUpgrade` (`AriDatabase.kt:63-67`).
- **Bitácora con fecha:** la tabla `agent_tasks` sirve de precedente, pero no de historial (ver 2.3).
- **Orquestador de agentes:** `agents/AriCeo` enruta cada mensaje al primer agente que lo entiende. `ceo.route(text)` se evalúa **antes** de comprobar si Gemma está lista (`MainActivity.kt:3052-3061`), así que un agente de Game Boost funcionaría con Gemma descargada.
- **Uso del teléfono:** `AriUsage` usa `UsageStatsManager` (`AriUsage.kt:84`), con el acceso especial "Acceso a uso" que concede el usuario.
- **Memoria del sistema:** `ActivityManager.getMemoryInfo` ya se usa en `MainActivity.kt:2948-2951`, `AutoReport` y `FailureReport`.
- **Cierres de ARI:** `FailureReport.checkSystemExit` usa `getHistoricalProcessExitReasons` de la propia app.
- **Ajustes que ARI ya sabe abrir o cambiar con permiso:** `PhoneSettings`, como No molestar y brillo (solo en el sabor `completa`).

### 2.2 Lo que no existe

- No hay código de juegos, de Eden, de temperatura ni de batería, ni ningún "GE" o GameOrchestrator, en ninguna de las 6 ramas.
- No hay `onTrimMemory`. ARI **no libera Gemma al pasar a segundo plano**: `onPause` y `onStop` no la sueltan; solo `onDestroy` (`MainActivity.kt:4940`), que no está garantizado si Android mata el proceso.
- No hay un estado persistente de "Gemma descargada a propósito": `onCreate` llama a `loadSavedModel()`, que carga el modelo siempre que el archivo exista (`MainActivity.kt:2467-2480`).
- No hay ninguna API de Eden integrada. Eden no está declarado por paquete en `<queries>`.

### 2.3 Riesgos de reutilizar lo existente tal cual

- **El diálogo de `runTools` no sirve como aprobación previa.** Si el usuario responde "No", igualmente ejecuta los pasos que considera seguros (`MainActivity.kt:3626-3629`). Si todos son seguros, ni siquiera pregunta (`:3613-3616`). Game Boost necesita que "No" cancele todo.
- **`AriTools` trata "abre <app>" como seguro (SAFE)** y lo hace sin preguntar. Game Boost no debe usar las etiquetas `HACER:` de Gemma ni ese nivel de riesgo para abrir Eden.
- **`releaseModel()` no comprueba si Gemma está respondiendo (`busy`) ni pone `modelReady = false`.** NO VERIFICADO: qué hace LiteRT-LM si se cierra una conversación a mitad de una respuesta.
- **Servicios propios que siguen vivos al salir de ARI:** la burbuja, la palabra de activación y `LiveSessionService` (micrófono). `onPause` no detiene el modo voz si `LiveSessionService` está corriendo (`MainActivity.kt:4623`). Mantienen vivo el proceso que guarda a Gemma.
- **`agent_tasks` no vale como historial:** el texto es libre, no tiene métricas y `recentAgentTasks` no devuelve `created_at`.
- **El respaldo cifrado (`importData` y `exportData`) solo trata como números algunas columnas concretas** (`AriDatabase.kt:330-334`, `:356-358`). Si se añaden tablas con métricas numéricas al respaldo, hay que ampliar ese manejo de tipos. Una importación con una columna que falta lanza `JSONException` y falla entera.
- **Recargar Gemma justo después de jugar, con la memoria apretada,** puede hacer que Android mate a ARI mientras prueba la GPU. Por las marcas "probando", se perdería para siempre el contexto grande de 8192 tokens, o la GPU quedaría marcada como "falla" (`MainActivity.kt:2811-2814`).
- **`checkSystemExit` solo lee la última salida** y solo informa de `LOW_MEMORY`, `CRASH_NATIVE` y `ANR` (`FailureReport.kt:97-110`). En dispositivos sin soporte de `isLowMemoryKillReportSupported()`, una muerte por memoria aparece como `SIGNALED` y se ignora.

## 3. Arquitectura propuesta

- **ARI (interfaz y recomendaciones):** muestra el estado del teléfono, el plan de preparación, los perfiles y el historial. Toda acción pasa por una pantalla de aprobación.
- **GE (coordinador de preparación y lanzamiento):** un componente nuevo dentro de ARI, no una app aparte. Se propone como un agente determinista registrado en `AriCeo`, que entiende frases como "prepara Eden" o "voy a jugar", más una pantalla propia.
  - Funciona sin Gemma: no depende del modelo para decidir.
  - Solo en el sabor `completa` (la ARI del dueño), salvo que el usuario decida otra cosa.
  - Solo actúa sobre ARI misma: guardar estado, liberar Gemma y apagar servicios propios. Sobre el sistema y sobre Eden solo **observa y recomienda**.
- **Eden (app externa):** ARI lo abre con su actividad de lanzador. No lee ni cambia su configuración, no lo cierra y no mide nada dentro de él.

Flujo de una sesión:
1. El usuario pide "prepara Eden" o abre la pantalla de juego.
2. GE comprueba el estado (sección 4.2) y muestra el plan: qué va a hacer ARI consigo misma, qué recomienda y qué datos hay.
3. El usuario aprueba o cancela. "Cancelar" no hace nada.
4. Si aprueba, en este orden: escribir la sesión en SQLite de forma síncrona, liberar Gemma (si no está respondiendo), apagar los servicios propios aprobados y medir otra vez.
5. GE abre Eden desde la actividad visible de ARI, en respuesta al toque del usuario.
6. Al volver a ARI (aunque Android la haya matado), se lee la sesión abierta desde SQLite, se pide al usuario el resultado y se pregunta si recargar Gemma.

## 4. Diseño por requisito

### 4.1 Modo asistido (requisito 1)

- Cada sesión empieza con un plan visible. Cada acción lleva su botón de aprobación, o hay un único "Aprobar todo" con la lista completa a la vista.
- "No" o "Cancelar" no ejecuta nada. No se copia el comportamiento de `runTools`.
- Las acciones solo son sobre ARI: liberar Gemma, apagar la burbuja, la palabra de activación o el modo live, y abrir Eden.
- Las recomendaciones sobre Eden o el teléfono son texto: el usuario las aplica a mano. Si ARI ofrece abrir una pantalla de ajustes de Android, es un botón más que el usuario toca.
- Nada se dispara solo: ni desde Gemma (`HACER:`, `BUSCAR:`) ni desde rutinas automáticas.

### 4.2 Preparación del juego (requisito 2)

Guardar el estado persistente:
- Antes de abrir Eden, GE escribe en SQLite (de forma síncrona, no en la cola asíncrona de `saveMessage`) una fila de sesión con estado `EN_CURSO`, la hora, los datos medidos y lo aprobado. Los mensajes pendientes deben haberse escrito ya.

Liberar Gemma de forma segura, "cuando sea posible":
- No se libera si Gemma está respondiendo (`busy`) ni mientras el modelo está cargando: se avisa y se espera.
- Al liberar: `releaseModel()` más `modelReady = false`, y una marca persistente "descargado para jugar".
- **Depende de la Fase 1 (criterio B):** hoy `onCreate` recarga el modelo siempre que el archivo existe. Para que la marca sirva tras un cierre del proceso, `loadSavedModel` debe respetarla. Eso toca justo el camino que mide B, y además contradice el objetivo documentado de "cargar automáticamente el modelo si existe" (`ARI_ESTADO.md`, objetivo inmediato, punto 6). **Lo decide el usuario.**
- No se promete ninguna cifra de memoria liberada. NO VERIFICADO: cuánta memoria devuelve `Engine.close()` en el POCO y si LiteRT-LM proyecta el modelo en memoria (mmap). Se muestra `availMem` antes y después, más la memoria del propio proceso de ARI, como dato orientativo.

Comprobar recursos (solo con APIs permitidas, desde ARI):
- Memoria del sistema: `ActivityManager.getMemoryInfo` (`availMem`, `threshold`, `lowMemory`). Es global y ruidosa; no es la memoria de Eden.
- Estado térmico: `PowerManager.getCurrentThermalStatus` (API 29) y `getThermalHeadroom` (API 30). Ver 4.4.
- Batería: temperatura (`ACTION_BATTERY_CHANGED`, `EXTRA_TEMPERATURE`, en décimas de grado según el código de AOSP; NO VERIFICADO en el POCO), nivel, si está cargando y si está activo el ahorro de batería (`PowerManager.isPowerSaveMode`).
- Eden instalado y su versión: con `PackageManager`, si el paquete está declarado en `<queries>`.
- Servicios propios activos: burbuja, palabra de activación, modo live.

Lanzar Eden:
- Con `getLaunchIntentForPackage(<paquete de Eden>)`, desde la actividad de ARI visible y como respuesta a un toque. Android bloquea los inicios de actividades desde segundo plano desde Android 10, así que GE no puede abrir Eden desde un servicio ni de forma diferida.
- No se pide `SYSTEM_ALERT_WINDOW` ni se usa la burbuja para saltarse esa restricción (requisito 7).
- Paquete de Eden: la build principal usa `dev.eden.eden_emulator`, pero hay variantes (`.nightly`, Legacy `dev.legacy.eden_emulator`, "Eden Optimized" con `com.miHoYo.Yuanshen`) y forks que reutilizan el mismo identificador. **El usuario confirma cuál tiene instalado.** El paquete no prueba que la build sea la oficial.

### 4.3 Perfiles por juego (requisito 3)

- **Sin API de Eden.** La configuración de Eden vive en su almacenamiento específico (`getExternalFilesDir(null)/config/config.ini` y `config/custom/0<TITLEID>.ini`).
  - Quedan excluidos por política: leerla o escribirla, el intent no documentado `dev.eden.eden_emulator.LAUNCH_WITH_CUSTOM_CONFIG` (sobrescribe el INI completo del juego) y el acceso al proveedor de documentos de Eden (técnicamente quizá posible con permiso del usuario, pero con riesgo de corromper el INI).
  - ARI tampoco usa Accesibilidad ni `MANAGE_EXTERNAL_STORAGE` para tocar Eden.
- Un perfil guarda:
  - el juego: nombre que da el usuario y, si lo sabe, el title ID;
  - las recomendaciones: lista de ajustes que el usuario aplicará a mano en Eden, con el motivo de cada uno;
  - los resultados observados: enlace a las sesiones de historial que usaron ese perfil;
  - el nivel de confianza.
- Los ajustes que se pueden recomendar son los que Eden muestra al usuario: resolución, modo de GPU (Fast/Accurate), modo dock o portátil, límite de velocidad, caché de shaders, shaders asíncronos, precisión de CPU y VSync.
  - Algunas opciones son hacks que el propio proyecto marca como temporales. Por ejemplo, "GPU async emulation" causa fallos gráficos y más cierres. No se presentan como permanentes.
  - **No se recomienda nunca** "Force maximum clocks" ni instalar controladores Turnip (solo aplican a GPU Adreno), ni descargar nada.
- Nivel de confianza:
  - **Baja:** sin sesiones propias, o recomendación genérica.
  - **Media:** 2 o más sesiones con el mismo perfil, la misma versión de Eden y el mismo dispositivo, sin cierres reportados.
  - **Alta:** varias sesiones repetidas y coherentes, con datos medidos y reportados.
  - Un cambio de versión de Eden o de ARI baja la confianza un nivel.
  - Por defecto se parte de confianza **baja**: la propia documentación de Eden avisa de que algunos chips (MediaTek Dimensity antiguos, GPU Mali) rinden mal. NO VERIFICADO: qué chip lleva el POCO X8 Pro Max (se comprueba con `Build.SOC_MODEL` en el dispositivo).

### 4.4 Protección térmica (requisito 4)

Datos permitidos (todos globales del dispositivo, ninguno de Eden):
- `getCurrentThermalStatus` (API 29). La guía oficial avisa de que algunos dispositivos devuelven siempre `NONE`.
- `getThermalHeadroom` (API 30): se consulta como máximo una vez cada 10 segundos, desde un solo hilo. Puede devolver `NaN`, y las primeras lecturas no sirven como previsión.
- `getThermalHeadroomThresholds` (API 35) y el listener de headroom (API 36), protegidos por versión y por `try/catch`.
- Temperatura de la batería.
- **No disponible:** temperatura de CPU o GPU (`HardwarePropertiesManager` solo funciona para el propietario del dispositivo o el servicio de VR). `getCpuHeadroom` y `getGpuHeadroom` (API 36) no atribuyen carga a Eden.

Comportamiento:
- Si la primera lectura es `NaN` o 0, o el estado nunca cambia, ARI dice "La protección térmica no está disponible en este teléfono" y no inventa valores.
- Medidas prudentes que se pueden recomendar:
  - hacer una pausa;
  - bajar la resolución o el límite de velocidad en Eden;
  - usar el modo portátil en lugar del modo dock;
  - no jugar mientras carga;
  - quitar la funda;
  - jugar en un lugar ventilado.
- No se recomienda el "modo batería" del Game Dashboard para Eden: Eden declara `supportsBatteryGameMode="false"`.
- Vigilar durante el juego **no está garantizado**: ARI puede morir mientras Eden está en primer plano. La forma segura es medir antes y después de la sesión. Vigilar en tiempo real necesitaría un servicio en primer plano propio (tipo `specialUse`, con revisión de Play). **Lo decide el usuario.**

### 4.5 Historial (requisito 5)

Una fila por sesión, con el origen de cada dato marcado como **medido** (ARI lo leyó de una API) o **reportado** (lo dijo el usuario):
- Inicio y fin. Fin = cuando el usuario vuelve y cierra la sesión, o el final del uso en primer plano de Eden según `UsageStatsManager`, si el usuario concedió "Acceso a uso".
- Paquete y versión de Eden (`versionName`), versión de ARI y dispositivo.
- Juego y perfil usado.
- Ajustes declarados por el usuario.
- Medidos antes y después: `availMem`, estado térmico, headroom, temperatura de la batería, si Gemma se liberó y si ARI murió durante la sesión (con su propio `ApplicationExitInfo`, leyendo varias entradas y tratando `SIGNALED`/`OTHER` como "causa incierta").
- Reportados: FPS que el usuario lee en el overlay de Eden, cierres, tirones y valoración.

Límites:
- ARI no puede medir FPS, CPU, GPU, RAM ni cierres de Eden: `dumpsys gfxinfo` y la lectura de `ApplicationExitInfo` de otra app exigen `DUMP`, y `getProcessMemoryInfo` solo devuelve el propio proceso.
- Las comparaciones solo se hacen entre sesiones del mismo dispositivo, juego y versión de Eden.

### 4.6 Recuperación (requisito 6)

- No se confía en que Android mantenga vivo a ARI. `onSaveInstanceState` no sobrevive a "Forzar detención" ni a quitar la app de Recientes; solo sobrevive SQLite.
- Al arrancar o volver a ARI, GE busca sesiones `EN_CURSO` huérfanas y:
  - muestra un resumen ("Volviste de Eden");
  - pide el resultado al usuario;
  - **pregunta** "¿Recargar Gemma ahora?" en lugar de recargarla sola, para no provocar un cierre por memoria con Eden aún vivo en segundo plano ni dañar las marcas de GPU;
  - registra si ARI murió y por qué, con la causa marcada como incierta si Android no la informa.
- **Depende de la Fase 1:** del criterio C (que lo escrito en SQLite sobreviva a un cierre forzado en el POCO) y del criterio B (poder arrancar sin cargar Gemma).

### 4.7 Seguridad (requisito 7)

Prohibido en el diseño y en cualquier implementación futura:
- Matar, cerrar o "liberar" procesos de otras apps (`killBackgroundProcesses` solo afecta a la propia app desde Android 14; `FORCE_STOP_PACKAGES` es de sistema).
- Cambiar frecuencias de CPU o GPU, el modo de rendimiento del sistema o el modo de juego de otra app (`GameManager.setGameMode` es API de sistema con `MANAGE_GAME_MODE`).
- Usar `PerformanceHint` o `SustainedPerformanceMode` para Eden (solo sirven para la propia app).
- Activar el modo avión o el ahorro de batería (solo se puede abrir el ajuste).
- Cambiar el No molestar global sin el acceso que concede el usuario.
- Saltarse las restricciones de inicio en segundo plano (nada de `SYSTEM_ALERT_WINDOW` ni trampolines de notificación para abrir Eden).
- Usar Accesibilidad para tocar Eden, o leer o escribir sus archivos (aunque el sabor `completa` declare `MANAGE_EXTERNAL_STORAGE`).
- Usar `QUERY_ALL_PACKAGES`: basta con declarar el paquete de Eden en `<queries>`.
- Leer los logs de Eden sin que el usuario los comparta con "Share debug logs".
- Descargar, distribuir o enlazar juegos, firmware, llaves (`prod.keys`, `title.keys`), controladores o APKs de Eden.
- Copiar código de Eden (licencia GPL-3.0-or-later).

## 5. Datos propuestos (para cuando se programe)

- Migración aditiva de `ari.db` de la versión 4 a la 5, solo con tablas nuevas:
  - `game_profiles`: perfil por juego (juego, title ID opcional, recomendaciones, confianza, fecha).
  - `game_sessions`: sesión (estado `EN_CURSO`/`CERRADA`/`HUERFANA`, inicio, fin, paquete y versión de Eden, versión de ARI, perfil, si Gemma se liberó, causa de muerte de ARI si la hubo).
  - `game_measurements`: mediciones con origen (`medido`/`reportado`), momento (`antes`/`después`) y valor.
- Decidir si estas tablas entran en el respaldo cifrado. Si entran, ampliar el manejo de tipos de `importData`/`exportData`. Tener en cuenta que recuperar un respaldo antiguo las vaciaría.
- Considerar `onDowngrade` en `AriDatabase`: hoy no existe, y un APK de otra rama con `DATABASE_VERSION = 4` haría fallar la apertura de la base.

## 6. Orden de trabajo propuesto

- **GB-0 (ahora):** solo documentación y decisiones. Sin código.
- **Requisito previo:** Fase 1 verificada (A, B y C) y decisiones de la sección 8.
- **GB-1 (hecha en la 0.68.0):** lógica pura y pruebas JVM, sin tocar la interfaz ni el arranque: reglas de confianza, formato de recomendaciones, validación de lecturas térmicas (`NaN`, `NONE`), reconciliación de sesiones huérfanas.
- **GB-2 (hecha en la 0.68.0):** tablas nuevas (migración v4 a v5) y pantalla de solo lectura del estado del teléfono (memoria, térmico, batería).
- **GB-3:** preparación con aprobación (liberar Gemma, apagar servicios propios) y lanzamiento de Eden. Requiere la decisión sobre `loadSavedModel` (sección 4.2).
- **GB-4:** recuperación al volver y registro de resultados.
- Cada etapa: una versión, compilación en verde con `android.yml` y prueba física en el POCO registrada antes de la siguiente.

## 7. Riesgos

- Ninguna cifra de mejora se puede prometer: ni GB liberados ni FPS ganados.
- Las APIs térmicas pueden no funcionar en el POCO con HyperOS (`NaN` o `NONE` permanentes). NO VERIFICADO.
- HyperOS puede matar a ARI en segundo plano de formas no documentadas: solo hay fuentes comunitarias, y de MIUI.
- Recargar Gemma con poca memoria puede degradar el motor para siempre (marcas de GPU).
- Eden puede morir por falta de memoria si Gemma sigue cargada. La propia documentación de Eden pide al menos 8 GB de RAM, y Gemma ocupa una parte importante de los 11.8 GB del POCO (cifra real NO VERIFICADA).
- Identificar la build oficial de Eden no es fiable solo por el nombre de paquete.
- Estado legal de Eden: hay noticias de un aviso DMCA de Nintendo en 2026 que no se pudieron verificar con fuentes primarias. Según su propia documentación, Eden no está en Google Play ni en F-Droid.
- Si ARI se publica en Google Play, un servicio en primer plano `specialUse` y la palabra "boost" requerirían revisión.

## 8. Decisiones pendientes del usuario

1. ¿Game Boost es solo de la ARI del dueño (sabor `completa`) o también de la Ari para otros y de ARI Trader?
2. ¿Se permite que, tras "preparar juego", ARI arranque **sin** recargar Gemma hasta que el usuario lo pida? Cambia el comportamiento documentado de carga automática y toca el criterio B.
3. ¿Qué variante y paquete de Eden tiene instalados en el POCO (y su versión)?
4. ¿Vigilancia térmica durante el juego (servicio en primer plano) o solo mediciones antes y después?
5. ¿Concede "Acceso a uso" para registrar la duración de las sesiones? El diseño funciona sin él.
6. ¿Las tablas de Game Boost entran en el respaldo cifrado?
7. ¿Se mantiene el nombre "Game Boost" de cara al usuario o se usa "Preparar juego"?
8. ¿Se escribe el texto oficial de los criterios A, B y C de la Fase 1 en `docs/ARI_ESTADO.md`? Hoy solo está en el documento de traspaso.

Respuestas del usuario (tablero, ficha "game-boost-decisiones", 16/10/2026 20:00 UTC): acepta las recomendaciones.
1. Solo en la ARI del dueño (sabor `completa`).
2. Sí: tras "preparar juego", ARI no recarga Gemma mientras haya una sesión de juego abierta (se programa en GB-3).
3. Eden 1.0.0. El paquete no se confirmó: ARI buscará los conocidos (`dev.eden.eden_emulator`, `.nightly`, `dev.legacy.eden_emulator`) y mostrará cuál encontró. Se excluye `com.miHoYo.Yuanshen` para no confundir Eden con el juego real.
4. Solo mediciones antes y después; sin servicio en primer plano.
5. Sin "Acceso a uso".
6. Sí, las tablas de Game Boost entran en el respaldo cifrado.
7. Nombre: "Game Boost" (lo eligió el usuario; ARI no se publica en Google Play, y la interfaz no promete cifras de rendimiento).
8. Hecho: los criterios están en `docs/ARI_ESTADO.md`.

## 9. Fuentes principales

Android (developer.android.com, consultado el 16/10/2026):
- `PowerManager` (estado térmico, headroom, umbrales y listener): /reference/android/os/PowerManager
- Guía térmica de ADPF: /games/optimize/adpf/thermal
- `SystemHealthManager` (headroom de CPU y GPU): /reference/android/os/health/SystemHealthManager
- Inicio de actividades en segundo plano: /guide/components/activities/background-starts
- Gestión de memoria: /topic/performance/memory-management
- Cambios de comportamiento de Android 14 (`killBackgroundProcesses` y la cita sobre apps de terceros).

Eden:
- Espejo oficial `github.com/eden-emulator/mirror` @ d8c6c59. `docs/user/ThirdParty.md:14` lo declara oficial. Los sitios `eden-emu.dev` y `git.eden-emu.dev` no se pudieron abrir desde el entorno.
- Archivos citados del espejo: `docs/user/Architectures.md`, `src/android/app/src/main/AndroidManifest.xml`, `DocumentProvider.kt`, `ForegroundService.kt`, `MemoryUtil.kt` y `res/xml/game_mode_config.xml`.

ARI:
- Rama `ccr-e45f2eee-wkjpdl` @ 9cbcaa3: `MainActivity.kt`, `AriDatabase.kt`, `AndroidPhone.kt`, `AriUsage.kt`, `FailureReport.kt`, `AndroidManifest.xml`.
