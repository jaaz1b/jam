package org.xmlpull.v1

import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

/** The XmlPullParser API Android has, over the JDK's StAX parser. */
interface XmlPullParser {
    fun setFeature(name: String, state: Boolean)
    fun setInput(reader: java.io.Reader)
    fun setInput(input: java.io.InputStream, encoding: String?)
    fun next(): Int
    fun nextToken(): Int = next()
    fun nextText(): String
    fun nextTag(): Int
    val eventType: Int
    val name: String?
    val text: String?
    val depth: Int
    val namespace: String?
    val attributeCount: Int
    fun getAttributeName(i: Int): String?
    fun getAttributeValue(i: Int): String?
    fun getAttributeValue(namespace: String?, name: String): String?
    fun require(type: Int, namespace: String?, name: String?) {}
    val isWhitespace: Boolean get() = text?.isBlank() ?: true
    companion object {
        const val START_DOCUMENT = 0; const val END_DOCUMENT = 1; const val START_TAG = 2; const val END_TAG = 3; const val TEXT = 4
        const val CDSECT = 5; const val ENTITY_REF = 6; const val IGNORABLE_WHITESPACE = 7; const val PROCESSING_INSTRUCTION = 8; const val COMMENT = 9; const val DOCDECL = 10
        const val FEATURE_PROCESS_NAMESPACES = "http://xmlpull.org/v1/doc/features.html#process-namespaces"
        const val NO_NAMESPACE = ""
    }
}
class XmlPullParserException(msg: String?) : Exception(msg)
class XmlPullParserFactory private constructor() {
    var isNamespaceAware = false
    fun newPullParser(): XmlPullParser = StaxPullParser()
    companion object { @JvmStatic fun newInstance() = XmlPullParserFactory() }
}

internal class StaxPullParser : XmlPullParser {
    private var r: XMLStreamReader? = null
    private var ns = false
    override var eventType = XmlPullParser.START_DOCUMENT; private set
    override var depth = 0; private set
    private var pendingEnd = false
    override fun setFeature(name: String, state: Boolean) { if (name == XmlPullParser.FEATURE_PROCESS_NAMESPACES) ns = state }
    private fun factory() = XMLInputFactory.newInstance().apply {
        setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, ns); setProperty(XMLInputFactory.SUPPORT_DTD, false); setProperty(XMLInputFactory.IS_COALESCING, true)
    }
    override fun setInput(reader: java.io.Reader) { r = factory().createXMLStreamReader(reader); eventType = XmlPullParser.START_DOCUMENT; depth = 0 }
    override fun setInput(input: java.io.InputStream, encoding: String?) { r = if (encoding != null) factory().createXMLStreamReader(input, encoding) else factory().createXMLStreamReader(input); depth = 0 }
    override fun next(): Int {
        val x = r ?: throw XmlPullParserException("no input")
        if (pendingEnd) { pendingEnd = false; depth-- }
        while (true) {
            if (!x.hasNext()) { eventType = XmlPullParser.END_DOCUMENT; return eventType }
            when (x.next()) {
                XMLStreamConstants.START_ELEMENT -> { depth++; eventType = XmlPullParser.START_TAG; return eventType }
                XMLStreamConstants.END_ELEMENT -> { pendingEnd = true; eventType = XmlPullParser.END_TAG; return eventType }
                XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA, XMLStreamConstants.SPACE -> { eventType = XmlPullParser.TEXT; return eventType }
                XMLStreamConstants.END_DOCUMENT -> { eventType = XmlPullParser.END_DOCUMENT; return eventType }
                else -> {}
            }
        }
    }
    override fun nextText(): String {
        val sb = StringBuilder()
        while (next() == XmlPullParser.TEXT) sb.append(text)
        return sb.toString()
    }
    override fun nextTag(): Int { var t = next(); while (t == XmlPullParser.TEXT && isWhitespace) t = next(); return t }
    private fun localOrPrefixed(q: javax.xml.namespace.QName): String = if (ns || q.prefix.isNullOrEmpty()) q.localPart else q.prefix + ":" + q.localPart
    override val name: String? get() = r?.takeIf { eventType == XmlPullParser.START_TAG || eventType == XmlPullParser.END_TAG }?.let { localOrPrefixed(it.name) }
    override val text: String? get() = r?.takeIf { eventType == XmlPullParser.TEXT }?.text
    override val namespace: String? get() = r?.takeIf { eventType == XmlPullParser.START_TAG || eventType == XmlPullParser.END_TAG }?.namespaceURI ?: ""
    override val attributeCount: Int get() = if (eventType == XmlPullParser.START_TAG) r!!.attributeCount else -1
    override fun getAttributeName(i: Int): String? = localOrPrefixed(r!!.getAttributeName(i))
    override fun getAttributeValue(i: Int): String? = r!!.getAttributeValue(i)
    override fun getAttributeValue(namespace: String?, name: String): String? {
        val x = r!!
        for (i in 0 until x.attributeCount) if (getAttributeName(i) == name || x.getAttributeLocalName(i) == name && (namespace == null || x.getAttributeNamespace(i) == namespace)) return x.getAttributeValue(i)
        return null
    }
}
