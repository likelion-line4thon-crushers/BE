package line4thon.boini.presenter.image.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import line4thon.boini.global.common.exception.CustomException;
import line4thon.boini.presenter.room.exception.RoomErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import line4thon.boini.global.config.AppProperties;
import line4thon.boini.presenter.image.dto.response.OriginalUrlResponse;
import line4thon.boini.presenter.image.dto.response.SlidesMetaResponse;
import line4thon.boini.presenter.image.dto.ThumbnailDto;
import line4thon.boini.presenter.image.dto.response.UploadPagesResponse;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeckAssetService {

  private final S3Client s3;
  private final S3Presigner presigner;
  private final AppProperties props;

  // public API: 컨트롤러에서 이 메서드만 사용
  public OriginalUrlResponse getOriginalUrl(String roomId, String deckId, int page, String extHint) {
    return getOriginalInternal(roomId, deckId, page, extHint); // 내부 위임
  }

  /** PDF→이미지 업로드(원본+썸네일) */
  public UploadPagesResponse uploadPages(String roomId, String deckId, List<MultipartFile> files) {
    String bucket = props.getS3().getBucket();
    String root   = normalizePrefix(props.getS3().getRootPrefix()); // [수정] 정규화 적용

    List<ThumbnailDto> thumbs = new ArrayList<>();
    String firstPageOriginalUrl = null;

    int page = 1;
    for (MultipartFile f : files) {
      String ext      = guessExt(f.getContentType());
      String origKey  = buildKey(root, roomId, deckId, page, false, ext);
      String thumbKey = buildKey(root, roomId, deckId, page, true,  "webp");

      try (InputStream inForThumb = f.getInputStream();
          ByteArrayOutputStream thumbOut = new ByteArrayOutputStream()) {

        // (1) 원본 업로드
        s3.putObject(
            PutObjectRequest.builder()
                .bucket(bucket)
                .key(origKey)
                .contentType(f.getContentType())
                .build(),
            RequestBody.fromInputStream(f.getInputStream(), f.getSize())
        );
        log.info("[S3] original uploaded: s3://{}/{}", bucket, origKey);

        // (2) 썸네일 생성 후 업로드
        Thumbnails.of(inForThumb).size(320, 320).outputFormat("webp").outputQuality(0.8f)
            .toOutputStream(thumbOut);

        s3.putObject(
            PutObjectRequest.builder()
                .bucket(bucket)
                .key(thumbKey)
                .contentType("image/webp")
                .build(),
            RequestBody.fromBytes(thumbOut.toByteArray())
        );
        log.info("[S3] thumb uploaded: s3://{}/{}", bucket, thumbKey);

        // (3) 응답용 절대 URL
        String thumbUrlAbs = buildPublicOrPresignedUrl(thumbKey, false);
        thumbs.add(new ThumbnailDto(page, thumbUrlAbs));

        if (page == 1) {
          firstPageOriginalUrl = buildPublicOrPresignedUrl(origKey, true);
        }

        page++;
      } catch (IOException e) {
        throw new RuntimeException("Upload failed at page=" + page, e);
      }
    }

    if (!thumbs.isEmpty()) {
      log.info("[RESP] first thumb: {}", thumbs.get(0).getThumbnailUrl());
      log.info("[RESP] first original: {}", firstPageOriginalUrl);
    }

    return new UploadPagesResponse(deckId, files.size(), firstPageOriginalUrl, thumbs);
  }

  /** 메타(썸네일) 목록도 절대 URL 보장 */
  public SlidesMetaResponse getThumbnails(String roomId, String deckId, int totalPages) {
    String root = normalizePrefix(props.getS3().getRootPrefix()); // [수정] 정규화 적용
    List<ThumbnailDto> list = new ArrayList<>(totalPages);
    for (int p = 1; p <= totalPages; p++) {
      String key = buildKey(root, roomId, deckId, p, true, "webp");
      String absoluteUrl = buildPublicOrPresignedUrl(key, false);
      list.add(new ThumbnailDto(p, absoluteUrl));
    }
    return new SlidesMetaResponse(roomId, deckId, totalPages, list);
  }

  /** 특정 페이지 원본 URL (항상 presign) */
  private OriginalUrlResponse getOriginalInternal(String roomId, String deckId, int page, String extHint) {
    String ext = (extHint == null || extHint.isBlank()) ? "png" : extHint.toLowerCase();
    if (!List.of("png","jpg","jpeg","webp").contains(ext)) {
      throw new CustomException(RoomErrorCode.INVALID_REQUEST);
    }

    String key = buildKey(
        normalizePrefix(props.getS3().getRootPrefix()), // [수정] 정규화 적용
        roomId, deckId, page, false, ext
    );

    String url = buildPresignedUrl(key); // 항상 presigned
    return new OriginalUrlResponse(roomId, deckId, page, url);
  }

  // ============== helpers ==============

  // rootPrefix 꼬임 방지
  private String normalizePrefix(String s) {
    if (s == null || s.isBlank()) return "";
    String t = s.trim();
    while (t.startsWith("/")) t = t.substring(1);
    while (t.endsWith("/")) t = t.substring(0, t.length()-1);
    return t;
  }

  private String buildKey(String root, String roomId, String deckId, int page, boolean thumb, String ext) {
    String folder = thumb ? "thumbs" : "pages";
    String file   = "%04d.%s".formatted(page, ext);
    return root.isEmpty()
        ? "%s/%s/%s/%s".formatted(roomId, deckId, folder, file)
        : "%s/%s/%s/%s/%s".formatted(root, roomId, deckId, folder, file);
  }

  // CloudFront 우선, 없으면 presign
  private String buildPublicOrPresignedUrl(String key, boolean forcePresign) {
    String cf = props.getS3().getCloudfrontDomain();
    if (!forcePresign && cf != null && !cf.isBlank()) {
      return "https://%s/%s".formatted(cf.replaceAll("/+$",""), urlEncodePath(key)); // [수정] 인코딩
    }
    return buildPresignedUrl(key);
  }

  // presign은 항상 https 로
  private String buildPresignedUrl(String key) {
    GetObjectRequest get = GetObjectRequest.builder()
        .bucket(props.getS3().getBucket())
        .key(key)
        // .responseContentType("image/png") // [선택] ext에 맞춰 힌트 주고싶으면 세팅
        .build();
    PresignedGetObjectRequest pre = presigner.presignGetObject(b -> b
        .signatureDuration(Duration.ofSeconds(props.getS3().getPresignSeconds()))
        .getObjectRequest(get));
    String url = pre.url().toString();
    return url.startsWith("http://") ? "https://" + url.substring(7) : url;
  }

  private String urlEncodePath(String path) {
    String[] parts = path.split("/");
    return String.join("/",
        java.util.Arrays.stream(parts)
            .map(p -> java.net.URLEncoder.encode(p, java.nio.charset.StandardCharsets.UTF_8))
            .toList());
  }

  private String guessExt(String contentType) {
    if (contentType == null) return "png";
    if (contentType.contains("png")) return "png";
    if (contentType.contains("jpeg") || contentType.contains("jpg")) return "jpg";
    if (contentType.contains("webp")) return "webp"; // [수정] 웹피도 처리
    return "png";
  }
}