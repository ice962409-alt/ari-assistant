from pathlib import Path
import sys

PROJECT_DIR = Path(__file__).resolve().parents[1]
MEMORY_DIR = PROJECT_DIR / "memory"

sys.path.insert(0, str(MEMORY_DIR))

from memory import add_memory, list_memories, search_memories


SYSTEM_PROMPT = """
Tu nombre es Ari.
Hablas español de forma natural y clara.
Utilizas los recuerdos disponibles cuando son relevantes.
No inventas recuerdos ni afirmas haber hecho acciones que no realizaste.
Si no sabes algo, lo reconoces.
"""


def get_context():
    memories = list_memories()

    if not memories:
        return "Todavía no hay recuerdos almacenados."

    return "\n".join(
        f"- [{item['category']}] {item['content']}"
        for item in memories
    )


def show_status():
    print("Ari: módulo conversacional preparado.")
    print("Memoria local: disponible.")
    print("Modelo de lenguaje: pendiente de conexión.")
    print(f"Recuerdos almacenados: {len(list_memories())}")


if __name__ == "__main__":
    show_status()
