package com.udea.agente_mcp;

import com.google.adk.agents.BaseAgent;
import com.udea.agente_mcp.agente.FabricaAsistenteSupermercado;
import com.udea.agente_mcp.config.ConfiguracionAgente;
import com.udea.agente_mcp.mcp.ConexionMcpSupermercado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Punto de entrada para la interfaz de desarrollo de Google ADK ({@code AdkWebServer}).
 *
 * <p>ADK busca en las clases compiladas un campo {@code public static ROOT_AGENT} de tipo
 * {@link BaseAgent}. La configuración se toma de las variables de entorno (ver
 * {@link ConfiguracionAgente}); crear el agente no abre conexiones: la sesión MCP se inicia la
 * primera vez que el agente necesita las herramientas.
 */
public final class AsistenteSupermercadoAgent {

    private static final Logger log = LoggerFactory.getLogger(AsistenteSupermercadoAgent.class);

    public static final BaseAgent ROOT_AGENT = crearAgenteRaiz();

    private AsistenteSupermercadoAgent() {
    }

    private static BaseAgent crearAgenteRaiz() {
        ConfiguracionAgente configuracion = ConfiguracionAgente.desdeEntorno();
        ConexionMcpSupermercado conexion = new ConexionMcpSupermercado(configuracion);
        Runtime.getRuntime().addShutdownHook(new Thread(conexion::close, "cierre-conexion-mcp"));
        log.info("Asistente de Supermercado listo: modelo {}, servidor MCP {}", configuracion.modelo(), conexion.url());
        if (!ConfiguracionAgente.credencialesGeminiConfiguradas(System::getenv)) {
            log.warn("No hay credenciales de Gemini: define la variable de entorno {} antes de conversar.",
                    ConfiguracionAgente.VAR_CLAVE_GOOGLE);
        }
        return FabricaAsistenteSupermercado.crear(configuracion.modelo(), conexion.toolset());
    }
}
