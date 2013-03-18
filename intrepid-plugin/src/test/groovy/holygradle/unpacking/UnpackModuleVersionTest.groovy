package holygradle

import org.junit.Test
import org.gradle.api.Project
import org.gradle.api.DefaultTask 
import org.gradle.api.Task
import org.gradle.testfixtures.ProjectBuilder
import static org.junit.Assert.*

import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier

class MockBuildScriptDependencies {
    private final Project project
   
    public MockBuildScriptDependencies(Project project) {
        this.project = project
    }
    
    public Task getUnpackTask(String dependencyName) {
        return project.task(dependencyName, type: DefaultTask)
    }
}

class UnpackModuleVersionTest extends TestBase {
    // The hierarchy of modules described by the test Ivy files is:
    // root (org:root:1.0) [apricot: "aa", blueberry: "sub/bb", coconut: "sub/"]
    // +---aa (org:apricot:1.1) [eggfruit: "../"]
    // +---sub
    // ¦   +---bb (org:blueberry:1.2)
    // ¦   +---coconut (org:coconut:1.3) [date: ""]
    // ¦       +---date (org:date:1.4)
    // +---eggfruit (org:eggfruit:1.5)
    private def getTestModules() {
        def modules = [:]
        modules["root"] = getUnpackModuleVersion("root", "1.0")
        modules["apricot"] = getUnpackModuleVersion("apricot", "1.1", modules["root"])
        modules["blueberry"] = getUnpackModuleVersion("blueberry", "1.2", modules["root"])
        modules["coconut"] = getUnpackModuleVersion("coconut", "1.3", modules["root"])
        modules["date"] = getUnpackModuleVersion("date", "1.4", modules["coconut"])
        modules["eggfruit"] = getUnpackModuleVersion("eggfruit", "1.5", modules["apricot"])
        modules
    }
        
    private File getIvyFile(String fileName) {
        return new File(getTestDir(), fileName)
    }

    private UnpackModuleVersion getUnpackModuleVersion(String moduleName, String moduleVersion, UnpackModuleVersion parent=null) {
        new UnpackModuleVersion(
            new DefaultModuleVersionIdentifier("org", moduleName, moduleVersion),
            getIvyFile(moduleName + ".xml"),
            parent,
            (parent == null) ? new PackedDependencyHandler(moduleName) : null
        )
    }
    
    private Project getProject() {
        Project project = ProjectBuilder.builder().build()
        project.ext.unpackedDependenciesCache = new File("theUnpackCache")
        project.ext.buildScriptDependencies = new MockBuildScriptDependencies(project)
        project
    }
    
    @Test
    public void testStandAloneModule() {
        Project project = getProject()
        def apricot = getUnpackModuleVersion("apricot", "1.1")
        def date = getUnpackModuleVersion("date", "1.4")
    
        assertEquals("org:apricot:1.1", apricot.getFullCoordinate())
        assertNotNull("getPackedDependency not null", apricot.getPackedDependency())
        assertEquals("apricot", apricot.getPackedDependency().name)
        assertNotNull("getParentPackedDependency not null", apricot.getParentPackedDependency())
        assertEquals("apricot", apricot.getParentPackedDependency().name)
        assertNull("getParent is null", apricot.getParent())
                
        def unpackTask = apricot.getUnpackTask(project)
        assertEquals("extractApricot1.1", unpackTask.name)
        assertTrue("Defaults to SpeedyUnpackTask", unpackTask instanceof SpeedyUnpackTask)
        assertEquals(new File(project.ext.unpackedDependenciesCache, "org/apricot-1.1"), unpackTask.unpackDir)
        assertEquals("Unpacks dependency 'apricot' (version 1.1) to the cache.", unpackTask.description)
        
        def symlinkTasks = apricot.collectParentSymlinkTasks(project)
        assertEquals(1, symlinkTasks.size())
        def symlinkTask = symlinkTasks[0]
        assertEquals("symlinkApricot1.1", symlinkTask.name)
        
        assertEquals("apricot", apricot.getTargetDirName())
        assertEquals(new File(project.projectDir, "apricot"), apricot.getTargetPathInWorkspace(project))
    }
    
    @Test
    public void testSingleModuleApplyUpToDateChecks() {
        Project project = getProject()
        def apricot = getUnpackModuleVersion("apricot", "1.1")
    
        def packedDep = apricot.getPackedDependency()
        packedDep.applyUpToDateChecks = true
        
        def unpackTask = apricot.getUnpackTask(project)
        assertTrue("Uses to UnpackTask when applyUpToDateChecks=true", unpackTask instanceof UnpackTask)
    }
    
    @Test
    public void testSingleModuleIncludeVersionNumberInPath() {
        def eggfruitPackedDep = new PackedDependencyHandler("../bowl/eggfruit-<version>-tasty")
        def eggfruit = new UnpackModuleVersion(
            new DefaultModuleVersionIdentifier("org", "eggfruit", "1.5"),
            getIvyFile("eggfruit.xml"),
            null,
            eggfruitPackedDep
        )
        
        assertEquals("eggfruit-1.5-tasty", eggfruit.getTargetDirName())
    }
    
    @Test
    public void testSingleModuleNotUnpackToCache() {
        Project project = getProject()
        def coconut = getUnpackModuleVersion("coconut", "1.3")
        
        def coconutPackedDep = coconut.getPackedDependency()
        coconutPackedDep.unpackToCache = false
        
        def targetPath = new File(project.projectDir, "coconut")
        assertEquals(targetPath, coconut.getTargetPathInWorkspace(project))
        assertEquals(new File("coconut"), coconut.getTargetPathInWorkspace(null))
        
        def unpackTask = coconut.getUnpackTask(project)
        assertEquals(targetPath, unpackTask.unpackDir)
        assertEquals("Unpacks dependency 'coconut' to coconut.", unpackTask.description)
    }
    
    @Test
    public void testOneChild() {
        Project project = getProject()
        def coconut = getUnpackModuleVersion("coconut", "1.3")
        def date = getUnpackModuleVersion("date", "1.4", coconut)
        
        assertNotNull("getParent not null", date.getParent())
        assertEquals(coconut, date.getParent())
        
        def symlinkTasks = date.collectParentSymlinkTasks(project)
        assertEquals(2, symlinkTasks.size())
        assertEquals("symlinkCoconut1.3", symlinkTasks[0].name)
        assertEquals("symlinkDate1.4", symlinkTasks[1].name)
        
        assertEquals(coconut.getPackedDependency(), date.getParentPackedDependency())
        
        def targetPath = new File(project.projectDir, "coconut")
        assertEquals(targetPath, coconut.getTargetPathInWorkspace(project))
        assertEquals(new File("coconut"), coconut.getTargetPathInWorkspace(null))
    }
    
    @Test
    public void testRelativePaths() {
        Project project = getProject()
        def modules = getTestModules()
        
        assertEquals(new File(project.projectDir, "root/aa"), modules["apricot"].getTargetPathInWorkspace(project))
        assertEquals(new File(project.projectDir, "root/sub/bb"), modules["blueberry"].getTargetPathInWorkspace(project))
        assertEquals(new File(project.projectDir, "root/sub/coconut"), modules["coconut"].getTargetPathInWorkspace(project))
        assertEquals(new File(project.projectDir, "root/sub/coconut/date"), modules["date"].getTargetPathInWorkspace(project))
        
        def eggfruitPath = modules["eggfruit"].getTargetPathInWorkspace(project)
        assertEquals(new File(project.projectDir, "root/aa/../eggfruit"), eggfruitPath)
        assertEquals(new File(project.projectDir, "root/eggfruit"), eggfruitPath.getCanonicalFile())
    }
}