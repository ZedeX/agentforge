package com.agent.gateway.util;

import com.agent.gateway.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * JWT 工具类（jjwt 0.12.5）。
 *
 * 关键 API 变更：
 *   - 旧版：Jwts.parser().setSigningKey(key).parseClaimsJws(token).getBody()
 *   - 0.12.x：Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload()
 */
public class JwtUtil {

    private final SecretKey signingKey;
    private final JwtProperties properties;

    public JwtUtil(JwtProperties properties) {
        this.properties = properties;
        byte[] keyBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成 JWT。
     *
     * @param userId   用户 ID
     * @param tenantId 租户 ID
     * @param roles    角色列表
     * @return JWT 字符串
     */
    public String generate(String userId, String tenantId, List<String> roles) {
        long now = System.currentTimeMillis();
        long ttlMillis = properties.getTtlMinutes() * 60L * 1000L;
        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(userId)
                .claim("tenantId", tenantId)
                .claim("roles", roles)
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlMillis))
                .signWith(signingKey)
                .compact();
    }

    /**
     * 解析并校验 JWT。
     *
     * @param token JWT 字符串
     * @return Claims 载荷
     * @throws JwtException 校验失败时抛出
     */
    public Claims parseAndValidate(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
