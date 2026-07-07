package com.bidscube.sdk.utils;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Secure VAST XML parsing (XXE-hardened {@link DocumentBuilderFactory}).
 */
public final class VastXmlParser {

    private static final String TAG = "VastXmlParser";

    private VastXmlParser() {
    }

    public static Document parse(String vastXml) throws Exception {
        if (vastXml == null || vastXml.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty VAST XML");
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        configureSecure(factory);
        DocumentBuilder builder = factory.newDocumentBuilder();
        InputSource source = new InputSource(new StringReader(vastXml));
        Document doc = builder.parse(source);
        if (doc.getDocumentElement() != null) {
            doc.getDocumentElement().normalize();
        }
        return doc;
    }

    static void configureSecure(DocumentBuilderFactory factory) {
        setFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        setFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
        setFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        setFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        try {
            factory.setXIncludeAware(false);
        } catch (UnsupportedOperationException ignored) {
        }
        try {
            factory.setExpandEntityReferences(false);
        } catch (UnsupportedOperationException ignored) {
        }
    }

    private static void setFeature(DocumentBuilderFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (Exception e) {
            SDKLogger.w(TAG, "XML parser feature unsupported: " + feature);
        }
    }
}
