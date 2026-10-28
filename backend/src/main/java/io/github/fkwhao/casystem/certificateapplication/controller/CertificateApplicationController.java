package io.github.fkwhao.casystem.certificateapplication.controller;

import io.github.fkwhao.casystem.certificateapplication.dto.ReviewRequest;
import io.github.fkwhao.casystem.certificateapplication.vo.ApplicationView;
import io.github.fkwhao.casystem.certificateapplication.vo.FileData;

import io.github.fkwhao.casystem.certificateapplication.service.CertificateApplicationService;
import io.github.fkwhao.casystem.common.ApiResponse;

import java.security.Principal;
import java.util.List;
import org.springframework.http.ContentDisposition;
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
public class CertificateApplicationController {
    private final CertificateApplicationService applications;


    @PostMapping(value = "/applications", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ApplicationView> create(Principal principal,
            @RequestParam String applicantName, @RequestParam String email, @RequestParam String publicKeyPem,
            @RequestParam("evidenceFiles") List<MultipartFile> evidenceFiles) {
        return ApiResponse.success("申请提交成功",
                applications.create(principal.getName(), applicantName, email, publicKeyPem, evidenceFiles));
    }

    @GetMapping("/applications/mine")
    public ApiResponse<List<ApplicationView>> mine(Principal principal,
            @RequestParam(defaultValue = "20") int limit, @RequestParam(defaultValue = "0") int offset) {
        return ApiResponse.success("申请列表", applications.mine(principal.getName(), limit, offset));
    }

    @GetMapping("/applications/{id}")
    public ApiResponse<ApplicationView> get(Principal principal, @PathVariable long id) {
        return ApiResponse.success("申请详情", applications.get(principal.getName(), id));
    }

    @GetMapping("/admin/applications")
    public ApiResponse<List<ApplicationView>> adminList(@RequestParam(required = false) String status,
            @RequestParam(defaultValue = "20") int limit, @RequestParam(defaultValue = "0") int offset) {
        return ApiResponse.success("申请列表", applications.adminList(status, limit, offset));
    }

    @PostMapping("/admin/applications/{id}/review")
    public ApiResponse<ApplicationView> review(Principal principal, @PathVariable long id,
            @RequestBody ReviewRequest request) {
        return ApiResponse.success("审核完成", applications.review(principal.getName(), id, request));
    }

    @GetMapping("/evidence/{id}/download")
    public ResponseEntity<byte[]> download(Principal principal, @PathVariable long id) {
        FileData file = applications.evidence(principal.getName(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.filename(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(file.bytes());
    }
}
