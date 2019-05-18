package holygradle.stamper

import java.io.File
import java.nio.charset.Charset
import java.util.regex.Pattern

sealed class Replacer(charsetName: String) {
    private data class ReplacePair(val regex: Regex, val replacement: String)

    private val regexFindAndReplace: MutableCollection<ReplacePair> = mutableListOf()
    private val charset = Charset.forName(charsetName)

    @Suppress("unused") // API method
    fun replaceRegex(regex: Pattern, replacementString: String) {
        regexFindAndReplace.add(ReplacePair(regex.toRegex(), replacementString))
    }

    protected fun doStringReplacementInFile(file: File) {
        val origRcText = file.readText(charset)
        var rcText = origRcText
        for ((pattern, replacement) in regexFindAndReplace) {
            rcText = rcText.replace(pattern) {
                val (start, end) = it.destructured
                "${start}${replacement}${end}"
            }
        }
        if (origRcText != rcText) {
            file.writeText(rcText, charset)
        }
    }
}

class FileReplacer(
        private val file: File,
        charsetName: String
) : Replacer(charsetName) {
    fun doPatternReplacement() {
        doStringReplacementInFile(file)
    }
}

class PatternReplacer(
        private val filePattern: String,
        charsetName: String
) : Replacer(charsetName) {
    fun doPatternReplacement(file: File) {
        if (file.name.endsWith(filePattern)) {
            doStringReplacementInFile(file)
        }
    }
}