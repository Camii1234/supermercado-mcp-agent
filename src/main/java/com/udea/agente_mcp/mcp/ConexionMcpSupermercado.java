package com.udea.agente_mcp.mcp;

import com.google.adk.JsonBaseModel;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.mcp.McpSessionManager;
import com.google.adk.tools.mcp.McpToolset;
import com.google.adk.tools.mcp.McpTransportBuilder;
import com.google.adk.tools.mcp.StreamableHttpServerParameters;
import com.udea.agente_mcp.config.ConfiguracionAgente;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Conexión del agente con el servidor MCP del supermercado (Streamable HTTP).
 *
 * <p>Encapsula los componentes MCP de Google ADK: {@link StreamableHttpServerParameters},
 * {@link McpSessionManager} y {@link McpToolset}. El toolset abre la sesión MCP de forma perezosa
 * (la primera vez que el agente necesita las herramientas), la reutiliza y la reabre si falla, por
 * lo que crear esta clase no realiza llamadas de red. Las herramientas expuestas al modelo se
 * limitan al contrato de {@link HerramientasSupermercado}.
 */
public final class ConexionMcpSupermercado implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ConexionMcpSupermercado.class);

    private final String url;
    private final McpSessionManager gestorSesiones;
    private final McpToolset toolset;

    public ConexionMcpSupermercado(ConfiguracionAgente configuracion) {
        this(configuracion, new ConstructorTransporteMcp(
                configuracion.tiempoNotificacionMcp(), configuracion.tiempoNotificacionMcp()));
    }

    /** Permite sustituir el transporte (por ejemplo, por un servidor MCP en memoria en las pruebas). */
    public ConexionMcpSupermercado(ConfiguracionAgente configuracion, McpTransportBuilder constructorTransporte) {
        Objects.requireNonNull(configuracion, "configuracion");
        ErroresDescartadosReactor.instalar();
        this.url = configuracion.urlServidorMcp().toString();
        StreamableHttpServerParameters parametros = StreamableHttpServerParameters.builder()
                .url(url)
                .timeout(configuracion.tiempoConexionMcp())
                .readTimeout(configuracion.tiempoLecturaMcp())
                .build();
        this.gestorSesiones = new McpSessionManager(parametros, constructorTransporte);
        this.toolset = new McpToolset(gestorSesiones, JsonBaseModel.getMapper(), HerramientasSupermercado.TODAS);
    }

    /** Toolset de ADK que se registra en el agente. */
    public McpToolset toolset() {
        return toolset;
    }

    public String url() {
        return url;
    }

    /**
     * Descubre, mediante el toolset de ADK, las herramientas que se expondrían al modelo.
     *
     * @throws FalloMcpException si el servidor no responde, se agota el tiempo o falla el protocolo
     */
    public List<BaseTool> descubrirHerramientas(Duration tiempoMaximo) {
        try {
            return toolset.getTools(null)
                    .toList()
                    .timeout(tiempoMaximo.toMillis(), TimeUnit.MILLISECONDS)
                    .blockingGet();
        } catch (RuntimeException e) {
            throw new FalloMcpException(ClasificadorFallosMcp.clasificar(e), e);
        }
    }

    /**
     * Abre una sesión MCP de diagnóstico independiente, lista todas las herramientas que publica el
     * servidor (sin filtrar) y cierra la sesión.
     *
     * @throws FalloMcpException si el servidor no responde, se agota el tiempo o falla el protocolo
     */
    public List<String> listarHerramientasDelServidor() {
        McpSyncClient cliente = null;
        try {
            cliente = gestorSesiones.createSession();
            return cliente.listTools().tools().stream().map(McpSchema.Tool::name).sorted().toList();
        } catch (RuntimeException e) {
            throw new FalloMcpException(ClasificadorFallosMcp.clasificar(e), e);
        } finally {
            if (cliente != null) {
                cerrar(cliente);
            }
        }
    }

    /** Cierra la sesión MCP del toolset. Es idempotente. */
    @Override
    public void close() {
        toolset.close();
        log.debug("Conexión MCP con {} cerrada.", url);
    }

    private static void cerrar(McpSyncClient cliente) {
        try {
            cliente.closeGracefully();
        } catch (RuntimeException e) {
            log.debug("No se pudo cerrar limpiamente la sesión MCP de diagnóstico: {}", e.toString());
        }
    }
}
