# Ari — Documentación del proyecto

Última actualización: 13 de octubre de 2026

Este documento lo mantiene Claude. Su fuente está en el repositorio, en docs/ARI_DOCUMENTACION.md, y se exporta a Google Drive, a la carpeta "Ari - Documentación de Claude", cada vez que se termina un cambio.

## 1. Qué es Ari

Ari es un asistente personal para Android que funciona con inteligencia artificial local: el modelo Gemma 4 E4B-it, mediante LiteRT-LM. Las conversaciones y los recuerdos no salen del teléfono y no se usan APIs de pago.

- Repositorio: https://github.com/javierhedzd-ops/ari-assistant
- Teléfono de prueba: POCO X8 Pro Max (12 GB de RAM)
- Modelo: gemma-4-E4B-it.litertlm (unos 3.4 GB)

## 2. Cómo usar Ari

### Primer uso

1. Instala el APK.
2. Toca **Seleccionar modelo** y elige el archivo gemma-4-E4B-it.litertlm. Ari lo copia a su almacenamiento privado.
3. Espera el mensaje "Gemma cargado. Ari está listo".

Las siguientes veces, el modelo carga solo al abrir la app.

### Chat por texto

Escribe y toca **Enviar**. Las conversaciones se guardan, y al abrir la app aparecen los últimos 100 mensajes.

### Memoria

Ari guarda recuerdos sobre ti y se los pasa a Gemma como contexto.

- **/recuerda** seguido del texto: guarda un recuerdo. Ejemplo: /recuerda Me gusta el café
- **/memoria**: muestra todos los recuerdos.
- **/olvida** seguido del texto: borra los recuerdos que contienen ese texto. Ejemplo: /olvida café

El botón **Memoria** también deja ver, agregar y borrar recuerdos. Para borrar uno, tócalo.

### Modo de voz

1. Toca **Voz** (el botón del micrófono). La primera vez, Ari pide permiso para usar el micrófono.
2. Habla. Ari escucha, responde con Gemma, lee la respuesta en voz alta y vuelve a escucharte.
3. Toca **⏹ Detener** para terminar.

El modo de voz se detiene solo si no te escucha 3 veces seguidas, si hay un error con el micrófono o si sales de la app. Para que te entienda sin internet, instala el paquete de español sin conexión: Ajustes → Sistema → Idiomas → Reconocimiento de voz.

### Ari como CEO de sus agentes

Ari dirige un equipo de agentes. Cada mensaje pasa primero por Ari, que decide si lo atiende un agente o lo responde Gemma. Los agentes contestan al instante y funcionan con texto y con voz, aunque el modelo no haya cargado.

- **Agente de memoria**: entiende frases normales, sin "/". Ejemplos: "recuerda que me gusta el café", "anota que mañana hay junta", "olvida café", "qué recuerdas de mí".
- **Agente de reloj**: dice la hora y la fecha del teléfono, que Gemma no conoce. Ejemplos: "qué hora es", "qué día es hoy".

Comandos:

- **/agentes**: muestra el equipo, qué hace cada agente y las últimas tareas.
- **/delega** seguido del agente y la tarea: encarga la tarea a un agente concreto. Ejemplo: /delega reloj qué hora es

Cuando responde un agente, el chat muestra su nombre, por ejemplo "Ari (Agente de reloj)". Cada tarea queda registrada en una bitácora dentro del teléfono.

## 3. Cómo instalar versiones nuevas

1. Cada cambio que llega a main se compila solo en GitHub Actions: https://github.com/javierhedzd-ops/ari-assistant/actions
2. Abre la ejecución más reciente, que debe tener la palomita verde, y descarga **ari-debug-apk**.
3. Instala el APK encima de la versión anterior. No hace falta desinstalar, y se conservan el modelo, el historial y los recuerdos.

### Problemas conocidos

- **"No se instaló la app debido a un conflicto con un paquete"**: la app instalada está firmada con otra clave. Esto ya está resuelto con una clave de depuración fija. Si vuelve a pasar, desinstala Ari una vez e instala de nuevo; tendrás que volver a seleccionar el modelo.

## 4. Historial de cambios

### PR #3 — Memoria persistente y firma fija (fusionado)

- https://github.com/javierhedzd-ops/ari-assistant/pull/3
- Historial del chat guardado en SQLite (ari.db) y restaurado al abrir la app.
- Comandos /recuerda, /memoria y /olvida, y botón Memoria.
- Gemma recibe los recuerdos y los últimos 8 mensajes como contexto.
- Carga automática del modelo guardado.
- Clave de depuración fija, para que las versiones nuevas se instalen encima de las anteriores.

### PR #4 — Modo de voz y documentación (fusionado)

- https://github.com/javierhedzd-ops/ari-assistant/pull/4
- Botón Voz con conversación continua: escuchar, responder, leer en voz alta.
- Reconocimiento y lectura de voz con los servicios de Android, sin APIs de pago.
- Documentación para el usuario exportada a Google Drive y CLAUDE.md con las reglas para Claude.
- Probado en el teléfono: funciona.

### Ari como CEO de agentes (en prueba, pendiente de PR)

- Contrato común para agentes y coordinador (AriCeo) que reparte las tareas antes de recurrir a Gemma.
- Agente de memoria con frases normales y agente de reloj.
- Bitácora de tareas en la base de datos y comandos /agentes y /delega.
- Compila correctamente; falta probarlo en el teléfono.

## 5. Ideas pendientes

- Siguientes fases del CEO:
  - Que Gemma ayude a decidir qué agente atiende cada mensaje.
  - Agentes que hagan cosas en el teléfono, como recordatorios y alarmas, siempre pidiendo confirmación.
  - Conectar bots externos, con permisos por agente.
- Que Ari empiece a hablar mientras Gemma escribe, en vez de esperar la respuesta completa.
- Elegir la voz y la velocidad de lectura.

## 6. Archivos principales del código

- **MainActivity.kt**: interfaz, chat, comandos y conexión con Gemma.
- **AriDatabase.kt**: base de datos SQLite con los mensajes y los recuerdos.
- **VoiceController.kt**: reconocimiento de voz y lectura en voz alta.
- **agents/**: el CEO (AriCeo.kt), el contrato de los agentes (Agent.kt) y los agentes de memoria y reloj.
- **docs/ARI_ESTADO.md**: estado técnico, para que cualquier asistente pueda continuar.
- **.github/workflows/android.yml**: compilación automática del APK.

El código está en app/src/main/java/com/ari/assistant/.
