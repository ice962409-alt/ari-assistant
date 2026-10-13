# Ari — Documentación del proyecto

Última actualización: 13 de octubre de 2026

Este documento lo mantiene Claude. Su fuente está en el repositorio, en docs/ARI_DOCUMENTACION.md, y se exporta a Google Drive, a la carpeta "Ari - Documentación de Claude", cada vez que se termina un cambio.

## 1. Qué es Ari

Ari es una asistente personal para Android que funciona con inteligencia artificial local: el modelo Gemma 4 E4B-it, mediante LiteRT-LM. Las conversaciones y los recuerdos no salen del teléfono y no se usan APIs de pago.

**Primer objetivo de la empresa:** lanzar Ari oficialmente. El nombre de la empresa se decidirá cuando Ari esté bien diseñada.

- Repositorio: https://github.com/javierhedzd-ops/ari-assistant
- Teléfono de prueba: POCO X8 Pro Max (12 GB de RAM)
- Modelo: gemma-4-E4B-it.litertlm (unos 3.4 GB)

## 2. Cómo usar Ari

### Primer uso

1. Instala el APK.
2. Toca **Modelo** (arriba a la derecha) y elige el archivo gemma-4-E4B-it.litertlm. Ari lo copia a su almacenamiento privado.
3. Espera el mensaje "Gemma cargado. Ari está lista".

Las siguientes veces, el modelo carga solo al abrir la app.

### La interfaz

Ari tiene una interfaz oscura parecida a la de Claude, con **color hielo** como acento:

- Arriba: el punto hielo, "Ari", lo que está haciendo y los botones **Memoria** y **Modelo**.
- Tus mensajes van en burbujas a la derecha. Los de Ari van sin burbuja, con su nombre arriba ("Ari", o "Ari · Agente de reloj" cuando responde un agente).
- Mientras piensa ves "Pensando…" animado, y la respuesta aparece a medida que Gemma la escribe.
- Las respuestas tienen formato: títulos, listas, **negritas**, cursivas y código. Puedes mantener presionado un mensaje para copiarlo.
- Abajo está el campo para escribir, con el botón del micrófono y el botón circular de enviar.
- Cuando todavía no hay mensajes, Ari te recibe con "Hola, soy Ari".

El icono de la app es un anillo color hielo con un punto al centro y un pequeño satélite: Ari y su equipo. Ari Estado tiene el mismo anillo convertido en barra de progreso.

### Chat por texto

Escribe y toca el botón de enviar. Las conversaciones se guardan. Al abrir la app aparecen los últimos 30 mensajes, y arriba está "Ver mensajes anteriores" para cargar más, de 30 en 30. Así Ari abre rápido aunque tenga muchos mensajes.

### Memoria

Ari guarda recuerdos sobre ti y se los pasa a Gemma como contexto.

- **/recuerda** seguido del texto: guarda un recuerdo. Ejemplo: /recuerda Me gusta el café
- **/memoria**: muestra todos los recuerdos.
- **/olvida** seguido del texto: borra los recuerdos que contienen ese texto. Ejemplo: /olvida café

El botón **Memoria** también deja ver, agregar y borrar recuerdos. Para borrar uno, tócalo.

### Respaldo cifrado (llevar a Ari a otro teléfono o guardar una copia)

En el botón **Memoria** → **Respaldo** hay dos opciones:

- **Guardar un respaldo cifrado**: pide una contraseña de al menos 8 caracteres (dos veces) y te deja elegir dónde guardar el archivo, por ejemplo en Descargas o en tu Google Drive. El archivo se llama "ari-respaldo-fecha.ari" y guarda los recuerdos, el historial de la conversación, la bitácora de los agentes, el equipo contratado y las redes aprobadas.
- **Recuperar desde un respaldo**: eliges el archivo, escribes la contraseña y Ari reemplaza sus datos actuales por los del archivo.

Importante:

- Sin la contraseña nadie puede abrir el archivo, **ni siquiera tú**. Anótala en un lugar seguro.
- Si la contraseña es incorrecta o alguien modificó el archivo, Ari no lo importa y no cambia nada.
- El modelo Gemma no va en el respaldo (pesa 3.4 GB); en el teléfono nuevo hay que volver a seleccionarlo.
- Por seguridad, al recuperar solo vuelven los equipos con direcciones de la red local.

### Modo de voz

1. Toca el botón del micrófono, junto al de enviar. La primera vez, Ari pide permiso para usar el micrófono.
2. Habla. Ari escucha, responde con Gemma, lee la respuesta en voz alta y vuelve a escucharte.
3. Toca el mismo botón (ahora con un cuadrado) para terminar.

El modo de voz se detiene solo si no te escucha 3 veces seguidas, si hay un error con el micrófono o si sales de la app. Para que te entienda sin internet, instala el paquete de español sin conexión: Ajustes → Sistema → Idiomas → Reconocimiento de voz.

### El widget de Ari

Ari tiene un widget para tu pantalla de inicio, en color hielo como la app. Va rotando mensajes animados: si está lista, cuántos recuerdos guarda, cuántos modelos tiene su equipo y una invitación a ver el progreso de Claude.

El widget no muestra el progreso de Claude directamente porque, para eso, la app tendría que conectarse a internet con tu cuenta, y Ari no sale de tu red. Por eso el progreso vive en el tablero, a un toque.

- **Hablar con Ari**: abre la app.
- **Ver a Claude**: abre tu tablero en el navegador. Arriba verás si Claude está trabajando o en pausa, una barra con el progreso de su tarea (por ejemplo "Paso 2 de 4") y, abajo, lo que te necesita. Si dice "En pausa", escribe "sigue" en la sesión de Claude.

Para agregarlo: mantén presionado un espacio vacío de la pantalla de inicio → Widgets → busca "Ari" → arrástralo a la pantalla.

Por privacidad, el widget no muestra el texto de tus conversaciones ni de tus recuerdos, porque cualquiera que vea tu pantalla los leería. Tampoco se conecta a internet. Se actualiza al salir de Ari y cada 30 minutos.

### Ari Estado: el widget que muestra cómo trabaja Claude

Ari Estado es una app aparte, pequeña, que solo tiene un widget: **"Claude trabajando"**. Así Ari sigue sin conectarse a internet; solo esta app lo hace, y únicamente para leer el estado.

Lo que muestra el widget:

- Si Claude está **Trabajando**, **En pausa** o **Sin noticias** (lleva más de 45 minutos sin actualizar; quizá se cerró la sesión).
- La tarea en la que está, una **barra de progreso** y, mientras trabaja, una barra fina animada.
- El paso y el porcentaje, hace cuánto se actualizó y cuántos pendientes te esperan.
- Botones **Actualizar** y **Tablero**.

Además, te manda una **notificación** cuando Claude se detiene o deja de dar noticias, para que entres y escribas "sigue". Revisa cada 15 minutos, y al momento si tocas Actualizar.

Cómo funciona: Claude escribe su estado en el repositorio privado **Ari-estado** y la app lo lee con una **llave de solo lectura** que solo da acceso a ese repositorio. La llave se guarda cifrada en la caja fuerte del teléfono (el Keystore).

Para instalarla:

1. Descarga **ari-estado-apk** de la compilación más reciente en GitHub Actions e instálalo.
2. Abre Ari Estado: la pantalla te explica cómo crear la llave en GitHub (solo lectura, solo Ari-estado, con vencimiento). Pégala ahí, nunca en un chat.
3. Toca **Guardar y probar**: debe decir "¡Funciona!". Debajo del campo verás el inicio de la llave guardada y cuántos caracteres tiene (una llave completa tiene unos 93 y empieza con github_pat_). Si sale menos, se copió incompleta.
4. Agrega el widget: mantén presionado un espacio vacío de la pantalla de inicio → Widgets → Ari Estado → "Claude trabajando".

### Ari como CEO de sus agentes

Ari dirige un equipo de agentes. Cada mensaje pasa primero por Ari, que decide si lo atiende un agente o lo responde Gemma. Los agentes contestan al instante y funcionan con texto y con voz, aunque el modelo no haya cargado.

- **Agente de memoria**: entiende frases normales, sin "/". Ejemplos: "recuerda que me gusta el café", "anota que mañana hay junta", "olvida café", "qué recuerdas de mí". "Olvida café" borra solo los recuerdos con la palabra "café" (no "cafetería"). Frases como "olvida eso" u "olvida lo que te dije" no borran nada: son conversación y las responde Gemma.
- **Agente de reloj**: dice la hora y la fecha del teléfono, que Gemma no conoce. Ejemplos: "qué hora es", "qué día es hoy".

Comandos:

- **/agentes**: muestra el equipo, qué hace cada agente y las últimas tareas.
- **/delega** seguido del agente y la tarea: encarga la tarea a un agente concreto. Ejemplo: /delega reloj qué hora es

Cuando responde un agente, el chat muestra su nombre, por ejemplo "Ari · Agente de reloj". Cada tarea queda registrada en una bitácora dentro del teléfono.

### La personalidad de Ari

Ari es mujer y es tu creación. Es cercana y con buen humor, pero seria cuando el tema lo pide. Es directa y breve; si no entiendes algo o le pides más, te lo explica con detalle, paso a paso. Es franca: dice lo que piensa sin rodeos y no endulza las malas noticias, y de vez en cuando suelta alguna grosería si el momento lo pide. Gemma recibe esta personalidad al empezar cada conversación.

### El equipo de modelos: los subordinados de Ari

Ari es la CEO de una empresa en formación dedicada a la autonomía de modelos de IA locales. Sus subordinados son modelos de IA que corren en tus computadoras o celulares, conectados a la misma red. Se comunican por la red local, sin pasar por internet.

Ari busca los modelos por su cuenta y le propone a cada uno un puesto según su capacidad. **Tú decides a quién contrata**: cualquier equipo de la red podría hacerse pasar por un modelo de IA, así que Ari te muestra la lista (equipo, modelo y puesto) y solo trabaja con los que apruebes. A los subordinados solo les pasa la tarea, nunca tus recuerdos.

- **Desarrollo de software**: modelos especializados en código, por ejemplo "coder". Atienden lo que tenga que ver con código, programación, Linux o terminal.
- **Análisis y estrategia**: modelos grandes, de 7B o más. Investigan, comparan y redactan planes y propuestas.
- **Asistencia general**: modelos medianos o sin tamaño conocido.
- **Respuestas rápidas**: modelos pequeños, de menos de 4B.

Los modelos que no sirven para conversar (los de "embeddings") no se contratan.

Cómo usarlo:

- "equipo, …" o "pregúntale al equipo …": Ari elige al mejor del equipo para esa tarea. También funciona "laptop, …".
- Autonomía: si escribes algo de una especialidad, por ejemplo "escribe un script en Python", y hay un modelo con ese puesto, Ari se lo encarga sin que se lo pidas. Lo demás lo responde Gemma en el teléfono.
- "busca modelos": Ari recorre la red y te pregunta a quién contratar.
- **/equipo**: muestra el equipo, el puesto de cada modelo y si está disponible.
- **/equipo** seguido de la dirección, por ejemplo /equipo http://192.168.1.50:11434: contrata directamente los modelos de ese servidor (tú escribiste la dirección).
- "despide al equipo": olvida el equipo de la red actual.
- Al abrir Ari, si estás en una red aprobada y todavía no hay equipo ahí, Ari busca modelos por su cuenta y te pregunta a quién contratar.

En el chat, cada respuesta del equipo dice quién la escribió, por ejemplo "[Desarrollo de software (qwen2.5-coder:7b)]".

### Preparar la laptop con Linux (Ollama)

1. Instala Ollama. En la terminal:
   curl -fsSL https://ollama.com/install.sh | sh
2. Descarga uno o más modelos. Cada uno recibirá su propio puesto. Por ejemplo:
   ollama pull llama3.1:8b (análisis y estrategia)
   ollama pull qwen2.5-coder:7b (desarrollo de software)
   ollama pull llama3.2:3b (respuestas rápidas)
   Empieza con uno o dos: cada modelo ocupa varios GB y usa mucha memoria al responder.
3. Permite que Ollama acepte conexiones de la red local:
   sudo systemctl edit ollama
   En el editor que se abre, escribe estas dos líneas, guarda y cierra:
   [Service]
   Environment="OLLAMA_HOST=0.0.0.0"
   Luego aplica el cambio:
   sudo systemctl daemon-reload
   sudo systemctl restart ollama
4. Si usas el firewall ufw, abre el puerto solo para tu red local:
   sudo ufw allow from 192.168.0.0/16 to any port 11434 proto tcp
5. Comprueba que funciona, en la laptop:
   curl http://localhost:11434/v1/models
   Debe mostrar la lista de tus modelos.
6. En Ari, conectado a la misma red: di "busca modelos".

### Otros equipos: Windows, Mac o celulares

- **LM Studio** (Windows, Mac o Linux): descarga un modelo, enciende el servidor en la sección para desarrolladores y activa la opción de servir en la red local. Usa el puerto 1234.
- **Celulares Android**: un celular también puede ser subordinado si corre un servidor de modelos, por ejemplo Ollama dentro de Termux, con OLLAMA_HOST=0.0.0.0. Necesita bastante memoria RAM.
- **llama.cpp**: su servidor también funciona, en el puerto 8080.

### Redes y seguridad (para entender qué pasa)

- Ari y su equipo se hablan sin cifrar, como si se pasaran tarjetas postales: cualquiera conectado a la misma red podría leerlas. Por eso Ari solo trabaja con el equipo en redes que tú apruebas.
- **Red privada** (por ejemplo, tu casa con contraseña): solo están tus dispositivos y gente de confianza. Se puede aprobar.
- **Red compartida** (un trabajo donde la Wi-Fi solo la usan empleados): no es pública, pero no controlas qué dispositivos hay. Apruébala solo si confías.
- **Red pública** (cafeterías, aeropuertos, hoteles): no la apruebes. Las redes sin contraseña Ari ni siquiera deja aprobarlas.
- **Hotspot del teléfono**: activa "compartir internet" en el teléfono y conecta la laptop. Es una red solo entre tus dispositivos, así que Ari siempre la permite. Es la mejor opción fuera de casa.
- La primera vez que uses el equipo en una red Wi-Fi, Ari pide el permiso de ubicación. Android lo exige para leer el nombre de la red; Ari no usa tu ubicación para nada más. Después pregunta "¿Trabajar con el equipo en esta red?".
- Ari solo se conecta a direcciones de la red local, nunca a internet (tampoco si un equipo intenta redirigirla), y guarda el equipo por separado en cada red.

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

### Ari como CEO, su equipo de modelos y su personalidad (en prueba, pendiente de PR)

- Contrato común para agentes y coordinador (AriCeo) que reparte las tareas antes de recurrir a Gemma.
- Agente de memoria con frases normales y agente de reloj.
- Bitácora de tareas en la base de datos y comandos /agentes y /delega.
- Equipo de modelos: Ari busca todos los modelos de la red (PC o celulares), les asigna un puesto según su capacidad y les delega las tareas de su especialidad.
- Protección de red: solo en el hotspot del teléfono o en redes aprobadas, y solo direcciones locales.
- Personalidad de Ari: mujer, cercana, con buen humor, seria cuando hace falta, directa y detallada si hace falta.
- Seguridad: revisión de seguridad hecha; Ari ya no contrata modelos sin tu aprobación ni les pasa tus recuerdos, y no aprueba redes sin contraseña.
- Revisión de código hecha: 10 problemas corregidos, entre ellos que "olvida eso" borraba recuerdos, un ciclo con el permiso de ubicación y cierres por respuestas enormes de la red.
- Estabilidad: Ari ya no se cierra al girar el teléfono o cambiar el tema; libera la memoria al cambiar de modelo; aguanta errores de memoria; no deja archivos del modelo a medias; y si el equipo no responde, contesta ella con Gemma.
- Widget animado para la pantalla de inicio, con acceso a Ari y al tablero de Claude (con barra de progreso).
- Interfaz estilo Claude en color hielo: burbujas, texto con formato, "Pensando…" animado y respuesta en vivo. Iconos nuevos para Ari y Ari Estado, y widgets en color hielo.
- App Ari Estado: widget "Claude trabajando" con la tarea, barra de progreso y avisos cuando Claude se detiene, leído del repositorio privado Ari-estado con una llave de solo lectura.
- Respaldo cifrado de la memoria: guardar y recuperar recuerdos, historial, equipo y redes aprobadas en un archivo con contraseña (versión 0.3.0). Base de los respaldos y de la mudanza a otra PC.
- Ari Estado 0.1.2: muestra el inicio y la longitud de la llave guardada y quita espacios que se cuelen al pegarla.
- Estabilidad, segunda ronda: 17 pruebas automáticas que se ejecutan en cada compilación (reglas de los agentes, puestos de los modelos, direcciones privadas y cifrado del respaldo); los mensajes se guardan en segundo plano; la base de datos es más rápida (modo WAL); el primer mensaje tras cargar el modelo ya no le llega repetido a Gemma; y el historial se carga por partes para que la app abra rápido.
- Personalidad ajustada a tu elección (0.3.1): franca y con alguna grosería de vez en cuando.
- Probado en el teléfono (0.3.0): "está bien para un inicio"; se seguirá mejorando.

## 5. Plan hacia el lanzamiento

- **Estabilidad y optimización**: dos rondas hechas, con pruebas automáticas en cada compilación; seguirá en cada cambio.
- **Interfaz como la de Claude**: hecha, en color hielo; se pulirán detalles con las pruebas.
- **Primer uso sencillo**: que la app guíe la descarga del modelo, sin buscar el archivo a mano.
- **Una Ari para cada usuario**: que cada persona que la instale tenga su propia Ari.
- **Seguridad y nube privada**: repositorio privado, la laptop con Linux como casa principal de Ari, túnel cifrado (Tailscale), respaldos automáticos (laptop, teléfono y copia cifrada en Drive) y poder mudar a Ari a otra PC.
- **Requisitos de Google Play**: firma oficial, política de privacidad, justificación de permisos y revisión de la licencia de Gemma.

## 6. Ideas pendientes

- Siguientes fases del CEO:
  - Que Gemma ayude a decidir qué agente atiende cada mensaje.
  - Que Ari ponga a prueba a cada modelo para darle un puesto según lo que de verdad sabe hacer, no solo por su nombre.
  - Un canal para que Ari comparta sus reportes con Claude, por ejemplo a través de Drive.
  - Agentes que hagan cosas en el teléfono, como recordatorios y alarmas, siempre pidiendo confirmación.
  - Más subordinados: otros bots o modelos, con permisos por agente.
- Que Ari empiece a hablar mientras Gemma escribe, en vez de esperar la respuesta completa.
- Elegir la voz y la velocidad de lectura.

## 7. Archivos principales del código

- **MainActivity.kt**: pantalla principal, comandos y conexión con Gemma.
- **AriDatabase.kt**: base de datos SQLite con los mensajes y los recuerdos.
- **AriBackup.kt** y **BackupCrypto.kt**: el respaldo y su cifrado con contraseña.
- **app/src/test/**: las pruebas automáticas.
- **VoiceController.kt**: reconocimiento de voz y lectura en voz alta.
- **AriWidget.kt**: el widget de la pantalla de inicio.
- **ui/**: la interfaz (colores en AriTheme.kt, formato del texto en Markdown.kt y la conversación en ChatView.kt).
- **estado/**: la app Ari Estado (widget del progreso de Claude, llave cifrada y revisión cada 15 minutos).
- **agents/**: el CEO (AriCeo.kt), el contrato de los agentes (Agent.kt), los agentes de memoria y reloj, el equipo de modelos (TeamAgent.kt y Role.kt) y el guardia de red (NetworkGuard.kt).
- **docs/ARI_ESTADO.md**: estado técnico, para que cualquier asistente pueda continuar.
- **.github/workflows/android.yml**: compilación automática del APK.

El código está en app/src/main/java/com/ari/assistant/.
