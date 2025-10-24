package line4thon.boini.global.jwt.exception;

import line4thon.boini.global.common.exception.model.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum JwtErrorCode implements BaseErrorCode {

  JWT_EXPIRED(HttpStatus.UNAUTHORIZED, "J001", "JWT 토큰이 만료되었습니다."),
  JWT_INVALID(HttpStatus.UNAUTHORIZED, "J002", "JWT 토큰 형식이 올바르지 않습니다."),
  JWT_SIGNATURE_INVALID(HttpStatus.UNAUTHORIZED, "J003", "JWT 서명 검증에 실패했습니다."),
  JWT_UNSUPPORTED(HttpStatus.UNAUTHORIZED, "J004", "지원되지 않는 JWT 형식입니다."),
  JWT_CLAIM_INVALID(HttpStatus.UNAUTHORIZED, "J005", "JWT Claim 정보가 유효하지 않습니다."),
  JWT_MISSING(HttpStatus.UNAUTHORIZED, "J006", "JWT 토큰이 존재하지 않습니다."),
  JWT_UNKNOWN(HttpStatus.UNAUTHORIZED, "J999", "JWT 처리 중 알 수 없는 오류가 발생했습니다.");

  private final HttpStatus status;
  private final String code;
  private final String message;
}