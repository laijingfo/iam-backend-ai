package com.lenovo.adfs.common;

import com.lenovo.adfs.handler.SamlHandler;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;
import org.opensaml.core.config.InitializationException;
import org.opensaml.core.config.InitializationService;
import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.saml.metadata.resolver.impl.AbstractReloadingMetadataResolver;
import org.opensaml.saml.metadata.resolver.impl.FilesystemMetadataResolver;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.saml.saml2.metadata.KeyDescriptor;
import org.opensaml.saml.saml2.metadata.SingleSignOnService;
import org.opensaml.security.credential.UsageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import net.shibboleth.utilities.java.support.resolver.ResolverException;
import org.springframework.core.io.ClassPathResource;

import javax.net.ssl.SSLContext;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class IDPMetaData {
    static Logger log = LoggerFactory.getLogger(SamlHandler.class);
    private static final String ADFS_US_METADATA_URL = "https://stsus.lenovo.com/FederationMetadata/2007-06/FederationMetadata.xml";
    private static final String ADFS_CN_METADATA_URL = "https://stscn.lenovo.com/FederationMetadata/2007-06/FederationMetadata.xml";
    private static final Map<String, String> map = new ConcurrentHashMap<>(2);

    private static final String SAML20_PROTOCOL = "urn:oasis:names:tc:SAML:2.0:protocol";
    private static final String HTTP_POST_BINDING = "urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST";
    private static final String METADATA_CLASSPATH = "FederationMetadata.xml";

    /** 用 AbstractReloadingMetadataResolver，它同时有 getLastRefresh/iterator/resolveSingle */
    private static AbstractReloadingMetadataResolver idpMetadataResolver;

    private static String entityId;
    private static PublicKey publicKey;
    private static String ssoHttpPostUri;
    private static Instant lastRefresh;
    private static String metaDataUrl;

    public IDPMetaData() {
    }

    public static synchronized void initialize(String location)
            throws InitializationException, ResolverException, ComponentInitializationException {
        String url = map.get(location);
        if (url == null) {
            throw new IllegalArgumentException("location:" + location + " is Illegal,location is need cn or us");
        }
        metaDataUrl = url;
        InitializationService.initialize();

        // 本地文件加载
        File metadataFile = resolveMetadataFile();
        log.info("Loading IdP metadata from local file: {}", metadataFile.getAbsolutePath());

        FilesystemMetadataResolver resolver = new FilesystemMetadataResolver(metadataFile);
        resolver.setRequireValidMetadata(true);
        resolver.setParserPool(XMLObjectProviderRegistrySupport.getParserPool());
        resolver.setId(String.valueOf(UUID.randomUUID()));
        resolver.setFailFastInitialization(false);
        resolver.initialize();

        idpMetadataResolver = resolver;
        lastRefresh = resolver.getLastRefresh();
    }

    private static File resolveMetadataFile() throws ResolverException {
        try {
            ClassPathResource resource = new ClassPathResource(METADATA_CLASSPATH);
            if (resource.isFile()) {
                File f = resource.getFile();
                if (!f.exists()) {
                    throw new IOException("Metadata file does not exist: " + f.getAbsolutePath());
                }
                return f;
            }
            File tmp = File.createTempFile("FederationMetadata", ".xml");
            tmp.deleteOnExit();
            try (InputStream in = resource.getInputStream();
                 OutputStream out = new FileOutputStream(tmp)) {
                in.transferTo(out);
            }
            return tmp;
        } catch (IOException e) {
            throw new ResolverException("Failed to resolve local FederationMetadata.xml", e);
        }
    }

    private static void refresh() {
        if (idpMetadataResolver == null) {
            return;
        }
        Instant current = idpMetadataResolver.getLastRefresh();
        if (!lastRefresh.equals(current)) {
            synchronized (IDPMetaData.class) {
                current = idpMetadataResolver.getLastRefresh();
                if (lastRefresh.equals(current)) {
                    return;
                }
                refreshEntityId();
                try {
                    refreshPublicKey();
                    refreshSsoHttpPostUrl();
                } catch (ResolverException | ComponentInitializationException e) {
                    log.warn("refresh metadata failed:" + e.getMessage(), e);
                }
                lastRefresh = current;
            }
        }
    }

    private static void refreshEntityId() {
        entityId = idpMetadataResolver.iterator().next().getEntityID();
    }

    public static void refreshPublicKey() throws ComponentInitializationException, ResolverException {
        EntityDescriptor entityDescriptor = resolveEntityDescriptor();
        if (entityDescriptor == null) {
            throw new ResolverException("EntityDescriptor not found for entityId=" + entityId);
        }
        IDPSSODescriptor idpsso = entityDescriptor.getIDPSSODescriptor(SAML20_PROTOCOL);
        if (idpsso == null) {
            throw new ResolverException("IDPSSODescriptor not found for entityId=" + entityId);
        }

        for (KeyDescriptor kd : idpsso.getKeyDescriptors()) {
            if (kd.getUse() != null && kd.getUse() != UsageType.SIGNING) {
                continue;
            }
            List<X509Certificate> certs = extractCerts(kd);
            if (!certs.isEmpty()) {
                publicKey = certs.get(0).getPublicKey();
                return;
            }
        }
        throw new ResolverException("No SIGNING X509 certificate in IDPSSODescriptor");
    }

    private static EntityDescriptor resolveEntityDescriptor() throws ResolverException {
        CriteriaSet criteriaSet = new CriteriaSet();
        criteriaSet.add(new EntityIdCriterion(entityId));
        return idpMetadataResolver.resolveSingle(criteriaSet);
    }

    private static List<X509Certificate> extractCerts(KeyDescriptor kd) {
        List<X509Certificate> certs = new ArrayList<>();
        if (kd.getKeyInfo() == null) {
            return certs;
        }
        List<org.opensaml.xmlsec.signature.X509Data> x509Datas =
                kd.getKeyInfo().getX509Datas();
        if (x509Datas == null) {
            return certs;
        }
        for (org.opensaml.xmlsec.signature.X509Data data : x509Datas) {
            List<org.opensaml.xmlsec.signature.X509Certificate> xmlCerts =
                    data.getX509Certificates();
            if (xmlCerts == null) {
                continue;
            }
            for (org.opensaml.xmlsec.signature.X509Certificate xmlCert : xmlCerts) {
                String b64 = xmlCert.getValue();
                if (b64 == null || b64.isBlank()) {
                    continue;
                }
                try {
                    byte[] der = Base64.getMimeDecoder().decode(b64);
                    CertificateFactory cf = CertificateFactory.getInstance("X.509");
                    certs.add((X509Certificate) cf.generateCertificate(
                            new ByteArrayInputStream(der)));
                } catch (Exception e) {
                    log.warn("Failed to parse embedded X509 certificate", e);
                }
            }
        }
        return certs;
    }

    public static CloseableHttpClient createIgnoreSSLHttpClient() {
        try {
            SSLContext sslContext = new SSLContextBuilder().loadTrustMaterial(
                    (KeyStore) null,
                    new TrustStrategy() {
                        public boolean isTrusted(X509Certificate[] chain, String authType)
                                throws CertificateException {
                            return true;
                        }
                    }).build();
            SSLConnectionSocketFactory sslConnectionSocketFactory =
                    new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
            return HttpClients.custom()
                    .setSSLSocketFactory(sslConnectionSocketFactory)
                    .build();
        } catch (Exception e) {
            log.error("createIgnoreSSLHttpClient failed", e);
            return null;
        }
    }

    private static void refreshSsoHttpPostUrl() throws ResolverException {
        EntityDescriptor entityDescriptor = resolveEntityDescriptor();
        if (entityDescriptor == null) {
            throw new ResolverException("EntityDescriptor not found for entityId=" + entityId);
        }
        IDPSSODescriptor idpsso = entityDescriptor.getIDPSSODescriptor(SAML20_PROTOCOL);
        if (idpsso == null) {
            throw new ResolverException("IDPSSODescriptor not found for entityId=" + entityId);
        }
        for (SingleSignOnService sso : idpsso.getSingleSignOnServices()) {
            if (HTTP_POST_BINDING.equals(sso.getBinding())) {
                ssoHttpPostUri = sso.getLocation();
            }
        }
    }

    public static String getSsoHttpPostUri() {
        refresh();
        return ssoHttpPostUri;
    }

    public static PublicKey getPublicKey() {
        refresh();
        return publicKey;
    }

    public static String getEntityId() {
        refresh();
        return entityId;
    }

    static {
        map.put("cn", ADFS_CN_METADATA_URL);
        map.put("us", ADFS_US_METADATA_URL);
        lastRefresh = Instant.now();
        metaDataUrl = null;
    }
}