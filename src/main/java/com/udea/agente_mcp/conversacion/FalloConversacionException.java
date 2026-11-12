package com.udea.agente_mcp.conversacion;

/** No fue posible obtener una respuesta del asistente para el mensaje del usuario. */
public class FalloConversacionException extends RuntimeException {

    private final TipoFalloConversacion tipo;
    private final String sesionId;

    public FalloConversacionException(TipoFalloConversacion tipo, String sesionId, Throwable causa) {
        super(tipo.mensajeUsuario(), causa);
        this.tipo = tipo;
        this.sesionId = sesionId;
    }

    public TipoFalloConversacion tipo() {
        return tipo;
    }

    public String sesionId() {
        return sesionId;
    }
}
