import sqlite3
from pathlib import Path
from datetime import datetime, timezone

PROJECT_DIR = Path(__file__).resolve().parents[1]
DATA_DIR = PROJECT_DIR / "data"
DB_PATH = DATA_DIR / "ari_memory.db"


def connect():
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    connection = sqlite3.connect(DB_PATH)
    connection.row_factory = sqlite3.Row
    connection.execute("""
        CREATE TABLE IF NOT EXISTS memories (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            category TEXT NOT NULL,
            content TEXT NOT NULL,
            created_at TEXT NOT NULL
        )
    """)
    return connection


def add_memory(category, content):
    category = category.strip()
    content = content.strip()

    if not category or not content:
        raise ValueError("La categoría y el contenido son obligatorios.")

    with connect() as db:
        cursor = db.execute(
            """INSERT INTO memories (category, content, created_at)
               VALUES (?, ?, ?)""",
            (category, content,
             datetime.now(timezone.utc).isoformat())
        )
        return cursor.lastrowid


def list_memories():
    with connect() as db:
        return db.execute(
            "SELECT * FROM memories ORDER BY id DESC"
        ).fetchall()


def search_memories(term):
    with connect() as db:
        return db.execute(
            """SELECT * FROM memories
               WHERE content LIKE ? OR category LIKE ?
               ORDER BY id DESC""",
            (f"%{term}%", f"%{term}%")
        ).fetchall()


def delete_memory(memory_id):
    with connect() as db:
        cursor = db.execute(
            "DELETE FROM memories WHERE id = ?",
            (memory_id,)
        )
        return cursor.rowcount > 0


if __name__ == "__main__":
    connect().close()
    print("Memoria de Ari inicializada correctamente.")
    print(f"Base de datos: {DB_PATH}")
