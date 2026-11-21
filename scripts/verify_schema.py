import json
import re
import sqlite3
from pathlib import Path


root = Path(__file__).resolve().parents[1]
schema_dir = root / "app/schemas/com.focusforceplus.app.data.db.AppDatabase"
original_schema = json.loads((schema_dir / "10.json").read_text(encoding="utf-8"))["database"]
new_schema = json.loads((schema_dir / "11.json").read_text(encoding="utf-8"))["database"]
linked_schema = json.loads((schema_dir / "12.json").read_text(encoding="utf-8"))["database"]
isolated_schema = json.loads((schema_dir / "13.json").read_text(encoding="utf-8"))["database"]
migration_source = (root / "app/src/main/java/com/focusforceplus/app/study/data/StudyMigration.kt").read_text(encoding="utf-8")
original_migration, isolation_migration = migration_source.split("object StudyTaskIsolationMigration", 1)
statements = re.findall(r'^\s*"(CREATE [^"]+)"[, ]*$', original_migration, flags=re.MULTILINE)
assert len(statements) == 14, f"Unexpected migration statements: {len(statements)}"


def create_schema(connection, schema):
    for entity in schema["entities"]:
        table = entity["tableName"]
        connection.execute(entity["createSql"].replace("${TABLE_NAME}", table))
        for index in entity.get("indices", []):
            connection.execute(index["createSql"].replace("${TABLE_NAME}", table))


def metadata(connection, table):
    columns = connection.execute(f'PRAGMA table_info("{table}")').fetchall()
    foreign_keys = sorted(connection.execute(f'PRAGMA foreign_key_list("{table}")').fetchall())
    indices = []
    for index in connection.execute(f'PRAGMA index_list("{table}")'):
        if index[3] == "pk":
            continue
        index_columns = connection.execute(f'PRAGMA index_info("{index[1]}")').fetchall()
        indices.append((index[1], index[2], index_columns))
    return columns, foreign_keys, sorted(indices)


migrated = sqlite3.connect(":memory:")
expected = sqlite3.connect(":memory:")
migrated.execute("PRAGMA foreign_keys=ON")
create_schema(migrated, original_schema)
before = {entity["tableName"]: metadata(migrated, entity["tableName"]) for entity in original_schema["entities"]}
migrated.execute(
    "INSERT INTO todos (id,title,priority,isCompleted,isRecurring,snoozeCount,maxSnoozeCount,"
    "rescheduleCount,maxRescheduleCount,createdAt) VALUES (42,'legacy todo',1,0,0,0,2,0,1,1000)"
)
for statement in statements:
    migrated.execute(statement)
create_schema(expected, new_schema)
assert migrated.execute("SELECT title FROM todos WHERE id=42").fetchone() == ("legacy todo",)
for table, shape in before.items():
    assert metadata(migrated, table) == shape, f"Legacy table changed: {table}"
for entity in new_schema["entities"]:
    table = entity["tableName"]
    assert metadata(migrated, table) == metadata(expected, table), f"Schema differs: {table}"
assert not migrated.execute("PRAGMA foreign_key_check").fetchall()
tables = len(new_schema["entities"])
print(f"PASS: Room 10->11 SQL matches exported schema for {tables} tables.")
print(f"PASS: All {len(before)} legacy table structures and sample todo preserved.")
print("PASS: Foreign keys, indices, nullability, primary keys and defaults match.")
link_statements = re.findall(r'\bdb\.execSQL\("(ALTER TABLE [^"]+)"\)', migration_source)
assert len(link_statements) == 1
for statement in link_statements:
    migrated.execute(statement)
linked_expected = sqlite3.connect(":memory:")
create_schema(linked_expected, linked_schema)
for entity in linked_schema["entities"]:
    table = entity["tableName"]
    assert metadata(migrated, table) == metadata(linked_expected, table), f"Linked schema differs: {table}"
assert migrated.execute("SELECT title FROM todos WHERE id=42").fetchone() == ("legacy todo",)
print("PASS: Room 11->12 task association migration matches exported schema without changing legacy data.")
isolation_statements = re.findall(r'^\s*"([^"]+)"[, ]*$', isolation_migration, flags=re.MULTILINE)
migrated.execute(
    "INSERT INTO todos (id,title,priority,isCompleted,isRecurring,snoozeCount,maxSnoozeCount,"
    "rescheduleCount,maxRescheduleCount,createdAt,completedAt) VALUES (43,'study title',1,1,0,0,2,0,1,1000,2000)"
)
migrated.execute("INSERT INTO study_tasks VALUES (42,43,'Math','2026-11-21',30,'MANUAL')")
migrated.execute("UPDATE sqlite_sequence SET seq=99 WHERE name='study_tasks'")
for statement in isolation_statements:
    migrated.execute(statement)
isolated_expected = sqlite3.connect(":memory:")
create_schema(isolated_expected, isolated_schema)
for entity in isolated_schema["entities"]:
    table = entity["tableName"]
    assert metadata(migrated, table) == metadata(isolated_expected, table), f"Isolated schema differs: {table}"
assert migrated.execute("SELECT id,title FROM todos").fetchall() == [(42, "legacy todo")]
migrated.execute("DELETE FROM todos")
assert migrated.execute("SELECT * FROM study_tasks").fetchall() == [
    (42, "study title", "Math", "2026-11-21", 30, "MANUAL", 1, 1000, 2000),
]
assert migrated.execute("SELECT seq FROM sqlite_sequence WHERE name='study_tasks'").fetchone() == (99,)
assert not migrated.execute("PRAGMA foreign_key_check").fetchall()

# Old sessions can refer to a deleted task even if the old task table is empty.
empty_tasks = sqlite3.connect(":memory:")
create_schema(empty_tasks, linked_schema)
empty_tasks.execute("INSERT INTO sqlite_sequence (name,seq) VALUES ('study_tasks',99)")
for statement in isolation_statements:
    empty_tasks.execute(statement)
assert empty_tasks.execute("SELECT seq FROM sqlite_sequence WHERE name='study_tasks'").fetchone() == (99,)
print("PASS: Room 12->13 task data, legacy todo isolation, and task ID sequence match expectations.")
print("Scope: host SQLite verification; Android Room instrumentation NOT executed by this script.")
