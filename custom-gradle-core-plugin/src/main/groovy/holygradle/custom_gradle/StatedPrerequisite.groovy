package holygradle.custom_gradle

import holygradle.custom_gradle.util.CamelCase
import org.gradle.api.DefaultTask
import org.gradle.api.Task

class StatedPrerequisite<T> {
    public final String name
    private final PrerequisitesCheckerFactory<T> checkerFactory
    public final T param
    private boolean hasRun = false
    private boolean ok = false
    private Task checkTask = null
    
    StatedPrerequisite(PrerequisitesCheckerFactory<T> checkerFactory) {
        this(checkerFactory, null)
    }
    
    StatedPrerequisite(PrerequisitesCheckerFactory<T> checkerFactory, T params) {
        this.name = checkerFactory.name
        this.checkerFactory = checkerFactory
        this.param = params
    }
    
    public boolean check() {
        if (!hasRun) {
            hasRun = true
            ok = checkerFactory.makeChecker(param).run()
        }
        ok
    }

    public Task getTask() {
        if (checkTask == null) {
            Collection<String> nameComponents = []
            nameComponents.add("checkPrerequisite")
            nameComponents.add(name)
            if (param != null) {
                if (param instanceof Iterable) {
                    nameComponents.addAll(param*.toString())
                } else {
                    nameComponents.add(param.toString())
                }
            }
            String taskName = CamelCase.build(nameComponents)
            checkTask = checkerFactory.project.task(taskName, type: DefaultTask) { DefaultTask task ->
                task.doLast { this.check() }
            }
        }
        checkTask
    }
}
