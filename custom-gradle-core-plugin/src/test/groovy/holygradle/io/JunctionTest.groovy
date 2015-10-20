package holygradle.io

class JunctionTest extends LinkTestBase
{
    @Override
    protected void makeLinkExternally(File link, File target) {
        Process p = "mklink /j ${link.canonicalFile} ${target}".execute([], testDir)
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
}
