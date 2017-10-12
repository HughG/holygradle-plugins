package holygradle.devenv

import org.gradle.api.Project
import org.junit.Test
import org.gradle.testfixtures.ProjectBuilder
import static org.junit.Assert.*

class DevEnvHandlerTest {
    private final File testInputDir = new File("src/test/test_input")
 
    private Project getProject(String testProjectDir) {
        ProjectBuilder.builder().withName("test").withProjectDir(new File(testInputDir, testProjectDir)).build()
    }
    
    @Test
    public void testSolutionFile() {
        Project project = getProject("single_solution_file")
        DevEnvHandler devenv = new DevEnvHandler(project, null)
         
        assertEquals(null, devenv.getVsSolutionFile())
        
        devenv.solutionFile "blah.sln"
        
        assertEquals(new File(project.projectDir, "blah.sln"), devenv.getVsSolutionFile())
    }
    
}