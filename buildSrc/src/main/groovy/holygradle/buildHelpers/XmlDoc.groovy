package holygradle.buildHelpers

/**
 * Simple class representing what we need to know about an XML document.
 */
public class XmlDoc {
    private final Node node
    private List<Node> links
    private Map<String, Node> elementsById

    public XmlDoc(Node node) {
        this.node = node
    }

    public boolean isValid() {
        return node != null
    }

    private void assertValid() {
        if (!valid) {
            throw new RuntimeException("This XmlDoc is not valid")
        }
    }

    public Node getNode() {
        assertValid()
        return node
    }

    public List<Node> getLinks() {
        assertValid()
        if (!links) {
            links = node.depthFirst().findAll { Node it -> it.name().localPart == "a" } as List<Node>
        }
        return links
    }

    public Node getElementById(String id) {
        assertValid()
        if (!elementsById) {
            elementsById = new HashMap()
            for (Node n : node.depthFirst()) {
                String nId = n.@id
                if (nId != null) {
                    elementsById[nId] = n
                }
            }
        }
        return elementsById[id]
    }
}
