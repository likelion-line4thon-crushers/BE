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
    UNSUPPORTED_PRESENTATION_FILE(HttpStatus.BAD_REQUEST, "P012", "지원하지 않는 프레젠테이션 파일 형식입니다."),

    // 파싱
    PDF_PARSE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "P021", "PDF 파싱에 실패했습니다."),
    PAGE_RENDER_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "P022", "페이지 렌더링에 실패했습니다."),
    OFFICE_CONVERSION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "P023", "PPT/PPTX 파일을 PDF로 변환하지 못했습니다."),

    // 폰트 업로드
    INVALID_FONT_FILE(HttpStatus.BAD_REQUEST, "P031", "유효하지 않은 폰트 파일입니다."),
    FONT_TOO_LARGE(HttpStatus.BAD_REQUEST, "P032", "폰트 파일 크기가 허용 범위를 초과했습니다."),
    TOO_MANY_FONTS(HttpStatus.BAD_REQUEST, "P033", "업로드 가능한 폰트 개수를 초과했습니다."),
    FONT_UPLOAD_TOO_LARGE_TOTAL(HttpStatus.BAD_REQUEST, "P034", "업로드한 폰트 총 용량이 허용 범위를 초과했습니다."),
    SESSION_NOT_AWAITING_FONTS(HttpStatus.CONFLICT, "P035", "폰트 대기 상태가 아닌 업로드 세션입니다."),
    UPLOAD_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "P036", "업로드 세션을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
