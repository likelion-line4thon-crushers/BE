package line4thon.boini.global.websocket;

import io.jsonwebtoken.Claims;
import line4thon.boini.global.jwt.service.JwtService;
import line4thon.boini.global.websocket.exception.StompErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

  private static final String ROLE_PRESENTER = "ROLE_PRESENTER";
  private static final String ROLE_AUDIENCE  = "ROLE_AUDIENCE";

  private static final String ATTR_ROLE   = "role";
  private static final String ATTR_ROOMID = "roomId";
  private static final String ATTR_SUB    = "sub";

  private static final Pattern TOPIC_ROOM_DOT = Pattern.compile("^/(topic|queue)/room\\.(?<rid>[A-Za-z0-9\\-]+).*");
  private static final Pattern SLASH_ROOMS    = Pattern.compile("^/.*/rooms/(?<rid>[A-Za-z0-9\\-]+)(/.*)?$");

  private final JwtService jwt;

  /**
   * [1] STOMP 메시지 가로채기
   * - CONNECT / SUBSCRIBE / SEND 단계에서 호출됨
   * - 메시지 헤더를 감싸고 토큰 검증 및 권한 검사 수행
   */
  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor acc = StompHeaderAccessor.wrap(message);
    StompCommand cmd = acc.getCommand();

    if (cmd == null) {
      return message;
    }

    if (log.isDebugEnabled()) {
      log.debug("[STOMP] 명령={}, 세션ID={}, 목적지={}, 메시지타입={}",
          cmd, safe(acc.getSessionId()), safe(acc.getDestination()), acc.getMessageType());
    }

    try {
      switch (cmd) {
        case CONNECT -> handleConnect(acc); // 연결 시 토큰 검증
        case SUBSCRIBE, SEND -> handleAuthorize(acc); // 메시지 보낼/구독할 때 권한 검사
        default -> { /* ACK/NACK, DISCONNECT 등은 통과 */ }
      }

      acc.setLeaveMutable(true); // 이후 체인에서 헤더 수정 가능하도록 설정

      // 변경된 헤더를 포함해 새 메시지로 반환(항상 안전)
      return MessageBuilder.createMessage(message.getPayload(), acc.getMessageHeaders());

    } catch (MessagingException ex) {
      log.warn("[STOMP] {} 요청이 거절됨: {}", cmd, ex.getMessage());
      throw ex;

    } catch (Throwable t) {
      log.error("💥 [STOMP] {} 처리 중 내부 오류: {}", cmd, t.toString(), t);
      throw new MessagingException(StompErrorCode.WS_INTERNAL.getMessage(), t);
    }
  }

  /**
   * [2] CONNECT 단계 처리
   * - 토큰을 검증하고 사용자 정보(역할, 방 ID)를 세션에 저장
   * - Principal(스프링 보안 객체) 주입
   */
  private void handleConnect(StompHeaderAccessor acc) {
    log.info("✅ [STOMP] CONNECT 시도: 세션ID={}, 헤더={}", acc.getSessionId(), maskAuthz(acc.toNativeHeaderMap()));

    // STOMP Native Header 로 들어온 Authorization만 읽음
    String authzRaw = first(acc, "Authorization");
    if (authzRaw == null) authzRaw = first(acc, "authorization");

    if (authzRaw == null || !authzRaw.toLowerCase().startsWith("bearer ")) {
      throw new MessagingException(StompErrorCode.WS_JWT_MISSING.getMessage());
    }

    // "Bearer " 이후 토큰 추출
    String token = authzRaw.substring("bearer ".length()).trim();

    Claims claims;

    try {
      claims = jwt.parse(token);
    } catch (Exception e) {
      log.warn("[STOMP] JWT 파싱 실패: {}", e.getMessage());
      throw new MessagingException("유효하지 않은 토큰입니다.");
    }

    String role    = claims.get("role", String.class);
    String roomId  = claims.get("roomId", String.class);
    String subject = Optional.ofNullable(claims.get("sub", String.class)).orElse("anon");

    if (role == null || roomId == null) {
      throw new MessagingException(StompErrorCode.WS_JWT_CLAIM_INVALID.getMessage());
    }

    String granted = "presenter".equalsIgnoreCase(role) ? ROLE_PRESENTER : ROLE_AUDIENCE;
    Authentication auth = new UsernamePasswordAuthenticationToken(
        subject, null, List.of(new SimpleGrantedAuthority(granted)));
    acc.setUser(auth); // Principal 주입

    // 세션 속성 저장(이후 SUBSCRIBE/SEND에서 사용)
    Map<String, Object> attrs = acc.getSessionAttributes();
    if (attrs != null) {
      attrs.put(ATTR_ROLE, role);
      attrs.put(ATTR_ROOMID, roomId);
      attrs.put(ATTR_SUB, subject);
    }

    log.info("[STOMP] CONNECT 성공: 사용자={}, 역할={}, 방ID={}", subject, role, roomId);
  }

  /**
   * [3] SUBSCRIBE / SEND 단계 처리
   * - 세션의 role/roomId를 확인하고
   * - 방 일치 및 역할별 접근 제한 검증
   */
  private void handleAuthorize(StompHeaderAccessor acc) {
    log.info("📩 [STOMP] {} 요청: 세션ID={}, 목적지={}, 헤더={}",
        acc.getCommand(), acc.getSessionId(), acc.getDestination(), maskAuthz(acc.toNativeHeaderMap()));

    Map<String, Object> attrs = acc.getSessionAttributes();
    String role   = (attrs == null) ? null : asStr(attrs.get(ATTR_ROLE));
    String myRoom = (attrs == null) ? null : asStr(attrs.get(ATTR_ROOMID));

    // 세션에 role/roomId가 없으면 Authorization 헤더를 재검증하여 보완
    if (role == null || myRoom == null) {
      String authzRaw = Optional.ofNullable(first(acc, "Authorization"))
          .orElse(first(acc, "authorization"));
      if (authzRaw == null || !authzRaw.toLowerCase().startsWith("bearer ")) {
        throw new MessagingException(StompErrorCode.WS_JWT_MISSING.getMessage());
      }
      String token = authzRaw.substring("bearer ".length()).trim();

      Claims claims;
      try { claims = jwt.parse(token); }
      catch (Exception e) {
        throw new MessagingException(StompErrorCode.WS_JWT_INVALID.getMessage(), e);
      }

      role   = claims.get("role", String.class);
      myRoom = claims.get("roomId", String.class);
      if (role == null || myRoom == null)
        throw new MessagingException(StompErrorCode.WS_JWT_CLAIM_INVALID.getMessage());

      if (attrs != null) {
        attrs.put(ATTR_ROLE, role);
        attrs.put(ATTR_ROOMID, myRoom);
        attrs.putIfAbsent(ATTR_SUB, Optional.ofNullable(claims.get("sub", String.class)).orElse("anon"));
      }
    }

    String dest = acc.getDestination();
    if (dest == null) return;

    String destRoomId = extractRoomIdFlexible(dest);

    // 동일 방 검증
    if (destRoomId != null && !Objects.equals(destRoomId, myRoom)) {
      throw new MessagingException(StompErrorCode.WS_ROOM_MISMATCH.getMessage());
    }

    // 역할별 제어 채널 제한
    if (dest.startsWith("/app/presenter/") && !"presenter".equalsIgnoreCase(role)) {
      throw new MessagingException(StompErrorCode.WS_FORBIDDEN.getMessage());
    }
    if (dest.startsWith("/app/audience/") && !"audience".equalsIgnoreCase(role)) {
      throw new MessagingException(StompErrorCode.WS_FORBIDDEN.getMessage());
    }

    log.debug("[STOMP] {} 승인됨: 역할={}, 방={}", acc.getCommand(), role, myRoom);
  }

  // null-safe 문자열 변환
  private static String safe(Object o) { return (o == null) ? "-" : String.valueOf(o); }

  // STOMP 헤더에서 첫 번째 값만 가져오기
  private String first(StompHeaderAccessor acc, String name) {
    List<String> list = acc.getNativeHeader(name);
    return (list == null || list.isEmpty()) ? null : list.get(0);
  }

  // 목적지 문자열에서 roomId를 유연하게 추출
  private String extractRoomIdFlexible(String destination) {
    if (destination == null) return null;
    Matcher m1 = TOPIC_ROOM_DOT.matcher(destination);
    if (m1.matches()) return m1.group("rid");
    Matcher m2 = SLASH_ROOMS.matcher(destination);
    if (m2.matches()) return m2.group("rid");
    return null;
  }

  // null-safe toString
  private String asStr(Object o) { return o == null ? null : String.valueOf(o); }

  // Authorization 헤더를 마스킹해서 로그 노출 방지
  private Map<String, List<String>> maskAuthz(Map<String, List<String>> headers) {
    if (headers == null || headers.isEmpty()) return Map.of();
    Map<String, List<String>> map = new HashMap<>(headers);
    if (map.containsKey("Authorization")) map.put("Authorization", List.of("***masked***"));
    if (map.containsKey("authorization")) map.put("authorization", List.of( "***masked***"));
    return map;
  }
}
