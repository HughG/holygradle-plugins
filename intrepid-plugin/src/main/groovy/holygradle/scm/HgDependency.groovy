package holygradle

import org.gradle.api.*
import org.gradle.api.tasks.*
import com.aragost.javahg.*
import com.aragost.javahg.commands.CloneCommand
import com.aragost.javahg.commands.flags.CloneCommandFlags
import com.aragost.javahg.commands.UpdateCommand

class HgDependency extends SourceDependency {
    private final BuildScriptDependencies buildScriptDependencies
    
    public HgDependency(Project project, SourceDependencyHandler sourceDependency, BuildScriptDependencies buildScriptDependencies) {
        super(project, sourceDependency)
        this.buildScriptDependencies = buildScriptDependencies
    }
    
    @Override
    public String getFetchTaskDescription() {
        "Retrieves an Hg Clone for '${sourceDependency.name}' into your workspace."
    }
    
    private void cacheCredentials(String username, String password, String repoUrl) {
        def credUrl = repoUrl.split("@")[0]
        def credentialStorePath = buildScriptDependencies.getPath("credential-store").path
        // println "${credentialStorePath} ${credUrl} ${username} <password>"
        def execResult = project.exec {
            setIgnoreExitValue true
            commandLine credentialStorePath, "${username}@@${credUrl}@Mercurial", username, password
        }
        if (execResult.getExitValue() == -1073741515) {
            println "-"*80
            println "Failed to cache Mercurial credentials. This is probably because you don't have the " +
                    "Visual C++ 2010 Redistributable installed on your machine. Please download and " +
                    "install the x86 version before continuing. Here's the link: "
            println "    http://www.microsoft.com/download/en/details.aspx?id=5555"
            println "-"*80
        }
        execResult.assertNormalExitValue()
    }
    
    private boolean TryCheckout(def repoConf, String repoUrl, def destinationDir, String repoBranch) {
        def hgPath = new File(buildScriptDependencies.getPath("Mercurial"), "hg.exe").path
        def cmdLine = [hgPath, "clone"]
        if (repoBranch != null) { 
            cmdLine.add("--branch")
            cmdLine.add(repoBranch)
        }
        cmdLine.add("--")
        cmdLine.add(repoUrl)
        cmdLine.add(destinationDir.path)
        def errorOutput = new ByteArrayOutputStream()
        def execResult = project.exec {
            setStandardOutput new ByteArrayOutputStream()
            setErrorOutput errorOutput
            setIgnoreExitValue true
            commandLine cmdLine
        }
        //println "execResult.getExitValue(): ${execResult.getExitValue()}"
        if (execResult.getExitValue() != 0) {
            println "    ${errorOutput.toString().trim()}"
            return false
        }
        return true
    }
    
    private static void deleteEmptyDir(File dir) {
        if (dir.exists()) {
            if ((dir.list() as List).empty) {
                dir.delete()
            }
        }
    }
    
    @Override
    protected String getCommandName() {
        "Hg Clone"
    }
    
    @Override
    protected boolean DoCheckout(File destinationDir, String repoUrl, String repoRevision, String repoBranch) {
        def repoConf = RepositoryConfiguration.DEFAULT
        def hgrcFile = new File(project.ext.hgConfigFile)
        if (!hgrcFile.exists()) {
            println "Warning: no Mercurial config file at '${hgrcFile.path}'."
        }
        repoConf.setHgrcPath(hgrcFile.path)
        
        boolean result = TryCheckout(repoConf, repoUrl, destinationDir, repoBranch)
        
        if (!result) {
            deleteEmptyDir(destinationDir)
            def myCredentialsExtension = project.extensions.findByName("my")
            if (myCredentialsExtension != null) {
                println "  Authentication failed. Trying credentials from 'my-credentials' plugin..."
                cacheCredentials(myCredentialsExtension.username(), myCredentialsExtension.password(), repoUrl)
                println "  Cached Mercurial credentials. Trying again..."
                result = TryCheckout(repoConf, repoUrl, destinationDir, repoBranch)
                if (!result) {
                    deleteEmptyDir(destinationDir)
                    println "  Well, that didn't work. Your \"Domain Credentials\" are probably out of date."
                    println "  Have you changed your password recently? If so then please try running "
                    println "  'credential-store.exe' which should be in the root of your workspace."
                    throw new RuntimeException("Hg authentication failure.")
                }
            } else {
                println "  Authentication failed. Please apply the 'my-credentials' plugin."
            }                
        }
        
        // Update to a specific revision if necessary.
        if (repoRevision != null) {
            Repository repo = Repository.open(repoConf, destinationDir)
            def updateCommand = new UpdateCommand(repo)
            updateCommand.rev(repoRevision).execute()
            repo.close()
        }
        
        result
    }
}