package com.data.oai.shared.util;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;

public final class XmlFactories {

    private XmlFactories() {}

    public static XMLInputFactory newFactory(boolean namespaceAware) {
        XMLInputFactory f = XMLInputFactory.newFactory();
        try {
            f.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, namespaceAware);
        } catch (IllegalArgumentException ignored) {
        }
        return f;
    }

    /**
     * Retrieves the value of an attribute by its local name from a StartElement.
     * First tries a direct QName lookup, then falls back to iterating all
     * attributes to match by local part (handles namespace-qualified attributes).
     */
    public static String attrValue(StartElement se, String attrLocalName) {
        if (se == null || attrLocalName == null) return null;

        // Try without namespace
        Attribute a = se.getAttributeByName(new QName(attrLocalName));
        if (a != null) return a.getValue();

        // Some parsers treat attributes with namespaces differently; try any attr that matches local part
        @SuppressWarnings("unchecked")
        var it = se.getAttributes();
        while (it != null && it.hasNext()) {
            Attribute at = (Attribute) it.next();
            if (attrLocalName.equals(at.getName().getLocalPart())) {
                return at.getValue();
            }
        }
        return null;
    }
}
