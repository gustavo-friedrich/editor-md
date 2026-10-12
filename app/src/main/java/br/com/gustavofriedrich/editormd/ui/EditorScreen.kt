package br.com.gustavofriedrich.editormd.ui

import android.content.Intent
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.gustavofriedrich.editormd.EditorViewModel
import br.com.gustavofriedrich.editormd.MarkdownFormatter
import br.com.gustavofriedrich.editormd.ViewMode

private enum class PendingAction { NEW, OPEN }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(viewModel: EditorViewModel) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingAction by rememberSaveable { mutableStateOf<PendingAction?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::openDocument)
    }
    val saveAsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        uri?.let { viewModel.saveTo(it) }
    }

    fun saveAs() = saveAsLauncher.launch(viewModel.documentName)
    fun save() {
        val uri = viewModel.documentUri
        if (uri == null) saveAs() else viewModel.saveTo(uri, onFailure = ::saveAs)
    }
    fun perform(action: PendingAction) = when (action) {
        PendingAction.NEW -> viewModel.newDocument()
        PendingAction.OPEN -> openLauncher.launch(arrayOf("text/*", "application/octet-stream"))
    }
    fun request(action: PendingAction) {
        if (viewModel.isModified) pendingAction = action else perform(action)
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text("Alterações não salvas") },
            text = { Text("Deseja descartar as alterações em \"${viewModel.documentName}\"?") },
            confirmButton = {
                TextButton(onClick = { pendingAction = null; perform(action) }) { Text("Descartar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) { Text("Cancelar") }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = viewModel.documentName + if (viewModel.isModified) " •" else "",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        DocumentStats(viewModel.content.text)
                    }
                },
                actions = {
                    IconButton(onClick = { request(PendingAction.NEW) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Novo")
                    }
                    IconButton(onClick = { request(PendingAction.OPEN) }) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = "Abrir")
                    }
                    IconButton(onClick = ::save) {
                        Icon(Icons.Filled.Save, contentDescription = "Salvar")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Salvar como…") },
                                onClick = { menuOpen = false; saveAs() },
                            )
                            DropdownMenuItem(
                                text = { Text("Compartilhar") },
                                onClick = {
                                    menuOpen = false
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, viewModel.documentName)
                                        putExtra(Intent.EXTRA_TEXT, viewModel.content.text)
                                    }
                                    context.startActivity(Intent.createChooser(send, "Compartilhar"))
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
        ) {
            ViewModeSelector(viewModel.viewMode, onSelect = { viewModel.viewMode = it })

            Box(Modifier.weight(1f)) {
                when (viewModel.viewMode) {
                    ViewMode.EDIT -> MarkdownEditor(viewModel.content, viewModel::updateContent)
                    ViewMode.PREVIEW -> MarkdownPreview(viewModel.content.text)
                    ViewMode.SPLIT -> SplitView(viewModel)
                }
            }

            if (viewModel.viewMode != ViewMode.PREVIEW) {
                HorizontalDivider()
                FormatToolbar(viewModel.content, viewModel::updateContent)
            }
        }
    }
}

@Composable
private fun DocumentStats(text: String) {
    val words = remember(text) { text.split(Regex("\\s+")).count { it.isNotBlank() } }
    Text(
        "$words palavras · ${text.length} caracteres",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ViewModeSelector(selected: ViewMode, onSelect: (ViewMode) -> Unit) {
    val modes = ViewMode.entries
    SingleChoiceSegmentedButtonRow(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        modes.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
            ) {
                Text(mode.label)
            }
        }
    }
}

@Composable
private fun SplitView(viewModel: EditorViewModel) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (landscape) {
        Row(Modifier.fillMaxSize()) {
            MarkdownEditor(viewModel.content, viewModel::updateContent, Modifier.weight(1f))
            VerticalDivider()
            MarkdownPreview(viewModel.content.text, Modifier.weight(1f))
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            MarkdownEditor(viewModel.content, viewModel::updateContent, Modifier.weight(1f))
            HorizontalDivider()
            MarkdownPreview(viewModel.content.text, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MarkdownEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        decorationBox = { innerTextField ->
            Box {
                if (value.text.isEmpty()) {
                    Text(
                        "Comece a escrever em Markdown…",
                        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                innerTextField()
            }
        },
    )
}

private data class FormatAction(
    val icon: ImageVector,
    val description: String,
    val apply: (TextFieldValue) -> TextFieldValue,
)

private val formatActions = listOf(
    FormatAction(Icons.Filled.FormatBold, "Negrito") { MarkdownFormatter.wrap(it, "**", placeholder = "negrito") },
    FormatAction(Icons.Filled.FormatItalic, "Itálico") { MarkdownFormatter.wrap(it, "*", placeholder = "itálico") },
    FormatAction(Icons.Filled.FormatStrikethrough, "Riscado") { MarkdownFormatter.wrap(it, "~~", placeholder = "riscado") },
    FormatAction(Icons.Filled.Title, "Título") { MarkdownFormatter.heading(it) },
    FormatAction(Icons.AutoMirrored.Filled.FormatListBulleted, "Lista") { MarkdownFormatter.prefixLines(it, "- ") },
    FormatAction(Icons.Filled.FormatListNumbered, "Lista numerada") { MarkdownFormatter.numberedList(it) },
    FormatAction(Icons.Filled.CheckBox, "Tarefa") { MarkdownFormatter.prefixLines(it, "- [ ] ") },
    FormatAction(Icons.Filled.FormatQuote, "Citação") { MarkdownFormatter.prefixLines(it, "> ") },
    FormatAction(Icons.Filled.Code, "Código") { MarkdownFormatter.code(it) },
    FormatAction(Icons.Filled.Link, "Link") { MarkdownFormatter.link(it) },
    FormatAction(Icons.Filled.TableChart, "Tabela") { MarkdownFormatter.insertBlock(it, MarkdownFormatter.TABLE_TEMPLATE) },
    FormatAction(Icons.Filled.HorizontalRule, "Linha horizontal") { MarkdownFormatter.insertBlock(it, MarkdownFormatter.HORIZONTAL_RULE) },
)

@Composable
private fun FormatToolbar(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit) {
    Surface(tonalElevation = 2.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp)
        ) {
            formatActions.forEach { action ->
                IconButton(onClick = { onValueChange(action.apply(value)) }) {
                    Icon(action.icon, contentDescription = action.description)
                }
            }
        }
    }
}
