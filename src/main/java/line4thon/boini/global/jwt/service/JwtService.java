package line4thon.boini.global.jwt.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
  private final Key key;

  public JwtService(@Value("${app.jwt.secret}") String secret) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes()); // 최소 256bit
  }

  // 발표자/청중 공통 발급
  public String issueJoinToken(String roomId, String role, Duration ttl) {
    var now = Instant.now();
    return Jwts.builder()
        .setId(UUID.randomUUID().toString())       // jti(토큰 고유 식별자)
        .setIssuedAt(Date.from(now))               // 발급 시각
        .setExpiration(Date.from(now.plus(ttl)))   // 만료 시각
        .claim("roomId", roomId)                   // 커스텀 데이터(방 ID)
        .claim("role", role)                       // 커스텀 데이터(역할)
        .signWith(key, SignatureAlgorithm.HS256)   // HMAC-SHA256 서명
        .compact();                                // 직렬화(String)
  }

  public Claims parse(String token) {
    return Jwts.parserBuilder().setSigningKey(key).build()
        .parseClaimsJws(token).getBody();
  }
}