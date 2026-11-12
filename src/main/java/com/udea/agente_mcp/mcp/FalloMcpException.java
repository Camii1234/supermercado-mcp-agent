package com.udea.agente_mcp.mcp;

/** Fallo técnico al comunicarse con el servidor MCP, ya clasificado. */
public class FalloMcpException extends RuntimeException {

    private final TipoFalloMcp tipo;

    public FalloMcpException(TipoFalloMcp tipo, Throwable causa) {
        super(tipo.mensajeUsuario(), causa);
        this.tipo = tipo;
    }

    public TipoFalloMcp tipo() {
        return tipo;
    }
}
