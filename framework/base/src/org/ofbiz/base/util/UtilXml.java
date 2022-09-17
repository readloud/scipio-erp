/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package org.ofbiz.base.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URL;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.xerces.parsers.DOMParser;
import org.apache.xerces.xni.Augmentations;
import org.apache.xerces.xni.NamespaceContext;
import org.apache.xerces.xni.QName;
import org.apache.xerces.xni.XMLAttributes;
import org.apache.xerces.xni.XMLLocator;
import org.apache.xerces.xni.XMLResourceIdentifier;
import org.apache.xerces.xni.XMLString;
import org.apache.xerces.xni.XNIException;
import org.ofbiz.base.util.collections.FlexibleMapAccessor;
import org.ofbiz.base.util.string.FlexibleStringExpander;
import org.w3c.dom.Attr;
import org.w3c.dom.DOMConfiguration;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.TypeInfo;
import org.w3c.dom.UserDataHandler;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

/**
 * Utilities methods to simplify dealing with JAXP and DOM XML parsing
 *
 */
public final class UtilXml {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());
    private static final XStream xstream = createXStream();
    private static final List<String> logDetailedExcludedClasses = Arrays.asList(UtilXml.class.getName()); // SCIPIO
    private UtilXml () {}

    private static XStream createXStream() {
        XStream xstream = new XStream();
        xstream.registerConverter(new UnsupportedClassConverter());
        return xstream;
    }

    // ----- DOM Level 3 Load and Save Methods -- //

    /** Returns a <code>DOMImplementationLS</code> instance.
     * @return A <code>DOMImplementationLS</code> instance
     * @see <a href="http://www.w3.org/TR/2004/REC-DOM-Level-3-LS-20040407/">DOM Level 3 Load and Save Specification</a>
     * @throws ClassCastException
     * @throws ClassNotFoundException
     * @throws InstantiationException
     * @throws IllegalAccessException
     */
    public static DOMImplementationLS getDomLsImplementation() throws ClassCastException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        DOMImplementationRegistry registry = DOMImplementationRegistry.newInstance();
        return (DOMImplementationLS)registry.getDOMImplementation("LS");
    }

    /** Returns a <code>LSOutput</code> instance.
     * @param impl A <code>DOMImplementationLS</code> instance
     * @param os Optional <code>OutputStream</code> instance
     * @param encoding Optional character encoding, default is UTF-8
     * @return A <code>LSOutput</code> instance
     * @see <a href="http://www.w3.org/TR/2004/REC-DOM-Level-3-LS-20040407/">DOM Level 3 Load and Save Specification</a>
     */
    public static LSOutput createLSOutput(DOMImplementationLS impl, OutputStream os, String encoding) {
        LSOutput out = impl.createLSOutput();
        if (os != null) {
            out.setByteStream(os);
        }
        if (encoding != null) {
            out.setEncoding(encoding);
        }
        return out;
    }

    /** Returns a <code>LSSerializer</code> instance.
     * @param impl A <code>DOMImplementationLS</code> instance
     * @param includeXmlDeclaration If set to <code>true</code>,
     * the xml declaration will be included in the output
     * @param enablePrettyPrint If set to <code>true</code>, the
     * output will be formatted in human-readable form. If set to
     * <code>false</code>, the entire document will consist of a single line.
     * @return A <code>LSSerializer</code> instance
     * @see <a href="http://www.w3.org/TR/2004/REC-DOM-Level-3-LS-20040407/">DOM Level 3 Load and Save Specification</a>
     */
    public static LSSerializer createLSSerializer(DOMImplementationLS impl, boolean includeXmlDeclaration, boolean enablePrettyPrint) {
        LSSerializer writer = impl.createLSSerializer();
        DOMConfiguration domConfig = writer.getDomConfig();
        domConfig.setParameter("xml-declaration", includeXmlDeclaration);
        domConfig.setParameter("format-pretty-print", enablePrettyPrint);
        return writer;
    }

    /** Serializes a DOM Node to an <code>OutputStream</code> using DOM 3.
     * @param os The <code>OutputStream</code> instance to write to
     * @param node The DOM <code>Node</code> object to be serialized
     * @param encoding Optional character encoding
     * @param includeXmlDeclaration If set to <code>true</code>,
     * the xml declaration will be included in the output
     * @param enablePrettyPrint If set to <code>true</code>, the
     * output will be formatted in human-readable form. If set to
     * <code>false</code>, the entire document will consist of a single line.
     * @see <a href="http://www.w3.org/TR/2004/REC-DOM-Level-3-LS-20040407/">DOM Level 3 Load and Save Specification</a>
     * @throws ClassCastException
     * @throws ClassNotFoundException
     * @throws InstantiationException
     * @throws IllegalAccessException
     */
    public static void writeXmlDocument(OutputStream os, Node node, String encoding, boolean includeXmlDeclaration, boolean enablePrettyPrint) throws ClassCastException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        DOMImplementationLS impl = getDomLsImplementation();
        LSOutput out = createLSOutput(impl, os, encoding);
        LSSerializer writer = createLSSerializer(impl, includeXmlDeclaration, enablePrettyPrint);
        writer.write(node, out);
    }

    // ----- TrAX Methods ----------------- //

    /** Creates a JAXP TrAX Transformer suitable for pretty-printing an
     * XML document. This method is provided as an alternative to the
     * deprecated <code>org.apache.xml.serialize.OutputFormat</code> class.
     * @param encoding Optional encoding, defaults to UTF-8
     * @param omitXmlDeclaration If <code>true</code> the xml declaration
     * will be omitted from the output
     * @param indent If <code>true</code>, the output will be indented
     * @param indentAmount If <code>indent</code> is <code>true</code>,
     * the number of spaces to indent. Default is 4.
     * @return A <code>Transformer</code> instance
     * @see <a href="http://java.sun.com/javase/6/docs/api/javax/xml/transform/package-summary.html">JAXP TrAX</a>
     * @throws TransformerConfigurationException
     */
    public static Transformer createOutputTransformer(String encoding, boolean omitXmlDeclaration, boolean indent, int indentAmount) throws TransformerConfigurationException {
        // Developers: This stylesheet strips all formatting space characters from the XML,
        // then indents the XML using the specified indentation.
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" xmlns:xalan=\"http://xml.apache.org/xslt\" version=\"1.0\">\n");
        sb.append("<xsl:output method=\"xml\" encoding=\"");
        sb.append(encoding == null ? "UTF-8" : encoding);
        sb.append("\"");
        if (omitXmlDeclaration) {
            sb.append(" omit-xml-declaration=\"yes\"");
        }
        sb.append(" indent=\"");
        sb.append(indent ? "yes" : "no");
        sb.append("\"");
        if (indent) {
            sb.append(" xalan:indent-amount=\"");
            sb.append(indentAmount <= 0 ? 4 : indentAmount);
            sb.append("\"");
        }
        sb.append("/>\n<xsl:strip-space elements=\"*\"/>\n");
        sb.append("<xsl:template match=\"@*|node()\">\n");
        sb.append("<xsl:copy><xsl:apply-templates select=\"@*|node()\"/></xsl:copy>\n");
        sb.append("</xsl:template>\n</xsl:stylesheet>\n");
        ByteArrayInputStream bis = new ByteArrayInputStream(sb.toString().getBytes(UtilIO.getUtf8())); // SCIPIO: UtilIO.getUtf8()
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        return transformerFactory.newTransformer(new StreamSource(bis));
    }

    /** Serializes a DOM <code>Node</code> to an <code>OutputStream</code>
     * using JAXP TrAX.
     * @param transformer A <code>Transformer</code> instance
     * @param node The <code>Node</code> to serialize
     * @param os The <code>OutputStream</code> to serialize to
     * @see <a href="http://java.sun.com/javase/6/docs/api/javax/xml/transform/package-summary.html">JAXP TrAX</a>
     * @throws TransformerException
     */
    public static void transformDomDocument(Transformer transformer, Node node, OutputStream os) throws TransformerException {
        DOMSource source = new DOMSource(node);
        StreamResult result = new StreamResult(os);
        transformer.transform(source, result);
    }

    /** Serializes a DOM <code>Node</code> to an <code>OutputStream</code>
     * using JAXP TrAX.
     * @param node The <code>Node</code> to serialize
     * @param os The <code>OutputStream</code> to serialize to
     * @param encoding Optional encoding, defaults to UTF-8
     * @param omitXmlDeclaration If <code>true</code> the xml declaration
     * will be omitted from the output
     * @param indent If <code>true</code>, the output will be indented
     * @param indentAmount If <code>indent</code> is <code>true</code>,
     * the number of spaces to indent. Default is 4.
     * @see <a href="http://java.sun.com/javase/6/docs/api/javax/xml/transform/package-summary.html">JAXP TrAX</a>
     * @throws TransformerException
     */
    public static void writeXmlDocument(Node node, OutputStream os, String encoding, boolean omitXmlDeclaration, boolean indent, int indentAmount) throws TransformerException {
        Transformer transformer = createOutputTransformer(encoding, omitXmlDeclaration, indent, indentAmount);
        transformDomDocument(transformer, node, os);
    }

    // ----- Java Object Marshalling/Unmarshalling ----- //

    /** Deserialize an object from an <code>InputStream</code>.
     *
     * @param input The <code>InputStream</code>
     * @return The deserialized <code>Object</code>
     */
    public static Object fromXml(InputStream input) {
        return xstream.fromXML(input);
    }

    /** Deserialize an object from a <code>Reader</code>.
     *
     * @param reader The <code>Reader</code>
     * @return The deserialized <code>Object</code>
     */
    public static Object fromXml(Reader reader) {
        return xstream.fromXML(reader);
    }

    /** Deserialize an object from a <code>String</code>.
     *
     * @param str The <code>String</code>
     * @return The deserialized <code>Object</code>
     */
    public static Object fromXml(String str) {
        return xstream.fromXML(str);
    }

    /** Serialize an object to an XML <code>String</code>.
     *
     * @param obj The object to serialize
     * @return An XML <code>String</code>
     */
    public static String toXml(Object obj) {
        return xstream.toXML(obj);
    }

    /** Serialize an object to an <code>OutputStream</code>.
     *
     * @param obj The object to serialize
     * @param output The <code>OutputStream</code>
     */
    public static void toXml(Object obj, OutputStream output) {
        xstream.toXML(obj, output);
    }

    /** Serialize an object to a <code>Writer</code>.
     *
     * @param obj The object to serialize
     * @param writer The <code>Writer</code>
     */
    public static void toXml(Object obj, Writer writer) {
        xstream.toXML(obj, writer);
    }

    // ------------------------------------------------- //

    public static String writeXmlDocument(Node node) throws java.io.IOException {
        if (node == null) {
            Debug.logWarning("[UtilXml.writeXmlDocument] Node was null, doing nothing" + getLogSuffixDetailed(), module); // SCIPIO: getLogSuffixDetailed
            return null;
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        writeXmlDocument(bos, node);
        return bos.toString("UTF-8");
    }

    public static void writeXmlDocument(String filename, Node node) throws FileNotFoundException, IOException {
        if (node == null) {
            Debug.logWarning("[UtilXml.writeXmlDocument] Node was null, doing nothing" + getLogSuffixDetailed(), module); // SCIPIO: getLogSuffixDetailed
            return;
        }
        if (filename == null) {
            Debug.logWarning("[UtilXml.writeXmlDocument] Filename was null, doing nothing" + getLogSuffixDetailed(), module); // SCIPIO: getLogSuffixDetailed
            return;
        }
        File outFile = new File(filename);
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(outFile);
            writeXmlDocument(fos, node);
        } finally {
            if (fos != null) {
                fos.close();
            }
        }
    }

    public static void writeXmlDocument(OutputStream os, Node node) throws java.io.IOException {
        if (node == null) {
            Debug.logWarning("[UtilXml.writeXmlDocument] Node was null, doing nothing" + getLogSuffixDetailed(), module); // SCIPIO: getLogSuffixDetailed
            return;
        }
        // OutputFormat defaults are: indent on, indent = 4, include XML declaration,
        // charset = UTF-8, line width = 72
        try {
            writeXmlDocument(node, os, "UTF-8", false, true, 4);
        } catch (TransformerException e) {
            // Wrapping this exception for backwards compatibility
            throw new IOException(e.getMessage());
        }
    }

    public static Document readXmlDocument(String content)
            throws SAXException, ParserConfigurationException, java.io.IOException {
        return readXmlDocument(content, true);
    }

    public static Document readXmlDocument(String content, boolean validate)
            throws SAXException, ParserConfigurationException, java.io.IOException {
        if (content == null) {
            Debug.logWarning("[UtilXml.readXmlDocument] content was null, doing nothing" + getLogSuffixDetailed(), module); // SCIPIO: getLogSuffixDetailed
            return null;
        }
        ByteArrayInputStream bis = new ByteArrayInputStream(content.getBytes(UtilIO.getUtf8())); // SCIPIO: UtilIO.getUtf8()
        return readXmlDocument(bis, validate, "Internal Content");
    }

    public static Document readXmlDocument(String content, boolean validate, boolean withPosition)
            throws SAXException, ParserConfigurationException, java.io.IOException {
        if (content == null) {
            Debug.logWarning("[UtilXml.readXmlDocument] content was null, doing nothing" + getLogSuffixDetailed(), module); // SCIPIO: getLogSuffixDetailed
            return null;
        }
        ByteArrayInputStream bis = new ByteArrayInputStream(content.getBytes(UtilIO.getUtf8())); // SCIPIO: UtilIO.getUtf8()
        return readXmlDocument(bis, validate, "Internal Content", withPosition);
    }

    public static Document readXmlDocument(URL url)
            throws SAXException, ParserConfigurationException, java.io.IOException {
        return readXmlDocument(url, true);
    }

    public static Document readXmlDocument(URL url, boolean validate)
            throws SAXException, ParserConfigurationException, java.io.IOException {
        if (url == null) {
            Debug.logWarning("[UtilXml.readXmlDocument] URL was null, doing nothing" + getLogSuffixDetailed(), module); // SCIPIO: getLogSuffixDetailed
            return null;
        }
        InputStream is = url.openStream();
        Document document = readXmlDocument(is, validate, url.toString());
        is.close();
        return document;
    }

    public static Document readXmlDocument(URL url, boolean validate, boolean withPosition)
            throws SAXException, ParserConfigurationException, java.io.IOException {
        if (url == null) {
            Debug.logWarning("[UtilXml.readXmlDocument] URL was null, doing nothing" + getLogSuffixDetailed(), module); // SCIPIO: getLogSuffixDetailed
            return null;
        }
        InputStream is = url.openStream();
        Document document = readXmlDocument(is, validate, url.toString(), withPosition);
        is.close();
        return document;
    }

    public static Document readXmlDocument(InputStream is, String docDescription)
            throws SAXException, ParserConfigurationException, java.io.IOException {
        return readXmlDocument(is, true, docDescription);
    }

    public static Document readXmlDocument(InputStream is, String docDescription, boolean withPosition)
            throws SAXException, ParserConfigurationException, java.io.IOException {
        return readXmlDocument(is, true, docDescription, withPosition);
    }

    public static Document readXmlDocument(InputStream is, boolean validate)
            throws SAXException, ParserConfigurationException, java.io.IOException { // SCIPIO: 2018-09-17: Added missing overload
        return readXmlDocument(is, validate, "Internal Content");
    }

    public static Document readXmlDocument(InputStream is, boolean validate, String docDescription)
            throws SAXException, ParserConfigurationException, java.io.IOException {
        if (is == null) {
            Debug.logWarning("[UtilXml.readXmlDocument] InputStream was null, doing nothing" + getLogSuffixDetailed(), module); // SCIPIO: getLogSuffixDetailed
            return null;
        }

        long startTime = System.currentTimeMillis();

        Document document = null;

        /* Standard JAXP (mostly), but doesn't seem to be doing XML Schema validation, so making sure that is on... */
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(validate);
        factory.setNamespaceAware(true);

        factory.setAttribute("http://xml.org/sax/features/validation", validate);
        factory.setAttribute("http://apache.org/xml/features/validation/schema", validate);

        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        // with a SchemaUrl, a URL object
        DocumentBuilder builder = factory.newDocumentBuilder();
        if (validate) {
            LocalResolver lr = new LocalResolver(new DefaultHandler());
            ErrorHandler eh = new LocalErrorHandler(docDescription, lr);

            builder.setEntityResolver(lr);
            builder.setErrorHandler(eh);
        }
        document = builder.parse(is);

        double totalSeconds = (System.currentTimeMillis() - startTime)/1000.0;
        if (Debug.verboseOn()) {
            Debug.logVerbose("XML Read " + totalSeconds + "s: " + docDescription, module);
        }
        return document;
    }

    public static Document readXmlDocument(InputStream is, boolean validate, String docDescription, boolean withPosition)
            throws SAXException, ParserConfigurationException, java.io.IOException {
        if (!withPosition) {
            return readXmlDocument(is, validate, docDescription);
        }

        if (is == null) {
            Debug.logWarning("[UtilXml.readXmlDocument] InputStream was null, doing nothing" + getLogSuffixDetailed(), module); // SCIPIO: getLogSuffixDetailed
            return null;
        }

        long startTime = System.currentTimeMillis();

        Document document = null;

        DOMParser parser = new DOMParser() {
            private XMLLocator locator = null;

            private void setLineColumn(Node node) {
                if (locator == null) {
                    throw new java.lang.IllegalStateException("XMLLocator is null");
                }
                if (node.getUserData("startLine") != null) {
                    return;
                }
                node.setUserData("systemId", locator.getLiteralSystemId(), null);
                node.setUserData("startLine", locator.getLineNumber(), null);
                node.setUserData("startColumn", locator.getColumnNumber(), null);
            }

            private void setLineColumn() {
                try {
                    Node node = (Node) getProperty("http://apache.org/xml/properties/dom/current-element-node");
                    if (node != null) {
                        setLineColumn(node);
                    }
                } catch (SAXException ex) {
                    Debug.logWarning(ex, module);
                }
            }

            private void setLastChildLineColumn() {
                try {
                    Node node = (Node) getProperty("http://apache.org/xml/properties/dom/current-element-node");
                    if (node != null) {
                       setLineColumn(node.getLastChild());
                    }
                } catch (SAXException ex) {
                    Debug.logWarning(ex, module);
                }
            }

            @Override
            public void startGeneralEntity(String name, XMLResourceIdentifier identifier, String encoding, Augmentations augs) throws XNIException {
                super.startGeneralEntity(name, identifier, encoding, augs);
                setLineColumn();
            }

            @Override
            public void comment(XMLString text, Augmentations augs) throws XNIException {
                super.comment(text, augs);
                setLastChildLineColumn();
            }

            @Override
            public void processingInstruction(String target, XMLString data, Augmentations augs) throws XNIException {
                super.processingInstruction(target, data, augs);
                setLastChildLineColumn();
            }

            @Override
            public void startDocument(XMLLocator locator, String encoding, NamespaceContext namespaceContext, Augmentations augs) throws XNIException {
                super.startDocument(locator, encoding, namespaceContext, augs);
                this.locator = locator;
                setLineColumn();
            }

            @Override
            public void doctypeDecl(String rootElement, String publicId, String systemId, Augmentations augs) throws XNIException {
                super.doctypeDecl(rootElement, publicId, systemId, augs);
            }

            @Override
            public void startElement(QName elementQName, XMLAttributes attrList, Augmentations augs) throws XNIException {
                super.startElement(elementQName, attrList, augs);
                setLineColumn();
            }

            @Override
            public void characters(XMLString text, Augmentations augs) throws XNIException {
                super.characters(text, augs);
                setLastChildLineColumn();
            }

            @Override
            public void ignorableWhitespace(XMLString text, Augmentations augs) throws XNIException {
                super.ignorableWhitespace(text, augs);
                setLastChildLineColumn();
            }
        };
        parser.setFeature("http://xml.org/sax/features/namespaces", true);
        parser.setFeature("http://xml.org/sax/features/validation", validate);
        parser.setFeature("http://apache.org/xml/features/validation/schema", validate);
        parser.setFeature("http://apache.org/xml/features/dom/defer-node-expansion", false);

        // with a SchemaUrl, a URL object
        if (validate) {
            LocalResolver lr = new LocalResolver(new DefaultHandler());
            ErrorHandler eh = new LocalErrorHandler(docDescription, lr);

            parser.setEntityResolver(lr);
            parser.setErrorHandler(eh);
        }
        InputSource inputSource = new InputSource(is);
        inputSource.setSystemId(docDescription);
        parser.parse(inputSource);
        document = parser.getDocument();

        double totalSeconds = (System.currentTimeMillis() - startTime)/1000.0;
        if (Debug.verboseOn()) {
            Debug.logVerbose("XML Read " + totalSeconds + "s: " + docDescription, module);
        }
        return document;
    }

    public static Document makeEmptyXmlDocument() {
        return makeEmptyXmlDocument(null);
    }

    public static Document makeEmptyXmlDocument(String rootElementName) {
        Document document = null;
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        factory.setValidating(true);
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();

            document = builder.newDocument();
        } catch (Exception e) {
            Debug.logError(e, module);
        }

        if (document == null) {
            return null;
        }

        if (rootElementName != null) {
            Element rootElement = document.createElement(rootElementName);
            document.appendChild(rootElement);
        }

        return document;
    }

    /** Creates a child element with the given name and appends it to the element child node list. */
    public static Element addChildElement(Element element, String childElementName, Document document) {
        Element newElement = document.createElement(childElementName);

        element.appendChild(newElement);
        return newElement;
    }

    /** Creates a child element with the given name and appends it to the element child node list.
     *  Also creates a Text node with the given value and appends it to the new elements child node list.
     */
    public static Element addChildElementValue(Element element, String childElementName,
            String childElementValue, Document document) {
        Element newElement = addChildElement(element, childElementName, document);

        newElement.appendChild(document.createTextNode(childElementValue));
        return newElement;
    }

    /** Creates a child element with the given namespace supportive name and appends it to the element child node list. */
    public static Element addChildElementNSElement(Element element, String childElementName,
            Document document, String nameSpaceUrl) {
        Element newElement = document.createElementNS(nameSpaceUrl, childElementName);
        element.appendChild(newElement);
        return element;
    }

    /** Creates a child element with the given namespace supportive name and appends it to the element child node list.
     *  Also creates a Text node with the given value and appends it to the new elements child node list.
     */
    public static Element addChildElementNSValue(Element element, String childElementName,
            String childElementValue, Document document, String nameSpaceUrl) {
        Element newElement = document.createElementNS(nameSpaceUrl, childElementName);
        newElement.appendChild(document.createTextNode(childElementValue));
        element.appendChild(newElement);
        return element;
    }

    /** Creates a child element with the given name and appends it to the element child node list.
     *  Also creates a CDATASection node with the given value and appends it to the new elements child node list.
     */
    public static Element addChildElementCDATAValue(Element element, String childElementName,
            String childElementValue, Document document) {
        Element newElement = addChildElement(element, childElementName, document);

        newElement.appendChild(document.createCDATASection(childElementValue));
        return newElement;
    }

    /** Return a List of Element objects that are children of the given element */
    public static List<? extends Element> childElementList(Element element) {
        if (element == null) {
            return null;
        }

        List<Element> elements = new LinkedList<>();
        Node node = element.getFirstChild();

        if (node != null) {
            do {
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element childElement = (Element) node;
                    elements.add(childElement);
                }
            } while ((node = node.getNextSibling()) != null);
        }
        return elements;
    }

    /** Return a List of Element objects that have the given name and are
     * immediate children of the given element; if name is null, all child
     * elements will be included. */
    public static List<? extends Element> childElementList(Element element, String childElementName) {
        if (element == null) {
            return null;
        }

        List<Element> elements = new LinkedList<>();
        Node node = element.getFirstChild();

        if (node != null) {
            do {
                String nodeName = UtilXml.getNodeNameIgnorePrefix(node);
                if (node.getNodeType() == Node.ELEMENT_NODE && (childElementName == null ||
                    childElementName.equals(nodeName))) {
                    Element childElement = (Element) node;
                    elements.add(childElement);
                }
            } while ((node = node.getNextSibling()) != null);
        }
        return elements;
    }

    /** Return a List of Element objects that have the given name and are
     * immediate children of the given element; if name is null, all child
     * elements will be included. */
    public static List<? extends Element> childElementList(Element element, Set<String> childElementNames) {
        if (element == null) {
            return null;
        }

        List<Element> elements = new LinkedList<>();
        if (childElementNames == null) {
            return elements;
        }
        Node node = element.getFirstChild();

        if (node != null) {
            do {
                if (node.getNodeType() == Node.ELEMENT_NODE && childElementNames.contains(node.getNodeName())) {
                    Element childElement = (Element) node;
                    elements.add(childElement);
                }
            } while ((node = node.getNextSibling()) != null);
        }
        return elements;
    }

    /** Return a List of Element objects that have the given name and are
     * immediate children of the given element; if name is null, all child
     * elements will be included. */
    public static List<? extends Element> childElementList(Element element, String... childElementNames) {
        return childElementList(element, UtilMisc.toSetArray(childElementNames));
    }

    /** Return a List of Element objects that are children of the given DocumentFragment */
    public static List<? extends Element> childElementList(DocumentFragment fragment) {
        if (fragment == null) {
            return null;
        }
        List<Element> elements = new LinkedList<>();
        Node node = fragment.getFirstChild();
        if (node != null) {
            do {
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element childElement = (Element) node;
                    elements.add(childElement);
                }
            } while ((node = node.getNextSibling()) != null);
        }
        return elements;
    }

    /** Return a List of Node objects that have the given name and are immediate children of the given element;
      * if name is null, all child elements will be included. */
    public static List<? extends Node> childNodeList(Node node) {
        if (node == null) {
            return null;
        }

        List<Node> nodes = new LinkedList<>();

        do {
            if (node.getNodeType() == Node.ELEMENT_NODE || node.getNodeType() == Node.COMMENT_NODE) {
                nodes.add(node);
            }
        } while ((node = node.getNextSibling()) != null);
        return nodes;
    }

    /** Return the first child Element
     * returns the first element. */
    public static Element firstChildElement(Element element, Set<String> childElementNames) {
        if (element == null) {
            return null;
        }
        // get the first element with the given name
        Node node = element.getFirstChild();

        if (node != null) {
            do {
                if (node.getNodeType() == Node.ELEMENT_NODE && childElementNames.contains(node.getNodeName())) {
                    Element childElement = (Element) node;

                    return childElement;
                }
            } while ((node = node.getNextSibling()) != null);
        }
        return null;
    }

    /** Return the first child Element
     * returns the first element. */
    public static Element firstChildElement(Element element, String... childElementNames) {
        return firstChildElement(element, UtilMisc.toSetArray(childElementNames));
    }

    /** Return the first child Element
     * returns the first element. */
    public static Element firstChildElement(Element element) {
        if (element == null) {
            return null;
        }
        // get the first element with the given name
        Node node = element.getFirstChild();

        if (node != null) {
            do {
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element childElement = (Element) node;

                    return childElement;
                }
            } while ((node = node.getNextSibling()) != null);
        }
        return null;
    }

    /** Return the first child Element with the given name; if name is null
     * returns the first element. */
    public static Element firstChildElement(Element element, String childElementName) {
        if (element == null) {
            return null;
        }
        if (UtilValidate.isEmpty(childElementName)) {
            return null;
        }
        // get the first element with the given name
        Node node = element.getFirstChild();

        if (node != null) {
            do {
                String nodeName = node.getLocalName();
                if (nodeName == null){
                    nodeName = UtilXml.getNodeNameIgnorePrefix(node);
                }
                if (node.getNodeType() == Node.ELEMENT_NODE && (childElementName == null ||
                    childElementName.equals(nodeName))) {
                    Element childElement = (Element) node;
                    return childElement;
                }
            } while ((node = node.getNextSibling()) != null);
        }
        return null;
    }

    /** Return the first child Element with the given name; if name is null
     * returns the first element. */
    public static Element firstChildElement(Element element, String childElementName, String attrName, String attrValue) {
        if (element == null) {
            return null;
        }
        // get the first element with the given name
        Node node = element.getFirstChild();

        if (node != null) {
            do {
                if (node.getNodeType() == Node.ELEMENT_NODE && (childElementName == null ||
                        childElementName.equals(node.getLocalName() != null ? node.getLocalName() : node.getNodeName()))) {
                    Element childElement = (Element) node;

                    String value = childElement.getAttribute(attrName);

                    if (value.equals(attrValue)) {
                        return childElement;
                    }
                }
            } while ((node = node.getNextSibling()) != null);
        }
        return null;
    }

    /** Return the text (node value) contained by the named child node. */
    public static String childElementValue(Element element, String childElementName) {
        if (element == null) {
            return null;
        }
        // get the value of the first element with the given name
        Element childElement = firstChildElement(element, childElementName);

        return elementValue(childElement);
    }

    /** Return the text (node value) contained by the named child node or a default value if null. */
    public static String childElementValue(Element element, String childElementName, String defaultValue) {
        if (element == null) {
            return defaultValue;
        }
        // get the value of the first element with the given name
        Element childElement = firstChildElement(element, childElementName);
        String elementValue = elementValue(childElement);

        if (UtilValidate.isEmpty(elementValue)) {
            return defaultValue;
        }
        return elementValue;
    }

    /** Return a named attribute of a named child node or a default if null. */
    public static String childElementAttribute(Element element, String childElementName, String attributeName, String defaultValue) {
        if (element == null) {
            return defaultValue;
        }
        // get the value of the first element with the given name
        Element childElement = firstChildElement(element, childElementName);
        String elementAttribute = elementAttribute(childElement, attributeName, defaultValue);

        if (UtilValidate.isEmpty(elementAttribute)) {
            return defaultValue;
        }
        return elementAttribute;
    }


    /** Return the text (node value) of the first node under this, works best if normalized. */
    public static String elementValue(Element element) {
        if (element == null) {
            return null;
        }
        // make sure we get all the text there...
        element.normalize();
        Node textNode = element.getFirstChild();

        if (textNode == null) {
            return null;
        }

        StringBuilder valueBuffer = new StringBuilder();
        do {
            if (textNode.getNodeType() == Node.CDATA_SECTION_NODE || textNode.getNodeType() == Node.TEXT_NODE) {
                valueBuffer.append(textNode.getNodeValue());
            }
        } while ((textNode = textNode.getNextSibling()) != null);
        return valueBuffer.toString();
    }

    /** Return the text (node value) of the first node under this */
    public static String nodeValue(Node node) {
        if (node == null) {
            return null;
        }

        StringBuilder valueBuffer = new StringBuilder();
        do {
            if (node.getNodeType() == Node.CDATA_SECTION_NODE || node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.COMMENT_NODE) {
                valueBuffer.append(node.getNodeValue());
            }
        } while ((node = node.getNextSibling()) != null);
        return valueBuffer.toString();
    }

    public static String elementAttribute(Element element, String attrName, String defaultValue) {
        if (element == null) {
            return defaultValue;
        }
        String attrValue = element.getAttribute(attrName);
        return UtilValidate.isNotEmpty(attrValue) ? attrValue : defaultValue;
    }

    public static String checkEmpty(String string) {
        if (UtilValidate.isNotEmpty(string)) {
            return string;
        }
        return "";
    }

    public static String checkEmpty(String string1, String string2) {
        if (UtilValidate.isNotEmpty(string1)) {
            return string1;
        } else if (UtilValidate.isNotEmpty(string2)) {
            return string2;
        } else {
            return "";
        }
    }

    public static String checkEmpty(String string1, String string2, String string3) {
        if (UtilValidate.isNotEmpty(string1)) {
            return string1;
        } else if (UtilValidate.isNotEmpty(string2)) {
            return string2;
        } else if (UtilValidate.isNotEmpty(string3)) {
            return string3;
        } else {
            return "";
        }
    }

    public static boolean checkBoolean(String str) {
        return checkBoolean(str, false);
    }

    public static boolean checkBoolean(String str, boolean defaultValue) {
        if (defaultValue) {
            //default to true, ie anything but false is true
            return !"false".equals(str);
        }
        //default to false, ie anything but true is false
        return "true".equals(str);
    }

    public static String nodeNameToJavaName(String nodeName, boolean capitalizeFirst) {
        boolean capitalize = capitalizeFirst;
        StringBuilder sb = new StringBuilder();
        for (int index = 0; index < nodeName.length(); index++) {
            char character = nodeName.charAt(index);
            if ((sb.length() == 0 && !Character.isJavaIdentifierStart(character)) || (sb.length() != 0 && !Character.isJavaIdentifierPart(character))) {
                capitalize = true;
                continue;
            }
            if (sb.length() == 0 && !capitalizeFirst) {
                sb.append(Character.toLowerCase(character));
            } else {
                if (capitalize) {
                    sb.append(Character.toUpperCase(character));
                    capitalize = false;
                } else {
                    sb.append(character);
                }
            }
        }
        return sb.toString();
    }

    /**
     * Local entity resolver to handle J2EE DTDs. With this a http connection
     * to sun is not needed during deployment.
     * Function boolean hadDTD() is here to avoid validation errors in
     * descriptors that do not have a DOCTYPE declaration.
     */
    public static class LocalResolver implements EntityResolver {

        private boolean hasDTD = false;
        private EntityResolver defaultResolver;

        public LocalResolver(EntityResolver defaultResolver) {
            this.defaultResolver = defaultResolver;
        }

        /**
         * Returns DTD inputSource. If DTD was found in the dtds Map and inputSource was created
         * flag hasDTD is set to true.
         * @param publicId - Public ID of DTD
         * @param systemId - System ID of DTD
         * @return InputSource of DTD
         */
        public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
            hasDTD = false;
            String dtd = UtilProperties.getSplitPropertyValue(UtilURL.fromResource("localdtds.properties"), publicId);
            if (UtilValidate.isNotEmpty(dtd)) {
                if (Debug.verboseOn()) {
                    Debug.logVerbose("[UtilXml.LocalResolver.resolveEntity] resolving DTD with publicId [" + publicId +
                            "], systemId [" + systemId + "] and the dtd file is [" + dtd + "]", module);
                }
                try {
                    URL dtdURL = UtilURL.fromResource(dtd);
                    if (dtdURL == null) {
                        throw new GeneralException("Local DTD not found - " + dtd);
                    }
                    InputStream dtdStream = dtdURL.openStream();
                    InputSource inputSource = new InputSource(dtdStream);

                    inputSource.setPublicId(publicId);
                    hasDTD = true;
                    if (Debug.verboseOn()) {
                        Debug.logVerbose("[UtilXml.LocalResolver.resolveEntity] got LOCAL DTD input source with publicId [" +
                                publicId + "] and the dtd file is [" + dtd + "]", module);
                    }
                    return inputSource;
                } catch (GeneralException | IOException e) {
                    Debug.logWarning(e, module);
                }
            } else {
                // nothing found by the public ID, try looking at the systemId, or at least the filename part of it and look for that on the classpath
                int lastSlash = systemId.lastIndexOf('/');
                String filename = null;
                if (lastSlash == -1) {
                    filename = systemId;
                } else {
                    filename = systemId.substring(lastSlash + 1);
                }

                URL resourceUrl = UtilURL.fromResource(filename);

                if (resourceUrl != null) {
                    InputStream resStream = resourceUrl.openStream();
                    InputSource inputSource = new InputSource(resStream);

                    if (UtilValidate.isNotEmpty(publicId)) {
                        inputSource.setPublicId(publicId);
                    }
                    hasDTD = true;
                    if (Debug.verboseOn()) {
                        Debug.logVerbose("[UtilXml.LocalResolver.resolveEntity] got LOCAL DTD/Schema input source with publicId [" +
                                publicId + "] and the file/resource is [" + filename + "]", module);
                    }
                    return inputSource;
                }
                Debug.logWarning("[UtilXml.LocalResolver.resolveEntity] could not find LOCAL DTD/Schema with publicId [" +
                        publicId + "] and the file/resource is [" + filename + "]", module);
                return null;
            }
            return defaultResolver.resolveEntity(publicId, systemId);
        }

        /**
         * Returns the boolean value to inform id DTD was found in the XML file or not
         * @return boolean - true if DTD was found in XML
         */
        public boolean hasDTD() {
            return hasDTD;
        }
    }


    /** Local error handler for entity resolver to DocumentBuilder parser.
     * Error is printed to output just if DTD was detected in the XML file.
     */
    public static class LocalErrorHandler implements ErrorHandler {

        private String docDescription;
        private LocalResolver localResolver;

        public LocalErrorHandler(String docDescription, LocalResolver localResolver) {
            this.docDescription = docDescription;
            this.localResolver = localResolver;
        }

        public void error(SAXParseException exception) {
            String exceptionMessage = exception.getMessage();
            Pattern valueFlexExpr = Pattern.compile("value '\\$\\{.*\\}'");
            Matcher matcher = valueFlexExpr.matcher(exceptionMessage.toLowerCase());
            if (localResolver.hasDTD() && !matcher.find()) {
                Debug.logError("XmlFileLoader: File "
                    + docDescription
                    + " process error. Line: "
                    + String.valueOf(exception.getLineNumber())
                    + ". Error message: "
                    + exceptionMessage, module
               );
            }
        }

        public void fatalError(SAXParseException exception) {
            if (localResolver.hasDTD()) {
                Debug.logError("XmlFileLoader: File "
                    + docDescription
                    + " process fatal error. Line: "
                    + String.valueOf(exception.getLineNumber())
                    + ". Error message: "
                    + exception.getMessage(), module
               );
            }
        }

        public void warning(SAXParseException exception) {
            if (localResolver.hasDTD()) {
                Debug.logError("XmlFileLoader: File "
                    + docDescription
                    + " process warning. Line: "
                    + String.valueOf(exception.getLineNumber())
                    + ". Error message: "
                    + exception.getMessage(), module
               );
            }
        }
    }

    private static class UnsupportedClassConverter implements Converter {

        @Override
        public boolean canConvert(@SuppressWarnings("rawtypes") Class arg0) {
            if (java.lang.ProcessBuilder.class.equals(arg0)) {
                return true;
            }
            return false;
        }

        @Override
        public void marshal(Object arg0, HierarchicalStreamWriter arg1, MarshallingContext arg2) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object unmarshal(HierarchicalStreamReader arg0, UnmarshallingContext arg1) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * get node name without any prefix
     * @param node
     * @return
     */
    public static String getNodeNameIgnorePrefix(Node node){
        if (node==null) {
            return null;
        }
        String nodeName = node.getNodeName();
        if (nodeName.contains(":")){
            // remove any possible prefix
            nodeName = nodeName.split(":")[1];
        }
        return nodeName;
    }

    /**
     * get tag name without any prefix
     * @param element
     * @return
     */
    public static String getTagNameIgnorePrefix(Element element){
        if (element==null) {
            return null;
        }
        String tagName = element.getTagName();
        if (tagName.contains(":")){
            // remove any possible prefix
            tagName = tagName.split(":")[1];
        }
        return tagName;
    }

    /**
     * get attribute value ignoring prefix in attribute name
     * @param element
     * @return
     */
    public static String getAttributeValueIgnorePrefix(Element element, String attributeName){
        if (element==null) {
            return "";
        }

        NamedNodeMap attributes = element.getAttributes();
        if (attributes != null){
            for (int i = 0, size = attributes.getLength(); i < size; i++)
            {
                Node node = attributes.item(i);
                if (node.getNodeType() == Node.ATTRIBUTE_NODE){
                    String nodeName = UtilXml.getNodeNameIgnorePrefix(node);
                    if (nodeName.equals(attributeName)){
                        return node.getNodeValue();
                    }
                }
            }
        }
        return "";
    }

    /**
     * SCIPIO: Base class wrapping and delegating to an Element.
     */
    public static abstract class ElementWrapper implements Element {

        protected final Element element;

        // Constructors
        public ElementWrapper(Element element) {
            this.element = element;
        }

        // Element interface methods

        @Override
        public String getTagName() {
            return element.getTagName();
        }

        @Override
        public String getAttribute(String name) {
            return element.getAttribute(name);
        }

        @Override
        public void setAttribute(String name, String value) throws DOMException {
            element.setAttribute(name, value);
        }

        @Override
        public void removeAttribute(String name) throws DOMException {
            element.removeAttribute(name);
        }

        @Override
        public Attr getAttributeNode(String name) {
            return element.getAttributeNode(name);
        }

        @Override
        public Attr setAttributeNode(Attr newAttr) throws DOMException {
            return element.setAttributeNode(newAttr);
        }

        @Override
        public Attr removeAttributeNode(Attr oldAttr) throws DOMException {
            return element.removeAttributeNode(oldAttr);
        }

        @Override
        public String getNodeName() {
            return element.getNodeName();
        }

        @Override
        public String getNodeValue() throws DOMException {
            return element.getNodeValue();
        }

        @Override
        public NodeList getElementsByTagName(String name) {
            return element.getElementsByTagName(name);
        }

        @Override
        public void setNodeValue(String nodeValue) throws DOMException {
            element.setNodeValue(nodeValue);
        }

        @Override
        public String getAttributeNS(String namespaceURI, String localName) throws DOMException {
            return element.getAttributeNS(namespaceURI, localName);
        }

        @Override
        public short getNodeType() {
            return element.getNodeType();
        }

        @Override
        public Node getParentNode() {
            return element.getParentNode();
        }

        @Override
        public NodeList getChildNodes() {
            return element.getChildNodes();
        }

        @Override
        public void setAttributeNS(String namespaceURI, String qualifiedName, String value) throws DOMException {
            element.setAttributeNS(namespaceURI, qualifiedName, value);
        }

        @Override
        public Node getFirstChild() {
            return element.getFirstChild();
        }

        @Override
        public Node getLastChild() {
            return element.getLastChild();
        }

        @Override
        public Node getPreviousSibling() {
            return element.getPreviousSibling();
        }

        @Override
        public Node getNextSibling() {
            return element.getNextSibling();
        }

        @Override
        public NamedNodeMap getAttributes() {
            return element.getAttributes();
        }

        @Override
        public Document getOwnerDocument() {
            return element.getOwnerDocument();
        }

        @Override
        public Node insertBefore(Node newChild, Node refChild) throws DOMException {
            return element.insertBefore(newChild, refChild);
        }

        @Override
        public void removeAttributeNS(String namespaceURI, String localName) throws DOMException {
            element.removeAttributeNS(namespaceURI, localName);
        }

        @Override
        public Node replaceChild(Node newChild, Node oldChild) throws DOMException {
            return element.replaceChild(newChild, oldChild);
        }

        @Override
        public Attr getAttributeNodeNS(String namespaceURI, String localName) throws DOMException {
            return element.getAttributeNodeNS(namespaceURI, localName);
        }

        @Override
        public Node removeChild(Node oldChild) throws DOMException {
            return element.removeChild(oldChild);
        }

        @Override
        public Attr setAttributeNodeNS(Attr newAttr) throws DOMException {
            return element.setAttributeNodeNS(newAttr);
        }

        @Override
        public Node appendChild(Node newChild) throws DOMException {
            return element.appendChild(newChild);
        }

        @Override
        public NodeList getElementsByTagNameNS(String namespaceURI, String localName) throws DOMException {
            return element.getElementsByTagNameNS(namespaceURI, localName);
        }

        @Override
        public boolean hasChildNodes() {
            return element.hasChildNodes();
        }

        @Override
        public Node cloneNode(boolean deep) {
            return element.cloneNode(deep);
        }

        @Override
        public boolean hasAttribute(String name) {
            return element.hasAttribute(name);
        }

        @Override
        public boolean hasAttributeNS(String namespaceURI, String localName) throws DOMException {
            return element.hasAttributeNS(namespaceURI, localName);
        }

        @Override
        public void normalize() {
            element.normalize();
        }

        @Override
        public TypeInfo getSchemaTypeInfo() {
            return element.getSchemaTypeInfo();
        }

        @Override
        public void setIdAttribute(String name, boolean isId) throws DOMException {
            element.setIdAttribute(name, isId);
        }

        @Override
        public boolean isSupported(String feature, String version) {
            return element.isSupported(feature, version);
        }

        @Override
        public void setIdAttributeNS(String namespaceURI, String localName, boolean isId) throws DOMException {
            element.setIdAttributeNS(namespaceURI, localName, isId);
        }

        @Override
        public String getNamespaceURI() {
            return element.getNamespaceURI();
        }

        @Override
        public String getPrefix() {
            return element.getPrefix();
        }

        @Override
        public void setIdAttributeNode(Attr idAttr, boolean isId) throws DOMException {
            element.setIdAttributeNode(idAttr, isId);
        }

        @Override
        public void setPrefix(String prefix) throws DOMException {
            element.setPrefix(prefix);
        }

        @Override
        public String getLocalName() {
            return element.getLocalName();
        }

        @Override
        public boolean hasAttributes() {
            return element.hasAttributes();
        }

        @Override
        public String getBaseURI() {
            return element.getBaseURI();
        }

        @Override
        public short compareDocumentPosition(Node other) throws DOMException {
            return element.compareDocumentPosition(other);
        }

        @Override
        public String getTextContent() throws DOMException {
            return element.getTextContent();
        }

        @Override
        public void setTextContent(String textContent) throws DOMException {
            element.setTextContent(textContent);
        }

        @Override
        public boolean isSameNode(Node other) {
            return element.isSameNode(other);
        }

        @Override
        public String lookupPrefix(String namespaceURI) {
            return element.lookupPrefix(namespaceURI);
        }

        @Override
        public boolean isDefaultNamespace(String namespaceURI) {
            return element.isDefaultNamespace(namespaceURI);
        }

        @Override
        public String lookupNamespaceURI(String prefix) {
            return element.lookupNamespaceURI(prefix);
        }

        @Override
        public boolean isEqualNode(Node arg) {
            return element.isEqualNode(arg);
        }

        @Override
        public Object getFeature(String feature, String version) {
            return element.getFeature(feature, version);
        }

        @Override
        public Object setUserData(String key, Object data, UserDataHandler handler) {
            return element.setUserData(key, data, handler);
        }

        @Override
        public Object getUserData(String key) {
            return element.getUserData(key);
        }
    }

    /**
     * SCIPIO: Returns the element wrapped in a helper that even implements the Element
     * interface for convenience.
     */
    public static ElementHelper getElementHelper(Element element) {
        return new ElementHelper(element);
    }

    /**
     * SCIPIO: Wraps an element to provide helper methods, in addition to supporting all
     * the regular Element methods.
     */
    public static class ElementHelper extends ElementWrapper {

        // Constructors

        public ElementHelper(Element element) {
            super(element);
        }

        // Getters

        public Element getWrappedElement() {
            return element;
        }

        // Attribute helper methods

        public String attr(String attrName) {
            return element.getAttribute(attrName);
        }

        public String attr(String attrName, String defaultValue) {
            String attrValue = element.getAttribute(attrName);
            return UtilValidate.isNotEmpty(attrValue) ? attrValue : defaultValue;
        }

        public Boolean attrAsBoolean(String attrName) {
            return UtilMisc.booleanValue(attr(attrName));
        }

        public boolean attrAsBoolean(String attrName, boolean defaultValue) {
            return UtilMisc.booleanValue(attr(attrName), defaultValue);
        }

        public FlexibleStringExpander attrAsExpander(String attrName) {
            return FlexibleStringExpander.getInstance(attr(attrName));
        }

        public FlexibleStringExpander attrAsExpander(String attrName, String defaultValue) {
            return FlexibleStringExpander.getInstance(attr(attrName, defaultValue));
        }

        public <T> FlexibleMapAccessor<T> attrAsAccessor(String attrName) {
            return FlexibleMapAccessor.getInstance(attr(attrName));
        }

        public <T> FlexibleMapAccessor<T> attrAsAccessor(String attrName, String defaultValue) {
            return FlexibleMapAccessor.getInstance(attr(attrName, defaultValue));
        }

        // UtilXml method delegates

        /** Creates a child element with the given name and appends it to the element child node list. */
        public Element addChildElement(String childElementName, Document document) {
            return UtilXml.addChildElement(element, childElementName, document);
        }

        /** Creates a child element with the given name and appends it to the element child node list.
         *  Also creates a Text node with the given value and appends it to the new elements child node list.
         */
        public Element addChildElementValue(String childElementName, String childElementValue, Document document) {
            return UtilXml.addChildElementValue(element, childElementName, childElementValue, document);
        }

        /** Creates a child element with the given namespace supportive name and appends it to the element child node list. */
        public Element addChildElementNSElement(String childElementName, Document document, String nameSpaceUrl) {
            return UtilXml.addChildElementNSElement(element, childElementName, document, nameSpaceUrl);
        }

        /** Creates a child element with the given namespace supportive name and appends it to the element child node list.
         *  Also creates a Text node with the given value and appends it to the new elements child node list.
         */
        public Element addChildElementNSValue(String childElementName, String childElementValue, Document document, String nameSpaceUrl) {
            return UtilXml.addChildElementNSValue(element, childElementName, childElementValue, document, nameSpaceUrl);
        }

        /** Creates a child element with the given name and appends it to the element child node list.
         *  Also creates a CDATASection node with the given value and appends it to the new elements child node list.
         */
        public Element addChildElementCDATAValue(String childElementName, String childElementValue, Document document) {
            return UtilXml.addChildElementCDATAValue(element, childElementName, childElementValue, document);
        }

        /** Return a List of Element objects that are children of the given element */
        public List<? extends Element> childElementList() {
            return UtilXml.childElementList(element);
        }

        /** Return a List of Element objects that have the given name and are
         * immediate children of the given element; if name is null, all child
         * elements will be included. */
        public List<? extends Element> childElementList(String childElementName) {
            return UtilXml.childElementList(element, childElementName);
        }

        /** Return a List of Element objects that have the given name and are
         * immediate children of the given element; if name is null, all child
         * elements will be included. */
        public List<? extends Element> childElementList(Set<String> childElementNames) {
            return UtilXml.childElementList(element, childElementNames);
        }

        /** Return a List of Element objects that have the given name and are
         * immediate children of the given element; if name is null, all child
         * elements will be included. */
        public List<? extends Element> childElementList(String... childElementNames) {
            return UtilXml.childElementList(element, childElementNames);
        }

        /** Return a List of Node objects that have the given name and are immediate children of the given element;
          * if name is null, all child elements will be included. */
        public List<? extends Node> childNodeList() {
            return UtilXml.childNodeList(element);
        }

        /** Return the first child Element
         * returns the first element. */
        public Element firstChildElement(Set<String> childElementNames) {
            return UtilXml.firstChildElement(element, childElementNames);
        }

        /** Return the first child Element
         * returns the first element. */
        public Element firstChildElement(String... childElementNames) {
            return UtilXml.firstChildElement(element, childElementNames);
        }

        /** Return the first child Element
         * returns the first element. */
        public Element firstChildElement() {
            return UtilXml.firstChildElement(element);
        }

        /** Return the first child Element with the given name; if name is null
         * returns the first element. */
        public Element firstChildElement(String childElementName) {
            return UtilXml.firstChildElement(element, childElementName);
        }

        /** Return the first child Element with the given name; if name is null
         * returns the first element. */
        public Element firstChildElement(String childElementName, String attrName, String attrValue) {
            return UtilXml.firstChildElement(element, childElementName, attrName, attrValue);
        }

        /** Return the text (node value) contained by the named child node. */
        public String childElementValue(String childElementName) {
            return UtilXml.childElementValue(element, childElementName);
        }

        /** Return the text (node value) contained by the named child node or a default value if null. */
        public String childElementValue(String childElementName, String defaultValue) {
            return UtilXml.childElementValue(element, childElementName, defaultValue);
        }

        /** Return a named attribute of a named child node or a default if null. */
        public String childElementAttribute(String childElementName, String attributeName, String defaultValue) {
            return UtilXml.childElementAttribute(element, childElementName, attributeName, defaultValue);
        }

        /** Return the text (node value) of the first node under this, works best if normalized. */
        public String elementValue() {
            return UtilXml.elementValue(element);
        }

        /** Return the text (node value) of the first node under this */
        public String nodeValue() {
            return UtilXml.nodeValue(element);
        }

        public String elementAttribute(String attrName, String defaultValue) {
            return UtilXml.elementAttribute(element, attrName, defaultValue);
        }
    }

    /**
     * SCIPIO: Appends info about the caller to warnings and errors, otherwise they're largely useless.
     * Added 2018-12-05.
     */
    private static String getLogSuffixDetailed() {
        return " (" + Debug.getCallerShortInfo(logDetailedExcludedClasses) + ")";
    }
}
