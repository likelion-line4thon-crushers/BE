package line4thon.boini.audience.question.exception;

import line4thon.boini.global.common.exception.model.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AudienceQuestionErrorCode implements BaseErrorCode {

  TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "Q001", "질문 요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),
  INVALID_ROOM_ID(HttpStatus.BAD_REQUEST, "Q002", "유효하지 않은 방 ID입니다."),
  REDIS_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Q003", "질문 저장 중 오류가 발생했습니다."),
  STREAM_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Q004", "이벤트 스트림 처리 중 오류가 발생했습니다.");

  private final HttpStatus status;
  private final String code;
  private final String message;
}
