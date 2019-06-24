package holygradle.buildHelpers

import org.xml.sax.EntityResolver
import org.xml.sax.InputSource

/**
 * Caching source for XML documents: requesting the same one more than once won't re-parse it.
 */
public class XmlDocumentSource {
    private final BuildContext buildContext
    private final Map<File, XmlDoc> documents = new HashMap()

    XmlDocumentSource(BuildContext buildContext) {
        this.buildContext = buildContext
    }

    // Private /
    private EntityResolver getEntityResolver() {
        return [
            resolveEntity: { String publicId, String systemId ->
                String dtd = "dtd/" + systemId.split("/").last()
                final entityFile = new File(buildContext.baseDir, dtd)
                buildContext.debug("Loading external entity '${publicId}' / '${systemId}' from ${entityFile}")
                new InputSource(new FileReader(entityFile))
            }
        ] as EntityResolver
    }

    public XmlDoc getXmlDocument(File file) {
        if (!documents.containsKey(file)) {
            final XmlParser parser = new XmlParser()
            // Use a custom entity resolver to get a local copy of the XHTML DTD, to save time and so that we don't
            // have to worry about proxies etc.  This also means we can use HTML entities like "&copy;".
            parser.setEntityResolver(getEntityResolver())
            parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
            parser.trimWhitespace = false
            final XmlDoc doc = new XmlDoc(parser.parse(file))
            if (!doc.valid) {
                buildContext.error("Failed to load XML from '${file}'")
            }
            documents[file] = doc
        }
        return documents[file]
    }
}
