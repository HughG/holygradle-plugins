package holygradle.artifactory_manager

import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.regex.Matcher

/**
 * Class representing one of the levels in an Ivy-style artifact path: group, module, and version.
 *
 * Instances for groups and modules may have children, and will have a null version value.  Instances for versions will
 * have a non-null version, and won't have children.
 */
class PathInfo {
    public final String path
    private final String version
    private Date creationDate = null
    private Map json
    private PathInfo parent
    private Collection<PathInfo> children = []
    
    public PathInfo(ArtifactoryAPI artifactory, String path) {
        this(null, artifactory, path)
    }
        
    public PathInfo(PathInfo parent, ArtifactoryAPI artifactory, String path) {
        this.parent = parent
        this.path = path
        Matcher splitPath = (path =~ "[^/]+/[^/]+/([^/]+)")
        if (splitPath.matches()) {
            this.version = splitPath.group(1)
        } else {
            this.version = null
        }
        initialize(artifactory)
    }
    
    private void initialize(ArtifactoryAPI artifactory) {
        json = artifactory.getFolderInfoJson(path)
        for (child in json.children) {
            if (child.folder) {
                children.add(new PathInfo(this, artifactory, path + child.uri))
            }
        }
    }

    public PathInfo getParent() {
        return this.parent
    }

    public String getVersion() {
        return this.version
    }
    
    public Date getCreationDate() {
        if (creationDate == null) {
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX")
            try {
                creationDate = dateFormat.parse(json.created as String)
            } catch (ParseException e) {
                println "Failed to parse date '${json.created}': " + e.toString()
                creationDate = new Date()
            }
        }
        return creationDate
    }
    
    public PathInfo getNewestChild() {
        PathInfo newestChild = null
        Date newestChildCreationDate = null
        for (child in children) {
            Date creation = child.getCreationDate()
            if (newestChildCreationDate == null || creation.after(newestChildCreationDate)) {
                newestChild = child
                newestChildCreationDate = creation
            }
        }
        newestChild
    }
    
    public boolean filter(Closure closure) {
        if (children.size() == 0) {
            return closure(this)
        } else {
            Collection<PathInfo> newChildren = []
            for (child in children) {
                if (!child.filter(closure)) {
                    newChildren.add(child)
                } else {
                    //println "filtering: ${child.path}"
                }
            }
            children = newChildren
            return false
        }
    }
    
    public void all(Closure closure) {
        if (children.size() == 0) {
            closure(this)
        } else {
            for (child in children) {
                child.all(closure)
            }
        }
    }

    @Override
    public String toString() {
        int size = children.size()
        "${path} (${size} ${size == 1 ? 'child' : 'children'})"
    }
}