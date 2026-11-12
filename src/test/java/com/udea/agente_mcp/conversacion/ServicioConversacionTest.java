package com.udea.agente_mcp.conversacion;

import com.google.adk.agents.LlmAgent;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.runner.InMemoryRunner;
import com.udea.agente_mcp.agente.FabricaAsistenteSupermercado;
import com.udea.agente_mcp.config.ConfiguracionAgente;
import com.udea.agente_mcp.mcp.ConexionMcpSupermercado;
import com.udea.agente_mcp.mcp.HerramientasSupermercado;
import com.udea.agente_mcp.soporte.ModeloGuionado;
import com.udea.agente_mcp.soporte.ServidorMcpFalso;
import io.reactivex.rxjava3.core.Flowable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Map;

import static com.udea.agente_mcp.mcp.HerramientasSupermercado.AGREGAR_PRODUCTO;
import static com.udea.agente_mcp.mcp.HerramientasSupermercado.CANCELAR_PEDIDO;
import static com.udea.agente_mcp.mcp.HerramientasSupermercado.CONFIRMAR_PEDIDO;
import static com.udea.agente_mcp.mcp.HerramientasSupermercado.CONSULTAR_PEDIDO;
import static com.udea.agente_mcp.mcp.HerramientasSupermercado.CREAR_PEDIDO;
import static com.udea.agente_mcp.mcp.HerramientasSupermercado.LISTAR_PRODUCTOS;
import static com.udea.agente_mcp.soporte.ModeloGuionado.instruccionSistema;
import static com.udea.agente_mcp.soporte.ModeloGuionado.ultimaRespuestaDeHerramienta;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas del flujo conversacional completo con el runner real de Google ADK, un modelo simulado
 * (sin Gemini ni API key) y el servidor MCP en memoria.
 */
class ServicioConversacionTest {

    private static final String PEDIDO_CREADO = """
            {"mensaje":"Pedido 41 creado en estado BORRADOR.","pedido":{"id":41,"clienteNombre":"Ana Gómez",\
            "telefono":"3000000000","direccion":"Calle 1 # 2-3","fechaCreacion":"2026-11-11T10:00:00",\
            "estado":"BORRADOR","detalles":[],"total":0}}""";
    private static final String PEDIDO_CONSULTADO = """
            {"id":41,"clienteNombre":"Ana Gómez","telefono":"3000000000","direccion":"Calle 1 # 2-3",\
            "fechaCreacion":"2026-11-11T10:00:00","estado":"BORRADOR","detalles":[{"productoId":3,\
            "sku":"ARR-001","nombreProducto":"Arroz 1 kg","cantidad":2,"precioUnitario":4200.00,\
            "subtotal":8400.00}],"total":8400.00}""";
    private static final String PEDIDO_CONFIRMADO = """
            {"mensaje":"Pedido 41 confirmado. Total definitivo: 8400.00.","pedido":{"id":41,\
            "clienteNombre":"Ana Gómez","telefono":"3000000000","direccion":"Calle 1 # 2-3",\
            "fechaCreacion":"2026-11-11T10:00:00","estado":"CONFIRMADO","detalles":[],"total":8400.00}}""";
    private static final Map<String, Object> PEDIDO_41 = Map.of("pedidoId", 41);

    private final ServidorMcpFalso servidor = ServidorMcpFalso.conLasDiezHerramientas()
            .responderJson(CREAR_PEDIDO, PEDIDO_CREADO)
            .responderJson(CONSULTAR_PEDIDO, PEDIDO_CONSULTADO)
            .responderJson(CONFIRMAR_PEDIDO, PEDIDO_CONFIRMADO)
            .responderJson(AGREGAR_PRODUCTO, PEDIDO_CREADO)
            .responderJson(CANCELAR_PEDIDO, PEDIDO_CREADO.replace("BORRADOR", "CANCELADO"));
    private final ModeloGuionado modelo = new ModeloGuionado();
    private ConexionMcpSupermercado conexion;

    @AfterEach
    void cerrar() {
        if (conexion != null) {
            conexion.close();
        }
    }

    private ServicioConversacion servicio(BaseLlm llm, ConexionMcpSupermercado conexionMcp) {
        conexion = conexionMcp;
        LlmAgent agente = FabricaAsistenteSupermercado.crear(llm, conexion.toolset());
        return new ServicioConversacion(new InMemoryRunner(agente, "supermercado-prueba"), Duration.ofSeconds(30));
    }

    private ServicioConversacion servicio() {
        return servicio(modelo, new ConexionMcpSupermercado(
                ConfiguracionAgente.desde(Map.<String, String>of()::get), servidor));
    }

    private static String errorRecibido(LlmRequest solicitud) {
        return String.valueOf(ultimaRespuestaDeHerramienta(solicitud).get("error"));
    }

    @Nested
    class RespuestasDeHerramientas {

        @Test
        void unaRespuestaExitosaLlegaAlModeloYSeDevuelveSuTexto() {
            servidor.responderJson(LISTAR_PRODUCTOS, """
                    [{"id":3,"sku":"ARR-001","nombre":"Arroz 1 kg","categoria":"Despensa","precio":4200.00,\
                    "stock":40,"disponible":true}]""");
            modelo.llamar(LISTAR_PRODUCTOS, Map.of())
                    .responderCon(s -> "Catálogo: " + ultimaRespuestaDeHerramienta(s));

            RespuestaConversacion respuesta = servicio().responder(null, "¿Qué productos tienen?");

            assertThat(respuesta.respuesta()).startsWith("Catálogo:").contains("Arroz 1 kg").contains("4200");
            assertThat(respuesta.sesionId()).isNotBlank();
            assertThat(respuesta.pedidoActualId()).isNull();
            assertThat(servidor.llamadasA(LISTAR_PRODUCTOS)).hasSize(1);
        }

        @Test
        void unErrorDeLaHerramientaSeEntregaAlModeloSinOcultarlo() {
            servidor.responderError(AGREGAR_PRODUCTO, "Stock insuficiente para 'Leche entera 1 L' (SKU LEC-001): "
                    + "se requieren 6 unidades y hay 5 disponibles.");
            modelo.llamar(AGREGAR_PRODUCTO, Map.of("pedidoId", 41, "productoId", 2, "cantidad", 6))
                    .responderCon(ServicioConversacionTest::errorRecibido);

            RespuestaConversacion respuesta = servicio().responder(null, "Agrega 6 leches");

            assertThat(respuesta.respuesta()).contains("Stock insuficiente").contains("hay 5 disponibles");
            assertThat(servidor.llamadasA(AGREGAR_PRODUCTO)).singleElement()
                    .satisfies(l -> assertThat(l.argumentos()).containsEntry("cantidad", 6));
        }
    }

    @Nested
    class ContextoDelPedido {

        @Test
        void elIdDelPedidoCreadoSeConservaEnLaSesionYSeInyectaEnLaInstruccion() {
            modelo.llamar(CREAR_PEDIDO, Map.of("clienteNombre", "Ana Gómez", "telefono", "3000000000",
                            "direccion", "Calle 1 # 2-3"))
                    .responder("Tu pedido 41 quedó creado.")
                    .responderCon(s -> instruccionSistema(s).contains("Pedido en curso de esta conversación: 41")
                            ? "contexto-conservado" : "contexto-perdido");
            ServicioConversacion servicio = servicio();

            RespuestaConversacion primera = servicio.responder(null,
                    "Quiero hacer un pedido a nombre de Ana Gómez, teléfono 3000000000, Calle 1 # 2-3");
            RespuestaConversacion segunda = servicio.responder(primera.sesionId(), "¿Cuál es mi pedido?");

            assertThat(primera.pedidoActualId()).isEqualTo(41L);
            assertThat(segunda.sesionId()).isEqualTo(primera.sesionId());
            assertThat(segunda.respuesta()).isEqualTo("contexto-conservado");
            assertThat(segunda.pedidoActualId()).isEqualTo(41L);
            assertThat(servidor.llamadasA(CREAR_PEDIDO)).hasSize(1);
        }

        @Test
        void lasSesionesNoCompartenElPedido() {
            modelo.llamar(CREAR_PEDIDO, Map.of("clienteNombre", "Ana", "telefono", "3000000000", "direccion", "Calle 1"))
                    .responder("Pedido creado.")
                    .responder("Hola, ¿en qué te ayudo?");
            ServicioConversacion servicio = servicio();

            RespuestaConversacion conPedido = servicio.responder("sesion-a", "Quiero hacer un pedido");
            RespuestaConversacion otra = servicio.responder("sesion-b", "Hola");

            assertThat(conPedido.pedidoActualId()).isEqualTo(41L);
            assertThat(otra.pedidoActualId()).isNull();
        }
    }

    @Nested
    class ConfirmacionExplicita {

        @Test
        void noConfirmaSiNoSeMostroAntesElResumen() {
            modelo.llamar(CONFIRMAR_PEDIDO, PEDIDO_41).responderCon(ServicioConversacionTest::errorRecibido);

            RespuestaConversacion respuesta = servicio().responder(null, "Sí, confirmo el pedido 41");

            assertThat(respuesta.respuesta()).contains("NO ejecutada").contains("consultarPedido");
            assertThat(servidor.llamadasA(CONFIRMAR_PEDIDO)).isEmpty();
        }

        @Test
        void noConfirmaEnElMismoTurnoEnQueConsultaElResumen() {
            modelo.llamar(CONSULTAR_PEDIDO, PEDIDO_41)
                    .llamar(CONFIRMAR_PEDIDO, PEDIDO_41)
                    .responderCon(ServicioConversacionTest::errorRecibido);

            RespuestaConversacion respuesta = servicio().responder(null, "Muéstrame el resumen y confírmalo");

            assertThat(respuesta.respuesta()).contains("NO ejecutada").contains("todavía no ha visto el resumen");
            assertThat(servidor.llamadasA(CONFIRMAR_PEDIDO)).isEmpty();
        }

        @Test
        void unaRespuestaAmbiguaNoConfirmaYUnaExplicitaSi() {
            modelo.llamar(CONSULTAR_PEDIDO, PEDIDO_41)
                    .responder("Pedido 41: 2 x Arroz 1 kg ($4.200) = $8.400. ¿Confirmas el pedido por $8.400?")
                    .llamar(CONFIRMAR_PEDIDO, PEDIDO_41)
                    .responderCon(ServicioConversacionTest::errorRecibido)
                    .llamar(CONFIRMAR_PEDIDO, PEDIDO_41)
                    .responder("Pedido 41 confirmado por $8.400.");
            ServicioConversacion servicio = servicio();

            String sesion = servicio.responder(null, "Quiero terminar mi pedido").sesionId();
            RespuestaConversacion ambigua = servicio.responder(sesion, "ok");
            assertThat(ambigua.respuesta()).contains("NO ejecutada");
            assertThat(servidor.llamadasA(CONFIRMAR_PEDIDO)).isEmpty();

            RespuestaConversacion explicita = servicio.responder(sesion, "Sí, confirmo el pedido");
            assertThat(explicita.respuesta()).isEqualTo("Pedido 41 confirmado por $8.400.");
            assertThat(servidor.llamadasA(CONFIRMAR_PEDIDO)).singleElement()
                    .satisfies(l -> assertThat(l.argumentos()).containsEntry("pedidoId", 41));
            assertThat(modelo.guionTerminado()).isTrue();
        }

        @Test
        void siElPedidoCambiaDespuesDelResumenHayQueMostrarloDeNuevo() {
            modelo.llamar(CONSULTAR_PEDIDO, PEDIDO_41)
                    .responder("¿Confirmas el pedido por $8.400?")
                    .llamar(AGREGAR_PRODUCTO, Map.of("pedidoId", 41, "productoId", 3, "cantidad", 1))
                    .responder("Agregué un arroz más.")
                    .llamar(CONFIRMAR_PEDIDO, PEDIDO_41)
                    .responderCon(ServicioConversacionTest::errorRecibido);
            ServicioConversacion servicio = servicio();

            String sesion = servicio.responder(null, "Quiero terminar mi pedido").sesionId();
            servicio.responder(sesion, "Mejor agrega un arroz más");
            RespuestaConversacion respuesta = servicio.responder(sesion, "Sí, confirmo");

            assertThat(respuesta.respuesta()).contains("NO ejecutada");
            assertThat(servidor.llamadasA(CONFIRMAR_PEDIDO)).isEmpty();
        }

        @Test
        void soloCancelaConUnaIntencionClara() {
            modelo.llamar(CANCELAR_PEDIDO, PEDIDO_41)
                    .responderCon(ServicioConversacionTest::errorRecibido)
                    .llamar(CANCELAR_PEDIDO, PEDIDO_41)
                    .responder("Pedido 41 cancelado.");
            ServicioConversacion servicio = servicio();

            String sesion = servicio.responder(null, "¿Y si mejor lo dejo para después?").sesionId();
            assertThat(servidor.llamadasA(CANCELAR_PEDIDO)).isEmpty();

            RespuestaConversacion respuesta = servicio.responder(sesion, "Cancela el pedido 41, por favor");
            assertThat(respuesta.respuesta()).isEqualTo("Pedido 41 cancelado.");
            assertThat(servidor.llamadasA(CANCELAR_PEDIDO)).hasSize(1);
        }
    }

    @Nested
    class Fallos {

        @Test
        void servidorMcpNoDisponibleProduceUnFalloClaro() throws IOException {
            int puerto;
            try (ServerSocket socket = new ServerSocket(0)) {
                puerto = socket.getLocalPort();
            }
            ConfiguracionAgente sinServidor = ConfiguracionAgente.desde(Map.of(
                    "MCP_SERVER_URL", "http://127.0.0.1:" + puerto + "/mcp",
                    "MCP_CONNECT_TIMEOUT_SECONDS", "3",
                    "MCP_NOTIFICATION_TIMEOUT_SECONDS", "1")::get);
            modelo.responder("no debería llegar aquí");
            ServicioConversacion servicio = servicio(modelo, new ConexionMcpSupermercado(sinServidor));

            assertThatThrownBy(() -> servicio.responder("sesion-sin-mcp", "Hola"))
                    .isInstanceOfSatisfying(FalloConversacionException.class, e -> {
                        assertThat(e.tipo()).isEqualTo(TipoFalloConversacion.MCP_NO_DISPONIBLE);
                        assertThat(e.getMessage()).contains("no está disponible");
                        assertThat(e.sesionId()).isEqualTo("sesion-sin-mcp");
                    });
        }

        @Test
        void unModeloSinCredencialesSeInformaComoModeloNoDisponible() {
            BaseLlm sinClave = new BaseLlm("gemini-sin-clave") {
                @Override
                public Flowable<LlmResponse> generateContent(LlmRequest solicitud, boolean flujo) {
                    return Flowable.error(new IllegalArgumentException(
                            "API key must either be provided or set in the environment variable GOOGLE_API_KEY"));
                }

                @Override
                public BaseLlmConnection connect(LlmRequest solicitud) {
                    throw new UnsupportedOperationException();
                }
            };
            ServicioConversacion servicio = servicio(sinClave, new ConexionMcpSupermercado(
                    ConfiguracionAgente.desde(Map.<String, String>of()::get), servidor));

            assertThatThrownBy(() -> servicio.responder(null, "Hola"))
                    .isInstanceOfSatisfying(FalloConversacionException.class,
                            e -> assertThat(e.tipo()).isEqualTo(TipoFalloConversacion.MODELO_NO_DISPONIBLE));
        }
    }

    @Nested
    class ValidacionDeEntrada {

        @Test
        void rechazaMensajesVaciosOExcesivos() {
            ServicioConversacion servicio = servicio();

            assertThatThrownBy(() -> servicio.responder(null, "  ")).isInstanceOf(SolicitudInvalidaException.class);
            assertThatThrownBy(() -> servicio.responder(null, null)).isInstanceOf(SolicitudInvalidaException.class);
            assertThatThrownBy(() -> servicio.responder(null, "a".repeat(ServicioConversacion.LONGITUD_MAXIMA_MENSAJE + 1)))
                    .isInstanceOf(SolicitudInvalidaException.class);
            assertThat(servidor.llamadas()).isEmpty();
        }

        @Test
        void rechazaIdsDeSesionConCaracteresNoPermitidos() {
            ServicioConversacion servicio = servicio();

            assertThatThrownBy(() -> servicio.responder("sesión con espacios", "Hola"))
                    .isInstanceOf(SolicitudInvalidaException.class);
            assertThatThrownBy(() -> servicio.responder("../../etc", "Hola"))
                    .isInstanceOf(SolicitudInvalidaException.class);
        }
    }

    @Test
    void elContratoDeHerramientasTieneDiezNombresDistintos() {
        assertThat(HerramientasSupermercado.TODAS).hasSize(10).doesNotHaveDuplicates();
    }
}
