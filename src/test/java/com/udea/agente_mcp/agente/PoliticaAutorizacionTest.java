package com.udea.agente_mcp.agente;

import com.udea.agente_mcp.agente.PoliticaAutorizacion.Operacion;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PoliticaAutorizacionTest {

    private final PoliticaAutorizacion politica = new PoliticaAutorizacion();

    @ParameterizedTest
    @ValueSource(strings = {
            "Sí, confirmo",
            "sí",
            "Si, confirma el pedido",
            "Confirmo el pedido por $22.400",
            "CONFÍRMALO por favor",
            "Acepto, adelante",
            "Sí. No olvides traer bolsas"
    })
    void autorizaConfirmacionesExplicitas(String mensaje) {
        assertThat(politica.evaluar(Operacion.CONFIRMAR, mensaje).autorizada()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ok", "dale", "listo", "bueno", "perfecto", "👍",
            "Creo que sí",
            "Todavía no",
            "Espera, quiero agregar algo",
            "No confirmes aún",
            "no",
            "Tal vez más tarde lo confirmo",
            "Sí, pero mejor cancélalo",
            "Muéstrame el resumen"
    })
    void rechazaExpresionesAmbiguasODudosas(String mensaje) {
        PoliticaAutorizacion.Decision decision = politica.evaluar(Operacion.CONFIRMAR, mensaje);

        assertThat(decision.autorizada()).isFalse();
        assertThat(decision.motivo()).isNotBlank();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void sinMensajeNoHayAutorizacion(String mensaje) {
        assertThat(politica.evaluar(Operacion.CONFIRMAR, mensaje).autorizada()).isFalse();
        assertThat(politica.evaluar(Operacion.CANCELAR, mensaje).autorizada()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Cancela el pedido 7",
            "Quiero cancelar mi pedido",
            "Sí, cancélalo",
            "sí",
            "Anula el pedido por favor",
            "Confirmo la cancelación"
    })
    void autorizaCancelacionesExplicitas(String mensaje) {
        assertThat(politica.evaluar(Operacion.CANCELAR, mensaje).autorizada()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "¿Puedo cancelar después?",
            "No lo canceles",
            "ok",
            "Muéstrame mis pedidos",
            "No quiero cancelarlo todavía"
    })
    void rechazaCancelacionesSinIntencionClara(String mensaje) {
        assertThat(politica.evaluar(Operacion.CANCELAR, mensaje).autorizada()).isFalse();
    }
}
