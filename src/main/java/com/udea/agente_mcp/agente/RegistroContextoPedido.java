package com.udea.agente_mcp.agente;

import com.google.adk.agents.Callbacks;
import com.google.adk.agents.InvocationContext;
import com.google.adk.sessions.State;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ToolContext;
import com.udea.agente_mcp.mcp.HerramientasSupermercado;

import java.util.Map;
import java.util.Optional;

/**
 * Callback de ADK que se ejecuta después de cada herramienta y conserva en el estado de la sesión
 * el contexto del pedido: el id del pedido en curso, su último estado conocido y qué resumen vio
 * el cliente. Solo usa datos de respuestas exitosas del servidor MCP y no altera la respuesta que
 * recibe el modelo.
 */
public final class RegistroContextoPedido implements Callbacks.AfterToolCallbackSync {

    @Override
    public Optional<Map<String, Object>> call(InvocationContext invocacion, BaseTool herramienta,
                                              Map<String, Object> argumentos, ToolContext contexto, Object respuesta) {
        if (ResultadoHerramienta.esError(respuesta)) {
            return Optional.empty();
        }
        State estado = contexto.state();
        Optional<Long> pedidoId = ResultadoHerramienta.idPedido(respuesta);
        Optional<String> estadoPedido = ResultadoHerramienta.estadoPedido(respuesta);

        switch (herramienta.name()) {
            case HerramientasSupermercado.CREAR_PEDIDO -> pedidoId.ifPresent(id -> {
                estado.put(EstadoConversacion.PEDIDO_ACTUAL_ID, id);
                estadoPedido.ifPresent(e -> estado.put(EstadoConversacion.PEDIDO_ACTUAL_ESTADO, e));
                olvidarResumen(estado);
            });
            case HerramientasSupermercado.CONSULTAR_PEDIDO -> pedidoId.ifPresent(id -> {
                estado.put(EstadoConversacion.RESUMEN_PEDIDO_ID, id);
                estado.put(EstadoConversacion.RESUMEN_INVOCACION_ID, invocacion.invocationId());
                if (!estado.containsKey(EstadoConversacion.PEDIDO_ACTUAL_ID) && "BORRADOR".equals(estadoPedido.orElse(null))) {
                    estado.put(EstadoConversacion.PEDIDO_ACTUAL_ID, id);
                }
                actualizarEstadoSiEsActual(estado, id, estadoPedido);
            });
            case HerramientasSupermercado.AGREGAR_PRODUCTO, HerramientasSupermercado.RETIRAR_PRODUCTO,
                 HerramientasSupermercado.CONFIRMAR_PEDIDO, HerramientasSupermercado.CANCELAR_PEDIDO -> {
                // El pedido cambió: el resumen que vio el cliente ya no es vigente.
                olvidarResumen(estado);
                pedidoId.ifPresent(id -> actualizarEstadoSiEsActual(estado, id, estadoPedido));
            }
            default -> {
                // Herramientas de solo lectura del catálogo o listados: no cambian el contexto.
            }
        }
        return Optional.empty();
    }

    private static void actualizarEstadoSiEsActual(State estado, Long pedidoId, Optional<String> estadoPedido) {
        Optional<Long> actual = ResultadoHerramienta.comoLong(estado.get(EstadoConversacion.PEDIDO_ACTUAL_ID));
        if (actual.isPresent() && actual.get().equals(pedidoId)) {
            estadoPedido.ifPresent(e -> estado.put(EstadoConversacion.PEDIDO_ACTUAL_ESTADO, e));
        }
    }

    private static void olvidarResumen(State estado) {
        estado.remove(EstadoConversacion.RESUMEN_PEDIDO_ID);
        estado.remove(EstadoConversacion.RESUMEN_INVOCACION_ID);
    }
}
