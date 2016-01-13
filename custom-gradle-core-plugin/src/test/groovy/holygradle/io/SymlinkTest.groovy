package holygradle.io

class SymlinkTest extends LinkTestBase
{
    @Override
    protected void makeLinkExternally(File link, File target) {
        Process p = "cmd /c mklink /d ${link.canonicalFile} ${target}".execute([], testDir)
        p.consumeProcessOutput()
        p.waitFor()
    }

    @Override
    protected boolean isLink(File link) {
        Symlink.isSymlink(link)
    }

    @Override
    protected void rebuild(File link, File target) {
        Symlink.rebuild(link, target)
    }

    @Override
    protected void delete(File link) {
        Symlink.delete(link)
    }
}
