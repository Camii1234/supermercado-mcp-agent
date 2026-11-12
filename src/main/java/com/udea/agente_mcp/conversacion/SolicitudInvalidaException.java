package com.udea.agente_mcp.conversacion;

/** La solicitud del usuario no cumple las validaciones de entrada del asistente. */
public class SolicitudInvalidaException extends RuntimeException {

    public SolicitudInvalidaException(String mensaje) {
        super(mensaje);
    }
}
