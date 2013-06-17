package holygradle

import groovyx.net.http.HTTPBuilder

import java.util.regex.Matcher

import static groovyx.net.http.Method.GET
import static groovyx.net.http.ContentType.TEXT

class ArtifactoryHelper {
    private final HTTPBuilder http 
    private final String repository
    
    public ArtifactoryHelper(String repositoryUrl) {
        Matcher urlMatch = repositoryUrl =~ /(http\w*[:\/]+[\w\.]+)\/.*\/([\w\-]+)/
        if (urlMatch.size() == 0) {
            throw new RuntimeException("Failed to parse URL for server and repository")
        }
        String server = urlMatch[0][1]
        repository = urlMatch[0][2]
        http = new HTTPBuilder(server + "/")
    }
    
    public ArtifactoryHelper(String repositoryUrl, String username, String password) {
        this(repositoryUrl)
        
        // Would be preferable to do: client.auth.basic username, password
        // but due to http://josephscott.org/archives/2011/06/http-basic-auth-with-httplib2/
        // we have to manually include the authorization header.
        String auth = "${username}:${password}".toString()
        String authEncoded = auth.bytes.encodeBase64().toString()
        http.setHeaders( ['Authorization' : 'Basic ' + authEncoded ] )    
    }
    
    public boolean artifactExists(String group, String module, String version) {
        return artifactExists("$group/$module/$version")
    }
    
    public boolean artifactExists(String artifactPath) {
        boolean exists = false
        http.request( GET, TEXT ) { HTTPBuilder.RequestConfigDelegate req ->
            req.uri.path = "artifactory/${repository}/${artifactPath}"
            req.response.success = { resp, reader ->
                exists = true
            }
            req.response.failure = { }
        }
        exists
    }
}