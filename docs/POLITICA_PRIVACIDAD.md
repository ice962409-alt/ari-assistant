# Política de privacidad de Ari (borrador)

Última actualización: 16 de octubre de 2026

Este es un borrador preparado por Claude para el lanzamiento en Google Play. Antes de publicarlo hay que completar los datos marcados entre corchetes y, si es posible, que lo revise una persona con conocimientos legales.

## Quiénes somos

Ari es una asistente personal con inteligencia artificial que funciona dentro de tu teléfono. La desarrolla [nombre de la empresa o del desarrollador]. Contacto: [correo de contacto].

## Qué datos usa Ari

- **Tus mensajes y las respuestas de Ari**: se guardan en tu teléfono para que la conversación continúe.
- **Tus recuerdos**: lo que le pides a Ari que recuerde.
- **Tu nombre y el tono que elegiste**: para personalizar a Ari.
- **Tus recordatorios**: el texto y la hora, para avisarte con una notificación.
- **Tus contactos**, solo si le pides a Ari llamar o escribir a alguien por su nombre y das el permiso: lee el nombre y el número para encontrarlo. No los copia ni los guarda.
- **Audio del micrófono**, solo mientras usas el modo de voz: lo convierte a texto el servicio de reconocimiento de voz de tu teléfono. Ari no guarda grabaciones.
- **El nombre de tu red Wi-Fi**, solo si usas el equipo de modelos: Android exige el permiso de ubicación para leerlo. Ari no usa ni guarda tu ubicación.

## Dónde se guardan

Todo se guarda **solo en tu teléfono**. Ari no tiene servidores, no tiene cuentas y no envía tus conversaciones, tus recuerdos ni tu nombre a internet. El modelo de IA (Gemma) también funciona dentro del teléfono.

La única conexión a internet es la **descarga del modelo**, si tú la pides: el gestor de descargas de Android baja el archivo desde Hugging Face (huggingface.co). Como en cualquier descarga, ese sitio ve la dirección de internet de tu teléfono, pero no recibe ningún dato tuyo de Ari.

## Con quién se comparten

- **Con nadie por defecto.**
- **Equipo de modelos (opcional)**: si tú apruebas una computadora de tu red local, Ari le envía la tarea que le encargas, pero nunca tus recuerdos. Esa conexión no sale de tu red.
- **Reconocimiento de voz**: lo hace el servicio de voz instalado en tu teléfono (por ejemplo, el de Google), con sus propias reglas de privacidad. Puedes instalar el paquete de idioma sin conexión para que funcione sin internet.
- **Informes de fallos automáticos (opcional, desde 0.57.0)**: solo si los aceptas, Ari manda por internet los datos técnicos de un fallo: el error, la versión de Ari, el modelo y la versión de Android del teléfono, la memoria y un número al azar de la instalación que no se relaciona contigo. Nunca pláticas, recuerdos, contactos, fotos, nombres ni tu voz. Van al intermediario de quien hace Ari (Cloudflare), que los guarda como fichas en GitHub. Se apagan en Ajustes. Para Google Play se declaran como "registros de fallos" y "diagnóstico", opcionales y no vinculados a la persona.
- **Respaldos**: si guardas un respaldo, va cifrado con tu contraseña al lugar que tú elijas (por ejemplo, tu Google Drive). Ni nosotros ni nadie sin tu contraseña puede leerlo.
- **Búsquedas en internet**: esta versión de Ari no busca en internet. (Nota interna, no publicar: las búsquedas existen solo en la ARI privada del dueño. Si algún día llegan a la versión pública, aquí hay que decir que se envía solo el texto de la búsqueda a DuckDuckGo, Wikipedia u Open-Meteo, que esos servicios ven la dirección de internet del teléfono, y que la búsqueda automática la redacta el modelo sin preguntar.)

## Publicidad y análisis

Ari no muestra publicidad, no usa herramientas de análisis ni rastreo y no vende datos.

## Cómo borrar tus datos

- Borra recuerdos desde Memoria o diciéndole a Ari "olvida …".
- Para borrar todo: Ajustes del teléfono → Apps → Ari → Almacenamiento → Borrar datos, o desinstala la app.

## Menores de edad

Ari no está dirigida a menores de [13] años.

## Cambios

Si esta política cambia, se publicará aquí con la fecha nueva.
