package com.udea.agente_mcp.agente;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Carga la instrucción de sistema del asistente desde un recurso de texto UTF-8. */
public final class InstruccionesAgente {

    public static final String RECURSO = "/agente/instrucciones-asistente.txt";

    private InstruccionesAgente() {
    }

    public static String cargar() {
        try (InputStream entrada = InstruccionesAgente.class.getResourceAsStream(RECURSO)) {
            if (entrada == null) {
                throw new IllegalStateException("No se encontró el recurso de instrucciones " + RECURSO);
            }
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el recurso de instrucciones " + RECURSO, e);
        }
    }
}
