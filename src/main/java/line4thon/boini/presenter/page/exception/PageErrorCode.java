package line4thon.boini.presenter.page.exception;


import line4thon.boini.global.common.exception.model.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum PageErrorCode implements BaseErrorCode {
    NOT_RECEIVE_PAGE_DATA(HttpStatus.BAD_REQUEST, "C001", "방 전환 정보가 제대로 전달되지 않았습니다.");
    private final HttpStatus status;
    private final String code;
    private final String message;
}
