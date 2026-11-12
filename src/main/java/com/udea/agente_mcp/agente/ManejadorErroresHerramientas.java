package com.udea.agente_mcp.agente;

import com.google.adk.agents.Callbacks;
import com.google.adk.agents.InvocationContext;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ToolContext;
import com.udea.agente_mcp.mcp.ClasificadorFallosMcp;
import com.udea.agente_mcp.mcp.TipoFalloMcp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

/**
 * Callback de ADK para excepciones técnicas al ejecutar una herramienta MCP (servidor caído,
 * tiempo agotado, error de protocolo). Convierte la excepción en una respuesta de error clara para
 * el modelo, sin trazas internas, y deja claro que el resultado no está confirmado.
 *
 * <p>Los errores de negocio que devuelve el servidor (stock insuficiente, pedido inexistente,
 * estado no permitido) no pasan por aquí: llegan como respuesta normal con la clave {@code error}.
 */
public final class ManejadorErroresHerramientas implements Callbacks.OnToolErrorCallbackSync {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErroresHerramientas.class);

    @Override
    public Optional<Map<String, Object>> call(InvocationContext invocacion, BaseTool herramienta,
                                              Map<String, Object> argumentos, ToolContext contexto, Exception error) {
        TipoFalloMcp tipo = ClasificadorFallosMcp.clasificar(error);
        log.warn("Fallo técnico al ejecutar la herramienta MCP {}: {} ({})",
                herramienta.name(), tipo, error.getClass().getSimpleName());
        return Optional.of(Map.of(
                "error", tipo.mensajeUsuario(),
                "tipoFallo", tipo.name(),
                "resultadoVerificado", false));
    }
}
