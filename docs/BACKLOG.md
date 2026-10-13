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

## Siguiente
- [autónomo] Llamar y escribir por nombre de contacto (requiere [permiso] de contactos al usarlo).

## Esperando al usuario
- [decisión] Control total de otras apps con Accesibilidad (en el tablero).
- [decisión] Qué mensajes manda Ari sola, además de los recordatorios (en el tablero).
- [decisión] Primer uso guiado: descargar el modelo exige que Ari se conecte a internet una vez (hoy no puede). Preguntar antes de empezar.
- [prueba] APK 0.3.1 con la personalidad nueva (en el tablero).
- [prueba] Laptop con Linux y equipo de modelos (en el tablero).

## Más adelante
- [permiso] Nube privada: túnel cifrado con Tailscale entre teléfono y laptop.
- [autónomo] Respaldos automáticos 3-2-1: laptop, teléfono y copia cifrada en Drive.
- [autónomo] Programa de Ari para Linux (casa principal en la laptop).
- [permiso] Google Play: cuenta de desarrollador y firma oficial (en el tablero). Licencia, política (borrador) y permisos ya preparados: docs/GOOGLE_PLAY.md.
- [decisión] Nombre de la empresa, cuando Ari esté bien diseñada.
