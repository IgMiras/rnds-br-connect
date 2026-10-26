package br.rndsconnect.semantic.regras;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rndsconnect.builder.RelBundleBuilder;
import br.rndsconnect.model.CodedValue;
import br.rndsconnect.model.EnrichedRegistroExameLaboratorial;
import br.rndsconnect.model.EnrichedRegistroExameLaboratorial.ExameLaboratorial;
import br.rndsconnect.semantic.RndsValidationException;
import br.rndsconnect.validator.ConformancePackage;
import br.rndsconnect.validator.LocalFhirValidator;
import br.rndsconnect.validator.Servico;
import ca.uhn.fhir.context.FhirContext;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Elo 7 — RN1 do manual REL.
 *
 * <p>O teste que carrega o argumento é {@link #trocaDeVocabularioPassaNaCamadaQuatro()}. Os
 * três CodeSystems de resultado usam <strong>os mesmos códigos</strong> {@code 1}, {@code 2} e
 * {@code 3}, com significados diferentes:
 *
 * <table border="1">
 *   <caption>Mesmos códigos, significados distintos</caption>
 *   <tr><th></th><th>{@code 1}</th><th>{@code 2}</th><th>{@code 3}</th></tr>
 *   <tr><td>{@code DTNT}</td><td>Detectável</td><td>Não Detectável</td><td>Indeterminado</td></tr>
 *   <tr><td>{@code RGNR}</td><td>Reagente</td><td>Não Reagente</td><td>Indeterminado</td></tr>
 *   <tr><td>{@code PRAU}</td><td>Presença</td><td>Ausência</td><td>Inconclusivo</td></tr>
 * </table>
 *
 * <p>Trocar o CodeSystem mantendo o código produz um documento que a Camada 4 aprova e que
 * afirma coisa diferente da que o laboratório mediu.
 *
 * @author Igor Mendes Domingues Miras
 */
class InterpretacaoPorDiagnosticoTest {

    private static final String CS = "http://www.saude.gov.br/fhir/r4/CodeSystem/";
    private static final Instant QUANDO = Instant.parse("2024-04-01T11:18:30Z");
    private static final AtomicInteger SEQ = new AtomicInteger();

    private static LocalFhirValidator validador;
    private static InterpretacaoPorDiagnostico regra;

    @BeforeAll
    static void carregar() {
        FhirContext ctx = FhirContext.forR4();
        validador = new LocalFhirValidator(ctx, ConformancePackage.paraServico(ctx, Servico.REL, "conformance"));
        regra = new InterpretacaoPorDiagnostico();
        System.out.printf("%n[RN1] tabela cobre %d códigos LOINC%n", regra.totalDeCodigos());
    }

    // ══ O argumento ═════════════════════════════════════════════════════

    @Test
    @DisplayName("RN1: RT-PCR com resultado sorológico é APROVADO pela Camada 4")
    void trocaDeVocabularioPassaNaCamadaQuatro() {
        // 94309-2 é RT-PCR para SARS-CoV-2; a RN1 exige DTNT (detectável).
        // Aqui o resultado vem de RGNR#2 — "Não Reagente", vocabulário de sorologia.
        Bundle bundle = new RelBundleBuilder("29376", uuids(), () -> QUANDO)
                .build(laudo(exame("94309-2", CS + "BRTipoResultadoRGNR", "2")));

        assertThat(LocalFhirValidator.erros(validador.validar(bundle)))
                .as("o binding de value[x] aceita os nove CodeSystems do "
                        + "BRResultadoQualitativoExame-2.0 sem distinguir qual combina com qual exame")
                .isEmpty();

        System.out.println("[RN1] RT-PCR com resultado 'Não Reagente' (sorologia): "
                + "APROVADO pela Camada 4, em violação da RN1.");
    }

    @Test
    @DisplayName("RN1: e é REPROVADO pelo elo 7, com o exame identificado")
    void trocaDeVocabularioEReprovadaPeloElo7() {
        assertThatThrownBy(() -> regra.validar(laudo(exame("94309-2", CS + "BRTipoResultadoRGNR", "2"))))
                .isInstanceOf(RndsValidationException.class)
                .hasMessageContaining("ELO-7/RN1")
                .hasMessageContaining("94309-2")
                .hasMessageContaining("BRTipoResultadoDTNT")
                .hasMessageContaining("BRTipoResultadoRGNR");
    }

    @Test
    @DisplayName("O vocabulário correto para o mesmo exame passa")
    void vocabularioCorretoPassa() {
        assertThatCode(() -> regra.validar(laudo(exame("94309-2", CS + "BRTipoResultadoDTNT", "2"))))
                .as("94309-2 com 'Não Detectável' — é o que a RN1 exige")
                .doesNotThrowAnyException();
    }

    // ══ Silêncio do manual não é proibição ══════════════════════════════

    @Test
    @DisplayName("Exame fora da tabela é aceito — a RN1 cobre 153 dos 442 códigos aceitos")
    void exameForaDaTabelaEAceito() {
        // A tabela do manual traz 160 códigos, mas 7 deles estão entre os removidos pelo
        // compose.exclude do ValueSet: a RN1 se pronuncia sobre códigos que a RNDS não
        // aceita. Dos 442 efetivamente aceitos, 153 constam da tabela e 289 não.
        assertThat(regra.totalDeCodigos()).isEqualTo(160);

        // 100846-5 é aceito pelo catálogo e ausente da tabela da RN1 (verificado).
        assertThatCode(() -> regra.validar(laudo(exame("100846-5", CS + "BRTipoResultadoRGNR", "1"))))
                .as("tratar ausência na tabela como proibição atribuiria ao manual uma "
                        + "afirmação que ele não faz — e deixaria 289 exames sem poder ser enviados")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Exame do GAL é aceito — a tabela da RN1 só trata de LOINC")
    void exameDoGalEAceito() {
        assertThatCode(() -> regra.validar(laudo(ExameLaboratorial.builder()
                        .nomeExame(CodedValue.rnds("BRNomeExameGAL", "DENAN"))
                        .categoriaExame(CodedValue.rnds("BRSubgrupoTabelaSUS", "0202"))
                        .tipoAmostra(CodedValue.rnds("BRTipoAmostraGAL", "SWNAFA"))
                        .dataColeta(QUANDO)
                        .dataLiberacaoLaudo(QUANDO.plusSeconds(3600))
                        .metodo("Teste rápido.")
                        .valorReferencia("Não reagente em não infectados.")
                        .resultadoQualitativo(CodedValue.rnds("BRTipoResultadoRGNR", "2"))
                        .build())))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Resultado quantitativo não é conferido por este elo")
    void quantitativoNaoEConferido() {
        assertThatCode(() -> regra.validar(laudo(ExameLaboratorial.builder()
                        .nomeExame(CodedValue.rnds("BRNomeExameLOINC", "94309-2"))
                        .categoriaExame(CodedValue.rnds("BRSubgrupoTabelaSUS", "0202"))
                        .tipoAmostra(CodedValue.rnds("BRTipoAmostraGAL", "SWNAFA"))
                        .dataColeta(QUANDO)
                        .dataLiberacaoLaudo(QUANDO.plusSeconds(3600))
                        .metodo("RT-PCR em tempo real.")
                        .valorReferencia("Escala Ct.")
                        .resultadoQuantitativo(new java.math.BigDecimal("28.4"))
                        .unidadeMedida("ciclos")
                        .build())))
                .as("a RN1 fala do resultado qualitativo; o quantitativo cabe ao elo 6 (RN3)")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("O elo é opt-in: não integra a cadeia padrão do REL")
    void eOptIn() {
        // Se estivesse na cadeia padrão, este laudo em violação da RN1 seria reprovado.
        assertThat(RegrasPadrao.exameLaboratorialA2().size())
                .as("elos que dependem de tabela clínica ficam a critério do integrador (ADR-05)")
                .isZero();
    }

    // ══ Auxiliares ══════════════════════════════════════════════════════

    private static java.util.function.Supplier<String> uuids() {
        return () -> String.format("cc000000-0000-4000-8000-%012d", SEQ.incrementAndGet());
    }

    private static ExameLaboratorial exame(String loinc, String systemResultado, String codigoResultado) {
        return ExameLaboratorial.builder()
                .nomeExame(CodedValue.rnds("BRNomeExameLOINC", loinc))
                .categoriaExame(CodedValue.rnds("BRSubgrupoTabelaSUS", "0202"))
                .tipoAmostra(CodedValue.rnds("BRTipoAmostraGAL", "SWNAFA"))
                .dataColeta(QUANDO)
                .dataLiberacaoLaudo(QUANDO.plusSeconds(3600))
                .metodo("RT-PCR em tempo real.")
                .valorReferencia("Não detectável em não infectados.")
                .resultadoQualitativo(new CodedValue(systemResultado, codigoResultado))
                .build();
    }

    private static EnrichedRegistroExameLaboratorial laudo(ExameLaboratorial e) {
        return EnrichedRegistroExameLaboratorial.builder()
                .idLocalRegistro("LAUDO-RN1")
                .cnsPaciente("898005900000003")
                .cnpjLaboratorio("00394544000185")
                .cnesEstabelecimento("0123456")
                .dataRegistroOrigem(QUANDO)
                .suspeitaDiagnostica(CodedValue.rnds("BRCID10", "B04"))
                .exames(List.of(e))
                .build();
    }
}
