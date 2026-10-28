package io.github.fkwhao.casystem.certificateapplication.service.impl;

import io.github.fkwhao.casystem.common.Pagination;
import io.github.fkwhao.casystem.auth.entity.Account;
import io.github.fkwhao.casystem.certificateapplication.dto.ReviewRequest;
import io.github.fkwhao.casystem.certificateapplication.entity.ApplicationRow;
import io.github.fkwhao.casystem.certificateapplication.entity.EvidenceMetaRow;
import io.github.fkwhao.casystem.certificateapplication.entity.EvidenceRow;
import io.github.fkwhao.casystem.certificateapplication.vo.ApplicationView;
import io.github.fkwhao.casystem.certificateapplication.vo.EvidenceView;
import io.github.fkwhao.casystem.certificateapplication.vo.FileData;

import io.github.fkwhao.casystem.certificateapplication.service.CertificateApplicationService;

import io.github.fkwhao.casystem.auth.service.AccountService;
import io.github.fkwhao.casystem.certificate.CertificateAuthority;
import io.github.fkwhao.casystem.certificate.PublicKeyParser;
import io.github.fkwhao.casystem.certificate.mapper.CertificateMapper;
import io.github.fkwhao.casystem.certificateapplication.mapper.CertificateApplicationMapper;
import io.github.fkwhao.casystem.common.ApiException;
import io.github.fkwhao.casystem.common.DataProtection;
import io.github.fkwhao.casystem.common.service.AuditService;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@lombok.RequiredArgsConstructor
public class CertificateApplicationServiceImpl implements CertificateApplicationService {
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024L;
    private final CertificateApplicationMapper mapper;
    private final CertificateMapper certificateMapper;
    private final AccountService accounts;
    private final DataProtection protection;
    private final PublicKeyParser publicKeys;
    private final CertificateAuthority authority;
    private final AuditService audit;




    private ApplicationRow find(long id) {
        return mapper.findById(id)
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("申请不存在"));
    }

    private Account authorize(String username, ApplicationRow application) {
        Account account = accounts.requireAccount(username);
        if (!account.role().equals("ADMIN") && account.id() != application.userId()) {
            throw ApiException.notFound("申请不存在");
        }
        return account;
    }

    private List<EvidenceView> evidenceFor(long id) {
        List<EvidenceMetaRow> rows = mapper.findEvidenceMetadata(id);
        return rows.stream().map(row -> new EvidenceView(Long.toString(row.id()),
                protection.decryptText(row.filename()), row.contentType(), row.sizeBytes())).toList();
    }

    private ApplicationView view(ApplicationRow row) {
        return new ApplicationView(Long.toString(row.id()), Long.toString(row.userId()),
                protection.decryptText(row.applicantName()), protection.decryptText(row.email()),
                row.publicKeyPem(), row.status(), row.createdAt(), row.reviewedAt(),
                row.reviewComment() == null ? null : protection.decryptText(row.reviewComment()),
                evidenceFor(row.id()));
    }

    private String validatedFilename(String input) {
        if (input == null) throw ApiException.badRequest("证明材料缺少文件名");
        String filename = input.replace((char) 92, '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1);
        if (filename.isBlank() || filename.length() > 128 || filename.chars().anyMatch(Character::isISOControl)) {
            throw ApiException.badRequest("证明材料文件名无效");
        }
        return filename;
    }

    private String detectType(String filename, byte[] bytes) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf") && bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P'
                && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-') return "application/pdf";
        if (lower.endsWith(".png") && bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 'P'
                && bytes[2] == 'N' && bytes[3] == 'G' && bytes[4] == 13 && bytes[5] == 10
                && bytes[6] == 26 && bytes[7] == 10) return "image/png";
        if ((lower.endsWith(".jpg") || lower.endsWith(".jpeg")) && bytes.length >= 3
                && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff) return "image/jpeg";
        throw ApiException.badRequest("证明材料仅支持真实的PDF、PNG或JPEG文件");
    }

    @Transactional
    public ApplicationView create(String username, String applicantName, String email,
            String publicKeyPem, List<MultipartFile> files) {
        Account account = accounts.requireAccount(username);
        if (!account.role().equals("USER")) throw ApiException.badRequest("请使用普通用户账号提交申请");
        if (applicantName == null || applicantName.isBlank() || applicantName.trim().length() > 100
                || applicantName.chars().anyMatch(Character::isISOControl)) {
            throw ApiException.badRequest("申请人姓名须为1至100个字符且不包含控制字符");
        }
        if (email == null || email.isBlank() || email.trim().length() > 254
                || !email.trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw ApiException.badRequest("邮箱格式无效");
        }
        publicKeys.parse(publicKeyPem);
        if (files == null || files.isEmpty() || files.size() > 3) {
            throw ApiException.badRequest("请提交1至3份证明材料");
        }
        Long id = mapper.insert(account.id(),
                protection.encryptText(applicantName.trim()), protection.encryptText(email.trim()),
                publicKeyPem.trim(), Instant.now().toString());
        for (MultipartFile file : files) {
            if (file.isEmpty() || file.getSize() > MAX_FILE_SIZE) throw ApiException.badRequest("每份材料须大于0且不超过5MB");
            String filename = validatedFilename(file.getOriginalFilename());
            try {
                byte[] bytes = file.getBytes();
                String type = detectType(filename, bytes);
                mapper.insertEvidence(id, protection.encryptText(filename), type, bytes.length, protection.encrypt(bytes));
            } catch (IOException ex) {
                throw ApiException.badRequest("读取证明材料失败");
            }
        }
        audit.record(account.id(), "APPLICATION_SUBMITTED", "APPLICATION", id.toString());
        return view(find(id));
    }

    public ApplicationView get(String username, long id) {
        ApplicationRow application = find(id);
        authorize(username, application);
        return view(application);
    }

    public List<ApplicationView> mine(String username, int limit, int offset) {
        Account account = accounts.requireAccount(username);
        Pagination.check(limit, offset);
        return mapper.findByUser(account.id(), limit, offset).stream().map(this::view).toList();
    }

    public List<ApplicationView> adminList(String status, int limit, int offset) {
        Pagination.check(limit, offset);
        if (status != null && !List.of("PENDING", "APPROVED", "REJECTED").contains(status)) {
            throw ApiException.badRequest("申请状态无效");
        }
        List<ApplicationRow> rows = mapper.findAll(status, limit, offset);
        return rows.stream().map(this::view).toList();
    }



    public FileData evidence(String username, long evidenceId) {
        var records = mapper.findEvidence(evidenceId);
        if (records.isEmpty()) throw ApiException.notFound("材料不存在");
        EvidenceRow record = records.get(0);
        authorize(username, find(record.applicationId()));
        return new FileData(protection.decryptText(record.name()), record.type(), protection.decrypt(record.content()));
    }

@Transactional
    public ApplicationView review(String adminUsername, long id, ReviewRequest request) {
        Account admin = accounts.requireAccount(adminUsername);
        if (!admin.role().equals("ADMIN")) throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "FORBIDDEN", "仅管理员可以审核");
        ApplicationRow application = find(id);
        if (!application.status().equals("PENDING")) throw ApiException.conflict("该申请已审核");
        if (request == null || request.decision() == null || !List.of("APPROVE", "REJECT").contains(request.decision())) {
            throw ApiException.badRequest("审核结果须为APPROVE或REJECT");
        }
        String comment = request.comment() == null ? "" : request.comment().trim();
        if (comment.length() > 1000) throw ApiException.badRequest("审核意见不能超过1000个字符");
        if (request.decision().equals("REJECT") && comment.isEmpty()) throw ApiException.badRequest("驳回申请时须填写审核意见");
        String now = Instant.now().toString();
        if (request.decision().equals("APPROVE")) {
            CertificateAuthority.Issued issued = authority.issue(
                    protection.decryptText(application.applicantName()), application.publicKeyPem());
            certificateMapper.insert(issued, id, application.userId());
        }
        int updated = mapper.reviewIfPending(id, request.decision().equals("APPROVE") ? "APPROVED" : "REJECTED",
                now, admin.id(), protection.encryptText(comment));
        if (updated != 1) throw ApiException.conflict("申请状态已发生变化");
        audit.record(admin.id(), request.decision().equals("APPROVE") ? "APPLICATION_APPROVED" : "APPLICATION_REJECTED",
                "APPLICATION", Long.toString(id));
        return view(find(id));
    }
}
