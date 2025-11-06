package line4thon.boini.audience.feedback.exception;

import line4thon.boini.global.common.exception.model.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum FeedbackErrorCode implements BaseErrorCode {

  INVALID_RATING(HttpStatus.BAD_REQUEST, "F001", "별점은 1~5 사이여야 합니다."),
  EMPTY_ROOM_ID(HttpStatus.BAD_REQUEST, "F002", "roomId가 전달되지 않았습니다."),
  SAVE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "F003", "후기 저장 중 오류가 발생했습니다."),
  FETCH_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "F004", "후기 조회 중 오류가 발생했습니다.");

  private final HttpStatus status;
  private final String code;
  private final String message;
}
