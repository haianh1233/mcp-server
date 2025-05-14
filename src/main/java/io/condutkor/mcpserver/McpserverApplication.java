package io.condutkor.mcpserver;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class McpserverApplication {
	public static void main(String[] args) {
		SpringApplication.run(McpserverApplication.class, args);
	}

	@Bean
	public ToolCallbackProvider graphQLTools(GraphQLService graphQLService) {
		return MethodToolCallbackProvider.builder().toolObjects(graphQLService).build();
	}
}
