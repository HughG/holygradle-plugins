package holygradle.artifactorymanager

import java.text.SimpleDateFormat

class ArtifactInfo {
    public final String path
    private Date creationDate = null
    public def json
    public ArtifactInfo parent
    public def children = []
    
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
    
    public String getVersion() {
        if (path ==~ /.*\/([\d\w\.]+\d+)/) {
            path.split("/")[-1]
        } else {
            null
        }
    }
    
    public Date getCreationDate() {
        if (creationDate == null) {
            def dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX")
            try {
                creationDate = dateFormat.parse(json.created)
            } catch (ParseException) {
                println "Failed to parse date '${json.created}'."
                creationDate = new Date()
            }
        }
        return creationDate
    }
    
    public ArtifactInfo getNewestChild() {
        ArtifactInfo newestChild = null
        Date newestChildCreationDate = null
        for (child in children) {
            def creation = child.getCreationDate()
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
            def newChildren = []
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