# Ari en Termux

Motor local de Ari: Node.js + llama.cpp (`llama-server` en `127.0.0.1:8080`).

| Archivo | Qué hace |
|---|---|
| `ari-daemon.js` | Cerebro de Ari: comandos, contexto y llamada al modelo local |
| `ari-llm.js` | Conexión con `llama-server` |
| `ari-security.js` | Cifrado AES-256 con la clave de `ARI_MASTER_KEY` |

## Clave maestra

La clave ya no está en el código. Se lee de la variable de entorno `ARI_MASTER_KEY`:

```bash
echo "export ARI_MASTER_KEY=$(head -c 32 /dev/urandom | od -An -tx1 | tr -d ' \n')" > ~/.ari_env
echo 'source ~/.ari_env' >> ~/.bashrc
```

## No se suben al repo

El modelo `.gguf`, `memory.json`, `evolution.json` y los logs (ver `.gitignore`).

## Pendiente

- Falta el archivo que arranca el servidor (`server.js`) y `package.json`.
