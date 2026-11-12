package com.udea.agente_mcp.mcp;

/** Categorías de fallos técnicos al comunicarse con el servidor MCP, con su mensaje para el usuario. */
public enum TipoFalloMcp {

    SERVIDOR_NO_DISPONIBLE(
            "El sistema del supermercado no está disponible en este momento. Intenta de nuevo en unos minutos."),
    TIEMPO_AGOTADO(
            "El sistema del supermercado tardó demasiado en responder. No se pudo verificar si la operación se "
                    + "realizó; consulta el pedido antes de intentarlo de nuevo."),
    ERROR_PROTOCOLO(
            "Hubo un problema de comunicación con el sistema del supermercado. Intenta de nuevo en unos minutos."),
    DESCONOCIDO(
            "Ocurrió un problema técnico al comunicarse con el sistema del supermercado y no se pudo verificar el "
                    + "resultado de la operación.");

    private final String mensajeUsuario;

    TipoFalloMcp(String mensajeUsuario) {
        this.mensajeUsuario = mensajeUsuario;
    }

    /** Mensaje en español, sin detalles técnicos, apto para mostrar al cliente. */
    public String mensajeUsuario() {
        return mensajeUsuario;
    }
}
