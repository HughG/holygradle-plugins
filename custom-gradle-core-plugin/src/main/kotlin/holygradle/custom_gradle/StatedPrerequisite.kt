package holygradle.custom_gradle

import holygradle.custom_gradle.util.CamelCase
import holygradle.kotlin.dsl.task
import org.gradle.api.DefaultTask
import org.gradle.api.Task

class StatedPrerequisite(
        private val checkerFactory: PrerequisitesCheckerFactory,
        val param: Array<out Any>?) {
    val name get() = checkerFactory.name

    constructor(checkerFactory: PrerequisitesCheckerFactory) : this(checkerFactory, null)

    private val ok: Boolean by lazy {
        checkerFactory.makeChecker(param).run()
    }

    fun check(): Boolean {
        return ok
    }

    val task: Task by lazy {
        val nameComponents = mutableListOf<String>()
        nameComponents.add("checkPrerequisite")
        nameComponents.add(name)
        if (param != null) {
            nameComponents.addAll(param.map { it.toString() })
        }
        val taskName = CamelCase.build(nameComponents)
        checkerFactory.project.task<DefaultTask>(taskName) {
            doLast { this@StatedPrerequisite.check() }
        }
    }
}
