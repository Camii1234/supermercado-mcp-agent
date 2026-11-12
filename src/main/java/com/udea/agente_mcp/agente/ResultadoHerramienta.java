package com.udea.agente_mcp.agente;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lectura de las respuestas de herramientas MCP tal como las entrega Google ADK.
 *
 * <p>ADK convierte el resultado MCP en un mapa: si el servidor marcó {@code isError}, el mapa trae
 * la clave {@code error}; si no, trae {@code text_output} con una lista de objetos JSON. Las
 * operaciones de pedido del servidor devuelven {@code {mensaje, pedido}} y consultarPedido devuelve
 * el pedido directamente.
 */
final class ResultadoHerramienta {

    private ResultadoHerramienta() {
    }

    static boolean esError(Object respuesta) {
        return respuesta instanceof Map<?, ?> mapa && mapa.containsKey("error");
    }

    /** Pedido contenido en la respuesta exitosa, si lo hay. */
    static Optional<Map<?, ?>> pedido(Object respuesta) {
        if (!(respuesta instanceof Map<?, ?> mapa) || esError(respuesta)
                || !(mapa.get("text_output") instanceof List<?> salidas) || salidas.isEmpty()
                || !(salidas.get(0) instanceof Map<?, ?> objeto)) {
            return Optional.empty();
        }
        if (objeto.get("pedido") instanceof Map<?, ?> pedido) {
            return Optional.of(pedido);
        }
        return objeto.containsKey("id") && objeto.containsKey("estado") ? Optional.of(objeto) : Optional.empty();
    }

    static Optional<Long> idPedido(Object respuesta) {
        return pedido(respuesta).flatMap(p -> comoLong(p.get("id")));
    }

    static Optional<String> estadoPedido(Object respuesta) {
        return pedido(respuesta).map(p -> p.get("estado")).map(String::valueOf);
    }

    /** Convierte un id recibido como número o texto ("12") en Long. */
    static Optional<Long> comoLong(Object valor) {
        if (valor instanceof Number numero) {
            return Optional.of(numero.longValue());
        }
        if (valor instanceof String texto) {
            try {
                return Optional.of(Long.parseLong(texto.trim()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
