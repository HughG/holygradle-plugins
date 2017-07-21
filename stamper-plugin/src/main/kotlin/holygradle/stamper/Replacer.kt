package holygradle.stamper

import java.io.File
import java.util.regex.Pattern

sealed class Replacer {
    private data class ReplacePair(val regex: Regex, val replacement: String)

    private val regexFindAndReplace: MutableCollection<ReplacePair> = mutableListOf()
    
    fun replaceRegex(regex: Pattern, replacementString: String) {
        regexFindAndReplace.add(ReplacePair(regex.toRegex(), replacementString))
    }

    protected fun doStringReplacementInFile(file: File) {
        val origRcText = file.readText()
        var rcText = origRcText
        for ((pattern, replacement) in regexFindAndReplace) {
            rcText = rcText.replace(pattern) {
                val (start, end) = it.destructured
                "${start}${replacement}${end}"
            }
        }
        if (origRcText != rcText) {
            file.writeText(rcText)
        }
    }
}

class FileReplacer(
        private val file: File
) : Replacer() {
    fun doPatternReplacement() {
        doStringReplacementInFile(file)
    }
}

class PatternReplacer(
        private val filePattern: String
) : Replacer() {
    fun doPatternReplacement(file: File) {
        if (file.name.endsWith(filePattern)) {
            doStringReplacementInFile(file)
        }
    }
}