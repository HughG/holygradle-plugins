package holygradle.stamper

import groovy.lang.Closure
import holygradle.custom_gradle.plugin_apis.StampingProvider
import org.gradle.api.Project
import org.gradle.util.ConfigureUtil
import java.io.File

open class StampingHandler(private val project: Project) : StampingProvider {
    private val _fileReplacers: MutableCollection<FileReplacer> = mutableListOf()
    val fileReplacers: Collection<FileReplacer> get() = _fileReplacers
    private val _patternReplacers: MutableCollection<PatternReplacer> = mutableListOf()
    val patternReplacers: Collection<PatternReplacer> get() = _patternReplacers

    val taskDescription = "Stamp things"
    private var _runPriorToBuild = false

    override var taskName: String = "stampFiles"

    override val runPriorToBuild: Boolean
        get() = _runPriorToBuild

    fun files(filePattern: String, config: Closure<Replacer>): Replacer {
        val replacer = PatternReplacer(filePattern)
        ConfigureUtil.configure(config, replacer)
        this._patternReplacers.add(replacer)
        return replacer
    }

    fun file(filePath: String, config: Closure<Replacer>): Replacer {
        val replacer = FileReplacer(File(project.projectDir, filePath))
        ConfigureUtil.configure(config, replacer)
        this._fileReplacers.add(replacer)
        return replacer
    }
}