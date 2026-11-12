package com.udea.agente_mcp.mcp;

import java.util.List;
import java.util.Objects;

/** Comprueba la conexión con el servidor MCP y que publique las diez herramientas esperadas. */
public class DiagnosticoMcp {

    private final ConexionMcpSupermercado conexion;

    public DiagnosticoMcp(ConexionMcpSupermercado conexion) {
        this.conexion = Objects.requireNonNull(conexion, "conexion");
    }

    public EstadoMcp verificar() {
        try {
            List<String> nombres = conexion.listarHerramientasDelServidor();
            HerramientasSupermercado.Verificacion verificacion = HerramientasSupermercado.verificar(nombres);
            return new EstadoMcp(conexion.url(), true, verificacion.completa(), nombres,
                    verificacion.faltantes(), null);
        } catch (FalloMcpException e) {
            return new EstadoMcp(conexion.url(), false, false, List.of(),
                    HerramientasSupermercado.TODAS, e.tipo().name());
        }
    }

    /** Resultado del diagnóstico; {@code fallo} es la categoría técnica cuando no hay conexión. */
    public record EstadoMcp(String url, boolean disponible, boolean herramientasCompletas,
                            List<String> herramientas, List<String> faltantes, String fallo) {
    }
}
