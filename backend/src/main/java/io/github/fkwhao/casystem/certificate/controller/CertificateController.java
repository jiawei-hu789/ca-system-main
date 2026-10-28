package io.github.fkwhao.casystem.certificate.controller;

import io.github.fkwhao.casystem.certificate.dto.ReasonRequest;
import io.github.fkwhao.casystem.certificate.vo.CertificateView;
import io.github.fkwhao.casystem.certificate.vo.RevocationView;
import io.github.fkwhao.casystem.certificate.vo.VerificationView;

import io.github.fkwhao.casystem.certificate.service.CertificateService;
import io.github.fkwhao.casystem.common.ApiResponse;

import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@lombok.RequiredArgsConstructor
public class CertificateController {
    private final CertificateService certificates;


    @GetMapping("/certificates/mine")
    public ApiResponse<List<CertificateView>> mine(Principal principal,
            @RequestParam(defaultValue = "20") int limit, @RequestParam(defaultValue = "0") int offset) {
        return ApiResponse.success("证书列表", certificates.mine(principal.getName(), limit, offset));
    }

    @GetMapping("/admin/certificates")
    public ApiResponse<List<CertificateView>> adminList(
            @RequestParam(defaultValue = "20") int limit, @RequestParam(defaultValue = "0") int offset) {
        return ApiResponse.success("证书列表", certificates.adminList(limit, offset));
    }

    @GetMapping("/certificates/{serial}")
    public ApiResponse<CertificateView> get(Principal principal, @PathVariable String serial) {
        return ApiResponse.success("证书详情", certificates.get(principal.getName(), serial));
    }

    @GetMapping("/certificates/{serial}/download")
    public ResponseEntity<byte[]> download(Principal principal, @PathVariable String serial) {
        byte[] pem = certificates.download(principal.getName(), serial);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/x-pem-file"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"certificate-" + serial.toLowerCase(java.util.Locale.ROOT) + ".pem\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff").body(pem);
    }

    @GetMapping("/ca/certificate")
    public ResponseEntity<byte[]> rootCertificate() {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/x-pem-file"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ca-root.pem\"")
                .header("X-Content-Type-Options", "nosniff").body(certificates.rootPem());
    }

    @PostMapping("/certificates/{serial}/revocation-requests")
    public ApiResponse<RevocationView> requestRevocation(Principal principal,
            @PathVariable String serial, @RequestBody ReasonRequest request) {
        return ApiResponse.success("吊销申请已提交", certificates.requestRevocation(principal.getName(), serial, request));
    }

    @PostMapping("/admin/certificates/{serial}/revoke")
    public ApiResponse<CertificateView> revoke(Principal principal, @PathVariable String serial,
            @RequestBody ReasonRequest request) {
        return ApiResponse.success("证书已吊销", certificates.revoke(principal.getName(), serial, request));
    }

    @PostMapping(value = "/certificates/verify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<VerificationView> verify(@RequestParam("certificate") MultipartFile file) {
        return ApiResponse.success("验证完成", certificates.verify(file));
    }
}
