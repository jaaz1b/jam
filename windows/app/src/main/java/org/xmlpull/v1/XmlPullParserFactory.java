package org.xmlpull.v1;
public class XmlPullParserFactory {
    private boolean ns;
    public static XmlPullParserFactory newInstance() { return new XmlPullParserFactory(); }
    public void setNamespaceAware(boolean b) { ns = b; }
    public XmlPullParser newPullParser() { StaxPullParser p = new StaxPullParser(); try { p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, ns); } catch (Exception ignored) {} return p; }
}
