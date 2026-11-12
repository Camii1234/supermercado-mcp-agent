package com.udea.agente_mcp.soporte;

import com.google.adk.tools.mcp.McpTransportBuilder;
import com.udea.agente_mcp.mcp.HerramientasSupermercado;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Doble de prueba del servidor MCP del supermercado que funciona en memoria.
 *
 * <p>Se conecta a Google ADK por el mismo punto de extensión que el transporte real
 * ({@link McpTransportBuilder}), de modo que las pruebas ejercitan el {@code McpToolset},
 * {@code McpSessionManager} y {@code McpTool} reales de ADK y el protocolo JSON-RPC de MCP
 * (initialize, tools/list, tools/call) sin red ni servidor externo. Las respuestas las define cada
 * prueba; aquí no hay reglas de negocio.
 */
public final class ServidorMcpFalso implements McpTransportBuilder {

    private static final McpJsonMapper JSON = McpJsonMapper.getDefault();

    private final List<McpSchema.Tool> herramientas;
    private final Map<String, Function<Map<String, Object>, McpSchema.CallToolResult>> respuestas = new ConcurrentHashMap<>();
    private final List<Llamada> llamadas = new CopyOnWriteArrayList<>();
    private final AtomicInteger sesionesIniciadas = new AtomicInteger();

    /** Invocación recibida por el servidor falso. */
    public record Llamada(String herramienta, Map<String, Object> argumentos) {
    }

    private ServidorMcpFalso(List<String> nombres) {
        this.herramientas = nombres.stream().map(ServidorMcpFalso::herramienta).toList();
    }

    public static ServidorMcpFalso conLasDiezHerramientas() {
        return new ServidorMcpFalso(HerramientasSupermercado.TODAS);
    }

    public static ServidorMcpFalso conHerramientas(List<String> nombres) {
        return new ServidorMcpFalso(nombres);
    }

    /** Respuesta exitosa: el texto JSON que devolvería el servidor. */
    public ServidorMcpFalso responderJson(String herramienta, String json) {
        respuestas.put(herramienta, argumentos -> McpSchema.CallToolResult.builder()
                .addTextContent(json).isError(false).build());
        return this;
    }

    /** Error de negocio devuelto por la herramienta ({@code isError: true}). */
    public ServidorMcpFalso responderError(String herramienta, String mensaje) {
        respuestas.put(herramienta, argumentos -> McpSchema.CallToolResult.builder()
                .addTextContent(mensaje).isError(true).build());
        return this;
    }

    public List<Llamada> llamadas() {
        return List.copyOf(llamadas);
    }

    public List<Llamada> llamadasA(String herramienta) {
        return llamadas.stream().filter(l -> l.herramienta().equals(herramienta)).toList();
    }

    public int sesionesIniciadas() {
        return sesionesIniciadas.get();
    }

    @Override
    public McpClientTransport build(Object parametrosConexion) {
        return new Transporte();
    }

    private McpSchema.CallToolResult ejecutar(Object parametros) {
        McpSchema.CallToolRequest solicitud = JSON.convertValue(parametros, new TypeRef<>() {
        });
        Map<String, Object> argumentos = solicitud.arguments() == null ? Map.of() : solicitud.arguments();
        llamadas.add(new Llamada(solicitud.name(), argumentos));
        return respuestas.getOrDefault(solicitud.name(), a -> McpSchema.CallToolResult.builder()
                        .addTextContent("Sin respuesta configurada en la prueba para " + solicitud.name())
                        .isError(true).build())
                .apply(argumentos);
    }

    private static McpSchema.Tool herramienta(String nombre) {
        Map<String, Object> propiedades = new LinkedHashMap<>();
        List<String> obligatorios = new ArrayList<>(parametros(nombre).keySet());
        parametros(nombre).forEach((parametro, tipo) -> propiedades.put(parametro, Map.of("type", tipo)));
        return McpSchema.Tool.builder()
                .name(nombre)
                .description("Herramienta de prueba " + nombre)
                .inputSchema(new McpSchema.JsonSchema("object", propiedades, obligatorios, false, null, null))
                .build();
    }

    private static Map<String, String> parametros(String herramienta) {
        return switch (herramienta) {
            case HerramientasSupermercado.BUSCAR_PRODUCTOS -> Map.of("texto", "string");
            case HerramientasSupermercado.CONSULTAR_STOCK -> Map.of("productoId", "integer");
            case HerramientasSupermercado.CREAR_PEDIDO ->
                    Map.of("clienteNombre", "string", "telefono", "string", "direccion", "string");
            case HerramientasSupermercado.AGREGAR_PRODUCTO ->
                    Map.of("pedidoId", "integer", "productoId", "integer", "cantidad", "integer");
            case HerramientasSupermercado.RETIRAR_PRODUCTO -> Map.of("pedidoId", "integer", "productoId", "integer");
            case HerramientasSupermercado.CONSULTAR_PEDIDO, HerramientasSupermercado.CONFIRMAR_PEDIDO,
                 HerramientasSupermercado.CANCELAR_PEDIDO -> Map.of("pedidoId", "integer");
            default -> Map.of();
        };
    }

    /** Transporte en memoria: responde las peticiones JSON-RPC y acepta las notificaciones. */
    private final class Transporte implements McpClientTransport {

        private volatile Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> manejador;

        @Override
        public Mono<Void> connect(Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> manejador) {
            this.manejador = manejador;
            return Mono.empty();
        }

        @Override
        public Mono<Void> sendMessage(McpSchema.JSONRPCMessage mensaje) {
            if (!(mensaje instanceof McpSchema.JSONRPCRequest solicitud)) {
                return Mono.empty();
            }
            Object resultado = switch (solicitud.method()) {
                case McpSchema.METHOD_INITIALIZE -> {
                    sesionesIniciadas.incrementAndGet();
                    McpSchema.InitializeRequest inicio = JSON.convertValue(solicitud.params(), new TypeRef<>() {
                    });
                    yield new McpSchema.InitializeResult(inicio.protocolVersion(),
                            McpSchema.ServerCapabilities.builder().tools(false).build(),
                            new McpSchema.Implementation("servidor-mcp-falso", "1.0"), null);
                }
                case McpSchema.METHOD_TOOLS_LIST -> new McpSchema.ListToolsResult(herramientas, null);
                case McpSchema.METHOD_TOOLS_CALL -> ejecutar(solicitud.params());
                default -> null;
            };
            McpSchema.JSONRPCResponse respuesta = resultado == null
                    ? new McpSchema.JSONRPCResponse("2.0", solicitud.id(), null,
                    new McpSchema.JSONRPCResponse.JSONRPCError(-32601, "Método no soportado: " + solicitud.method(), null))
                    : new McpSchema.JSONRPCResponse("2.0", solicitud.id(), resultado, null);
            return manejador.apply(Mono.just(respuesta)).then();
        }

        @Override
        public Mono<Void> closeGracefully() {
            return Mono.empty();
        }

        @Override
        public <T> T unmarshalFrom(Object datos, TypeRef<T> tipo) {
            return JSON.convertValue(datos, tipo);
        }

        @Override
        public List<String> protocolVersions() {
            return List.of("2025-06-18", "2025-03-26");
        }
    }
}
