package line4thon.boini.presenter.room.exception;

import line4thon.boini.global.common.exception.model.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum PresenterErrorCode implements BaseErrorCode {
  INVALID_ROOM_ID(HttpStatus.BAD_REQUEST, "P001", "유효하지 않은 방 ID입니다."),
  PRESENTER_KEY_NOT_FOUND(HttpStatus.UNAUTHORIZED, "P002", "발표자 키가 존재하지 않거나 만료되었습니다."),
  PRESENTER_KEY_MISMATCH(HttpStatus.UNAUTHORIZED, "P003", "발표자 키가 일치하지 않습니다."),
  HASH_FAILURE(HttpStatus.INTERNAL_SERVER_ERROR, "P004", "발표자 키 해시 처리 중 오류가 발생했습니다."),
  REDIS_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "P005", "저장소 처리 중 오류가 발생했습니다."),
  JWT_ISSUE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "P006", "발표자 토큰 발급에 실패했습니다.");

  private final HttpStatus status;
  private final String code;
  private final String message;
}
