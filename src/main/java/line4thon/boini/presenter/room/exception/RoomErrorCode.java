package line4thon.boini.presenter.room.exception;

import line4thon.boini.global.common.exception.model.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum RoomErrorCode implements BaseErrorCode {

  INVALID_REQUEST(HttpStatus.BAD_REQUEST, "R001", "요청 값이 유효하지 않습니다."),
  INVALID_JOIN_BASE_URL(HttpStatus.INTERNAL_SERVER_ERROR, "R002", "참여 URL 기본값이 잘못 설정되었습니다."),
  INVALID_WS_URL(HttpStatus.INTERNAL_SERVER_ERROR, "R003", "WebSocket URL 설정이 잘못되었습니다."),
  CODE_RESERVE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "R010", "방 코드 예약에 실패했습니다."),
  CODE_CONFIRM_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "R011", "방 코드 확정에 실패했습니다."),
  CODE_RELEASE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "R012", "방 코드 해제에 실패했습니다."),
  QR_GENERATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "R020", "QR 코드 생성에 실패했습니다."),
  PRESENTER_TOKEN_ISSUE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "R030", "발표자 토큰 발급에 실패했습니다."),
  PRESENTER_KEY_ISSUE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "R031", "발표자 키 발급에 실패했습니다."),
  UNEXPECTED(HttpStatus.INTERNAL_SERVER_ERROR, "R999", "방 생성 중 알 수 없는 오류가 발생했습니다.");

  private final HttpStatus status;
  private final String code;
  private final String message;
}
