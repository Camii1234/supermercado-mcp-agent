package com.udea.agente_mcp.mcp;

import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpTransportException;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/**
 * Traduce excepciones técnicas (normalmente envueltas por ADK, Reactor o el SDK MCP) a una
 * categoría comprensible. Recorre toda la cadena de causas porque ADK y el SDK envuelven la causa
 * original en excepciones genéricas.
 */
public final class ClasificadorFallosMcp {

    private static final int MAXIMO_CAUSAS = 25;

    private ClasificadorFallosMcp() {
    }

    public static TipoFalloMcp clasificar(Throwable error) {
        List<Throwable> cadena = cadenaDeCausas(error);
        if (cadena.stream().anyMatch(ClasificadorFallosMcp::esServidorNoDisponible)) {
            return TipoFalloMcp.SERVIDOR_NO_DISPONIBLE;
        }
        if (cadena.stream().anyMatch(ClasificadorFallosMcp::esTiempoAgotado)) {
            return TipoFalloMcp.TIEMPO_AGOTADO;
        }
        if (cadena.stream().anyMatch(t -> t instanceof McpError || t instanceof McpTransportException)) {
            return TipoFalloMcp.ERROR_PROTOCOLO;
        }
        return TipoFalloMcp.DESCONOCIDO;
    }

    private static boolean esServidorNoDisponible(Throwable t) {
        return t instanceof ConnectException
                || t instanceof HttpConnectTimeoutException
                || t instanceof UnknownHostException
                || t instanceof NoRouteToHostException
                || t instanceof ClosedChannelException;
    }

    private static boolean esTiempoAgotado(Throwable t) {
        if (t instanceof TimeoutException || t instanceof HttpTimeoutException || t instanceof SocketTimeoutException) {
            return true;
        }
        // Reactor informa los tiempos agotados de block()/timeout() con este texto.
        String mensaje = t.getMessage();
        return mensaje != null && mensaje.toLowerCase(Locale.ROOT).contains("did not observe any item");
    }

    private static List<Throwable> cadenaDeCausas(Throwable error) {
        List<Throwable> cadena = new ArrayList<>();
        Set<Throwable> vistas = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Throwable> porRevisar = new ArrayList<>();
        if (error != null) {
            porRevisar.add(error);
        }
        while (!porRevisar.isEmpty() && cadena.size() < MAXIMO_CAUSAS) {
            Throwable actual = porRevisar.remove(0);
            if (!vistas.add(actual)) {
                continue;
            }
            cadena.add(actual);
            if (actual.getCause() != null) {
                porRevisar.add(actual.getCause());
            }
            // Reactor adjunta la causa de block() como excepción suprimida.
            Collections.addAll(porRevisar, actual.getSuppressed());
        }
        return cadena;
    }
}
