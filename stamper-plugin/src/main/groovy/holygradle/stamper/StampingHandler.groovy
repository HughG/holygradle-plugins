package holygradle.stamper

import holygradle.custom_gradle.plugin_apis.StampingProvider
import org.gradle.api.Project
import org.gradle.util.ConfigureUtil

class StampingHandler implements StampingProvider {
    private Project project
    private Collection<Replacer> fileReplacers = []
    private Collection<Replacer> patternReplacers = []
    
    public String taskDescription = "Stamp things"
    private String taskName = "stampFiles"
    private boolean runPriorToBuild = false
    
    StampingHandler(Project project) {
        this.project = project
    }

    public Replacer files(String filePattern, Closure config) {
        Replacer replacer = new Replacer(filePattern)
        ConfigureUtil.configure(config, replacer)
        this.patternReplacers.add(replacer)
        return replacer
    }

    public Replacer file(String filePath, Closure config) {
        Replacer replacer = new Replacer(new File(project.projectDir, filePath))
        ConfigureUtil.configure(config, replacer)
        this.fileReplacers.add(replacer)
        return replacer
    }

    public Iterable<Replacer> getFileReplacers() {
        return this.fileReplacers
    }

    public Iterable<Replacer> getPatternReplacers() {
        return this.patternReplacers
    }

    @Override
    String getTaskName() {
        return this.taskName
    }

    @Override
    public void setTaskName(String n) {
        this.taskName = n
    }
    
    @Override
    boolean getRunPriorToBuild() {
        return this.runPriorToBuild
    }

    public void setRunPriorToBuild(boolean runPriorToBuild) {
        this.runPriorToBuild = runPriorToBuild
    }
}