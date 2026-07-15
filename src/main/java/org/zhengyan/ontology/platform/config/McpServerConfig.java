package org.zhengyan.ontology.platform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zhengyan.ontology.platform.service.McpToolService;

@Configuration
public class McpServerConfig {

    @Bean
    public McpJsonMapper mcpJsonMapper(ObjectMapper objectMapper) {
        return new JacksonMcpJsonMapper(objectMapper);
    }

    @Bean
    public HttpServletStreamableServerTransportProvider mcpTransportProvider(McpJsonMapper jsonMapper) {
        return HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(jsonMapper)
                .mcpEndpoint("/mcp")
                .build();
    }

    @Bean
    public ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServlet(
            HttpServletStreamableServerTransportProvider transportProvider) {
        return new ServletRegistrationBean<>(transportProvider, "/mcp");
    }

    @Bean(destroyMethod = "close")
    public McpSyncServer mcpServer(
            HttpServletStreamableServerTransportProvider transportProvider,
            McpToolService toolService) {
        McpSyncServer server = McpServer.sync(transportProvider)
                .serverInfo("ontology-platform", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .resources(false, true)
                        .build())
                .build();

        for (var tool : toolService.getTools()) {
            server.addTool(tool);
        }
        for (var resource : toolService.getResources()) {
            server.addResource(resource);
        }
        for (var template : toolService.getResourceTemplates()) {
            server.addResourceTemplate(template);
        }

        return server;
    }
}
