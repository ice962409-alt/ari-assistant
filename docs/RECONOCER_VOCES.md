# Plan: que Ari reconozca voces

Petición del usuario: "Que tenga guardado mi voz y me reconozca a mí y a otras personas".

## Cómo se haría (todo dentro del teléfono)

- **Modelo de hablante local**: sherpa-onnx (Apache 2.0, corre en Android sin internet) con un modelo de huella de voz de unos 25 MB (3D-Speaker ERes2Net o WeSpeaker ResNet34, ambos Apache 2.0). Va dentro del APK o se descarga una vez, como Gemma.
- **Micrófono compartido**: hoy el reconocimiento de Android usa el micrófono solo. Desde Android 13 (`RecognizerIntent.EXTRA_AUDIO_SOURCE`) Ari puede grabar ella misma con `AudioRecord` y pasarle el audio al reconocedor; así la misma frase sirve para entender lo que dices y para saber quién lo dice. En Android 12 o menos, sin reconocimiento de voces. Hay que comprobar en el POCO que el reconocedor de Google acepte el audio así.
- **Registro**: "Ari, aprende mi voz" → lees 3 a 5 frases (unos 30 s) → se guarda el promedio de las huellas (192 a 256 números), cifrado con una llave del Keystore, en la base de datos. Nunca la grabación.
- **Otras personas**: "Ari, ella es mi mamá, aprende su voz" (con su permiso, que Ari pide en voz alta). Cada persona tiene nombre y, más adelante, su propia memoria.
- **Reconocer**: en cada frase, similitud coseno contra las huellas guardadas; por encima del umbral, es esa persona. Si no se parece a nadie, "invitado".

## Para qué se usaría

- Saludar y hablarle a cada quien por su nombre.
- **Privacidad**: si quien habla no eres tú, Ari no usa tus recuerdos ni tu historial (Gemma solo recibe los de esa persona o ninguno).
- No sirve como contraseña: una grabación podría engañarla. Las acciones del teléfono siguen pidiendo que la persona confirme tocando la pantalla.

## Decisiones del usuario (en el tablero)

1. Si se hace, y si es solo para ti o también para otras personas.
2. Qué pasa cuando habla alguien desconocido: no usar tus recuerdos (recomendado), o pedir que se identifique.

## Riesgos

- Ruido, gripa o el altavoz del coche bajan la precisión: el umbral se ajusta con tus pruebas.
- Google Play: la huella de voz es dato biométrico y debe declararse en la sección de seguridad de datos y en la política de privacidad.
- Tamaño de la app: unos 25 MB de modelo más 10 a 15 MB de librerías.
