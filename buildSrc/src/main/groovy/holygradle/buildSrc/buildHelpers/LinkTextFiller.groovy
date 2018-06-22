package holygradle.buildSrc.buildHelpers

import holygradle.buildSrc.groovy.util.*

import java.util.regex.Matcher

/**
 * Replaces the text content of XHTML links in AsciiDoc-derived documents based on the content of the link target, if
 * the link is to the same document or a neighbouring local document.
 */
public class LinkTextFiller {
    class Link {
        public final Node node
        public final String target
        public final String text
        public final URI targetUri
        private boolean gotTargetDoc = false
        private XmlDoc targetDoc

        private Link(Node node, String target, String text, URI targetUri) {
            this.node = node
            this.target = target
            this.text = text
            this.targetUri = targetUri
        }

        public XmlDoc getTargetDoc() {
            if (!gotTargetDoc) {
                final String linkTargetPath = targetUri.path
                if (linkTargetPath == null || linkTargetPath.empty) {
                    targetDoc = doc
                } else {
                    final File targetFile = new File(file.parentFile, linkTargetPath)
                    if (!targetFile.name.endsWith('.html')) {
                        targetDoc = null
                    } else if (!targetFile.exists()) {
                        buildContext.warn("${file}: Skipping non-existent link target file '${targetFile}'")
                        targetDoc = null
                    } else {
                        targetDoc = documentSource.getXmlDocument(targetFile)
                        if (targetDoc != null && !targetDoc.valid) {
                            targetDoc = null
                        }
                    }
                }
                gotTargetDoc = true
            }
            return targetDoc
        }

        @Override
        public String toString() {
            return "Link{" +
                "node=" + node +
                ", -> target='" + target + '\'' +
                ", text='" + text + '\'' +
                '}';
        }
    }

    private final BuildContext buildContext
    private final XmlDocumentSource documentSource
    private final File file
    private XmlDoc doc
    private Map<URI, String> fillText = new HashMap()

    public LinkTextFiller(BuildContext buildContext, XmlDocumentSource documentSource, File file) {
        this.buildContext = buildContext
        this.documentSource = documentSource
        this.file = file
    }

    // This is an entry point, so suppress "unused".
    @SuppressWarnings("GroovyUnusedDeclaration")
    public void fillAllLinkText() {
        if (!doc) {
            doc = documentSource.getXmlDocument(file)
        }
        if (!doc.valid) {
            return
        }
        final List<Node> linkNodes = doc.links
        for (Node linkNode in linkNodes) {
            fillLinkText(linkNode)
        }
    }

    private void fillLinkText(Node linkNode) {
        // Get some info about the link to decide whether or not to fill it in.
        Link link = makeLinkFromNode(linkNode)
        if (link.targetUri == null) {
            // It's a link with an empty href, so don't try to follow it.
            return
        }
        if (link.targetUri.authority != null) {
            // It's a link to another site, so don't try to follow it.
            return
        }
        fillLink(link)
    }

    private Link makeLinkFromNode(Node linkNode) {
        String linkText = null
        final List<Node> children = linkNode.children()
        if (children.size() == 1) {
            Object firstChild = children[0]
            if (firstChild instanceof String) {
                linkText = (String)firstChild
            }
        }
        String linkTarget = linkNode.@href
        URI linkTargetUri = null
        if (linkTarget != null) {
            linkTargetUri = new URI(linkTarget)
        }
        return new Link(linkNode, linkTarget, linkText, linkTargetUri)
    }

    private void fillLink(Link link) {
        if (!shouldFill(link)) {
            return
        }

        String fillText = getFillText(link)
        if (fillText != null) {
            link.node.setValue(fillText.trim())
        }
    }

    private String getFillText(Link link) {
        // We cache the lookup because the same target may be linked to from several places.
        if (!fillText.containsKey(link.targetUri)) {
            fillText[link.targetUri] = makeFillText(link)
        }
        return fillText[link.targetUri]
    }

    private String makeFillText(Link link) {
        XmlDoc targetDoc = link.targetDoc
        if (link.targetDoc == null) {
            return null
        }
        if (!shouldFill(link)) {
            return null
        }

        final String fragment = link.targetUri.fragment
        if (fragment == null) {
            return targetDoc.node.head*.title*.text().join()
        } else {
            Node targetElement = targetDoc.getElementById(fragment)
            if (targetElement == null) {
                buildContext.warn("${file}: Failed to find target for ${fragment}")
                return "MISSING TARGET"
            } else {
                if (targetElement.name().localPart ==~ /h[1-6]/) {
                    // Strip numbers from link target text which comes from a heading.
                    final Matcher headingMatch = (targetElement.text() =~ /([0-9.]+ +)?(.*)/)
                    final List<String> matches = headingMatch[0] as List<String>
                    return matches[2]
                } else if (targetElement.name().localPart == "a") {
                    return targetElement.parent().text()
                } else {
                    return targetElement.text()
                }
            }
        }
    }

    private boolean shouldFill(Link link) {
        // Fill empty links, or ones within the same document which have AsciiDoc default text.
        return (
            (link.text == null) ||
            (link.targetDoc == doc && link.text == "[${link.targetUri.fragment}]") ||
            (link.targetDoc != doc && link.text == "${link.targetUri}")
        )
    }

    public void writeTo(File outputFile) {
        // NOTE: We can't just use XmlUtil.serialize because it turns head/script into an empty tag, instead of
        // start/end, which means Firefox won't load the script.  Some other browsers also refuse to handle empty
        // tags for other elements.  Based on the XHTML 1 spec (https://www.w3.org/TR/xhtml1/#C_3) as linked from
        // <http://stackoverflow.com/a/69984>, we add a complete list of elements which have an empty content
        // model, so we can expand empty tags to a stard/end pair for all other elements.  Also, the default
        // XmlNodePrinter adds extra whitespace, which the custom class avoids.
        final XmlNodePrinter printer = new XhtmlNodePrinter(new PrintWriter(outputFile, "UTF-8"), "")
        printer.expandEmptyElements = true
        printer.keepEmptyElementsSet.addAll([
            "area",
            "base",
            "basefont",
            "br",
            "col",
            "img",
            "input",
            "isindex",
            "link",
            "meta",
            "param",
        ])
        printer.preserveWhitespace = true // to avoid extra line breaks
        printer.print(doc.node)
    }
}
