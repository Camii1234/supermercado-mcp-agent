package com.udea.agente_mcp.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Hooks;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Registro de los errores que Reactor descarta después de cancelar un flujo.
 *
 * <p>Al cerrar una sesión MCP, el SDK cancela la petición HTTP del flujo SSE pendiente y el
 * cliente HTTP de Java emite "Request cancelled" cuando ya nadie lo escucha. Reactor lo registra
 * por defecto como ERROR con la traza completa, aunque es el resultado esperado del cierre. Este
 * gancho baja a DEBUG únicamente esas cancelaciones; cualquier otro error descartado se sigue
 * registrando como ERROR.
 */
final class ErroresDescartadosReactor {

    private static final Logger log = LoggerFactory.getLogger(ErroresDescartadosReactor.class);
    private static final AtomicBoolean INSTALADO = new AtomicBoolean();

    private ErroresDescartadosReactor() {
    }

    static void instalar() {
        if (INSTALADO.compareAndSet(false, true)) {
            Hooks.onErrorDropped(ErroresDescartadosReactor::registrar);
        }
    }

    static boolean esCancelacion(Throwable error) {
        for (Throwable actual = error; actual != null; actual = actual.getCause()) {
            if (actual instanceof CancellationException) {
                return true;
            }
            if (actual.getCause() == actual) {
                break;
            }
        }
        return false;
    }

    private static void registrar(Throwable error) {
        if (esCancelacion(error)) {
            log.debug("Petición MCP cancelada durante el cierre de la sesión: {}", error.toString());
        } else {
            log.error("Error descartado por Reactor", error);
        }
    }
}
