package holygradle.stamper

import groovy.lang.Closure
import holygradle.custom_gradle.plugin_apis.StampingProvider
import org.gradle.api.Project
import org.gradle.util.ConfigureUtil
import java.io.File
import java.nio.charset.Charset

open class StampingHandler(private val project: Project) : StampingProvider {
    private val _fileReplacers: MutableCollection<FileReplacer> = mutableListOf()
    val fileReplacers: Collection<FileReplacer> get() = _fileReplacers
    private val _patternReplacers: MutableCollection<PatternReplacer> = mutableListOf()
    val patternReplacers: Collection<PatternReplacer> get() = _patternReplacers

    val taskDescription = "Stamp things"
    override var taskName: String = "stampFiles"
    override var runPriorToBuild: Boolean = false
    val charsetName: String = Charset.defaultCharset().name()

    fun files(filePattern: String, config: Closure<Replacer>): Replacer {
        val replacer = PatternReplacer(filePattern, charsetName)
        ConfigureUtil.configure(config, replacer)
        this._patternReplacers.add(replacer)
        return replacer
    }

    fun file(filePath: String, config: Closure<Replacer>): Replacer {
        val replacer = FileReplacer(File(project.projectDir, filePath), charsetName)
        ConfigureUtil.configure(config, replacer)
        this._fileReplacers.add(replacer)
        return replacer
    }
}