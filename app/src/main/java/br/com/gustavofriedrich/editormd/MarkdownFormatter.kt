package br.com.gustavofriedrich.editormd

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** Operações de edição que inserem sintaxe Markdown na posição do cursor/seleção. */
object MarkdownFormatter {

    /** Envolve a seleção com [prefix]/[suffix]; remove se já estiver envolvida. */
    fun wrap(
        value: TextFieldValue,
        prefix: String,
        suffix: String = prefix,
        placeholder: String,
    ): TextFieldValue {
        val text = value.text
        val start = value.selection.min
        val end = value.selection.max

        if (start == end) {
            val newText = text.substring(0, start) + prefix + placeholder + suffix + text.substring(end)
            val selStart = start + prefix.length
            return TextFieldValue(newText, TextRange(selStart, selStart + placeholder.length))
        }

        val alreadyWrapped = start >= prefix.length &&
            text.startsWith(prefix, start - prefix.length) &&
            text.startsWith(suffix, end)
        if (alreadyWrapped) {
            val newText = text.substring(0, start - prefix.length) +
                text.substring(start, end) +
                text.substring(end + suffix.length)
            return TextFieldValue(newText, TextRange(start - prefix.length, end - prefix.length))
        }

        val newText = text.substring(0, start) + prefix + text.substring(start, end) + suffix + text.substring(end)
        return TextFieldValue(newText, TextRange(start + prefix.length, end + prefix.length))
    }

    /** Código inline para seleção de uma linha; bloco cercado para várias linhas. */
    fun code(value: TextFieldValue): TextFieldValue {
        val selected = value.text.substring(value.selection.min, value.selection.max)
        return if ('\n' in selected) {
            wrap(value, "```\n", "\n```", placeholder = "")
        } else {
            wrap(value, "`", placeholder = "código")
        }
    }

    fun link(value: TextFieldValue): TextFieldValue =
        wrap(value, "[", "](https://)", placeholder = "texto do link")

    /** Adiciona (ou remove, se todas já tiverem) [prefix] no início de cada linha selecionada. */
    fun prefixLines(value: TextFieldValue, prefix: String): TextFieldValue =
        transformLines(value) { lines ->
            if (lines.all { it.startsWith(prefix) }) lines.map { it.removePrefix(prefix) }
            else lines.map { prefix + it }
        }

    fun numberedList(value: TextFieldValue): TextFieldValue {
        val numbered = Regex("^\\d+\\. ")
        return transformLines(value) { lines ->
            if (lines.all { numbered.containsMatchIn(it) }) lines.map { it.replaceFirst(numbered, "") }
            else lines.mapIndexed { i, line -> "${i + 1}. $line" }
        }
    }

    /** Alterna o nível do título da linha atual: nenhum → H1 → H2 → H3 → nenhum. */
    fun heading(value: TextFieldValue): TextFieldValue {
        val heading = Regex("^(#{1,6}) ")
        return transformLines(value) { lines ->
            lines.map { line ->
                val level = heading.find(line)?.groupValues?.get(1)?.length ?: 0
                val body = line.replaceFirst(heading, "")
                if (level >= 3) body else "#".repeat(level + 1) + " " + body
            }
        }
    }

    /** Insere um bloco em linhas próprias, separado do texto vizinho. */
    fun insertBlock(value: TextFieldValue, block: String): TextFieldValue {
        val text = value.text
        val pos = value.selection.max
        val before = when {
            pos == 0 -> ""
            text.substring(0, pos).endsWith("\n\n") -> ""
            text[pos - 1] == '\n' -> "\n"
            else -> "\n\n"
        }
        val after = if (pos < text.length && text[pos] != '\n') "\n\n" else "\n"
        val inserted = before + block + after
        val newText = text.substring(0, pos) + inserted + text.substring(pos)
        return TextFieldValue(newText, TextRange(pos + inserted.length))
    }

    const val TABLE_TEMPLATE = "| Coluna 1 | Coluna 2 |\n|----------|----------|\n| Valor    | Valor    |"
    const val HORIZONTAL_RULE = "---"

    private fun transformLines(value: TextFieldValue, transform: (List<String>) -> List<String>): TextFieldValue {
        val text = value.text
        val start = value.selection.min
        val end = value.selection.max
        val lineStart = text.lastIndexOf('\n', start - 1) + 1
        val lineEnd = text.indexOf('\n', end).let { if (it < 0) text.length else it }

        val block = transform(text.substring(lineStart, lineEnd).split('\n')).joinToString("\n")
        val newText = text.substring(0, lineStart) + block + text.substring(lineEnd)
        val selection = if (start == end) TextRange(lineStart + block.length)
        else TextRange(lineStart, lineStart + block.length)
        return TextFieldValue(newText, selection)
    }
}
