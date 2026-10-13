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

### El primer subordinado: el modelo de IA de tu laptop

Ari puede delegar preguntas a otro modelo de IA que corra en tu laptop. El teléfono y la laptop se comunican por la red, sin pasar por internet.

Cómo usarlo:

- "laptop, explícame la fotosíntesis" o "pregúntale a la laptop …": Ari le pasa la pregunta y te lee la respuesta.
- "busca la laptop": Ari recorre la red y se conecta por su cuenta.
- **/laptop**: muestra si la laptop está conectada y con qué modelo.
- **/laptop** seguido de la dirección, por ejemplo /laptop http://192.168.1.50:11434: conecta a mano.
- "desconecta la laptop": olvida la laptop en la red actual.
- **/delega laptop** seguido de la pregunta: también funciona.

Autonomía: al abrir Ari, si estás en una red aprobada y todavía no tiene subordinado ahí, busca la laptop por su cuenta y te avisa cuando la encuentra.

### Preparar la laptop

Opción más sencilla, **LM Studio**:

1. Descarga LM Studio desde lmstudio.ai e instálalo.
2. Descarga un modelo pequeño desde la propia app. Para empezar, uno de 3 a 8 mil millones de parámetros (3B a 8B) va bien en la mayoría de las laptops.
3. En la sección para desarrolladores, enciende el servidor y activa la opción para servir en la red local. Usa el puerto 1234.
4. Si Windows pregunta si permites el acceso a la red, acepta solo para redes privadas.

Alternativa, **Ollama**: instálalo desde ollama.com, descarga un modelo y permite conexiones desde la red. En las versiones recientes hay una opción en la configuración; en las anteriores se usa la variable de entorno OLLAMA_HOST=0.0.0.0. Usa el puerto 11434.

### Redes y seguridad (para entender qué pasa)

- Ari y la laptop se hablan sin cifrar, como si se pasaran tarjetas postales: cualquiera conectado a la misma red podría leerlas. Por eso Ari solo usa la laptop en redes que tú apruebas.
- **Red privada** (por ejemplo, tu casa con contraseña): solo están tus dispositivos y gente de confianza. Se puede aprobar.
- **Red compartida** (un trabajo donde la Wi-Fi solo la usan empleados): no es pública, pero no controlas qué dispositivos hay. Apruébala solo si confías.
- **Red pública** (cafeterías, aeropuertos, hoteles): no la apruebes.
- **Hotspot del teléfono**: activa "compartir internet" en el teléfono y conecta la laptop. Es una red solo entre los dos, así que Ari siempre la permite. Es la mejor opción fuera de casa.
- La primera vez que uses la laptop en una red Wi-Fi, Ari pide el permiso de ubicación. Android lo exige para leer el nombre de la red; Ari no usa tu ubicación para nada más. Después pregunta "¿Usar el subordinado en esta red?".
- Ari solo se conecta a direcciones de la red local, nunca a internet, y guarda la laptop por separado en cada red.

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

### Ari como CEO de agentes y primer subordinado (en prueba, pendiente de PR)

- Contrato común para agentes y coordinador (AriCeo) que reparte las tareas antes de recurrir a Gemma.
- Agente de memoria con frases normales y agente de reloj.
- Bitácora de tareas en la base de datos y comandos /agentes y /delega.
- Primer subordinado: el modelo de IA de la laptop, con búsqueda automática en la red.
- Protección de red: solo en el hotspot del teléfono o en redes aprobadas, y solo direcciones locales.
- Compila correctamente; falta probarlo en el teléfono.

## 5. Ideas pendientes

- Siguientes fases del CEO:
  - Que Gemma ayude a decidir qué agente atiende cada mensaje, por ejemplo, mandar a la laptop las preguntas difíciles.
  - Agentes que hagan cosas en el teléfono, como recordatorios y alarmas, siempre pidiendo confirmación.
  - Más subordinados: otros bots o modelos, con permisos por agente.
- Que Ari empiece a hablar mientras Gemma escribe, en vez de esperar la respuesta completa.
- Elegir la voz y la velocidad de lectura.

## 6. Archivos principales del código

- **MainActivity.kt**: interfaz, chat, comandos y conexión con Gemma.
- **AriDatabase.kt**: base de datos SQLite con los mensajes y los recuerdos.
- **VoiceController.kt**: reconocimiento de voz y lectura en voz alta.
- **agents/**: el CEO (AriCeo.kt), el contrato de los agentes (Agent.kt), los agentes de memoria, reloj y laptop, y el guardia de red (NetworkGuard.kt).
- **docs/ARI_ESTADO.md**: estado técnico, para que cualquier asistente pueda continuar.
- **.github/workflows/android.yml**: compilación automática del APK.

El código está en app/src/main/java/com/ari/assistant/.
