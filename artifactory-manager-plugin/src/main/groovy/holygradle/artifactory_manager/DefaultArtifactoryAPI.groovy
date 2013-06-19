package holygradle.artifactory_manager

import groovy.text.SimpleTemplateEngine
import groovyx.net.http.HttpResponseDecorator
import groovyx.net.http.ParserRegistry
import groovyx.net.http.RESTClient

class DefaultArtifactoryAPI implements ArtifactoryAPI {
    private RESTClient client 
    private String repository
    private SimpleTemplateEngine engine = new SimpleTemplateEngine()
    private boolean dryRun = true
    
    public DefaultArtifactoryAPI(String server, String repository, String username, String password, boolean dryRun) {
        this.repository = repository
        this.dryRun = dryRun
        
        client = new RESTClient(server)
        client.parser['application/vnd.org.jfrog.artifactory.storage.FolderInfo+json'] = client.parser['application/json']
        client.parser['application/vnd.org.jfrog.artifactory.repositories.RepositoryDetailsList+json'] = client.parser['application/json']
        
        // Would be preferable to do: client.auth.basic username, password
        // but due to http://josephscott.org/archives/2011/06/http-basic-auth-with-httplib2/
        // we have to manually include the authorization header.
        String auth = "${username}:${password}".toString()
        String authEncoded = auth.bytes.encodeBase64().toString()
        client.setHeaders( ['Authorization' : 'Basic ' + authEncoded ] )

        ParserRegistry.setDefaultCharset(null)
    }
    
    public String getRepository() {
        repository
    }
    
    public Date getNow() {
        new Date()
    }
    
    public Map getFolderInfoJson(String path) {
        Map binding = [repository: repository, path: path]
        Object template = engine.createTemplate('''/artifactory/api/storage/$repository/$path''').make(binding)
        String query = template.toString()
        HttpResponseDecorator resp = (HttpResponseDecorator)(client.get(path: query))
        if (resp.status != 200) {
            println "ERROR: problem obtaining folder info: " + resp.status
            println query
            System.exit(-1)
        }
        return resp.data as Map
    }
    
    public void removeItem(String path) {
        Map binding = [repository: repository, path: path]
        Object template = engine.createTemplate('''/artifactory/$repository/$path''').make(binding)
        String query = template.toString()
        if (!dryRun) {
            client.delete(path: query)
        }
    }
}