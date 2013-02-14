package holygradle

import org.gradle.api.*
import org.gradle.util.ConfigureUtil

class SymlinkHandler {
    private String fromLocation = "."
    private def toLocations = []
    private def children = []
    
    public static SymlinkHandler createExtension(Project project) {
        project.extensions.create("symlinks", SymlinkHandler)
    }
    
    SymlinkHandler() { }
    
    SymlinkHandler(String fromLocation) {
        this.fromLocation = fromLocation
    }
    
    public void addFrom(String fromLocation, SymlinkHandler handler) {
        def fromHandler = from(fromLocation)
        for (toLocation in handler.getToLocations()) {
            fromHandler.to(toLocation)
        }
    }
    
    public SymlinkHandler from(String location) {
        def childLocation = location
        if (fromLocation != ".") {
            childLocation = fromLocation + "/" + location
        }
        def child = new SymlinkHandler(childLocation)
        children.add(child)
        child
    }
    
    public SymlinkHandler from(String location, Closure c) {
        def handler = from(location)
        if (c != null) {
            ConfigureUtil.configure(c, handler)
        }
        handler
    }
    
    public void to(String... locations) {
        for (location in locations) {
            toLocations.add(location)
        }
    }
    
    public String getFromLocation() {
        fromLocation
    }
    
    public def getToLocations() {
        toLocations
    }
    
    public def getChildHandlers() {
        children
    }
    
    public def getMappings() {
        def mappings = []
        getMappings(mappings)
        mappings
    }
    
    public void writeScript(StringBuilder str) {
        if (toLocations.size() > 0 || children.size() > 0) {
            str.append("symlinks {\n")
            writeScript(str, 4)
            str.append("}\n")
            str.append("\n")
        }
    }    
    
    private void writeScript(StringBuilder str, int indent) {
        int toIndent = indent
        if (toLocations.size() > 0 || children.size() > 0) {
            if (fromLocation != ".") {
                str.append(" "*indent)
                str.append("from(\"")
                str.append(fromLocation)
                str.append("\") {\n")
                toIndent = indent + 4
            }
            if (toLocations.size() > 0) {
                str.append(" "*toIndent)
                str.append("to ")
                str.append(toLocations.collect { "\"${it}\"" }.join(", "))
                str.append("\n")
            }
            children.each {
                it.writeScript(str, toIndent)
            }
            if (fromLocation != ".") {
                str.append(" "*indent)
                str.append("}\n")
            }
        }
    }
        
    private String getLinkPath(String toLocation) {
        def last = toLocation.split("/")[-1]
        if (fromLocation == ".") {
            last
        } else {
            fromLocation + "/" + last
        }
    }
    
    private String getTargetPath(String toLocation) {
        if (fromLocation == ".") {
            toLocation
        } else {
            fromLocation + "/" + toLocation
        }
    }
    
    private void getMappings(def mappings) {
        for (toLocation in toLocations) {
            mappings.add([getLinkPath(toLocation), getTargetPath(toLocation)])
        }
        for (child in children) {
            child.getMappings(mappings)
        }
    }
}
