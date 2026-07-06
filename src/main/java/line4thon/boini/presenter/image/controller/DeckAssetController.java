package line4thon.boini.presenter.image.controller;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import line4thon.boini.global.common.response.BaseResponse;
import line4thon.boini.presenter.image.dto.SlideNoteDto;
import line4thon.boini.presenter.image.dto.request.UpdateSlideNoteRequest;
import line4thon.boini.presenter.image.dto.response.OriginalUrlResponse;
import line4thon.boini.presenter.image.dto.response.SlideNotesResponse;
import line4thon.boini.presenter.image.dto.response.SlidesMetaResponse;
import line4thon.boini.presenter.image.dto.response.UploadPagesResponse;
import line4thon.boini.presenter.image.service.DeckAssetService;
import line4thon.boini.presenter.image.service.SlideNoteService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/presentations")
@Validated
public class DeckAssetController {

  private final DeckAssetService deckAssets;
  private final SlideNoteService slideNoteService;

  @GetMapping("/{roomId}/{deckId}/pages/{page:\\d+}")
  @Operation(
      summary = "원본 이미지 Presigned URL 발급",
      description = """
      특정 프레젠테이션의 페이지(슬라이드) 원본 이미지를 다운로드할 수 있는 presigned GET URL을 발급합니다.
      - `ext` 파라미터로 확장자를 지정합니다 (기본값: png)
      - URL은 제한된 기간 동안만 유효합니다 (설정값: presignSeconds)
      """
  )
  public BaseResponse<OriginalUrlResponse> getOriginal(
      @PathVariable String roomId,
      @PathVariable String deckId,
      @PathVariable @Min(1) int page,
      @RequestParam(defaultValue = "png")
      @Pattern(regexp = "png|jpg|jpeg|webp") String ext
  ) {
    var res = deckAssets.getOriginalUrl(roomId, deckId, page, ext);
    return BaseResponse.success(res);
  }

  @GetMapping("/{roomId}/{deckId}/pages/{page:\\d+}.{ext}")
  @Operation(hidden = true)
  public BaseResponse<OriginalUrlResponse> getOriginalWithExtInPath(
      @PathVariable String roomId,
      @PathVariable String deckId,
      @PathVariable @Min(1) int page,
      @PathVariable @Pattern(regexp = "png|jpg|jpeg|webp") String ext
  ) {
    return BaseResponse.success(deckAssets.getOriginalUrl(roomId, deckId, page, ext));
  }

  // =====================================================================
  // [과거 방식] 프론트에서 PDF → 이미지 변환 후 직접 업로드하는 방식
  // 현재는 백엔드에서 청크 수신 → PDF 조립 → 이미지 변환 → SSE 스트리밍 방식으로 대체됨
  // 관련 신규 API: POST /api/upload/chunk, GET /api/pdf/{pdfId}/stream
  // =====================================================================
  // @PostMapping(value = "/{roomId}/{deckId}/pages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  // @Operation(
  //     summary = "프레젠테이션 페이지 이미지 업로드",
  //     description = """
  //     프론트엔드에서 PDF를 이미지로 분할한 결과를 업로드합니다.
  //     - 요청 형식: `multipart/form-data`
  //     - 필드명: `files[]` (여러 페이지 이미지)
  //     - 서버에서는 각 페이지를 S3에 업로드하고, 썸네일(webp)도 함께 생성합니다.
  //     - 응답에는 첫 페이지 원본 URL과 각 페이지 썸네일 목록이 포함됩니다.
  //     """
  // )
  // public BaseResponse<UploadPagesResponse> uploadPages(
  //     @PathVariable String roomId,
  //     @PathVariable String deckId,
  //     @RequestPart("files") List<MultipartFile> files
  // ) {
  //   return BaseResponse.success(deckAssets.uploadPages(roomId, deckId, files));
  // }

  @GetMapping("/{roomId}/{deckId}/meta")
  @Operation(
      summary = "프레젠테이션 썸네일 목록 조회",
      description = """
      업로드된 프레젠테이션의 모든 썸네일(webp) 이미지의 절대 URL 목록을 반환합니다.
      - `totalPages` 파라미터로 총 페이지 수를 전달해야 합니다.
      - CloudFront가 설정된 경우 공개 URL을, 아니면 presigned URL을 반환합니다.
      """
  )
  public BaseResponse<SlidesMetaResponse> meta(
      @PathVariable String roomId,
      @PathVariable String deckId,
      @RequestParam @Min(1) int totalPages
  ) {
    return BaseResponse.success(deckAssets.getThumbnails(roomId, deckId, totalPages));
  }

  @GetMapping("/{roomId}/{deckId}/notes")
  @Operation(
      summary = "발표자용 슬라이드 노트 목록 조회",
      description = """
      PPT/PPTX 업로드 시 추출된 발표자 노트를 반환합니다.
      - 발표자 JWT(`Authorization: Bearer ...`)가 필요합니다.
      - 청중 클라이언트는 호출하지 않습니다.
      """
  )
  public BaseResponse<SlideNotesResponse> notes(
      @PathVariable String roomId,
      @PathVariable String deckId,
      @RequestHeader(value = "Authorization", required = false) String authorization
  ) {
    return BaseResponse.success(slideNoteService.getPresenterNotes(roomId, deckId, authorization));
  }

  @PutMapping("/{roomId}/{deckId}/notes/{page:\\d+}")
  @Operation(
      summary = "발표자용 슬라이드 노트 저장",
      description = """
      세션 시작 전 발표자가 슬라이드별 노트를 직접 저장합니다.
      - 발표자 JWT(`Authorization: Bearer ...`)가 필요합니다.
      - 세션이 시작된 뒤에는 읽기 전용입니다.
      - 빈 문자열은 해당 슬라이드 노트를 삭제합니다.
      """
  )
  public BaseResponse<SlideNoteDto> updateNote(
      @PathVariable String roomId,
      @PathVariable String deckId,
      @PathVariable @Min(1) int page,
      @Valid @RequestBody UpdateSlideNoteRequest request,
      @RequestHeader(value = "Authorization", required = false) String authorization
  ) {
    String notes = request == null ? "" : request.getNotes();
    return BaseResponse.success(slideNoteService.updatePresenterNote(roomId, deckId, page, notes, authorization));
  }
}
