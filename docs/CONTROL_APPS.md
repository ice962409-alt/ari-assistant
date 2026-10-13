# Propuesta: que Ari controle otras apps de forma segura

Investigación de Claude, 13 de octubre de 2026. El usuario eligió "solo acciones seguras por ahora", pero quiere llegar al control total con seguridad. Este documento resume qué permite hoy Android y Google Play, y el plan por etapas.

## Lo que encontré

### 1. Intents (lo que Ari ya usa)

- Ari le pide a otra app que haga algo y esa app sigue al mando: el usuario confirma el último paso.
- Funciona en todos los teléfonos, no necesita permisos peligrosos y Google Play lo permite sin problema.
- Límite: solo sirve para lo que las apps exponen (abrir, marcar, compartir, buscar, reproducir, crear un evento…). No permite tocar botones arbitrarios.

### 2. AppFunctions (la vía oficial del futuro)

- Es la API nueva de Android para que las apps ofrezcan funciones concretas a los asistentes de IA ("crear nota", "agregar a la lista", "reproducir"), como herramientas. Google la compara con MCP en el teléfono y se ejecuta en el propio dispositivo.
- Estado: vista previa experimental, solo Android 16 o superior, y por ahora solo unas cuantas apps y agentes del sistema (como Gemini) pueden llamar funciones de otras apps. Según un proyecto de la comunidad, el permiso EXECUTE_APP_FUNCTIONS está reservado a apps privilegiadas del sistema; no lo confirmé en la documentación oficial.
- Conclusión: hoy Ari no puede usarla, pero es el camino correcto. Hay que revisarla cada cierto tiempo.

### 3. Accesibilidad (control total de la pantalla)

- Permite ver la pantalla y tocar botones en cualquier app.
- La política de Google Play dice que un asistente general no cuenta como herramienta de accesibilidad, y prohíbe usar la API de accesibilidad para que una app inicie, planee y ejecute acciones de forma autónoma. Sí permite automatización basada en reglas con un guion fijo escrito por una persona (con aviso y consentimiento destacados).
- Riesgos: la app ve todo (bancos, contraseñas mientras se escriben) y un texto en pantalla podría intentar engañar a la IA (inyección de instrucciones).

## Plan propuesto

### Etapa 1 (ahora, sin riesgos): más acciones con intents

Ampliar lo que Ari ya hace, siempre con confirmación del usuario en la app:

- Música: "pon música de Bad Bunny" (búsqueda en Spotify, YouTube Music o la app de música).
- YouTube: "busca en YouTube cómo hacer pan".
- Mensajes SMS a contactos: "mándale un mensaje a mamá que diga ya llegué".
- Correo: "escríbele un correo a ana@ejemplo.com que diga …".
- Calendario: "agenda una cita con el dentista el viernes a las 5" (se abre el calendario con el evento listo).

### Etapa 2 (cuando Android lo abra): AppFunctions

- En cuanto los asistentes de terceros puedan llamar AppFunctions, Ari las usará: cada app decide qué funciones ofrece y con qué límites, y Android controla el permiso.

### Etapa 3 (solo si el usuario lo decide): rutinas con Accesibilidad, fuera de Google Play

Si el usuario aún quiere tocar botones dentro de apps antes de la etapa 2, la forma más segura es:

- **Rutinas escritas por el usuario**, no decisiones autónomas de la IA: el usuario graba o describe los pasos una vez ("abre la app del súper, toca Repetir pedido") y Ari solo los repite. Esto es lo que la política de Google Play permite como automatización basada en reglas.
- **Lista de apps permitidas** elegida por el usuario; todo lo demás queda bloqueado.
- **Nunca** en apps de bancos, pagos, contraseñas o autenticación; las pantallas protegidas (por ejemplo, las que no dejan hacer capturas) se ignoran.
- **Confirmación** antes de cada rutina, con la lista de pasos visible, y un botón para detener todo.
- Ari no obedece instrucciones que aparezcan en la pantalla: solo las del usuario.
- Iría en una versión aparte ("Ari personal") para el teléfono del usuario, sin afectar la versión que se publique en Google Play.

## Recomendación

Hacer ya la etapa 1, vigilar la etapa 2 y dejar la etapa 3 como decisión del usuario.

## Fuentes

- Google Play, política de uso de la API de accesibilidad: https://support.google.com/googleplay/android-developer/answer/10964491
- Android, Overview of AppFunctions: https://developer.android.com/ai/appfunctions
- InfoQ, Android AppFunctions para agentes (marzo de 2026): https://www.infoq.com/news/2026/03/android-appfunctions-agents
- Proyecto AppFunctionsPilot (nivel del permiso, comunidad): https://github.com/FilipFan/AppFunctionsPilot
