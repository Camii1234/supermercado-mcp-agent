package com.udea.agente_mcp.config;

import com.udea.agente_mcp.mcp.DiagnosticoMcp;
import com.udea.agente_mcp.mcp.DiagnosticoMcp.EstadoMcp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Al arrancar, informa en el log si hay credenciales de Gemini y si el servidor MCP responde con
 * las diez herramientas. No detiene la aplicación: el servidor MCP puede iniciarse después, porque
 * el agente abre la sesión MCP cuando la necesita. Nunca registra el valor de la clave.
 */
@Component
class VerificacionInicial implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(VerificacionInicial.class);

    private final ConfiguracionAgente configuracion;
    private final DiagnosticoMcp diagnostico;
    private final Environment entorno;

    VerificacionInicial(ConfiguracionAgente configuracion, DiagnosticoMcp diagnostico, Environment entorno) {
        this.configuracion = configuracion;
        this.diagnostico = diagnostico;
        this.entorno = entorno;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        if (ConfiguracionAgente.credencialesGeminiConfiguradas(entorno::getProperty)) {
            log.info("Credenciales de Gemini detectadas. Modelo: {}", configuracion.modelo());
        } else {
            log.warn("No hay credenciales de Gemini ({} no está definida). El asistente no podrá conversar "
                    + "hasta configurarla.", ConfiguracionAgente.VAR_CLAVE_GOOGLE);
        }
        if (!configuracion.verificarMcpAlIniciar()) {
            return;
        }
        EstadoMcp estado = diagnostico.verificar();
        if (estado.herramientasCompletas()) {
            log.info("Servidor MCP {} disponible con las {} herramientas esperadas.",
                    estado.url(), estado.herramientas().size());
        } else if (estado.disponible()) {
            log.warn("Servidor MCP {} disponible, pero faltan herramientas: {}", estado.url(), estado.faltantes());
        } else {
            log.warn("Servidor MCP {} no disponible ({}). Inícialo antes de conversar con el asistente.",
                    estado.url(), estado.fallo());
        }
    }
}
