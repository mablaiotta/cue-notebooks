package ar.matiasblaiotta.cuenotebooks.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class Db(context: Context) : SQLiteOpenHelper(context, "cuadernos.db", null, 2) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE notebooks(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, " +
                "template TEXT NOT NULL, created_at INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE entries(id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "notebook_id INTEGER NOT NULL REFERENCES notebooks(id) ON DELETE CASCADE, " +
                "created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, " +
                "course TEXT NOT NULL, present INTEGER, total INTEGER, topics TEXT NOT NULL, " +
                "pending TEXT NOT NULL, observations TEXT NOT NULL, mood TEXT NOT NULL, " +
                "detail TEXT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, " +
                "checklist TEXT NOT NULL, refs TEXT NOT NULL)"
        )
        db.execSQL("CREATE INDEX idx_entries_nb ON entries(notebook_id, created_at)")
        createAttendanceTables(db)
    }

    private fun createAttendanceTables(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS students(id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "notebook_id INTEGER NOT NULL REFERENCES notebooks(id) ON DELETE CASCADE, " +
                "name TEXT NOT NULL, position INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_students_nb ON students(notebook_id, position)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS attendance(" +
                "entry_id INTEGER NOT NULL REFERENCES entries(id) ON DELETE CASCADE, " +
                "student_id INTEGER NOT NULL REFERENCES students(id) ON DELETE CASCADE, " +
                "status TEXT NOT NULL, PRIMARY KEY(entry_id, student_id))"
        )
    }

    /** Migración aditiva: v1 -> v2 solo agrega tablas, no toca datos existentes. */
    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {
        if (o < 2) createAttendanceTables(db)
    }

    fun students(): List<Student> = readableDatabase.query(
        "students", null, null, null, null, null, "notebook_id, position, id"
    ).use { c -> buildList { while (c.moveToNext()) add(Student(c.long("id"), c.long("notebook_id"), c.str("name"))) } }

    /** Reemplaza la lista del cuaderno conservando (por nombre) los alumnos que siguen, y con ellos su asistencia. */
    fun saveStudents(notebookId: Long, names: List<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val existing = students().filter { it.notebookId == notebookId }.associateBy { it.name.lowercase() }
            val keep = names.map { it.lowercase() }.toSet()
            existing.values.filter { it.name.lowercase() !in keep }
                .forEach { db.delete("students", "id=?", arrayOf(it.id.toString())) }
            names.forEachIndexed { i, name ->
                val v = ContentValues().apply { put("name", name); put("position", i) }
                val old = existing[name.lowercase()]
                if (old != null) db.update("students", v, "id=?", arrayOf(old.id.toString()))
                else { v.put("notebook_id", notebookId); db.insert("students", null, v) }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    private fun attendanceByEntry(): Map<Long, Map<Long, Attendance>> = readableDatabase.query(
        "attendance", null, null, null, null, null, null
    ).use { c ->
        val out = HashMap<Long, MutableMap<Long, Attendance>>()
        while (c.moveToNext()) {
            val a = Attendance.from(c.str("status")) ?: continue
            out.getOrPut(c.long("entry_id")) { HashMap() }[c.long("student_id")] = a
        }
        out
    }

    fun notebooks(): List<Notebook> = readableDatabase.query(
        "notebooks", null, null, null, null, null, "created_at DESC"
    ).use { c ->
        buildList { while (c.moveToNext()) add(Notebook(c.long("id"), c.str("name"), Template.from(c.str("template")), c.long("created_at"))) }
    }

    fun saveNotebook(nb: Notebook): Long {
        val v = ContentValues().apply {
            put("name", nb.name.trim()); put("template", nb.template.name)
            put("created_at", if (nb.createdAt == 0L) System.currentTimeMillis() else nb.createdAt)
        }
        return if (nb.id == 0L) writableDatabase.insert("notebooks", null, v)
        else { writableDatabase.update("notebooks", v, "id=?", arrayOf(nb.id.toString())); nb.id }
    }

    fun deleteNotebook(id: Long) { writableDatabase.delete("notebooks", "id=?", arrayOf(id.toString())) }

    fun entries(): List<Entry> = readableDatabase.query(
        "entries", null, null, null, null, null, "created_at DESC"
    ).use { c ->
        val att = attendanceByEntry()
        buildList { while (c.moveToNext()) add(c.toEntry().let { it.copy(attendance = att[it.id].orEmpty()) }) }
    }

    fun saveEntry(e: Entry): Long {
        val now = System.currentTimeMillis()
        val v = ContentValues().apply {
            put("notebook_id", e.notebookId)
            put("created_at", if (e.id == 0L) now else e.createdAt)
            put("updated_at", now)
            put("course", e.course); put("present", e.present); put("total", e.total)
            put("topics", e.topics); put("pending", e.pending); put("observations", e.observations)
            put("mood", e.mood.name); put("detail", e.detail); put("title", e.title); put("body", e.body)
            put("checklist", EntryLogic.encodeChecklist(e.checklist)); put("refs", e.references)
        }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val id = if (e.id == 0L) db.insert("entries", null, v)
            else { db.update("entries", v, "id=?", arrayOf(e.id.toString())); e.id }
            db.delete("attendance", "entry_id=?", arrayOf(id.toString()))
            e.attendance.forEach { (sid, a) ->
                db.insert("attendance", null, ContentValues().apply {
                    put("entry_id", id); put("student_id", sid); put("status", a.name)
                })
            }
            db.setTransactionSuccessful()
            return id
        } finally { db.endTransaction() }
    }

    fun deleteEntry(id: Long) { writableDatabase.delete("entries", "id=?", arrayOf(id.toString())) }

    private fun Cursor.str(col: String) = getString(getColumnIndexOrThrow(col)) ?: ""
    private fun Cursor.long(col: String) = getLong(getColumnIndexOrThrow(col))
    private fun Cursor.intOrNull(col: String) = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getInt(it) }

    private fun Cursor.toEntry() = Entry(
        id = long("id"), notebookId = long("notebook_id"), createdAt = long("created_at"),
        updatedAt = long("updated_at"), course = str("course"), present = intOrNull("present"),
        total = intOrNull("total"), topics = str("topics"), pending = str("pending"),
        observations = str("observations"), mood = Mood.from(str("mood")), detail = str("detail"),
        title = str("title"), body = str("body"),
        checklist = EntryLogic.decodeChecklist(str("checklist")), references = str("refs"),
    )
}
