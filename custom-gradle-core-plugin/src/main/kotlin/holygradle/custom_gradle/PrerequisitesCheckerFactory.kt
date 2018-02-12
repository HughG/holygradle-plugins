package holygradle.custom_gradle

import org.gradle.api.Action
import org.gradle.api.Project

class PrerequisitesCheckerFactory(
            val project: Project,
            val name: String,
            private val checkAction: Action<PrerequisitesChecker>
) {
    fun makeChecker(params: Array<out Any>?): PrerequisitesChecker =
            PrerequisitesChecker(project, name, checkAction, params)
}