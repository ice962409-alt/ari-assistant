# Herramientas de Claude para trabajar en ARI

Pedidas por el dueño el 17/10/2026 ("Instala los links"). Antes de instalar, cada repositorio se revisó dos veces (un revisor y un escéptico de seguridad), sin ejecutar nada de él. Todo queda fijado a una versión revisada: nada se actualiza solo.

Importante: lo que está en `.claude/` funciona en las sesiones nuevas de Claude Code que empiecen desde esta rama. Para que lo tengan todas las sesiones, tiene que llegar a la rama principal del repositorio (`main`).

## graphify: el mapa del código

- Qué es: arma un mapa de ARI (archivos, clases y funciones, y quién llama a quién) con tree-sitter, sin internet y sin ningún modelo. Sirve para preguntar "¿quién llama a handlePhoneAnswer?" sin leer los 5 200 renglones de MainActivity, y así gastar menos tokens.
- Origen: Graphify-Labs/graphify, paquete de PyPI `graphifyy` (con doble "y"; los parecidos pueden ser maliciosos), versión 0.9.92, commit 86c57be. Licencia Apache-2.0.
- Cómo se instala: al empezar cada sesión en la nube, `.claude/hooks/session-start.sh` crea un entorno de Python aparte y lo instala con `.claude/graphify-requirements.txt` (31 paquetes, cada uno con su huella sha256: si algo cambió en PyPI, no se instala). Después arma el mapa en `app/src/graphify-out/` (no se sube a git). Tarda unos 12 segundos y nunca hace fallar la sesión.
- Se usa así: `graphify explain "handlePhoneAnswer"`, `graphify path "sendMessage" "PhoneAgent"`, `graphify query "..."`. Después de cambiar código: `graphify update app/src`.
- Lo que NO se instaló, a propósito: la skill `/graphify` (se actualiza sola desde PyPI y cuesta unos 11 000 tokens), los "hooks" que revisan cada lectura, el texto que agrega al CLAUDE.md y los modos que mandan código a modelos en la nube. `GRAPHIFY_BACKEND=none` lo deja todo local.
- Para actualizar: revisar el cambio de versión y volver a generar los hashes; nunca `pip install graphify` (con una sola "y").

## ponytail: el modo "programador senior flojo"

- Qué es: instrucciones para hacer el cambio más pequeño que resuelve todo, reutilizar lo que ya existe y no agregar cosas que nadie pidió. Trae revisiones de código y de todo el repositorio, explicadas en lenguaje sencillo.
- Origen: DietrichGebert/ponytail, v5.3.2, commit 16352ac. Licencia MIT.
- Instalado: solo 3 skills de texto en `.claude/skills/`: `/ponytail` (solo cuando se pide; respeta las tareas del CLAUDE.md), `/ponytail-review` y `/ponytail-audit` (responden en español sencillo).
- No instalado: el plugin completo, sus hooks y la "band" (código que corre dentro de Claude Code en cada comando y tenía menos de 2 días), y la actualización automática.

## agent-skills (Addy Osmani)

- Qué es: recetas de ingeniería (planear, depurar, simplificar, documentar decisiones, migraciones, pruebas).
- Origen: addyosmani/agent-skills, commit aeac41d. Licencia MIT.
- Instalado: 10 skills y 3 referencias, solo texto.
  - Se activan solas cuando hacen falta: `code-simplification`, `debugging-and-error-recovery`, `planning-and-task-breakdown`, `documentation-and-adrs`, `deprecation-and-migration`.
  - Solo a mano con `/nombre` (cuestan muchos tokens o chocan con el trabajo autónomo): `incremental-implementation`, `test-driven-development`, `context-engineering`, `doubt-driven-development`, `interview-me`.
- No instalado: el plugin (en las sesiones en la nube los plugins del proyecto no se instalan), los hooks, los agentes y los comandos.

## OmniRoute: no va aquí, va en la laptop

- Qué es: un servidor que junta cientos de modelos de IA (de pago, gratis y locales) detrás de una sola dirección compatible con OpenAI, con respaldo automático si uno falla. Es útil para que ARI y sus "subordinados" usen muchos modelos desde un solo lugar.
- Por qué no se instaló en Claude Code: es un servidor que debe estar prendido siempre; aquí se borraría en cada sesión, no ayuda a programar ARI y ofrece cambiar la configuración de Claude Code y meterse entre Claude y Anthropic.
- En el teléfono (Termux) tampoco se recomienda: en Android cualquier app instalada puede hablarle a 127.0.0.1, y trae contraseña de ejemplo y sin llave por defecto.
- Cómo instalarlo en la laptop con Linux (cuando la tengas), de forma segura:
  1. Usar Docker con la versión fijada por su huella: `diegosouzapw/omniroute@sha256:8bd462c9f60d8eda79329cfbb6ea7ea723505fe7721beb944f3d43835409e218` (versión 3.8.51). Nunca `:latest`.
  2. Antes de arrancarlo, crear `~/omniroute.env` con permisos 600: `INITIAL_PASSWORD` largo y al azar (`openssl rand -base64 24`), `REQUIRE_API_KEY=true`, `CLI_ALLOW_CONFIG_WRITES=false`, `ARENA_ELO_SYNC_ENABLED=false`, `OPENROUTER_PROVIDER_STATS_ENABLED=false`, `NO_UPDATE_NOTIFIER=1`, `OMNIROUTE_NO_UPDATE_NOTIFIER=1`.
  3. Arrancarlo solo en la propia laptop: `docker run -d --name omniroute --restart unless-stopped -p 127.0.0.1:20128:20128 -v omniroute-data:/app/data --env-file ~/omniroute.env diegosouzapw/omniroute@sha256:8bd462c9f60d8eda79329cfbb6ea7ea723505fe7721beb944f3d43835409e218`. Sin `docker.sock`, sin `--privileged`.
  4. En el panel: nunca "saltar la seguridad"; una llave por cliente (ARI, Claude Code) con presupuesto; sin MITM, túneles, sincronización en la nube, plugins ni "aplicar a Claude"; sin "Update Now".
  5. Actualizar a mano después de leer su CHANGELOG y sus avisos de seguridad.
