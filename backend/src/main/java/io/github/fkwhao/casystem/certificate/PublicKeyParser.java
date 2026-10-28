package io.github.fkwhao.casystem.certificate;

import io.github.fkwhao.casystem.common.ApiException;
import java.io.IOException;
import java.io.StringReader;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.springframework.stereotype.Component;

@Component
public class PublicKeyParser {
    public PublicKey parse(String pem) {
        if (pem == null || pem.length() > 8192) throw ApiException.badRequest("公钥格式无效");
        try (PemReader reader = new PemReader(new StringReader(pem))) {
            PemObject object = reader.readPemObject();
            if (object == null || !"PUBLIC KEY".equals(object.getType()) || reader.readPemObject() != null) {
                throw ApiException.badRequest("请提交PEM格式的SPKI公钥");
            }
            PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(object.getContent()));
            if (!(key instanceof RSAPublicKey rsa)
                    || rsa.getModulus().bitLength() < 2048 || rsa.getModulus().bitLength() > 4096) {
                throw ApiException.badRequest("RSA公钥长度须为2048至4096位");
            }
            return key;
        } catch (IOException | GeneralSecurityException | IllegalArgumentException ex) {
            throw ApiException.badRequest("RSA公钥格式无效");
        }
    }
}
