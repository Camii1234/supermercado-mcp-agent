package com.udea.agente_mcp.config;

import com.google.adk.agents.BaseAgent;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.runner.Runner;
import com.udea.agente_mcp.agente.FabricaAsistenteSupermercado;
import com.udea.agente_mcp.conversacion.ServicioConversacion;
import com.udea.agente_mcp.mcp.ConexionMcpSupermercado;
import com.udea.agente_mcp.mcp.DiagnosticoMcp;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Ensambla los componentes del asistente para la aplicación Spring Boot: configuración, conexión
 * MCP, agente ADK, runner con sesiones en memoria y servicio de conversación.
 */
@Configuration(proxyBeanMethods = false)
public class AgenteSupermercadoConfig {

    /** Nombre de la aplicación en el runner de ADK. */
    public static final String NOMBRE_APLICACION = "supermercado";

    @Bean
    ConfiguracionAgente configuracionAgente(Environment entorno) {
        return ConfiguracionAgente.desde(entorno::getProperty);
    }

    @Bean(destroyMethod = "close")
    ConexionMcpSupermercado conexionMcpSupermercado(ConfiguracionAgente configuracion) {
        return new ConexionMcpSupermercado(configuracion);
    }

    @Bean
    BaseAgent asistenteSupermercado(ConfiguracionAgente configuracion, ConexionMcpSupermercado conexion) {
        return FabricaAsistenteSupermercado.crear(configuracion.modelo(), conexion.toolset());
    }

    /** La conexión MCP se cierra con su propio bean; el runner no requiere cierre adicional. */
    @Bean(destroyMethod = "")
    Runner runnerAsistente(BaseAgent asistenteSupermercado) {
        return new InMemoryRunner(asistenteSupermercado, NOMBRE_APLICACION);
    }

    @Bean
    ServicioConversacion servicioConversacion(Runner runnerAsistente, ConfiguracionAgente configuracion) {
        return new ServicioConversacion(runnerAsistente, configuracion.tiempoRespuestaAgente());
    }

    @Bean
    DiagnosticoMcp diagnosticoMcp(ConexionMcpSupermercado conexion) {
        return new DiagnosticoMcp(conexion);
    }
}
