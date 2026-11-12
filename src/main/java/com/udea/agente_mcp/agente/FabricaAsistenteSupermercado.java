package com.udea.agente_mcp.agente;

import com.google.adk.agents.LlmAgent;
import com.google.adk.models.BaseLlm;
import com.google.adk.tools.BaseToolset;
import com.google.genai.types.GenerateContentConfig;

import java.util.Objects;

/**
 * Construye el agente Google ADK del Asistente de Supermercado: modelo, instrucción, toolset MCP y
 * callbacks de control.
 */
public final class FabricaAsistenteSupermercado {

    /** Nombre del agente en ADK (también es el nombre de la aplicación en la interfaz de desarrollo). */
    public static final String NOMBRE = "asistente_supermercado";
    public static final String DESCRIPCION =
            "Asistente de Supermercado: atiende pedidos a domicilio en español usando las herramientas del "
                    + "servidor MCP del supermercado.";
    /** Temperatura baja para respuestas consistentes al usar herramientas. */
    static final float TEMPERATURA = 0.2f;

    private FabricaAsistenteSupermercado() {
    }

    /** Agente con un modelo Gemini identificado por nombre (por ejemplo, gemini-3.6-flash). */
    public static LlmAgent crear(String modelo, BaseToolset herramientasMcp) {
        if (modelo == null || modelo.isBlank()) {
            throw new IllegalArgumentException("El nombre del modelo es obligatorio.");
        }
        return base(herramientasMcp).model(modelo).build();
    }

    /** Agente con una instancia de modelo concreta (se usa en las pruebas con un modelo simulado). */
    public static LlmAgent crear(BaseLlm modelo, BaseToolset herramientasMcp) {
        Objects.requireNonNull(modelo, "modelo");
        return base(herramientasMcp).model(modelo).build();
    }

    private static LlmAgent.Builder base(BaseToolset herramientasMcp) {
        Objects.requireNonNull(herramientasMcp, "herramientasMcp");
        return LlmAgent.builder()
                .name(NOMBRE)
                .description(DESCRIPCION)
                .instruction(InstruccionesAgente.cargar())
                .generateContentConfig(GenerateContentConfig.builder().temperature(TEMPERATURA).build())
                .tools(herramientasMcp)
                .beforeToolCallbackSync(new GuardiaOperacionesCriticas(new PoliticaAutorizacion()))
                .afterToolCallbackSync(new RegistroContextoPedido())
                .onToolErrorCallbackSync(new ManejadorErroresHerramientas())
                .disallowTransferToParent(true)
                .disallowTransferToPeers(true);
    }
}
