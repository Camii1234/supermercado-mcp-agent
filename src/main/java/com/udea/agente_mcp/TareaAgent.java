package com.udea.agente_mcp;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.mcp.McpToolset;
import com.google.adk.tools.mcp.StreamableHttpServerParameters;

import java.time.Duration;
import java.util.List;

public class TareaAgent {
    public static BaseAgent ROOT_AGENT= buildAgent();

    private static BaseAgent buildAgent() {
        StreamableHttpServerParameters params = StreamableHttpServerParameters.builder()
                .url("http://localhost:8088/mcp")
                .timeout(Duration.ofSeconds(30L))
                .build();
        McpToolset mcpToolset = new McpToolset(params);

        try{
            List<BaseTool> mcpTools = mcpToolset.getTools(null)
                    .toList()
                    .blockingGet();
            System.out.println("Tools MCP: " +mcpTools.size());

            return LlmAgent.builder()
                    .name("TareaAgent")
                    .model("gemini-2.5-flash")
                    .description("Agente de Tareas")
                    .instruction("Usa las Tools del MCP de tareas para administrar tus pendientes")
                    .tools(mcpTools)
                    .build();


        }catch (Exception e) {
            System.err.println("MCP no disponible: "+ e.getMessage());
           return LlmAgent.builder()
                   .name("TareaAgent")
                   .model("gemini-2.5-flash")
                           .description("Agente de Tareas no disponible")
                           .instruction("El servidor MCP  no esta disponible")
                            .build();
        }


           }
}
