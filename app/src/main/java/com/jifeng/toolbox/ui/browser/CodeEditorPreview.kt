package com.jifeng.toolbox.ui.browser

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * 代码编辑器/预览器 - 多语言语法高亮 + 行号。
 */
object CodeEditorPreview {

    enum class Language {
        KOTLIN, JAVA, PYTHON, JAVASCRIPT, TYPESCRIPT, GO, RUST,
        C, CPP, HTML, CSS, JSON, YAML, XML, SQL, SHELL, PLAIN
    }

    fun detectLanguage(file: File): Language = when (file.extension.lowercase()) {
        "kt", "kts" -> Language.KOTLIN
        "java" -> Language.JAVA
        "py" -> Language.PYTHON
        "js", "mjs" -> Language.JAVASCRIPT
        "ts", "tsx" -> Language.TYPESCRIPT
        "go" -> Language.GO
        "rs" -> Language.RUST
        "c", "h" -> Language.C
        "cpp", "cc", "cxx", "hpp" -> Language.CPP
        "html", "htm" -> Language.HTML
        "css", "scss" -> Language.CSS
        "json" -> Language.JSON
        "yaml", "yml" -> Language.YAML
        "xml", "svg" -> Language.XML
        "sql" -> Language.SQL
        "sh", "bash", "zsh" -> Language.SHELL
        else -> Language.PLAIN
    }

    private val KW_COLOR = Color(0xFF569CD6)
    private val STR_COLOR = Color(0xFFCE9178)
    private val CMT_COLOR = Color(0xFF6A9955)
    private val NUM_COLOR = Color(0xFFB5CEA8)
    private val TYPE_COLOR = Color(0xFF4EC9B0)
    private val DEF_COLOR = Color(0xFFD4D4D4)

    private val KEYWORDS = mapOf(
        Language.KOTLIN to setOf("val","var","fun","class","object","interface","enum","when","if","else","for","while","do","return","break","continue","import","package","private","public","protected","internal","override","abstract","open","sealed","data","companion","suspend","try","catch","finally","throw","is","as","in","null","true","false","this","super"),
        Language.JAVA to setOf("public","private","protected","class","interface","enum","extends","implements","static","final","void","int","long","float","double","boolean","char","String","new","return","if","else","for","while","switch","case","break","continue","try","catch","finally","throw","import","package","null","true","false","this","super"),
        Language.PYTHON to setOf("def","class","import","from","return","if","elif","else","for","while","break","continue","try","except","finally","raise","with","as","yield","lambda","pass","True","False","None","and","or","not","in","is","async","await"),
        Language.JAVASCRIPT to setOf("function","var","let","const","class","extends","import","export","default","return","if","else","for","while","switch","case","break","continue","try","catch","finally","throw","new","typeof","instanceof","null","undefined","true","false","this","async","await"),
        Language.GO to setOf("func","package","import","var","const","type","struct","interface","map","chan","go","select","switch","case","default","if","else","for","range","return","break","continue","defer","nil","true","false","make","len","append"),
        Language.RUST to setOf("fn","let","mut","const","struct","enum","impl","trait","pub","use","mod","self","super","where","match","if","else","for","while","loop","return","break","continue","async","await","move","ref","type","as","true","false"),
        Language.SQL to setOf("SELECT","FROM","WHERE","INSERT","UPDATE","DELETE","CREATE","DROP","ALTER","TABLE","JOIN","LEFT","RIGHT","INNER","ON","AND","OR","NOT","NULL","IS","IN","ORDER","BY","GROUP","HAVING","LIMIT","AS","SET","INTO","VALUES"),
    )

    fun highlightLine(line: String, lang: Language): AnnotatedString {
        if (lang == Language.PLAIN) return AnnotatedString(line)
        val b = AnnotatedString.Builder()
        val kws = KEYWORDS[lang] ?: emptySet()
        var i = 0
        while (i < line.length) {
            // Line comments
            if (i + 1 < line.length && line[i] == '/' && line[i + 1] == '/') {
                b.pushStyle(SpanStyle(color = CMT_COLOR))
                b.append(line.substring(i))
                b.pop()
                break
            }
            // Python comments
            if (lang == Language.PYTHON && line[i] == '#') {
                b.pushStyle(SpanStyle(color = CMT_COLOR))
                b.append(line.substring(i))
                b.pop()
                break
            }
            // String literals
            if (line[i] == '"' || line[i] == '\'') {
                val q = line[i]
                b.pushStyle(SpanStyle(color = STR_COLOR))
                b.append(q)
                i++
                while (i < line.length && line[i] != q) {
                    if (line[i] == '\\' && i + 1 < line.length) {
                        b.append(line.substring(i, i + 2))
                        i += 2
                    } else {
                        b.append(line[i])
                        i++
                    }
                }
                if (i < line.length) {
                    b.append(line[i])
                    i++
                }
                b.pop()
                continue
            }
            // Words (keywords/identifiers)
            if (line[i].isLetter() || line[i] == '_') {
                val s = i
                while (i < line.length && (line[i].isLetterOrDigit() || line[i] == '_')) i++
                val w = line.substring(s, i)
                val c = when {
                    w in kws -> KW_COLOR
                    w.first().isUpperCase() && lang != Language.SQL -> TYPE_COLOR
                    w.all { it.isDigit() } -> NUM_COLOR
                    else -> DEF_COLOR
                }
                b.pushStyle(SpanStyle(color = c))
                b.append(w)
                b.pop()
                continue
            }
            // Numbers
            if (line[i].isDigit()) {
                b.pushStyle(SpanStyle(color = NUM_COLOR))
                while (i < line.length && (line[i].isDigit() || line[i] == '.')) {
                    b.append(line[i])
                    i++
                }
                b.pop()
                continue
            }
            // Default
            b.pushStyle(SpanStyle(color = DEF_COLOR))
            b.append(line[i])
            b.pop()
            i++
        }
        return b.toAnnotatedString()
    }
}

@Composable
fun CodePreviewContent(
    code: String,
    language: CodeEditorPreview.Language = CodeEditorPreview.Language.PLAIN,
    modifier: Modifier = Modifier
) {
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    val lines = code.lines()
    Surface(modifier = modifier.fillMaxSize(), color = Color(0xFF1E1E1E)) {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 8.dp).verticalScroll(vScroll)) {
                lines.forEachIndexed { idx, _ ->
                    Text(
                        "${idx + 1}",
                        color = Color(0xFF858585),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f).padding(8.dp).horizontalScroll(hScroll).verticalScroll(vScroll)
            ) {
                lines.forEach { line ->
                    Text(
                        text = CodeEditorPreview.highlightLine(line, language),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFD4D4D4),
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }
    }
}
