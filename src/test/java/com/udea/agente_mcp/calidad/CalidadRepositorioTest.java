package com.udea.agente_mcp.calidad;

import com.udea.agente_mcp.agente.InstruccionesAgente;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verificaciones estáticas sobre los archivos del proyecto: codificación UTF-8, ausencia de
 * secretos reales y ausencia de restos del dominio del proyecto base.
 */
class CalidadRepositorioTest {

    private static final Path RAIZ = Path.of("").toAbsolutePath();
    private static final Set<String> DIRECTORIOS_EXCLUIDOS = Set.of(".git", "target", ".idea", ".vscode", ".mvn");
    private static final Set<String> EXTENSIONES_DE_TEXTO = Set.of(
            ".java", ".properties", ".txt", ".md", ".xml", ".example", ".gitignore", ".gitattributes", ".cmd", ".json");

    /** Patrones de credenciales reales; los valores de ejemplo del proyecto no los cumplen. */
    private static final List<Pattern> PATRONES_DE_SECRETOS = List.of(
            Pattern.compile("AIza[0-9A-Za-z_\\-]{35}"),                       // clave de API de Google
            Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----"),            // claves privadas
            Pattern.compile("ya29\\.[0-9A-Za-z_\\-]{20,}"),                   // tokens OAuth de Google
            Pattern.compile("gh[pousr]_[0-9A-Za-z]{30,}"),                    // tokens de GitHub
            Pattern.compile("(?i)(api[_-]?key|password|passwd|secret|token)\\s*[=:]\\s*\"[^\"$<{ ]{12,}\""));

    @Test
    void lasInstruccionesDelAgenteSonUtf8ValidoConTildesYEnes() throws IOException {
        try (var entrada = InstruccionesAgente.class.getResourceAsStream(InstruccionesAgente.RECURSO)) {
            assertThat(entrada).isNotNull();
            String texto = decodificarUtf8Estricto(entrada.readAllBytes());
            assertThat(texto).contains("español").contains("teléfono").contains("dirección").contains("¿Confirmas");
            assertThat(texto).isEqualTo(InstruccionesAgente.cargar());
        }
    }

    @Test
    void todosLosArchivosDeTextoDelProyectoSonUtf8Valido() throws IOException {
        List<Path> invalidos = new ArrayList<>();
        for (Path archivo : archivosDeTexto()) {
            try {
                decodificarUtf8Estricto(Files.readAllBytes(archivo));
            } catch (CharacterCodingException e) {
                invalidos.add(RAIZ.relativize(archivo));
            }
        }
        assertThat(invalidos).as("Archivos que no son UTF-8 válido").isEmpty();
    }

    @Test
    void noHaySecretosRealesEnLosArchivosDelProyecto() throws IOException {
        List<String> hallazgos = new ArrayList<>();
        for (Path archivo : archivosDeTexto()) {
            if (archivo.getFileName().toString().equals(".env")) {
                continue; // archivo local ignorado por Git; nunca se versiona
            }
            String contenido = Files.readString(archivo, StandardCharsets.UTF_8);
            for (Pattern patron : PATRONES_DE_SECRETOS) {
                if (patron.matcher(contenido).find()) {
                    hallazgos.add(RAIZ.relativize(archivo) + " -> " + patron.pattern());
                }
            }
        }
        assertThat(hallazgos).as("Posibles secretos en archivos del proyecto").isEmpty();
    }

    @Test
    void elArchivoEnvEstaIgnoradoYElEjemploNoTieneValoresReales() throws IOException {
        List<String> gitignore = Files.readAllLines(RAIZ.resolve(".gitignore"), StandardCharsets.UTF_8);
        assertThat(gitignore).contains(".env");

        Path ejemplo = RAIZ.resolve(".env.example");
        assertThat(ejemplo).exists();
        String clave = Files.readAllLines(ejemplo, StandardCharsets.UTF_8).stream()
                .filter(linea -> linea.startsWith("GOOGLE_API_KEY="))
                .findFirst().orElseThrow()
                .substring("GOOGLE_API_KEY=".length());
        assertThat(clave).isEqualTo("tu-clave-de-google-ai-studio");
    }

    @Test
    void elCodigoNoConservaReferenciasAlDominioDelProyectoBase() throws IOException {
        Pattern dominioAnterior = Pattern.compile("(?i)\\btareas?\\b|\\btasks?\\b|TareaAgent");
        List<String> hallazgos = new ArrayList<>();
        for (Path archivo : archivosDeTexto()) {
            Path relativo = RAIZ.relativize(archivo);
            if (!relativo.startsWith("src/main") && !relativo.startsWith("src\\main")) {
                continue;
            }
            if (dominioAnterior.matcher(Files.readString(archivo, StandardCharsets.UTF_8)).find()) {
                hallazgos.add(relativo.toString());
            }
        }
        assertThat(hallazgos).isEmpty();
    }

    private static List<Path> archivosDeTexto() throws IOException {
        try (Stream<Path> recorrido = Files.walk(RAIZ)) {
            return recorrido
                    .filter(Files::isRegularFile)
                    .filter(CalidadRepositorioTest::fueraDeDirectoriosExcluidos)
                    .filter(CalidadRepositorioTest::esTexto)
                    .toList();
        }
    }

    private static boolean fueraDeDirectoriosExcluidos(Path archivo) {
        for (Path parte : RAIZ.relativize(archivo)) {
            if (DIRECTORIOS_EXCLUIDOS.contains(parte.toString())) {
                return false;
            }
        }
        return true;
    }

    private static boolean esTexto(Path archivo) {
        String nombre = archivo.getFileName().toString().toLowerCase(Locale.ROOT);
        return nombre.equals(".env") || nombre.equals("mvnw")
                || EXTENSIONES_DE_TEXTO.stream().anyMatch(nombre::endsWith);
    }

    private static String decodificarUtf8Estricto(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }
}
