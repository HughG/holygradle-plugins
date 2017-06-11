package holygradle.custom_gradle

import org.gradle.api.Action
import org.gradle.api.Project

class PrerequisitesCheckerFactory<T> {
    public final String name
    public final Project project
    private final Action<PrerequisitesChecker<T>> checkAction

    PrerequisitesCheckerFactory(Project project, String name, Action<PrerequisitesChecker<T>> checkAction) {
        this.project = project
        this.name = name
        this.checkAction = checkAction
    }

    PrerequisitesChecker<T> makeChecker(T params) {
        new PrerequisitesChecker<T>(project, name, checkAction, params)
    }
}