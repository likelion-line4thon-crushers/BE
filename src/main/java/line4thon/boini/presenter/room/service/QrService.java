package line4thon.boini.presenter.room.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

@Service
public class QrService {
  @SneakyThrows
  public String toBase64Png(String url) {
    BitMatrix matrix = new MultiFormatWriter().encode(url, BarcodeFormat.QR_CODE, 512, 512);
    var img = MatrixToImageWriter.toBufferedImage(matrix);
    var baos = new ByteArrayOutputStream();
    ImageIO.write(img, "png", baos);
    return Base64.getEncoder().encodeToString(baos.toByteArray());
  }
}
