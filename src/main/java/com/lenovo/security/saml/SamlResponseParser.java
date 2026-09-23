package com.lenovo.security.saml;

import com.alibaba.fastjson.JSON;
import com.lenovo.adfs.common.IDPMetaData;
import com.lenovo.adfs.exception.SAMLHandlerException;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.Unmarshaller;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Attribute;
import org.opensaml.saml.saml2.core.AttributeStatement;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.core.StatusCode;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.crypto.MarshalException;
import javax.xml.crypto.dom.DOMStructure;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureException;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Java 17 compatible replacement for the response parsing part of the legacy
 * lenovo-samlsp SamlHandler. The legacy implementation directly constructs a
 * JDK-internal Xerces class, which is inaccessible under the Java module system.
 */
public final class SamlResponseParser {

    private static final String XMLDSIG_NS = XMLSignature.XMLNS;
    private static final Set<String> RETURNED_ATTRIBUTES =
            Set.of("itcode", "firstname", "lastname", "email", "displayname", "manager");

    private SamlResponseParser() {
    }

    public static String parse(String encodedResponse) throws SAMLHandlerException {
        if (encodedResponse == null || encodedResponse.isBlank()) {
            throw new SAMLHandlerException("SAMLResponse is null");
        }

        try {
            Response response = unmarshall(encodedResponse);
            validateResponse(response);

            Assertion assertion = getAssertion(response);
            validateAssertion(assertion);
            validateSignature(assertion);

            return JSON.toJSONString(readAttributes(assertion));
        } catch (SAMLHandlerException e) {
            throw e;
        } catch (Exception e) {
            throw new SAMLHandlerException("SAMLResponse parsing failed: " + e.getMessage(), e);
        }
    }

    private static Response unmarshall(String encodedResponse) throws Exception {
        byte[] xml;
        try {
            xml = Base64.getMimeDecoder().decode(encodedResponse.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new SAMLHandlerException("SAMLResponse BASE64 decoding failed", e);
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        Document document;
        try (ByteArrayInputStream input = new ByteArrayInputStream(xml)) {
            document = factory.newDocumentBuilder().parse(input);
        }

        Element root = document.getDocumentElement();
        Unmarshaller unmarshaller = XMLObjectProviderRegistrySupport
                .getUnmarshallerFactory().getUnmarshaller(root);
        if (unmarshaller == null) {
            throw new SAMLHandlerException("No OpenSAML unmarshaller for SAMLResponse");
        }

        XMLObject object = unmarshaller.unmarshall(root);
        if (!(object instanceof Response)) {
            throw new SAMLHandlerException("Decoded object is not a SAMLResponse");
        }
        return (Response) object;
    }

    private static void validateResponse(Response response) throws SAMLHandlerException {
        String expectedIssuer = IDPMetaData.getEntityId();
        String actualIssuer = response.getIssuer() == null ? null : response.getIssuer().getValue();
        if (expectedIssuer == null || actualIssuer == null || !expectedIssuer.equalsIgnoreCase(actualIssuer)) {
            throw new SAMLHandlerException("Unexpected SAML response issuer");
        }

        String status = response.getStatus() == null || response.getStatus().getStatusCode() == null
                ? null : response.getStatus().getStatusCode().getValue();
        if (!StatusCode.SUCCESS.equals(status)) {
            throw new SAMLHandlerException("SAML response status is not Success");
        }
    }

    private static Assertion getAssertion(Response response) throws SAMLHandlerException {
        if (response.getAssertions() == null || response.getAssertions().size() != 1) {
            throw new SAMLHandlerException("SAMLResponse must contain exactly one assertion");
        }
        return response.getAssertions().get(0);
    }

    private static void validateAssertion(Assertion assertion) throws SAMLHandlerException {
        String expectedIssuer = IDPMetaData.getEntityId();
        String actualIssuer = assertion.getIssuer() == null ? null : assertion.getIssuer().getValue();
        if (expectedIssuer == null || actualIssuer == null || !expectedIssuer.equalsIgnoreCase(actualIssuer)) {
            throw new SAMLHandlerException("Unexpected SAML assertion issuer");
        }

        if (assertion.getConditions() == null || assertion.getConditions().getNotOnOrAfter() == null) {
            throw new SAMLHandlerException("SAML assertion expiration is missing");
        }
        if (assertion.getConditions().getNotOnOrAfter().isBeforeNow()) {
            throw new SAMLHandlerException("SAML assertion has expired");
        }
    }

    private static void validateSignature(Assertion assertion) throws SAMLHandlerException {
        Element assertionElement = assertion.getDOM();
        if (assertionElement == null) {
            throw new SAMLHandlerException("SAML assertion DOM is missing");
        }

        NodeList signatures = assertionElement.getElementsByTagNameNS(XMLDSIG_NS, "Signature");
        if (signatures.getLength() != 1) {
            throw new SAMLHandlerException("SAML assertion must contain exactly one signature");
        }

        String assertionId = assertion.getID();
        if (assertionId == null || assertionId.isBlank()) {
            throw new SAMLHandlerException("SAML assertion ID is missing");
        }
        assertionElement.setIdAttributeNS(null, "ID", true);

        PublicKey publicKey = IDPMetaData.getPublicKey();
        if (publicKey == null) {
            throw new SAMLHandlerException("ADFS signature public key is unavailable");
        }

        Node signatureNode = signatures.item(0);
        DOMValidateContext context = new DOMValidateContext(publicKey, signatureNode);
        context.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.TRUE);

        try {
            XMLSignature signature = XMLSignatureFactory.getInstance("DOM")
                    .unmarshalXMLSignature(new DOMStructure(signatureNode));

            @SuppressWarnings("unchecked")
            List<Reference> references = signature.getSignedInfo().getReferences();
            if (references.isEmpty()
                    || references.stream().noneMatch(reference -> ("#" + assertionId).equals(reference.getURI()))) {
                throw new SAMLHandlerException("SAML signature does not reference the assertion ID");
            }
            if (!signature.validate(context)) {
                throw new SAMLHandlerException("SAML assertion signature verification failed");
            }
        } catch (MarshalException | XMLSignatureException e) {
            throw new SAMLHandlerException("SAML assertion signature verification failed: " + e.getMessage(), e);
        }
    }

    private static Map<String, String> readAttributes(Assertion assertion) throws SAMLHandlerException {
        Map<String, String> attributes = new LinkedHashMap<>();
        for (AttributeStatement statement : assertion.getAttributeStatements()) {
            for (Attribute attribute : statement.getAttributes()) {
                String name = attribute.getName();
                if (name == null) {
                    continue;
                }

                String normalizedName = name.toLowerCase(Locale.ROOT);
                if (!RETURNED_ATTRIBUTES.contains(normalizedName) || attribute.getAttributeValues().isEmpty()) {
                    continue;
                }

                String value = textValue(attribute.getAttributeValues().get(0));
                if ("manager".equals(normalizedName)) {
                    value = managerCommonName(value);
                }
                attributes.put(normalizedName, value);
            }
        }

        if (!attributes.containsKey("itcode") || attributes.get("itcode").isBlank()) {
            throw new SAMLHandlerException("SAML assertion does not contain itcode");
        }
        return attributes;
    }

    private static String textValue(XMLObject value) {
        Element dom = value.getDOM();
        return dom == null || dom.getTextContent() == null ? "" : dom.getTextContent();
    }

    private static String managerCommonName(String distinguishedName) {
        if (distinguishedName == null) {
            return "";
        }
        for (String part : distinguishedName.split(",")) {
            String trimmed = part.trim();
            if (trimmed.regionMatches(true, 0, "CN=", 0, 3)) {
                return trimmed.substring(3);
            }
        }
        return distinguishedName;
    }
}
