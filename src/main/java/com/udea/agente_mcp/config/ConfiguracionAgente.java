package com.udea.agente_mcp.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/**
 * Configuración centralizada del Asistente de Supermercado.
 *
 * <p>Todos los valores se leen de variables de entorno (o propiedades del sistema/Spring con el
 * mismo nombre) y tienen un valor predeterminado apto para el laboratorio local. Esta clase es la
 * única que conoce los nombres de las variables y sus valores por defecto; la usan tanto la
 * aplicación Spring Boot como la interfaz de desarrollo de ADK.
 *
 * <p>La clave de Google nunca se guarda aquí: la lee directamente la librería de Gemini desde
 * {@value #VAR_CLAVE_GOOGLE}. Solo se expone si está configurada o no.
 */
public record ConfiguracionAgente(
        URI urlServidorMcp,
        Duration tiempoConexionMcp,
        Duration tiempoLecturaMcp,
        Duration tiempoNotificacionMcp,
        String modelo,
        Duration tiempoRespuestaAgente,
        boolean verificarMcpAlIniciar) {

    public static final String VAR_URL_MCP = "MCP_SERVER_URL";
    public static final String VAR_TIEMPO_CONEXION_MCP = "MCP_CONNECT_TIMEOUT_SECONDS";
    public static final String VAR_TIEMPO_LECTURA_MCP = "MCP_READ_TIMEOUT_SECONDS";
    public static final String VAR_TIEMPO_NOTIFICACION_MCP = "MCP_NOTIFICATION_TIMEOUT_SECONDS";
    public static final String VAR_VERIFICAR_MCP = "MCP_VERIFY_ON_STARTUP";
    public static final String VAR_MODELO = "GEMINI_MODEL";
    public static final String VAR_TIEMPO_RESPUESTA = "AGENT_RESPONSE_TIMEOUT_SECONDS";
    public static final String VAR_CLAVE_GOOGLE = "GOOGLE_API_KEY";
    public static final String VAR_CLAVE_GEMINI = "GEMINI_API_KEY";
    public static final String VAR_USAR_VERTEX = "GOOGLE_GENAI_USE_VERTEXAI";

    public static final String URL_MCP_PREDETERMINADA = "http://localhost:8088/mcp";
    /** Modelo validado en la prueba conversacional real del laboratorio. */
    public static final String MODELO_PREDETERMINADO = "gemini-3.6-flash";
    static final long CONEXION_MCP_SEGUNDOS = 10;
    static final long LECTURA_MCP_SEGUNDOS = 30;
    static final long NOTIFICACION_MCP_SEGUNDOS = 2;
    static final long RESPUESTA_AGENTE_SEGUNDOS = 120;
    private static final long MAXIMO_SEGUNDOS = 600;

    public ConfiguracionAgente {
        Objects.requireNonNull(urlServidorMcp, "urlServidorMcp");
        Objects.requireNonNull(tiempoConexionMcp, "tiempoConexionMcp");
        Objects.requireNonNull(tiempoLecturaMcp, "tiempoLecturaMcp");
        Objects.requireNonNull(tiempoNotificacionMcp, "tiempoNotificacionMcp");
        Objects.requireNonNull(tiempoRespuestaAgente, "tiempoRespuestaAgente");
        if (modelo == null || modelo.isBlank()) {
            throw new IllegalArgumentException("El modelo (" + VAR_MODELO + ") no puede estar vacío.");
        }
    }

    /** Lee la configuración de las variables de entorno, con propiedades del sistema como alternativa. */
    public static ConfiguracionAgente desdeEntorno() {
        return desde(clave -> {
            String propiedad = System.getProperty(clave);
            return propiedad != null ? propiedad : System.getenv(clave);
        });
    }

    /**
     * Construye la configuración a partir de una fuente de valores (por ejemplo, el Environment de
     * Spring). Los valores ausentes o en blanco toman el valor predeterminado.
     *
     * @throws IllegalArgumentException si algún valor presente es inválido
     */
    public static ConfiguracionAgente desde(Function<String, String> fuente) {
        Objects.requireNonNull(fuente, "fuente");
        return new ConfiguracionAgente(
                urlMcp(valor(fuente, VAR_URL_MCP, URL_MCP_PREDETERMINADA)),
                segundos(fuente, VAR_TIEMPO_CONEXION_MCP, CONEXION_MCP_SEGUNDOS),
                segundos(fuente, VAR_TIEMPO_LECTURA_MCP, LECTURA_MCP_SEGUNDOS),
                segundos(fuente, VAR_TIEMPO_NOTIFICACION_MCP, NOTIFICACION_MCP_SEGUNDOS),
                valor(fuente, VAR_MODELO, MODELO_PREDETERMINADO),
                segundos(fuente, VAR_TIEMPO_RESPUESTA, RESPUESTA_AGENTE_SEGUNDOS),
                booleano(fuente, VAR_VERIFICAR_MCP, true));
    }

    /**
     * Indica si hay credenciales para Gemini (clave de Google AI Studio o Vertex AI), sin leer ni
     * devolver su valor.
     */
    public static boolean credencialesGeminiConfiguradas(Function<String, String> fuente) {
        return tieneTexto(fuente.apply(VAR_CLAVE_GOOGLE))
                || tieneTexto(fuente.apply(VAR_CLAVE_GEMINI))
                || "true".equalsIgnoreCase(recortar(fuente.apply(VAR_USAR_VERTEX)));
    }

    private static String valor(Function<String, String> fuente, String clave, String predeterminado) {
        String valor = recortar(fuente.apply(clave));
        return valor == null || valor.isEmpty() ? predeterminado : valor;
    }

    private static URI urlMcp(String texto) {
        try {
            URI uri = new URI(texto);
            String esquema = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(esquema.equals("http") || esquema.equals("https")) || uri.getHost() == null) {
                throw new IllegalArgumentException(
                        VAR_URL_MCP + " debe ser una URL http(s) absoluta, por ejemplo " + URL_MCP_PREDETERMINADA
                                + "; se recibió: " + texto);
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(VAR_URL_MCP + " no es una URL válida: " + texto, e);
        }
    }

    private static Duration segundos(Function<String, String> fuente, String clave, long predeterminado) {
        String texto = valor(fuente, clave, Long.toString(predeterminado));
        long segundos;
        try {
            segundos = Long.parseLong(texto);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(clave + " debe ser un número entero de segundos; se recibió: " + texto, e);
        }
        if (segundos < 1 || segundos > MAXIMO_SEGUNDOS) {
            throw new IllegalArgumentException(
                    clave + " debe estar entre 1 y " + MAXIMO_SEGUNDOS + " segundos; se recibió: " + segundos);
        }
        return Duration.ofSeconds(segundos);
    }

    private static boolean booleano(Function<String, String> fuente, String clave, boolean predeterminado) {
        String texto = valor(fuente, clave, Boolean.toString(predeterminado)).toLowerCase(Locale.ROOT);
        return switch (texto) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException(clave + " debe ser true o false; se recibió: " + texto);
        };
    }

    private static boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    private static String recortar(String valor) {
        return valor == null ? null : valor.trim();
    }
}
