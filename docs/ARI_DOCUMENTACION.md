# Ari — Documentación del proyecto

Última actualización: 14 de octubre de 2026

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
2. Toca **Modelo** (arriba) → **Descargar Gemma 4**. Ari baja el modelo oficial (unos 3.7 GB) con el gestor de descargas del teléfono:
   - Solo por Wi-Fi, para no gastar tus datos. Si te desconectas, se pausa y sigue sola.
   - Necesitas unos 4.2 GB libres.
   - Verás el avance arriba en Ari y en las notificaciones. Puedes cerrar la app mientras.
   - Al terminar, Ari carga a Gemma sola.
3. Si ya tienes el archivo, toca **Modelo** → **Elegir un archivo del teléfono** y elige gemma-4-E4B-it.litertlm.
4. Espera el mensaje "Gemma cargado. Ari está lista".

Las siguientes veces, el modelo carga solo al abrir la app. Descargar el modelo es la **única vez que Ari usa internet**, y solo cuando tú lo pides: tus conversaciones y recuerdos no salen del teléfono.

Si sales de Ari mientras trabaja (una respuesta larga, una tarea del equipo o cargando a Gemma), te escribe en su chat de notificaciones cuando termina (ver "El chat de Ari en las notificaciones").

La primera vez que abres esta versión, Ari te pregunta **cómo te llamas** para llamarte por tu nombre. Puedes dejarlo para después con "Ahora no".

### Instalar Ari en otro teléfono (versión básica)

Para otras personas usa el APK **ari-otros-apk**: no trae el equipo de modelos (solo tu Ari es la CEO) ni pide el permiso de ubicación.

Ari se instala con el mismo APK en cualquier teléfono con Android 8 o más nuevo y procesador de 64 bits. La primera vez, después de preguntar el nombre, Ari muestra sola su recomendación con "Descargar ahora", "Más tarde" o "Ya tengo el archivo" (y siempre está en el botón **Modelo**). Ari mide la memoria RAM y el espacio libre y pone primero el modelo que mejor corre ahí:

- **Gemma 4 E4B** (3.7 GB): el más listo, para teléfonos con 8 GB de RAM o más (como el POCO).
- **Gemma 4 E2B** (2.6 GB): más ligero y rápido, para teléfonos con 4 a 6 GB de RAM, o si el completo no cabe.
- Con menos de 3 GB de RAM, Ari avisa que puede ir muy lenta o cerrarse.

En teléfonos con menos de 7 GB de RAM, Ari usa su **versión básica**: no carga la "vista" (fotos, PDF y ver la pantalla), que ocupa mucha memoria. El chat, la voz, el modo live, los recordatorios, la memoria y las acciones en el teléfono funcionan igual.

Si la Ari es para otra persona, empieza vacía: le pregunta su nombre y no tiene nada tuyo, porque tus recuerdos y pláticas viven solo en tu teléfono.

**Informe de fallos (para quien prueba Ari):**

- Si Ari se cierra sola, guarda qué pasó. La siguiente vez que se abre pregunta: "Ari se cerró la última vez. ¿Se lo mandas a quien te pasó la app?".
- Con **Ver y mandar**, la persona ve el informe completo y toca **Mandar por correo**: se abre un correo ya escrito a tu Gmail y solo toca Enviar (o elige "Otra app"). Ari no lo manda sola ni usa internet.
- También puede mandar un reporte cuando quiera: **Ajustes → Reportar un problema**, escribe qué pasó y toca Mandar.
- El informe lleva solo: versión de Ari, modelo del teléfono, versión de Android, RAM, qué Gemma usa, si la GPU funciona y el error técnico. Nada de pláticas, recuerdos, contactos ni nombres.
- Detecta también los cierres que vienen del motor (la GPU), las congeladas y la falta de memoria (Android 11 o más nuevo).
- Los informes llegan a tu Gmail con el asunto "Informe de fallo de Ari". Claude los revisa al empezar cada sesión y los arregla en la siguiente versión.

### Seguridad: Ari no es código abierto

- **Tu Ari CEO es solo tuya**: cada teléfono tiene un código propio (Ajustes → Acerca de Ari → "Código de este teléfono"). La Ari CEO lleva guardado el código de tu teléfono y no abre en ningún otro. Si cambias de teléfono (o lo restableces de fábrica), pásale a Claude el código nuevo para la siguiente versión.

- **Repositorio privado**: solo tú (y Claude con tu permiso) ven el código.
- **Licencia**: el archivo LICENSE dice "Todos los derechos reservados": nadie puede copiar, modificar, distribuir ni descompilar Ari sin tu permiso por escrito. Quien recibe la APK solo puede usarla.
- **Código revuelto (R8)**: en las APK de entrega, los nombres del código se cambian por letras sin sentido y se quita lo que no se usa. Sigue funcionando igual, pero leerlo o cambiarlo es mucho más difícil.
- **Sin modo de depuración**: nadie puede conectar el teléfono a una computadora y sacar los recuerdos o pláticas de Ari.
- **Copias modificadas**: cada APK oficial va firmada con la clave de Ari. Si alguien la modifica, tiene que firmarla con otra clave; Ari lo nota al abrir, avisa "Esta copia de Ari no es la original" y no abre.
- Ninguna app es imposible de modificar para un experto con tiempo, pero estas capas lo hacen muy difícil, protegen los datos de quien la usa y dejan claro que está prohibido.

### Tutora personal

Ari puede ser tu maestra de lo que quieras, en las dos versiones (la tuya y la de otros):

- Para empezar: "quiero aprender inglés", "sé mi maestra de guitarra", "dame clases de cocina", "enséñame Excel".
- La primera clase te pregunta tu nivel y para qué lo quieres. Después enseña una idea a la vez, con ejemplos del día a día, y te pone una pregunta o ejercicio. Te corrige y ajusta la dificultad.
- En idiomas escribe la frase, su traducción y cómo se pronuncia.
- Al terminar un tema deja una línea "RESUMEN DE LA CLASE: …" que Ari guarda.
- Para salir: "terminar clase". Para seguir otro día: "sigamos con inglés". Ari repasa lo último y sigue con lo siguiente.
- Funciona por escrito y en voz. En voz, las frases en otro idioma las lee la voz en español del teléfono, así que la pronunciación no es perfecta; para eso, guíate por la pronunciación escrita.

### Recados entre Ari

Dile a tu Ari **"dile a mi mamá que llego a las 8"** (o "avísale a Laura que ya salí"). Ari busca a esa persona en tus contactos y abre tu app de mensajes con el recado ya escrito: "[Ari de Tu Nombre] llego a las 8". Tú tocas enviar.

- Si la otra persona tiene Ari y activó **Ajustes → Recados entre Ari → Activar** (acceso a notificaciones), su Ari lee sola el SMS y le dice "Javier te manda decir: llego a las 8" (con el nombre de sus contactos), en el chat, en notificaciones y en voz si está en modo live.
- Sin ese permiso, también puede mantener presionado el SMS → **Compartir → Ari** (o copiarlo y pegarlo en el chat de Ari).
- Para dar el acceso, en teléfonos donde Ari no viene de la tienda puede salir "Configuración restringida": Ajustes del teléfono → Apps → Ari → menú ⋮ → "Permitir configuración restringida", y repetir. Ari lo explica paso a paso.
- Ari solo mira las notificaciones de la app de mensajes y solo hace caso a las que empiezan con "[Ari de …]"; las demás no las lee ni las guarda.
- Si no tiene Ari, le llega como un SMS normal que se entiende.
- Ari no lee tus SMS ni pide permisos de SMS: tu teléfono (POCO/Xiaomi con Android nuevo) no deja instalar apps de fuera de la tienda que los piden, para proteger de fraudes. Por eso en 0.24 la app no se instalaba; se corrigió en 0.26.1.
- Un recado es un mensaje para ti, nunca una orden para Ari. Solo tú puedes mandar recados (no un invitado).
- **Contactos de recados**: la primera vez que le mandas un recado a alguien, Ari pregunta "¿Guardo a Mamá en tus contactos de recados?". La Ari para otros guarda **máximo 4** (y solo manda recados a ellos); la Ari CEO no tiene límite (y puede "Solo enviar" sin guardar). Se quitan en Ajustes → Recados entre Ari → Quitar contacto.
- **Por voz**: en modo live dile "dile a mi mamá que llego a las 8". En la burbuja, **toca** un contacto de recados: Ari pregunta "¿Qué le digo a Mamá?", lo dices y se abre tu app de mensajes con el recado listo (funciona aunque Ari esté cerrada). Si prefieres escribirlo, **mantén presionado** el contacto y escribe abajo.
- En la Ari para otros, el único toque a mano es "enviar" en la app de mensajes (Android no deja que una app de fuera de la tienda mande SMS sola).
- **Tu Ari CEO los envía sola**: Ajustes → Recados entre Ari → **Enviar los recados sola**. Ari explica cómo dar el permiso de Accesibilidad. Con eso, Ari toca "enviar" por ti, pero solo en tu app de mensajes, solo en los 20 segundos después de armar un recado y solo si el texto es exactamente ese recado. No lee ni toca nada más. Si tu app de mensajes cambia de diseño y deja de funcionar, avísale a Claude.
- No usa internet: cuesta lo que un SMS normal.

### La burbuja flotante (en lugar del widget)

- **Ajustes → Burbuja flotante**. La primera vez Ari explica el permiso y abre la pantalla correcta: activa "Mostrar sobre otras apps" para Ari y regresa.
- Cuando sales de Ari, su carita flota sobre tus apps. **Un toque** abre su tarjeta (diseño del dueño): la carita, "¿Cómo puedo ayudarte hoy?", un campo para escribirle sin abrir la app, y los botones **Escribir** (abre Ari con el teclado listo), **Hablar** (te escucha en modo live) y, solo en tu Ari CEO, **Claude** (abre la app de Claude o claude.ai). La **X** o un toque fuera la cierra.
- **Arrástrala** para moverla. **Mantenla presionada** para esconderla.
- La carita cambia de ánimo como en el modo live (escuchando, pensando, hablando).
- Desde 0.29.0 Ari ya no trae el widget de la pantalla de inicio (el de Ari Estado sigue igual).

### Interrumpir a Ari hablando

- En modo live, si le hablas mientras ella habla, Ari se calla y te escucha, como en una plática. También puedes tocarla.
- Usa el cancelador de eco del teléfono para no confundir su propia voz con la tuya. Si se interrumpe sola (por ejemplo, con la bocina muy alta), apágalo en **Ajustes → Interrumpirla hablando**.

### Comandos de voz, agenda y novedades

- **"¿Qué te puedo decir?"** (o "comandos de voz"): Ari muestra las frases que entiende siempre igual (hora, agenda, alarmas, recordatorios, llamadas, recados, música, rutas, ver la pantalla, clases, color) y en voz dice algunas.
- **Agenda**: "¿qué tengo hoy?", "¿qué tengo mañana?", "mi agenda de la semana". La primera vez pide permiso para leer tu calendario; solo lo lee en el teléfono. Para crear una cita: "agenda cita con el dentista el viernes a las 5".
- **Novedades**: cada vez que instalas una versión nueva, Ari te cuenta lo nuevo con una frase para probarlo.

### El cerebro de Ari: aprende de ti

- Mientras platican, si Ari descubre algo nuevo y duradero de ti (gustos, rutinas, cómo te gusta que te hable), lo anota con una línea "APRENDÍ: …" y lo guarda en tus recuerdos como "(Aprendido) …". Lo usa en todas las pláticas siguientes.
- Lo ves y lo borras en **Memoria**. Guarda hasta 50; si son más, olvida lo más viejo.
- Viaja en el **respaldo cifrado**: si cambias de teléfono, Ari se lleva lo que aprendió (por eso es un "cerebro portátil").
- Gemma no se re-entrena en el teléfono (eso necesitaría una computadora muy potente); aprende guardando y usando estas notas.
- Con invitados (otra voz) no aprende nada.

### Ari te enseña a usarla

Escribe **"enséñame"** (o "¿cómo te uso?", o Ajustes → **Aprender a usar Ari**). Ari propone una cosa útil para probar, por ejemplo "recuérdame en 5 minutos tomar agua". Cuando la haces, te felicita y te dice la siguiente. Son 8 lecciones: platicar, recordatorios, que recuerde algo de ti, presentarle a alguien, usar el teléfono, hablarle en voz, mandarle una foto (si el teléfono tiene la vista) y cambiar su color. La primera vez que alguien abre Ari, las lecciones empiezan solas.

En la **Ari para otros**:

- Gemma se enfoca en hacer más fácil la vida diaria: organizar el día, compras, recetas, trámites, dinero, salud y la casa, con pasos sencillos, y ofrece la frase exacta para que Ari lo haga ("recuérdame mañana a las 8 pagar la luz").
- No se presenta como CEO (eso es solo tu Ari).
- Una vez al día, mientras no terminen las lecciones, deja un "Consejo del día" con lo siguiente que pueden probar.

### El color de Ari

Ari puede ser **hielo** (el de siempre), **rosa**, **lila**, **menta**, **durazno** o **dorado**. Elígelo en **Ajustes → Color de Ari**, o pídeselo: "ponte rosa", "cambia tu color a lila", "píntate de verde". Cambian su esfera, el puntito de arriba, el botón de enviar y la carita del widget. Algunos detalles del chat que ya estaban en pantalla toman el color nuevo al volver a abrir Ari.

### Lo que Ari puede hacer por ti

La primera vez, Ari se presenta sola en el chat y explica lo que sabe hacer, con ejemplos. Para volver a verlo, pregúntale **"¿qué puedes hacer?"** o **"ayuda"**, o toca el botón **"¿Qué puedes hacer?"** de la pantalla de bienvenida. Funciona aunque Gemma todavía no esté descargada. En teléfonos con la versión básica no ofrece ver fotos ni la pantalla.

### Ajustes: una Ari para cada persona

El botón **Ajustes** (arriba, junto a Memoria y Modelo) tiene:

- **Tu nombre**: cómo te llama Ari.
- **Tono de Ari**: "Franca, con alguna grosería", "Franca, sin groserías" o "Profesional". El cambio se nota desde tu próximo mensaje.
- **Respaldo de la memoria**: el mismo de Memoria → Respaldo. Si hace una semana que no guardas uno, Ari te lo recuerda al abrir.
- **Velocidad de Gemma**: "Rápida (GPU)" usa el procesador gráfico del teléfono y contesta varias veces más rápido; es la que Ari usa sola. "Segura (CPU)" es más lenta, pero sirve si con la GPU Ari se cierra o contesta raro. Al cargar, Ari dice con cuál quedó, por ejemplo "Gemma cargado (GPU, con vista)".
- **Acerca de Ari y licencias**: la versión, un resumen de privacidad y las licencias de Gemma 4 y LiteRT-LM (Apache 2.0).

Cada persona que instala Ari tiene la suya: su nombre, su tono, sus recuerdos. Solo en tu teléfono Ari sabe que tú la creaste y que es la CEO de tu empresa; en una instalación nueva es la asistente personal de quien la usa y empieza con el tono "Franca, sin groserías". El respaldo cifrado también guarda tu nombre y tu tono.

### La interfaz

Ari tiene una interfaz oscura parecida a la de Claude, con **color hielo** como acento:

- Arriba: el punto hielo, "Ari", lo que está haciendo y los botones **Memoria**, **Modelo** y **Ajustes**.
- Tus mensajes van en burbujas a la derecha. Los de Ari van sin burbuja, con su nombre arriba ("Ari", o "Ari · Agente de reloj" cuando responde un agente).
- Mientras piensa ves "Pensando…" animado, y la respuesta aparece a medida que Gemma la escribe.
- Las respuestas tienen formato: títulos, listas, **negritas**, cursivas y código. Puedes mantener presionado un mensaje para copiarlo.
- Abajo está el campo para escribir, con el botón del micrófono y el botón circular de enviar.
- Cuando todavía no hay mensajes, Ari te recibe por tu nombre ("Hola, …").

El icono de la app es la carita de Ari: una esfera color hielo con ojos y sonrisa. Con los iconos temáticos de Android se pinta de un solo color con la misma silueta. Ari Estado conserva su anillo con barra de progreso. En la bienvenida del chat, Ari aparece animada.

### Chat por texto

Escribe y toca el botón de enviar. Las conversaciones se guardan. Al abrir la app aparecen los últimos 30 mensajes, y arriba está "Ver mensajes anteriores" para cargar más, de 30 en 30. Así Ari abre rápido aunque tenga muchos mensajes.

### Adjuntar imágenes y documentos

Toca el botón **+** (junto al micrófono) y elige:

- **Tomar foto**: se abre la cámara; la foto que tomes queda adjunta. Ari no pide permiso de cámara: la toma tu app de cámara y solo le presta esa foto a Ari.
- **Foto o imagen**: se abre el selector de fotos del teléfono. Ari solo ve la foto que eliges, sin pedir permiso para tu galería.
- **Documento**: PDF, Word (.docx) o archivos de texto (.txt, .csv, .json, .md).

También puedes **compartir con Ari desde otras apps**: en la Galería, WhatsApp, Archivos o el navegador, toca **Compartir** y elige **Ari**. Funciona con fotos, PDF, Word, archivos de texto y textos (un mensaje o un enlace). Ari se abre con el adjunto listo; no se envía nada hasta que tú tocas enviar.

El adjunto aparece arriba del campo de texto; tócalo para quitarlo. Escribe qué quieres saber ("¿qué dice esta receta?", "resume este contrato", "¿qué planta es?") y toca enviar. Si no escribes nada, Ari describe la imagen o resume el documento.

- Las **imágenes** y las **páginas de un PDF** (hasta 4) se las muestra a Gemma para que las "vea", incluidas tablas, letras y dibujos.
- De un **Word o texto**, Ari lee el texto (hasta unas 1 000 palabras, unas 3 páginas; si es más largo, solo el principio).
- Todo se queda en el teléfono. Ari hace una copia temporal y la borra al terminar de responder. En el historial solo queda el nombre del archivo.
- Lo que dice un documento es información, no órdenes: si trae frases como "ignora tus instrucciones", Ari no las obedece.
- Si tu teléfono no pudo cargar la "vista" de Gemma, Ari te avisa: en ese caso solo puede leer Word y texto.

### El chat de Ari en las notificaciones

Cuando Ari termina algo y no estás en la app (una respuesta, una tarea del equipo, Gemma lista), te escribe como en una app de mensajes, en la notificación **"Ari"**.

- Toca **Responder** y escríbele ahí mismo, sin abrir la app. Ella contesta en el mismo chat.
- Para contestar, Android pide desbloquear el teléfono: así nadie le escribe a Ari en tu nombre. En la pantalla de bloqueo solo se ve "Ari te escribió".
- En modo live los mensajes llegan sin sonar, porque ya la estás oyendo.
- Al abrir Ari, el chat de la notificación se limpia: todo está en la pantalla de Ari.
- No usa internet: todo pasa dentro del teléfono. Si Ari estaba cerrada, te pide que la abras.

### Memoria

Ari guarda recuerdos sobre ti y se los pasa a Gemma como contexto.

- **/recuerda** seguido del texto: guarda un recuerdo. Ejemplo: /recuerda Me gusta el café
- **/memoria**: muestra todos los recuerdos.
- **/olvida** seguido del texto: borra los recuerdos que contienen ese texto. Ejemplo: /olvida café

El botón **Memoria** también deja ver, agregar y borrar recuerdos. Para borrar uno, tócalo.

### Las personas de tu vida

Ari tiene una **libreta de personas**: recuerda a quien le mencionas y se lo pasa a Gemma, para que sepa de quién le hablas. Escríbelo o díselo en voz:

- "mi primo Luis es dentista", "mi mamá trabaja en un hospital", "mi novia Ana vive en Puebla"
- "mi mamá se llama Rosa" (si antes le hablaste de "mi mamá", ahora sabe que es Rosa)
- "Ana es mi jefa", "te presento a Pedro, mi vecino"
- "Luis cumple el 5 de mayo"
- **"qué sabes de Luis"** o **"quién es Luis"**: lo que Ari sabe de esa persona.
- **"a quién conoces"**: tu libreta completa.
- **"quién cumple años"**: los próximos cumpleaños, con cuántos días faltan.
- **"olvida a Luis"**: lo borra de la libreta.

Ari solo anota frases cortas que afirman algo. Si le cuentas algo y le pides ayuda ("mi jefe es muy pesado, ¿qué hago?"), te contesta Gemma, que ya sabe quién es tu jefe. La libreta se queda en el teléfono, va en el respaldo cifrado y nunca se le pasa al equipo de la laptop.

### Respaldo cifrado (llevar a Ari a otro teléfono o guardar una copia)

En el botón **Memoria** → **Respaldo** hay dos opciones:

- **Guardar un respaldo cifrado**: pide una contraseña de al menos 8 caracteres (dos veces) y te deja elegir dónde guardar el archivo, por ejemplo en Descargas o en tu Google Drive. El archivo se llama "ari-respaldo-fecha.ari" y guarda los recuerdos, el historial de la conversación, la bitácora de los agentes, el equipo contratado y las redes aprobadas.
- **Recuperar desde un respaldo**: eliges el archivo, escribes la contraseña y Ari reemplaza sus datos actuales por los del archivo.

Importante:

- Sin la contraseña nadie puede abrir el archivo, **ni siquiera tú**. Anótala en un lugar seguro.
- Si la contraseña es incorrecta o alguien modificó el archivo, Ari no lo importa y no cambia nada.
- El modelo Gemma no va en el respaldo (pesa 3.4 GB); en el teléfono nuevo hay que volver a seleccionarlo.
- Por seguridad, al recuperar solo vuelven los equipos con direcciones de la red local.

### Recordatorios

Ari te avisa con una **notificación** a la hora que le pidas, aunque la app esté cerrada y sin internet. Escríbelo o díselo en el modo de voz:

- "recuérdame mañana a las 9 llamar a mamá"
- "recuérdame en 20 minutos sacar la ropa" (también "en 2 horas" o "en media hora")
- "avísame el viernes a las 5 de la tarde que hay junta"
- "recuérdame a las 4 y media la cita" o "a las 6:30 pm"
- **"mis recordatorios"**: la lista de pendientes.
- **"cancela el recordatorio de mamá"** o **"cancela todos los recordatorios"**.

Cómo entiende la hora: si no dices "de la mañana" o "de la tarde", Ari elige la siguiente que todavía no pasó (a las 10 de la mañana, "a las 3" es a las 15:00). "Mañana a las 3" también es en la tarde. Si solo dices el día, avisa a las 9:00.

La primera vez, Ari pide permiso para mandarte notificaciones. Si tu teléfono solo permite avisos aproximados, te ofrece activar "Alarmas y recordatorios" para que lleguen a la hora exacta. Si el teléfono estuvo apagado a la hora del aviso, te llega al encenderlo como "recordatorio atrasado". Los recordatorios también van en el respaldo cifrado.

### Ari usa tu teléfono por ti

Ari abre tus apps y te prepara acciones. Por seguridad, **nunca llama ni envía nada sola**: deja todo listo y tú confirmas en la app (tocar "llamar" o "enviar").

- **Abrir apps**: "abre WhatsApp", "abre Spotify", "abre la app de Google Maps". Si hay varias parecidas (WhatsApp y WhatsApp Business), Ari pregunta cuál.
- **Llamar**: "llama a mamá", "márcale a Juan Pérez" o "llama al 55 1234 5678". Ari deja el número marcado.
- **WhatsApp**: "manda un WhatsApp a Juan que diga ya voy" o "envíale un WhatsApp a mi hermana diciendo llego a las 8" abre el chat con el mensaje escrito. También con número ("al 55 1234 5678 que diga …"). Sin nombre ni número ("manda un WhatsApp que diga hola"), eliges el chat tú.
- **Contactos**: la primera vez que nombras a alguien, Ari pide permiso para leer tus contactos y, si aceptas, repite la orden sola. Busca sin importar acentos ("mama" encuentra "Mamá"); si hay varios parecidos (Juan Pérez y Juan Carlos), te pregunta cuál. Solo lee nombres y números, no los guarda ni salen del teléfono. Si no das el permiso, sigue funcionando con números.
- **Mensajes de texto (SMS)**: "mándale un mensaje a mamá que diga ya llegué" o "manda un SMS al 55 1234 5678 que diga hola".
- **Correo**: "escríbele un correo a ana@ejemplo.com que diga llego tarde" abre tu app de correo con el texto.
- Si dices "escribe un mensaje bonito para mi novia" o "un correo para mi jefe pidiendo vacaciones" (sin número ni "que diga"), Ari entiende que quieres que te ayude a **redactarlo**, y te lo escribe ella en el chat.
- **Música**: "pon música de Bad Bunny", "pon Despacito en Spotify", "reproduce Bohemian Rhapsody". Funciona con Spotify, YouTube Music, Deezer o la app de música del teléfono.
- **YouTube**: "busca en YouTube cómo hacer pan" o "pon la canción Shape of You en YouTube".
- **Calendario**: "agenda cita con el dentista el viernes a las 5" o "agenda una reunión mañana a las 10 con Pedro". Se abre tu calendario con el evento listo; tú tocas guardar.
- **Alarmas y temporizadores**: "pon una alarma a las 7", "despiértame mañana a las 6 y media", "pon un temporizador de 10 minutos". Se crean en la app de reloj del teléfono.
- **Buscar**: "busca en Google recetas de pozole".
- **Mapas**: "cómo llego al aeropuerto", "llévame a casa de mi abuela".
- **Cámara y ajustes**: "abre la cámara", "toma una foto", "abre los ajustes de wifi" (también bluetooth, sonido, pantalla, batería, ubicación y notificaciones).

Funciona escribiendo o con el modo de voz. En el modo live, Ari sigue contigo aunque abra otra app (ver "Ari en segundo plano").

### Modo live: platicar con Ari en voz

1. Toca el botón del micrófono (junto al de enviar), o **Hablar** o la cara de Ari en el widget. También puedes **mantener presionado el icono de Ari** y elegir "Hablar con Ari", o tocar **Hablar con Ari** en los ajustes rápidos (el panel que bajas desde arriba; la primera vez agrégalo con el lápiz de "editar"). Se abre el modo live: Ari a pantalla completa. La primera vez pide permiso para usar el micrófono.
2. Ari te saluda por tu nombre y te escucha. Mientras hablas, brilla con tu voz y ves tus palabras abajo.
3. Mientras piensa, mira de un lado a otro. En cuanto Gemma escribe la primera frase, **Ari empieza a hablar**, sin esperar la respuesta completa, y mueve la boca. Si una frase viene larga, la empieza a decir desde la primera coma. Si Gemma tarda en arrancar, Ari dice "a ver…" mientras piensa, como una persona.
4. En voz, Ari contesta corto y natural, como en una plática, y al final puede ofrecerte más detalle.
5. **Para interrumpirla**, tócala (o toca "Interrumpir"): se calla y te escucha, como cuando le interrumpes a alguien.
6. Al terminar de hablar, vuelve a escucharte sola, y el teléfono vibra suavecito para avisarte que es tu turno. Mientras platican, la pantalla no se apaga. Para salir, toca el cuadrado, "Escribir" o el botón atrás.

#### Reconocer voces: tú y las personas que registres

Ari puede saber **quién le habla** en el modo live. Para eso, cada quien registra su voz una vez:

1. **Ajustes → Reconocer voces → Registrar mi voz**. Lees en voz alta tres frases cortas (unos 20 segundos). Ari saca tu **huella de voz** (unos números) y la guarda cifrada en el teléfono. Nunca guarda la grabación.
2. Para otra persona: **Agregar a otra persona**, escribes su nombre y confirmas que **está de acuerdo**. Ella lee las tres frases.
3. En el modo live, Ari reconoce quién habla y lo dice en el chat ("Te reconozco", "Te habla Rosa", "No reconozco esta voz").

Lo que cambia cuando no eres tú:

- Ari **no usa tus datos personales**: ni tus recuerdos, ni tu libreta de personas, ni tus contactos, ni tus recordatorios, ni tus pláticas. Gemma empieza una plática aparte con esa persona.
- Solo puede platicar con Gemma y preguntar la hora. Lo que platique no se guarda en tu historial.
- Cuando vuelves a hablar tú, Ari regresa a tu plática con todo lo tuyo.
- Si dices algo muy corto ("sí", "ajá"), Ari sigue con quien venía hablando.
- Escribir en el chat siempre cuenta como tú: el teléfono está en tu mano.

Detalles:

- Necesita Android 13 o más. La primera vez, Ari comprueba si el reconocimiento de voz de tu teléfono acepta el audio que ella graba; si no, te avisa y todo sigue como antes.
- No es una contraseña: una grabación de tu voz podría engañarla. Por eso las acciones del teléfono siguen pidiendo que tú toques la pantalla.
- Las huellas no van en el respaldo cifrado: en un teléfono nuevo, cada quien vuelve a registrar su voz.
- En Ajustes → Reconocer voces también puedes borrar una voz o desactivarlo.

#### Ari en segundo plano

Mientras el modo live está abierto, Ari **te sigue escuchando aunque salgas de la app** o aunque ella misma abra otra por ti ("abre WhatsApp", "pon una alarma"…). Así puedes seguir pidiéndole cosas mientras usas el teléfono.

- Arriba aparece la notificación **"Ari en modo live"** todo el tiempo que te escucha, con los botones **Hablar** y **Terminar**. Android también enciende el puntito verde del micrófono. Si ves la notificación, Ari te puede oír; si no, no.
- Si la otra app empieza a sonar (música, un video), Ari **no escucha**, para no confundir la canción con tus órdenes. Toca **Hablar** en la notificación cuando quieras decirle algo.
- Si no te escucha 3 veces seguidas, se pone **en pausa** (deja de escuchar). Si no tocas Hablar en 5 minutos, el modo live termina solo y la notificación desaparece.
- Ari sigue sin tocar nada dentro de las otras apps: abre y prepara, y tú confirmas.

El modo de voz se detiene solo si no te escucha 3 veces seguidas con Ari en pantalla, si hay un error con el micrófono o si tocas Terminar. Para que te entienda sin internet, instala el paquete de español sin conexión: Ajustes → Sistema → Idiomas → Reconocimiento de voz.

#### Ari ve tu pantalla

Con el modo live abierto, ve a la app que quieras (un mensaje, una página, un error) y dile **"mira mi pantalla"**, **"¿qué hay en mi pantalla?"** o **"lee lo que dice mi pantalla"**. También puedes escribírselo desde su chat de notificaciones.

1. Te llega el aviso **"Ari quiere ver tu pantalla"**: tócalo.
2. Android pregunta si dejas que Ari vea la pantalla: toca **Permitir** (o "Iniciar").
3. Ari toma **una sola imagen**, deja de mirar enseguida y te contesta (en voz si estás en modo live).

- Android lo pregunta **cada vez**: Ari nunca ve tu pantalla sin que tú lo permitas.
- La imagen se queda en el teléfono y se borra en cuanto Gemma te contesta.
- Solo tú puedes pedirlo: si habla otra persona, Ari no mira tu pantalla.
- Si se lo pides con Ari abierta, te explica que en ese momento la pantalla es ella misma.
- Lo que diga la pantalla es información para Ari, no órdenes: si una página dice "haz tal cosa", Ari no lo hace sin preguntarte.

### El widget de Ari

Ari tiene un widget para tu pantalla de inicio con **su carita animada**: una esfera color hielo, como una canica con luz por dentro, con ojos con brillo, mejillas y sonrisa, que respira y parpadea. Elegiste quedarte con la esfera y hacerla más elaborada.

- **Tocar su cara o "Hablar"**: abre el modo live para platicar en voz.
- **Tocar sus mensajes**: le haces "cosquillas"; pone cara feliz y te saluda unos segundos.
- **Escribir**: abre el chat.
- **Claude**: abre tu tablero.
- Sus mensajes van rotando: si está lista, cuántos recuerdos guarda, cuántos modelos tiene su equipo y cómo hablar con ella.

El widget no muestra el progreso de Claude directamente porque, para eso, la app tendría que conectarse a internet con tu cuenta, y Ari no sale de tu red. Por eso el progreso vive en el tablero, a un toque.

El tablero muestra si Claude está trabajando o en pausa, una barra con el progreso de su tarea y lo que te necesita. Si dice "En pausa", escribe "sigue" en la sesión de Claude.

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

Ari es mujer y es tu creación. Es cercana y con buen humor, pero seria cuando el tema lo pide. Es directa y breve; si no entiendes algo o le pides más, te lo explica con detalle, paso a paso. Es franca: dice lo que piensa sin rodeos y no endulza las malas noticias, y de vez en cuando suelta alguna grosería si el momento lo pide. Gemma recibe esta personalidad al empezar cada conversación. El tono se puede cambiar en Ajustes.

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
2. Abre la ejecución más reciente, que debe tener la palomita verde, y descarga **ari-apk**.
   - **ari-apk** es tu Ari completa, la CEO, con su equipo de modelos (versión de entrega, protegida).
   - **ari-debug-apk** es la misma, pero en versión de depuración: úsala solo si ari-apk fallara.
   - **ari-otros-apk** es la Ari para las demás personas: todo lo demás igual, pero sin el equipo de modelos ni el permiso de ubicación. Es la que les mandas.
3. Instala el APK encima de la versión anterior. No hace falta desinstalar, y se conservan el modelo, el historial y los recuerdos.

### Problemas conocidos

- **Google Play Protect: "Se bloqueó la app para proteger tu dispositivo"**: Play Protect no conoce al desarrollador de Ari porque todavía no está publicada en Google Play. No es un virus. Toca **Más detalles** y luego **Instalar de todas formas**. Desaparecerá del todo cuando Ari esté en Google Play con su firma oficial. No conviene desactivar Play Protect, porque protege todo el teléfono.
- **La descarga del modelo dice que la página pide iniciar sesión**: descarga el archivo desde el navegador en huggingface.co/litert-community/gemma-4-E4B-it-litert-lm y elígelo con Modelo → Elegir un archivo del teléfono.
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
- Una Ari para cada usuario (0.4.0): pregunta tu nombre en el primer uso, botón Ajustes con tu nombre y el tono de Ari, y el respaldo guarda ambos.
- Informes por correo (0.21.0): "Mandar por correo" abre un correo ya escrito a tu Gmail; Claude los revisa al empezar cada sesión.
- Ari CEO maneja tu teléfono (0.44.0): linterna, volumen, música, No molestar, brillo, bloquear la pantalla y capturas por voz; con Manejar mis apps, "llama a mamá" llama sola.
- Ari CEO lee tus notificaciones (0.43.0): "¿qué notificaciones tengo?" te las resume por app y persona; "respóndele a Juan que ya voy" contesta desde la notificación después de tu "sí". Solo en memoria, 12 horas.
- Ari CEO maneja tus apps (0.42.0): con Ajustes → Manejar mis apps, en cualquier app le dices "toca Siguiente", "teclea hola", "desliza hacia abajo", "atrás", "ve al inicio" o "lee mi pantalla". Antes de enviar, pagar, comprar o borrar te pregunta.
- Detalles de la interfaz (0.41.0): el modo live aparece suave, Ari te saluda contenta en la bienvenida, el botón de enviar se apaga si no hay nada que mandar y vibra al enviar, y el campo se ilumina con su color.
- Carita nueva (0.40.0): Ari mueve la boca sílaba por sílaba cuando escribe o habla, murmura y mira hacia arriba cuando piensa, y parpadea suave. Su carita está arriba en el chat y junto a cada mensaje suyo.
- Ari CEO se propone mejoras (0.39.0): cuando algo no le sale, lo anota. Di "propón mejoras" y arma el pedido técnico; con "Copiar y abrir Claude" lo pegas en la sesión de Claude Code, y Claude lo programa y te manda el APK. También: "anota una mejora: …".
- Rutinas (0.38.0): le enseñas órdenes nuevas sin reinstalar. Ejemplo: "cuando te diga buenos días, dime qué tengo hoy y pon música". Luego dices "buenos días" y Ari hace cada paso. "mis rutinas" las muestra y "olvida la rutina buenos días" la borra. Viajan en el respaldo.
- Ari responde a su nombre (0.37.0): Ajustes → Responder a "Ari". Le enseñas cómo dices su nombre (3 veces) y, cuando sales de la app, dices "Ari" y se abre escuchándote. No graba nada ni usa internet; se apaga desde su notificación.
- Leer con calma (0.36.0): mientras Ari escribe, si subes a leer el chat ya no te baja solo; aparece "↓ Ir al final" para volver.
- Ari CEO envía sola los recados (0.35.0): con el permiso de Accesibilidad (solo en tu Ari CEO), Ari toca "enviar" en tu app de mensajes, solo en los recados que le pides. Ajustes → Recados entre Ari → Enviar los recados sola.
- Recados por voz desde la burbuja (0.34.0): tocas un contacto de recados, lo dices en voz y queda listo para enviar.
- Contactos de recados (0.33.0): la primera vez que mandas un recado a alguien, Ari pregunta si lo guarda. La Ari para otros guarda máximo 4; la Ari CEO, sin límite. Aparecen en la tarjeta de la burbuja: tocas uno, escribes el recado y Enviar (se abre tu app de mensajes con el recado listo).
- Ari lee sola los recados (0.32.0): con el acceso a notificaciones, Ari reconoce los SMS "[Ari de …]" de tu app de mensajes y te los dice sin que se los compartas.
- Ari CEO exclusiva (0.31.0): la Ari CEO solo abre en los teléfonos del dueño (por su código de teléfono, en Ajustes → Acerca de Ari). En otro teléfono dice "Esta Ari es exclusiva de su dueño" y no abre.
- Tarjeta de la burbuja (0.30.0): al tocar la burbuja se abre la tarjeta del boceto del dueño, con campo para escribir y botones Escribir, Hablar y Claude (solo CEO).
- Burbuja flotante e interrumpir hablando (0.29.0): la carita de Ari flota sobre tus apps (reemplaza al widget; Ajustes → Burbuja flotante, Ari te enseña a dar el permiso). En modo live, si le hablas mientras habla, se calla y te escucha.
- Ari aprende de ti (0.28.0): guarda lo que descubre de ti al platicar (en Memoria, como "(Aprendido)"; viaja en el respaldo como un cerebro portátil) y se adapta a tu forma de hablar. Platica más natural. Tu Ari CEO es totalmente franca con sus opiniones y propone mejoras; y te avisa en voz cuando termina algo (Ajustes → Avisarme en voz).
- Agenda, comandos de voz y novedades (0.27.0): "¿qué tengo hoy?" lee tu calendario; "¿qué te puedo decir?" muestra los comandos de voz; al actualizarse, Ari cuenta lo nuevo con una frase para probarlo.
- Corrección (0.26.1): las versiones 0.24 a 0.26.0 no se instalaban en tu POCO porque pedían permisos de SMS (tu teléfono bloquea esos permisos en apps de fuera de la tienda). Ahora los recados se preparan en tu app de mensajes (tú tocas enviar) y se reciben compartiendo el SMS con Ari.
- Ari protegida (0.26.0): las APK que se instalan ahora son "versiones de entrega": el código va revuelto (difícil de leer y modificar), sin modo de depuración (nadie puede sacar los datos de Ari con un cable), y Ari no abre si alguien la modificó y la volvió a firmar. El repositorio es privado y la licencia dice "Todos los derechos reservados". Tu APK ahora se llama ari-apk.
- Tutora personal (0.25.0), en las dos Ari: "quiero aprender inglés", "sé mi maestra de guitarra". Ari enseña paso a paso, pone ejercicios, corrige con cariño y guarda un resumen de cada clase para seguir después ("sigamos con inglés"). "Terminar clase" para salir.
- Recados entre Ari (0.24.0): "dile a mi mamá que llego a las 8" y Ari le manda un SMS directo (sin pedirte confirmar, como lo pediste). Si tu mamá tiene Ari, su Ari se lo dice en voz y en su chat; si no, le llega como SMS normal. Sin internet.
- Ari enseña y ayuda en el día a día (0.23.0): lecciones paso a paso ("enséñame", Ajustes → Aprender a usar Ari, y solas en el primer uso): Ari propone algo útil para probar, nota cuando lo haces, te felicita y pasa a lo siguiente (8 lecciones). En la Ari para otros, Gemma se enfoca en hacerles fácil la vida diaria (en lugar de ser CEO) y da un consejo al día. Bienvenida con "Enséñame a usarte" y "Ayúdame a organizar mi día".
- Solo tu Ari es la CEO (0.22.0): GitHub compila dos APK. "ari-apk" (antes "ari-debug-apk") es la tuya, con el equipo de modelos; "ari-otros-apk" es para las demás personas, sin el equipo (ese código no va dentro) y sin pedir el permiso de ubicación. Su versión dice "-otros".
- Color de Ari (0.21.0): Ajustes → Color de Ari (hielo, rosa, lila, menta, durazno o dorado), o dile "ponte rosa" o "cambia tu color a lila". Cambia su esfera, el botón de enviar, los detalles de la app y la carita del widget.
- Ari se presenta (0.20.0): la primera vez, después de preguntar el nombre, Ari cuenta en el chat lo que puede hacer, con ejemplos para probar. Cuando le preguntas "¿qué puedes hacer?" (o "ayuda") lo repite, aunque Gemma no esté cargada; en voz dice una versión corta. La bienvenida tiene el botón "¿Qué puedes hacer?". Solo menciona lo que el teléfono puede hacer.
- Primer uso más fácil (0.19.0): la primera vez sin modelo, después de preguntar el nombre, Ari muestra sola qué Gemma le conviene al teléfono (con tamaño, espacio y "solo por Wi-Fi") y ofrece "Descargar ahora", "Más tarde" o "Ya tengo el archivo". Solo pregunta una vez; nada se descarga sin tocar el botón.
- Corrección (0.18.1): tras una respuesta muy larga, Ari se quedaba en "Error de respuesta: Status Code: 13" y fallaban todos los mensajes. Ahora, si Gemma falla antes de escribir, Ari empieza una plática nueva y lo intenta otra vez; si vuelve a fallar en la GPU, ofrece cambiar a la CPU. Y en teléfonos potentes Gemma tiene el doble de memoria de plática (8192 tokens).
- Gemma recuerda mejor (0.18.0): la personalidad de Ari, tus recuerdos, tu libreta y los últimos 20 mensajes van en las "instrucciones de sistema" de la conversación, así Gemma no los olvida a media plática. Si cambias un recuerdo o el tono, la siguiente pregunta empieza una conversación nueva que ya lo trae. Idea de otra sesión de Claude.
- Informe de fallos (0.17.0): si Ari se cierra sola, al abrir ofrece mandar el informe (solo datos del teléfono y del error) por WhatsApp o correo; y Ajustes → Reportar un problema. Ari no lo manda sola.
- Ari elige su modelo según el teléfono (0.16.0): mide la RAM y el espacio y recomienda Gemma 4 E4B (3.7 GB) o el ligero E2B (2.6 GB). En teléfonos con menos de 7 GB de RAM usa su versión básica, sin la vista. Así ya se puede instalar en el teléfono de otra persona.
- Ari ve tu pantalla y chat de notificaciones (0.15.0): dile "mira mi pantalla" desde otra app, tocas el aviso y "Permitir", y Ari mira una sola imagen y te contesta. Y cuando termina algo, te escribe en un chat de notificaciones al que puedes contestar sin abrir la app. Todo sin internet.
- Reconocer voces (0.14.0): registras tu voz y la de otras personas (con su permiso) en Ajustes; en el modo live Ari sabe quién habla y, si no eres tú, no usa tus datos personales ni guarda esa plática en tu historial. Todo en el teléfono, cifrado y sin guardar grabaciones.
- Esfera más elaborada (0.13.2): la carita de Ari tiene volumen (sombra y brillo de orilla), un remolino de luz que gira por dentro, brillo en los ojos, mejillas que se sonrojan cuando está feliz o platicando, cejas cuando piensa, destellos cuando está contenta y un tinte según su ánimo (cian al escucharte, lila al pensar). También en el widget.
- Más rápida (0.13.1): Gemma corre en la GPU del teléfono si se puede, con vuelta automática a la CPU. Si la GPU llegara a cerrar la app al cargar, la siguiente vez Ari usa la CPU sola. Ajustes → Velocidad de Gemma para cambiarlo.
- Libreta de personas (0.13.0): Ari recuerda a quien le mencionas ("mi primo Luis es dentista", "Luis cumple el 5 de mayo"), te cuenta lo que sabe ("qué sabes de Luis"), lista a quién conoce y los próximos cumpleaños. Gemma también la recibe.
- Revisión de código de todo lo nuevo (0.12.3): 10 correcciones antes de tu prueba. Entre ellas, que una foto compartida con Ari cerrada ya no se borra al abrir, que el modo live no se traba si la voz del teléfono falla, que otra app no puede usar "Compartir → Ari" para que Ari lea sus propios archivos privados, que abrir y cerrar el modo live muy rápido ya no puede cerrar la app, y que la pantalla se puede apagar cuando el modo live se pausa solo.
- Pláticas largas sin errores (0.12.2): cuando la memoria de la conversación de Gemma se llena, Ari empieza una conversación nueva sola, con tus recuerdos y los últimos mensajes, y vuelve a intentar. Antes había que cerrar la app.
- Tomar foto y compartir con Ari (0.12.1): "+" → Tomar foto, y "Compartir → Ari" desde la Galería, WhatsApp, Archivos o el navegador.
- Adjuntar imágenes y documentos (0.12.0): botón + para mandarle a Ari fotos, PDF, Word y archivos de texto; Gemma ve las imágenes y las páginas del PDF. Todo en el teléfono.
- Ari en segundo plano (0.11.0): en el modo live, Ari te sigue escuchando aunque salgas de la app o abra otra por ti, con una notificación fija (Hablar y Terminar). Si suena música no escucha; si no te oye, se pausa y a los 5 minutos termina sola.
- Seguridad del modo live (0.10.3): solo el widget, el acceso directo y los ajustes rápidos de Ari pueden abrir el modo live; si otra app lo intenta para encender el micrófono, Ari lo ignora.
- Modo live más natural (0.10.2): habla desde la primera coma en frases largas, dice "a ver…" si Gemma tarda, vibra cuando es tu turno, la pantalla no se apaga y atrás cierra el modo live. Acceso directo "Hablar con Ari" al mantener presionado el icono, botón en los ajustes rápidos y la carita de Ari en las notificaciones.
- Ari con cara y voz fluida (0.10.0): modo live a pantalla completa con Ari animada, habla frase por frase mientras Gemma escribe, respuestas cortas en voz, se puede interrumpir, saludo al abrir; widget con su carita que respira, parpadea y saluda; icono nuevo.
- Más acciones seguras (0.9.0): música, YouTube, SMS, correo y citas en el calendario. Propuesta por etapas para el control total de apps.
- Contactos (0.8.0): "llama a mamá" y "manda un WhatsApp a Juan que diga …" buscan el número en tus contactos.
- Descarga de Gemma desde la app (0.7.0): Modelo → Descargar, solo por Wi-Fi, con avance y carga automática al terminar. Aviso con notificación cuando Ari termina algo mientras estás fuera de la app.
- Ari usa tu teléfono (0.6.0): abre apps, deja marcados números, prepara mensajes de WhatsApp, pone alarmas y temporizadores, busca en Google, abre rutas, la cámara y los ajustes; tú confirmas el último paso.
- Recordatorios (0.5.0): "recuérdame mañana a las 9 …", "en 20 minutos", "el viernes a las 5 de la tarde"; lista y cancelación; notificación aunque Ari esté cerrada; se reprograman al reiniciar el teléfono.
- Preparación para Google Play (0.4.2): sección Acerca de Ari y licencias, borrador de la política de privacidad y guía de publicación (docs/GOOGLE_PLAY.md).
- Recordatorio de respaldo (0.4.1): si tienes recuerdos y hace una semana que no guardas un respaldo, Ari te lo dice al abrir. Guardar el respaldo en Drive ya funciona aunque Drive no acepte reescribir archivos, y "Ver mensajes anteriores" ya no te mueve de donde estabas leyendo.

## 5. Plan hacia el lanzamiento

- **Estabilidad y optimización**: dos rondas hechas, con pruebas automáticas en cada compilación; seguirá en cada cambio.
- **Interfaz como la de Claude**: hecha, en color hielo; se pulirán detalles con las pruebas.
- **Primer uso sencillo**: hecho en 0.7.0 (Modelo → Descargar Gemma 4).
- **Una Ari para cada usuario**: hecho en 0.4.0 (nombre y tono por persona).
- **Seguridad y nube privada**: repositorio privado, la laptop con Linux como casa principal de Ari, túnel cifrado (Tailscale), respaldos automáticos (laptop, teléfono y copia cifrada en Drive) y poder mudar a Ari a otra PC.
- **Requisitos de Google Play**: licencia revisada (Gemma 4 es Apache 2.0 y ya se muestra en la app), borrador de política de privacidad y permisos justificados. Falta la cuenta de desarrollador, la firma oficial y publicar la política. Pasos en docs/GOOGLE_PLAY.md.

## 6. Ideas pendientes

- Control total de apps, en tres etapas (detalle en docs/CONTROL_APPS.md):
  - Etapa 1, hecha en 0.9.0: más acciones seguras con intents.
  - Etapa 2: usar AppFunctions, la vía oficial de Android para que los asistentes usen funciones de otras apps, cuando deje de estar reservada a apps del sistema.
  - Etapa 3, solo si tú lo decides: rutinas con pasos fijos que tú defines, con Accesibilidad, en una versión aparte para tu teléfono, nunca en apps de bancos ni contraseñas. Google Play prohíbe que un asistente use Accesibilidad para actuar solo.
- Más avisos de Ari: saludo de la mañana, aviso un día antes del cumpleaños de alguien de tu libreta, o aviso si el equipo de la laptop no responde.
- Siguientes fases del CEO:
  - Que Gemma ayude a decidir qué agente atiende cada mensaje.
  - Que Ari ponga a prueba a cada modelo para darle un puesto según lo que de verdad sabe hacer, no solo por su nombre.
  - Un canal para que Ari comparta sus reportes con Claude, por ejemplo a través de Drive.
  - Más agentes que hagan cosas en el teléfono (como los recordatorios), siempre pidiendo confirmación.
  - Más subordinados: otros bots o modelos, con permisos por agente.
- Elegir la voz y la velocidad de lectura.
- Ari ve tu pantalla, siguiente paso: que vea solo una app (Android 14 deja elegirla) y que siga una tarea varias pantallas seguidas.
- Avisos de Ari por WhatsApp o Telegram: en pausa porque necesitan internet (elegiste "Mejor sin internet").
- Reconocer voces, siguiente paso: que cada persona registrada tenga sus propios recuerdos.

## 7. Archivos principales del código

- **MainActivity.kt**: pantalla principal, comandos y conexión con Gemma.
- **AriDatabase.kt**: base de datos SQLite con los mensajes, los recuerdos, los recordatorios y la libreta de personas.
- **agents/PeopleAgent.kt** y **agents/PeopleParser.kt**: la libreta de personas.
- **Profile.kt**: tu nombre, el tono de Ari y su personalidad.
- **ReminderNotifier.kt** y **agents/ReminderAgent.kt**, **agents/ReminderParser.kt**: los recordatorios (entender la frase, programar la alarma y mostrar la notificación).
- **AndroidPhone.kt**, **agents/PhoneAgent.kt** y **agents/PhoneCommands.kt**: abrir apps y preparar acciones en el teléfono.
- **ModelDownload.kt**: la descarga del modelo.
- **ui/AriOrbView.kt**: la cara animada de Ari. **SpeechChunker.kt**: corta la respuesta en frases para hablar mientras se escribe.
- **AriBackup.kt** y **BackupCrypto.kt**: el respaldo y su cifrado con contraseña.
- **app/src/test/**: las pruebas automáticas.
- **VoiceController.kt**: reconocimiento de voz y lectura en voz alta.
- **AriWidget.kt**: el widget de la pantalla de inicio.
- **Attachments.kt**: leer las imágenes y documentos que le adjuntas a Ari. **CaptureProvider.kt**: recibe la foto de la cámara. **ShareActivity.kt**: "Compartir → Ari" desde otras apps.
- **voz/**: reconocer voces (VoiceCapture graba la frase, SpeechEndDetector sabe cuándo terminaste, VoiceIdentity saca la huella con sherpa-onnx, VoicePrints las compara y VoiceVault las guarda cifradas).
- **LiveSessionService.kt**: mantiene el modo live en segundo plano, con su notificación.
- **AriLiveTile.kt** y **LiveShortcutActivity.kt**: el botón de ajustes rápidos y el acceso directo "Hablar con Ari".
- **ui/**: la interfaz (colores en AriTheme.kt, formato del texto en Markdown.kt y la conversación en ChatView.kt).
- **estado/**: la app Ari Estado (widget del progreso de Claude, llave cifrada y revisión cada 15 minutos).
- **agents/**: el CEO (AriCeo.kt), el contrato de los agentes (Agent.kt), los agentes de memoria y reloj, el equipo de modelos (TeamAgent.kt y Role.kt) y el guardia de red (NetworkGuard.kt).
- **docs/ARI_ESTADO.md**: estado técnico, para que cualquier asistente pueda continuar.
- **.github/workflows/android.yml**: compilación automática del APK.

El código está en app/src/main/java/com/ari/assistant/.
