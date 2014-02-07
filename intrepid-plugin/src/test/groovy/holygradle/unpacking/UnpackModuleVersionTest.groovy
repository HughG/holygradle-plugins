package holygradle.unpacking

import holygradle.Helper
import holygradle.test.*
import org.junit.Test
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.testfixtures.ProjectBuilder
import java.util.Collections
import static org.junit.Assert.*

import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import holygradle.dependencies.PackedDependencyHandler

class UnpackModuleVersionTest extends AbstractHolyGradleTest {

    //
    // Build the hierarchy of modules (and their expected file-system locations) described by the test Ivy files.
    //
    // 'root' and 'date' are packedDependencies (top-level), but 'date' is also a transitive dependency of 'coconut'
    //
    // root (org:root:1.0) [apricot: "aa", blueberry: "sub/bb", coconut: "sub/"]
    // +---aa (org:apricot:1.1) [eggfruit: "../"]
    // +---sub
    // |   +---bb (org:blueberry:1.2)
    // |   +---coconut (org:coconut:1.3) [date: ""]
    // |       +---date (org:date:1.4)
    // +---eggfruit (org:eggfruit:1.5)
    // date (org:date:1.4)
    //
    private Map<String, UnpackModuleVersion> getTestModules() {
        Map<String, UnpackModuleVersion> modules = [:]
        modules["root"] = getUnpackModuleVersion("root", "1.0")
        modules["date"] = getUnpackModuleVersion("date", "1.4")
        modules["apricot"] = getUnpackModuleVersion("apricot", "1.1", modules["root"])
        modules["blueberry"] = getUnpackModuleVersion("blueberry", "1.2", modules["root"])
        modules["coconut"] = getUnpackModuleVersion("coconut", "1.3", modules["root"])
        modules["date"].addParent(modules["coconut"])
        modules["eggfruit"] = getUnpackModuleVersion("eggfruit", "1.5", modules["apricot"])
        return modules
    }
        
    private File getIvyFile(String fileName) {
        return new File(getTestDir(), fileName)
    }

    private UnpackModuleVersion getUnpackModuleVersion(String moduleName, String moduleVersion, UnpackModuleVersion parent=null) {
        UnpackModuleVersion result = new UnpackModuleVersion(
            new DefaultModuleVersionIdentifier("org", moduleName, moduleVersion),
            getIvyFile(moduleName + ".xml"),
            (parent == null) ? new PackedDependencyHandler(moduleName) : null
        )
        if (parent != null) {
            result.addParent(parent)
        }
        return result
    }
    
    private Project getProject() {
        Project project = ProjectBuilder.builder().build()
        project.ext.unpackedDependenciesCache = new File("theUnpackCache")
        project.ext.buildScriptDependencies = new DummyBuildScriptDependencies(project)
        project
    }
    
    @Test
    public void testStandAloneModule() {
        Project project = getProject()
        UnpackModuleVersion apricot = getUnpackModuleVersion("apricot", "1.1")

        assertEquals("org:apricot:1.1", apricot.getFullCoordinate())
        assertNotNull("getPackedDependency not null", apricot.getPackedDependency())
        assertEquals("apricot", apricot.getPackedDependency().name)
        assertTrue("getParents is empty", apricot.getParents().isEmpty())
                
        Set<Task> unpackTasks = apricot.getUnpackTasks(project)
        assertEquals(1, unpackTasks.size())
        Task unpackTask = unpackTasks.find() as Task // gets first item
        assertTrue(unpackTask.name.contains("extract"))
        assertTrue(unpackTask.name.contains("Apricot"))
        assertEquals(new File(project.unpackedDependenciesCache as File, "org/apricot-1.1"), unpackTask.unpackDir)
        assertEquals("Unpacks dependency 'apricot' (version 1.1) to the cache.", unpackTask.description)
        
        Collection<Task> symlinkTasks = apricot.collectParentSymlinkTasks(project)
        assertEquals(0, symlinkTasks.size())

        symlinkTasks = apricot.getSymlinkTasksIfUnpackingToCache(project)
        assertEquals(1, symlinkTasks.size())
        Task symlinkTask = symlinkTasks.find() as Task
        print "symlinkTask = ${symlinkTask}"
        assertTrue(symlinkTask.name.contains("symlink"))
        assertTrue(symlinkTask.name.contains("Apricot"))

        assertEquals("apricot", apricot.getTargetDirName())
        assertEquals(1, apricot.getTargetPathsInWorkspace(project).size() )
        assertEquals(new File(project.projectDir, "apricot"), apricot.getTargetPathsInWorkspace(project).iterator().next())
    }

    @Test
    public void testSingleModuleApplyUpToDateChecks() {
        Project project = getProject()
        UnpackModuleVersion apricot = getUnpackModuleVersion("apricot", "1.1")
    
        PackedDependencyHandler packedDep = apricot.getPackedDependency()
        packedDep.applyUpToDateChecks = true
        
        Set<Task> unpackTasks = apricot.getUnpackTasks(project)
        assertTrue("Uses to UnpackTask when applyUpToDateChecks=true", null != unpackTasks.find({ it instanceof UnpackTask }))
    }
    
    @Test
    public void testSingleModuleIncludeVersionNumberInPath() {
        PackedDependencyHandler eggfruitPackedDep = new PackedDependencyHandler("../bowl/eggfruit-<version>-tasty")
        UnpackModuleVersion eggfruit = new UnpackModuleVersion(
            new DefaultModuleVersionIdentifier("org", "eggfruit", "1.5"),
            getIvyFile("eggfruit.xml"),
            eggfruitPackedDep
        )
        
        assertEquals("eggfruit-1.5-tasty", eggfruit.getTargetDirName())
    }
    
    @Test
    public void testSingleModuleNotUnpackToCache() {
        Project project = getProject()
        UnpackModuleVersion coconut = getUnpackModuleVersion("coconut", "1.3")
        
        PackedDependencyHandler coconutPackedDep = coconut.getPackedDependency()
        coconutPackedDep.unpackToCache = false
        
        File targetPath = new File(project.projectDir, "coconut")
        assertEquals(1, coconut.getTargetPathsInWorkspace(project).size())
        assertEquals(targetPath, coconut.getTargetPathsInWorkspace(project).iterator().next())
        assertEquals(new File("coconut").getCanonicalFile(), coconut.getTargetPathsInWorkspace(null).iterator().next())
        
        Set<Task> unpackTasks = coconut.getUnpackTasks(project)
        Unpack unpack = unpackTasks.find() as Unpack

        assertEquals(targetPath, unpack.unpackDir)
        String expectedMessage = "Unpacks dependency 'coconut' to [${targetPath.getCanonicalFile()}]."
        assertEquals(expectedMessage, unpack.description)
    }
    
    @Test
    public void testOneChild() {
        Project project = getProject()
        UnpackModuleVersion coconut = getUnpackModuleVersion("coconut", "1.3")
        UnpackModuleVersion date = getUnpackModuleVersion("date", "1.4", coconut)
        
        assertTrue("getParents not empty", !date.getParents().isEmpty())
        assertTrue("coconut is parent of date", date.getParents().contains(coconut))

        Collection<Task> symlinkTasks = date.collectParentSymlinkTasks(project)
        assertEquals(1, symlinkTasks.size())

        Task theTask = symlinkTasks.find() as Task

        assertTrue(theTask.name.contains("Coconut") && theTask.name.contains("symlink"))
        assertTrue(!(theTask.name.contains("date")))
                
        File targetPath = new File(project.projectDir, "coconut")
        assertEquals(1, coconut.getTargetPathsInWorkspace(project).size())
        assertEquals(targetPath, coconut.getTargetPathsInWorkspace(project).iterator().next())
    }
    
    @Test
    public void testRelativePaths() {
        Project project = getProject()
        Map<String, UnpackModuleVersion> modules = getTestModules()

        assertEquals(1, modules["apricot"].getTargetPathsInWorkspace(project).size())
        assertEquals(new File(project.projectDir, "root/aa"), modules["apricot"].getTargetPathsInWorkspace(project).iterator().next())

        assertEquals(1, modules["blueberry"].getTargetPathsInWorkspace(project).size())
        assertEquals(new File(project.projectDir, "root/sub/bb"), modules["blueberry"].getTargetPathsInWorkspace(project).iterator().next())
        
        assertEquals(1, modules["coconut"].getTargetPathsInWorkspace(project).size())
        assertEquals(new File(project.projectDir, "root/sub/coconut"), modules["coconut"].getTargetPathsInWorkspace(project).iterator().next())

        println "testrelativepaths:"
        Set<File> targetPaths = modules["date"].getTargetPathsInWorkspace(project)
        assertEquals(2, targetPaths.size())
        assertTrue( targetPaths.find { it == new File(project.projectDir, "root/sub/coconut/date") } != null )
        assertTrue( targetPaths.find { it == new File(project.projectDir, "date") } != null )
        
        File eggfruitPath = modules["eggfruit"].getTargetPathsInWorkspace(project).iterator().next()
        
        assertEquals(new File(project.projectDir, "root/aa/../eggfruit"), eggfruitPath)
        assertEquals(new File(project.projectDir, "root/eggfruit"), eggfruitPath.getCanonicalFile())
    }

    @Test
    public void testUnpackingModuleToBothCacheAndWorkspaceFails() {

        println "testUnpackingModuleToBothCacheAndWorkspaceFails:"

        // 'date' is both a top-level dependency and a transitive dependency of grand-parent 'root'
        Map<String, UnpackModuleVersion> modules = getTestModules()
        modules["root"].getPackedDependency().unpackToCache(true)
        modules["date"].getPackedDependency().unpackToCache(false)

        assertTrue(modules["root"].shouldUnpackToCache())

        try {
            // This should throw
            boolean dummy = modules["date"].shouldUnpackToCache()
            assertTrue("Should not get here", false)
        }  catch  (RuntimeException e) {
            assertTrue(e.getMessage().contains("Inconsistent unpack policy (shouldUnpackToCache) specified for module org:date:1.4"))
        }
    }
}