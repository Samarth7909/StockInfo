package com.indira.opsconsole.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies that role-based access control is enforced at the API layer.
 * Uses an isolated in-memory SQLite DB (unique per test class).
 *
 * Scenarios:
 *  - No token → 401 on protected route
 *  - SUPPORT role → 403 on POST /api/imports
 *  - AUDITOR role → 403 on POST /api/cases/:id/notes
 *  - AUDITOR role → 403 on POST /api/cases/:id/transition
 *  - SUPPORT can GET /api/cases (read allowed, masking applied server-side)
 *  - INVESTIGATOR → 403 when trying to RESOLVE (OPS_LEAD only)
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:sqlite:file:test-access?mode=memory&cache=shared&uri=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccessDenialTest {

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper mapper;

    // ── Unauthenticated ───────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/cases without token returns 401")
    void unauthenticated_cases_returns401() throws Exception {
        mockMvc.perform(get("/api/cases"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/imports without token returns 401")
    void unauthenticated_imports_returns401() throws Exception {
        mockMvc.perform(post("/api/imports"))
            .andExpect(status().isUnauthorized());
    }

    // ── SUPPORT role restrictions ─────────────────────────────────────────────

    @Test
    @DisplayName("SUPPORT cannot POST /api/imports (OPS_LEAD only route)")
    void support_cannotImport() throws Exception {
        String token = loginToken("support1", "support123");
        mockMvc.perform(post("/api/imports")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.MULTIPART_FORM_DATA))
            .andExpect(status().isForbidden());
    }

    // ── AUDITOR role restrictions ─────────────────────────────────────────────

    @Test
    @DisplayName("AUDITOR cannot POST notes (read-only role)")
    void auditor_cannotAddNote() throws Exception {
        String token = loginToken("auditor1", "audit123");
        mockMvc.perform(post("/api/cases/nonexistent-id/notes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("body", "test note"))))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AUDITOR cannot POST transition (read-only role)")
    void auditor_cannotTransition() throws Exception {
        String token = loginToken("auditor1", "audit123");
        mockMvc.perform(post("/api/cases/nonexistent-id/transition")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                    "targetState", "INVESTIGATING",
                    "reason", "test",
                    "expectedVersion", 1))))
            .andExpect(status().isForbidden());
    }

    // ── Cross-role: SUPPORT reads cases ──────────────────────────────────────

    @Test
    @DisplayName("SUPPORT can GET /api/cases (read allowed; masking applied server-side)")
    void support_canReadCases() throws Exception {
        String token = loginToken("support1", "support123");
        mockMvc.perform(get("/api/cases")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
    }

    // ── INVESTIGATOR cannot RESOLVE ───────────────────────────────────────────

    @Test
    @DisplayName("INVESTIGATOR gets 403 when trying to RESOLVE (OPS_LEAD only)")
    void investigator_cannotResolve() throws Exception {
        String investorToken = loginToken("invest1", "invest123");
        MvcResult list = mockMvc.perform(get("/api/cases?state=OPEN")
                .header("Authorization", "Bearer " + investorToken))
            .andExpect(status().isOk())
            .andReturn();

        String body = list.getResponse().getContentAsString();
        if (body.contains("\"totalItems\":0") || !body.contains("\"id\"")) return;

        var items = mapper.readTree(body).get("items");
        String caseId = items.get(0).get("id").asText();
        int version   = items.get(0).get("version").asInt();

        mockMvc.perform(post("/api/cases/" + caseId + "/transition")
                .header("Authorization", "Bearer " + investorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                    "targetState", "RESOLVED",
                    "reason", "trying to resolve as investigator",
                    "expectedVersion", version))))
            .andExpect(status().isForbidden());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String loginToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                    "username", username, "password", password))))
            .andExpect(status().isOk())
            .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString())
            .get("token").asText();
    }
}
