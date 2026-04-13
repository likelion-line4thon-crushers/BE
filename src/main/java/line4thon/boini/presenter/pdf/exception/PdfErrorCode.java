package line4thon.boini.presenter.pdf.exception;

import line4thon.boini.global.common.exception.model.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum PdfErrorCode implements BaseErrorCode {

    // 청크 업로드
    CHUNK_TOO_LARGE(HttpStatus.BAD_REQUEST, "P001", "청크 크기가 허용 범위(2MB)를 초과했습니다."),
    INVALID_CHUNK_INDEX(HttpStatus.BAD_REQUEST, "P002", "청크 인덱스가 유효하지 않습니다."),
    CHUNK_SAVE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "P003", "청크 저장에 실패했습니다."),

    // 조립
    ASSEMBLY_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "P011", "PDF 조립에 실패했습니다."),

    // 파싱
    PDF_PARSE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "P021", "PDF 파싱에 실패했습니다."),
    PAGE_RENDER_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "P022", "페이지 렌더링에 실패했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
