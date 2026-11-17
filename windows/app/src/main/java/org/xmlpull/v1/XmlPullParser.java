package org.xmlpull.v1;

public interface XmlPullParser {
    int START_DOCUMENT = 0, END_DOCUMENT = 1, START_TAG = 2, END_TAG = 3, TEXT = 4, CDSECT = 5, ENTITY_REF = 6, IGNORABLE_WHITESPACE = 7, PROCESSING_INSTRUCTION = 8, COMMENT = 9, DOCDECL = 10;
    String FEATURE_PROCESS_NAMESPACES = "http://xmlpull.org/v1/doc/features.html#process-namespaces";
    String NO_NAMESPACE = "";
    void setFeature(String name, boolean state) throws XmlPullParserException;
    void setInput(java.io.Reader reader) throws XmlPullParserException;
    void setInput(java.io.InputStream in, String encoding) throws XmlPullParserException;
    int next() throws XmlPullParserException, java.io.IOException;
    int nextToken() throws XmlPullParserException, java.io.IOException;
    String nextText() throws XmlPullParserException, java.io.IOException;
    int nextTag() throws XmlPullParserException, java.io.IOException;
    int getEventType() throws XmlPullParserException;
    String getName();
    String getText();
    int getDepth();
    String getNamespace();
    int getAttributeCount();
    String getAttributeName(int i);
    String getAttributeValue(int i);
    String getAttributeValue(String namespace, String name);
    void require(int type, String namespace, String name) throws XmlPullParserException, java.io.IOException;
    boolean isWhitespace() throws XmlPullParserException;
}
