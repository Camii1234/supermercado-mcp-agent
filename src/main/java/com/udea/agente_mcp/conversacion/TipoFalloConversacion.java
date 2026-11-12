package com.udea.agente_mcp.conversacion;

import com.udea.agente_mcp.mcp.TipoFalloMcp;

/** Fallos que impiden obtener una respuesta del asistente, con su mensaje para el usuario. */
public enum TipoFalloConversacion {

    MCP_NO_DISPONIBLE(TipoFalloMcp.SERVIDOR_NO_DISPONIBLE.mensajeUsuario()),
    MCP_TIEMPO_AGOTADO(TipoFalloMcp.TIEMPO_AGOTADO.mensajeUsuario()),
    MCP_ERROR_PROTOCOLO(TipoFalloMcp.ERROR_PROTOCOLO.mensajeUsuario()),
    MODELO_NO_DISPONIBLE("El modelo de lenguaje no está disponible. Verifica la configuración de "
            + "GOOGLE_API_KEY y GEMINI_MODEL e intenta de nuevo."),
    TIEMPO_AGOTADO("El asistente tardó demasiado en responder. Intenta de nuevo; si estabas haciendo un "
            + "cambio en tu pedido, consúltalo antes de repetirlo."),
    INTERNO("Ocurrió un error inesperado en el asistente. Intenta de nuevo.");

    private final String mensajeUsuario;

    TipoFalloConversacion(String mensajeUsuario) {
        this.mensajeUsuario = mensajeUsuario;
    }

    public String mensajeUsuario() {
        return mensajeUsuario;
    }

    static TipoFalloConversacion desde(TipoFalloMcp tipo) {
        return switch (tipo) {
            case SERVIDOR_NO_DISPONIBLE -> MCP_NO_DISPONIBLE;
            case TIEMPO_AGOTADO -> MCP_TIEMPO_AGOTADO;
            case ERROR_PROTOCOLO -> MCP_ERROR_PROTOCOLO;
            case DESCONOCIDO -> INTERNO;
        };
    }
}
