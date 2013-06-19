package holygradle.artifactory_manager

import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat

class ArtifactInfo {
    public final String path
    private Date creationDate = null
    private Map json
    private ArtifactInfo parent
    private Collection<ArtifactInfo> children = []
    
    public ArtifactInfo(ArtifactoryAPI artifactory, String path) {
        this.path = path
        initialize(artifactory)
    }
        
    public ArtifactInfo(ArtifactInfo parent, ArtifactoryAPI artifactory, String path) {
        this.parent = parent
        this.path = path
        initialize(artifactory)
    }
    
    private void initialize(ArtifactoryAPI artifactory) {
        //println "ArtifactInfo: '$path'"
        json = artifactory.getFolderInfoJson(path)
        for (child in json.children) {
            if (child.folder) {
                children.add(new ArtifactInfo(this, artifactory, path + child.uri))
            }
        }
    }

    public ArtifactInfo getParent() {
        return this.parent
    }

    public String getVersion() {
        if (path ==~ /.*\/([\d\w\.]+\d+)/) {
            path.split("/").last()
        } else {
            null
        }
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
    
    public ArtifactInfo getNewestChild() {
        ArtifactInfo newestChild = null
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
            Collection<ArtifactInfo> newChildren = []
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
}