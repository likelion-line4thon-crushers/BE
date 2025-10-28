package line4thon.boini.global.websocket.exception;

import line4thon.boini.global.common.exception.model.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * STOMP/WebSocket 영역에서 사용하는 에러 코드 모음
 * - JWT 관련(누락/형식/클레임)도 STOMP 컨텍스트에 맞춰 여기서 관리
 */
@Getter
@AllArgsConstructor
public enum StompErrorCode implements BaseErrorCode {

  // JWT 계열
  WS_JWT_MISSING(HttpStatus.UNAUTHORIZED, "WS-J001", "JWT 토큰이 존재하지 않습니다."),
  WS_JWT_INVALID(HttpStatus.UNAUTHORIZED, "WS-J002", "유효하지 않은 JWT 토큰입니다."),
  WS_JWT_CLAIM_INVALID(HttpStatus.UNAUTHORIZED, "WS-J003", "JWT Claim 정보가 유효하지 않습니다."),

  // 접근 제어/요청 오류
  WS_FORBIDDEN(HttpStatus.FORBIDDEN, "WS-001", "접근이 허용되지 않은 채널입니다."),
  WS_ROOM_MISMATCH(HttpStatus.FORBIDDEN, "WS-002", "다른 방으로 접근할 수 없습니다."),
  WS_BAD_REQUEST(HttpStatus.BAD_REQUEST, "WS-003", "잘못된 STOMP 요청입니다."),

  // 내부 오류
  WS_INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR, "WS-999", "내부 오류가 발생했습니다.");

  private final HttpStatus status;
  private final String code;
  private final String message;
}