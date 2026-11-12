package com.udea.agente_mcp.agente;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.tools.mcp.McpToolset;
import com.udea.agente_mcp.AsistenteSupermercadoAgent;
import com.udea.agente_mcp.config.ConfiguracionAgente;
import com.udea.agente_mcp.mcp.ConexionMcpSupermercado;
import com.udea.agente_mcp.mcp.HerramientasSupermercado;
import com.udea.agente_mcp.soporte.ServidorMcpFalso;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FabricaAsistenteSupermercadoTest {

    @Test
    void creaElAgenteConNombreModeloToolsetMcpYCallbacks() {
        try (ConexionMcpSupermercado conexion = new ConexionMcpSupermercado(
                ConfiguracionAgente.desde(Map.<String, String>of()::get), ServidorMcpFalso.conLasDiezHerramientas())) {
            LlmAgent agente = FabricaAsistenteSupermercado.crear(ConfiguracionAgente.MODELO_PREDETERMINADO, conexion.toolset());

            assertThat(agente.name()).isEqualTo("asistente_supermercado");
            assertThat(agente.description()).contains("Asistente de Supermercado");
            assertThat(agente.model()).isPresent();
            assertThat(agente.model().get().modelName()).contains("gemini-3.6-flash");
            assertThat(agente.toolsUnion()).containsExactly(conexion.toolset());
            assertThat(agente.toolsUnion().get(0)).isInstanceOf(McpToolset.class);
            assertThat(agente.beforeToolCallback()).hasSize(1);
            assertThat(agente.afterToolCallback()).hasSize(1);
            assertThat(agente.onToolErrorCallback()).hasSize(1);
            assertThat(agente.generateContentConfig()).hasValueSatisfying(
                    c -> assertThat(c.temperature()).contains(FabricaAsistenteSupermercado.TEMPERATURA));
        }
    }

    @Test
    void laInstruccionContieneLasReglasConversacionalesClave() {
        String instruccion = InstruccionesAgente.cargar();

        assertThat(instruccion)
                .contains("Asistente de Supermercado")
                .contains("español")
                .contains("Nunca respondas de memoria")
                .contains("nombre completo, teléfono y dirección")
                .contains("Explorar el catálogo NO es pedir")
                .contains("¿Confirmas el pedido por $22.400?")
                .contains("NO son confirmación")
                .contains("Nunca afirmes que una operación se realizó")
                .contains("{pedido_actual_id?}")
                .contains("{pedido_actual_estado?}");
        HerramientasSupermercado.TODAS.forEach(nombre -> assertThat(instruccion).contains(nombre));
    }

    @Test
    void laInstruccionSoloUsaMarcadoresDeEstadoOpcionales() {
        // ADK interpreta todo texto entre llaves como variable de estado; uno no opcional y ausente
        // haría fallar cada turno.
        String sinMarcadores = InstruccionesAgente.cargar()
                .replace("{pedido_actual_id?}", "")
                .replace("{pedido_actual_estado?}", "");

        assertThat(sinMarcadores).doesNotContain("{").doesNotContain("}");
    }

    @Test
    void rechazaUnModeloVacio() {
        try (ConexionMcpSupermercado conexion = new ConexionMcpSupermercado(
                ConfiguracionAgente.desde(Map.<String, String>of()::get), ServidorMcpFalso.conLasDiezHerramientas())) {
            assertThatThrownBy(() -> FabricaAsistenteSupermercado.crear(" ", conexion.toolset()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void exponeElRootAgentQueBuscaLaInterfazDeDesarrolloDeAdk() throws Exception {
        Field campo = AsistenteSupermercadoAgent.class.getField("ROOT_AGENT");

        assertThat(Modifier.isStatic(campo.getModifiers())).isTrue();
        assertThat(Modifier.isPublic(campo.getModifiers())).isTrue();
        assertThat(BaseAgent.class).isAssignableFrom(campo.getType());
        BaseAgent agente = (BaseAgent) campo.get(null);
        assertThat(agente).isInstanceOf(LlmAgent.class);
        assertThat(agente.name()).isEqualTo(FabricaAsistenteSupermercado.NOMBRE);
    }
}
