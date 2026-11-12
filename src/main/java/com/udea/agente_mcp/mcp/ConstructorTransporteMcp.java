package com.udea.agente_mcp.mcp;

import com.google.adk.tools.mcp.DefaultMcpTransportBuilder;
import com.google.adk.tools.mcp.McpTransportBuilder;
import io.modelcontextprotocol.spec.McpClientTransport;

import java.time.Duration;
import java.util.Objects;

/**
 * Punto de extensión de ADK ({@link McpTransportBuilder}) que crea el transporte Streamable HTTP
 * estándar de ADK y lo envuelve en {@link TransporteMcpConNotificacionesAcotadas}.
 */
public final class ConstructorTransporteMcp implements McpTransportBuilder {

    private final McpTransportBuilder base;
    private final Duration esperaNotificacion;
    private final Duration esperaCierre;

    public ConstructorTransporteMcp(Duration esperaNotificacion, Duration esperaCierre) {
        this(new DefaultMcpTransportBuilder(), esperaNotificacion, esperaCierre);
    }

    ConstructorTransporteMcp(McpTransportBuilder base, Duration esperaNotificacion, Duration esperaCierre) {
        this.base = Objects.requireNonNull(base, "base");
        this.esperaNotificacion = Objects.requireNonNull(esperaNotificacion, "esperaNotificacion");
        this.esperaCierre = Objects.requireNonNull(esperaCierre, "esperaCierre");
    }

    @Override
    public McpClientTransport build(Object parametrosConexion) {
        return new TransporteMcpConNotificacionesAcotadas(base.build(parametrosConexion), esperaNotificacion, esperaCierre);
    }
}
