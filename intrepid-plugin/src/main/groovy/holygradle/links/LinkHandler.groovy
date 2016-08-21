package holygradle.links

import org.gradle.api.*
import org.gradle.util.ConfigureUtil

class LinkHandler {
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
    private Collection<LinkHandler> children = []
    
    public static LinkHandler createExtension(Project project) {
        return project.extensions.create("links", LinkHandler)
    }

    LinkHandler() { }

    LinkHandler(String fromLocation) {
        this.fromLocation = fromLocation
    }

    LinkHandler(LinkHandler that) {
        this.fromLocation = that.getFromLocation()
        this.merge(that)
    }

    // Copy the "to locations" and "child handlers", but not the "fromLocation", from other to this
    private void merge(LinkHandler other) {
        to(other.getToLocations())
        other.childHandlers.each { child ->
            children.add(new LinkHandler(child))
        }
    }

    public void addFrom(String fromLocation, LinkHandler handler) {
        LinkHandler fromHandler = from(fromLocation)
        fromHandler.merge(handler)
    }
    
    public LinkHandler from(String location) {
        String childLocation = location
        if (fromLocation != ".") {
            childLocation = fromLocation + "/" + location
        }
        LinkHandler child = new LinkHandler(childLocation)
        children.add(child)
        child
    }
    
    public LinkHandler from(String location, Closure c) {
        LinkHandler handler = from(location)
        if (c != null) {
            ConfigureUtil.configure(c, handler)
        }
        handler
    }
    
    public void to(String... locations) {
        to(locations.toList())
    }

    public void to(Iterable<String> locations) {
        for (location in locations) {
            toLocations.add(location)
        }
    }

    public String getFromLocation() {
        fromLocation
    }
    
    public Collection<String> getToLocations() {
        toLocations
    }
    
    public Collection<LinkHandler> getChildHandlers() {
        children
    }
    
    public Collection<Mapping> getMappings() {
        Collection<Mapping> mappings = []
        collectMappings(mappings)
        mappings
    }
    
    private int countToLocations() {
        (int)children.sum(toLocations.size()) { LinkHandler it -> it.countToLocations() }
    }
    
    public void writeScript(StringBuilder str) {
        if (countToLocations() > 0) {
            str.append("links {\n")
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
