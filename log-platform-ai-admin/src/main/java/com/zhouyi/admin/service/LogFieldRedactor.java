package com.zhouyi.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

/** 查询响应字段脱敏器，避免密码、Token 和 Cookie 随日志详情返回给前端。 */
@Service
public class LogFieldRedactor {

    private static final Set<String> SENSITIVE_NAMES = Set.of(
            "password", "passwd", "secret", "token", "access_token", "refresh_token",
            "authorization", "cookie", "set-cookie", "api-key", "apikey");
    /** 返回深拷贝后的脱敏结果，不修改 Elasticsearch 客户端返回对象。 */
    public JsonNode redact(JsonNode source) {
        if (source == null || source.isNull()) {
            return source;
        }
        JsonNode copy = source.deepCopy();
        redactInPlace(copy);
        return copy;
    }

    /** 递归遍历对象和数组，按字段名而不是字段值判断敏感数据。 */
    private void redactInPlace(JsonNode node) {
        if (node instanceof ObjectNode object) {
            object.fields().forEachRemaining(entry -> {
                if (isSensitive(entry.getKey())) {
                    object.put(entry.getKey(), "[REDACTED]");
                } else {
                    redactInPlace(entry.getValue());
                }
            });
        } else if (node instanceof ArrayNode array) {
            array.forEach(this::redactInPlace);
        }
    }

    /** 允许点号路径和大小写差异，覆盖 attributes 下的常见凭据字段。 */
    private boolean isSensitive(String name) {
        String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
        String leaf = normalized.substring(normalized.lastIndexOf('.') + 1)
                .replace('-', '_');
        return SENSITIVE_NAMES.contains(leaf) || leaf.endsWith("_token") || leaf.endsWith("_secret");
    }
}
