package line4thon.boini.audience.feedback;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import line4thon.boini.audience.feedback.dto.response.AudienceVoiceResponse;
import line4thon.boini.audience.feedback.dto.response.AudienceVoiceResponse.QuestionVoice;
import line4thon.boini.audience.feedback.service.AudienceVoiceReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class AudienceVoiceReportControllerTest {

  @Autowired WebApplicationContext context;
  @MockBean AudienceVoiceReportService service;

  MockMvc mockMvc;

  private MockMvc mvc() {
    if (mockMvc == null) mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    return mockMvc;
  }

  @Test
  void returnsReport() throws Exception {
    when(service.getReport("r1")).thenReturn(AudienceVoiceResponse.builder()
        .averageRating(4.5).hasQuestions(true)
        .questions(List.of(QuestionVoice.builder()
            .questionId(10).orderIndex(0).questionText("Q1")
            .answers(List.of("a")).summary("s").build()))
        .build());

    mvc().perform(get("/api/report/r1/audience-voice"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.hasQuestions").value(true))
        .andExpect(jsonPath("$.data.questions[0].questionText").value("Q1"));
  }
}
