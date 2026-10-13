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

Ari guarda recuerdos sobre ti y se los pasa a Gemma en cada conversación, junto con los últimos 20 mensajes. Cuando agregas u olvidas un recuerdo, Gemma lo tiene en cuenta desde el siguiente mensaje.

- **/recuerda** seguido del texto: guarda un recuerdo. Ejemplo: /recuerda Me gusta el café
- **/memoria**: muestra todos los recuerdos.
- **/olvida** seguido del texto: borra los recuerdos que contienen ese texto. Ejemplo: /olvida café

El botón **Memoria** también deja ver, agregar y borrar recuerdos. Para borrar uno, tócalo.

### Modo de voz

1. Toca **Voz** (el botón del micrófono). La primera vez, Ari pide permiso para usar el micrófono.
2. Habla. Ari escucha, responde con Gemma, lee la respuesta en voz alta y vuelve a escucharte.
3. Toca **⏹ Detener** para terminar.

El modo de voz se detiene solo si no te escucha 3 veces seguidas, si hay un error con el micrófono o si sales de la app. Para que te entienda sin internet, instala el paquete de español sin conexión: Ajustes → Sistema → Idiomas → Reconocimiento de voz.

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

### PR #4 — Modo de voz (fusionado)

- https://github.com/javierhedzd-ops/ari-assistant/pull/4
- Botón Voz con conversación continua: escuchar, responder, leer en voz alta.
- Reconocimiento y lectura de voz con los servicios de Android, sin APIs de pago.
- Probado en el teléfono: funciona.

### PR #2 — Primera versión de la memoria (cerrado sin fusionar)

- https://github.com/javierhedzd-ops/ari-assistant/pull/2
- Se hizo en paralelo al PR #3 y quedó duplicado. Sus mejoras se rehicieron sobre la versión actual en un PR nuevo.

### PR #5 — Más contexto para Gemma y estabilidad (abierto, pendiente de probar y fusionar)

- https://github.com/javierhedzd-ops/ari-assistant/pull/5

- Gemma recibe los recuerdos y los últimos 20 mensajes como contexto de toda la conversación, no solo en el primer mensaje.
- Los cambios en los recuerdos se aplican desde el siguiente mensaje.
- Girar el teléfono ya no recarga el modelo.
- Al elegir otro modelo, se libera el anterior de la memoria.
- GitHub Actions compila también las ramas de trabajo y los PR.
- Compila correctamente. Falta probarlo en el teléfono.

## 5. Ideas pendientes

- Que Ari empiece a hablar mientras Gemma escribe, en vez de esperar la respuesta completa.
- Comandos por voz sin decir "/": "recuerda…", "olvida…".
- Elegir la voz y la velocidad de lectura.

## 6. Archivos principales del código

- **MainActivity.kt**: interfaz, chat, comandos y conexión con Gemma.
- **AriDatabase.kt**: base de datos SQLite con los mensajes y los recuerdos.
- **VoiceController.kt**: reconocimiento de voz y lectura en voz alta.
- **docs/ARI_ESTADO.md**: estado técnico, para que cualquier asistente pueda continuar.
- **.github/workflows/android.yml**: compilación automática del APK.

Los tres archivos .kt están en app/src/main/java/com/ari/assistant/.
