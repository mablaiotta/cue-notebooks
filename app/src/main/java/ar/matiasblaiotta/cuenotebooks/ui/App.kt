package ar.matiasblaiotta.cuenotebooks.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ar.matiasblaiotta.cuenotebooks.data.*

private sealed interface Screen {
    data object Home : Screen
    data class Book(val id: Long) : Screen
    data class Edit(val notebookId: Long, val entryId: Long) : Screen
}

@Composable
fun App(context: Context) {
    val db = remember { Db(context) }
    var notebooks by remember { mutableStateOf(db.notebooks()) }
    var entries by remember { mutableStateOf(db.entries()) }
    var students by remember { mutableStateOf(db.students()) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    fun reload() { notebooks = db.notebooks(); entries = db.entries(); students = db.students() }

    BackHandler(screen != Screen.Home) {
        screen = when (val s = screen) { is Screen.Edit -> Screen.Book(s.notebookId); else -> Screen.Home }
    }

    Surface(Modifier.fillMaxSize(), color = Bg) {
        Box(Modifier.systemBarsPadding().imePadding()) {
            when (val s = screen) {
                Screen.Home -> HomeScreen(
                    notebooks, entries,
                    onCreate = { name, t -> db.saveNotebook(Notebook(name = name, template = t)); reload() },
                    onOpenBook = { screen = Screen.Book(it) },
                    onOpenEntry = { screen = Screen.Edit(it.notebookId, it.id) },
                )
                is Screen.Book -> {
                    val nb = notebooks.firstOrNull { it.id == s.id }
                    if (nb == null) screen = Screen.Home else BookScreen(
                        nb, entries.filter { it.notebookId == nb.id }, students.filter { it.notebookId == nb.id },
                        onSaveStudents = { db.saveStudents(nb.id, it); reload() },
                        onBack = { screen = Screen.Home },
                        onNew = { screen = Screen.Edit(nb.id, 0) },
                        onOpen = { screen = Screen.Edit(nb.id, it.id) },
                        onDelete = { db.deleteNotebook(nb.id); reload(); screen = Screen.Home },
                    )
                }
                is Screen.Edit -> {
                    val nb = notebooks.firstOrNull { it.id == s.notebookId }
                    if (nb == null) screen = Screen.Home else EditorScreen(
                        nb, entries.firstOrNull { it.id == s.entryId }, students.filter { it.notebookId == nb.id },
                        onBack = { screen = Screen.Book(nb.id) },
                        onSave = { db.saveEntry(it); reload(); screen = Screen.Book(nb.id) },
                        onDelete = { id -> db.deleteEntry(id); reload(); screen = Screen.Book(nb.id) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeScreen(
    notebooks: List<Notebook>, entries: List<Entry>,
    onCreate: (String, Template) -> Unit,
    onOpenBook: (Long) -> Unit, onOpenEntry: (Entry) -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var template by remember { mutableStateOf<Template?>(null) }
    var bookFilter by remember { mutableStateOf<Long?>(null) }
    var creating by remember { mutableStateOf(false) }
    val filter = EntryFilter(bookFilter, template, query)
    val shownEntries = filter.apply(entries, notebooks)
    val shownBooks = filter.applyNotebooks(notebooks)
    val byId = notebooks.associateBy { it.id }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Q Cuadernos", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            QButton("+ Cuaderno", { creating = true })
        }
        QField(query, { query = it }, "Buscar texto", Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(8.dp))
        Header("Plantilla")
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QChip("Todas", template == null) { template = null }
            Template.entries.forEach { t -> QChip(t.label, template == t) { template = if (template == t) null else t } }
        }
        if (notebooks.isNotEmpty()) {
            Header("Cuaderno")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { QChip("Todos", bookFilter == null) { bookFilter = null } }
                items(notebooks, key = { it.id }) { nb -> QChip(nb.name, bookFilter == nb.id) { bookFilter = if (bookFilter == nb.id) null else nb.id } }
            }
        }
        Spacer(Modifier.height(8.dp))
        TabRow(
            selectedTabIndex = tab, containerColor = Bg, contentColor = Accent,
            divider = { HorizontalDivider(thickness = 1.dp, color = Line) },
        ) {
            Tab(tab == 0, { tab = 0 }, text = { Text("Cuadernos (${shownBooks.size})", style = MaterialTheme.typography.labelLarge) })
            Tab(tab == 1, { tab = 1 }, text = { Text("Entradas (${shownEntries.size})", style = MaterialTheme.typography.labelLarge) })
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (tab == 0) {
                if (shownBooks.isEmpty()) item { Empty(if (notebooks.isEmpty()) "Creá tu primer cuaderno." else "Sin resultados.") }
                items(shownBooks, key = { it.id }) { nb ->
                    val n = entries.count { it.notebookId == nb.id }
                    FlatCard(Modifier.fillMaxWidth(), onClick = { onOpenBook(nb.id) }) {
                        Text(nb.name, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Tag(nb.template.label)
                            Text("$n ${if (n == 1) "entrada" else "entradas"}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            } else {
                if (shownEntries.isEmpty()) item { Empty("Sin entradas.") }
                items(shownEntries, key = { it.id }) { e ->
                    val nb = byId[e.notebookId] ?: return@items
                    EntryCard(e, nb, showBook = true) { onOpenEntry(e) }
                }
            }
        }
    }
    if (creating) NewNotebookDialog({ creating = false }) { n, t -> creating = false; onCreate(n, t) }
}

@Composable
private fun Empty(text: String) =
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 24.dp))

@Composable
private fun EntryCard(e: Entry, nb: Notebook, showBook: Boolean, onClick: () -> Unit) {
    FlatCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(EntryLogic.displayTitle(e, nb.template), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(Formats.stamp(e.createdAt), style = MaterialTheme.typography.labelMedium)
        }
        if (showBook) {
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Tag(nb.template.label); Text(nb.name, style = MaterialTheme.typography.labelMedium)
            }
        }
        val att = EntryLogic.attendanceLabel(e.present, e.total)
        if (nb.template == Template.DOCENTE && att.isNotEmpty()) Text("Asistencia $att", style = MaterialTheme.typography.bodyMedium)
        val prev = EntryLogic.preview(e, nb.template)
        if (prev.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(prev, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun NewNotebookDialog(onDismiss: () -> Unit, onOk: (String, Template) -> Unit) {
    var name by remember { mutableStateOf("") }
    var t by remember { mutableStateOf(Template.LIBRE) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel, tonalElevation = 0.dp,
        title = { Text("Nuevo cuaderno", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QField(name, { name = it }, "Nombre", Modifier.fillMaxWidth(), singleLine = true)
                Header("Plantilla")
                Template.entries.forEach { opt ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(t == opt, { t = opt }, colors = RadioButtonDefaults.colors(selectedColor = Accent, unselectedColor = LineStrong))
                        Text(opt.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { QButton("Crear", { onOk(name, t) }, enabled = name.isNotBlank()) },
        dismissButton = { QButton("Cancelar", onDismiss, primary = false) },
    )
}

@Composable
private fun StudentsDialog(students: List<Student>, onDismiss: () -> Unit, onOk: (List<String>) -> Unit) {
    var text by remember { mutableStateOf(students.joinToString("\n") { it.name }) }
    var one by remember { mutableStateOf("") }
    fun addOne() {
        if (one.isNotBlank()) { text = (text.trimEnd() + "\n" + one.trim()).trimStart('\n'); one = "" }
    }
    val names = StudentLogic.parseNames(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel, tonalElevation = 0.dp,
        title = { Text("Alumnos (${names.size})", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QField(one, { one = it }, "Agregar alumno", Modifier.weight(1f), singleLine = true)
                    QButton("Agregar", { addOne() }, primary = false, enabled = one.isNotBlank())
                }
                QField(text, { text = it }, "Lista (un nombre por línea; podés pegar varios)", Modifier.fillMaxWidth().heightIn(max = 280.dp), minLines = 6)
            }
        },
        confirmButton = { QButton("Guardar", { onOk(StudentLogic.parseNames(text + if (one.isNotBlank()) "\n$one" else "")) }) },
        dismissButton = { QButton("Cancelar", onDismiss, primary = false) },
    )
}

@Composable
private fun BookScreen(
    nb: Notebook, entries: List<Entry>, students: List<Student>, onSaveStudents: (List<String>) -> Unit,
    onBack: () -> Unit, onNew: () -> Unit, onOpen: (Entry) -> Unit, onDelete: () -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    var editingStudents by remember { mutableStateOf(false) }
    val sorted = entries.sortedByDescending { it.createdAt }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QButton("← Atrás", onBack, primary = false)
            Spacer(Modifier.weight(1f))
            QButton("+ Entrada", onNew)
        }
        Text(nb.name, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Tag(nb.template.label)
            Text("${entries.size} entradas", style = MaterialTheme.typography.labelMedium)
        }
        if (nb.template == Template.DOCENTE) {
            Spacer(Modifier.height(8.dp))
            QButton("Alumnos (${students.size})", { editingStudents = true }, primary = false)
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (sorted.isEmpty()) item { Empty("Todavía no hay entradas.") }
            items(sorted, key = { it.id }) { e -> EntryCard(e, nb, showBook = false) { onOpen(e) } }
        }
        QButton("Eliminar cuaderno", { confirm = true }, primary = false)
        Spacer(Modifier.height(12.dp))
    }
    if (editingStudents) StudentsDialog(students, { editingStudents = false }) { editingStudents = false; onSaveStudents(it) }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false }, containerColor = Panel, tonalElevation = 0.dp,
        title = { Text("¿Eliminar \"${nb.name}\"?", style = MaterialTheme.typography.titleMedium) },
        text = { Text("Se borran también todas sus entradas.", style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { QButton("Eliminar", { confirm = false; onDelete() }) },
        dismissButton = { QButton("Cancelar", { confirm = false }, primary = false) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorScreen(nb: Notebook, existing: Entry?, students: List<Student>, onBack: () -> Unit, onSave: (Entry) -> Unit, onDelete: (Long) -> Unit) {
    val created = remember { existing?.createdAt ?: System.currentTimeMillis() }
    var e by remember { mutableStateOf(existing ?: Entry(notebookId = nb.id, createdAt = created)) }
    var present by remember { mutableStateOf(e.present?.toString() ?: "") }
    var total by remember { mutableStateOf(e.total?.toString() ?: "") }
    var newItem by remember { mutableStateOf("") }
    var showDetail by remember { mutableStateOf(e.detail.isNotBlank()) }

    val hasList = nb.template == Template.DOCENTE && students.isNotEmpty()

    fun finish(): Entry {
        val pending = newItem.trim()
        val items = if (pending.isNotEmpty()) e.checklist + ChecklistItem(pending) else e.checklist
        if (hasList) {
            val ids = students.map { it.id }.toSet()
            val att = e.attendance.filterKeys { it in ids }
            val (p, t) = StudentLogic.counts(students, att)
            return e.copy(present = p, total = t, attendance = att, checklist = items)
        }
        return e.copy(present = present.toIntOrNull(), total = total.toIntOrNull(), checklist = items)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QButton("← Atrás", onBack, primary = false)
            Spacer(Modifier.weight(1f))
            QButton("Guardar", {
                val out = finish()
                if (EntryLogic.isEmpty(out, nb.template)) onBack() else onSave(out)
            })
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(nb.name, style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Tag(nb.template.label)
                Text(Formats.date(created), style = MaterialTheme.typography.labelMedium)
                Text(Formats.time(created) + " hs", style = MaterialTheme.typography.labelMedium)
            }
            when (nb.template) {
                Template.DOCENTE -> {
                    QField(e.course, { e = e.copy(course = it) }, "Curso / materia", Modifier.fillMaxWidth(), singleLine = true)
                    Header("Asistencia / estado del grupo")
                    if (hasList) {
                        students.forEach { st ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(st.name, style = MaterialTheme.typography.bodyLarge)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Attendance.entries.forEach { a ->
                                        QChip(a.label, e.attendance[st.id] == a) {
                                            e = e.copy(attendance = if (e.attendance[st.id] == a) e.attendance - st.id else e.attendance + (st.id to a))
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            QField(present, { present = it.filter(Char::isDigit).take(3) }, "Presentes", Modifier.weight(1f), singleLine = true, keyboardType = KeyboardType.Number)
                            QField(total, { total = it.filter(Char::isDigit).take(3) }, "Total", Modifier.weight(1f), singleLine = true, keyboardType = KeyboardType.Number)
                        }
                    }
                    val counted = if (hasList) StudentLogic.counts(students, e.attendance) else null
                    if (counted != null) Text("Presentes ${counted.first} · Total ${counted.second}", style = MaterialTheme.typography.bodyMedium)
                    val att = if (counted != null) EntryLogic.attendanceLabel(counted.first, counted.second)
                        else EntryLogic.attendanceLabel(present.toIntOrNull(), total.toIntOrNull())
                    if (att.isNotEmpty()) Text(att, style = MaterialTheme.typography.titleMedium)
                    QField(e.topics, { e = e.copy(topics = it) }, "Temario dado", Modifier.fillMaxWidth(), minLines = 3)
                    QField(e.pending, { e = e.copy(pending = it) }, "Pendientes para la próxima clase", Modifier.fillMaxWidth(), minLines = 2)
                    QField(e.observations, { e = e.copy(observations = it) }, "Observaciones", Modifier.fillMaxWidth(), minLines = 2)
                }
                Template.PERSONAL -> {
                    Header("Cómo estás (opcional)")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Mood.entries.forEach { m -> QChip(m.label, e.mood == m) { e = e.copy(mood = m) } }
                    }
                    QField(e.body, { e = e.copy(body = it) }, "Cuerpo", Modifier.fillMaxWidth(), minLines = 6)
                    if (showDetail) QField(e.detail, { e = e.copy(detail = it) }, "Detalle del día", Modifier.fillMaxWidth(), minLines = 2)
                    else QButton("+ Detalle del día", { showDetail = true }, primary = false)
                }
                Template.LIBRE -> {
                    QField(e.title, { e = e.copy(title = it) }, "Título", Modifier.fillMaxWidth(), singleLine = true)
                    QField(e.body, { e = e.copy(body = it) }, "Desarrollo", Modifier.fillMaxWidth(), minLines = 6)
                    Header("Puntos / checklist " + EntryLogic.checklistProgress(e.checklist))
                    e.checklist.forEachIndexed { i, item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(item.done, { c -> e = e.copy(checklist = e.checklist.toMutableList().also { it[i] = item.copy(done = c) }) },
                                colors = CheckboxDefaults.colors(checkedColor = Accent, uncheckedColor = LineStrong, checkmarkColor = Bg))
                            Text(item.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            QButton("Quitar", { e = e.copy(checklist = e.checklist.toMutableList().also { it.removeAt(i) }) }, primary = false)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QField(newItem, { newItem = it }, "Nuevo punto", Modifier.weight(1f), singleLine = true)
                        QButton("Agregar", {
                            if (newItem.isNotBlank()) { e = e.copy(checklist = e.checklist + ChecklistItem(newItem.trim())); newItem = "" }
                        }, primary = false)
                    }
                    QField(e.references, { e = e.copy(references = it) }, "Referencias / notas vinculadas", Modifier.fillMaxWidth(), minLines = 2)
                }
            }
            if (existing != null) {
                Spacer(Modifier.height(8.dp))
                QButton("Eliminar entrada", { onDelete(existing.id) }, primary = false)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
