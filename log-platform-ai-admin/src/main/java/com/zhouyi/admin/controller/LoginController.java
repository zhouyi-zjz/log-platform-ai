package com.zhouyi.admin.controller;

import com.zhouyi.common.domain.AjaxResult;
import com.zhouyi.common.domain.model.LoginBody;
import com.zhouyi.common.domain.model.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/monitor/api/auth")
public class LoginController {

    private static final Logger log = LoggerFactory.getLogger(LoginController.class);

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

    public LoginController(
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            SessionAuthenticationStrategy sessionAuthenticationStrategy) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
    }

    @PostMapping("/login")
    public ResponseEntity<AjaxResult<UserView>> login(
            @Valid @RequestBody LoginBody loginBody,
            HttpServletRequest request,
            HttpServletResponse response) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            loginBody.getUsername().trim(), loginBody.getPassword()));

            request.getSession(true);
            sessionAuthenticationStrategy.onAuthentication(authentication, request, response);

            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(authentication);
            SecurityContextHolder.setContext(securityContext);
            securityContextRepository.saveContext(securityContext, request, response);

            LoginUser loginUser = (LoginUser) authentication.getPrincipal();
            return ResponseEntity.ok(AjaxResult.success("登录成功", UserView.from(loginUser)));
        } catch (InternalAuthenticationServiceException exception) {
            log.error("用户认证服务访问失败", exception);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(AjaxResult.error(HttpStatus.SERVICE_UNAVAILABLE.value(),
                            "认证服务暂不可用，请检查数据库连接"));
        } catch (AuthenticationException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(AjaxResult.error(HttpStatus.UNAUTHORIZED.value(), "用户名或密码错误"));
        }
    }

    @GetMapping("/me")
    public AjaxResult<UserView> currentUser(Authentication authentication) {
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        return AjaxResult.success(UserView.from(loginUser));
    }

    @GetMapping("/csrf")
    public AjaxResult<CsrfView> csrf(CsrfToken csrfToken) {
        return AjaxResult.success(new CsrfView(csrfToken.getHeaderName(), csrfToken.getToken()));
    }

    public record UserView(Long userId, String username, String displayName, boolean admin) {

        private static UserView from(LoginUser loginUser) {
            return new UserView(
                    loginUser.getUserId(),
                    loginUser.getUsername(),
                    loginUser.getDisplayName(),
                    loginUser.isAdmin());
        }
    }

    public record CsrfView(String headerName, String token) {
    }
}
