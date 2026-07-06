package line4thon.boini.audience.feedback;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Transactional
class FeedbackQuestionControllerTest {

  @Autowired WebApplicationContext context;
  MockMvc mockMvc;

  private MockMvc mvc() {
    if (mockMvc == null) mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    return mockMvc;
  }

  @Test
  void putThenGetReturnsSavedQuestions() throws Exception {
    String body = "{\"questions\":[{\"orderIndex\":0,\"questionText\":\"세션은 어땠나요?\"}]}";

    mvc().perform(put("/api/rooms/roomZ/feedback-questions")
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.questions[0].questionText").value("세션은 어땠나요?"));

    mvc().perform(get("/api/rooms/roomZ/feedback-questions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.questions[0].questionText").value("세션은 어땠나요?"));
  }
}
