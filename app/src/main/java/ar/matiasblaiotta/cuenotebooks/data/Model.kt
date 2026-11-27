package ar.matiasblaiotta.cuenotebooks.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class Template(val label: String) {
    DOCENTE("Docente"),
    PERSONAL("Personal"),
    LIBRE("Libre / Proyectos");

    companion object {
        fun from(name: String?): Template = entries.firstOrNull { it.name == name } ?: LIBRE
    }
}

enum class Mood(val label: String) {
    NONE("Sin marcar"),
    BIEN("Bien"),
    TRANQUILO("Tranquilo"),
    ENFOCADO("Enfocado"),
    CANSADO("Cansado"),
    TENSO("Tenso"),
    BAJON("Bajón");

    companion object {
        fun from(name: String?): Mood = entries.firstOrNull { it.name == name } ?: NONE
    }
}

data class Notebook(
    val id: Long = 0,
    val name: String,
    val template: Template,
    val createdAt: Long = 0,
)

data class Student(val id: Long = 0, val notebookId: Long, val name: String)

enum class Attendance(val label: String) {
    PRESENTE("Presente"),
    AUSENTE("Ausente"),
    TARDE("Tarde");

    companion object {
        fun from(name: String?): Attendance? = entries.firstOrNull { it.name == name }
    }
}

data class ChecklistItem(val text: String, val done: Boolean = false)

data class Entry(
    val id: Long = 0,
    val notebookId: Long,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    // Docente
    val course: String = "",
    val present: Int? = null,
    val total: Int? = null,
    val topics: String = "",
    val pending: String = "",
    val observations: String = "",
    // Personal
    val mood: Mood = Mood.NONE,
    val detail: String = "",
    // Libre / compartido
    val title: String = "",
    val body: String = "",
    val checklist: List<ChecklistItem> = emptyList(),
    val references: String = "",
    /** Docente: estado de asistencia por id de alumno (sin marca = no figura). */
    val attendance: Map<Long, Attendance> = emptyMap(),
)

object Formats {
    private val time = DateTimeFormatter.ofPattern("HH:mm")
    private val date = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    /** Hora en 24 hs, siempre HH:mm. */
    fun time(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        time.format(Instant.ofEpochMilli(millis).atZone(zone))

    fun date(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        date.format(Instant.ofEpochMilli(millis).atZone(zone))

    fun stamp(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        "${date(millis, zone)} ${time(millis, zone)}"
}

object StudentLogic {
    /** Nombres separados por salto de línea: recorta, descarta vacíos y repetidos (sin distinguir mayúsculas). */
    fun parseNames(raw: String): List<String> {
        val seen = HashSet<String>()
        return raw.lines().map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotEmpty() && seen.add(it.lowercase()) }
    }

    /** Presentes (Presente + Tarde) y total de la lista; solo cuentan alumnos de la lista actual. */
    fun counts(students: List<Student>, attendance: Map<Long, Attendance>): Pair<Int, Int> =
        students.count { attendance[it.id].let { a -> a == Attendance.PRESENTE || a == Attendance.TARDE } } to students.size
}

object EntryLogic {
    /** "12/15 · 80%" o "" si faltan datos. */
    fun attendanceLabel(present: Int?, total: Int?): String {
        if (present == null || total == null || total <= 0) return ""
        val p = present.coerceIn(0, total)
        return "$p/$total · ${p * 100 / total}%"
    }

    fun encodeChecklist(items: List<ChecklistItem>): String =
        items.filter { it.text.isNotBlank() }
            .joinToString("\n") { (if (it.done) "1|" else "0|") + it.text.trim().replace("\n", " ") }

    fun decodeChecklist(raw: String): List<ChecklistItem> =
        raw.lines().filter { it.length > 2 && it[1] == '|' }
            .map { ChecklistItem(it.substring(2), it[0] == '1') }

    fun checklistProgress(items: List<ChecklistItem>): String =
        if (items.isEmpty()) "" else "${items.count { it.done }}/${items.size}"

    /** Una entrada vacía (según su plantilla) no se guarda. */
    fun isEmpty(e: Entry, template: Template): Boolean = when (template) {
        Template.DOCENTE -> listOf(e.course, e.topics, e.pending, e.observations).all { it.isBlank() } &&
            e.present == null && e.total == null && e.attendance.isEmpty()
        Template.PERSONAL -> e.body.isBlank() && e.detail.isBlank() && e.mood == Mood.NONE
        Template.LIBRE -> e.title.isBlank() && e.body.isBlank() && e.references.isBlank() &&
            e.checklist.none { it.text.isNotBlank() }
    }

    fun displayTitle(e: Entry, template: Template): String = when (template) {
        Template.DOCENTE -> e.course.ifBlank { "Clase" }
        Template.PERSONAL -> if (e.mood == Mood.NONE) "Día" else e.mood.label
        Template.LIBRE -> e.title.ifBlank { "Sin título" }
    }

    fun preview(e: Entry, template: Template): String = when (template) {
        Template.DOCENTE -> listOf(e.topics, e.pending, e.observations).firstOrNull { it.isNotBlank() }.orEmpty()
        Template.PERSONAL -> e.body.ifBlank { e.detail }
        Template.LIBRE -> e.body.ifBlank { e.references }
    }

    fun searchableText(e: Entry): String = listOf(
        e.course, e.topics, e.pending, e.observations, e.detail, e.title, e.body, e.references,
        e.mood.takeIf { it != Mood.NONE }?.label.orEmpty(),
        e.checklist.joinToString(" ") { it.text },
    ).joinToString("\n")
}

data class EntryFilter(
    val notebookId: Long? = null,
    val template: Template? = null,
    val query: String = "",
) {
    fun apply(entries: List<Entry>, notebooks: List<Notebook>): List<Entry> {
        val byId = notebooks.associateBy { it.id }
        val q = query.trim()
        return entries.filter { e ->
            val nb = byId[e.notebookId] ?: return@filter false
            (notebookId == null || e.notebookId == notebookId) &&
                (template == null || nb.template == template) &&
                (q.isEmpty() || EntryLogic.searchableText(e).contains(q, ignoreCase = true) ||
                    nb.name.contains(q, ignoreCase = true))
        }.sortedByDescending { it.createdAt }
    }

    fun applyNotebooks(notebooks: List<Notebook>): List<Notebook> {
        val q = query.trim()
        return notebooks.filter {
            (template == null || it.template == template) &&
                (notebookId == null || it.id == notebookId) &&
                (q.isEmpty() || it.name.contains(q, ignoreCase = true))
        }
    }
}
