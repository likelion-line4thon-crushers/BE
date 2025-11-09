package line4thon.boini.global;

import jakarta.servlet.http.HttpServletRequest;

public class WsUrlResolver {
  public static String resolve(HttpServletRequest req) {
    String scheme = first(req.getHeader("X-Forwarded-Proto"), req.getScheme()); // https or http
    String host   = first(req.getHeader("X-Forwarded-Host"),
        req.getHeader("Host"),
        req.getServerName());
    return scheme + "://" + host + "/ws"; // SockJS 엔드포인트 base
  }
  private static String first(String... vs) {
    for (String v : vs) if (v != null && !v.isBlank()) return v;
    return "";
  }
}