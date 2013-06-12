package holygradle.stamper

import org.gradle.api.Project
import org.gradle.util.ConfigureUtil

class StampingHandler {
    private Project project
    
    public Collection<Replacer> fileReplacers = []
    public Collection<Replacer> patternReplacers = []
    
    public String taskName = "stampFiles"
    public String taskDescription = "Stamp things"
    public boolean runPriorToBuild = false
    
    StampingHandler(Project project) {
        this.project = project
    }    
    
    def files(String filePattern, Closure config) {
        Replacer replacer = new Replacer(filePattern)
        ConfigureUtil.configure(config, replacer)
        patternReplacers.add(replacer)
        return replacer
    }    
    def file(String filePath, Closure config) {
        Replacer replacer = new Replacer(new File(project.projectDir, filePath))
        ConfigureUtil.configure(config, replacer)
        fileReplacers.add(replacer)
        return replacer
    }
}