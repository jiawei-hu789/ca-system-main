package io.github.fkwhao.casystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.fkwhao.casystem.common.ApiException;
import io.github.fkwhao.casystem.common.DataProtection;
import io.github.fkwhao.casystem.common.StorageStateMapper;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

@SpringBootTest
@AutoConfigureMockMvc
class BackendWorkflowTest {
    private static final String USER_PASSWORD = "user-password-123";
    private static final String ADMIN_PASSWORD = "admin-password-123";
    private static final String CA_PASSWORD = "ca-keystore-password-123";
    private static final Path TEST_DIRECTORY = createDirectory();
    private static final String STORAGE_KEY = randomKey();

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired StorageStateMapper storageStateMapper;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;

    private static Path createDirectory() {
        try { return Files.createTempDirectory("ca-system-test-"); }
        catch (Exception ex) { throw new ExceptionInInitializerError(ex); }
    }

    private static String randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + TEST_DIRECTORY.resolve("test.db").toString().replace((char) 92, '/'));
        registry.add("ca.storage.root", () -> TEST_DIRECTORY.toString());
        registry.add("ca.storage-key", () -> STORAGE_KEY);
        registry.add("ca.keystore-password", () -> CA_PASSWORD);
        registry.add("ca.admin-password", () -> ADMIN_PASSWORD);
    }

    private String json(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    private void register(String username) throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json(username, USER_PASSWORD))).andExpect(status().isOk());
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json(username, password))).andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String publicKeyPem() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        byte[] key = generator.generateKeyPair().getPublic().getEncoded();
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[] { '\n' }).encodeToString(key)
                + "\n-----END PUBLIC KEY-----\n";
    }

    private String field(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), path);
    }

    private MockMultipartFile pdf(String content) {
        return new MockMultipartFile("evidenceFiles", "proof.pdf", "application/pdf",
                content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void authenticationCsrfAndCompleteCertificateLifecycle() throws Exception {
        mvc.perform(get("/api/applications/mine")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json("alice", USER_PASSWORD))).andExpect(status().isForbidden());

        MvcResult csrfResponse = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        Cookie csrfCookie = csrfResponse.getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();
        String token = field(csrfResponse, "$.data.token");
        mvc.perform(post("/api/auth/register").cookie(csrfCookie).header("X-XSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON).content(json("alice", USER_PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("USER"));
        register("bob");
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json("alice", "wrong-password"))).andExpect(status().isUnauthorized());
        MockHttpSession alice = login("alice", USER_PASSWORD);
        MockHttpSession bob = login("bob", USER_PASSWORD);
        MockHttpSession admin = login("admin", ADMIN_PASSWORD);
        mvc.perform(get("/api/auth/me").session(alice)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("alice"));
        mvc.perform(get("/api/not-a-real-endpoint").session(alice)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.TEXT_PLAIN).content("invalid"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        mvc.perform(get("/api/admin/applications").session(alice)).andExpect(status().isForbidden());

        String publicKey = publicKeyPem();
        mvc.perform(multipart("/api/applications").file(pdf("not a pdf"))
                .param("applicantName", "Alice Example").param("email", "alice@example.test")
                .param("publicKeyPem", publicKey).session(alice).with(csrf()))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM applications", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM system_state", Integer.class)).isZero();

        byte[] pdfBytes = "%PDF-1.4\nexample\n%%EOF".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile proof = new MockMultipartFile("evidenceFiles", "proof.pdf", "application/pdf", pdfBytes);
        MvcResult application = mvc.perform(multipart("/api/applications").file(proof)
                .param("applicantName", "Alice Example").param("email", "alice@example.test")
                .param("publicKeyPem", publicKey).session(alice).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING")).andReturn();
        String applicationId = field(application, "$.data.id");
        String evidenceId = field(application, "$.data.evidenceFiles[0].id");
        byte[] storedName = jdbc.queryForObject("SELECT applicant_name FROM applications WHERE id=?", byte[].class, applicationId);
        byte[] storedEmail = jdbc.queryForObject("SELECT email FROM applications WHERE id=?", byte[].class, applicationId);
        byte[] storedEvidence = jdbc.queryForObject("SELECT content FROM evidence_files WHERE id=?", byte[].class, evidenceId);
        assertThat(storedName).isNotEqualTo("Alice Example".getBytes(StandardCharsets.UTF_8));
        assertThat(storedEmail).isNotEqualTo("alice@example.test".getBytes(StandardCharsets.UTF_8));
        assertThat(storedEvidence).isNotEqualTo(pdfBytes);
        mvc.perform(get("/api/evidence/{id}/download", evidenceId).session(bob)).andExpect(status().isNotFound());
        MvcResult evidence = mvc.perform(get("/api/evidence/{id}/download", evidenceId).session(alice))
                .andExpect(status().isOk()).andReturn();
        assertThat(evidence.getResponse().getContentAsByteArray()).isEqualTo(pdfBytes);
        mvc.perform(get("/api/applications/{id}", applicationId).session(bob)).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/applications").session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].applicantName").value("Alice Example"));

        mvc.perform(post("/api/admin/applications/{id}/review", applicationId).session(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVE\",\"comment\":\"verified\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("APPROVED"));
        mvc.perform(post("/api/admin/applications/{id}/review", applicationId).session(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isConflict());

        MvcResult listing = mvc.perform(get("/api/certificates/mine").session(alice)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("VALID")).andReturn();
        String serial = field(listing, "$.data[0].serial");
        mvc.perform(get("/api/certificates/{serial}/download", serial).session(bob)).andExpect(status().isNotFound());
        byte[] certificateBytes = mvc.perform(get("/api/certificates/{serial}/download", serial).session(alice))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        byte[] rootBytes = mvc.perform(get("/api/ca/certificate").session(alice))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        X509Certificate certificate = (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(certificateBytes));
        X509Certificate root = (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(rootBytes));
        certificate.verify(root.getPublicKey());
        byte[] submittedKey = Base64.getMimeDecoder().decode(publicKey.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", ""));
        assertThat(certificate.getPublicKey().getEncoded()).isEqualTo(submittedKey);
        assertThat(certificate.getBasicConstraints()).isNegative();
        assertThat(root.getBasicConstraints()).isGreaterThanOrEqualTo(0);

        MockMultipartFile uploadedCertificate = new MockMultipartFile("certificate", "cert.pem",
                "application/x-pem-file", certificateBytes);
        MockMultipartFile invalidCertificate = new MockMultipartFile("certificate", "invalid.pem",
                "application/x-pem-file", "invalid".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/certificates/verify").file(invalidCertificate).session(alice).with(csrf()))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/certificates/verify").file(uploadedCertificate).session(alice).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.revocationStatus").value("GOOD"));
        mvc.perform(post("/api/certificates/{serial}/revocation-requests", serial).session(alice).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"private key lost\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING"));
        mvc.perform(post("/api/certificates/{serial}/revocation-requests", serial).session(alice).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"duplicate\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/admin/certificates/{serial}/revoke", serial).session(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"private key lost\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REVOKED"))
                .andExpect(jsonPath("$.data.revocationRequests[0].status").value("COMPLETED"));
        mvc.perform(multipart("/api/certificates/verify").file(uploadedCertificate).session(alice).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.revocationStatus").value("REVOKED"));
        MockMultipartFile uploadedRoot = new MockMultipartFile("certificate", "ca.pem", "application/x-pem-file", rootBytes);
        mvc.perform(multipart("/api/certificates/verify").file(uploadedRoot).session(alice).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.revocationStatus").value("UNKNOWN"));
        mvc.perform(post("/api/admin/certificates/{serial}/revoke", serial).session(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"again\"}"))
                .andExpect(status().isConflict());

        assertThatThrownBy(() -> new DataProtection(randomKey(), storageStateMapper).encryptText("wrong-key-write"))
                .isInstanceOf(ApiException.class).hasMessageContaining("CA_STORAGE_KEY");
        MvcResult secondApplication = mvc.perform(multipart("/api/applications").file(pdf("%PDF-1.4\nproof"))
                .param("applicantName", "Bob Example").param("email", "bob@example.test")
                .param("publicKeyPem", publicKey).session(bob).with(csrf()))
                .andExpect(status().isOk()).andReturn();
        String secondId = field(secondApplication, "$.data.id");
        Path rootFile = TEST_DIRECTORY.resolve("ca-root.p12");
        Path backupFile = TEST_DIRECTORY.resolve("ca-root-test-backup.p12");
        Files.move(rootFile, backupFile);
        try {
            mvc.perform(post("/api/admin/applications/{id}/review", secondId).session(admin).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVE\"}"))
                    .andExpect(status().isServiceUnavailable());
            assertThat(Files.exists(rootFile)).isFalse();
            mvc.perform(get("/api/applications/{id}", secondId).session(bob))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING"));
        } finally {
            Files.move(backupFile, rootFile);
        }
        mvc.perform(post("/api/admin/applications/{id}/review", secondId).session(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"REJECT\",\"comment\":\"证件不符\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REJECTED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM certificates", Integer.class)).isEqualTo(1);
        mvc.perform(post("/api/auth/logout").session(alice).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(alice)).andExpect(status().isUnauthorized());
    }

    @Test
    void browserCanUseReturnedCsrfCookieAndSession() throws Exception {
        MvcResult initial = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        Cookie initialCookie = initial.getResponse().getCookie("XSRF-TOKEN");
        String initialToken = field(initial, "$.data.token");
        assertThat(initialCookie).isNotNull();
        mvc.perform(post("/api/auth/register").cookie(initialCookie).header("X-XSRF-TOKEN", initialToken)
                .contentType(MediaType.APPLICATION_JSON).content(json("csrf_user", USER_PASSWORD)))
                .andExpect(status().isOk());
        MvcResult loggedIn = mvc.perform(post("/api/auth/login").cookie(initialCookie).header("X-XSRF-TOKEN", initialToken)
                .contentType(MediaType.APPLICATION_JSON).content(json("csrf_user", USER_PASSWORD)))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) loggedIn.getRequest().getSession(false);
        assertThat(session).isNotNull();
        MvcResult refreshed = mvc.perform(get("/api/auth/csrf").session(session)).andExpect(status().isOk()).andReturn();
        Cookie refreshedCookie = refreshed.getResponse().getCookie("XSRF-TOKEN");
        String refreshedToken = field(refreshed, "$.data.token");
        assertThat(refreshedCookie).isNotNull();
        mvc.perform(post("/api/auth/logout").session(session).cookie(refreshedCookie)
                .header("X-XSRF-TOKEN", refreshedToken)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void openApiDocumentsAllImplementedRoutes() throws Exception {
        Path spec = Path.of("..", "docs", "openapi.yaml");
        if (!Files.isRegularFile(spec)) spec = Path.of("docs", "openapi.yaml");
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Map<?, ?> document;
        try (var input = Files.newInputStream(spec)) {
            document = new Yaml(new SafeConstructor(options)).load(input);
        }
        assertThat(document.get("openapi")).isEqualTo("3.0.3");
        assertReferencesResolve(document, document);
        Map<?, ?> paths = (Map<?, ?>) document.get("paths");
        Set<String> documented = new TreeSet<>();
        paths.forEach((path, value) -> ((Map<?, ?>) value).forEach((method, ignored) -> {
            String verb = method.toString().toUpperCase(java.util.Locale.ROOT);
            if (Set.of("GET", "POST", "PUT", "PATCH", "DELETE").contains(verb)) {
                documented.add(verb + " " + path);
            }
        }));
        Set<String> actual = new TreeSet<>();
        mappings.getHandlerMethods().forEach((mapping, handler) -> {
            if (handler.getBeanType().getPackageName().startsWith("io.github.fkwhao.casystem")) {
                mapping.getPatternValues().forEach(path -> mapping.getMethodsCondition().getMethods()
                        .forEach(method -> actual.add(method.name() + " " + path)));
            }
        });
        // Spring Security handles logout before MVC, so it does not appear in handler mappings.
        actual.add("POST /api/auth/logout");
        assertThat(documented).containsExactlyInAnyOrderElementsOf(actual);
    }

    private void assertReferencesResolve(Object root, Object node) {
        if (node instanceof Map<?, ?> map) {
            if (map.get("$ref") instanceof String ref) {
                assertThat(ref).startsWith("#/");
                Object current = root;
                for (String part : ref.substring(2).split("/")) {
                    assertThat(current).isInstanceOf(Map.class);
                    Map<?, ?> parent = (Map<?, ?>) current;
                    String key = part.replace("~1", "/").replace("~0", "~");
                    assertThat(parent.containsKey(key)).as("OpenAPI reference %s", ref).isTrue();
                    current = parent.get(key);
                }
            }
            map.values().forEach(value -> assertReferencesResolve(root, value));
        } else if (node instanceof Iterable<?> list) {
            list.forEach(value -> assertReferencesResolve(root, value));
        }
    }
}
