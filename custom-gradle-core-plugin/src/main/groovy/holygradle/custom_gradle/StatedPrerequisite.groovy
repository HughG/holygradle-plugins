package holygradle.custom_gradle
import holygradle.util.*
import org.gradle.*
import org.gradle.api.*
import holygradle.custom_gradle.util.CamelCase
class StatedPrerequisite {
    public final String name
    private final PrerequisitesChecker checker
    public final def params = null
    private boolean hasRun = false
    private boolean ok = false
    private Task checkTask = null
    
    StatedPrerequisite(PrerequisitesChecker checker) {
        this.name = checker.name
        this.checker = checker
    }
    
    StatedPrerequisite(PrerequisitesChecker checker, def params) {
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
            def nameComponents = []
            nameComponents.add("checkPrerequisite")
            nameComponents.add(name)
            if (params != null) {
                nameComponents.addAll(params)
            }
            def taskName = CamelCase.build(nameComponents)
            checkTask = checker.project.task(taskName, type: StatedPrerequisiteTask) {
                initialize(this)
            }
        }
        checkTask
    }
}
