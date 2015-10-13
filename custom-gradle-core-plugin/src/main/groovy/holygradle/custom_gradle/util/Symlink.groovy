package holygradle.custom_gradle.util

@SuppressWarnings("GroovyUnusedDeclaration")
@Deprecated
public class Symlink {
    public static boolean isJunctionOrSymlink(File file) throws IOException {
        throw new RuntimeException("holygradle.custom_gradle.util.Symlink is deprecated.  Use holygradle.io.Symlink instead.")
    }

    public static void delete(File link) {
        throw new RuntimeException("holygradle.custom_gradle.util.Symlink is deprecated.  Use holygradle.io.Symlink instead.")
    }
    
    public static void rebuild(File link, File target) {
        throw new RuntimeException("holygradle.custom_gradle.util.Symlink is deprecated.  Use holygradle.io.Symlink instead.")
    }
}