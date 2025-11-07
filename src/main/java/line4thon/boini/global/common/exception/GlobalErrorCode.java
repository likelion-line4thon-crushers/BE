package line4thon.boini.global.common.exception;

import line4thon.boini.global.common.exception.model.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum GlobalErrorCode implements BaseErrorCode {

  INVALID_INPUT_VALUE("G001", "유효하지 않은 입력입니다.", HttpStatus.BAD_REQUEST),
  RESOURCE_NOT_FOUND("G002", "요청한 리소스를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
  INTERNAL_SERVER_ERROR("G003", "서버 내부 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),

  // 인증 / 인가 관련
  UNAUTHORIZED("A001", "인증이 필요합니다. (JWT 토큰이 존재하지 않거나 유효하지 않습니다.)", HttpStatus.UNAUTHORIZED),
  FORBIDDEN("A002", "해당 리소스에 접근할 권한이 없습니다.", HttpStatus.FORBIDDEN),

  // S3 에러
  S3_DELETE_FAILED("S002", "해당 폴더를 삭제하지 못했습니다.", HttpStatus.BAD_REQUEST);


  private final String code;
  private final String message;
  private final HttpStatus status;
}