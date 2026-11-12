package com.udea.agente_mcp.agente;

import com.google.adk.agents.Callbacks;
import com.google.adk.agents.InvocationContext;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.udea.agente_mcp.agente.PoliticaAutorizacion.Decision;
import com.udea.agente_mcp.agente.PoliticaAutorizacion.Operacion;
import com.udea.agente_mcp.mcp.HerramientasSupermercado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Callback de ADK que se ejecuta antes de cada herramienta y protege las operaciones
 * irreversibles. No sustituye las reglas de negocio del servidor (estados, stock); solo verifica
 * que el cliente las haya autorizado en la conversación:
 *
 * <ul>
 *   <li><b>confirmarPedido</b>: el resumen del mismo pedido debe haberse consultado en un turno
 *       anterior (el cliente lo vio), sin modificaciones posteriores, y el mensaje actual del
 *       cliente debe ser una confirmación explícita.</li>
 *   <li><b>cancelarPedido</b>: el mensaje actual del cliente debe expresar claramente la intención
 *       de cancelar o responder afirmativamente a la pregunta de cancelación.</li>
 * </ul>
 *
 * <p>Si no se cumple, la herramienta no se invoca y el modelo recibe un error explicativo para que
 * pida la confirmación al cliente.
 */
public final class GuardiaOperacionesCriticas implements Callbacks.BeforeToolCallbackSync {

    private static final Logger log = LoggerFactory.getLogger(GuardiaOperacionesCriticas.class);

    private final PoliticaAutorizacion politica;

    public GuardiaOperacionesCriticas(PoliticaAutorizacion politica) {
        this.politica = Objects.requireNonNull(politica, "politica");
    }

    @Override
    public Optional<Map<String, Object>> call(InvocationContext invocacion, BaseTool herramienta,
                                              Map<String, Object> argumentos, ToolContext contexto) {
        return switch (herramienta.name()) {
            case HerramientasSupermercado.CONFIRMAR_PEDIDO -> revisarConfirmacion(invocacion, argumentos, contexto);
            case HerramientasSupermercado.CANCELAR_PEDIDO -> revisarCancelacion(argumentos, contexto);
            default -> Optional.empty();
        };
    }

    private Optional<Map<String, Object>> revisarConfirmacion(InvocationContext invocacion,
                                                             Map<String, Object> argumentos, ToolContext contexto) {
        Optional<Long> pedidoId = ResultadoHerramienta.comoLong(argumentos.get("pedidoId"));
        Optional<Long> resumenId = ResultadoHerramienta.comoLong(contexto.state().get(EstadoConversacion.RESUMEN_PEDIDO_ID));
        Object invocacionResumen = contexto.state().get(EstadoConversacion.RESUMEN_INVOCACION_ID);

        if (pedidoId.isEmpty() || resumenId.isEmpty() || !resumenId.equals(pedidoId)) {
            return bloquear(HerramientasSupermercado.CONFIRMAR_PEDIDO, pedidoId,
                    "antes de confirmar debes consultar el pedido con consultarPedido, mostrar al cliente el resumen "
                            + "(productos, cantidades, precios unitarios, subtotales y total) y preguntarle si confirma.");
        }
        if (Objects.equals(invocacionResumen, invocacion.invocationId())) {
            return bloquear(HerramientasSupermercado.CONFIRMAR_PEDIDO, pedidoId,
                    "el cliente todavía no ha visto el resumen que acabas de consultar. Muéstraselo y pregúntale "
                            + "explícitamente si confirma el pedido por el total indicado.");
        }
        Decision decision = politica.evaluar(Operacion.CONFIRMAR, textoDelCliente(contexto));
        if (!decision.autorizada()) {
            return bloquear(HerramientasSupermercado.CONFIRMAR_PEDIDO, pedidoId,
                    decision.motivo() + ". Pregunta de nuevo, por ejemplo: \"¿Confirmas el pedido por $<total>?\"");
        }
        return Optional.empty();
    }

    private Optional<Map<String, Object>> revisarCancelacion(Map<String, Object> argumentos, ToolContext contexto) {
        Decision decision = politica.evaluar(Operacion.CANCELAR, textoDelCliente(contexto));
        if (!decision.autorizada()) {
            return bloquear(HerramientasSupermercado.CANCELAR_PEDIDO,
                    ResultadoHerramienta.comoLong(argumentos.get("pedidoId")),
                    decision.motivo() + ". Identifica el pedido y pregunta al cliente si desea cancelarlo.");
        }
        return Optional.empty();
    }

    private static Optional<Map<String, Object>> bloquear(String herramienta, Optional<Long> pedidoId, String motivo) {
        log.info("Operación {} sobre el pedido {} no ejecutada: falta autorización explícita del cliente.",
                herramienta, pedidoId.map(String::valueOf).orElse("(sin id)"));
        return Optional.of(Map.of(
                "error", "Operación NO ejecutada por la política del asistente: " + motivo,
                "operacionEjecutada", false));
    }

    private static String textoDelCliente(ToolContext contexto) {
        return contexto.userContent()
                .flatMap(Content::parts)
                .map(partes -> partes.stream()
                        .map(Part::text)
                        .flatMap(Optional::stream)
                        .collect(Collectors.joining(" ")))
                .orElse("");
    }
}
