package com.udea.agente_mcp.web;

import com.udea.agente_mcp.agente.FabricaAsistenteSupermercado;
import com.udea.agente_mcp.config.ConfiguracionAgente;
import com.udea.agente_mcp.conversacion.RespuestaConversacion;
import com.udea.agente_mcp.conversacion.ServicioConversacion;
import com.udea.agente_mcp.mcp.DiagnosticoMcp;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API HTTP mínima para conversar con el asistente desde cualquier cliente (curl, PowerShell,
 * Postman). La interfaz gráfica de desarrollo es la de Google ADK.
 */
@RestController
@RequestMapping(path = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class ChatController {

    private final ServicioConversacion conversacion;
    private final DiagnosticoMcp diagnostico;
    private final ConfiguracionAgente configuracion;
    private final Environment entorno;

    public ChatController(ServicioConversacion conversacion, DiagnosticoMcp diagnostico,
                          ConfiguracionAgente configuracion, Environment entorno) {
        this.conversacion = conversacion;
        this.diagnostico = diagnostico;
        this.configuracion = configuracion;
        this.entorno = entorno;
    }

    /** Envía un mensaje. Sin {@code sesionId} se inicia una conversación nueva. */
    @PostMapping(path = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    public RespuestaConversacion conversar(@RequestBody SolicitudChat solicitud) {
        return conversacion.responder(solicitud.sesionId(), solicitud.mensaje());
    }

    /** Estado del asistente: modelo, credenciales (sí/no) y conexión con el servidor MCP. */
    @GetMapping("/estado")
    public EstadoAsistente estado() {
        return new EstadoAsistente(
                FabricaAsistenteSupermercado.NOMBRE,
                configuracion.modelo(),
                ConfiguracionAgente.credencialesGeminiConfiguradas(entorno::getProperty),
                diagnostico.verificar());
    }

    /** Cuerpo de POST /api/chat. */
    public record SolicitudChat(String sesionId, String mensaje) {
    }

    /** Respuesta de GET /api/estado. */
    public record EstadoAsistente(String agente, String modelo, boolean credencialesGemini,
                                  DiagnosticoMcp.EstadoMcp mcp) {
    }
}
