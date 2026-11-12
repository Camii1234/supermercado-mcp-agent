package com.udea.agente_mcp.agente;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Decide de forma determinística si el último mensaje del cliente autoriza explícitamente una
 * operación irreversible (confirmar o cancelar un pedido).
 *
 * <p>Es deliberadamente conservadora: ante la duda no autoriza, y el asistente vuelve a preguntar.
 * Expresiones ambiguas como "ok", "dale", "listo" o "bueno" no bastan por sí solas; tampoco un
 * mensaje con dudas o negaciones ("todavía no", "espera", "no lo confirmes", "tal vez").
 */
public final class PoliticaAutorizacion {

    /** Operaciones que requieren autorización explícita del cliente. */
    public enum Operacion { CONFIRMAR, CANCELAR }

    /** Resultado de la evaluación; {@code motivo} explica el rechazo. */
    public record Decision(boolean autorizada, String motivo) {
        static Decision permitir() {
            return new Decision(true, "");
        }

        static Decision rechazar(String motivo) {
            return new Decision(false, motivo);
        }
    }

    private static final Set<String> AFIRMACIONES_EXPLICITAS = Set.of("acepto", "autorizo");
    private static final List<String> RAICES_CONFIRMAR = List.of("confirm");
    private static final List<String> RAICES_CANCELAR = List.of("cancel", "anul");
    private static final List<String> DUDAS = List.of(
            "todavia", "aun no", "espera", "esperame", "un momento", "quizas", "quiza", "tal vez", "despues",
            "mas tarde", "lo pienso", "pensarlo", "no se", "no estoy segur", "creo que", "puede ser", "a lo mejor");
    private static final int DISTANCIA_NEGACION = 3;

    public Decision evaluar(Operacion operacion, String mensajeCliente) {
        if (mensajeCliente == null || mensajeCliente.isBlank()) {
            return Decision.rechazar("no hay un mensaje del cliente que autorice la operación");
        }
        boolean siConTilde = mensajeCliente.toLowerCase(Locale.ROOT).matches("(?s).*(^|[^\\p{L}])sí([^\\p{L}]|$).*");
        String texto = normalizar(mensajeCliente);
        List<String> palabras = Arrays.asList(texto.split(" "));
        String conEspacios = " " + texto + " ";

        for (String duda : DUDAS) {
            if (conEspacios.contains(" " + duda)) {
                return Decision.rechazar("el mensaje del cliente expresa duda o aplazamiento");
            }
        }
        if (palabras.get(0).equals("no") || negacionCercana(palabras)) {
            return Decision.rechazar("el mensaje del cliente contiene una negación");
        }

        List<String> raicesPropias = operacion == Operacion.CONFIRMAR ? RAICES_CONFIRMAR : RAICES_CANCELAR;
        if (operacion == Operacion.CONFIRMAR && contieneRaiz(palabras, RAICES_CANCELAR)) {
            return Decision.rechazar("el mensaje del cliente menciona cancelar, no confirmar");
        }

        boolean afirmacion = siConTilde
                || palabras.get(0).equals("si")
                || palabras.stream().anyMatch(AFIRMACIONES_EXPLICITAS::contains);
        if (contieneRaiz(palabras, raicesPropias) || afirmacion) {
            return Decision.permitir();
        }
        return Decision.rechazar(operacion == Operacion.CONFIRMAR
                ? "el cliente no confirmó de forma explícita (por ejemplo: \"Sí, confirmo el pedido\")"
                : "el cliente no expresó de forma explícita que quiere cancelar el pedido");
    }

    private static boolean negacionCercana(List<String> palabras) {
        for (int i = 0; i < palabras.size(); i++) {
            if (!palabras.get(i).equals("no") && !palabras.get(i).equals("ni")) {
                continue;
            }
            for (int j = i + 1; j <= Math.min(palabras.size() - 1, i + DISTANCIA_NEGACION); j++) {
                if (empiezaConAlguna(palabras.get(j), RAICES_CONFIRMAR) || empiezaConAlguna(palabras.get(j), RAICES_CANCELAR)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean contieneRaiz(List<String> palabras, List<String> raices) {
        return palabras.stream().anyMatch(p -> empiezaConAlguna(p, raices));
    }

    private static boolean empiezaConAlguna(String palabra, List<String> raices) {
        return raices.stream().anyMatch(palabra::startsWith);
    }

    /** Minúsculas, sin tildes y solo letras y dígitos separados por un espacio. */
    static String normalizar(String texto) {
        String sinTildes = Normalizer.normalize(texto.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return sinTildes.replaceAll("[^a-z0-9ñ]+", " ").trim();
    }
}
