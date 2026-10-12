package br.com.gustavofriedrich.editormd

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class ViewMode(val label: String) {
    EDIT("Editar"),
    PREVIEW("Visualizar"),
    SPLIT("Dividido"),
}

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val resolver get() = getApplication<Application>().contentResolver
    private val prefs = application.getSharedPreferences("editor_state", Context.MODE_PRIVATE)
    private val draftFile = File(application.filesDir, "draft.md")

    var content by mutableStateOf(TextFieldValue(""))
        private set
    var documentUri by mutableStateOf<Uri?>(null)
        private set
    var documentName by mutableStateOf(DEFAULT_NAME)
        private set
    var isModified by mutableStateOf(false)
        private set
    var viewMode by mutableStateOf(ViewMode.EDIT)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    private var draftJob: Job? = null

    init {
        restoreDraft()
    }

    fun updateContent(value: TextFieldValue) {
        val changed = value.text != content.text
        content = value
        if (changed) {
            isModified = true
            scheduleDraftSave()
        }
    }

    fun newDocument() {
        content = TextFieldValue("")
        documentUri = null
        documentName = DEFAULT_NAME
        isModified = false
        viewMode = ViewMode.EDIT
        scheduleDraftSave()
    }

    fun openDocument(uri: Uri) {
        viewModelScope.launch {
            try {
                val (text, name) = withContext(Dispatchers.IO) {
                    takePermission(uri)
                    val text = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("arquivo indisponível")
                    text to queryName(uri)
                }
                content = TextFieldValue(text)
                documentUri = uri
                documentName = name ?: DEFAULT_NAME
                isModified = false
                scheduleDraftSave()
                _messages.emit("Arquivo aberto: $documentName")
            } catch (e: Exception) {
                _messages.emit("Não foi possível abrir o arquivo: ${e.message}")
            }
        }
    }

    /** Salva no [uri] indicado; [onFailure] é chamado se não houver permissão de escrita. */
    fun saveTo(uri: Uri, onFailure: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val text = content.text
                val name = withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(text) }
                        ?: error("arquivo indisponível")
                    queryName(uri)
                }
                documentUri = uri
                documentName = name ?: documentName
                isModified = false
                scheduleDraftSave()
                _messages.emit("Salvo em $documentName")
            } catch (e: Exception) {
                _messages.emit("Não foi possível salvar: ${e.message}")
                onFailure()
            }
        }
    }

    fun saveDraftNow() {
        draftJob?.cancel()
        writeDraft()
    }

    private fun scheduleDraftSave() {
        draftJob?.cancel()
        draftJob = viewModelScope.launch {
            delay(800)
            withContext(Dispatchers.IO) { writeDraft() }
        }
    }

    /** O rascunho preserva o texto entre sessões, mesmo sem salvar em arquivo. */
    private fun writeDraft() {
        runCatching {
            draftFile.writeText(content.text)
            prefs.edit {
                putString(KEY_URI, documentUri?.toString())
                putString(KEY_NAME, documentName)
                putBoolean(KEY_MODIFIED, isModified)
            }
        }
    }

    private fun restoreDraft() {
        if (!draftFile.exists()) {
            content = TextFieldValue(WELCOME_TEXT)
            documentName = "Bem-vindo.md"
            return
        }
        content = TextFieldValue(runCatching { draftFile.readText() }.getOrDefault(""))
        documentUri = prefs.getString(KEY_URI, null)?.let(Uri::parse)
        documentName = prefs.getString(KEY_NAME, null) ?: DEFAULT_NAME
        isModified = prefs.getBoolean(KEY_MODIFIED, false)
    }

    private fun takePermission(uri: Uri) {
        val rw = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { resolver.takePersistableUriPermission(uri, rw) }
            .recoverCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    private fun queryName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    companion object {
        const val DEFAULT_NAME = "sem-titulo.md"
        private const val KEY_URI = "uri"
        private const val KEY_NAME = "name"
        private const val KEY_MODIFIED = "modified"

        private val WELCOME_TEXT = """
            # Bem-vindo ao Editor MD

            Escreva na aba **Editar** e veja o resultado em **Visualizar** — ou use o modo **Dividido** para ver os dois ao mesmo tempo.

            ## Formatação

            - Texto em **negrito**, *itálico* e ~~riscado~~
            - Código `inline` e [links](https://commonmark.org/help/)
            - Endereços como https://www.markdownguide.org viram links automaticamente

            ## Lista de tarefas

            - [x] Criar um documento
            - [ ] Salvar em um arquivo `.md`

            ## Citação

            > A barra de ferramentas na parte de baixo insere a sintaxe para você.

            ## Tabela

            | Recurso | Suporte |
            |---------|:-------:|
            | Tabelas | ✓ |
            | Tarefas | ✓ |

            ## Código

            ```kotlin
            fun main() {
                println("Olá, Markdown!")
            }
            ```
        """.trimIndent()
    }
}
