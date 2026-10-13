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

### Permisos de Android

Una app tiene que pedir permiso para usar el micrófono, la ubicación y otras funciones sensibles. Es buena práctica pedirlos **solo en el momento en que hacen falta** y explicar para qué, como hace Ari con el micrófono y la ubicación.

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

### El tablero de pendientes

Claude trabaja primero en todo lo que no necesita tu permiso. Lo que sí te necesita (decisiones, permisos, pruebas) aparece en tu tablero "Pendientes de Ari". Cuando Claude se detiene, te avisa con una notificación y el tablero dice "En pausa: escribe 'sigue'".

## 8. Glosario rápido

- **APK**: el archivo instalador de una app de Android.
- **Agente**: un programa que hace una tarea concreta por encargo, como el agente de reloj.
- **CEO**: en Ari, quien reparte el trabajo entre los agentes y los modelos del equipo.
- **Commit**: un cambio guardado en el historial del código, con una descripción.
- **Compilar**: convertir el código en una app que se puede instalar.
- **Hook**: una acción automática que se dispara cuando pasa algo.
- **Repositorio**: la carpeta del proyecto con todo su historial, guardada en GitHub.
- **SQLite**: la pequeña base de datos donde Ari guarda los mensajes y los recuerdos dentro del teléfono.
- **SSID**: el nombre de una red Wi-Fi.
- **Widget**: un mini panel de una app en la pantalla de inicio.
