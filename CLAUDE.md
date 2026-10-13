# Instrucciones para Claude en este repositorio

- Habla con el usuario en español.
- Lee `docs/ARI_ESTADO.md` antes de cambiar código y actualízalo cuando cambie el estado del proyecto.
- No se puede compilar Android en el contenedor (Google Maven está bloqueado). Para verificar, lanza el workflow `android.yml` con `workflow_dispatch` en la rama de trabajo y espera a que termine en verde antes de decir que compila.

## Documento en Google Drive (actualizarlo siempre)

El usuario pidió que, cada vez que se termine un cambio, se actualice su documento en Google Drive.

1. Edita `docs/ARI_DOCUMENTACION.md`: fecha, uso, historial de cambios, estado de los PR, ideas pendientes.
2. El conector de Drive no puede editar el contenido de un archivo existente. Crea un documento nuevo con `create_file`: título "Ari - Documentación del proyecto", `contentMimeType: text/markdown`, `parentId: 1REKTNNKcgTP9DnetLUgbHCsbLrvQZFI7` (carpeta "Ari - Documentación de Claude").
3. Compruébalo con `read_file_content`. Después, manda a la papelera la versión anterior: busca con `search_files` usando `parentId = '1REKTNNKcgTP9DnetLUgbHCsbLrvQZFI7'`.
4. En el Markdown, no uses emojis de 4 bytes (salen como caracteres raros) ni tablas (se convierten mal). Usa listas.
5. Si el conector de Drive no está disponible en la sesión, avisa al usuario.
6. No toques los otros documentos de Ari que el usuario tiene en Drive.
