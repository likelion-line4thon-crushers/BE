package line4thon.boini.presenter.image.exception;

import line4thon.boini.global.common.exception.model.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ImageAssetErrorCode implements BaseErrorCode {

  // 입력/검증 관련
  NO_FILES(HttpStatus.BAD_REQUEST, "I001", "업로드할 파일이 비어 있습니다."),
  INVALID_CONTENT_TYPE(HttpStatus.BAD_REQUEST, "I002", "지원하지 않는 이미지 형식입니다."),
  INVALID_EXTENSION(HttpStatus.BAD_REQUEST, "I003", "허용되지 않은 확장자입니다."),
  INVALID_PAGE_NUMBER(HttpStatus.BAD_REQUEST, "I004", "페이지 번호가 올바르지 않습니다."),

  // 저장/가공/조회 관련
  THUMBNAIL_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "I011", "썸네일 생성에 실패했습니다."),
  ORIGINAL_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "I012", "원본 이미지 업로드에 실패했습니다."),
  THUMBNAIL_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "I013", "썸네일 업로드에 실패했습니다."),
  PRESIGN_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "I014", "URL 서명(Pre-signed)에 실패했습니다."),
  OBJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "I015", "요청한 원본 이미지가 존재하지 않습니다."),

  // 설정/시스템
  MISCONFIGURED_S3(HttpStatus.INTERNAL_SERVER_ERROR, "I021", "S3 설정이 올바르지 않습니다."),
  UNKNOWN_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "I099", "알 수 없는 오류가 발생했습니다.");

  private final HttpStatus status;
  private final String code;
  private final String message;
}