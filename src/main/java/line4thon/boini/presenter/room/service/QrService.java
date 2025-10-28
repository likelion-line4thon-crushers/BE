package line4thon.boini.presenter.room.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import line4thon.boini.global.common.exception.CustomException;
import line4thon.boini.presenter.room.exception.RoomErrorCode;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

@Slf4j
@Service
public class QrService {
  @SneakyThrows
  public String toBase64Png(String url) {

    if (url == null || url.isBlank()) {
      throw new CustomException(RoomErrorCode.INVALID_REQUEST);
    }

    try {
      // QR 코드 생성
      BitMatrix matrix = new MultiFormatWriter()
          .encode(url, BarcodeFormat.QR_CODE, 512, 512);
      var img = MatrixToImageWriter.toBufferedImage(matrix);

      // 이미지 → Base64 변환
      var baos = new ByteArrayOutputStream();
      ImageIO.write(img, "png", baos);
      String encoded = Base64.getEncoder().encodeToString(baos.toByteArray());

      log.debug("QR 코드 생성 완료: {} bytes (url={})", encoded.length(), url);
      return encoded;

    } catch (Exception ex) {
      log.error("QR 코드 생성 실패: url={}, err={}", url, ex.toString());
      throw new CustomException(RoomErrorCode.QR_GENERATE_FAILED);
    }
  }
}