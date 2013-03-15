package holygradle.stamper

import org.gradle.api.Project
import org.gradle.util.ConfigureUtil

class StampingHandler {
    Project m_project
    
    public def m_fileReplacers = []
    public def m_patternReplacers = []
    
    public String taskName = "stampFiles"
    public String taskDescription = "Stamp things"
    public boolean runPriorToBuild = false
    
    StampingHandler(Project project) {
        m_project = project
    }    
    
    def files(String filePattern, Closure config) {
        def replacer = new Replacer(filePattern)
        ConfigureUtil.configure(config, replacer)
        m_patternReplacers.add(replacer)
        return replacer
    }    
    def file(String filePath, Closure config) {
        def replacer = new Replacer(new File(m_project.projectDir, filePath))
        ConfigureUtil.configure(config, replacer)
        m_fileReplacers.add(replacer)
        return replacer
    }
}