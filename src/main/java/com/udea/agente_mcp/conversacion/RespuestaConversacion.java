package com.udea.agente_mcp.conversacion;

/**
 * Respuesta del asistente a un mensaje.
 *
 * @param sesionId       sesión de la conversación (se debe reenviar en los siguientes mensajes)
 * @param respuesta      texto del asistente
 * @param pedidoActualId id del pedido en curso según el estado de la sesión, o {@code null}
 */
public record RespuestaConversacion(String sesionId, String respuesta, Long pedidoActualId) {
}
