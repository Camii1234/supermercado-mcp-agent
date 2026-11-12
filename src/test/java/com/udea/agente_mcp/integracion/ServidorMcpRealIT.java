package com.udea.agente_mcp.integracion;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.agents.LlmAgent;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.tools.BaseTool;
import com.udea.agente_mcp.agente.FabricaAsistenteSupermercado;
import com.udea.agente_mcp.config.ConfiguracionAgente;
import com.udea.agente_mcp.conversacion.RespuestaConversacion;
import com.udea.agente_mcp.conversacion.ServicioConversacion;
import com.udea.agente_mcp.mcp.ConexionMcpSupermercado;
import com.udea.agente_mcp.mcp.HerramientasSupermercado;
import com.udea.agente_mcp.soporte.ModeloGuionado;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de integración contra el servidor MCP real (por defecto http://localhost:8088/mcp).
 *
 * <p>No se ejecuta con {@code mvnw clean test}: requiere el servidor y MySQL en marcha y se activa
 * explícitamente con la variable {@code MCP_INTEGRATION=true}. Usa el {@code McpToolset} de ADK y
 * las herramientas MCP reales, sin modelo de lenguaje. Las pruebas de escritura (pedidos de
 * demostración que se cancelan al final) requieren además {@code MCP_INTEGRATION_WRITE=true}.
 *
 * <pre>
 * $env:MCP_INTEGRATION = "true"; $env:MCP_INTEGRATION_WRITE = "true"
 * .\mvnw.cmd test "-Dtest=ServidorMcpRealIT" "-Dsurefire.failIfNoSpecifiedTests=false"
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "MCP_INTEGRATION", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ServidorMcpRealIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CLIENTE_DEMO = "Cliente Demo Laboratorio ADK";

    private static ConexionMcpSupermercado conexion;
    private static Map<String, BaseTool> herramientas;

    @BeforeAll
    static void conectar() {
        conexion = new ConexionMcpSupermercado(ConfiguracionAgente.desdeEntorno());
        herramientas = conexion.descubrirHerramientas(Duration.ofSeconds(60)).stream()
                .collect(Collectors.toMap(BaseTool::name, Function.identity()));
    }

    @AfterAll
    static void cerrar() {
        if (conexion != null) {
            conexion.close();
        }
    }

    @Test
    @Order(1)
    void elServidorPublicaExactamenteLasDiezHerramientas() {
        List<String> delServidor = conexion.listarHerramientasDelServidor();
        System.out.println("[IT] Herramientas del servidor: " + delServidor);

        assertThat(HerramientasSupermercado.verificar(delServidor).completa()).isTrue();
        assertThat(herramientas.keySet()).containsExactlyInAnyOrderElementsOf(HerramientasSupermercado.TODAS);
    }

    @Test
    @Order(2)
    void operacionesDeSoloLectura() {
        List<Map<String, Object>> catalogo = lista(llamar("listarProductos", Map.of()));
        System.out.println("[IT] listarProductos: " + catalogo.size() + " productos; primero: " + catalogo.get(0));
        assertThat(catalogo).isNotEmpty();

        String texto = String.valueOf(catalogo.get(0).get("nombre")).split(" ")[0];
        List<Map<String, Object>> encontrados = lista(llamar("buscarProductos", Map.of("texto", texto)));
        System.out.println("[IT] buscarProductos('" + texto + "'): " + encontrados.size() + " resultados");
        assertThat(encontrados).isNotEmpty();

        Number id = (Number) catalogo.get(0).get("id");
        Map<String, Object> stock = objeto(llamar("consultarStock", Map.of("productoId", id)));
        System.out.println("[IT] consultarStock(" + id + "): " + stock.get("mensaje"));
        assertThat(stock).containsKeys("producto", "aLaVenta", "mensaje");
    }

    @Test
    @Order(3)
    void unProductoInexistenteDevuelveUnErrorDelServidor() {
        Map<String, Object> respuesta = llamar("consultarStock", Map.of("productoId", 999999));
        System.out.println("[IT] consultarStock(999999): " + respuesta);
        assertThat(respuesta).containsKey("error");
        assertThat(String.valueOf(respuesta.get("error"))).contains("No existe un producto con id 999999");
    }

    @Test
    @Order(4)
    @EnabledIfEnvironmentVariable(named = "MCP_INTEGRATION_WRITE", matches = "true")
    void pedidoBorradorDeDemostracionSeCreaSeTotalizaYSeCancela() {
        Map<String, Object> producto = lista(llamar("listarProductos", Map.of())).get(0);
        Number productoId = (Number) producto.get("id");

        Number pedidoId = idPedido(llamar("crearPedido", Map.of(
                "clienteNombre", CLIENTE_DEMO, "telefono", "300 000 0000", "direccion", "Calle Demo 1 # 2-3")));
        System.out.println("[IT] Pedido de demostración (borrador) creado: " + pedidoId);
        llamar("agregarProducto", Map.of("pedidoId", pedidoId, "productoId", productoId, "cantidad", 1));
        llamar("agregarProducto", Map.of("pedidoId", pedidoId, "productoId", productoId, "cantidad", 1));

        Map<String, Object> consultado = objeto(llamar("consultarPedido", Map.of("pedidoId", pedidoId)));
        Map<String, Object> linea = lista(consultado.get("detalles")).get(0);
        BigDecimal precio = new BigDecimal(String.valueOf(producto.get("precio")));
        System.out.println("[IT] Pedido " + pedidoId + ": " + linea + " total=" + consultado.get("total"));
        assertThat(((Number) linea.get("cantidad")).intValue()).isEqualTo(2);
        assertThat(new BigDecimal(String.valueOf(consultado.get("total")))).isEqualByComparingTo(precio.multiply(BigDecimal.TWO));

        Map<String, Object> cancelado = objeto(llamar("cancelarPedido", Map.of("pedidoId", pedidoId)));
        System.out.println("[IT] " + cancelado.get("mensaje"));
        assertThat(objeto(cancelado.get("pedido")).get("estado")).isEqualTo("CANCELADO");
    }

    @Test
    @Order(5)
    @EnabledIfEnvironmentVariable(named = "MCP_INTEGRATION_WRITE", matches = "true")
    void confirmarDescuentaStockYCancelarLoRestituye() {
        Map<String, Object> producto = lista(llamar("listarProductos", Map.of())).get(0);
        Number productoId = (Number) producto.get("id");
        int stockInicial = stock(productoId);

        Number pedidoId = idPedido(llamar("crearPedido", Map.of(
                "clienteNombre", CLIENTE_DEMO, "telefono", "300 000 0000", "direccion", "Calle Demo 1 # 2-3")));
        llamar("agregarProducto", Map.of("pedidoId", pedidoId, "productoId", productoId, "cantidad", 1));
        Map<String, Object> confirmado = objeto(llamar("confirmarPedido", Map.of("pedidoId", pedidoId)));
        int stockConfirmado = stock(productoId);
        Map<String, Object> cancelado = objeto(llamar("cancelarPedido", Map.of("pedidoId", pedidoId)));
        int stockFinal = stock(productoId);

        System.out.println("[IT] Pedido de demostración " + pedidoId + ": stock " + stockInicial + " -> "
                + stockConfirmado + " (confirmado) -> " + stockFinal + " (cancelado). " + cancelado.get("mensaje"));
        assertThat(objeto(confirmado.get("pedido")).get("estado")).isEqualTo("CONFIRMADO");
        assertThat(stockConfirmado).isEqualTo(stockInicial - 1);
        assertThat(stockFinal).isEqualTo(stockInicial);
    }

    @Test
    @Order(6)
    void elAgenteAdkUsaElServidorRealYLaGuardiaBloqueaConfirmacionesAmbiguas() {
        ModeloGuionado modelo = new ModeloGuionado()
                .llamar("buscarProductos", Map.of("texto", "arroz"))
                .responderCon(s -> "Resultado: " + ModeloGuionado.ultimaRespuestaDeHerramienta(s))
                .llamar("confirmarPedido", Map.of("pedidoId", 999999))
                .responderCon(s -> String.valueOf(ModeloGuionado.ultimaRespuestaDeHerramienta(s).get("error")));
        ServicioConversacion servicio = servicioConModelo(modelo);

        RespuestaConversacion busqueda = servicio.responder(null, "¿Tienen arroz?");
        RespuestaConversacion ambigua = servicio.responder(busqueda.sesionId(), "ok");

        System.out.println("[IT] Agente + MCP real, búsqueda: " + busqueda.respuesta());
        System.out.println("[IT] Agente + MCP real, 'ok': " + ambigua.respuesta());
        assertThat(busqueda.respuesta()).startsWith("Resultado:").containsIgnoringCase("arroz");
        assertThat(ambigua.respuesta()).contains("NO ejecutada");
    }

    @Test
    @Order(7)
    @EnabledIfEnvironmentVariable(named = "MCP_INTEGRATION_WRITE", matches = "true")
    void elAgenteConservaElPedidoCreadoEnElServidorRealYLoCancelaConIntencionClara() {
        ModeloGuionado modelo = new ModeloGuionado()
                .llamar("crearPedido", Map.of("clienteNombre", CLIENTE_DEMO, "telefono", "300 000 0000",
                        "direccion", "Calle Demo 1 # 2-3"))
                .responderCon(s -> "Creado: " + ModeloGuionado.ultimaRespuestaDeHerramienta(s));
        ServicioConversacion servicio = servicioConModelo(modelo);

        RespuestaConversacion creado = servicio.responder(null, "Quiero hacer un pedido de demostración");
        Long pedidoId = creado.pedidoActualId();
        assertThat(pedidoId).isNotNull();
        modelo.llamar("cancelarPedido", Map.of("pedidoId", pedidoId))
                .responderCon(s -> "Cancelación: " + ModeloGuionado.ultimaRespuestaDeHerramienta(s));
        RespuestaConversacion cancelado = servicio.responder(creado.sesionId(), "Cancela el pedido, por favor");

        System.out.println("[IT] Agente + MCP real: pedido de demostración " + pedidoId + " en sesión; "
                + cancelado.respuesta());
        assertThat(cancelado.respuesta()).contains("CANCELADO");
    }

    private static ServicioConversacion servicioConModelo(ModeloGuionado modelo) {
        LlmAgent agente = FabricaAsistenteSupermercado.crear(modelo, conexion.toolset());
        return new ServicioConversacion(new InMemoryRunner(agente, "supermercado-it"), Duration.ofSeconds(60));
    }

    private int stock(Number productoId) {
        Map<String, Object> respuesta = objeto(llamar("consultarStock", Map.of("productoId", productoId)));
        return ((Number) objeto(respuesta.get("producto")).get("stock")).intValue();
    }

    private static Number idPedido(Map<String, Object> respuesta) {
        return (Number) objeto(objeto(respuesta).get("pedido")).get("id");
    }

    private static Map<String, Object> llamar(String herramienta, Map<String, Object> argumentos) {
        return herramientas.get(herramienta).runAsync(argumentos, null).blockingGet();
    }

    /** Primer objeto de text_output (ADK lo deserializa si el JSON es un objeto). */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> objeto(Object valor) {
        if (valor instanceof Map<?, ?> mapa && mapa.get("text_output") instanceof List<?> salidas) {
            return (Map<String, Object>) salidas.get(0);
        }
        assertThat(valor).isInstanceOf(Map.class);
        return (Map<String, Object>) valor;
    }

    /** Lista JSON: ADK la entrega como texto porque no es un objeto. */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> lista(Object valor) {
        if (valor instanceof List<?> lista) {
            return (List<Map<String, Object>>) lista;
        }
        try {
            String texto = String.valueOf(objeto(valor).get("text"));
            return JSON.readValue(texto, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new AssertionError("Respuesta inesperada: " + valor, e);
        }
    }
}
