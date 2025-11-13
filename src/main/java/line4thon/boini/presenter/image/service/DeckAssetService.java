package line4thon.boini.presenter.image.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import line4thon.boini.global.common.exception.CustomException;
import line4thon.boini.presenter.image.exception.ImageAssetErrorCode;
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
import software.amazon.awssdk.core.exception.SdkException;
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

  public OriginalUrlResponse getOriginalUrl(String roomId, String deckId, int page, String extHint) {
    return getOriginalInternal(roomId, deckId, page, extHint);
  }

  public UploadPagesResponse uploadPages(String roomId, String deckId, List<MultipartFile> files) {
    String bucket = props.getS3().getBucket();
    String root   = normalizePrefix(props.getS3().getRootPrefix());

    if (files == null || files.isEmpty()) {
      log.warn("[검증] 업로드 파일이 비어 있음: roomId={}, deckId={}", roomId, deckId);
      throw new CustomException(ImageAssetErrorCode.NO_FILES);
    }

    List<ThumbnailDto> thumbs = new ArrayList<>();
    String firstPageOriginalUrl = null;

    int page = 1;
    for (MultipartFile f : files) {
      if (page <= 0) {
        throw new CustomException(ImageAssetErrorCode.INVALID_PAGE_NUMBER);
      }
      String contentType = f.getContentType();
      String ext      = guessExt(f.getContentType());
      String origKey  = buildKey(root, roomId, deckId, page, false, ext);
      String thumbKey = buildKey(root, roomId, deckId, page, true,  "webp");

      try (InputStream inForThumb = f.getInputStream();
          ByteArrayOutputStream thumbOut = new ByteArrayOutputStream()) {

        try {
          s3.putObject(
              PutObjectRequest.builder()
                  .bucket(bucket)
                  .key(origKey)
                  .contentType(contentType)
                  .build(),
              RequestBody.fromInputStream(f.getInputStream(), f.getSize())
          );
          log.info("[S3] 원본 업로드 완료 → s3://{}/{}", bucket, origKey);
        } catch (SdkException e) {
          log.error("[오류] 원본 업로드 실패: key={}, 이유={}", origKey, e.getMessage(), e);
          throw new CustomException(ImageAssetErrorCode.ORIGINAL_UPLOAD_FAILED);
        }

        try {
          Thumbnails.of(inForThumb)
              .size(320, 320)
              .outputFormat("webp")
              .outputQuality(0.8f)
              .toOutputStream(thumbOut);
        } catch (IOException tEx) {
          log.error("[오류] 썸네일 생성 실패: key={}, 이유={}", thumbKey, tEx.getMessage(), tEx);
          throw new CustomException(ImageAssetErrorCode.THUMBNAIL_GENERATION_FAILED);
        }

        try {
          s3.putObject(
              PutObjectRequest.builder()
                  .bucket(bucket)
                  .key(thumbKey)
                  .contentType("image/webp")
                  .build(),
              RequestBody.fromBytes(thumbOut.toByteArray())
          );
          log.info("[S3] 썸네일 업로드 완료 → s3://{}/{}", bucket, thumbKey);
        } catch (SdkException e) {
          log.error("[오류] 썸네일 업로드 실패: key={}, 이유={}", thumbKey, e.getMessage(), e);
          throw new CustomException(ImageAssetErrorCode.THUMBNAIL_UPLOAD_FAILED);
        }

        String thumbUrlAbs = buildPublicOrPresignedUrl(thumbKey, false);
        thumbs.add(new ThumbnailDto(page, thumbUrlAbs));

        if (page == 1) {
          firstPageOriginalUrl = buildPublicOrPresignedUrl(origKey, true);
        }

        page++;
      } catch (IOException e) {
        log.error("[오류] 페이지 업로드 중 IO 예외: page={}, 이유={}", page, e.getMessage(), e);
        throw new CustomException(ImageAssetErrorCode.UNKNOWN_ERROR);
      }
    }

    if (!thumbs.isEmpty()) {
      log.info("[응답] 첫 번째 썸네일 URL: {}", thumbs.get(0).getThumbnailUrl());
      log.info("[응답] 첫 번째 원본 URL: {}", firstPageOriginalUrl);
    }

    return new UploadPagesResponse(deckId, files.size(), firstPageOriginalUrl, thumbs);
  }

  public SlidesMetaResponse getThumbnails(String roomId, String deckId, int totalPages) {
    if (totalPages <= 0) {
      throw new CustomException(ImageAssetErrorCode.INVALID_PAGE_NUMBER);
    }

    String root = normalizePrefix(props.getS3().getRootPrefix());
    List<ThumbnailDto> list = new ArrayList<>(totalPages);
    for (int p = 1; p <= totalPages; p++) {
      String key = buildKey(root, roomId, deckId, p, true, "webp");
      String absoluteUrl = buildPublicOrPresignedUrl(key, false);
      list.add(new ThumbnailDto(p, absoluteUrl));
    }
    log.info("[S3] 총 {}개의 썸네일 메타 URL 생성 완료", totalPages);
    return new SlidesMetaResponse(roomId, deckId, totalPages, list);
  }

  private OriginalUrlResponse getOriginalInternal(String roomId, String deckId, int page, String extHint) {
    if (page <= 0) {
      throw new CustomException(ImageAssetErrorCode.INVALID_PAGE_NUMBER);
    }

    String ext = (extHint == null || extHint.isBlank()) ? "png" : extHint.toLowerCase();
    if (!List.of("png","jpg","jpeg","webp").contains(ext)) {
      throw new CustomException(RoomErrorCode.INVALID_REQUEST);
    }

    String key = buildKey(
        normalizePrefix(props.getS3().getRootPrefix()),
        roomId, deckId, page, false, ext
    );

    String url = buildPresignedUrl(key);
    return new OriginalUrlResponse(roomId, deckId, page, url);
  }

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

  private String buildPublicOrPresignedUrl(String key, boolean forcePresign) {
    String cf = props.getS3().getCloudfrontDomain();
    if (!forcePresign && cf != null && !cf.isBlank()) {
      return "https://%s/%s".formatted(cf.replaceAll("/+$",""), urlEncodePath(key));
    }
    return buildPresignedUrl(key);
  }

  private String buildPresignedUrl(String key) {
    GetObjectRequest get = GetObjectRequest.builder()
        .bucket(props.getS3().getBucket())
        .key(key)
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
    if (contentType.contains("webp")) return "webp";
    return "png";
  }
}