package io.github.fkwhao.casystem.certificateapplication.service;

import io.github.fkwhao.casystem.certificateapplication.dto.ReviewRequest;
import io.github.fkwhao.casystem.certificateapplication.vo.ApplicationView;
import io.github.fkwhao.casystem.certificateapplication.vo.FileData;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface CertificateApplicationService {
    ApplicationView create(String username, String applicantName, String email, String publicKeyPem, List<MultipartFile> files);
    ApplicationView get(String username, long id);
    List<ApplicationView> mine(String username, int limit, int offset);
    List<ApplicationView> adminList(String status, int limit, int offset);
    FileData evidence(String username, long evidenceId);
    ApplicationView review(String adminUsername, long id, ReviewRequest request);
}
