# ARI — Documento de traspaso (handoff)

- Fecha de la auditoría: 15-16 de octubre de 2026 (última comprobación: 16/10/2026 03:08 UTC).
- Tipo de sesión: auditoría de solo lectura. No se cambió código, no se hizo commit, push, fusión ni compilación nueva.
- Preparado en una copia de `main` (00527d1) y guardado en la rama activa `ccr-e45f2eee-wkjpdl` con autorización del usuario (16/10/2026). Solo se añadió este archivo; no se cambió código.
- Para qué sirve: que cualquier sesión (Claude, ChatGPT u otra) retome ARI sin depender del historial de conversaciones.
- Relación con otros documentos: este archivo es una foto verificada del estado. La bitácora completa, versión por versión, es `docs/ARI_ESTADO.md` **de la rama activa** (`ccr-e45f2eee-wkjpdl`, 813 líneas). El `ARI_ESTADO.md` de `main` (97 líneas) está desactualizado. No se duplicó esa bitácora aquí.

Convención: cada dato lleva su fuente (ruta:línea@rama, SHA, id de run de GitHub Actions o documento). "Confirmado por el usuario" significa que hay un registro escrito de que el dueño lo probó en el teléfono; si no, se dice "solo código" o "sin verificar".

## 1. Resumen

- El desarrollo real está en la rama `ccr-e45f2eee-wkjpdl` @ `9cbcaa3` (versión 0.66.0, versionCode 80). Tiene 154 commits por delante de `main` y no tiene PR. `main` sigue en la 0.1.0 (00527d1).
- El motor de IA es **LiteRT-LM con modelos .litertlm** (Gemma 4 E4B/E2B, Qwen). No hay GGUF ni llama.cpp dentro de la app: los `.gguf` se rechazan a propósito (`ModelFamily.kt:28-38`, commit 3f621fa).
- La memoria persistente es SQLite (`ari.db`, versión 4, WAL, 5 tablas). No existe una separación "episódica/semántica" en el código; la correspondencia de la sección 6 es una interpretación.
- AriCore (en Android), ModelManager, ResourceCoordinator, GameOrchestrator y GE **no existen** en ninguna rama. Hay equivalentes parciales (sección 2.4).
- Fase 1: **A PENDIENTE** (compila sin errores y solo arm64-v8a, falta definir "limpia"), **B PENDIENTE** y **C PENDIENTE** (sin pruebas físicas registradas).
- No se debe avanzar a la Fase 2.

## 2. Objetivo y arquitectura

### 2.1 Objetivo declarado por el usuario en la sesión de auditoría (15/10/2026)

- Asistente de IA local para Android, con mascota virtual inteligente, personalidad, conversación, memoria persistente SQLite e inferencia local con GGUF y llama.cpp.
- GE: orquestador independiente para aplicaciones y emuladores compatibles.
- Componentes objetivo: AriCore, ModelManager, ResourceCoordinator y GameOrchestrator.
- Prioridades: funcionamiento local, cambios mínimos, proteger el código existente, copias de seguridad antes de cambios importantes y documentar cada sesión.

### 2.1 bis. Propuesta de diseño "ARI Game Boost" (añadida por el usuario el 16/10/2026)

- Es un módulo de optimización asistida para juegos y emuladores externos (por ejemplo, Eden). ARI recomienda, GE coordina la preparación y el lanzamiento, y Eden sigue siendo una app externa.
- Estado: **solo propuesta de diseño, sin código**. El documento de diseño (`docs/ARI_GAME_BOOST_DISENO.md`) **todavía no existe**: la investigación previa seguía en curso al guardar este archivo.
- No se programa nada de Game Boost hasta que la Fase 1 (A, B y C) esté verificada.

### 2.2 Objetivo según la documentación del repositorio

- "Ari es un asistente Android local que utiliza Gemma 4 E4B-it mediante LiteRT-LM" (`docs/ARI_ESTADO.md:4`@rama activa).
- Privado y sin APIs de pago. Se decidió después usar internet de forma limitada (descarga del modelo, y en la ARI del dueño búsqueda web, actualización e informes; ver sección 4).
- El objetivo declarado en 2.1 **no coincide** con el repositorio en tres puntos: motor (GGUF/llama.cpp frente a LiteRT-LM), componentes con esos nombres y GE. Ver la sección 5 (decisiones pendientes).

### 2.3 Arquitectura real (rama activa `ccr-e45f2eee-wkjpdl` @ 9cbcaa3)

- Módulos de Gradle: `:app` y `:estado` (`settings.gradle.kts:18-19`).
- Sabores de `:app` (`app/build.gradle.kts:72-90`):
  - `completa`: la "ARI CEO" del dueño, `com.ari.assistant`. Solo abre en el teléfono registrado en `app/src/completa/res/values/ceo.xml` (DeviceLock).
  - `otros`: Ari comercial, `com.ari.asistente`.
  - `trader`: ARI Trader, `com.ari.trader`.
- `:estado`: app aparte "Ari Estado" (`com.ari.estado`, 0.3.0), un widget que muestra lo que hace Claude.
- Herramientas de compilación: AGP 8.13.2, Kotlin 2.4.21, Gradle 8.13, compileSdk 36, targetSdk 35, minSdk 26, Java 21.
- ABI: `ndk { abiFilters += listOf("arm64-v8a") }` (`app/build.gradle.kts:63-66`), introducido en 0.14.0 (commit 4ff25ea).
- Inferencia: `com.google.ai.edge.litertlm:litertlm-android:latest.release` (`app/build.gradle.kts:94`). La versión **no está fijada**; la documentación dice que una vez resolvió 0.18.0 (`ARI_ESTADO.md:15`), pero no se sabe qué versión usó el último build.
- Motor: `startFastestEngine` intenta GPU (con contexto grande de 8192 tokens y luego normal, con y sin visión) y después CPU, con marcas "probando/falla" en preferencias para no repetir un fallo de GPU (`MainActivity.kt:2807-2875`).
- Reconocimiento de voz y huella de voz: sherpa-onnx 1.13.8 (AAR descargado en CI con sha256 comprobado).
- Orquestación interna: `agents/AriCeo` pasa cada mensaje al primer agente que lo entiende (reloj, recordatorios, memoria, personas, calendario, teléfono, mensajes; `TeamAgent` solo en `completa`). Si ninguno lo entiende, va al modelo.
- Persistencia: `AriDatabase.kt` (SQLiteOpenHelper, `ari.db`, `DATABASE_VERSION = 4`, WAL). Tablas: `messages`, `memories`, `agent_tasks` (v2), `reminders` (v3), `people` (v4). `onUpgrade` solo crea tablas; no hay DROP ni borrados al arrancar.
- Tamaño: unas 18 000 líneas de Kotlin de app y 2 967 de pruebas (55 archivos de prueba JVM, unos 264 `@Test`). `MainActivity.kt` tiene 5 036 líneas.
- Conexiones a internet (el código está en los tres sabores, pero se activa por sabor en tiempo de ejecución):
  - Todos los sabores: descarga del modelo desde huggingface.co.
  - Solo `completa`: AriWeb (Wikipedia, Open-Meteo, DuckDuckGo), incluida la búsqueda automática cuando el modelo escribe "BUSCAR:" (0.66.0); AriUpdate y AriGitHub con la llave del usuario.
  - Opcional, apagado hasta aceptar: AutoReport (informes de fallo a un Worker de Cloudflare).
  - `trader`: TraderUpdate y TraderProposals.
  - `TeamAgent` (`completa`): HTTP en red local hacia Ollama, LM Studio o llama-server.
- Otras ramas:
  - `ccr-3d48bcea-ddppo3` @ 8789351: app iOS en Swift (LiteRT-LM 0.18.0 fijado), con un paquete Swift llamado `AriCore` (lógica de perfil, memoria y modelos; guarda en JSON, no en SQLite). CI de iOS en verde (run 38642065236); sin probar en un iPhone real.
  - `feature/ari-memory` @ 8b52757: intento de Room "FASE 1", incompleto (referencia clases que no existen) y sin CI. Abandonado de hecho; no hay decisión escrita.
  - `termux-codigo-corregido` @ 1edc272 (PR #1 abierto): código Node.js para Termux contra llama-server local. Incompleto (faltan `server.js` y `package.json`), sin CI, y ejecuta comandos de shell con "ejecuta:/cmd:/terminal:".
  - `core/` y `memory/` (Python): prototipo inicial (commit ccba356), sin uso.

### 2.4 Componentes objetivo: qué existe de verdad

- AriCore: **no existe en Android**. Solo existe con ese nombre en la rama iOS, como paquete de lógica; no es un núcleo orquestador.
- ModelManager: **no existe** con ese nombre. Equivalente parcial: `ModelChoice` (catálogo y recomendación por RAM y espacio), `ModelDownload`, `ModelFamily` y las funciones de carga de `MainActivity`.
- ResourceCoordinator: **no existe**. Equivalente mínimo: `ModelChoice.recommend/basic` (menos de 7 GiB de RAM, sin visión) y las marcas de fallo de GPU. No hay `onTrimMemory` ni coordinación con otras apps.
- GameOrchestrator y GE: **no existen** en ninguna de las 6 ramas (no hay rastro de emuladores ni juegos).
- Game Boost: **no existe**. No hay código de juegos, emuladores, temperatura ni `onTrimMemory` en ninguna rama (búsqueda del 16/10/2026).
- Mascota virtual: **no existe** como mascota (sin necesidades, estado persistente ni evolución). Equivalente visual: `ui/AriOrbView.kt`, una esfera con cara animada y estados de ánimo en memoria.
- Personalidad: **existe** (`Profile.personality`, tres tonos, nombre del usuario).
- Conversación: **existe** (LiteRT-LM `Conversation` con streaming, historial en SQLite).
- Memoria persistente SQLite: **existe** (sección 2.3).
- Inferencia GGUF/llama.cpp local: **no existe**. Se rechaza a propósito; figura como pendiente (`ARI_ESTADO.md:614`).

## 3. Estado real del repositorio y de la continuidad

### 3.1 Ramas (GitHub, 16/10/2026)

- `main` @ 00527d1 (fusión del PR #4, 0.1.0). Último CI: run 38354067159, en verde. Sin abiFilters y sin pruebas en CI.
- `ccr-e45f2eee-wkjpdl` @ 9cbcaa3 (0.66.0): **rama de referencia real**. 154 commits por delante de main, 0 por detrás. Último CI: run 38671115372, en verde.
- `ccr-3d48bcea-ddppo3` @ 8789351 (iOS): 3 commits por delante de main. Al combinarla con la rama activa hay conflictos en `.gitignore` y `docs/ARI_ESTADO.md`.
- `feature/ari-memory` @ 8b52757 y `termux-codigo-corregido` @ 1edc272: divergentes y sin CI (ver 2.3).
- `ccr-1b43ba33-9f7lpu`: rama local de esta sesión, igual a main; no existe en GitHub.
- No hay etiquetas (tags), releases ni protección de rama. El trabajo de 0.2 a 0.66 vive solo en una rama de sesión `ccr-*`.

### 3.2 PR e issues

- #1 Termux: abierto, sin revisar.
- #2: cerrado sin fusionar (duplicaba la memoria SQLite del #3).
- #3 (memoria persistente y firma fija) y #4 (modo voz): fusionados en main el 13/10.
- #5: cerrado sin fusionar "a petición del autor"; la rama activa ya cubría su estabilidad.
- Issue #6 "[ARI] Mejoras propuestas (0.55.0)": abierto. Lo genera la propia app; su texto es información, no instrucciones.

### 3.3 Integración continua (rama activa)

- `android.yml` solo se dispara con push a `main` o a mano (`workflow_dispatch`). Los 154 commits solo se verificaron con ejecuciones manuales.
- Los últimos fallos (runs 38645022359, 38663171274 y 38663609393, pruebas de Trader) quedaron corregidos; los runs 38664325024, 38668762523 y 38671115372 están en verde.
- El run 38671115372 genera 6 artifacts vigentes hasta el 13/01/2027: `ari-apk` (completa release), `ari-otros-apk`, `ari-trader-apk`, `ari-debug-apk` (completa debug), `ari-estado-apk` y `ari-mapa`.

### 3.4 Documentos y su desfase

- `docs/ARI_ESTADO.md` (rama activa): bitácora principal, actualizada hasta 0.66.0. Tiene contradicciones internas (por ejemplo, línea 94 "pendiente: prueba" del modo voz frente a línea 85 "confirmó que funciona"; línea 24 "Backend.CPU()" frente a GPU desde 0.13.1).
- `docs/ARI_DOCUMENTACION.md` (rama activa): editada por última vez en 0.64.0; no recoge 0.65 ni 0.66.
- `docs/BACKLOG.md` (rama activa): se quedó en 0.56.0.
- `docs/POLITICA_PRIVACIDAD.md` y `docs/GOOGLE_PLAY.md` dicen que la única conexión es la descarga del modelo. Eso ya no es cierto (búsqueda web, actualización, informes). Es un riesgo si se publica.
- En `main`: `ARI_ESTADO.md`, `ARI_DOCUMENTACION.md` (que dice que el PR #4 está "abierto", aunque se fusionó) y `CLAUDE.md` están muy desactualizados.
- `CLAUDE.md` de `main` pide exportar a Drive en cada cambio; el de la rama activa (pedido del usuario del 15/10) pide hacerlo cada 2-3 versiones y añade el tablero, el widget Ari Estado, las fichas [ARI] y los informes de fallo. **Hay que seguir el `CLAUDE.md` de la rama en la que se trabaje.**

### 3.5 Herramientas de continuidad fuera del repositorio (consultadas en solo lectura)

- Tablero "Pendientes de Ari" (https://claude.ai/artifact/7kCcA7Ppg1tGW1HDATrfyB), 53 asuntos:
  - `estado/actual` sigue con `activo: true` y "Compilando ARI 0.65" desde el 15/10 02:00 UTC. Está obsoleto: la 0.66 ya existe y no hay ninguna prueba de la 0.65 ni de la 0.66.
  - Última versión confirmada en el teléfono: 0.55 ("probar-055", "Todo bien", 14/10). Las pruebas 0.56, 0.57, 0.58 y 0.60 quedaron en "Probaremos en 0.64", y "probar-064" sigue pendiente.
  - Fallos que reportó el dueño y que no constan como resueltos: "tarea-11" Manejar mis apps ("No pude activarlo… No ejecuta bien"), "tarea-14" ajustes por voz ("Falla mucho"), "tarea-15" agendar cita ("Dice que agendo pero no veo nada en mi calendario"), "probar-0150" 0.26.0 ("No se instaló") e "instalar-ari-estado" ("Encontré un problema").
  - Sin responder: "laptop-linux", "probar-recados", "tarea-17" y "probar-064".
  - "tarea-19" (respaldo cifrado de la memoria): "Hecho" el 14/10. No se sabe dónde quedó el archivo.
- Tarea programada "Relevo de Claude para ARI" (`trig_01U44nT45SYEeYexGZCEJriJ`): todos los días a las 8:52 (hora de CDMX), sobre la rama activa. Su instrucción es subir la versión, hacer commit y push, compilar y cerrar issues sin PR. La última ejecución (15/10 14:53 UTC) **falló**: GitHub devolvió 403 porque el repositorio no estaba adjunto a esa sesión. No hizo nada y mandó un aviso al teléfono. Sigue activada (próxima: 16/10 14:52 UTC). Si alguien le da acceso a GitHub, empezará a cambiar la rama activa sin supervisión. **Vuelve a traer la rama antes de trabajar.**
- Google Drive, carpeta "Ari - Documentación de Claude": la última exportación de la documentación y de la guía es del 14/10 hacia las 21:12 UTC. Corresponde aproximadamente a la 0.58; la 0.59 a la 0.66 no están en Drive.
- Informes de fallo por Gmail (últimos 14 días), dos:
  - 0.36.0 en el teléfono del dueño (Xiaomi 2602BPC18G, Android 16 / API 36, 11.8 GB de RAM, 13/10 21:46 CST): "Ari se cerró por un error del motor (por ejemplo, la GPU)", "Modelo: elegido a mano o sin modelo", "GPU: falla", "Android dice: crash". `ARI_ESTADO.md:676` lo anota como atendido.
  - 0.23.0-otros, del teléfono de un tester (también un fallo del motor).
  - El repositorio `ari-informes`, al que van los informes automáticos desde la 0.57, no fue accesible en esta sesión: no se sabe si hay informes más recientes.

## 4. Decisiones confirmadas por el usuario

Fuente principal: `docs/ARI_ESTADO.md`@rama activa (número de línea) y el tablero.

- Motor local con Gemma 4 mediante LiteRT-LM, sin APIs de pago (`:4`).
- Internet solo para descargar el modelo (tablero "descarga-modelo", 13/10). Se amplió después para la ARI del dueño: búsqueda web (0.51, 0.64, 0.66), actualización desde GitHub e informes automáticos con permiso (0.57, `:696`).
- Interfaz parecida a Claude, color hielo (tablero "estilo-interfaz"); "carita" en forma de esfera más elaborada (tablero "diseno-caricatura").
- Personalidad franca, con alguna grosería (tablero "personalidad", aplicado en 0.3.1).
- Dos y luego tres versiones del APK con productFlavors (`:424-428`, `:764`); app comercial gratis + Premium (`:642`); publicar en Google Play más adelante (tablero "google-play": "Probemos bien todo").
- Proteger el código: R8, AppIntegrity y licencia (`:456-460`); ARI completa exclusiva del teléfono del dueño con DeviceLock (`:488-491`).
- Control de apps: primero "solo acciones seguras" y después etapas 1 y 3 con lista de funciones por app (tablero "control-total" y "control-etapas"); "mis apps son sus herramientas" con confirmación para mensajes, llamadas y citas (`:723`).
- Que ARI responda a su nombre con un modelo de voz local (tablero "palabra-ari") y que reconozca voces (tablero "reconocer-voces").
- Trabajo con el usuario: el tablero para decisiones y pruebas, ahorrar tokens, exportar a Drive cada 2-3 versiones (`CLAUDE.md`@rama activa, 15/10).

## 5. Cuestiones pendientes (decisiones que solo puede tomar el usuario)

1. Rama de referencia para Fase 1: ¿se evalúa la rama activa (0.66.0) o `main` (0.1.0)? ¿Se fusiona la rama activa en `main`?
2. Motor: ¿se acepta LiteRT-LM (.litertlm) como motor válido de Fase 1, o GGUF/llama.cpp es obligatorio? Hoy la app rechaza los `.gguf` a propósito.
3. Criterio A: ¿"compilación limpia" significa "sin errores" o también "sin advertencias"?
4. Criterio B: ¿"sin cargar el modelo" significa "la interfaz no espera al modelo" (el diseño lo cumple) o "poder abrir la app sin cargar el modelo aunque el archivo exista" (no existe; haría falta código nuevo)?
5. Criterio C: ¿qué es "memoria episódica" y "semántica"? Propuesta: episódica = `messages` (más `agent_tasks` y `reminders`); semántica = `memories` (incluye "(Aprendido)") y `people`. Si "semántica" significa búsqueda por significado (embeddings), eso no existe y haría falta código nuevo.
6. Arquitectura objetivo: ¿dónde viven GE y GameOrchestrator (otra app, otro repositorio, un módulo nuevo)? ¿Se renombran o envuelven los equivalentes actuales como AriCore/ModelManager/ResourceCoordinator, o solo son nombres de diseño?
7. Funcionamiento local: ¿es aceptable la búsqueda automática "BUSCAR:" de la 0.66.0, que envía consultas redactadas por el modelo a DuckDuckGo/Wikipedia sin confirmar cada una?
8. Seguridad de firma: ¿crear una clave de publicación propia, fuera del repositorio, antes de distribuir la Ari comercial o ARI Trader? (sección 8).
9. Ramas viejas: ¿archivar o cerrar el PR #1 (Termux) y `feature/ari-memory` (Room)? ¿Se integra la rama iOS?
10. Respaldo del repositorio: ¿autoriza crear una etiqueta (por ejemplo `respaldo-0.66.0` sobre 9cbcaa3) antes de cualquier cambio de Fase 1?

## 6. Fase 1: estado de los criterios A, B y C

Recordatorio: una compilación en verde no demuestra ninguna prueba física en el teléfono. No hay ningún registro de que la 0.66.0 esté instalada en el POCO X8 Pro Max. La última versión confirmada en el tablero es la 0.55 ("probar-055", "Todo bien", 14/10); la prueba de la 0.64 sigue pendiente.

### A. Compilación limpia para arm64-v8a: PENDIENTE (casi cumplido)

Evidencia a favor (rama activa @ 9cbcaa3):
- Run 38671115372 (workflow_dispatch, job 116214221591): pruebas unitarias de los 3 sabores y compilación con "BUILD SUCCESSFUL", sin ninguna línea de error ni FAILED.
- `aapt2` en CI informa `native-code: 'arm64-v8a'` en completa-release, otros-release y completa-debug (firma v2 y zipalign correctos).
- `abiFilters` arm64-v8a en `app/build.gradle.kts:63-66`. La JNI de LiteRT-LM (`liblitertlm_jni.so`) va empaquetada.

Por qué no se da por aprobado:
- "Limpia" no está definida. El build tiene 448 avisos de R8 "An error occurred when parsing kotlin metadata" (Kotlin 2.4.21 es más nuevo que el R8 de AGP 8.13.2) en las tres versiones release, 5 advertencias únicas de Kotlin (30 líneas `w:`), 2 avisos de Gradle 8.13 deprecado y avisos de Actions (Node 20).
- LiteRT-LM está en `latest.release`: el mismo commit puede compilar otro motor otro día, y no se sabe qué versión resolvió este run.
- El APK de ARI Trader no pasa por la revisión de ABI en CI, y el paso "Revisar APK" no puede fallar (usa `|| true`).
- No se pudo inspeccionar el APK por fuera: la descarga del artifact fue bloqueada por el proxy (403).
- En `main` no se cumple: no hay abiFilters y CI solo corre `assembleDebug`.

Para cerrarlo: el usuario define "limpia" (si es "sin errores", A pasa a APROBADO para 9cbcaa3); o se eliminan los avisos (alinear Kotlin con el R8 del AGP), se fija la versión de LiteRT-LM y se relanza en verde.

### B. Arranque en frío de la interfaz sin cargar el modelo: PENDIENTE

Lo que hace el código (rama activa):
- La interfaz se construye en el hilo principal antes de tocar el modelo; el historial (30 mensajes) se muestra antes de la carga (`MainActivity.kt:232`, `:254`).
- `loadSavedModel` (`:2467-2480`): sin archivo de modelo no carga nada y muestra "Toca Modelo para descargar a Gemma…"; con archivo, manda la carga a un executor en segundo plano.
- **Con el modelo presente, la carga empieza siempre en cada arranque en frío.** No existe ajuste, bandera ni botón para abrir sin cargarlo. El literal "sin cargar el modelo" solo se cumple si no hay archivo.
- Los fallos de Kotlin y OutOfMemoryError de la carga se capturan (`:2925`). Un cierre nativo (GPU) o por memoria del sistema mata todo el proceso, interfaz incluida. En CPU no hay marca de protección.
- No se encontró trabajo pesado en el hilo principal (solo lecturas pequeñas de SQLite); no se ha medido.
- Mientras el modelo no está listo, un mensaje libre no se responde ni se guarda: solo cambia la barra de estado (`:3061-3071`). Los agentes locales (hora, recordatorios, recuerdos, personas) sí responden.

Evidencia física: ninguna. No consta un arranque sin modelo en el POCO ni una medida del arranque en frío. `ARI_ESTADO.md:74-75` sigue con "Pendiente de verificar: … carga automática del modelo".

Sobre GGUF: el criterio menciona GGUF, pero el modelo real es `.litertlm` (`files/gemma-model.litertlm` o el descargado en `Android/data/…/files/modelos/`).

No está BLOQUEADO: se puede probar con `ari-otros-apk` (otro paquete, sin modelo) o renombrando el modelo si está en el almacenamiento externo de la app. Si el modelo está en el almacenamiento interno, la única forma no destructiva de abrir la ARI completa sin cargarlo sería un cambio de código (por ejemplo, un "modo seguro"). Indicio sin verificar en la versión actual: el informe de la 0.36.0 dice "Modelo: elegido a mano", o sea, en el almacenamiento interno (`files/gemma-model.litertlm`).

### C. Persistencia de SQLite tras forzar el cierre (POCO X8 Pro Max), memoria episódica y semántica: PENDIENTE

Lo que hace el código (rama activa):
- Una sola base `ari.db` (v4, WAL). Cada INSERT es autocommit. No hay migraciones destructivas ni borrados al arrancar.
- Mensajes: se guardan en un hilo de fondo (`saveMessage`, executor `storage`), y los errores se ignoran sin avisar (`:4038-4046`). Un "Forzar detención" justo después de enviar podría perder lo que aún esté en cola (milisegundos).
- La respuesta de Gemma se guarda solo al terminar (`onDone`/`onError`): forzar el cierre a mitad de una respuesta pierde la respuesta parcial y lo "(Aprendido)" en ella. La pregunta sí queda.
- Los comandos con "/" (`/recuerda`, `/memoria`) **no** se guardan en el historial; el recuerdo sí se guarda.
- "recuerda que …" en lenguaje natural pasa por MemoryAgent, guarda el recuerdo y los dos mensajes, aunque el modelo no esté cargado.
- Pasar de `main` (ari.db v1) a la rama activa conserva los datos según el código: mismo applicationId, misma clave de firma, versionCode 1 → 80 y `onUpgrade` solo crea tablas.
- No hay pruebas automáticas de `AriDatabase` ni de sus migraciones (las pruebas usan FakeStore y no hay androidTest).
- El código no usa los términos "episódica" ni "semántica" y no tiene búsqueda por significado: todos los recuerdos y hasta 25 personas van en las instrucciones de sistema de Gemma.

Evidencia física:
- No hay ninguna prueba registrada de forzar el cierre y reabrir (ni en docs, ni en PR, ni en el tablero). `ARI_ESTADO.md:74-75` sigue pendiente.
- Lo más cercano está en el tablero: "tarea-06" y "tarea-08" ("Recuerda que…" y revisar en Memoria), respondidas "Hecho" el 14/10, en la misma sesión y sin cierre forzado. La prueba de "Mi gente" (people) quedó aplazada ("Probaremos en 0.64").

No está BLOQUEADO: nada impide la prueba. Pasaría a BLOQUEADO solo si el usuario exige memoria semántica con embeddings.

## 7. Protocolo de prueba física segura (B y C)

Reglas antes de empezar:
- No desinstalar ARI, no usar "Borrar datos" ni `pm clear`, no borrar ni mover el modelo. Desinstalar borra `ari.db` y el modelo (unos 3.7 GB), y `allowBackup="false"` impide que Android lo restaure.
- Hacer primero el respaldo cifrado: Memoria → Respaldo → Guardar un respaldo cifrado. Guardar el archivo fuera del teléfono y anotar la contraseña.
- No usar "Recuperar" durante la prueba: reemplaza todas las tablas.
- **No forzar la detención mientras el modelo carga.** Antes de tocar la GPU, la app guarda una marca "probando" (en cada arranque). Si el proceso muere ahí, al reabrir pasa una de dos cosas, según el estado de las preferencias `ari_motor`: pierde para siempre la memoria grande de 8192 tokens, o marca la GPU como "falla" y pasa a CPU. Ajustes no recupera la memoria grande (`MainActivity.kt:2811-2814`, `:2836`, `:2850`, `:1921-1926`). Esperar siempre a "… Ari está lista". Si la GPU ya está en "falla" (como en el informe de la 0.36.0), la carga va por CPU y este riesgo no aplica.
- Anotar en cada paso: fecha y hora, versión (Ajustes → Acerca de Ari), con o sin modelo, GPU o CPU, y capturas.

Paso 0. Versión: anotar la versión instalada. Para evaluar 9cbcaa3, instalar encima (sin desinstalar) `ari-apk` del run 38671115372 y comprobar que dice 0.66.0.

Prueba C (ARI completa, `com.ari.assistant`):
1. Punto de partida: en Memoria, anotar cuántos recuerdos y cuántas personas ("Mi gente") hay.
2. Esperar a que el modelo esté cargado ("Ari está lista").
3. Semántica (recuerdos): escribir exactamente `recuerda que mi palabra de prueba es PRUEBAC1610` (sin "/").
4. Semántica (personas): Memoria → Mi gente → Agregar persona: nombre "Pruebac", vecino, dato "prueba C".
5. Episódica (historial): escribir `Prueba C: contesta solo con la palabra listo` y esperar a que termine la respuesta.
6. Esperar unos 10 segundos.
7. Forzar la detención: Ajustes → Aplicaciones → ARI → Forzar detención (o `adb shell am force-stop com.ari.assistant`).
8. Reabrir ARI y comprobar:
   - Historial: aparecen los mensajes de los pasos 3 y 5.
   - Memoria: aparece "mi palabra de prueba es PRUEBAC1610", y "Mi gente" incluye a Pruebac.
   - "qué recuerdas de mí" incluye el recuerdo.
9. Opcional: repetir 7-8 tras reiniciar el teléfono.
10. Opcional C2 (caso límite): pedir una respuesta larga y forzar la detención mientras escribe (con el modelo ya cargado). Lo esperado según el código: la pregunta queda y la respuesta parcial no. Anotar lo que pase.
11. Limpieza: "olvida PRUEBAC1610" y Mi gente → Pruebac → Olvidar.
12. Opcional y solo con permiso explícito: leer `ari.db` con `run-as` instalando encima `ari-debug-apk` del mismo run (la release no es depurable), sin escribir nunca en la base del teléfono, y volver después a `ari-apk`.

Prueba B:
1. Con modelo (ARI completa): con la app ya cargada, forzar la detención y abrirla. Medir el tiempo hasta que aparece el chat y hasta "Ari está lista" (con cable: `adb shell am start -W -n com.ari.assistant/.MainActivity` y anotar LaunchState, TotalTime y WaitTime). Mientras dice "Cargando el modelo guardado…", escribir "qué hora es": debe responder sin el modelo. Revisar si hubo cierres (`adb logcat -d -b crash`, `adb shell dumpsys activity exit-info com.ari.assistant`). Repetir tres veces. Esto prueba que la interfaz no espera al modelo, no que no lo cargue.
2. Sin modelo, sin tocar los datos de ARI: instalar `ari-otros-apk` del mismo run (paquete `com.ari.asistente`, datos separados). Abrirla, tocar "Más tarde" en la descarga y no descargar nada. La barra debe decir "Cuando quieras, toca Modelo para descargar a Gemma…". Comprobar que "qué hora es" responde. Forzar la detención, reabrir y medir igual. Después se puede desinstalar solo esa app. Ojo: el sabor `otros` no tiene DeviceLock ni el descubrimiento de servidores de `completa`, así que no es idéntico.
3. Registrar los resultados en `docs/ARI_ESTADO.md` de la rama activa, cerrando la línea 75 "Pendiente de verificar".

## 8. Riesgos conocidos

- Pérdida del trabajo: los 154 commits de 0.2 a 0.66 solo existen en una rama de sesión `ccr-*`, sin etiqueta ni release. Además, el actualizador de la app (`AriUpdate.kt:66-68`) busca compilaciones por el nombre de esa rama.
- Firma: las versiones release se firman con `app/debug.keystore`, versionada en el repositorio con contraseñas en texto plano en `app/build.gradle.kts`, y AppIntegrity usa esa huella como "oficial". Cualquiera con acceso de lectura al repositorio puede firmar un APK que se instale encima de la ARI del dueño. Esto contradice `ARI_ESTADO.md:81` y `GOOGLE_PLAY.md:28`. Hoy el repositorio es privado. AppIntegrity además da la copia por buena si Android no devuelve la firma, si la lista de firmantes está vacía o si la comprobación falla (`AppIntegrity.kt:24`, `:32-33`: `?: return true`, `list.isEmpty() || …` y `getOrDefault(true)`).
- Datos accesibles por cable: el comentario de `app/build.gradle.kts:35-36` dice que nadie puede leer los datos de Ari con un cable, pero cada run publica `ari-debug-apk`, que es depurable y usa el mismo paquete, la misma clave y el mismo versionCode. Instalado encima, permitiría `run-as` (comportamiento estándar de Android; no probado en el POCO).
- Reproducibilidad: LiteRT-LM en `latest.release`.
- R8 frente a Kotlin: 448 avisos de metadatos en las versiones que se instalan; un fallo así solo se vería en el teléfono.
- Gradle 8.13 deprecado para el plugin de Kotlin: subir a Kotlin 2.5.0 exigirá Gradle 8.14.4 o superior.
- Arranque: no hay modo seguro para saltarse la carga del modelo; un cierre nativo reproducible en CPU dejaría la app cerrándose en cada arranque.
- Datos: los errores de escritura de mensajes se ignoran sin aviso; no hay pruebas de la base ni de sus migraciones; no hay `onDowngrade` (instalar una versión con base más vieja haría fallar la apertura).
- Privacidad y cumplimiento: la búsqueda automática "BUSCAR:" envía consultas redactadas por el modelo sin confirmación; la política de privacidad no lo refleja. `docs/ARI_ESTADO.md:481` contiene el correo personal de un tester.
- Rama Termux: ejecuta comandos de shell arbitrarios si alguien la usa o la fusiona.
- Rama Room: fusionarla rompería la compilación y duplicaría la base de datos.
- Tarea de relevo automática: hoy falla por falta de acceso a GitHub, pero si se le da acceso cambiará la rama activa cada día a las 8:52 (CDMX), sin PR y sin supervisión, en plena evaluación de la Fase 1. Pausarla o no es decisión del usuario.

## 9. Última sesión técnica conocida

- Commit 9cbcaa3, 15/10/2026 02:00:59 UTC, "ARI busca sola en internet lo que no sabe (0.66.0)". Sesión de Claude: https://claude.ai/code/session_01CyDGkuvFNwm1XX6rPSD4G1 (la llevan 160 de los 174 commits de la rama).
- Después, el usuario lanzó dos compilaciones en verde sobre ese commit: runs 38668762523 y 38671115372.
- Quedó a medias: `ARI_DOCUMENTACION.md` sin 0.65-0.66, `BACKLOG.md` sin actualizar desde 0.56, el tablero con "activo: true" obsoleto, la prueba de la 0.64 pendiente, `ProfileTest` sin pruebas de la regla de internet (`ARI_ESTADO.md:802` dice "si aplica"), y la publicación de ARI Trader sin el secreto `ARI_WEB_TOKEN` (no configurado en el run 38671115372).
- Otras sesiones: iOS (session_01SHwvTtt1TzG7QqFtBmm7Ys, 3 commits del 14/10) y Termux (PR #1).
- Sesión de auditoría (esta): solo lectura, 15-16/10/2026. Su único cambio es este documento, guardado en la rama activa con autorización del usuario.

## 10. Próxima acción concreta y segura

Primera acción, sin instalar nada ni forzar el cierre:
1. En el POCO X8 Pro Max, abrir ARI → Memoria → Respaldo → "Guardar un respaldo cifrado" y sacar el archivo del teléfono (por ejemplo, a Drive).
2. Anotar la versión que muestra Ajustes → Acerca de Ari y el valor de Ajustes → "Velocidad de Gemma" (GPU o CPU).

Por qué esta primero: protege lo único que no se puede recuperar desde GitHub (los datos de la memoria) y resuelve tres incógnitas que deciden las pruebas: qué versión se evalúa (la última confirmada es la 0.55), si la GPU ya está en "falla" y si forzar el cierre durante la carga puede degradar el motor.

Después:
3. El usuario responde las decisiones 1 a 5 de la sección 5 (rama, motor, "limpia", "sin cargar el modelo", "episódica/semántica") y autoriza o no la etiqueta de respaldo de la decisión 10.
4. Ejecutar la prueba C y la prueba B de la sección 7 con la versión acordada.
5. Registrar los resultados con fecha y versión en `docs/ARI_ESTADO.md` de la rama activa y actualizar este documento. Solo con A, B y C verificados se pasa a la Fase 2.

Hasta entonces: no instalar `ari-debug-apk`, no desinstalar, no usar "Recuperar" y no darle acceso a GitHub a la tarea de relevo sin decidirlo antes.

Antes de trabajar, cualquier sesión debe:
- Volver a traer la rama activa (`git fetch origin ccr-e45f2eee-wkjpdl`) y comprobar que su head sigue siendo 9cbcaa3. Si cambió, la tarea de relevo pudo añadir commits.
- Leer el `CLAUDE.md` de la rama en la que vaya a trabajar.
- No compilar en el contenedor (Google Maven está bloqueado): usar `android.yml` con `workflow_dispatch`.
