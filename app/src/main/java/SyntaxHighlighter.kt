import android.text.Editable
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import androidx.core.graphics.toColorInt
import java.util.regex.Pattern

data class SyntaxColorInfo(val start: Int, val end: Int, val color: Int)

class SyntaxHighlighter {

    private val colorKeyword = "#CC7832".toColorInt()
    private val colorFunction = "#FFC66D".toColorInt()
    private val colorType = "#A9B7C6".toColorInt()
    private val colorString = "#6A8759".toColorInt()
    private val colorComment = "#808080".toColorInt()
    private val colorNumber = "#6897BB".toColorInt()
    private val colorPreprocessor = "#BBB529".toColorInt()

    private val colorXmlTag = "#E8BF6A".toColorInt()
    private val colorXmlNamespace = "#9876AA".toColorInt()
    private val colorXmlAttr = "#BABABA".toColorInt()
    private val colorXmlBracket = "#E8BF6A".toColorInt()

    private val cppKeywords = Pattern.compile("\\b(alignas|alignof|and|and_eq|asm|atomic_cancel|atomic_commit|atomic_noexcept|auto|bitand|bitor|break|case|catch|class|compl|concept|const|consteval|constexpr|constinit|const_cast|continue|co_await|co_return|co_yield|decltype|default|delete|do|dynamic_cast|else|enum|explicit|export|extern|false|for|friend|goto|if|inline|mutable|namespace|new|noexcept|not|not_eq|nullptr|operator|or|or_eq|private|protected|public|reflexpr|register|reinterpret_cast|requires|return|signed|sizeof|static|static_assert|static_cast|struct|switch|template|this|thread_local|throw|true|try|typedef|typeid|typename|union|unsigned|using|virtual|void|volatile|while|xor|xor_eq)\\b")
    private val cppTypes = Pattern.compile("\\b(bool|char|char8_t|char16_t|char32_t|double|float|int|long|short|size_t|int8_t|int16_t|int32_t|int64_t|uint8_t|uint16_t|uint32_t|uint64_t|string|vector|map|set|pair|std)\\b")
    private val cppPreprocessor = Pattern.compile("^\\s*#\\s*(include|define|undef|ifdef|ifndef|if|else|elif|endif|pragma|error|line)\\b", Pattern.MULTILINE)

    private val jvmKeywords = Pattern.compile("\\b(package|import|class|interface|fun|val|var|if|else|for|while|when|return|private|public|protected|override|abstract|data|sealed|object|companion|typealias|as|is|in|throw|try|catch|finally|this|super|void|static|final|new|switch|case|break|continue|default|synchronized|volatile|transient|native|strictfp)\\b")
    private val jvmTypes = Pattern.compile("\\b(String|Int|Long|Double|Float|Boolean|Char|Byte|Short|Unit|Any|Nothing|Array|List|Set|Map|Integer|Object|View|Context|Bundle|LayoutInflater|ViewGroup|File|Editable)\\b")

    private val xmlTags = Pattern.compile("(?<=<)[\\w.:]+|(?<=</)[\\w.:]+|(?<=<)[\\w.:]+(?=/>)")
    private val xmlNamespace = Pattern.compile("\\b[\\w-]+(?=:)")
    private val xmlAttributes = Pattern.compile("\\b[\\w.:-]+(?=\\s*=)")
    private val xmlBrackets = Pattern.compile("[<>/!?]|/>")
    private val xmlComments = Pattern.compile("<!--[\\s\\S]*?-->")

    private val patternFunctions = Pattern.compile("\\b\\w+(?=\\s*\\()")
    private val strings = Pattern.compile("\"(\\\\.|[^\"\\\\])*\"|'(\\\\.|[^'\\\\])*'")
    private val comments = Pattern.compile("//.*|/\\*[\\s\\S]*?\\*/")
    private val numbers = Pattern.compile("\\b\\d+(\\.\\d+)?\\b")

    fun applyHighlighting(editable: Editable, extension: String, start: Int = 0, end: Int = editable.length) {
        val safeStart = start.coerceIn(0, editable.length)
        val safeEnd = end.coerceIn(safeStart, editable.length)
        if (safeStart >= safeEnd) return

        val spans = editable.getSpans(safeStart, safeEnd, ForegroundColorSpan::class.java)
        for (span in spans) editable.removeSpan(span)

        if (editable.isEmpty()) return


        val textSnapshot = editable.subSequence(safeStart, safeEnd).toString()

        when (extension.lowercase()) {
            "xml" -> {

                highlightRange(editable, textSnapshot, xmlBrackets, colorXmlBracket, safeStart)
                highlightRange(editable, textSnapshot, xmlTags, colorXmlTag, safeStart)
                highlightRange(editable, textSnapshot, xmlNamespace, colorXmlNamespace, safeStart)
                highlightRange(editable, textSnapshot, xmlAttributes, colorXmlAttr, safeStart)
                highlightRange(editable, textSnapshot, xmlComments, colorComment, safeStart)
            }
            "kt", "java" -> {
                highlightRange(editable, textSnapshot, jvmKeywords, colorKeyword, safeStart)
                highlightRange(editable, textSnapshot, jvmTypes, colorType, safeStart)
                highlightRange(editable, textSnapshot, patternFunctions, colorFunction, safeStart)
                highlightRange(editable, textSnapshot, comments, colorComment, safeStart)
            }
            "cpp", "h", "hpp", "c" -> {
                highlightRange(editable, textSnapshot, cppKeywords, colorKeyword, safeStart)
                highlightRange(editable, textSnapshot, cppTypes, colorType, safeStart)
                highlightRange(editable, textSnapshot, cppPreprocessor, colorPreprocessor, safeStart)
                highlightRange(editable, textSnapshot, patternFunctions, colorFunction, safeStart)
                highlightRange(editable, textSnapshot, comments, colorComment, safeStart)
            }
        }

        highlightRange(editable, textSnapshot, strings, colorString, safeStart)
        highlightRange(editable, textSnapshot, numbers, colorNumber, safeStart)
    }

    private fun highlightRange(editable: Editable, textSnapshot: String, pattern: Pattern, color: Int, startOffset: Int) {
        val matcher = pattern.matcher(textSnapshot)

        var matchCount = 0
        while (matcher.find()) {
            matchCount++
            if (matchCount > 200) break


            val matchStart = startOffset + matcher.start()
            val matchEnd = startOffset + matcher.end()

            if (matchStart < matchEnd) {
                editable.setSpan(
                    ForegroundColorSpan(color),
                    matchStart,
                    matchEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }

    fun generateColorMap(content: String, extension: String): List<SyntaxColorInfo> {
        val colorMap = mutableListOf<SyntaxColorInfo>()

        val patterns = when (extension.lowercase()) {
            "xml" -> mutableMapOf(
                colorXmlTag to xmlTags, colorXmlAttr to xmlAttributes,
                colorXmlBracket to xmlBrackets, colorXmlNamespace to xmlNamespace,
                colorComment to xmlComments
            )
            "cpp", "hpp", "h", "c" -> mutableMapOf(
                colorKeyword to cppKeywords, colorType to cppTypes,
                colorPreprocessor to cppPreprocessor, colorFunction to patternFunctions,
                colorComment to comments
            )
            else -> mutableMapOf(
                colorKeyword to jvmKeywords, colorType to jvmTypes,
                colorFunction to patternFunctions, colorComment to comments
            )
        }

        patterns[colorString] = strings
        patterns[colorNumber] = numbers

        patterns.forEach { (color, pattern) ->
            val matcher = pattern.matcher(content)
            while (matcher.find()) {
                colorMap.add(SyntaxColorInfo(matcher.start(), matcher.end(), color))
            }
        }
        return colorMap
    }
}