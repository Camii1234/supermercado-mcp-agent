package com.udea.agente_mcp.mcp;

import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpTransportException;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class ClasificadorFallosMcpTest {

    @Test
    void conexionRechazadaEnvueltaEsServidorNoDisponible() {
        RuntimeException error = new RuntimeException("Failed to load tools after multiple retries",
                new RuntimeException("Client failed to initialize", new CompletionException(new ConnectException())));

        assertThat(ClasificadorFallosMcp.clasificar(error)).isEqualTo(TipoFalloMcp.SERVIDOR_NO_DISPONIBLE);
        assertThat(ClasificadorFallosMcp.clasificar(new HttpConnectTimeoutException("connect timed out")))
                .isEqualTo(TipoFalloMcp.SERVIDOR_NO_DISPONIBLE);
    }

    @Test
    void tiempoAgotadoSeDetectaPorTipoOMensajeDeReactor() {
        assertThat(ClasificadorFallosMcp.clasificar(new RuntimeException(new TimeoutException())))
                .isEqualTo(TipoFalloMcp.TIEMPO_AGOTADO);
        assertThat(ClasificadorFallosMcp.clasificar(new IllegalStateException(
                "Did not observe any item or terminal signal within 30000ms in 'map'")))
                .isEqualTo(TipoFalloMcp.TIEMPO_AGOTADO);
    }

    @Test
    void erroresDelProtocoloSeClasificanComoTales() {
        McpError errorJsonRpc = new McpError(new McpSchema.JSONRPCResponse.JSONRPCError(-32601, "Method not found", null));

        assertThat(ClasificadorFallosMcp.clasificar(new RuntimeException(errorJsonRpc)))
                .isEqualTo(TipoFalloMcp.ERROR_PROTOCOLO);
        assertThat(ClasificadorFallosMcp.clasificar(new McpTransportException("respuesta inválida")))
                .isEqualTo(TipoFalloMcp.ERROR_PROTOCOLO);
    }

    @Test
    void lasCausasSuprimidasTambienSeRevisan() {
        RuntimeException error = new RuntimeException("block terminated with an error");
        error.addSuppressed(new ConnectException());

        assertThat(ClasificadorFallosMcp.clasificar(error)).isEqualTo(TipoFalloMcp.SERVIDOR_NO_DISPONIBLE);
    }

    @Test
    void otrosErroresSonDesconocidosYSusMensajesNoTienenDetallesTecnicos() {
        assertThat(ClasificadorFallosMcp.clasificar(new IllegalStateException("otra cosa")))
                .isEqualTo(TipoFalloMcp.DESCONOCIDO);
        assertThat(ClasificadorFallosMcp.clasificar(null)).isEqualTo(TipoFalloMcp.DESCONOCIDO);
        for (TipoFalloMcp tipo : TipoFalloMcp.values()) {
            assertThat(tipo.mensajeUsuario()).doesNotContainIgnoringCase("exception").doesNotContain("http");
        }
    }
}
