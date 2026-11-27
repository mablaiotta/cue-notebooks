package ar.matiasblaiotta.cuenotebooks

import ar.matiasblaiotta.cuenotebooks.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ModelTest {
    private val utc = ZoneId.of("UTC")
    private val docente = Notebook(1, "Matemática 3°B", Template.DOCENTE)
    private val personal = Notebook(2, "Diario", Template.PERSONAL)
    private val libre = Notebook(3, "Proyecto Q", Template.LIBRE)
    private val books = listOf(docente, personal, libre)

    @Test fun timeIs24hHHmm() {
        val t = ZonedDateTime.of(2026, 11, 26, 7, 5, 0, 0, utc).toInstant().toEpochMilli()
        assertEquals("07:05", Formats.time(t, utc))
        val t2 = ZonedDateTime.of(2026, 11, 26, 21, 45, 0, 0, utc).toInstant().toEpochMilli()
        assertEquals("21:45", Formats.time(t2, utc))
        assertEquals("26/11/2026 21:45", Formats.stamp(t2, utc))
    }

    @Test fun moodDefaultsToSinMarcar() {
        assertEquals("Sin marcar", Entry(notebookId = 2, createdAt = 0).mood.label)
        assertEquals(Mood.NONE, Mood.from("desconocido"))
        assertEquals(
            listOf("Sin marcar", "Bien", "Tranquilo", "Enfocado", "Cansado", "Tenso", "Bajón"),
            Mood.entries.map { it.label },
        )
    }

    @Test fun attendance() {
        assertEquals("12/15 · 80%", EntryLogic.attendanceLabel(12, 15))
        assertEquals("15/15 · 100%", EntryLogic.attendanceLabel(20, 15))
        assertEquals("", EntryLogic.attendanceLabel(null, 15))
        assertEquals("", EntryLogic.attendanceLabel(3, 0))
    }

    @Test fun checklistRoundTrip() {
        val items = listOf(ChecklistItem("uno", true), ChecklistItem("dos"), ChecklistItem("  "))
        val raw = EntryLogic.encodeChecklist(items)
        assertEquals(listOf(ChecklistItem("uno", true), ChecklistItem("dos")), EntryLogic.decodeChecklist(raw))
        assertEquals("1/2", EntryLogic.checklistProgress(EntryLogic.decodeChecklist(raw)))
        assertEquals("", EntryLogic.checklistProgress(emptyList()))
    }

    @Test fun emptyEntriesPerTemplate() {
        val e = Entry(notebookId = 1, createdAt = 0)
        Template.entries.forEach { assertTrue(EntryLogic.isEmpty(e, it)) }
        assertFalse(EntryLogic.isEmpty(e.copy(present = 0), Template.DOCENTE))
        assertFalse(EntryLogic.isEmpty(e.copy(mood = Mood.BIEN), Template.PERSONAL))
        assertFalse(EntryLogic.isEmpty(e.copy(checklist = listOf(ChecklistItem("x"))), Template.LIBRE))
    }

    private val entries = listOf(
        Entry(1, 1, 100, course = "Álgebra", topics = "Ecuaciones lineales"),
        Entry(2, 2, 300, mood = Mood.CANSADO, body = "Día largo"),
        Entry(3, 3, 200, title = "Idea", body = "Prototipo", checklist = listOf(ChecklistItem("comprar cable"))),
        Entry(4, 9, 400, title = "Huérfana"),
    )

    @Test fun filterByNotebookTemplateAndQuery() {
        assertEquals(listOf(2L, 3L, 1L), EntryFilter().apply(entries, books).map { it.id })
        assertEquals(listOf(1L), EntryFilter(notebookId = 1).apply(entries, books).map { it.id })
        assertEquals(listOf(2L), EntryFilter(template = Template.PERSONAL).apply(entries, books).map { it.id })
        assertEquals(listOf(3L), EntryFilter(query = "CABLE").apply(entries, books).map { it.id })
        assertEquals(listOf(2L), EntryFilter(query = "cansado").apply(entries, books).map { it.id })
        assertEquals(listOf(3L), EntryFilter(query = "proyecto q").apply(entries, books).map { it.id })
        assertTrue(EntryFilter(template = Template.DOCENTE, query = "prototipo").apply(entries, books).isEmpty())
    }

    @Test fun filterNotebooks() {
        assertEquals(listOf(libre), EntryFilter(template = Template.LIBRE).applyNotebooks(books))
        assertEquals(listOf(personal), EntryFilter(query = "diar").applyNotebooks(books))
    }

    @Test fun titles() {
        assertEquals("Álgebra", EntryLogic.displayTitle(entries[0], Template.DOCENTE))
        assertEquals("Cansado", EntryLogic.displayTitle(entries[1], Template.PERSONAL))
        assertEquals("Sin título", EntryLogic.displayTitle(Entry(notebookId = 3, createdAt = 0), Template.LIBRE))
    }

    @Test fun parseStudentNames() {
        assertEquals(
            listOf("Ana Pérez", "Luis", "Marta"),
            StudentLogic.parseNames("  Ana   Pérez \n\nLuis\r\nana pérez\n Marta  \n"),
        )
        assertTrue(StudentLogic.parseNames(" \n ").isEmpty())
    }

    @Test fun attendanceCountsFromStudentList() {
        val st = listOf(Student(1, 1, "A"), Student(2, 1, "B"), Student(3, 1, "C"), Student(4, 1, "D"))
        val att = mapOf(
            1L to Attendance.PRESENTE, 2L to Attendance.TARDE, 3L to Attendance.AUSENTE,
            99L to Attendance.PRESENTE, // alumno ya borrado: no cuenta
        )
        assertEquals(2 to 4, StudentLogic.counts(st, att))
        assertEquals(0 to 4, StudentLogic.counts(st, emptyMap()))
        assertEquals(0 to 0, StudentLogic.counts(emptyList(), att))
        assertEquals("2/4 · 50%", EntryLogic.attendanceLabel(2, 4))
    }

    @Test fun attendanceStatusParsingAndEmptiness() {
        assertEquals(Attendance.TARDE, Attendance.from("TARDE"))
        assertNull(Attendance.from("x"))
        assertEquals(listOf("Presente", "Ausente", "Tarde"), Attendance.entries.map { it.label })
        val e = Entry(notebookId = 1, createdAt = 0)
        assertFalse(EntryLogic.isEmpty(e.copy(attendance = mapOf(1L to Attendance.AUSENTE)), Template.DOCENTE))
    }
}
