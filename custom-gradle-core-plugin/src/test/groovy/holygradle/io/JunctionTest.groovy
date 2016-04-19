package holygradle.io

class JunctionTest extends LinkTestBase
{
    @Override
    protected void makeLinkExternally(File link, File target) {
        Process p = "cmd /c mklink /J ${link.canonicalPath} ${target.canonicalPath}".execute([], testDir)
        p.consumeProcessOutput()
        p.waitFor()
    }

    @Override
    protected boolean isLink(File link) {
        Junction.isJunction(link)
    }

    @Override
    protected void rebuild(File link, File target) {
        Junction.rebuild(link, target)
    }

    @Override
    protected void delete(File link) {
        Junction.delete(link)
    }

    @Override
    protected File getTarget(File link) {
        Junction.getTarget(link)
    }
}
