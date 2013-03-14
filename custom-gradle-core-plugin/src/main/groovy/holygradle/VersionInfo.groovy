package holygradle.customgradleimport org.gradle.*import org.gradle.api.*class VersionInfo {    private Project project    private def versions = [:]    private def buildscriptDependencies = null    private String windowsUpdates = null        public static VersionInfo defineExtension(Project project) {        if (project == project.rootProject) {            project.extensions.create("versionInfo", VersionInfo, project)        } else {            project.extensions.add("versionInfo", project.rootProject.extensions.findByName("versionInfo"))        }    }        VersionInfo(Project project) {        this.project = project                specifyPowershell("Windows", "[System.Environment]::OSVersion.VersionString.Trim()")        specify("gradle", project.gradle.gradleVersion)        specify("custom-gradle", project.gradle.gradleHomeDir.parentFile.parentFile.name.split("-")[-1])        specify("custom-gradle (init script)", project.ext.initScriptVersion)    }        public void specify(String item, String version) {        versions[item] = version    }        public void specifyPowershell(String item, String powershellCommand) {        def powershellOutput = new ByteArrayOutputStream()        def execResult = project.exec {            commandLine "powershell", "-Command", powershellCommand            setStandardOutput powershellOutput            setErrorOutput new ByteArrayOutputStream()            setIgnoreExitValue true        }        specify(item, powershellOutput.toString().trim())    }        public def getBuildscriptDependencies() {        if (buildscriptDependencies == null) {            buildscriptDependencies = []            project.getBuildscript().getConfigurations().each { conf ->                conf.resolvedConfiguration.getResolvedArtifacts().each { art ->                    buildscriptDependencies.add(art.getModuleVersion().getId())                }            }        }        buildscriptDependencies    }        public String getVersion(String plugin) {        versions[plugin]    }        public def getVersions() {        versions    }        private String getWindowsUpdates() {        if (windowsUpdates == null) {            def wmicOutput = new ByteArrayOutputStream()            def execResult = project.exec {                commandLine "wmic", "qfe", "list"                setStandardOutput wmicOutput                setErrorOutput new ByteArrayOutputStream()                setIgnoreExitValue true            }            windowsUpdates = wmicOutput.toString()        }        windowsUpdates    }        public void writeFile(File file) {        StringBuilder str = new StringBuilder()                str.append "Versions\n"        str.append "========\n"        getVersions().each { item, version ->            str.append "${item} : ${version}\n"        }        getBuildscriptDependencies().each { version ->            str.append "${version.getName()} : ${version.getVersion()}\n"        }        str.append "\n"                def updates = getWindowsUpdates()        if (updates != null) {            str.append "Windows Updates\n"            str.append "===============\n"            str.append updates            str.append "\n"        }                file.write(str.toString())    }}