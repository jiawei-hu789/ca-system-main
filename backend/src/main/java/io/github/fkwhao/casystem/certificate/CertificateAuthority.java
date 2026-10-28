package io.github.fkwhao.casystem.certificate;

import io.github.fkwhao.casystem.certificate.mapper.CertificateMapper;
import io.github.fkwhao.casystem.common.ApiException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CertificateAuthority {
    private static final String ALIAS = "ca";
    private final Path storageRoot;
    private final String password;
    private final CertificateMapper mapper;
    private final PublicKeyParser parser;
    private final SecureRandom random = new SecureRandom();
    private final BouncyCastleProvider provider = new BouncyCastleProvider();

    public CertificateAuthority(@Value("${ca.storage.root:./data}") String root,
            @Value("${ca.keystore-password:}") String password, CertificateMapper mapper, PublicKeyParser parser) {
        this.storageRoot = Path.of(root).toAbsolutePath().normalize();
        this.password = password;
        this.mapper = mapper;
        this.parser = parser;
    }

    private record Authority(PrivateKey privateKey, X509Certificate certificate) {}
    public record Issued(String serial, byte[] der, Instant issuedAt, Instant notBefore, Instant notAfter) {}

    private BigInteger randomSerial() {
        BigInteger serial;
        do { serial = new BigInteger(159, random); } while (serial.signum() == 0);
        return serial;
    }

    private ContentSigner signer(PrivateKey key) throws OperatorCreationException {
        return new JcaContentSignerBuilder("SHA256withRSA").setProvider(provider).build(key);
    }

    private X509Certificate convert(X509v3CertificateBuilder builder, PrivateKey key)
            throws OperatorCreationException, GeneralSecurityException {
        return new JcaX509CertificateConverter().setProvider(provider).getCertificate(builder.build(signer(key)));
    }

    private Authority createAuthority() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072, random);
        KeyPair keyPair = generator.generateKeyPair();
        Instant now = Instant.now();
        X500Name subject = new X500Name("CN=Coursework CA Root");
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(subject, randomSerial(),
                Date.from(now.minus(1, ChronoUnit.MINUTES)), Date.from(now.plus(3650, ChronoUnit.DAYS)),
                subject, keyPair.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        builder.addExtension(Extension.subjectKeyIdentifier, false,
                new JcaX509ExtensionUtils().createSubjectKeyIdentifier(keyPair.getPublic()));
        X509Certificate root = convert(builder, keyPair.getPrivate());
        root.verify(keyPair.getPublic());
        return new Authority(keyPair.getPrivate(), root);
    }

    private Authority readAuthority(Path file) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(file)) { store.load(input, password.toCharArray()); }
        var key = store.getKey(ALIAS, password.toCharArray());
        var cert = store.getCertificate(ALIAS);
        if (!(key instanceof PrivateKey privateKey) || !(cert instanceof X509Certificate root)
                || root.getBasicConstraints() < 0) {
            throw new GeneralSecurityException("Invalid CA keystore");
        }
        root.checkValidity();
        root.verify(root.getPublicKey());
        Signature signature = Signature.getInstance("SHA256withRSA");
        byte[] challenge = new byte[32];
        random.nextBytes(challenge);
        signature.initSign(privateKey);
        signature.update(challenge);
        byte[] signed = signature.sign();
        signature.initVerify(root.getPublicKey());
        signature.update(challenge);
        if (!signature.verify(signed)) throw new GeneralSecurityException("CA key mismatch");
        return new Authority(privateKey, root);
    }

    private synchronized Authority loadOrCreate() {
        if (password == null || password.length() < 12) {
            throw ApiException.unavailable("请设置至少12个字符的CA_KEYSTORE_PASSWORD");
        }
        Path file = storageRoot.resolve("ca-root.p12");
        try {
            Files.createDirectories(storageRoot);
            if (Files.exists(file)) return readAuthority(file);
            Integer issuedCount = mapper.countIssued();
            if (issuedCount != null && issuedCount > 0) {
                throw ApiException.unavailable("已有签发记录但CA密钥库缺失，请恢复原密钥库");
            }
            Authority authority = createAuthority();
            KeyStore store = KeyStore.getInstance("PKCS12");
            store.load(null, password.toCharArray());
            store.setKeyEntry(ALIAS, authority.privateKey(), password.toCharArray(),
                    new java.security.cert.Certificate[] { authority.certificate() });
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            store.store(buffer, password.toCharArray());
            Path temporary = Files.createTempFile(storageRoot, ".ca-root-", ".tmp");
            try {
                Files.write(temporary, buffer.toByteArray(), StandardOpenOption.TRUNCATE_EXISTING);
                try { Files.move(temporary, file); }
                catch (FileAlreadyExistsException ex) { return readAuthority(file); }
            } finally {
                Files.deleteIfExists(temporary);
            }
            return authority;
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ApiException.unavailable("CA密钥库不可用，请检查密码和存储目录");
        }
    }

    public Issued issue(String applicantName, String publicKeyPem) {
        Authority authority = loadOrCreate();
        PublicKey publicKey = parser.parse(publicKeyPem);
        Instant now = Instant.now();
        Instant notBefore = now.minus(1, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
        Instant notAfter = now.plus(365, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant rootExpiry = authority.certificate().getNotAfter().toInstant();
        if (notAfter.isAfter(rootExpiry)) notAfter = rootExpiry;
        if (!notAfter.isAfter(now)) throw ApiException.unavailable("CA证书已到期");
        BigInteger serial = randomSerial();
        try {
            X500Name subject = new X500NameBuilder(BCStyle.INSTANCE).addRDN(BCStyle.CN, applicantName).build();
            X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    authority.certificate(), serial, Date.from(notBefore), Date.from(notAfter), subject, publicKey);
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
            builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
            JcaX509ExtensionUtils extensions = new JcaX509ExtensionUtils();
            builder.addExtension(Extension.subjectKeyIdentifier, false, extensions.createSubjectKeyIdentifier(publicKey));
            builder.addExtension(Extension.authorityKeyIdentifier, false,
                    extensions.createAuthorityKeyIdentifier(authority.certificate()));
            X509Certificate certificate = convert(builder, authority.privateKey());
            certificate.verify(authority.certificate().getPublicKey());
            return new Issued(serial.toString(16), certificate.getEncoded(), now, notBefore, notAfter);
        } catch (Exception ex) {
            throw ApiException.unavailable("证书签发失败");
        }
    }

    public X509Certificate rootCertificate() { return loadOrCreate().certificate(); }

    public static String toPem(byte[] der) {
        return "-----BEGIN CERTIFICATE-----\n"
                + Base64.getMimeEncoder(64, new byte[] { '\n' }).encodeToString(der)
                + "\n-----END CERTIFICATE-----\n";
    }
}
