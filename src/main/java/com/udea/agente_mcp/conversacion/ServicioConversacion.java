package com.udea.agente_mcp.conversacion;

import com.google.adk.events.Event;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.udea.agente_mcp.agente.EstadoConversacion;
import com.udea.agente_mcp.mcp.ClasificadorFallosMcp;
import com.udea.agente_mcp.mcp.TipoFalloMcp;
import io.reactivex.rxjava3.core.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Caso de uso "conversar con el asistente": valida la entrada, ubica o crea la sesión de ADK,
 * ejecuta el agente con el {@link Runner} y devuelve el texto final. El estado de la sesión (por
 * ejemplo, el pedido en curso) lo conserva el servicio de sesiones de ADK.
 */
public class ServicioConversacion {

    private static final Logger log = LoggerFactory.getLogger(ServicioConversacion.class);

    /** Usuario lógico de ADK; cada conversación se distingue por su id de sesión. */
    static final String USUARIO = "cliente";
    public static final int LONGITUD_MAXIMA_MENSAJE = 2000;
    private static final Pattern FORMATO_SESION = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    static final String RESPUESTA_VACIA =
            "No obtuve una respuesta del asistente. ¿Puedes repetir tu mensaje?";

    private final Runner runner;
    private final Duration tiempoMaximo;
    private final ConcurrentMap<String, Object> candados = new ConcurrentHashMap<>();

    public ServicioConversacion(Runner runner, Duration tiempoMaximo) {
        this.runner = Objects.requireNonNull(runner, "runner");
        this.tiempoMaximo = Objects.requireNonNull(tiempoMaximo, "tiempoMaximo");
    }

    /**
     * Envía un mensaje al asistente dentro de una sesión.
     *
     * @param sesionId id de sesión existente o {@code null}/vacío para iniciar una nueva
     * @throws SolicitudInvalidaException  si el mensaje o la sesión no son válidos
     * @throws FalloConversacionException si el agente, el modelo o el servidor MCP fallan
     */
    public RespuestaConversacion responder(String sesionId, String mensaje) {
        String id = validarSesion(sesionId);
        String texto = validarMensaje(mensaje);
        // Los turnos de una misma sesión se procesan en orden para no mezclar su estado.
        synchronized (candados.computeIfAbsent(id, clave -> new Object())) {
            asegurarSesion(id);
            List<Event> eventos = ejecutar(id, texto);
            return new RespuestaConversacion(id, textoFinal(eventos), pedidoActual(id).orElse(null));
        }
    }

    private List<Event> ejecutar(String sesionId, String texto) {
        Content contenido = Content.builder().role("user").parts(List.of(Part.fromText(texto))).build();
        List<Event> eventos;
        try {
            eventos = runner.runAsync(USUARIO, sesionId, contenido)
                    .timeout(tiempoMaximo.toMillis(), TimeUnit.MILLISECONDS,
                            Flowable.error(TiempoRespuestaAgotadoException::new))
                    .toList()
                    .blockingGet();
        } catch (RuntimeException e) {
            throw traducir(sesionId, e);
        }
        Optional<Event> eventoConError = eventos.stream().filter(ev -> ev.errorMessage().isPresent()
                || ev.errorCode().isPresent()).findFirst();
        if (eventoConError.isPresent() && textoFinal(eventos).equals(RESPUESTA_VACIA)) {
            log.warn("El modelo devolvió un error en la sesión {}: {}", sesionId,
                    eventoConError.get().errorCode().map(Object::toString).orElse("sin código"));
            throw new FalloConversacionException(TipoFalloConversacion.MODELO_NO_DISPONIBLE, sesionId, null);
        }
        return eventos;
    }

    private FalloConversacionException traducir(String sesionId, RuntimeException error) {
        TipoFalloConversacion tipo;
        if (error instanceof TiempoRespuestaAgotadoException) {
            tipo = TipoFalloConversacion.TIEMPO_AGOTADO;
        } else {
            TipoFalloMcp tipoMcp = ClasificadorFallosMcp.clasificar(error);
            tipo = tipoMcp != TipoFalloMcp.DESCONOCIDO
                    ? TipoFalloConversacion.desde(tipoMcp)
                    : (esFalloDelModelo(error) ? TipoFalloConversacion.MODELO_NO_DISPONIBLE : TipoFalloConversacion.INTERNO);
        }
        if (tipo == TipoFalloConversacion.INTERNO) {
            log.error("Error inesperado al ejecutar el asistente en la sesión {}", sesionId, error);
        } else {
            log.warn("No se pudo responder en la sesión {}: {} ({})", sesionId, tipo, error.getClass().getSimpleName());
        }
        return new FalloConversacionException(tipo, sesionId, error);
    }

    /** Errores de configuración o de la API de Gemini (clave ausente o rechazada, modelo inexistente). */
    private static boolean esFalloDelModelo(Throwable error) {
        for (Throwable actual = error; actual != null; actual = actual.getCause()) {
            String clase = actual.getClass().getName();
            String mensaje = Optional.ofNullable(actual.getMessage()).orElse("").toLowerCase(Locale.ROOT);
            if (clase.startsWith("com.google.genai.errors.") || mensaje.contains("api key")
                    || mensaje.contains("gemini_api_key") || mensaje.contains("google_api_key")) {
                return true;
            }
            if (actual.getCause() == actual) {
                break;
            }
        }
        return false;
    }

    private void asegurarSesion(String sesionId) {
        Session existente = runner.sessionService()
                .getSession(runner.appName(), USUARIO, sesionId, Optional.empty())
                .blockingGet();
        if (existente == null) {
            Map<String, Object> estadoInicial = new HashMap<>();
            Session nueva = runner.sessionService()
                    .createSession(runner.appName(), USUARIO, estadoInicial, sesionId)
                    .blockingGet();
            log.info("Nueva sesión de conversación {}", nueva.id());
        }
    }

    private Optional<Long> pedidoActual(String sesionId) {
        Session sesion = runner.sessionService()
                .getSession(runner.appName(), USUARIO, sesionId, Optional.empty())
                .blockingGet();
        if (sesion == null) {
            return Optional.empty();
        }
        Object valor = sesion.state().get(EstadoConversacion.PEDIDO_ACTUAL_ID);
        return valor instanceof Number numero ? Optional.of(numero.longValue()) : Optional.empty();
    }

    private static String textoFinal(List<Event> eventos) {
        String texto = eventos.stream()
                .filter(Event::finalResponse)
                .map(Event::stringifyContent)
                .filter(t -> t != null && !t.isBlank())
                .map(String::trim)
                .collect(Collectors.joining("\n\n"));
        return texto.isBlank() ? RESPUESTA_VACIA : texto;
    }

    private static String validarSesion(String sesionId) {
        if (sesionId == null || sesionId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String id = sesionId.trim();
        if (!FORMATO_SESION.matcher(id).matches()) {
            throw new SolicitudInvalidaException(
                    "El id de sesión solo puede tener letras, números, guiones y guiones bajos (máximo 64 caracteres).");
        }
        return id;
    }

    /** Se superó el tiempo máximo de respuesta del agente completo (modelo y herramientas). */
    private static final class TiempoRespuestaAgotadoException extends RuntimeException {
        TiempoRespuestaAgotadoException() {
            super("Tiempo máximo de respuesta del asistente agotado", null, false, false);
        }
    }

    private static String validarMensaje(String mensaje) {
        if (mensaje == null || mensaje.isBlank()) {
            throw new SolicitudInvalidaException("El mensaje no puede estar vacío.");
        }
        String texto = mensaje.trim();
        if (texto.length() > LONGITUD_MAXIMA_MENSAJE) {
            throw new SolicitudInvalidaException(
                    "El mensaje supera el máximo de " + LONGITUD_MAXIMA_MENSAJE + " caracteres.");
        }
        return texto;
    }
}
