package com.udea.agente_mcp.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfiguracionAgenteTest {

    @Test
    void usaValoresPredeterminadosCuandoNoHayVariables() {
        ConfiguracionAgente configuracion = ConfiguracionAgente.desde(Map.<String, String>of()::get);

        assertThat(configuracion.urlServidorMcp()).hasToString("http://localhost:8088/mcp");
        assertThat(configuracion.modelo()).isEqualTo("gemini-3.6-flash");
        assertThat(ConfiguracionAgente.MODELO_PREDETERMINADO).isEqualTo("gemini-3.6-flash");
        assertThat(configuracion.tiempoConexionMcp()).isEqualTo(Duration.ofSeconds(10));
        assertThat(configuracion.tiempoLecturaMcp()).isEqualTo(Duration.ofSeconds(30));
        assertThat(configuracion.tiempoNotificacionMcp()).isEqualTo(Duration.ofSeconds(2));
        assertThat(configuracion.tiempoRespuestaAgente()).isEqualTo(Duration.ofSeconds(120));
        assertThat(configuracion.verificarMcpAlIniciar()).isTrue();
    }

    @Test
    void laUrlMcpYElModeloSeConfiguranPorVariablesDeEntorno() {
        ConfiguracionAgente configuracion = ConfiguracionAgente.desde(Map.of(
                "MCP_SERVER_URL", " http://servidor-mcp:9090/mcp ",
                "GEMINI_MODEL", "gemini-2.5-pro",
                "MCP_READ_TIMEOUT_SECONDS", "45",
                "MCP_VERIFY_ON_STARTUP", "false")::get);

        assertThat(configuracion.urlServidorMcp()).hasToString("http://servidor-mcp:9090/mcp");
        assertThat(configuracion.modelo()).isEqualTo("gemini-2.5-pro");
        assertThat(configuracion.tiempoLecturaMcp()).isEqualTo(Duration.ofSeconds(45));
        assertThat(configuracion.verificarMcpAlIniciar()).isFalse();
    }

    @Test
    void valoresEnBlancoTomanElPredeterminado() {
        ConfiguracionAgente configuracion = ConfiguracionAgente.desde(Map.of("MCP_SERVER_URL", "  ")::get);

        assertThat(configuracion.urlServidorMcp()).hasToString(ConfiguracionAgente.URL_MCP_PREDETERMINADA);
    }

    @Test
    void rechazaUrlsQueNoSonHttp() {
        assertThatThrownBy(() -> ConfiguracionAgente.desde(Map.of("MCP_SERVER_URL", "ftp://localhost/mcp")::get))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MCP_SERVER_URL");
        assertThatThrownBy(() -> ConfiguracionAgente.desde(Map.of("MCP_SERVER_URL", "localhost:8088/mcp")::get))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaTiemposInvalidos() {
        assertThatThrownBy(() -> ConfiguracionAgente.desde(Map.of("MCP_CONNECT_TIMEOUT_SECONDS", "0")::get))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MCP_CONNECT_TIMEOUT_SECONDS");
        assertThatThrownBy(() -> ConfiguracionAgente.desde(Map.of("AGENT_RESPONSE_TIMEOUT_SECONDS", "dos")::get))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConfiguracionAgente.desde(Map.of("MCP_VERIFY_ON_STARTUP", "quizas")::get))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void detectaCredencialesDeGeminiSinExponerSuValor() {
        assertThat(ConfiguracionAgente.credencialesGeminiConfiguradas(Map.<String, String>of()::get)).isFalse();
        assertThat(ConfiguracionAgente.credencialesGeminiConfiguradas(Map.of("GOOGLE_API_KEY", " ")::get)).isFalse();
        assertThat(ConfiguracionAgente.credencialesGeminiConfiguradas(Map.of("GOOGLE_API_KEY", "valor-de-prueba")::get)).isTrue();
        assertThat(ConfiguracionAgente.credencialesGeminiConfiguradas(Map.of("GOOGLE_GENAI_USE_VERTEXAI", "TRUE")::get)).isTrue();
    }

    @Test
    void laRepresentacionTextualNoIncluyeLaClave() {
        ConfiguracionAgente configuracion = ConfiguracionAgente.desde(Map.of("GOOGLE_API_KEY", "clave-que-no-debe-aparecer")::get);

        assertThat(configuracion.toString()).doesNotContain("clave-que-no-debe-aparecer");
    }
}
