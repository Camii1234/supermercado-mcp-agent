package com.udea.agente_mcp.mcp;

import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransporteMcpConNotificacionesAcotadasTest {

    private static final Duration ESPERA = Duration.ofMillis(200);

    /** Simula la respuesta 202 cuyo cuerpo nunca termina: los envíos no se completan nunca. */
    private final TransporteQueNoTermina delegado = new TransporteQueNoTermina();
    private final TransporteMcpConNotificacionesAcotadas transporte =
            new TransporteMcpConNotificacionesAcotadas(delegado, ESPERA, ESPERA);

    @Test
    void unaNotificacionSinConfirmarSeDaPorEnviadaAlVencerLaEspera() {
        McpSchema.JSONRPCNotification notificacion =
                new McpSchema.JSONRPCNotification("2.0", McpSchema.METHOD_NOTIFICATION_INITIALIZED, null);

        long inicio = System.nanoTime();
        transporte.sendMessage(notificacion).block(Duration.ofSeconds(5));
        long transcurrido = Duration.ofNanos(System.nanoTime() - inicio).toMillis();

        assertThat(delegado.ultimoMensaje.get()).isSameAs(notificacion);
        assertThat(transcurrido).isLessThan(3000);
    }

    @Test
    void lasPeticionesNoSeAcotanNiSeOcultan() {
        McpSchema.JSONRPCRequest peticion = new McpSchema.JSONRPCRequest("2.0", McpSchema.METHOD_TOOLS_LIST, 1, null);

        assertThatThrownBy(() -> transporte.sendMessage(peticion).block(Duration.ofMillis(600)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Timeout");
        assertThat(delegado.ultimoMensaje.get()).isSameAs(peticion);
    }

    @Test
    void elCierreNoBloqueaIndefinidamente() {
        long inicio = System.nanoTime();
        transporte.closeGracefully().block(Duration.ofSeconds(5));

        assertThat(Duration.ofNanos(System.nanoTime() - inicio).toMillis()).isLessThan(3000);
    }

    @Test
    void delegaLasDemasOperaciones() {
        assertThat(transporte.protocolVersions()).containsExactly("2025-06-18");
        assertThat(transporte.unmarshalFrom("dato", new TypeRef<String>() {
        })).isEqualTo("dato");
    }

    private static final class TransporteQueNoTermina implements McpClientTransport {

        private final AtomicReference<McpSchema.JSONRPCMessage> ultimoMensaje = new AtomicReference<>();

        @Override
        public Mono<Void> connect(Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> manejador) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> sendMessage(McpSchema.JSONRPCMessage mensaje) {
            ultimoMensaje.set(mensaje);
            return Mono.never();
        }

        @Override
        public Mono<Void> closeGracefully() {
            return Mono.never();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T unmarshalFrom(Object datos, TypeRef<T> tipo) {
            return (T) datos;
        }

        @Override
        public List<String> protocolVersions() {
            return List.of("2025-06-18");
        }
    }
}
