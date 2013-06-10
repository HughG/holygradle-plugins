package holygradle.custom_gradle

import org.gradle.api.DefaultTask

class StatedPrerequisiteTask extends DefaultTask {
    private StatedPrerequisite statedPrerequisite
    
    public void initialize(StatedPrerequisite statedPrerequisite) {
        this.statedPrerequisite = statedPrerequisite
        doLast {
            statedPrerequisite.check()
        }
    }
    
    public boolean check() {
        statedPrerequisite.check()
    }
}