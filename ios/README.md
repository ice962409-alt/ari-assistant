# Ari para iPhone

Primera versión de Ari para iOS (0.1.0). Es una app nativa en Swift y SwiftUI que corre Gemma 4 en el iPhone con LiteRT-LM, la misma tecnología que la versión de Android.

## Qué hace esta versión

- **Chat con Gemma en el iPhone:** la respuesta aparece mientras Gemma la escribe. Usa la GPU (Metal) y, si no se puede, la CPU.
- **Primer uso:** pregunta tu nombre y el tono (franca con alguna grosería, franca sin groserías o profesional). Luego recomienda el modelo según la RAM y el espacio del iPhone.
- **Descarga del modelo:** solo por Wi-Fi, en segundo plano (sigue aunque bloquees el iPhone). También puedes elegir un archivo `.litertlm` desde Archivos.
  - Gemma 4 E4B (unos 3.7 GB): iPhone con 8 GB de RAM o más (iPhone 15 Pro y posteriores).
  - Gemma 4 E2B (unos 2.6 GB): los demás.
- **Memoria:** "recuerda que …", "qué recuerdas de mí", "olvida …", o `/recuerda`, `/memoria`, `/olvida`. También desde el botón Memoria. Gemma recibe los recuerdos y los últimos 20 mensajes en cada conversación.
- **Historial:** las pláticas se guardan en el iPhone, cifradas por iOS mientras el teléfono está bloqueado.
- **Informes de fallos, con permiso:** Ari pregunta una vez. Si aceptas, los cierres y congelamientos (MetricKit) y los errores del modelo se mandan al intermediario de `ari-informes`. Solo datos técnicos, nunca mensajes, recuerdos ni nombres.
- **No inventa funciones:** Gemma recibe la lista exacta de lo que sabe hacer esta versión.

## Lo que todavía no tiene (y por qué)

- **Abrir apps, llamar, mandar mensajes, alarmas:** iOS no deja que una app haga eso por ti como Android. Lo posible (por ejemplo, con Atajos de Siri) llegará en una versión siguiente.
- **Recordatorios, modo voz y widget:** pendientes para las siguientes versiones.
- **Ver fotos y documentos:** pendiente.

## Cómo se compila

GitHub Actions compila la app en cada cambio de `ios/` (workflow "Build Ari iOS", en una Mac de GitHub): corre las pruebas de `AriCore` y compila la app para iPhone **sin firmar**. Eso comprueba que el código es correcto, pero una app sin firmar no se puede instalar en un iPhone.

Nota: las Macs de GitHub Actions gastan los minutos gratis 10 veces más rápido que las de Linux en repositorios privados.

En una Mac:

```
brew install xcodegen
cd ios && xcodegen generate
open Ari.xcodeproj
```

## Cómo instalarla en iPhones (TestFlight)

Apple no deja pasar apps como un APK: para que tú y otras personas la instalen hace falta TestFlight.

1. **Cuenta de Apple Developer** (de pago, anual): https://developer.apple.com/programs/
2. **Identificador de la app:** en Certificates, Identifiers & Profiles crea el App ID `com.javierhedzd.ari` (o el que elijas: cámbialo en `project.yml`) y activa la capacidad **Increased Memory Limit**. Sin ella, iOS cierra Ari al cargar Gemma.
3. **App Store Connect:** crea la app "Ari" con ese identificador.
4. **Firmar y subir:** desde Xcode en una Mac (Product → Archive → Distribute → TestFlight), o automatizado con GitHub Actions guardando el certificado y una llave de App Store Connect como secretos del repositorio. Claude puede preparar ese workflow cuando tengas la cuenta.
5. **Probadores:** en TestFlight invitas por correo; ellos instalan la app TestFlight y luego Ari. Para personas fuera de tu equipo, Apple revisa antes la versión de prueba.

## Archivos

- `project.yml`: el proyecto (XcodeGen). Versión, identificador, permisos y la dirección del intermediario de informes.
- `Ari/`: la app (SwiftUI).
  - `ChatStore.swift`: el chat, los comandos, el modelo y los recuerdos.
  - `AriEngine.swift`: Gemma con LiteRT-LM.
  - `ModelDownloader.swift`, `ModelFiles.swift`: descarga y archivos del modelo.
  - `Reporter.swift`: informes de fallos.
  - `Views/`: pantallas (chat, bienvenida, modelo, memoria, ajustes).
- `AriCore/`: la lógica que no depende de la pantalla, con pruebas (`swift test`): personalidad, comandos, historial, elección de modelo, guardado e informes.
