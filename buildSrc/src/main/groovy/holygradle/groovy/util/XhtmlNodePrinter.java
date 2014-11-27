package holygradle.groovy.util;

import groovy.util.*;
import groovy.xml.*;

import java.io.*;
import java.util.*;

/**
 * Subclass of {@link XmlNodePrinter} tweaked to output XHTML correctly (in particular, no extra line breaks or other
 * whitespace around text nodes).
 *
 * This class is substantially the same source as XmlNodePrinter so I regard it as a Derived Work under the Apache
 * License 2.0 under which Groovy 1.8.6 is distributed.
 */
public class XhtmlNodePrinter extends XmlNodePrinter {
    public XhtmlNodePrinter(PrintWriter out) {
        super(out);
    }

    public XhtmlNodePrinter(PrintWriter out, String indent) {
        super(out, indent);
    }

    public XhtmlNodePrinter(PrintWriter out, String indent, String quote) {
        super(out, indent, quote);
    }

    public XhtmlNodePrinter(IndentPrinter out) {
        super(out);
    }

    public XhtmlNodePrinter(IndentPrinter out, String quote) {
        super(out, quote);
    }

    public XhtmlNodePrinter() {
    }

    @Override
    protected void print(Node node, NamespaceContext ctx) {
        /*
         * Handle empty elements like '<br/>', '<img/> or '<hr noshade="noshade"/>.
         */
        if (isEmptyElement(node)) {
            printLineBegin();
            out.print("<");
            out.print(getName(node));
            if (ctx != null) {
                printNamespace(node, ctx);
            }
            printNameAttributes(node.attributes(), ctx);
            if (isExpandEmptyElements()) {
                out.print("></");
                out.print(getName(node));
                out.print(">");
            } else {
                out.print("/>");
            }
            printLineEnd();
            out.flush();
            return;
        }

        /*
         * Hook for extra processing, e.g. GSP tag element!
         */
        if (printSpecialNode(node)) {
            out.flush();
            return;
        }

        /*
         * Handle normal element like <html> ... </html>.
         */
        Object value = node.value();
        if (value instanceof List) {
            printName(node, ctx, true, firstChildIsSimple((List) value));
            printList((List) value, ctx);
            printName(node, ctx, false, firstChildIsSimple((List) value));
            out.flush();
            return;
        }

        // treat as simple type - probably a String
        printName(node, ctx, true, isPreserveWhitespace());
        printSimpleItemWithIndent(value);
        printName(node, ctx, false, isPreserveWhitespace());
        out.flush();
    }

    private boolean firstChildIsSimple(List value) {
        return !(value.get(0) instanceof Node) && isPreserveWhitespace();
    }

    @Override
    protected void printName(Node node, NamespaceContext ctx, boolean begin, boolean preserve) {
        if (node == null) {
            throw new NullPointerException("Node must not be null.");
        }
        Object name = node.name();
        if (name == null) {
            throw new NullPointerException("Name must not be null.");
        }
        if (!preserve && begin) printLineBegin();
        out.print("<");
        if (!begin) {
            out.print("/");
        }
        out.print(getName(node));
        if (ctx != null) {
            printNamespace(node, ctx);
        }
        if (begin) {
            printNameAttributes(node.attributes(), ctx);
        }
        out.print(">");
        if (!preserve && !begin) printLineEnd();
    }

    private boolean isEmptyElement(Node node) {
        if (node == null) {
            throw new IllegalArgumentException("Node must not be null!");
        }
        if (!node.children().isEmpty()) {
            return false;
        }
        return node.text().length() == 0;
    }

    private String getName(Object object) {
        if (object instanceof String) {
            return (String) object;
        } else if (object instanceof QName) {
            QName qname = (QName) object;
            if (!isNamespaceAware()) {
                return qname.getLocalPart();
            }
            return qname.getQualifiedName();
        } else if (object instanceof Node) {
            Object name = ((Node) object).name();
            return getName(name);
        }
        return object.toString();
    }

    private void printSimpleItemWithIndent(Object value) {
        if (!isPreserveWhitespace()) out.incrementIndent();
        printSimpleItem(value);
        if (!isPreserveWhitespace()) out.decrementIndent();
    }
}
