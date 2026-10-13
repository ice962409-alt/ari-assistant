# Publicar Ari en Google Play

Lista de pasos para el lanzamiento. Lo marcado [autónomo] lo hace Claude; lo demás necesita al usuario.

## Por qué importa

- Hoy Ari se instala "por fuera" (desde GitHub) y Google Play Protect la bloquea por ser de un desarrollador desconocido.
- Desde el 1 de octubre de 2026, en Brasil, Indonesia, Singapur y Tailandia los teléfonos certificados exigen un desarrollador verificado. Google planea extenderlo a todo el mundo en 2027. Publicar con una cuenta verificada resuelve las dos cosas.
- Opción intermedia: una cuenta de "distribución limitada" deja compartir la app con hasta 20 dispositivos, sin identificación oficial ni pago. Sirve para probar con amigos antes del lanzamiento.

## 1. Cuenta de desarrollador [permiso]

- Crear la cuenta de Google Play Console (pago único y verificación de identidad).
- Decidir si va a nombre personal o de la empresa (la empresa todavía no tiene nombre).

## 2. Firma oficial [permiso]

- Crear una **clave de subida** (upload key) y guardarla en un lugar seguro, nunca en el repositorio.
- Activar **Play App Signing**: Google guarda la clave final.
- Guardar la clave de subida como secreto de GitHub Actions para compilar la versión de lanzamiento.
- La clave de depuración actual (`app/debug.keystore`) es solo para pruebas.

## 3. Licencias [hecho]

- Gemma 4 se publica con licencia **Apache 2.0**, que permite el uso comercial. Hay que incluir el texto de la licencia: está en la app, en Ajustes → Acerca de Ari y licencias.
- LiteRT-LM también es Apache 2.0.
- El archivo del modelo no va dentro de la app (pesa 3.4 GB); por eso falta decidir cómo se descarga (pendiente en el tablero).

## 4. Política de privacidad [autónomo + decisión]

- Borrador en `docs/POLITICA_PRIVACIDAD.md`. Falta: nombre del desarrollador, correo de contacto y edad mínima.
- Google Play pide un enlace público a la política. Se puede publicar como una página aparte.

## 5. Seguridad de los datos (formulario de Play Console) [autónomo]

Respuestas según cómo funciona Ari hoy:

- ¿Recopila o comparte datos con terceros? No. Todo se queda en el teléfono.
- ¿Los datos se cifran en tránsito? No aplica: Ari no envía datos a internet. Con el equipo de la red local la conexión es HTTP sin cifrar, solo en redes aprobadas por el usuario.
- ¿El usuario puede pedir que se borren? Sí, desde la app o borrando los datos de la app.

## 6. Justificación de permisos [autónomo]

- **Micrófono (RECORD_AUDIO)**: modo de voz, solo cuando el usuario lo activa.
- **Internet y estado de red**: hablar con los modelos del equipo en la red local.
- **Estado del Wi-Fi y ubicación precisa**: Android exige la ubicación para leer el nombre de la red Wi-Fi; Ari lo usa solo para saber si la red está aprobada. No se usa la ubicación.
- **Notificaciones (POST_NOTIFICATIONS)**: avisar los recordatorios.
- **Alarmas exactas (SCHEDULE_EXACT_ALARM)**: que los recordatorios lleguen a la hora pedida; si el usuario no lo activa, llegan con un posible retraso.
- **Iniciar al encender (RECEIVE_BOOT_COMPLETED)**: volver a programar los recordatorios después de reiniciar.
- **Poner alarmas (SET_ALARM)**: crear alarmas en la app de reloj cuando el usuario lo pide.
- **Ver apps instaladas (queries de MAIN/LAUNCHER)**: abrir la app que el usuario nombra. No se usa QUERY_ALL_PACKAGES.
- **Tráfico sin cifrar (usesCleartextTraffic)**: Ollama y LM Studio solo hablan HTTP; Ari lo limita a direcciones privadas de redes aprobadas.

## 7. Requisitos técnicos [autónomo]

- **Nivel de API**: Google Play exige apuntar a una versión reciente de Android. Hoy Ari usa targetSdk 35; hay que comprobar el mínimo vigente al publicar y subir a 36 si hace falta (requiere probar en el teléfono).
- **Formato**: Google Play pide un paquete AAB (`bundleRelease`) en lugar de APK.
- **Ficha de la tienda**: icono de 512 px, gráfico de 1024 × 500, capturas de pantalla y descripción.
