package com.udea.agente_mcp.soporte;

import com.google.adk.models.BaseLlm;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Modelo de lenguaje simulado para las pruebas: en lugar de llamar a Gemini, devuelve en orden los
 * pasos de un guion (llamar a una herramienta o responder un texto). Registra las solicitudes que
 * recibe para verificar qué instrucción y qué respuestas de herramientas le entregó ADK.
 */
public final class ModeloGuionado extends BaseLlm {

    private final Queue<Function<LlmRequest, LlmResponse>> pasos = new ConcurrentLinkedQueue<>();
    private final List<LlmRequest> solicitudes = new CopyOnWriteArrayList<>();

    public ModeloGuionado() {
        super("modelo-guionado-de-prueba");
    }

    /** Siguiente paso: pedir a ADK que ejecute una herramienta. */
    public ModeloGuionado llamar(String herramienta, Map<String, Object> argumentos) {
        pasos.add(solicitud -> respuesta(Part.fromFunctionCall(herramienta, argumentos)));
        return this;
    }

    /** Siguiente paso: responder un texto fijo al usuario. */
    public ModeloGuionado responder(String texto) {
        pasos.add(solicitud -> respuesta(Part.fromText(texto)));
        return this;
    }

    /** Siguiente paso: responder un texto calculado a partir de la solicitud recibida. */
    public ModeloGuionado responderCon(Function<LlmRequest, String> generador) {
        pasos.add(solicitud -> respuesta(Part.fromText(generador.apply(solicitud))));
        return this;
    }

    public List<LlmRequest> solicitudes() {
        return List.copyOf(solicitudes);
    }

    public boolean guionTerminado() {
        return pasos.isEmpty();
    }

    @Override
    public Flowable<LlmResponse> generateContent(LlmRequest solicitud, boolean flujo) {
        solicitudes.add(solicitud);
        Function<LlmRequest, LlmResponse> paso = pasos.poll();
        if (paso == null) {
            return Flowable.error(new IllegalStateException("El guion del modelo simulado se agotó"));
        }
        return Flowable.just(paso.apply(solicitud));
    }

    @Override
    public BaseLlmConnection connect(LlmRequest solicitud) {
        throw new UnsupportedOperationException("El modelo simulado no admite conexiones en vivo");
    }

    /** Respuesta de la última herramienta que ADK entregó al modelo en esta solicitud. */
    public static Map<String, Object> ultimaRespuestaDeHerramienta(LlmRequest solicitud) {
        List<Content> contenidos = solicitud.contents();
        for (int i = contenidos.size() - 1; i >= 0; i--) {
            List<Part> partes = contenidos.get(i).parts().orElse(List.of());
            for (int j = partes.size() - 1; j >= 0; j--) {
                Optional<Map<String, Object>> respuesta = partes.get(j).functionResponse()
                        .flatMap(FunctionResponse::response);
                if (respuesta.isPresent()) {
                    return respuesta.get();
                }
            }
        }
        throw new AssertionError("La solicitud no contiene respuestas de herramientas");
    }

    /** Instrucción de sistema (con el estado de sesión ya inyectado) que recibió el modelo. */
    public static String instruccionSistema(LlmRequest solicitud) {
        return String.join("\n", solicitud.getSystemInstructions());
    }

    private static LlmResponse respuesta(Part parte) {
        return LlmResponse.builder()
                .content(Content.builder().role("model").parts(List.of(parte)).build())
                .build();
    }
}
