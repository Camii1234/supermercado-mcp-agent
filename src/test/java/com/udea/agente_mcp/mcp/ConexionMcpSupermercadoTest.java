package com.udea.agente_mcp.mcp;

import com.google.adk.tools.BaseTool;
import com.google.adk.tools.mcp.McpTool;
import com.udea.agente_mcp.config.ConfiguracionAgente;
import com.udea.agente_mcp.soporte.ServidorMcpFalso;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConexionMcpSupermercadoTest {

    private static final ConfiguracionAgente CONFIGURACION = ConfiguracionAgente.desde(Map.of(
            "MCP_CONNECT_TIMEOUT_SECONDS", "3",
            "MCP_READ_TIMEOUT_SECONDS", "5",
            "MCP_NOTIFICATION_TIMEOUT_SECONDS", "1")::get);

    @Test
    void crearLaConexionNoAbreSesionesMcp() {
        ServidorMcpFalso servidor = ServidorMcpFalso.conLasDiezHerramientas();

        try (ConexionMcpSupermercado conexion = new ConexionMcpSupermercado(CONFIGURACION, servidor)) {
            assertThat(conexion.url()).isEqualTo("http://localhost:8088/mcp");
            assertThat(servidor.sesionesIniciadas()).isZero();
        }
    }

    @Test
    void elToolsetDeAdkDescubreLasDiezHerramientasDelServidor() {
        ServidorMcpFalso servidor = ServidorMcpFalso.conLasDiezHerramientas();

        try (ConexionMcpSupermercado conexion = new ConexionMcpSupermercado(CONFIGURACION, servidor)) {
            List<BaseTool> herramientas = conexion.descubrirHerramientas(Duration.ofSeconds(10));

            assertThat(herramientas).hasSize(10).allMatch(McpTool.class::isInstance);
            assertThat(herramientas).extracting(BaseTool::name)
                    .containsExactlyInAnyOrderElementsOf(HerramientasSupermercado.TODAS);
            assertThat(herramientas).allSatisfy(h -> assertThat(h.declaration()).isPresent());
            assertThat(servidor.sesionesIniciadas()).isEqualTo(1);
        }
    }

    @Test
    void soloSeExponenAlModeloLasHerramientasDelContrato() {
        List<String> nombres = new ArrayList<>(HerramientasSupermercado.TODAS);
        nombres.add("borrarBaseDeDatos");
        ServidorMcpFalso servidor = ServidorMcpFalso.conHerramientas(nombres);

        try (ConexionMcpSupermercado conexion = new ConexionMcpSupermercado(CONFIGURACION, servidor)) {
            assertThat(conexion.descubrirHerramientas(Duration.ofSeconds(10))).extracting(BaseTool::name)
                    .hasSize(10).doesNotContain("borrarBaseDeDatos");
            assertThat(conexion.listarHerramientasDelServidor()).hasSize(11).contains("borrarBaseDeDatos");
        }
    }

    @Test
    void elDiagnosticoVerificaQueEstenLasDiezHerramientas() {
        ServidorMcpFalso completo = ServidorMcpFalso.conLasDiezHerramientas();
        ServidorMcpFalso incompleto = ServidorMcpFalso.conHerramientas(HerramientasSupermercado.TODAS.subList(0, 8));

        try (ConexionMcpSupermercado conexionCompleta = new ConexionMcpSupermercado(CONFIGURACION, completo);
             ConexionMcpSupermercado conexionIncompleta = new ConexionMcpSupermercado(CONFIGURACION, incompleto)) {
            DiagnosticoMcp.EstadoMcp estadoCompleto = new DiagnosticoMcp(conexionCompleta).verificar();
            DiagnosticoMcp.EstadoMcp estadoIncompleto = new DiagnosticoMcp(conexionIncompleta).verificar();

            assertThat(estadoCompleto.disponible()).isTrue();
            assertThat(estadoCompleto.herramientasCompletas()).isTrue();
            assertThat(estadoCompleto.herramientas()).hasSize(10);
            assertThat(estadoIncompleto.herramientasCompletas()).isFalse();
            assertThat(estadoIncompleto.faltantes()).containsExactlyInAnyOrder("listarPedidos", "cancelarPedido");
        }
    }

    @Test
    void servidorNoDisponibleSeInformaComoFalloClasificado() throws IOException {
        ConfiguracionAgente sinServidor = ConfiguracionAgente.desde(Map.of(
                "MCP_SERVER_URL", "http://127.0.0.1:" + puertoLibre() + "/mcp",
                "MCP_CONNECT_TIMEOUT_SECONDS", "3",
                "MCP_NOTIFICATION_TIMEOUT_SECONDS", "1")::get);

        try (ConexionMcpSupermercado conexion = new ConexionMcpSupermercado(sinServidor)) {
            assertThatThrownBy(conexion::listarHerramientasDelServidor)
                    .isInstanceOfSatisfying(FalloMcpException.class,
                            e -> assertThat(e.tipo()).isEqualTo(TipoFalloMcp.SERVIDOR_NO_DISPONIBLE));
            assertThatThrownBy(() -> conexion.descubrirHerramientas(Duration.ofSeconds(60)))
                    .isInstanceOfSatisfying(FalloMcpException.class,
                            e -> assertThat(e.tipo()).isEqualTo(TipoFalloMcp.SERVIDOR_NO_DISPONIBLE));

            DiagnosticoMcp.EstadoMcp estado = new DiagnosticoMcp(conexion).verificar();
            assertThat(estado.disponible()).isFalse();
            assertThat(estado.fallo()).isEqualTo("SERVIDOR_NO_DISPONIBLE");
        }
    }

    @Test
    void cerrarLaConexionEsIdempotente() {
        ConexionMcpSupermercado conexion = new ConexionMcpSupermercado(CONFIGURACION, ServidorMcpFalso.conLasDiezHerramientas());
        conexion.descubrirHerramientas(Duration.ofSeconds(10));

        conexion.close();
        conexion.close();
    }

    static int puertoLibre() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
