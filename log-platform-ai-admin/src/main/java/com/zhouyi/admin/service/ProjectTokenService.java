package com.zhouyi.admin.service;

import com.zhouyi.admin.repository.ProjectTokenRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 项目级采集凭据服务。
 *
 * <p>Token 使用 256 位随机数，摘要比较采用定长字节比较，避免把不同项目的
 * 全局 Token 混用，也不会在日志或数据库中保存明文凭据。</p>
 */
@Service
public class ProjectTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final ProjectTokenRepository repository;

    public ProjectTokenService(ProjectTokenRepository repository) {
        this.repository = repository;
    }

    /** 校验 Token 是否属于指定的启用项目。 */
    public boolean isValid(String projectId, String suppliedToken) {
        if (projectId == null || projectId.isBlank() || suppliedToken == null || suppliedToken.isBlank()) {
            return false;
        }
        byte[] suppliedHash = digest(suppliedToken);
        return repository.findEnabledTokenHash(projectId)
                .map(this::decodeHash)
                .filter(expectedHash -> expectedHash.length == suppliedHash.length)
                .map(expectedHash -> MessageDigest.isEqual(expectedHash, suppliedHash))
                .orElse(false);
    }

    /** 判断项目是否已经启用独立 Token；启用后不再回退到全局兼容 Token。 */
    public boolean hasConfiguredToken(String projectId) {
        return projectId != null && repository.findEnabledTokenHash(projectId).isPresent();
    }

    /** 为项目轮换采集 Token，返回值只应直接交给管理员或密钥管理系统。 */
    public String rotate(String projectId) {
        if (projectId == null || !projectId.matches("[a-zA-Z0-9_.-]{1,64}")) {
            throw new IllegalArgumentException("项目 ID 格式无效");
        }
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        if (!repository.updateTokenHash(projectId, hex(digest(token)))) {
            throw new ProjectNotFoundException("项目不存在或已停用");
        }
        return token;
    }

    /** 计算 Token 的 SHA-256 摘要。 */
    static byte[] digest(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", exception);
        }
    }

    /** 解析数据库保存的十六进制摘要，非法数据按无效 Token 处理。 */
    private byte[] decodeHash(String value) {
        if (value == null || !value.matches("[0-9a-fA-F]{64}")) {
            return new byte[0];
        }
        byte[] result = new byte[32];
        for (int index = 0; index < result.length; index++) {
            result[index] = (byte) Integer.parseInt(value.substring(index * 2, index * 2 + 2), 16);
        }
        return result;
    }

    /** 将摘要编码为便于排查数据库内容的固定长度十六进制字符串。 */
    private static String hex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) {
            result.append(String.format("%02x", item));
        }
        return result.toString();
    }

    /** 项目不存在或已停用时由 Controller 映射为 404。 */
    public static final class ProjectNotFoundException extends IllegalArgumentException {
        public ProjectNotFoundException(String message) {
            super(message);
        }
    }
}
