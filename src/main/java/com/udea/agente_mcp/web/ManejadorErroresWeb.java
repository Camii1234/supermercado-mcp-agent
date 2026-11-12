package com.udea.agente_mcp.web;

import com.udea.agente_mcp.conversacion.FalloConversacionException;
import com.udea.agente_mcp.conversacion.SolicitudInvalidaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Convierte los errores en respuestas JSON en español, sin trazas internas. */
@RestControllerAdvice
public class ManejadorErroresWeb {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErroresWeb.class);

    /** Cuerpo de error: código estable para clientes y mensaje para personas. */
    public record RespuestaError(String codigo, String mensaje, String sesionId) {
    }

    @ExceptionHandler(SolicitudInvalidaException.class)
    ResponseEntity<RespuestaError> solicitudInvalida(SolicitudInvalidaException e) {
        return ResponseEntity.badRequest().body(new RespuestaError("SOLICITUD_INVALIDA", e.getMessage(), null));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class})
    ResponseEntity<RespuestaError> cuerpoIlegible(Exception e) {
        return ResponseEntity.badRequest().body(new RespuestaError("SOLICITUD_INVALIDA",
                "El cuerpo debe ser JSON con la forma {\"sesionId\": \"opcional\", \"mensaje\": \"texto\"}.", null));
    }

    @ExceptionHandler(FalloConversacionException.class)
    ResponseEntity<RespuestaError> falloConversacion(FalloConversacionException e) {
        HttpStatus estado = switch (e.tipo()) {
            case MCP_NO_DISPONIBLE, MODELO_NO_DISPONIBLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case MCP_TIEMPO_AGOTADO, TIEMPO_AGOTADO -> HttpStatus.GATEWAY_TIMEOUT;
            case MCP_ERROR_PROTOCOLO -> HttpStatus.BAD_GATEWAY;
            case INTERNO -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(estado).body(new RespuestaError(e.tipo().name(), e.getMessage(), e.sesionId()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<RespuestaError> inesperado(Exception e) {
        log.error("Error no controlado en la API del asistente", e);
        return ResponseEntity.internalServerError().body(new RespuestaError("INTERNO",
                "Ocurrió un error inesperado en el asistente. Intenta de nuevo.", null));
    }
}
