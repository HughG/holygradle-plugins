package holygradle

import org.gradle.api.*
import org.gradle.api.artifacts.repositories.AuthenticationSupported
import org.gradle.util.ConfigureUtil

class RepublishHandler {
    private def replacements = [:]
    private String fromRepo = null
    private String toRepo = null
    
    public static RepublishHandler createExtension(Project project) {
        project.extensions.create("republish", RepublishHandler)
    }
    
    RepublishHandler() { }
    
    public void replace(String find, String replace) {
        replacements[find] = replace
    }
    
    public void from(String fromUrl) {
        fromRepo = fromUrl
    }
    
    public void to(String toUrl) {
        toRepo = toUrl
    }
    
    public def getReplacements() {
        def repl = replacements.clone()
        if (fromRepo != null && toRepo != null) {
            repl[fromRepo] = toRepo
        }
        repl
    }
    
    public String getFromRepository() {
        fromRepo
    }
    
    public String getToRepository() {
        toRepo
    }
    
    public void writeScript(StringBuilder str, int indent) {
        str.append(" "*indent)
        str.append('republish {\n')
        if (fromRepo != null) {
            str.append(" "*(indent+4))
            str.append('from "')
            str.append(fromRepo)
            str.append('"\n')
        }
        if (toRepo != null) {
            str.append(" "*(indent+4))
            str.append('to "')
            str.append(toRepo)
            str.append('"\n')
        }
        replacements.each { replFrom, replTo ->
            str.append(" "*(indent+4))
            str.append('replace "')
            str.append(replFrom)
            str.append('", "')
            str.append(replTo)
            str.append('"\n')
        }
        str.append(" "*indent)
        str.append('}\n')
    }
}