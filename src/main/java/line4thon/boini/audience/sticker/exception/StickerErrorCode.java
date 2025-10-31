package line4thon.boini.audience.sticker.exception;


import line4thon.boini.global.common.exception.model.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum StickerErrorCode implements BaseErrorCode {
    NOT_SEND_REACTION_STICKER(HttpStatus.BAD_REQUEST, "S001", "리액션 스티커가 정상적으로 전달되지 않았습니다.");
    private final HttpStatus status;
    private final String code;
    private final String message;
}
