package holygradle.stamper

import holygradle.custom_gradle.plugin_apis.StampingProvider
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.util.ConfigureUtil
import java.io.File

class StampingHandler(private val project: Project) : StampingProvider {
    private val _fileReplacers: MutableCollection<FileReplacer> = mutableListOf()
    val fileReplacers: Collection<FileReplacer> get() = _fileReplacers
    private val _patternReplacers: MutableCollection<PatternReplacer> = mutableListOf()
    val patternReplacers: Collection<PatternReplacer> get() = _patternReplacers

    val taskDescription = "Stamp things"
    var taskName = "stampFiles"
    var runPriorToBuild = false
    
    fun files(filePattern: String, config: Action<Replacer>): Replacer {
        val replacer = PatternReplacer(filePattern)
        ConfigureUtil.configure(config, replacer)
        this._patternReplacers.add(replacer)
        return replacer
    }

    fun file(filePath: String, config: Action<Replacer>): Replacer {
        val replacer = FileReplacer(File(project.projectDir, filePath))
        ConfigureUtil.configure(config, replacer)
        this._patternReplacers.add(replacer)
        return replacer
    }
}