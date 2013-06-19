package holygradle.custom_gradle

import holygradle.custom_gradle.util.CamelCase
import org.gradle.api.Task

class StatedPrerequisite {
    public final String name
    private final PrerequisitesChecker checker
    public final Object[] params = null
    private boolean hasRun = false
    private boolean ok = false
    private Task checkTask = null
    
    StatedPrerequisite(PrerequisitesChecker checker) {
        this.name = checker.name
        this.checker = checker
    }
    
    StatedPrerequisite(PrerequisitesChecker checker, Object[] params) {
        this(checker)
        this.params = params
    }
    
    public boolean check() {
        if (!hasRun) {
            hasRun = true
            ok = checker.run(params)
        }
        ok
    }
    
    public Task getTask() {
        if (checkTask == null) {
            Collection<String> nameComponents = []
            nameComponents.add("checkPrerequisite")
            nameComponents.add(name)
            if (params != null) {
                nameComponents.addAll(params)
            }
            String taskName = CamelCase.build(nameComponents)
            checkTask = checker.project.task(taskName, type: StatedPrerequisiteTask) { StatedPrerequisiteTask task ->
                task.initialize(this)
            }
        }
        checkTask
    }
}
