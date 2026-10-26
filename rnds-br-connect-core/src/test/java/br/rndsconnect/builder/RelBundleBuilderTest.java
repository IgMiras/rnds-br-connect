package br.rndsconnect.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rndsconnect.model.CodedValue;
import br.rndsconnect.model.EnrichedRegistroExameLaboratorial;
import br.rndsconnect.model.EnrichedRegistroExameLaboratorial.ExameLaboratorial;
import br.rndsconnect.validator.ConformancePackage;
import br.rndsconnect.validator.LocalFhirValidator;
import br.rndsconnect.validator.Servico;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.validation.ValidationResult;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Critério de saída do {@link RelBundleBuilder}.
 *
 * <p>É o único serviço cujo artefato de referência é <strong>reconstrução</strong>, e não
 * transcrição: o exemplo oficial não traz o {@code Specimen} que o perfil exige. Ver o README
 * do diretório {@code golden}.
 *
 * @author Igor Mendes Domingues Miras
 */
class RelBundleBuilderTest {

    private static final String SOLICITANTE = "29376";
    private static final String CNS = "898005900000823";
    private static final String CNPJ = "00394544000185";
    private static final String CNES = "034544015";
    private static final Instant DATA_REGISTRO = Instant.parse("2021-09-15T13:57:06Z");
    private static final Instant MONTAGEM = Instant.parse("2021-09-15T13:57:05.995Z");
    private static final Instant LIBERACAO = Instant.parse("2021-09-15T14:57:06Z");

    private static final AtomicInteger SEQUENCIA = new AtomicInteger();

    private static FhirContext ctx;
    private static LocalFhirValidator validador;
    private static IParser parser;

    @BeforeAll
    static void carregar() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Noronha")); // -02:00, como o exemplo
        ctx = FhirContext.forR4();
        validador = new LocalFhirValidator(ctx, ConformancePackage.paraServico(ctx, Servico.REL, "conformance"));
        parser = ctx.newJsonParser();
    }

    private static ExameLaboratorial.ExameLaboratorialBuilder exameQualitativo() {
        return ExameLaboratorial.builder()
                .nomeExame(CodedValue.rnds("BRNomeExameLOINC", "94309-2"))
                .categoriaExame(CodedValue.rnds("BRSubgrupoTabelaSUS", "0202"))
                .tipoAmostra(CodedValue.rnds("BRTipoAmostraGAL", "SWNAFA"))
                .dataColeta(DATA_REGISTRO)
                .dataLiberacaoLaudo(LIBERACAO)
                .metodo("Diagnóstico através de um hemograma.")
                .valorReferencia("Escala de -1 para +1.")
                .resultadoQualitativo(CodedValue.rnds("BRTipoResultadoDTNT", "2"))
                .patogeno(CodedValue.rnds("BRTerminologiaPatogeno", "01"));
    }

    private static EnrichedRegistroExameLaboratorial.EnrichedRegistroExameLaboratorialBuilder laudo() {
        return EnrichedRegistroExameLaboratorial.builder()
                .idLocalRegistro("12345678")
                .cnsPaciente(CNS)
                .cnpjLaboratorio(CNPJ)
                .cnesEstabelecimento(CNES)
                .dataRegistroOrigem(DATA_REGISTRO)
                // DEFEITO Nº 8: o exemplo oficial usa o nome do ValueSet como se fosse um
                // CodeSystem. ".../CodeSystem/BRTerminologiaSuspeitaDiagnostica" não existe
                // em nenhum dos 113 artefatos; o ValueSet de mesmo nome compõe o BRCID10.
                .suspeitaDiagnostica(CodedValue.rnds("BRCID10", "B04"));
    }

    private static RelBundleBuilder builderFixo() {
        // UUIDs exatos do artefato de referência.
        java.util.Deque<String> fila = new java.util.ArrayDeque<>(List.of(
                "d4e5f6a7-8b9c-4d0e-9f1a-2b3c4d5e6f70",
                "d4e5f6a7-8b9c-4d0e-9f1a-2b3c4d5e6f71",
                "d4e5f6a7-8b9c-4d0e-9f1a-2b3c4d5e6f72",
                "d4e5f6a7-8b9c-4d0e-9f1a-2b3c4d5e6f73"));
        return new RelBundleBuilder(SOLICITANTE, fila::removeFirst, () -> MONTAGEM);
    }

    private static RelBundleBuilder builder() {
        return new RelBundleBuilder(
                SOLICITANTE,
                () -> String.format("ee000000-0000-4000-8000-%012d", SEQUENCIA.incrementAndGet()),
                () -> MONTAGEM);
    }

    // ══ Critério de saída ═══════════════════════════════════════════════

    @Test
    @DisplayName("Reproduz rel-completo.json campo a campo")
    void reproduzArtefatoDeReferencia() {
        Bundle construido = builderFixo()
                .build(laudo().exames(List.of(exameQualitativo().build())).build());
        assertThat(canonico(construido)).isEqualTo(canonico(ler("golden/rel-completo.json")));
    }

    @Test
    @DisplayName("O Bundle construído passa na Camada 4")
    void bundleConstruidoEConforme() {
        Bundle b = builder()
                .build(laudo().exames(List.of(exameQualitativo().build())).build());
        ValidationResult r = validador.validar(b);
        LocalFhirValidator.erros(r)
                .forEach(m ->
                        System.out.printf("  [%s] %s: %s%n", m.getSeverity(), m.getLocationString(), m.getMessage()));
        assertThat(LocalFhirValidator.erros(r)).isEmpty();
    }

    // ══ Topologia ═══════════════════════════════════════════════════════

    @Test
    @DisplayName("O Specimen que o exemplo oficial omite é construído — 2 + 2×N resources")
    void specimenEConstruido() {
        Bundle um = builder()
                .build(laudo().exames(List.of(exameQualitativo().build())).build());
        assertThat(um.getEntry())
                .as("1 exame → Composition, Condition, Observation, Specimen")
                .hasSize(4);

        Bundle tres = builder()
                .build(laudo().exames(List.of(
                                exameQualitativo().build(),
                                exameQualitativo().build(),
                                exameQualitativo().build()))
                        .build());
        assertThat(tres.getEntry()).as("3 exames → 2 + 2×3").hasSize(8);

        assertThat(tres.getEntry().stream()
                        .filter(e -> "Specimen".equals(e.getResource().fhirType()))
                        .count())
                .as("Observation.specimen é 1..1: cada exame tem o seu")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("O vínculo é bidirecional: Condition.stage.assessment aponta para todos os exames")
    void vinculoBidirecional() {
        Bundle b = builder()
                .build(laudo().exames(List.of(
                                exameQualitativo().build(), exameQualitativo().build()))
                        .build());

        org.hl7.fhir.r4.model.Composition c =
                (org.hl7.fhir.r4.model.Composition) b.getEntry().get(0).getResource();
        assertThat(c.getSectionFirstRep().getEntry())
                .as("section.entry: 1 Condition + 2 Observation")
                .hasSize(3);

        org.hl7.fhir.r4.model.Condition cond =
                (org.hl7.fhir.r4.model.Condition) b.getEntry().get(1).getResource();
        assertThat(cond.getStageFirstRep().getAssessment())
                .as("stage.assessment aponta de volta para os dois exames")
                .hasSize(2);
        assertThat(cond.hasClinicalStatus())
                .as("clinicalStatus é PROIBIDO no BRCondicaoSaude — e obrigatório no " + "BRCID10Avaliado-1.0 do RIA-R")
                .isFalse();
    }

    @Test
    @DisplayName("Toda referência interna é urn:uuid, inclusive specimen e stage.assessment")
    void referenciasInternasUsamUrnUuid() {
        Bundle b = builder()
                .build(laudo().exames(List.of(exameQualitativo().build())).build());
        String json = canonico(b);

        assertThat(json)
                .as("o exemplo oficial usa a URL do StructureDefinition nestes dois pontos — "
                        + "referencia o TIPO, não a INSTÂNCIA (ADR-07)")
                .doesNotContain("\"reference\": \"http://www.saude.gov.br");

        Observation o = (Observation) b.getEntry().get(2).getResource();
        assertThat(o.getSpecimen().getReference()).startsWith("urn:uuid:");
    }

    // ══ Resultado quantitativo ══════════════════════════════════════════

    @Test
    @DisplayName("Quantitativo vira Quantity com unidade em texto — system e code reprovariam")
    void resultadoQuantitativoNaoCodificaUnidade() {
        Bundle b = builder()
                .build(laudo().exames(List.of(exameQualitativo()
                                .resultadoQualitativo(null)
                                .resultadoQuantitativo(new BigDecimal("4.7"))
                                .unidadeMedida("log10 cópias/mL")
                                .build()))
                        .build());

        Observation o = (Observation) b.getEntry().get(2).getResource();
        Quantity q = (Quantity) o.getValue();
        assertThat(q.getValue()).isEqualByComparingTo("4.7");
        assertThat(q.getUnit()).isEqualTo("log10 cópias/mL");

        // O binding required de value[x] recai sobre Quantity.code e exige ali um código de
        // RESULTADO QUALITATIVO. Emitir system e code UCUM reprova na Camada 4 — verificado
        // por experimento em SondaValueQuantityTest. Esta asserção trava a regressão.
        assertThat(q.hasSystem())
                .as("emitir system UCUM faria o Bundle ser reprovado pelo próprio framework")
                .isFalse();
        assertThat(q.hasCode()).isFalse();

        assertThat(LocalFhirValidator.erros(validador.validar(b)))
                .as("e a forma emitida precisa, de fato, passar")
                .isEmpty();
    }

    @Test
    @DisplayName("Quantitativo sem unidade falha: número sem unidade não tem significado clínico")
    void quantitativoSemUnidadeFalha() {
        assertThatThrownBy(() -> builder()
                        .build(laudo().exames(List.of(exameQualitativo()
                                        .resultadoQualitativo(null)
                                        .resultadoQuantitativo(new BigDecimal("4.7"))
                                        .build()))
                                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unidadeMedida");
    }

    @Test
    @DisplayName("value[x] é 1..1: nem os dois resultados, nem nenhum")
    void resultadoEExclusivo() {
        assertThatThrownBy(() -> builder()
                        .build(laudo().exames(List.of(exameQualitativo()
                                        .resultadoQuantitativo(new BigDecimal("4.7"))
                                        .unidadeMedida("log10 cópias/mL")
                                        .build()))
                                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Foram informados os dois");

        assertThatThrownBy(() -> builder()
                        .build(laudo().exames(List.of(exameQualitativo()
                                        .resultadoQualitativo(null)
                                        .build()))
                                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nenhum foi informado");
    }

    // ══ Campos de texto livre exigidos pelo perfil ══════════════════════

    @Test
    @DisplayName("metodo e valorReferencia são obrigatórios, e só aceitam texto")
    void metodoEValorReferenciaSaoObrigatorios() {
        assertThatThrownBy(() -> builder()
                        .build(laudo().exames(
                                        List.of(exameQualitativo().metodo(null).build()))
                                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("texto livre");

        Bundle b = builder()
                .build(laudo().exames(List.of(exameQualitativo().build())).build());
        Observation o = (Observation) b.getEntry().get(2).getResource();
        assertThat(o.getMethod().hasCoding())
                .as("Observation.method.coding é max=0: o perfil proíbe codificar o método")
                .isFalse();
        assertThat(o.getMethod().getText()).isNotBlank();
        assertThat(o.getReferenceRangeFirstRep().hasLow()).isFalse();
        assertThat(o.getReferenceRangeFirstRep().getText()).isNotBlank();
    }

    @Test
    @DisplayName("CANÁRIO: sem Specimen no Bundle o perfil reprova")
    void perfilRejeitaObservationSemSpecimen() {
        Bundle b = builder()
                .build(laudo().exames(List.of(exameQualitativo().build())).build());
        b.getEntry().remove(3); // remove o Specimen
        ((Observation) b.getEntry().get(2).getResource()).setSpecimen(null);

        ValidationResult r = validador.validar(b);
        assertThat(LocalFhirValidator.erros(r))
                .as("Observation.specimen é 1..1 — é exatamente o que falta no exemplo oficial " + "(defeito nº 5)")
                .isNotEmpty();
        assertThat(LocalFhirValidator.erros(r).toString()).containsIgnoringCase("specimen");
    }

    @Test
    @DisplayName("Laudo sem exame algum falha na construção")
    void laudoSemExameFalha() {
        assertThatThrownBy(() -> builder().build(laudo().exames(List.of()).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pelo menos um exame");
    }

    // ══ Auxiliares ══════════════════════════════════════════════════════

    private static String canonico(Bundle b) {
        return parser.setPrettyPrint(true).encodeResourceToString(b);
    }

    private static Bundle ler(String recurso) {
        try (InputStream in = RelBundleBuilderTest.class.getClassLoader().getResourceAsStream(recurso)) {
            if (in == null) {
                throw new IllegalStateException("Artefato de referência ausente: " + recurso);
            }
            return parser.parseResource(Bundle.class, new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao ler " + recurso, e);
        }
    }
}
