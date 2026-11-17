package org.xmlpull.v1;

import javax.xml.stream.*;

/** The XmlPullParser Android has, over the JDK's StAX parser. */
final class StaxPullParser implements XmlPullParser {
    private XMLStreamReader r; private boolean ns; private int event = START_DOCUMENT, depth; private boolean pendingEnd;
    public void setFeature(String name, boolean state) { if (FEATURE_PROCESS_NAMESPACES.equals(name)) ns = state; }
    private XMLInputFactory factory() { XMLInputFactory f = XMLInputFactory.newInstance(); f.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, ns); f.setProperty(XMLInputFactory.SUPPORT_DTD, false); f.setProperty(XMLInputFactory.IS_COALESCING, true); return f; }
    public void setInput(java.io.Reader reader) throws XmlPullParserException { try { r = factory().createXMLStreamReader(reader); event = START_DOCUMENT; depth = 0; } catch (XMLStreamException e) { throw new XmlPullParserException(e.getMessage()); } }
    public void setInput(java.io.InputStream in, String enc) throws XmlPullParserException { try { r = enc != null ? factory().createXMLStreamReader(in, enc) : factory().createXMLStreamReader(in); event = START_DOCUMENT; depth = 0; } catch (XMLStreamException e) { throw new XmlPullParserException(e.getMessage()); } }
    public int next() throws XmlPullParserException {
        if (r == null) throw new XmlPullParserException("no input");
        if (pendingEnd) { pendingEnd = false; depth--; }
        try {
            while (true) {
                if (!r.hasNext()) return event = END_DOCUMENT;
                switch (r.next()) {
                    case XMLStreamConstants.START_ELEMENT: depth++; return event = START_TAG;
                    case XMLStreamConstants.END_ELEMENT: pendingEnd = true; return event = END_TAG;
                    case XMLStreamConstants.CHARACTERS: case XMLStreamConstants.CDATA: case XMLStreamConstants.SPACE: return event = TEXT;
                    case XMLStreamConstants.END_DOCUMENT: return event = END_DOCUMENT;
                    default:
                }
            }
        } catch (XMLStreamException e) { throw new XmlPullParserException(e.getMessage()); }
    }
    public int nextToken() throws XmlPullParserException { return next(); }
    public String nextText() throws XmlPullParserException { StringBuilder b = new StringBuilder(); while (next() == TEXT) b.append(getText()); return b.toString(); }
    public int nextTag() throws XmlPullParserException { int t = next(); while (t == TEXT && isWhitespace()) t = next(); return t; }
    public int getEventType() { return event; }
    private String q(javax.xml.namespace.QName n) { return ns || n.getPrefix() == null || n.getPrefix().isEmpty() ? n.getLocalPart() : n.getPrefix() + ":" + n.getLocalPart(); }
    public String getName() { return r != null && (event == START_TAG || event == END_TAG) ? q(r.getName()) : null; }
    public String getText() { return r != null && event == TEXT ? r.getText() : null; }
    public int getDepth() { return depth; }
    public String getNamespace() { String n = r != null && (event == START_TAG || event == END_TAG) ? r.getNamespaceURI() : null; return n == null ? "" : n; }
    public int getAttributeCount() { return event == START_TAG ? r.getAttributeCount() : -1; }
    public String getAttributeName(int i) { return q(r.getAttributeName(i)); }
    public String getAttributeValue(int i) { return r.getAttributeValue(i); }
    public String getAttributeValue(String namespace, String name) {
        for (int i = 0; i < r.getAttributeCount(); i++) if (name.equals(getAttributeName(i)) || name.equals(r.getAttributeLocalName(i)) && (namespace == null || namespace.equals(r.getAttributeNamespace(i)))) return r.getAttributeValue(i);
        return null;
    }
    public void require(int type, String namespace, String name) throws XmlPullParserException { if (type != event || (name != null && !name.equals(getName()))) throw new XmlPullParserException("expected " + type + " " + name); }
    public boolean isWhitespace() { String t = getText(); return t == null || t.trim().isEmpty(); }
}
