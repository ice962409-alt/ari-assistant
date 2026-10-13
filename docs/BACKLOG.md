# Lista de trabajo de Ari

Etiquetas:
- **[autónomo]**: Claude lo hace sin esperar al usuario.
- **[permiso]**: necesita que el usuario apruebe algo (una solicitud de permiso, una acción en su cuenta).
- **[decisión]**: lo decide el usuario (gustos, personalidad de Ari, rumbo de la empresa).
- **[prueba]**: hay que probarlo en el teléfono o la laptop del usuario.

Todo lo que no es [autónomo] se publica en el tablero "Pendientes de Ari" (https://claude.ai/artifact/7kCcA7Ppg1tGW1HDATrfyB) y se espera la respuesta ahí.

## En curso
- Nada en este momento: lo siguiente espera decisiones del usuario.

## Hecho recientemente
- [autónomo] Respaldo cifrado de la memoria de Ari (0.3.0). Falta [prueba] en el teléfono.
- [autónomo] Estabilidad, segunda ronda: pruebas automáticas, guardado en segundo plano, WAL e historial por partes.
- [decisión] Personalidad: franca, con groserías de vez en cuando (0.3.1).
- [autónomo] Una Ari para cada usuario: nombre y tono por persona (0.4.0).
- [autónomo] Recordatorios con notificación (0.5.0). Falta [prueba] en el teléfono.
- [autónomo] Ari abre apps y prepara acciones (0.6.0). Falta [prueba] en el teléfono.
- [autónomo] Descarga de Gemma desde la app y aviso de tareas terminadas (0.7.0). Falta [prueba].
- [autónomo] Llamar y escribir a contactos por nombre (0.8.0). Falta [prueba].
- [autónomo] Ari con cara, modo live y voz fluida (0.10.0). Falta [prueba].
- [autónomo] Modo live más natural y accesos rápidos (0.10.2). Falta [prueba].
- [autónomo] Seguridad: solo Ari puede abrir su modo live (0.10.3).
- [autónomo] Ari en segundo plano en el modo live (0.11.0), pedido por el usuario. Falta [prueba].
- [autónomo] Adjuntar imágenes y documentos (0.12.0), pedido por el usuario. Falta [prueba].
- [autónomo] Tomar foto y "Compartir → Ari" (0.12.1). Falta [prueba].
- [autónomo] Libreta de personas (0.13.0), pedida por el usuario. Falta [prueba].
- [autónomo] Gemma en la GPU (0.13.1), por "responde lento". Falta [prueba].
- [autónomo] Esfera más elaborada (0.13.2), decisión del usuario. Falta [prueba].
- [autónomo] Reconocer voces (0.14.0), decidido por el usuario. Falta [prueba] (sobre todo si el reconocedor del POCO acepta el audio de Ari).
- [autónomo] Contexto de Gemma con ConversationConfig (0.18.0), idea de otra sesión de Claude (rama ccr-3d48bcea-ddppo3, commit bd7e6a2). Falta [prueba]: que siga recordando y que la primera respuesta no tarde mucho más.
- [autónomo] Protección: versiones de entrega con R8, sin depuración, detección de copias modificadas y licencia privada (0.26.0), pedido por el usuario. Falta [prueba]: que ari-apk funcione igual que antes (si algo falla, usar ari-debug-apk y avisar). Para Google Play: crear la clave de subida y guardar su huella en AppIntegrity.
- [autónomo] Tutora personal de cualquier tema (0.25.0), pedido por el usuario. Falta [prueba]. Siguiente: voz en inglés para las frases en ese idioma.
- [autónomo] Recados entre Ari por SMS (0.24.0), decisión del usuario (envío directo sin confirmar). Falta [prueba] entre dos teléfonos con Ari. Pendiente: Google Play restringe los permisos de SMS.
- [autónomo] Lecciones para aprender a usar Ari y enfoque en la vida diaria en la versión para otros (0.23.0), pedido por el usuario. Falta [prueba] con el tester.
- [autónomo] Dos versiones: completa (CEO, del dueño) y para otros, sin equipo (0.22.0), decisión del usuario. Falta [prueba] de ari-otros-apk en el teléfono del tester.
- [autónomo] Color de Ari (0.21.0), pedido por el usuario. Falta [prueba] (sobre todo cómo se ve la carita del widget en cada color).
- [autónomo] Ari se presenta y dice lo que puede hacer (0.20.0), pedido por el usuario. Falta [prueba].
- [autónomo] Informe de fallos para quien prueba (0.17.0), pedido por el usuario. Falta [prueba].
- [autónomo] Elegir el modelo según el teléfono y versión básica (0.16.0), pedido por el usuario (para el teléfono de otra persona). Falta [prueba] en otro teléfono.
- [autónomo] Ari ve tu pantalla (0.15.0), pedido por el usuario. Falta [prueba].
- [autónomo] Chat de Ari en las notificaciones (0.15.0), pedido por el usuario ("Mejor sin internet"). Falta [prueba].
- [autónomo] Siguiente para voces: recuerdos propios de cada persona registrada.
- [autónomo] Siguiente para la libreta: aviso un día antes de cada cumpleaños y verla y editarla en una pantalla (botón Memoria).
- [autónomo] Siguiente para adjuntos: audios (Gemma 4 también entiende audio) y varias imágenes a la vez.
- [decisión] Burbuja flotante con la carita de Ari sobre otras apps (necesita el permiso "mostrar sobre otras apps"). Proponer después de la prueba de 0.11.0.
- [autónomo] Investigación de control total (docs/CONTROL_APPS.md) y acciones de música, YouTube, SMS, correo y calendario (0.9.0).

## Siguiente
- [autónomo] Revisar AppFunctions cada cierto tiempo (etapa 2 del control de apps).

## Esperando al usuario
- [decisión] Etapa 3 del control de apps: rutinas con Accesibilidad (en el tablero).
- [prueba] APK más reciente (en el tablero).
- [prueba] Laptop con Linux y equipo de modelos (en el tablero).

## Más adelante
- [permiso] Nube privada: túnel cifrado con Tailscale entre teléfono y laptop.
- [autónomo] Respaldos automáticos 3-2-1: laptop, teléfono y copia cifrada en Drive.
- [autónomo] Programa de Ari para Linux (casa principal en la laptop).
- [permiso] Google Play: cuenta de desarrollador y firma oficial. El usuario dijo "más adelante": primero probar todo. Licencia, política (borrador) y permisos ya preparados: docs/GOOGLE_PLAY.md.
- [decisión] Nombre de la empresa, cuando Ari esté bien diseñada.
