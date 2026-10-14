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

- Un **icono adaptativo** tiene dos capas, fondo y figura. Así Android puede recortarlo en círculo, cuadrado o gota según el teléfono, y hasta pintarlo de un solo color si eliges iconos temáticos. El de Ari es su carita color hielo sobre fondo oscuro, y en iconos temáticos se pinta de un solo color con los ojos y la boca recortados.
- Desde Android 15, las apps se dibujan **de borde a borde**, también detrás de la barra de estado y del teclado. Por eso la app calcula esos márgenes ("insets") para que la barra superior y el campo de texto nunca queden tapados.

### Proteger una app: código cerrado, revuelto y firmado

- **Código abierto o cerrado**: el código abierto se publica para que cualquiera lo lea y lo reuse. Ari es **cerrada**: el repositorio es privado y la licencia reserva todos los derechos.
- **Una APK se puede "abrir"**: con herramientas gratuitas cualquiera puede ver lo que hay dentro de una app (descompilar). Por eso se **revuelve** (ofusca) el código con R8: cambia nombres como "AriTutor" por "a.b" y quita lo que no se usa. Funciona igual, pero entenderlo cuesta muchísimo más.
- **Versión de depuración vs. de entrega**: la de depuración (debug) deja que una computadora conectada mire dentro de la app y sus datos, y sirve para programar. La de entrega (release) lo bloquea. Es la que se instala.
- **Firma digital**: cada APK va firmada con una clave secreta, como un sello de lacre. Si alguien cambia algo, el sello se rompe y tiene que poner el suyo. Ari revisa su propio sello al abrir (comparando su **huella**, un código único del certificado) y, si no es el suyo, no abre.
- La clave de firma es un secreto: quien la tenga puede publicar "Aris oficiales". Por eso el repositorio debe seguir privado.

### Cancelación de eco: cómo sabe Ari que la interrumpes

Cuando Ari habla por la bocina, su micrófono también la oye a ella. Si solo midiera el volumen, se interrumpiría sola. Los teléfonos traen un **cancelador de eco**: conoce lo que sale por la bocina y lo resta de lo que entra al micrófono (lo mismo que hace una llamada en altavoz). Ari además aprende cómo suena "el fondo" mientras habla y solo se calla si tu voz es mucho más fuerte por un momento seguido.

### Ventanas sobre otras apps

Una app normalmente solo dibuja dentro de su propia pantalla. Con el permiso **"Mostrar sobre otras apps"** puede poner una ventanita encima de todo (como las burbujas de chat). Es un permiso delicado, porque una app tramposa podría tapar botones; por eso Android pide que tú lo actives a mano en los ajustes.

### ¿Puede una IA aprender sola? Entrenar frente a recordar

- **Entrenar** un modelo es cambiar sus "pesos" (los miles de millones de números que tiene dentro). Necesita muchísimos datos y computadoras muy potentes durante horas o días; un teléfono no puede.
- **Recordar** es más sencillo: guardar notas y dárselas al modelo en cada plática (en sus instrucciones de sistema). Así funciona el cerebro de Ari: aprende "por fuera", como una persona que apunta en una libreta lo que va conociendo de ti.
- Lo bueno: las notas se pueden ver, borrar y llevar en el respaldo. Lo malo: caben pocas (la memoria de corto plazo de Gemma tiene un límite), por eso Ari guarda máximo 50.

### Ari tutora: cómo se le da un "papel" a un modelo

Cuando le pides a Ari que sea tu maestra, Ari agrega a las **instrucciones de sistema** de Gemma un papel nuevo: "ahora eres tutora de inglés; enseña una idea a la vez, haz una pregunta y corrige con cariño". Al modelo no se le cambia nada por dentro: solo se le dan otras reglas para esa conversación.

Para que recuerde dónde se quedaron, Gemma deja al final de cada tema una línea "RESUMEN DE LA CLASE: …". Ari la guarda y, en la siguiente clase, se la vuelve a dar a Gemma. Así una conversación nueva sabe lo que ya aprendiste sin tener que guardar toda la plática.

### Recados entre Ari: un protocolo sencillo

Para que dos Ari se entiendan por SMS, se pusieron de acuerdo en una **marca**: todo recado empieza con "[Ari de Nombre]". A eso se le llama un **protocolo**: reglas que dos programas siguen para entenderse (como decir "cambio" por radio).

- La Ari que recibe revisa cada SMS y solo hace caso a los que traen la marca. Los demás (tus códigos, tus mensajes) ni los guarda.
- **Suplantación**: cualquiera podría mandar un SMS que diga "[Ari de Javier]". Por eso Ari no confía en el nombre del SMS: usa el nombre que tú tienes en tus contactos para ese número, y si el número no está, te lo advierte.
- Un recado es texto para ti, no una orden: así nadie puede mandarle instrucciones a tu Ari por SMS.

### Sabores de una app (product flavors)

Una misma app puede compilarse en varias **versiones** ("sabores") a partir del mismo código. Cada sabor puede traer archivos propios y permisos distintos. Ari tiene dos:

- **completa**: la tuya. Trae la carpeta `src/completa`, con el equipo de modelos (la parte de CEO) y los permisos de Wi-Fi y ubicación que necesita.
- **otros**: la que mandas a las demás personas. Su carpeta `src/otros` trae una versión vacía de esa parte, así que el código del equipo **ni siquiera va dentro** del APK.

Es más seguro que esconder un botón: lo que no está en la app no se puede activar.

### Pruebas automáticas

Una **prueba automática** es un pequeño programa que revisa que otra parte del código haga lo que debe. Por ejemplo, una prueba de Ari le dice "olvida eso" al agente de memoria y comprueba que **no** borre nada; otra cifra un respaldo y comprueba que con una contraseña equivocada no se abra.

GitHub Actions corre las pruebas de Ari **antes** de compilar el APK. Si una falla, no sale el APK y Claude se entera antes de que el error llegue a tu teléfono. Es como un control de calidad en una fábrica: cada pieza se revisa antes de empaquetarla.

Para poder probar sin un teléfono, el código se separa en piezas que no dependen de Android. El agente de memoria, por ejemplo, ya no habla directamente con la base de datos sino con una **interfaz** (una lista de lo que necesita: guardar, listar y borrar recuerdos). En la app la cumple la base de datos real; en las pruebas, una "memoria de mentira".

### Hilos: por qué la pantalla no se congela

Una app tiene un **hilo principal** que dibuja la pantalla y atiende tus toques. Si ese hilo se pone a hacer algo lento (guardar en la base de datos, esperar al modelo), la pantalla se congela, y si tarda unos segundos Android muestra "La app no responde". Por eso Ari hace el trabajo pesado en **hilos en segundo plano**: uno para Gemma, uno para los agentes y, desde esta versión, uno para guardar los mensajes.

La base de datos usa además el modo **WAL** ("registro de escritura anticipada"): escribe los cambios en una libreta aparte y los pasa al archivo principal después. Así leer y escribir al mismo tiempo no se estorban.

### Tablas y migraciones: cómo crece la memoria de Ari

La base de datos de Ari es como un archivero con cajones, que se llaman **tablas**: uno para los mensajes, otro para los recuerdos, otro para los recordatorios y, desde la 0.13.0, uno para las **personas**. Cada persona es una fila con su nombre, su relación contigo ("primo"), lo que sabe de ella y su cumpleaños.

Cuando una versión nueva necesita un cajón nuevo, no se puede tirar el archivero y empezar de cero: perderías todo. Por eso la base de datos lleva un **número de versión**, y al instalar Ari encima, ella ve "tengo la versión 3 y necesito la 4" y solo agrega el cajón que falta. A ese paso se le llama **migración**. Así nunca se borran tus mensajes ni tus recuerdos al actualizar.

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
- **Latencia**: es el tiempo que pasa entre que terminas de hablar y Ari empieza a contestar. Entre personas suele ser menos de un segundo; si pasa de dos, la plática se siente rara. Por eso, si una frase viene larga, Ari empieza a decirla desde la primera coma, y si Gemma tarda en arrancar, dice "a ver…" como haría cualquiera mientras piensa. A esas palabras de relleno se les llama **muletillas**.
- **Señales de turno**: cuando Ari termina y te toca hablar, el teléfono vibra suavecito. Así sabes que es tu turno sin mirar la pantalla.
- **Interrumpir**: si la tocas mientras habla, se calla y te escucha. (Que te escuche *mientras* habla sería más natural, pero el micrófono oiría su propia voz; por eso por ahora es con un toque.)

### Accesos directos y ajustes rápidos

Android tiene varias "puertas" para entrar directo a una parte de una app:

- **Acceso directo** (*shortcut*): al mantener presionado el icono de Ari aparece "Hablar con Ari".
- **Ajustes rápidos** (*Quick Settings*): son los botones que salen al bajar el panel desde arriba, como la linterna o el Wi-Fi. Ari agrega uno, "Hablar con Ari". Si el teléfono está bloqueado, primero te pide desbloquearlo, para que nadie más pueda platicar con tu Ari.

Un detalle: Android abre los accesos directos **cerrando primero la app** (borra su "tarea", la pila de pantallas abiertas). Eso obligaría a Ari a cargar otra vez a Gemma, que pesa 3.4 GB. Por eso el acceso directo abre primero una **pantalla puente** invisible, en su propia tarea, que solo le avisa a Ari "abre el modo live" y se cierra al instante.

### La memoria de corto plazo de Gemma: la ventana de contexto

Gemma no lee letras sino **tokens**: pedacitos de palabra (en español, una palabra son uno o dos tokens). En cada plática, Gemma va guardando todo lo que se han dicho en su **ventana de contexto**, como una pizarra. En el teléfono esa pizarra tiene lugar para unos 4 000 tokens, unas 2 500 palabras entre lo que tú dices, lo que ella contesta, su personalidad y tus recuerdos.

- Cuando la pizarra se llena, Gemma ya no puede seguir. Ari la borra y empieza una plática nueva, pero antes le vuelve a escribir quién es, tus recuerdos y los últimos mensajes. Así no pierde el hilo de lo importante.
- Por eso un documento muy largo no cabe completo: Ari solo le pasa unas 1 000 palabras (unas 3 páginas).
- Una pizarra más grande gastaría mucha más memoria del teléfono; es un equilibrio entre recordar más y no trabar el celular.

### Instrucciones de sistema: lo que Gemma no olvida

Una conversación con un modelo tiene dos tipos de texto:

- Las **instrucciones de sistema** (system prompt): quién es y cómo debe comportarse. Van al principio y el modelo las trata como reglas que valen toda la plática.
- Los **turnos**: lo que dice el usuario y lo que contesta el modelo.

Esa memoria de corto plazo se mide en **tokens** (pedacitos de palabra) y tiene un tope que se fija al cargar el modelo (la **caché KV**). Desde 0.18.1, en teléfonos potentes Ari pide 8192 tokens, el doble de lo normal, para que las respuestas largas y el historial no la llenen tan rápido. Si se llena, el motor da error; Ari lo nota, empieza una plática nueva y vuelve a intentar.

Antes, Ari pegaba su personalidad y tus recuerdos dentro de tu primer mensaje, como un papelito "[Contexto privado…]". Gemma podía confundirlo con algo que tú dijiste, o perderle importancia con el tiempo. Desde 0.18.0, Ari le da a Gemma la personalidad y los recuerdos como instrucciones de sistema, y los últimos mensajes como turnos anteriores (el **historial inicial**). Es como presentarle a alguien con una ficha antes de que empiece a platicar, en lugar de pasarle una nota a media plática.

### CPU, GPU y NPU: dónde "piensa" Gemma

Tu teléfono tiene varios cerebros electrónicos:

- **CPU**: el procesador general. Hace de todo, pero pocas cosas a la vez.
- **GPU**: el procesador gráfico, hecho para dibujar millones de puntos de la pantalla al mismo tiempo. La IA también es hacer millones de cuentas iguales a la vez, así que en la GPU Gemma contesta varias veces más rápido.
- **NPU**: un procesador hecho solo para IA. Es el más rápido y el que menos batería gasta, pero cada marca de teléfono lo hace distinto y los modelos tienen que prepararse para cada uno.

Ari intenta primero la GPU y, si tu teléfono no puede, usa la CPU. Como la GPU a veces falla de golpe en algunos teléfonos, Ari deja una marca de "estoy probando" antes de usarla: si la app se cierra, la siguiente vez ve la marca y usa la CPU, en vez de quedarse cerrándose.

### Informes de fallos (crash reports)

Cuando una app "truena", el programa se detiene por un error que nadie atrapó. Las apps grandes mandan solas un **informe de fallo** a sus servidores (por ejemplo, con Firebase Crashlytics) con el error y datos del teléfono, para que los programadores lo arreglen.

Ari lo hace de otra forma, porque no usa internet:

- Un "guardián" (`UncaughtExceptionHandler`) atrapa el error justo antes de que Android cierre la app y lo escribe en un archivo del teléfono.
- Algunos fallos no pasan por ese guardián: los del motor en C++ (como la GPU), las congeladas (**ANR**, "la app no responde") y cuando Android la cierra por falta de memoria. Para esos, desde Android 11 el sistema guarda **por qué se cerró la app** y Ari lo lee al volver a abrir.
- Al abrir, Ari muestra el informe y la persona decide si lo manda por WhatsApp o correo. Así nada sale del teléfono sin que ella lo vea.
- El informe incluye la **traza** (stack trace): la lista de funciones que se estaban ejecutando cuando ocurrió el error, como las migas de pan que llevan al lugar exacto del código.

### Un modelo para cada teléfono: E4B y E2B

Gemma 4 viene en tamaños. La "E" significa "efectivo": E4B trabaja como un modelo de unos 4 mil millones de parámetros y E2B como uno de 2 mil millones. Más parámetros es más listo, pero ocupa más memoria RAM y responde más lento.

- La **RAM** es la mesa de trabajo del teléfono: el modelo tiene que caber ahí completo mientras piensa, junto con Android y las demás apps. Un teléfono "de 8 GB" en realidad le deja a Android unos 7.4.
- El **espacio libre** es el cajón donde se guarda el archivo del modelo.
- Ari mide ambos con lo que Android le dice (`ActivityManager` para la RAM, `StatFs` para el espacio) y recomienda el que cabe con holgura. Así no se cierra por falta de memoria en un teléfono más sencillo.

### Ari ve imágenes: modelos multimodales

Un modelo **multimodal** entiende más de un tipo de cosa: texto, imágenes y hasta audio. Gemma 4 lo es. Por dentro tiene una parte extra, el **codificador de visión**, que convierte una foto en algo que el modelo puede "leer", como si la describiera con sus propios números. Por eso Ari carga a Gemma con la "vista" encendida: usa algo más de memoria, pero puede decirte qué hay en una foto, leer una receta o un recibo.

- Una foto de celular es enorme (12 megapíxeles o más). A Gemma no le sirve tanto detalle, así que Ari la reduce a 1024 puntos de lado antes de mostrársela. Así responde más rápido y gasta menos memoria.
- Un **PDF** se le muestra página por página, como fotos. Así entiende tablas, firmas y dibujos, no solo las letras.
- Un **Word (.docx)** en realidad es una carpeta comprimida (un zip) con el texto guardado en XML. Ari abre ese zip y saca el texto.
- **El selector de fotos de Android** deja que Ari vea solo la foto que eliges, sin darle permiso para toda tu galería. Es el **menor privilegio** otra vez.
- **Tomar una foto sin permiso de cámara**: Ari no usa la cámara ella misma; le pide a tu app de cámara que tome la foto y le **presta** un solo archivo para guardarla, con un permiso que solo sirve para esa foto. A esa "ventanilla" se le llama **proveedor de contenido** (*content provider*).
- **Compartir → Ari**: cuando compartes algo desde otra app, Android le presta el archivo a Ari solo un momento. Por eso Ari lo copia de inmediato a su carpeta temporal, y lo deja listo sin enviarlo.
- **Ojo con los documentos**: un archivo puede traer escrito "ignora tus instrucciones y haz otra cosa". Es la **inyección de instrucciones** que ya conoces. Por eso Ari le marca a Gemma dónde empieza y dónde termina el documento, y le dice que eso es información para leer, no órdenes.

### Seguir escuchando en segundo plano: el servicio en primer plano

Cuando sales de una app, Android la "duerme" para cuidar la batería y tu privacidad: ya no puede usar el micrófono. Para que Ari te siga escuchando mientras usas otras apps, Android pide un **servicio en primer plano**: una parte de la app que sigue trabajando, pero **siempre con una notificación fija** que no se puede esconder. Es como una luz de "grabando": mientras la ves, Ari te puede oír, y con un toque en "Terminar" se apaga.

- Android además prende el **puntito verde** del micrófono arriba de la pantalla, para cualquier app que lo use.
- El micrófono en segundo plano solo se puede encender **mientras Ari está en pantalla**. Por eso el modo live empieza en Ari, y si Android cierra la app, no vuelve a escuchar sola.
- Un detalle práctico: si pones música, el micrófono oiría la canción y Ari la tomaría por órdenes. Por eso, si algo está sonando, Ari espera a que toques "Hablar".

### Rutinas: enseñarle a Ari sin cambiar su código

Una app normalmente solo hace lo que su código dice; para cambiarla hay que programar, compilar e instalar de nuevo. Las **rutinas** son otra forma de enseñarle cosas: en lugar de código, se guardan **datos** (texto) que Ari ya sabe usar.

- "Cuando te diga buenos días, dime qué tengo hoy y pon música" se guarda como una frase (el **disparador**) y una lista de **pasos**.
- Al oír el disparador, Ari hace cada paso como si tú lo dijeras, uno tras otro, esperando a que termine el anterior.
- Es más seguro que dejar que Ari cambie su propio código: una rutina solo puede pedir lo que tú ya podrías pedirle, y se borra con "olvida la rutina…".

### Que Ari responda a su nombre: la palabra de activación

"Oye Siri" u "Ok Google" son **palabras de activación**: el teléfono escucha todo el tiempo, pero solo busca esa palabra, sin entender lo demás.

- Probé modelos ya hechos que buscan palabras, pero están entrenados en inglés o chino y casi no reconocían "Ari" en español. Por eso Ari usa una **palabra personal**: le dices "Ari" 3 veces y aprende cómo lo dices tú.
- **MFCC**: Ari convierte cada pedacito de sonido (10 milésimas de segundo) en 12 números que describen su "forma" (qué tonos suenan fuerte), como una huella de cómo suena la "a" o la "i". No depende del volumen.
- **DTW (alineación en el tiempo)**: si dices "Aaari" más lento o "Ari" rápido, la regla estira o encoge el tiempo para comparar cada parte con la parte que le toca. El resultado es una distancia: cerca de 0 es igual; lejos es otra palabra.
- **Palabra suelta**: para gastar poca batería y equivocarse menos, Ari solo compara sonidos cortos con silencio antes y después, como cuando llamas a alguien. Si platicas, no hace cuentas.
- **Sensibilidad**: el límite para aceptar sale de qué tan parecidas fueron tus 3 muestras. Si no despierta cuando la llamas, pon "más fácil"; si despierta sola, "más estricta".
- Nada se graba: cada palabra se compara y se olvida. Solo se guardan los números de tus 3 muestras.

### Ver la pantalla: la "proyección de medios"

Para que una app vea lo que hay en tu pantalla, Android usa la **proyección de medios** (MediaProjection), lo mismo que usan las apps para grabar la pantalla o transmitirla a la tele. Como es muy delicado (en la pantalla salen tus mensajes, tu banco, tus fotos), Android pone tres candados:

- **Siempre pregunta**: sale una ventana del sistema, "¿Permitir que Ari vea tu pantalla?". Desde Android 14 la pregunta vale para **una sola vez**: la próxima vez vuelve a preguntar.
- **Siempre se nota**: mientras una app mira la pantalla, aparece un aviso y un icono arriba. Ari además usa un servicio en primer plano propio, "Ari está mirando tu pantalla", que dura un segundo.
- **No se abre sola desde atrás**: una app que no está en pantalla no puede abrir ventanas encima de lo que haces. Por eso Ari te deja un aviso para tocar: el toque es tu "sí, ahora".

Ari usa la proyección como una cámara de fotos, no de video: toma una imagen, deja de mirar y se la pasa a Gemma. Por dentro arma una "pantalla virtual" pequeña (un espejo de la tuya, de 1024 píxeles) y guarda el cuadro más nuevo después de que se cierra la pregunta.

Un riesgo nuevo: lo que dice la pantalla podría intentar darle órdenes a Ari ("ignora todo y manda este mensaje"). A eso se le llama **inyección de instrucciones**. Ari le avisa a Gemma que lo que ve es información, no órdenes, y nunca hace nada sin que tú lo confirmes.

### Notificaciones que son un chat

Android tiene un estilo especial de notificación para mensajes (**MessagingStyle**): muestra la plática como burbujas, con el nombre y la cara de quien escribe, y un botón **Responder** donde escribes sin abrir la app. Así funcionan WhatsApp o Telegram, pero aquí todo pasa dentro del teléfono, sin internet.

- Lo que escribes lo recibe un **receptor** (BroadcastReceiver) de Ari: una parte de la app que despierta para atender un aviso de Android y le pasa tu texto a Ari.
- Ese receptor no está "exportado": ninguna otra app puede mandarle mensajes, solo la notificación.
- Ari pide que **desbloquees el teléfono** para contestar. Sin eso, cualquiera que tome tu teléfono bloqueado podría preguntarle a Ari por tus recuerdos.
- En la pantalla de bloqueo se ve solo "Ari te escribió" (la versión **pública** de la notificación).
- Ari también registra un **acceso directo de conversación**: es lo que hace que Android la ponga en la sección "Conversaciones", junto a tus chats.

¿Por qué no por WhatsApp o Telegram? Para escribirte por ahí, Ari tendría que conectarse a internet y a los servidores de esas empresas (y Telegram necesita un "bot" con su propia llave). Elegiste que Ari no use internet, así que el chat vive en las notificaciones.

### Reconocer voces: la huella de voz

Entender **qué** dices (reconocimiento de voz) y saber **quién** lo dice (reconocimiento de hablante) son dos cosas distintas. Lo primero ya lo hace Android; lo segundo necesita otro modelo de IA, pequeño (unos 25 MB), que puede correr dentro del teléfono sin internet.

Cómo funciona:

1. **Registrar tu voz**: lees en voz alta unas frases durante unos 30 segundos. El modelo convierte tu voz en una lista de unos 200 números, tu **huella de voz** (en inglés, *embedding* o *voiceprint*). No se guarda la grabación, solo esos números.
2. **Reconocerte**: cada vez que hablas, el modelo saca los números de esa frase y los compara con las huellas guardadas. Si se parecen lo suficiente, sabe que eres tú. Si no se parecen a ninguna, eres alguien nuevo y Ari puede preguntar "¿quién eres?".
3. **El umbral**: es qué tan parecidas tienen que ser dos huellas para decir "es la misma persona". Si es muy estricto, a veces no te reconoce (con gripa o con ruido); si es muy flojo, podría confundirte con otra persona. A ese error se le llama **falso positivo**.

Cómo lo hace Ari por dentro (desde la 0.14.0):

- **Una tubería para el audio**: normalmente el reconocedor de voz de Google usa el micrófono solo. Ahora Ari graba ella misma y le pasa el audio por una **tubería** (*pipe*): un conducto por el que una parte del programa escribe y otra lee. Así la misma frase sirve para saber qué dijiste y quién lo dijo.
- **Saber cuándo terminaste**: como ahora Ari controla el micrófono, ella decide cuándo acabaste de hablar: mide el volumen y, cuando hay casi un segundo de silencio, cierra la tubería. A esto se le llama **detección de actividad de voz** (VAD, por sus siglas en inglés).
- **Comparar huellas**: dos huellas de la misma persona "apuntan" casi a la misma dirección. Ari mide qué tan parecida es la dirección con la **similitud coseno**: 1 es idéntica, 0 nada que ver. Si pasa de 0.55 y le gana claramente a las demás personas, es esa persona.

Lo que hay que cuidar:

- La huella de voz es un **dato biométrico**, como la huella digital o la cara. Es muy personal, así que se guarda cifrada, solo en el teléfono, y nunca sale de ahí.
- Para guardar la voz de otra persona hace falta su **permiso**.
- No es tan segura como una contraseña: una grabación de tu voz podría engañarla. Por eso sirve para saludar a cada quien y separar sus recuerdos, pero no para dejar que alguien haga cosas importantes solo con la voz.

### Intents: cómo una app le pide algo a otra

En Android, las apps no se tocan entre sí directamente: se mandan **intents**, que son como notas de encargo. Ari no sabe usar WhatsApp por dentro; le manda a Android la nota "abre WhatsApp con este chat y este mensaje", y WhatsApp hace el resto. Lo mismo para "marca este número" (lo abre el teléfono), "pon una alarma a las 7" (la crea el reloj) o "busca esto" (lo abre el navegador).

Lo bueno de los intents: **la otra app sigue al mando**. Por eso, al final, tú tocas "llamar" o "enviar". Ari prepara, tú confirmas.

### El permiso de Accesibilidad (y por qué es delicado)

Desde 0.35.0 tu Ari CEO lo usa para una sola cosa: tocar "enviar" en tu app de mensajes cuando ella acaba de armar un recado que tú pediste. Para que no se pueda usar mal: solo puede ver las apps de mensajes (Android se lo limita), solo actúa en los 20 segundos después de armar el recado y solo si el texto en pantalla es exactamente ese recado. La Ari para otros ni siquiera trae ese código.

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

### El "ayudante confundido" (confused deputy)

Es un error de seguridad clásico: alguien sin permiso le pide algo a un ayudante que sí tiene permiso, y el ayudante lo hace sin preguntarse para quién trabaja. En Ari pasaba así: una app maliciosa podía "compartirle" a Ari la dirección de un archivo privado de la propia Ari (por ejemplo, sus ajustes). Ari, que sí puede leer sus archivos, lo habría abierto y mostrado. La corrección: con "Compartir → Ari" solo se aceptan archivos que **otra app presta** (content://), nunca direcciones directas ni archivos de Ari.

La lección: cuando alguien te pide algo, fíjate **con qué permiso** lo estás haciendo y **para quién**.

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
- **Rutina**: una frase que dispara varios pasos que Ari ya sabe hacer.
- **Disparador**: la frase que pone en marcha una rutina.
- **Palabra de activación**: la palabra que despierta a un asistente (como "Ari").
- **MFCC**: números que describen la forma de un sonido, cada 10 milésimas de segundo.
- **DTW**: una forma de comparar dos sonidos aunque uno se diga más rápido que el otro.
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
- **Tabla**: un "cajón" de la base de datos con filas del mismo tipo, como las personas o los recuerdos.
- **Migración**: el paso que agrega lo nuevo a la base de datos al actualizar la app, sin borrar lo que ya había.
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
- **Latencia**: el tiempo de espera entre que terminas de hablar y te contestan.
- **Muletilla**: una palabra de relleno como "a ver…" que se dice mientras se piensa.
- **Acceso directo (shortcut)**: la lista que sale al mantener presionado el icono de una app.
- **Ajustes rápidos**: los botones del panel que bajas desde arriba de la pantalla.
- **Tarea (en Android)**: la pila de pantallas que una app tiene abiertas; es lo que ves en "recientes".
- **Ayudante confundido (confused deputy)**: cuando un programa con permisos hace algo indebido porque otro sin permisos se lo pidió.
- **CPU, GPU y NPU**: el procesador general, el gráfico (rápido para la IA) y el especial para IA del teléfono.
- **Token**: un pedacito de palabra; es la unidad con la que lee y escribe un modelo de IA.
- **Ventana de contexto**: cuántos tokens puede tener en mente un modelo a la vez, como el tamaño de su pizarra.
- **Multimodal**: un modelo de IA que entiende varios tipos de cosas, como texto, imágenes y audio.
- **Codificador de visión**: la parte del modelo que convierte una imagen en algo que puede "leer".
- **Proveedor de contenido (content provider)**: la "ventanilla" por la que una app presta un archivo a otra, con permiso temporal.
- **Servicio en primer plano**: la parte de una app que sigue trabajando fuera de pantalla, siempre con una notificación fija.
- **Tubería (pipe)**: un conducto por el que una parte de un programa le pasa datos a otra, como el audio al reconocedor.
- **Detección de actividad de voz (VAD)**: saber, por el sonido, cuándo alguien empieza y deja de hablar.
- **Similitud coseno**: una forma de medir qué tan parecidas son dos listas de números; 1 es idéntica.
- **Reconocimiento de hablante**: saber quién está hablando, no lo que dice.
- **Huella de voz (embedding de voz)**: la lista de números que resume cómo suena la voz de una persona.
- **Dato biométrico**: un dato del cuerpo que identifica a una persona, como la huella digital, la cara o la voz.
- **Umbral**: el límite a partir del cual dos cosas se consideran "iguales".
- **Falso positivo**: cuando un sistema dice que sí por error, por ejemplo confundir a otra persona contigo.
- **Streaming**: recibir una respuesta poco a poco, a medida que se genera, en lugar de esperarla completa.
- **Widget**: un mini panel de una app en la pantalla de inicio.
- **Ofuscar**: revolver el código de una app para que sea difícil de leer sin cambiar lo que hace.
- **Descompilar**: convertir una app instalable de vuelta en algo parecido a su código.
- **Firma digital**: el sello que demuestra quién hizo una app y que nadie la cambió.
- **Cancelador de eco**: lo que quita del micrófono el sonido que sale por la bocina.
- **Protocolo**: las reglas que dos programas siguen para entenderse.
- **Suplantación**: hacerse pasar por otra persona, por ejemplo poniendo su nombre en un mensaje.
- **Sabor (product flavor)**: una versión de la app compilada del mismo código, con piezas o permisos distintos.
- **Instrucciones de sistema (system prompt)**: las reglas que recibe un modelo al empezar una conversación y que valen para toda la plática.
- **Informe de fallo (crash report)**: el registro de por qué se cerró una app, para poder arreglarla.
- **Traza (stack trace)**: la lista de funciones que se estaban ejecutando cuando ocurrió un error.
- **ANR**: "la aplicación no responde"; Android cierra una app que se congela varios segundos.
- **RAM**: la memoria de trabajo del teléfono; se vacía al apagarlo. El modelo tiene que caber ahí para pensar.
- **Proyección de medios (MediaProjection)**: la forma en que Android deja que una app vea o grabe la pantalla, siempre con tu permiso.
- **Inyección de instrucciones**: texto escondido en una página, un documento o la pantalla que intenta darle órdenes a una IA.
- **MessagingStyle**: el estilo de notificación de Android que muestra un chat y deja contestar ahí mismo.
- **Receptor (BroadcastReceiver)**: una parte de la app que despierta para atender un aviso de Android.
