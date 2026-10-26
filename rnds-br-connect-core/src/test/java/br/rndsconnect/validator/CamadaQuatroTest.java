package br.rndsconnect.validator;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Immunization;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Portão da Semana 1: a Camada 4 carrega os artefatos oficiais da RNDS e aprova os
 * artefatos de referência do RIA-R — sem que exista um único Builder.
 *
 * <p>O teste que realmente importa aqui não é o positivo, e sim o
 * {@link #perfilRejeitaBundleSemRoute()}. Uma cadeia de validação mal montada aprova tudo
 * silenciosamente: o HAPI cai para o resource base do FHIR R4 e nada reprova. O teste
 * negativo é o que distingue "validando" de "parecendo validar".
 *
 * @author Igor Mendes Domingues Miras
 */
class CamadaQuatroTest {

    private static FhirContext ctx;
    private static ConformancePackage pacote;
    private static LocalFhirValidator validador;

    /**
     * Quantos StructureDefinition estavam sem snapshot <strong>no momento da carga</strong>.
     *
     * <p>Precisa ser capturado aqui, e não dentro do teste: o
     * {@code SnapshotGeneratingValidationSupport} grava o snapshot que gera na própria
     * instância do StructureDefinition, e essas instâncias são as mesmas que o
     * {@link ConformancePackage} guarda. Medir depois de qualquer validação contaria os
     * snapshots produzidos em runtime, não os publicados pela RNDS — e o resultado passaria
     * a depender da ordem de execução dos testes.
     */
    private static long sdSemSnapshotNaCarga;

    @BeforeAll
    static void carregar() {
        ctx = FhirContext.forR4();
        pacote = ConformancePackage.paraServico(ctx, Servico.RIA, "conformance");

        sdSemSnapshotNaCarga = pacote.structureDefinitions().stream()
                .filter(sd -> !sd.hasSnapshot())
                .count();

        validador = new LocalFhirValidator(ctx, pacote);

        System.out.printf(
                "%n[Camada 4] %d artefatos carregados (%d SD, %d VS, %d CS) em %d ms%n"
                        + "[Camada 4] SD sem snapshot na carga: %d%n"
                        + "[Camada 4] ValueSets normalizados (versao \"*\"): %d, %d referencias removidas%n",
                pacote.total(),
                pacote.structureDefinitions().size(),
                pacote.valueSets().size(),
                pacote.codeSystems().size(),
                pacote.tempoCargaMillis(),
                sdSemSnapshotNaCarga,
                pacote.valueSetsNormalizados().size(),
                pacote.referenciasCoringaRemovidas());
    }

    @Test
    @DisplayName("O pacote de conformidade carrega os artefatos do Portal de Serviços")
    void pacoteCarrega() {
        assertThat(pacote.total()).isGreaterThan(100);
        assertThat(pacote.ignorados()).isEmpty();

        List<String> urls =
                pacote.structureDefinitions().stream().map(sd -> sd.getUrl()).toList();
        assertThat(urls)
                .contains(
                        "http://www.saude.gov.br/fhir/r4/StructureDefinition/BRRegistroImunobiologicoAdministradoRotina-2.0",
                        "http://www.saude.gov.br/fhir/r4/StructureDefinition/BRImunobiologicoAdministrado-3.0",
                        "http://www.saude.gov.br/fhir/r4/StructureDefinition/BRCID10Avaliado-1.0");
    }

    @Test
    @DisplayName("Os perfis da RNDS vêm sem snapshot — a cadeia precisa gerá-lo")
    void perfisNaoTrazemSnapshot() {
        assertThat(sdSemSnapshotNaCarga)
                .as("Se algum dia vierem com snapshot, o elo SnapshotGeneratingValidationSupport "
                        + "deixa de ser obrigatório e este teste avisa")
                .isEqualTo(pacote.structureDefinitions().size());
    }

    @Test
    @DisplayName("Os ValueSets da RNDS usam versão coringa e são normalizados na carga")
    void valueSetsUsamVersaoCoringa() {
        assertThat(pacote.valueSetsNormalizados())
                .as("O FHIR R4 admite compose.include.version=\"*\", mas o expansor em memória "
                        + "do HAPI não implementa essa semântica. Se um dia implementar, ou se a "
                        + "RNDS parar de usar o coringa, este teste avisa que a normalização "
                        + "virou desnecessária.")
                .isNotEmpty();
        assertThat(pacote.referenciasCoringaRemovidas())
                .isGreaterThanOrEqualTo(pacote.valueSetsNormalizados().size());
    }

    @Test
    @DisplayName("Artefato de referência completo do RIA-R é aprovado pela Camada 4")
    void goldenCompletoAprovado() {
        Bundle bundle = ler("golden/ria-r-completo.json");
        ValidationResult resultado = validador.validar(bundle);
        imprimir("ria-r-completo", resultado);
        assertThat(LocalFhirValidator.erros(resultado)).isEmpty();
    }

    @Test
    @DisplayName("Artefato de referência mínimo do RIA-R é aprovado pela Camada 4")
    void goldenMinimoAprovado() {
        Bundle bundle = ler("golden/ria-r-minimo.json");
        ValidationResult resultado = validador.validar(bundle);
        imprimir("ria-r-minimo", resultado);
        assertThat(LocalFhirValidator.erros(resultado)).isEmpty();
    }

    @Test
    @DisplayName("CANÁRIO: sem Immunization.route o perfil reprova — se passar, a validação é inócua")
    void perfilRejeitaBundleSemRoute() {
        Bundle bundle = ler("golden/ria-r-minimo.json");
        Immunization imm = (Immunization) bundle.getEntry().get(1).getResource();
        imm.setRoute(null);

        ValidationResult resultado = validador.validar(bundle);
        List<SingleValidationMessage> erros = LocalFhirValidator.erros(resultado);
        imprimir("ria-r-minimo SEM route", resultado);

        assertThat(erros)
                .as("Immunization.route é 0..1 no FHIR R4 base e 1..1 no "
                        + "BRImunobiologicoAdministrado-3.0. Só reprova se o perfil estiver "
                        + "efetivamente aplicado.")
                .isNotEmpty();
        assertThat(erros.toString()).containsIgnoringCase("route");
    }

    private static Bundle ler(String recurso) {
        try (InputStream in = CamadaQuatroTest.class.getClassLoader().getResourceAsStream(recurso)) {
            if (in == null) {
                throw new IllegalStateException("Artefato de referência ausente: " + recurso);
            }
            return ctx.newJsonParser().parseResource(Bundle.class, new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao ler " + recurso, e);
        }
    }

    private static void imprimir(String rotulo, ValidationResult resultado) {
        System.out.println("--- " + rotulo + " ---");
        resultado
                .getMessages()
                .forEach(m ->
                        System.out.printf("  [%s] %s: %s%n", m.getSeverity(), m.getLocationString(), m.getMessage()));
    }
}
