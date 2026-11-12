package com.udea.agente_mcp.web;

import com.udea.agente_mcp.config.ConfiguracionAgente;
import com.udea.agente_mcp.conversacion.FalloConversacionException;
import com.udea.agente_mcp.conversacion.RespuestaConversacion;
import com.udea.agente_mcp.conversacion.ServicioConversacion;
import com.udea.agente_mcp.conversacion.SolicitudInvalidaException;
import com.udea.agente_mcp.conversacion.TipoFalloConversacion;
import com.udea.agente_mcp.mcp.DiagnosticoMcp;
import com.udea.agente_mcp.mcp.HerramientasSupermercado;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
@Import(ChatControllerTest.Configuracion.class)
class ChatControllerTest {

    @TestConfiguration
    static class Configuracion {
        @Bean
        ConfiguracionAgente configuracionAgente() {
            return ConfiguracionAgente.desde(Map.<String, String>of()::get);
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ServicioConversacion conversacion;

    @MockitoBean
    private DiagnosticoMcp diagnostico;

    @Test
    void respondeEnJsonUtf8ConLaSesionYElPedidoActual() throws Exception {
        when(conversacion.responder(isNull(), any())).thenReturn(new RespuestaConversacion(
                "sesion-1", "¡Hola! Tenemos piña, café y ñame. ¿Qué deseas pedir?", 41L));

        MvcResult resultado = mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mensaje\":\"Hola, ¿qué tienen?\"}".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sesionId").value("sesion-1"))
                .andExpect(jsonPath("$.pedidoActualId").value(41))
                .andReturn();

        assertThat(resultado.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("¡Hola! Tenemos piña, café y ñame. ¿Qué deseas pedir?");
    }

    @Test
    void unaSolicitudInvalidaDevuelve400() throws Exception {
        when(conversacion.responder(any(), any())).thenThrow(new SolicitudInvalidaException("El mensaje no puede estar vacío."));

        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"mensaje\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"))
                .andExpect(jsonPath("$.mensaje").value("El mensaje no puede estar vacío."));
    }

    @Test
    void unJsonMalFormadoDevuelve400SinLlamarAlAgente() throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{mensaje: hola"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
        verifyNoInteractions(conversacion);
    }

    @Test
    void servidorMcpNoDisponibleDevuelve503ConMensajeClaro() throws Exception {
        when(conversacion.responder(any(), any())).thenThrow(new FalloConversacionException(
                TipoFalloConversacion.MCP_NO_DISPONIBLE, "sesion-1", new RuntimeException("Connection refused")));

        MvcResult resultado = mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sesionId\":\"sesion-1\",\"mensaje\":\"Hola\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("MCP_NO_DISPONIBLE"))
                .andExpect(jsonPath("$.sesionId").value("sesion-1"))
                .andReturn();

        assertThat(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("no está disponible")
                .doesNotContain("Connection refused")
                .doesNotContain("Exception");
    }

    @Test
    void tiempoAgotadoDevuelve504() throws Exception {
        when(conversacion.responder(any(), any())).thenThrow(new FalloConversacionException(
                TipoFalloConversacion.TIEMPO_AGOTADO, "s", null));

        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"mensaje\":\"Hola\"}"))
                .andExpect(status().isGatewayTimeout());
    }

    @Test
    void elEstadoInformaModeloCredencialesYConexionMcpSinExponerClaves() throws Exception {
        when(diagnostico.verificar()).thenReturn(new DiagnosticoMcp.EstadoMcp(
                "http://localhost:8088/mcp", true, true, HerramientasSupermercado.TODAS, List.of(), null));

        mvc.perform(get("/api/estado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agente").value("asistente_supermercado"))
                .andExpect(jsonPath("$.modelo").value("gemini-3.6-flash"))
                .andExpect(jsonPath("$.credencialesGemini").isBoolean())
                .andExpect(jsonPath("$.mcp.disponible").value(true))
                .andExpect(jsonPath("$.mcp.herramientas.length()").value(10));
    }
}
