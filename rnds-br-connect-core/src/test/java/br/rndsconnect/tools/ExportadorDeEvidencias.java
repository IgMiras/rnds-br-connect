package br.rndsconnect.tools;

import ca.uhn.fhir.context.FhirContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.hl7.fhir.r4.model.Bundle;

/**
 * Grava o Bundle de um teste como evidência, para que o mesmo arquivo seja submetido à Camada 4
 * e ao Validador Local da RNDS.
 *
 * <p>Só age quando a variável de ambiente {@value #VARIAVEL} aponta para uma pasta: é o que faz
 * {@code scripts/evidencias-validador-local.py}. Numa execução comum da suíte, inclusive no CI,
 * não grava nada.
 *
 * <p>O nome do arquivo começa pela sigla do documento seguida de {@code --}, como em
 * {@code ria-r--minimo}: é por ela que o script escolhe o pacote de conformidade de cada
 * validador.
 *
 * @author Igor Mendes Domingues Miras
 */
public final class ExportadorDeEvidencias {

    public static final String VARIAVEL = "RNDS_EVIDENCIAS_DIR";

    private static final FhirContext CTX = FhirContext.forR4();

    private ExportadorDeEvidencias() {}

    public static void gravar(String nome, Bundle bundle) {
        String pasta = System.getenv(VARIAVEL);
        if (pasta == null || pasta.isBlank()) {
            return;
        }
        try {
            Files.createDirectories(Path.of(pasta));
            Files.writeString(
                    Path.of(pasta, nome + ".json"),
                    CTX.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gravar a evidência " + nome, e);
        }
    }
}
