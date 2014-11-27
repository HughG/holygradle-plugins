package holygradle.buildHelpers

/**
 * Caching source for XML documents: requesting the same one more than once won't re-parse it.
 */
public class XmlDocumentSource {
    private final BuildContext buildContext
    private final Map<File, XmlDoc> documents = new HashMap()

    XmlDocumentSource(BuildContext buildContext) {
        this.buildContext = buildContext
    }

    public XmlDoc getXmlDocument(File file) {
        if (!documents.containsKey(file)) {
            final XmlParser parser = new XmlParser()
            // Disable loading of DTDs from external URLs.
            parser.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
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
