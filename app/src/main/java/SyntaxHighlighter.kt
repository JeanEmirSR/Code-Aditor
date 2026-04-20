import android.graphics.Color
import android.text.Editable
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import java.util.regex.Pattern
data class SyntaxColorInfo(val start: Int, val end: Int, val color: Int)
class SyntaxHighlighter {

    // --- PALETA DARCULA (Android Studio) ---
    private val colorKeyword = Color.parseColor("#CC7832")    // Naranja
    private val colorFunction = Color.parseColor("#FFC66D")   // Amarillo
    private val colorType = Color.parseColor("#A9B7C6")       // Gris claro
    private val colorString = Color.parseColor("#6A8759")     // Verde
    private val colorComment = Color.parseColor("#808080")    // Gris
    private val colorNumber = Color.parseColor("#6897BB")     // Azul
    private val colorPreprocessor = Color.parseColor("#BBB529") // Oro/Amarillo

    // --- COLORES XML ESPECÍFICOS ---
    private val colorXmlTag = Color.parseColor("#E8BF6A")       // Amarillo/Dorado (Componentes)
    private val colorXmlNamespace = Color.parseColor("#9876AA") // Morado (android, app, tools)
    private val colorXmlAttr = Color.parseColor("#BABABA")      // Gris (Atributos: layout_width, id)
    private val colorXmlBracket = Color.parseColor("#E8BF6A")   // Dorado (<, >, />)

    // --- PATRONES C++ ---
    private val cppKeywords = Pattern.compile("\\b(alignas|alignof|and|and_eq|asm|atomic_cancel|atomic_commit|atomic_noexcept|auto|bitand|bitor|break|case|catch|class|compl|concept|const|consteval|constexpr|constinit|const_cast|continue|co_await|co_return|co_yield|decltype|default|delete|do|dynamic_cast|else|enum|explicit|export|extern|false|for|friend|goto|if|inline|mutable|namespace|new|noexcept|not|not_eq|nullptr|operator|or|or_eq|private|protected|public|reflexpr|register|reinterpret_cast|requires|return|signed|sizeof|static|static_assert|static_cast|struct|switch|template|this|thread_local|throw|true|try|typedef|typeid|typename|union|unsigned|using|virtual|void|volatile|while|xor|xor_eq)\\b")
    private val cppTypes = Pattern.compile("\\b(bool|char|char8_t|char16_t|char32_t|double|float|int|long|short|size_t|int8_t|int16_t|int32_t|int64_t|uint8_t|uint16_t|uint32_t|uint64_t|string|vector|map|set|pair|std)\\b")
    private val cppPreprocessor = Pattern.compile("^\\s*#\\s*(include|define|undef|ifdef|ifndef|if|else|elif|endif|pragma|error|line)\\b", Pattern.MULTILINE)

    // --- PATRONES KOTLIN / JAVA ---
    private val jvmKeywords = Pattern.compile("\\b(package|import|class|interface|fun|val|var|if|else|for|while|when|return|private|public|protected|override|abstract|data|sealed|object|companion|typealias|as|is|in|throw|try|catch|finally|this|super|void|static|final|new|switch|case|break|continue|default|synchronized|volatile|transient|native|strictfp)\\b")
    private val jvmTypes = Pattern.compile("\\b(String|Int|Long|Double|Float|Boolean|Char|Byte|Short|Unit|Any|Nothing|Array|List|Set|Map|Integer|Object|View|Context|Bundle|LayoutInflater|ViewGroup|File|Editable)\\b")

    // --- PATRONES XML ---
    private val xmlTags = Pattern.compile("(?<=<)[\\w\\.:]+|(?<=</)[\\w\\.:]+|(?<=<)[\\w\\.:]+(?=/>)")
    private val xmlNamespace = Pattern.compile("\\b[\\w-]+(?=:)")
    private val xmlAttributes = Pattern.compile("\\b[\\w\\.:-]+(?=\\s*=)")
    private val xmlBrackets = Pattern.compile("[<>/!\\?]|/>")
    private val xmlComments = Pattern.compile("")

    // --- COMUNES ---
    private val patternFunctions = Pattern.compile("\\b\\w+(?=\\s*\\()")
    private val strings = Pattern.compile("\"(\\\\.|[^\"\\\\])*\"|'(\\\\.|[^'\\\\])*'")
    private val comments = Pattern.compile("//.*|/\\*(?:.|[\\n\\r])*?\\*/")
    private val numbers = Pattern.compile("\\b\\d+(\\.\\d+)?\\b")

    /**
     * Aplica resaltado en el rango visible (Viewport)
     */
    fun applyHighlighting(editable: Editable, extension: String, start: Int = 0, end: Int = editable.length) {
        val safeStart = start.coerceIn(0, editable.length)
        val safeEnd = end.coerceIn(safeStart, editable.length)

        // Limpiar spans en el rango
        val spans = editable.getSpans(safeStart, safeEnd, ForegroundColorSpan::class.java)
        for (span in spans) editable.removeSpan(span)

        if (editable.isEmpty()) return

        when (extension.lowercase()) {
            "xml" -> {
                highlightRange(editable, xmlBrackets, colorXmlBracket, safeStart, safeEnd)
                highlightRange(editable, xmlTags, colorXmlTag, safeStart, safeEnd)
                highlightRange(editable, xmlNamespace, colorXmlNamespace, safeStart, safeEnd)
                highlightRange(editable, xmlAttributes, colorXmlAttr, safeStart, safeEnd)
                highlightRange(editable, xmlComments, colorComment, safeStart, safeEnd)
            }
            "kt", "java" -> {
                highlightRange(editable, jvmKeywords, colorKeyword, safeStart, safeEnd)
                highlightRange(editable, jvmTypes, colorType, safeStart, safeEnd)
                highlightRange(editable, patternFunctions, colorFunction, safeStart, safeEnd)
                highlightRange(editable, comments, colorComment, safeStart, safeEnd)
            }
            "cpp", "h", "hpp", "c" -> {
                highlightRange(editable, cppKeywords, colorKeyword, safeStart, safeEnd)
                highlightRange(editable, cppTypes, colorType, safeStart, safeEnd)
                highlightRange(editable, cppPreprocessor, colorPreprocessor, safeStart, safeEnd)
                highlightRange(editable, patternFunctions, colorFunction, safeStart, safeEnd)
                highlightRange(editable, comments, colorComment, safeStart, safeEnd)
            }
        }

        // Aplicar comunes (strings y números) a todos
        highlightRange(editable, strings, colorString, safeStart, safeEnd)
        highlightRange(editable, numbers, colorNumber, safeStart, safeEnd)
    }

    private fun highlightRange(editable: Editable, pattern: Pattern, color: Int, startRange: Int, endRange: Int) {
        val matcher = pattern.matcher(editable)

        // Seguridad para no procesar fuera de límites
        val safeStart = startRange.coerceAtLeast(0)
        val safeEnd = endRange.coerceAtMost(editable.length)
        if (safeStart >= safeEnd) return

        matcher.region(safeStart, safeEnd)

        var matchCount = 0
        while (matcher.find()) {
            matchCount++

            // FRENO DE EMERGENCIA: Si hay más de 200 coincidencias de un solo tipo
            // en el viewport, algo anda mal o el archivo es demasiado denso.
            if (matchCount > 200) break

            val start = matcher.start()
            val end = matcher.end()

            if (start < end) {
                editable.setSpan(
                    ForegroundColorSpan(color),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }
    // Esta función solo BUSCA, no PINTA. Es ultra rápida.
    fun generateColorMap(content: String, extension: String): List<SyntaxColorInfo> {
        val colorMap = mutableListOf<SyntaxColorInfo>()
        val safeContent = content // Evitamos tocar el Editable aquí

        val patterns = when (extension.lowercase()) {
            "xml" -> mapOf(colorXmlTag to xmlTags, colorXmlAttr to xmlAttributes) // simplificado
            "cpp", "hpp", "h" -> mapOf(colorKeyword to cppKeywords, colorType to cppTypes, colorComment to comments)
            else -> mapOf(colorKeyword to jvmKeywords)
        }

        patterns.forEach { (color, pattern) ->
            val matcher = pattern.matcher(safeContent)
            while (matcher.find()) {
                colorMap.add(SyntaxColorInfo(matcher.start(), matcher.end(), color))
            }
        }
        return colorMap
    }
}