package com.udea.agente_mcp.mcp;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Contrato de nombres de las herramientas que publica el servidor MCP del supermercado.
 *
 * <p>Aquí solo se declaran los nombres: la descripción, el esquema de argumentos y la lógica de
 * cada herramienta los publica y ejecuta el servidor, y el agente los descubre por MCP. La lista
 * sirve para filtrar qué herramientas se exponen al modelo y para verificar la conexión.
 */
public final class HerramientasSupermercado {

    public static final String LISTAR_PRODUCTOS = "listarProductos";
    public static final String BUSCAR_PRODUCTOS = "buscarProductos";
    public static final String CONSULTAR_STOCK = "consultarStock";
    public static final String CREAR_PEDIDO = "crearPedido";
    public static final String AGREGAR_PRODUCTO = "agregarProducto";
    public static final String RETIRAR_PRODUCTO = "retirarProducto";
    public static final String CONSULTAR_PEDIDO = "consultarPedido";
    public static final String CONFIRMAR_PEDIDO = "confirmarPedido";
    public static final String LISTAR_PEDIDOS = "listarPedidos";
    public static final String CANCELAR_PEDIDO = "cancelarPedido";

    /** Las diez herramientas esperadas, en el orden en que las documenta el servidor. */
    public static final List<String> TODAS = List.of(
            LISTAR_PRODUCTOS, BUSCAR_PRODUCTOS, CONSULTAR_STOCK, CREAR_PEDIDO, AGREGAR_PRODUCTO,
            RETIRAR_PRODUCTO, CONSULTAR_PEDIDO, CONFIRMAR_PEDIDO, LISTAR_PEDIDOS, CANCELAR_PEDIDO);

    private HerramientasSupermercado() {
    }

    /** Compara los nombres descubiertos en el servidor con el contrato esperado. */
    public static Verificacion verificar(Collection<String> descubiertas) {
        Set<String> faltantes = new TreeSet<>(TODAS);
        faltantes.removeAll(descubiertas);
        Set<String> adicionales = new TreeSet<>(descubiertas);
        TODAS.forEach(adicionales::remove);
        return new Verificacion(descubiertas.size(), List.copyOf(faltantes), List.copyOf(adicionales));
    }

    /** Resultado de comparar las herramientas del servidor con las esperadas. */
    public record Verificacion(int descubiertas, List<String> faltantes, List<String> adicionales) {

        /** {@code true} si el servidor publica exactamente las diez herramientas esperadas. */
        public boolean completa() {
            return faltantes.isEmpty() && adicionales.isEmpty() && descubiertas == TODAS.size();
        }
    }
}
