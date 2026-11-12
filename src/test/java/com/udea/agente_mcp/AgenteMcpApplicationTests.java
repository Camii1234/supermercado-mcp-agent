package com.udea.agente_mcp;

import com.google.adk.agents.BaseAgent;
import com.google.adk.runner.Runner;
import com.udea.agente_mcp.agente.FabricaAsistenteSupermercado;
import com.udea.agente_mcp.config.ConfiguracionAgente;
import com.udea.agente_mcp.conversacion.ServicioConversacion;
import com.udea.agente_mcp.mcp.ConexionMcpSupermercado;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Carga completa del contexto sin servidor MCP ni API key: crear el agente no abre conexiones.
 * La URL MCP de la prueba apunta a un puerto sin servicio para demostrar que no se usa al arrancar.
 */
@SpringBootTest(properties = {
        "MCP_VERIFY_ON_STARTUP=false",
        "MCP_SERVER_URL=http://127.0.0.1:9/mcp"
})
class AgenteMcpApplicationTests {

    @Autowired
    private ConfiguracionAgente configuracion;

    @Autowired
    private ConexionMcpSupermercado conexion;

    @Autowired
    private BaseAgent asistente;

    @Autowired
    private Runner runner;

    @Autowired
    private ServicioConversacion servicioConversacion;

    @Test
    void contextLoads() {
        assertThat(servicioConversacion).isNotNull();
        assertThat(asistente.name()).isEqualTo(FabricaAsistenteSupermercado.NOMBRE);
        assertThat(runner.agent()).isSameAs(asistente);
    }

    @Test
    void laUrlMcpSeTomaDeLaConfiguracionDeSpringOVariablesDeEntorno() {
        assertThat(configuracion.urlServidorMcp()).hasToString("http://127.0.0.1:9/mcp");
        assertThat(conexion.url()).isEqualTo("http://127.0.0.1:9/mcp");
        assertThat(configuracion.verificarMcpAlIniciar()).isFalse();
        // Sin GEMINI_MODEL en la configuración de la prueba se usa el modelo predeterminado.
        assertThat(configuracion.modelo()).isEqualTo("gemini-3.6-flash");
    }
}
