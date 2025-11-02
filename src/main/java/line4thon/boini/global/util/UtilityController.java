package line4thon.boini.global.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class UtilityController {

    // favicon 요청 무시 (204 No Content)
    @RequestMapping("favicon.ico")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void favicon() {
        // 브라우저가 favicon 요청을 보내도 아무 일 없도록
    }

    // health 체크용 엔드포인트
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "timestamp", System.currentTimeMillis()
        );
    }
}

