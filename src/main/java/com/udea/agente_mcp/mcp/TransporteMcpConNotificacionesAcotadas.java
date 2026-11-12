package com.udea.agente_mcp.mcp;

import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Decorador del transporte Streamable HTTP del SDK MCP que acota la espera de las notificaciones
 * del cliente y del cierre de sesión.
 *
 * <p><b>Por qué existe.</b> Según la especificación MCP, a una notificación JSON-RPC (por ejemplo
 * {@code notifications/initialized}) el servidor responde {@code 202 Accepted} sin cuerpo. El
 * transporte del SDK (0.17.2) solo da la notificación por entregada cuando termina de leer ese
 * cuerpo vacío. En el entorno del laboratorio se observó que la respuesta 202 del servidor llega
 * con {@code transfer-encoding: chunked} y nunca recibe el fragmento final, de modo que el
 * {@code initialize} del cliente se quedaba bloqueado hasta agotar el tiempo de espera y ADK no
 * podía descubrir las herramientas.
 *
 * <p><b>Qué hace.</b> Para las notificaciones (que por definición no tienen respuesta) espera como
 * máximo el tiempo configurado; si vence, la considera enviada y lo registra. Las peticiones
 * ({@code tools/list}, {@code tools/call}, ...) pasan sin cambios, con sus propios tiempos de espera,
 * así que si el servidor no hubiera procesado la notificación, la siguiente petición fallaría con
 * un error explícito. El cierre de sesión también se acota para no bloquear el apagado.
 */
public final class TransporteMcpConNotificacionesAcotadas implements McpClientTransport {

    private static final Logger log = LoggerFactory.getLogger(TransporteMcpConNotificacionesAcotadas.class);

    private final McpClientTransport delegado;
    private final Duration esperaNotificacion;
    private final Duration esperaCierre;

    public TransporteMcpConNotificacionesAcotadas(McpClientTransport delegado, Duration esperaNotificacion,
                                                 Duration esperaCierre) {
        this.delegado = Objects.requireNonNull(delegado, "delegado");
        this.esperaNotificacion = Objects.requireNonNull(esperaNotificacion, "esperaNotificacion");
        this.esperaCierre = Objects.requireNonNull(esperaCierre, "esperaCierre");
    }

    @Override
    public Mono<Void> connect(Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> manejador) {
        return delegado.connect(manejador);
    }

    @Override
    public Mono<Void> sendMessage(McpSchema.JSONRPCMessage mensaje) {
        if (!(mensaje instanceof McpSchema.JSONRPCNotification notificacion)) {
            return delegado.sendMessage(mensaje);
        }
        return delegado.sendMessage(mensaje)
                .timeout(esperaNotificacion)
                .onErrorResume(TimeoutException.class, e -> {
                    log.debug("La notificación MCP '{}' no terminó de confirmarse en {} ms; se considera enviada.",
                            notificacion.method(), esperaNotificacion.toMillis());
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Void> closeGracefully() {
        return delegado.closeGracefully()
                .timeout(esperaCierre)
                .onErrorResume(e -> {
                    log.debug("El cierre de la sesión MCP no se completó limpiamente: {}", e.toString());
                    return Mono.empty();
                });
    }

    @Override
    public void setExceptionHandler(Consumer<Throwable> manejador) {
        delegado.setExceptionHandler(manejador);
    }

    @Override
    public <T> T unmarshalFrom(Object datos, TypeRef<T> tipo) {
        return delegado.unmarshalFrom(datos, tipo);
    }

    @Override
    public List<String> protocolVersions() {
        return delegado.protocolVersions();
    }
}
