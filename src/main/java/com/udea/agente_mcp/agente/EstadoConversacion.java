package com.udea.agente_mcp.agente;

/**
 * Claves del estado de sesión de ADK que usa el asistente para mantener el contexto del pedido.
 *
 * <p>Los valores provienen exclusivamente de respuestas exitosas de las herramientas MCP; el
 * agente nunca los inventa. Las dos primeras se inyectan en la instrucción del modelo.
 */
public final class EstadoConversacion {

    /** Id del pedido en curso de la conversación (devuelto por crearPedido). */
    public static final String PEDIDO_ACTUAL_ID = "pedido_actual_id";
    /** Último estado conocido del pedido en curso (BORRADOR, CONFIRMADO o CANCELADO). */
    public static final String PEDIDO_ACTUAL_ESTADO = "pedido_actual_estado";
    /** Id del pedido cuyo resumen se consultó por última vez sin cambios posteriores. */
    public static final String RESUMEN_PEDIDO_ID = "resumen_pedido_id";
    /** Invocación (turno) en que se consultó ese resumen. */
    public static final String RESUMEN_INVOCACION_ID = "resumen_invocacion_id";

    private EstadoConversacion() {
    }
}
