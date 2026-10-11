package com.github.aceofwands.tasp;

import org.junit.jupiter.api.Test;
import com.github.aceofwands.tasp.repository.EntityRepository;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
		"OPENAI_API_KEY=test-key",
		"tasp.tdb.location=",
		"spring.autoconfigure.exclude="
				+ "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
				+ "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
				+ "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"
})
class ToolAugmentedSemanticParsedApplicationTests {

	@MockitoBean
	private ChatModel chatModel;

	@MockitoBean
	private EntityRepository entityRepository;

	@Test
	void contextLoads() {
	}

}
