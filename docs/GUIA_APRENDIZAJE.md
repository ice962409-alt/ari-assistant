# Guía de aprendizaje del proyecto Ari

Lo que has aprendido construyendo a Ari, explicado de forma sencilla. Claude la amplía cada vez que te enseña algo nuevo. Su fuente está en el repositorio, en docs/GUIA_APRENDIZAJE.md, y se exporta a tu Google Drive, a la carpeta "Ari - Documentación de Claude".

## 1. De qué está hecha Ari

Piensa en Ari como una persona con tres partes:

- **El cerebro**: el modelo de IA que piensa. Hoy es Gemma, un archivo de unos 3.4 GB que corre dentro de tu teléfono. Si se pierde, se puede descargar otra vez.
- **La memoria**: sus recuerdos, el historial de conversaciones, su personalidad y su equipo. Es lo valioso, lo que hace que Ari sea *tu* Ari, y ocupa muy poco espacio.
- **El cuerpo**: la app donde vive. Su código está guardado en GitHub y se puede reinstalar cuando quieras.

Por eso, para que Ari nunca se pierda, lo importante es respaldar su memoria.

## 2. IA local frente a IA en la nube

- **IA en la nube** (como ChatGPT o Claude): tu pregunta viaja por internet a los servidores de una empresa, que la responde.
- **IA local** (como Gemma en Ari): el modelo corre en tu propio dispositivo. Tus datos no salen de ahí y no se paga por uso.
- Lo que cuesta la IA local: los modelos ocupan varios GB, necesitan mucha memoria RAM y en un teléfono responden más lento que en la nube.

### El tamaño de un modelo: la "B"

El nombre de un modelo suele llevar un número con "B", como "llama3.1:8b". La B es de *billion* en inglés, es decir, miles de millones de parámetros, que son las "neuronas" del modelo.

- **Menos de 4B**: modelos pequeños, rápidos y con menos capacidad. En Ari son "Respuestas rápidas".
- **De 4B a 7B**: modelos medianos. En Ari, "Asistencia general".
- **7B o más**: modelos grandes, que razonan mejor pero son más lentos y piden más memoria. En Ari, "Análisis y estrategia".
- Los modelos con "coder" en el nombre están entrenados para programar. En Ari, "Desarrollo de software".
- Los modelos de "embeddings" no conversan; sirven para buscar textos parecidos. Ari no los contrata.

## 3. Redes: cuándo es seguro que Ari hable con otros equipos

### Tarjeta postal frente a sobre cerrado

- **HTTP (sin cifrar)** es como mandar una **tarjeta postal**: cualquiera que esté en la misma red podría leerla por el camino.
- **HTTPS (cifrado)** es como un **sobre cerrado**: solo el destinatario puede leerlo.
- Ollama y LM Studio usan HTTP. Por eso Ari solo habla con su equipo en redes de confianza.

### Tipos de red

- **Red privada**: por ejemplo, la de tu casa con contraseña. Solo están tus dispositivos y gente de confianza.
- **Red compartida**: por ejemplo, la Wi-Fi de un trabajo que solo usan los empleados. No es pública, pero no controlas qué dispositivos hay conectados.
- **Red pública**: cafeterías, aeropuertos, hoteles. Cualquiera puede conectarse. No es lugar para datos personales.
- Lo que importa no es el lugar, sino **quién está conectado**: un restaurante que antes era una casa es red compartida si solo la usan los empleados, y pública si los clientes tienen la clave.

### El hotspot del teléfono

Si activas "compartir internet" en tu teléfono y conectas la laptop, los dos forman **su propia red privada**, en la que solo están ellos. Es la opción más segura fuera de casa. Si la laptop además usa internet a través del teléfono, gasta tus datos móviles.

### Por qué Ari pide el permiso de ubicación

Android solo deja leer el **nombre de la red Wi-Fi** a las apps que tienen permiso de ubicación precisa, porque el nombre de una red puede revelar dónde estás. Ari lo usa únicamente para saber si la red está aprobada.

### Puertos

Un **puerto** es como el número de puerta de un edificio: la dirección IP es el edificio y el puerto dice a qué servicio llamas. Ollama usa el 11434, LM Studio el 1234 y llama.cpp el 8080.

## 4. Seguridad de la empresa

### Lo que encontró la revisión de seguridad

Al principio, Ari contrataba a **cualquier equipo de la red** que dijera ser un modelo de IA, y le pasaba tus recuerdos. Alguien en la misma red podía montar un "modelo falso" y recibir tus datos.

La lección: **nunca confíes en algo solo porque responde como esperas**. Por eso ahora:

- Ari te pregunta a quién contratar.
- Los subordinados solo reciben la tarea, nunca tus recuerdos.
- Ari no aprueba redes sin contraseña, porque cualquiera puede crear una red abierta con el nombre de la tuya.

### Repositorio público frente a privado

- **Público**: cualquiera en internet puede ver el código y la documentación.
- **Privado**: solo tú y quienes invites.
- Para una empresa, lo normal es privado. Se cambia en GitHub: Settings → Danger Zone → Change visibility.

### Lo básico que protege todo

- **Verificación en dos pasos** en GitHub y Google: aunque alguien robe tu contraseña, no puede entrar sin tu teléfono.
- **Disco cifrado** en la laptop: si te la roban, no pueden leer lo que tiene.
- **Contraseñas fuertes** y distintas en cada servicio.

## 5. Respaldos y la "nube personal"

### La regla 3-2-1

La regla que usan las empresas para no perder nada:

- **3 copias** de lo importante,
- en **2 dispositivos distintos**,
- y **1 fuera de casa**.

Para Ari: la laptop, tu teléfono y una copia cifrada en tu Google Drive.

### Cómo funciona el respaldo cifrado de Ari

Cuando guardas un respaldo desde Memoria → Respaldo, pasan tres cosas:

1. **De la contraseña sale una llave.** Una contraseña como "gatito2026" es fácil de adivinar para una computadora que prueba millones por segundo. Por eso Ari la pasa por una "licuadora" llamada **PBKDF2** que la mezcla 210 000 veces. Para ti tarda menos de un segundo; para alguien que quiera probar millones de contraseñas, se vuelve lentísimo.
2. **La sal.** Antes de licuar, Ari agrega unos bytes al azar (la **sal**) que se guardan en el archivo. Así, dos respaldos con la misma contraseña tienen llaves distintas, y nadie puede usar una tabla de contraseñas ya calculadas.
3. **El candado AES-GCM.** Con esa llave, Ari cierra el contenido con **AES-256-GCM**, el mismo tipo de cifrado que usan los bancos. "GCM" además pone un **sello**: si alguien cambia aunque sea una letra del archivo, Ari lo nota y no lo importa.

Lo importante: sin la contraseña el archivo es solo ruido, por eso puedes guardarlo en Drive sin miedo. Pero si la olvidas, tampoco tú lo puedes abrir: nadie tiene una "llave maestra".

### Nube privada

Una **nube privada** es un servidor que es tuyo y al que solo tú y tus dispositivos pueden entrar, desde cualquier lugar. Para Ari, la laptop con Linux será su casa principal y el teléfono, tu forma de hablarle.

### VPN o túnel cifrado

Una **VPN** como Tailscale o WireGuard crea un **túnel cifrado** entre tus dispositivos. Todo viaja en sobre cerrado aunque estés en una red pública, y puedes llegar a la laptop desde cualquier lugar.

### Opciones de nube

- **Nube privada en casa**: gratis y tus datos no salen de tus dispositivos, pero la laptop tiene que estar encendida.
- **Servidor rentado (VPS)**: siempre encendido, pero cuesta cada mes y tus datos quedan en la computadora de otra empresa.
- **Nube de una gran empresa**: cara, compleja y contraria a la idea de IA local bajo tu control.

## 6. Cómo se construye y se instala Ari

### GitHub y GitHub Actions

- **GitHub** guarda el código de Ari y su historial de cambios.
- **GitHub Actions** es un "robot" que, cada vez que cambia el código, lo compila y genera el APK, el instalador de Android.
- La **palomita verde** en una ejecución significa que compiló bien.

### Ramas y pull requests

- Una **rama** es una copia del código donde se trabaja sin tocar la versión principal (`main`).
- Un **pull request** es la propuesta de pasar esos cambios a `main`. Cuando lo apruebas ("Merge"), los cambios quedan en la versión principal.

### La firma de la app

Android solo deja instalar una versión nueva **encima** de la anterior si las dos están firmadas con la misma clave. Por eso apareció el error "conflicto con un paquete": cada compilación usaba una clave distinta. Ahora Ari usa siempre la misma clave de depuración. Para publicarla en Google Play hará falta una clave oficial, guardada en secreto.

### Google Play Protect y las apps fuera de la tienda

**Play Protect** es el antivirus de Google que viene en Android. Revisa cada app que instalas. Cuando una app no viene de Google Play y su desarrollador es desconocido, a veces la bloquea con el aviso "Se bloqueó la app para proteger tu dispositivo".

Ari aparece así porque la instalas directamente desde GitHub (a esto se le llama **instalar por fuera** o *sideloading*) y Google todavía no conoce su firma. No significa que tenga un virus: significa que Google no sabe quién la hizo.

- Para instalarla: **Más detalles** → **Instalar de todas formas**.
- No desactives Play Protect: te protege de apps que sí son peligrosas.
- La solución definitiva es publicar Ari en Google Play con una cuenta de desarrollador y su firma oficial. Es uno de los requisitos del lanzamiento.

### Verificación de desarrolladores

Google está pidiendo que toda app para Android tenga un **desarrollador verificado**, es decir, una persona o empresa que demostró quién es. Desde el 1 de octubre de 2026 ya se exige en Brasil, Indonesia, Singapur y Tailandia, y Google planea extenderlo a todo el mundo en 2027. Para proyectos pequeños existe una cuenta de **distribución limitada**: deja compartir una app con hasta 20 teléfonos sin identificación oficial ni pago.

### Licencias de software

Una **licencia** dice qué puedes hacer con el trabajo de otros. Ari usa piezas de Google: el modelo **Gemma 4** y el motor **LiteRT-LM**. Las dos tienen licencia **Apache 2.0**, que es muy permisiva: deja usarlas gratis, incluso para vender un producto, con una condición principal: **incluir el texto de la licencia**. Por eso Ari lo muestra en Ajustes → Acerca de Ari y licencias.

### Política de privacidad

Google Play pide que cada app publique una **política de privacidad**: un texto público que explica qué datos usa, dónde los guarda y con quién los comparte. La de Ari es sencilla porque todo se queda en el teléfono, y eso es una ventaja para venderla: "tu asistente no le cuenta nada a nadie".

### Iconos adaptativos y pantallas "borde a borde"

- Un **icono adaptativo** tiene dos capas, fondo y figura. Así Android puede recortarlo en círculo, cuadrado o gota según el teléfono, y hasta pintarlo de un solo color si eliges iconos temáticos. El de Ari es un anillo hielo sobre fondo oscuro.
- Desde Android 15, las apps se dibujan **de borde a borde**, también detrás de la barra de estado y del teclado. Por eso la app calcula esos márgenes ("insets") para que la barra superior y el campo de texto nunca queden tapados.

### Pruebas automáticas

Una **prueba automática** es un pequeño programa que revisa que otra parte del código haga lo que debe. Por ejemplo, una prueba de Ari le dice "olvida eso" al agente de memoria y comprueba que **no** borre nada; otra cifra un respaldo y comprueba que con una contraseña equivocada no se abra.

GitHub Actions corre las pruebas de Ari **antes** de compilar el APK. Si una falla, no sale el APK y Claude se entera antes de que el error llegue a tu teléfono. Es como un control de calidad en una fábrica: cada pieza se revisa antes de empaquetarla.

Para poder probar sin un teléfono, el código se separa en piezas que no dependen de Android. El agente de memoria, por ejemplo, ya no habla directamente con la base de datos sino con una **interfaz** (una lista de lo que necesita: guardar, listar y borrar recuerdos). En la app la cumple la base de datos real; en las pruebas, una "memoria de mentira".

### Hilos: por qué la pantalla no se congela

Una app tiene un **hilo principal** que dibuja la pantalla y atiende tus toques. Si ese hilo se pone a hacer algo lento (guardar en la base de datos, esperar al modelo), la pantalla se congela, y si tarda unos segundos Android muestra "La app no responde". Por eso Ari hace el trabajo pesado en **hilos en segundo plano**: uno para Gemma, uno para los agentes y, desde esta versión, uno para guardar los mensajes.

La base de datos usa además el modo **WAL** ("registro de escritura anticipada"): escribe los cambios en una libreta aparte y los pasa al archivo principal después. Así leer y escribir al mismo tiempo no se estorban.

### Alarmas y notificaciones: cómo Ari te avisa estando cerrada

Una app cerrada no puede "estar pendiente" de la hora: Android la detiene para ahorrar batería. Por eso Ari le pide al sistema una **alarma** (con el **AlarmManager**, el reloj despertador de Android): "despiértame el martes a las 9". A esa hora Android despierta a Ari solo un instante, ella muestra la **notificación** y vuelve a dormir. No hace falta internet ni que Gemma esté cargada.

- **Alarma exacta o aproximada**: para cuidar la batería, Android a veces junta varios avisos y los retrasa unos minutos. Las alarmas exactas necesitan el permiso "Alarmas y recordatorios".
- **Al reiniciar el teléfono, Android borra todas las alarmas.** Por eso Ari guarda los recordatorios en su base de datos y, al encender el teléfono, los vuelve a programar.
- **Entender la frase**: "recuérdame mañana a las 3 pagar la luz" se separa en partes: el día ("mañana"), la hora ("a las 3", que se entiende como 15:00) y lo que queda ("pagar la luz"). A esto se le llama **analizar** (en inglés, *parsing*).

### Descargar un archivo grande sin riesgos

Para bajar a Gemma (3.7 GB), Ari no se conecta ella misma: le pide la descarga al **gestor de descargas** de Android, el mismo que usa el navegador. Así:

- La descarga sigue aunque cierres Ari, y si se corta el Wi-Fi, se pausa y continúa sola.
- Ari pide **solo Wi-Fi** para no gastar tus datos móviles.
- El archivo se baja con otro nombre ("a medias") y solo se convierte en el modelo cuando está completo. Así nunca se carga un archivo roto.
- Antes de empezar, Ari revisa que haya espacio libre. Si el teléfono se llenara a la mitad, la descarga fallaría y habría que borrar el archivo incompleto.

Es la única puerta a internet que Ari abre, y solo cuando tú la pides: tus conversaciones nunca pasan por ahí.

### Cómo se logra una plática fluida por voz

En una conversación entre personas, nadie espera a tener toda la respuesta pensada para empezar a hablar. Ari hace lo mismo:

- **Por frases**: Gemma escribe la respuesta poco a poco (a esto se le llama *streaming*). En cuanto termina la primera frase, Ari la dice en voz alta mientras Gemma sigue con la siguiente. Así contesta en uno o dos segundos en vez de esperar todo.
- **Respuestas cortas**: en voz, Ari le pide a Gemma contestar en una a tres frases, sin listas ni formato, como en una plática.
- **Turnos**: cuando dejas de hablar un segundo, Ari entiende que terminaste y contesta. Cuando ella termina, te vuelve a escuchar sola.
- **Interrumpir**: si la tocas mientras habla, se calla y te escucha. (Que te escuche *mientras* habla sería más natural, pero el micrófono oiría su propia voz; por eso por ahora es con un toque.)

### Intents: cómo una app le pide algo a otra

En Android, las apps no se tocan entre sí directamente: se mandan **intents**, que son como notas de encargo. Ari no sabe usar WhatsApp por dentro; le manda a Android la nota "abre WhatsApp con este chat y este mensaje", y WhatsApp hace el resto. Lo mismo para "marca este número" (lo abre el teléfono), "pon una alarma a las 7" (la crea el reloj) o "busca esto" (lo abre el navegador).

Lo bueno de los intents: **la otra app sigue al mando**. Por eso, al final, tú tocas "llamar" o "enviar". Ari prepara, tú confirmas.

### El permiso de Accesibilidad (y por qué es delicado)

Para que una app toque botones y escriba **dentro** de otras apps, Android tiene el permiso de **Accesibilidad**. Existe para ayudar a personas con discapacidad, por ejemplo leyendo la pantalla en voz alta. Pero es el permiso más poderoso del teléfono:

- La app puede **ver todo** lo que aparece en pantalla, incluidas tus apps del banco y lo que escribes.
- Un texto malicioso en una página o un mensaje podría intentar **engañar a la IA** para que haga algo que no pediste. A esto se le llama **inyección de instrucciones**.
- Google Play casi no lo permite en apps que no son de accesibilidad.

Por eso Ari empieza solo con intents, y el control total queda como decisión tuya, con límites claros si se hace.

Además, **Google Play prohíbe** que un asistente use Accesibilidad para planear y ejecutar acciones por su cuenta. Solo lo permite a las apps hechas para personas con discapacidad, o para rutinas con pasos fijos escritos por una persona.

### AppFunctions: la forma nueva y segura

Android está creando **AppFunctions**: cada app ofrece a los asistentes una lista de funciones concretas que se pueden usar ("crear nota", "agregar a la lista del súper", "reproducir canción"), como un menú. El asistente pide una función y la app decide qué deja hacer, sin que nadie vea la pantalla. Es como pedir en una ventanilla en vez de entrar a la cocina.

Hoy está en prueba, solo en Android 16 o superior, y por ahora solo la usan apps del sistema como Gemini. Cuando se abra a otros asistentes, Ari la usará.

### Permisos de Android

Una app tiene que pedir permiso para usar el micrófono, la ubicación y otras funciones sensibles. Es buena práctica pedirlos **solo en el momento en que hacen falta** y explicar para qué, como hace Ari con el micrófono, la ubicación, las notificaciones y los contactos. Así, si dices que no, el resto de Ari sigue funcionando: sin el permiso de contactos, por ejemplo, puedes llamar diciendo el número.

### Por qué el widget no muestra el progreso de Claude

El widget vive en tu teléfono y Claude trabaja en un servidor en la nube. Son dos lugares distintos, sin conexión directa. Para que el widget mostrara el progreso, la app tendría que conectarse a internet y guardar dentro una contraseña de tu cuenta, para leerlo aunque el repositorio sea privado. Eso abriría una puerta que hoy está cerrada a propósito. Por eso el progreso vive en el tablero, que se abre con tu sesión de claude.ai, y el widget solo te lleva ahí.

La lección: **cada conexión nueva es una puerta**. Antes de abrirla, pregúntate qué datos pasarían por ella y quién más podría usarla.

**Cómo lo resolvimos:** en lugar de abrir esa puerta en Ari, creamos **Ari Estado**, una app aparte que solo hace una cosa: leer el estado de Claude. Así, si algo saliera mal en esa app, Ari y tus datos no se ven afectados. A esto se le llama **aislar**: separar lo que necesita internet de lo que guarda datos personales.

### Llaves de acceso y el "menor privilegio"

Una **llave de acceso** (en GitHub, *personal access token*) es como una contraseña pensada para que una app entre a tu cuenta. La regla de oro es el **menor privilegio**: darle a cada llave **solo lo mínimo que necesita**.

- La llave de Ari Estado solo puede **leer** y solo el repositorio **Ari-estado**. Si alguien la robara, solo vería en qué trabaja Claude: ni tu código, ni tus datos, ni tu cuenta.
- Tiene **fecha de vencimiento** (por ejemplo, 90 días). Si se perdiera, deja de servir sola.
- **Nunca la mandes por chat** ni la guardes en notas: todo lo que escribes en un chat queda guardado en la conversación.

### Guardar secretos en el teléfono: el Keystore

Android tiene una "caja fuerte" en el hardware del teléfono, el **Keystore**. Ari Estado guarda ahí una clave y con ella **cifra** la llave de GitHub. Esa clave no se puede sacar del teléfono, así que aunque alguien copiara los archivos de la app, no podría leer la llave.

### Revisar en segundo plano sin gastar batería

Android no deja que una app consulte internet cuando quiera, para cuidar la batería. Ari Estado usa el **JobScheduler**, el sistema de "tareas programadas" de Android: revisa cada 15 minutos (lo mínimo permitido) y solo cuando hay red.

## 7. Herramientas para trabajar con Claude

### Skills y plugins

- Una **skill** son instrucciones guardadas para que Claude haga una tarea siempre de la misma forma, como una receta.
- Un **plugin** es un paquete de skills, comandos o conexiones que se instala en Claude Code.

Las skills de Anthropic más útiles para Ari:

- **security-review**: busca fallos de seguridad. Encontró el problema del "modelo falso".
- **code-review**: busca errores en el código. Encontró, por ejemplo, que "olvida eso" borraba recuerdos al azar.
- **skill-creator**: para crear skills propias del proyecto.
- **deep-research**: investigación a fondo, útil para preparar el lanzamiento.

### Cómo evaluar una herramienta nueva (el caso Graphify)

Antes de instalar algo, pregúntate:

1. **¿Es real y de quién es?** Graphify es de código abierto y popular.
2. **¿Sus números son independientes o son publicidad?** El "71 veces menos tokens" lo midió el propio proyecto.
3. **¿Hay imitaciones?** El paquete oficial se llama "graphifyy", con doble "y"; los parecidos pueden ser maliciosos.
4. **¿Lo necesito ahora?** Graphify sirve para proyectos enormes; Ari tiene unos 10 archivos, así que hoy no aporta.

### Claude Code frente al chat normal de Claude

En la app de Claude hay dos cosas distintas:

- **El chat normal**: conversaciones sueltas. No sabe nada de Ari ni puede tocar el código.
- **Claude Code** (sección "Code"): sesiones de trabajo conectadas al repositorio de Ari, donde Claude programa, compila y actualiza tu tablero.

Por eso "sigue" se escribe **en la sesión de Claude Code**, no en un chat nuevo. El aviso naranja del widget y la notificación de Ari Estado te llevan directo a esa sesión.

### Cuando una app se cierra sola

Android muestra "la app continúa fallando" cuando hay un error que el programa no supo manejar. Para encontrar la causa sin herramientas de desarrollador, Ari Estado guarda el último error y lo muestra en su pantalla: con una captura de ese texto, Claude puede saber qué pasó. El primer cierre de Ari Estado fue por un **permiso faltante**: desde Android 14, una app que quiere revisar "solo cuando haya red" debe declarar que consulta el estado de la red.

### El tablero de pendientes

Claude trabaja primero en todo lo que no necesita tu permiso. Lo que sí te necesita (decisiones, permisos, pruebas) aparece en tu tablero "Pendientes de Ari". Arriba ves si Claude está trabajando o en pausa, una barra con el progreso de su tarea y el paso en el que va. Cuando Claude se detiene, te avisa con una notificación y el tablero dice "En pausa: escribe 'sigue'".

## 8. Glosario rápido

- **APK**: el archivo instalador de una app de Android.
- **Agente**: un programa que hace una tarea concreta por encargo, como el agente de reloj.
- **CEO**: en Ari, quien reparte el trabajo entre los agentes y los modelos del equipo.
- **Commit**: un cambio guardado en el historial del código, con una descripción.
- **Compilar**: convertir el código en una app que se puede instalar.
- **Hook**: una acción automática que se dispara cuando pasa algo.
- **Repositorio**: la carpeta del proyecto con todo su historial, guardada en GitHub.
- **SQLite**: la pequeña base de datos donde Ari guarda los mensajes y los recuerdos dentro del teléfono.
- **Keystore**: la caja fuerte del teléfono para guardar claves de cifrado.
- **Llave de acceso (token)**: una contraseña limitada que una app usa para entrar a un servicio.
- **Menor privilegio**: darle a cada cosa solo los permisos mínimos que necesita.
- **SSID**: el nombre de una red Wi-Fi.
- **AES-GCM**: un cifrado muy seguro que además detecta si alguien modificó el archivo.
- **PBKDF2**: la "licuadora" que convierte una contraseña en una llave difícil de adivinar.
- **Sal**: bytes al azar que se mezclan con la contraseña para que cada respaldo tenga una llave distinta.
- **Respaldo**: una copia de tus datos para recuperarlos si algo falla.
- **Prueba automática**: un programa que revisa que otra parte del código funcione bien, en cada compilación.
- **Hilo**: una línea de trabajo dentro de una app; el principal dibuja la pantalla.
- **Interfaz (en programación)**: la lista de lo que una pieza necesita de otra, sin decir cómo se hace.
- **WAL**: un modo de la base de datos que permite leer y escribir a la vez sin estorbarse.
- **Play Protect**: el revisor de seguridad de Google en Android.
- **Instalar por fuera (sideloading)**: instalar una app sin pasar por Google Play.
- **Licencia Apache 2.0**: un permiso para usar software gratis, también en productos comerciales, incluyendo su texto.
- **Política de privacidad**: el texto público que explica qué hace una app con tus datos.
- **Desarrollador verificado**: quien demostró su identidad ante Google para distribuir apps.
- **AlarmManager**: el despertador de Android; despierta a una app a una hora para que haga algo.
- **Notificación**: el aviso que aparece arriba en la pantalla del teléfono.
- **Analizar una frase (parsing)**: separar un texto en partes que el programa entiende, como el día, la hora y la tarea.
- **Intent**: la "nota de encargo" con la que una app le pide algo a otra en Android.
- **Accesibilidad**: el permiso que deja a una app ver y controlar la pantalla; muy poderoso y delicado.
- **Inyección de instrucciones**: cuando un texto intenta engañar a una IA para que haga algo que su dueño no pidió.
- **Gestor de descargas (DownloadManager)**: el servicio de Android que baja archivos grandes, aunque la app esté cerrada.
- **AppFunctions**: funciones que una app ofrece a los asistentes de IA para que las usen de forma segura.
- **SMS**: los mensajes de texto normales del teléfono.
- **Streaming**: recibir una respuesta poco a poco, a medida que se genera, en lugar de esperarla completa.
- **Widget**: un mini panel de una app en la pantalla de inicio.
