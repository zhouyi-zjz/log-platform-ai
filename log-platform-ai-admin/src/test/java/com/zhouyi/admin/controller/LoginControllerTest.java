package com.zhouyi.admin.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminCanLoginAndReadCurrentUser() throws Exception {
        MvcResult loginResult = login("admin", "admin@2024")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.admin").value(true))
                .andReturn();
        MockHttpSession session = sessionFrom(loginResult);

        mockMvc.perform(get("/monitor/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.displayName").value("系统管理员"))
                .andExpect(jsonPath("$.data.admin").value(true));
    }

    @Test
    void browserCanLoginWithTokenReturnedByCsrfEndpoint() throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/monitor/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode csrfBody = objectMapper.readTree(csrfResult.getResponse().getContentAsByteArray());
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        MockHttpSession csrfSession = (MockHttpSession) csrfResult.getRequest().getSession(false);
        MockHttpServletRequestBuilder loginRequest = post("/monitor/api/auth/login")
                .header(csrfBody.path("data").path("headerName").asText(),
                        csrfBody.path("data").path("token").asText())
                .contentType("application/json")
                .content("{\"username\":\"admin\",\"password\":\"admin@2024\"}");
        if (csrfCookie != null) {
            loginRequest.cookie(csrfCookie);
        }
        if (csrfSession != null) {
            loginRequest.session(csrfSession);
        }

        assertThat(csrfCookie != null || csrfSession != null).isTrue();
        mockMvc.perform(loginRequest)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("admin"));
    }

    @Test
    void viewerIsNotAllowedToAccessAdminApi() throws Exception {
        MockHttpSession session = sessionFrom(login("viewer", "viewer@2024").andReturn());

        mockMvc.perform(get("/monitor/api/admin/settings").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void invalidCredentialsAreRejected() throws Exception {
        login("admin", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    void loginWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/monitor/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"admin\",\"password\":\"admin@2024\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void disabledUserCannotLogin() throws Exception {
        login("disabled", "viewer@2024")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/monitor/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("请先登录"));
    }

    @Test
    void logoutInvalidatesSession() throws Exception {
        MockHttpSession session = sessionFrom(login("viewer", "viewer@2024").andReturn());

        mockMvc.perform(post("/monitor/api/auth/logout")
                        .session(session)
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("退出成功"));

        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void logoutWithoutCsrfTokenIsRejected() throws Exception {
        MockHttpSession session = sessionFrom(login("viewer", "viewer@2024").andReturn());

        mockMvc.perform(post("/monitor/api/auth/logout").session(session))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions login(String username, String password)
            throws Exception {
        String body = """
                {"username":"%s","password":"%s"}
                """.formatted(username, password);
        return mockMvc.perform(post("/monitor/api/auth/login")
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType("application/json")
                .content(body));
    }

    private MockHttpSession sessionFrom(MvcResult result) {
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
