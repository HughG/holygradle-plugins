package holygradle.buildHelpers

import holygradle.groovy.util.*

import java.util.regex.Matcher

/**
 * Replaces the text content of XHTML links in AsciiDoc-derived documents based on the content of the link target, if
 * the link is to the same document or a neighbouring local document.
 */
public class LinkTextFiller {
    static class Link {
        public final Node node
        public final String target
        public final String text
        public final URI targetUri

        private Link(Node node, String target, String text, URI targetUri) {
            this.node = node
            this.target = target
            this.text = text
            this.targetUri = targetUri
        }

        public static Link fromNode(Node linkNode) {
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
        /*
            getXmlDocument from documentSource
            for each link
                if it has empty text
                    fillLinkText
         */
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
        /*
            get path and fragment parts of link target
            if path is empty
                fillLinkFromCurrentDocument
            ekse
                fillLinkFromOtherDocument
         */
        // Get some info about the link to decide whether or not to fill it in.
        Link link = Link.fromNode(linkNode)
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

    private void fillLink(Link link) {
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
        XmlDoc targetDoc = getTargetDoc(link)
        if (!targetDoc?.valid) {
            return null
        }
        if (!shouldFill(link, targetDoc)) {
            return null
        }

        final String fragment = link.targetUri.fragment
        if (fragment == null) {
            return targetDoc.node.head*.title*.text().join()
        } else {
            Node targetElement = targetDoc.getElementById(fragment)
            if (targetElement == null) {
                buildContext.warn("Failed to find target for ${fragment}")
                return null
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

    private XmlDoc getTargetDoc(Link link) {
        if (link.targetUri.path?.empty) {
            return doc
        } else {
            final File targetFile = new File(file.parentFile, link.targetUri.path)
            if (!targetFile.exists()) {
                buildContext.warn("Skipping non-existent link target file '${targetFile}'")
                return null
            }
            XmlDoc targetDoc = documentSource.getXmlDocument(targetFile)
            if (!targetDoc?.valid) {
                return null
            }
            return targetDoc
        }
    }

    private boolean shouldFill(Link link, XmlDoc targetDoc) {
        // Fill empty links, or ones within the same document which have AsciiDoc default text.
        return (
            (link.text == null) ||
            (targetDoc == doc && link.text == "[${link.targetUri.fragment}]")
        )
    }

    public void writeTo(File outputFile) {
        // NOTE: We can't just use XmlUtil.serialize because it turns head/script into an empty tag, instead of
        // start/end, which means Firefox won't load the script.  The default XmlNodePrinter adds extra whitespace, so
        // I made a custom version.
        final XmlNodePrinter printer = new XhtmlNodePrinter(new PrintWriter(outputFile, "UTF-8"), "")
        printer.expandEmptyElements = true // for Firefox
        printer.preserveWhitespace = true // to avoid extra line breaks
        printer.print(doc.node)
    }
}
