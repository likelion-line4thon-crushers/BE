package line4thon.boini;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class BoiniApplication {

  public static void main(String[] args) {
    SpringApplication.run(BoiniApplication.class, args);
  }

}
