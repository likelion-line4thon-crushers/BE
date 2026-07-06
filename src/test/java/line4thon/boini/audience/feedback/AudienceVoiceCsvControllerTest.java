package line4thon.boini.audience.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import line4thon.boini.audience.feedback.service.AudienceVoiceCsvService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class AudienceVoiceCsvControllerTest {

  @Autowired WebApplicationContext context;
  @MockBean AudienceVoiceCsvService csvService;

  MockMvc mockMvc;

  private MockMvc mvc() {
    if (mockMvc == null) mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    return mockMvc;
  }

  @Test
  void downloadsCsvWithAttachmentHeaderAndBom() throws Exception {
    when(csvService.buildCsv("r1")).thenReturn("audienceId,rating\r\nu1,4\r\n");

    var result =
        mvc().perform(get("/api/report/r1/audience-voice/csv"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition",
                org.hamcrest.Matchers.allOf(
                    org.hamcrest.Matchers.containsString("attachment"),
                    org.hamcrest.Matchers.containsString("audience-voice-r1.csv"))))
            .andExpect(header().string("Content-Type",
                org.hamcrest.Matchers.startsWith("text/csv")))
            .andReturn();

    byte[] body = result.getResponse().getContentAsByteArray();
    assertThat(body[0]).isEqualTo((byte) 0xEF);
    assertThat(body[1]).isEqualTo((byte) 0xBB);
    assertThat(body[2]).isEqualTo((byte) 0xBF);
  }
}
