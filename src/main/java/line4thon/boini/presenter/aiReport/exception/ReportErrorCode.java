package line4thon.boini.presenter.aiReport.exception;

import line4thon.boini.global.common.exception.model.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ReportErrorCode implements BaseErrorCode {

  REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "R001", "해당 roomId의 리포트를 찾을 수 없습니다."),
  REPORT_SAVE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "R002", "리포트 저장 중 오류가 발생했습니다."),
  INVALID_REPORT_DATA(HttpStatus.BAD_REQUEST, "R003", "리포트 데이터가 올바르지 않습니다.");

  private final HttpStatus status;
  private final String code;
  private final String message;
}
