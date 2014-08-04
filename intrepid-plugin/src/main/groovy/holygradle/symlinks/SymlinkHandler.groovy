package holygradle.symlinks

import org.gradle.api.*
import org.gradle.util.ConfigureUtil

class SymlinkHandler {
    class Mapping {
        final String linkPath
        final String targetPath

        Mapping(String linkPath, String targetPath) {
            this.linkPath = linkPath
            this.targetPath = targetPath
        }
    }

    private String fromLocation = "."
    private Collection<String> toLocations = []
    private Collection<SymlinkHandler> children = []
    
    public static SymlinkHandler createExtension(Project project) {
        project.extensions.create("symlinks", SymlinkHandler)
    }
    
    SymlinkHandler() { }
    
    SymlinkHandler(String fromLocation) {
        this.fromLocation = fromLocation
    }
    
    SymlinkHandler(SymlinkHandler that) {
        this.fromLocation = that.getFromLocation()
        this.toLocations = that.getToLocations().clone()
        
        that.getChildHandlers().each { child ->
            this.children.add(new SymlinkHandler(child))
        }
    }
    
    public void addFrom(String fromLocation, SymlinkHandler handler) {
        SymlinkHandler fromHandler = from(fromLocation)
        for (toLocation in handler.getToLocations()) {
            fromHandler.to(toLocation)
        }
    }
    
    public SymlinkHandler from(String location) {
        String childLocation = location
        if (fromLocation != ".") {
            childLocation = fromLocation + "/" + location
        }
        SymlinkHandler child = new SymlinkHandler(childLocation)
        children.add(child)
        child
    }
    
    public SymlinkHandler from(String location, Closure c) {
        SymlinkHandler handler = from(location)
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
    
    public Iterable<String> getToLocations() {
        toLocations
    }
    
    public Iterable<String> getChildHandlers() {
        children
    }
    
    public Collection<Mapping> getMappings() {
        Collection<Mapping> mappings = []
        collectMappings(mappings)
        mappings
    }
    
    private int countToLocations() {
        (int)children.sum(toLocations.size()) { SymlinkHandler it -> it.countToLocations() }
    }
    
    public void writeScript(StringBuilder str) {
        if (countToLocations() > 0) {
            str.append("symlinks {\n")
            writeScriptIndented(str, 4)
            str.append("}\n")
            str.append("\n")
        }
    }    
    
    private void writeScriptIndented(StringBuilder str, int indent) {
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
                it.writeScriptIndented(str, toIndent)
            }
            if (fromLocation != ".") {
                str.append(" "*indent)
                str.append("}\n")
            }
        }
    }
        
    private String getLinkPath(String toLocation) {
        String last = toLocation.split("/")[-1]
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
    
    private void collectMappings(Collection<Mapping> mappings) {
        for (toLocation in toLocations) {
            mappings.add(new Mapping(getLinkPath(toLocation), getTargetPath(toLocation)))
        }
        for (child in children) {
            child.collectMappings(mappings)
        }
    }
}
