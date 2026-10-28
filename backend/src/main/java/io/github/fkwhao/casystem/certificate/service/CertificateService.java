package io.github.fkwhao.casystem.certificate.service;

import io.github.fkwhao.casystem.certificate.dto.ReasonRequest;
import io.github.fkwhao.casystem.certificate.vo.CertificateView;
import io.github.fkwhao.casystem.certificate.vo.RevocationView;
import io.github.fkwhao.casystem.certificate.vo.VerificationView;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface CertificateService {
    List<CertificateView> mine(String username, int limit, int offset);
    List<CertificateView> adminList(int limit, int offset);
    CertificateView get(String username, String serial);
    byte[] download(String username, String serial);
    RevocationView requestRevocation(String username, String serial, ReasonRequest request);
    CertificateView revoke(String username, String serial, ReasonRequest request);
    VerificationView verify(MultipartFile file);
    byte[] rootPem();

}
