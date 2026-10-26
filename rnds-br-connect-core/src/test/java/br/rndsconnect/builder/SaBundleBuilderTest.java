package br.rndsconnect.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rndsconnect.model.CodedValue;
import br.rndsconnect.model.EnrichedRegistroSumarioAlta;
import br.rndsconnect.model.EnrichedRegistroSumarioAlta.Alergia;
import br.rndsconnect.model.EnrichedRegistroSumarioAlta.ContatoAssistencial;
import br.rndsconnect.model.EnrichedRegistroSumarioAlta.Diagnostico;
import br.rndsconnect.model.EnrichedRegistroSumarioAlta.ItemPrescricao;
import br.rndsconnect.model.EnrichedRegistroSumarioAlta.PrescricaoAlta;
import br.rndsconnect.model.EnrichedRegistroSumarioAlta.Procedimento;
import br.rndsconnect.model.VocabularioClinicoFhir.CategoriaAlergia;
import br.rndsconnect.model.VocabularioClinicoFhir.CriticidadeAlergia;
import br.rndsconnect.model.VocabularioClinicoFhir.SituacaoPrescricao;
import br.rndsconnect.model.VocabularioClinicoFhir.UnidadeTempo;
import br.rndsconnect.model.VocabularioClinicoFhir.VerificacaoAlergia;
import br.rndsconnect.validator.ConformancePackage;
import br.rndsconnect.validator.LocalFhirValidator;
import br.rndsconnect.validator.Servico;
import ca.uhn.fhir.context.FhirContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Critério de saída do {@link SaBundleBuilder}.
 *
 * <p>O exemplo oficial do SA reprova com 36 erros (defeito nº 15), e por isso o alvo destes
 * testes é o artefato corrigido em {@code golden/sa-completo.json}: mesmas treze entradas,
 * mesmas dez seções, aprovado pelo validador desktop do DATASUS.
 *
 * @author Igor Mendes Domingues Miras
 */
class SaBundleBuilderTest {

    private static final String CNS = "706004324048847";
    private static final String CNES = "1113333";
    private static final Instant ALTA = Instant.parse("2021-01-25T11:00:00Z");
    private static final AtomicInteger SEQ = new AtomicInteger();
    private static LocalFhirValidator validador;

    @BeforeAll
    static void carregar() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
        FhirContext ctx = FhirContext.forR4();
        validador = new LocalFhirValidator(ctx, ConformancePackage.paraServico(ctx, Servico.SA, "conformance/sa"));
    }

    private static SaBundleBuilder builder() {
        return new SaBundleBuilder(
                "12345", () -> String.format("5a000000-0000-4000-8000-%012d", SEQ.incrementAndGet()), () -> ALTA);
    }

    private static CodedValue rnds(String cs, String code) {
        return CodedValue.rnds(cs, code);
    }

    /** Medicamento do item de prescrição — Paracetamol 500 mg comprimido, como no golden. */
    private static ItemPrescricao.ItemPrescricaoBuilder item() {
        return ItemPrescricao.builder()
                .medicamento(rnds("BRObmCATMAT", "BR0267778"))
                .formaFarmaceutica(rnds("BRUnidadeMedida", "19"))
                .viaAdministracao(rnds("BRViaAdministracao", "10907"))
                .dose(new BigDecimal("10"))
                .unidadeDose(rnds("BRUnidadeMedida", "13"))
                .doseMaximaPorAdministracao(new BigDecimal("10"))
                .frequencia(12)
                .vezes(1)
                .vezesMaximo(1)
                .turno(rnds("BRTurno", "3"))
                .periodo(1)
                .unidadePeriodo(UnidadeTempo.MINUTO)
                .seNecessario(true)
                .instrucaoPaciente("Tomar uma vez ao dia via oral.")
                .situacao(SituacaoPrescricao.CONCLUIDA);
    }

    private static EnrichedRegistroSumarioAlta.EnrichedRegistroSumarioAltaBuilder sumario() {
        return EnrichedRegistroSumarioAlta.builder()
                .idLocalRegistro("c6d8d486")
                .cnsPaciente(CNS)
                .cnesEstabelecimento(CNES)
                .dataRegistroOrigem(ALTA)
                .modalidadeAssistencial(rnds("BRModalidadeAssistencial", "04"))
                .contato(ContatoAssistencial.builder()
                        .caraterAtendimento(rnds("BRCaraterAtendimento", "02"))
                        .dataInicio(Instant.parse("2021-01-18T11:00:00Z"))
                        .dataFim(ALTA)
                        .procedencia(rnds("BRProcedencia", "09"))
                        .motivoDesfecho(rnds("BRMotivoDesfecho", "03"))
                        .cnsProfissionalAlta("815113499149874")
                        .cnesLotacaoProfissionalAlta("3191384")
                        .cboProfissionalAlta(rnds("BRCBO", "225125"))
                        .build())
                .diagnosticos(List.of(
                        Diagnostico.builder()
                                .cid10(rnds("BRCID10", "J121"))
                                .ordem(1)
                                .presenteNaAdmissao(true)
                                .build(),
                        Diagnostico.builder()
                                .cid10(rnds("BRCID10", "B342"))
                                .ordem(2)
                                .build()))
                .procedimentos(List.of(Procedimento.builder()
                        .procedimento(rnds("BRTabelaSUS", "0206020040"))
                        .dataRealizacao(Instant.parse("2021-01-20T12:20:32Z"))
                        .numeroAutorizacao("350001-M-24-01-000001-0000001-5")
                        .quantidade(8)
                        .financiamento(rnds("BRFinanciamento", "02"))
                        .cboExecutante(rnds("BRCBO", "225125"))
                        .cnesExecutante(CNES)
                        .observacao("Texto livre")
                        .build()))
                .restricaoFuncional("Texto livre")
                .alergia(Alergia.builder()
                        .agente(rnds("BRMedicamento", "BR0267205U0063"))
                        .categoria(CategoriaAlergia.MEDICAMENTO)
                        .criticidade(CriticidadeAlergia.ALTA)
                        .verificacao(VerificacaoAlergia.CONFIRMADA)
                        .dataInicio(LocalDate.of(2025, 9, 17))
                        .manifestacao(rnds("BRMedDRA", "10002198"))
                        .observacao("Alergia a dipirona sódica")
                        .build())
                .prescricaoAlta(PrescricaoAlta.builder()
                        .cnsPrescritor("898005900000003")
                        .dataPrescricao(ALTA)
                        .itens(List.of(item().build()))
                        .build())
                .resumoEvolucaoClinica("Evoluiu com agravamento do desconforto respiratório.")
                .planoCuidados("Repouso pós-medicação intramuscular")
                .informacoesAdicionais("Após 7 dias de tratamento recebeu alta com encaminhamento.");
    }

    private static void imprimir(Bundle b) {
        LocalFhirValidator.erros(validador.validar(b))
                .forEach(m ->
                        System.out.printf("  [%s] %s: %s%n", m.getSeverity(), m.getLocationString(), m.getMessage()));
    }

    @Test
    @DisplayName("O Bundle do SA passa na Camada 4")
    void bundleConstruidoEConforme() {
        Bundle b = builder().build(sumario().build());
        imprimir(b);
        assertThat(LocalFhirValidator.erros(validador.validar(b))).isEmpty();
    }

    @Test
    @DisplayName("Treze entradas e dez seções, como no artefato de referência")
    void estruturaDoGolden() {
        Bundle b = builder().build(sumario().build());
        assertThat(b.getEntry())
                .extracting(e -> e.getResource().fhirType())
                .containsExactly(
                        "Composition",
                        "Encounter",
                        "Condition",
                        "Condition",
                        "Procedure",
                        "Condition",
                        "AllergyIntolerance",
                        "Composition",
                        "MedicationRequest",
                        "Medication",
                        "ClinicalImpression",
                        "CarePlan",
                        "Observation");
        Composition sa = (Composition) b.getEntry().get(0).getResource();
        assertThat(sa.getSection()).hasSize(10);
        assertThat(sa.getSection()).allSatisfy(s -> assertThat(s.hasCode()).isFalse());
    }

    @Test
    @DisplayName("A prescrição de alta é um documento RPM envolvendo pedido e medicamento")
    void prescricaoEmbutidaComoDocumento() {
        Bundle b = builder().build(sumario().build());
        Composition rpm = (Composition) b.getEntry().get(7).getResource();
        assertThat(rpm.getType().getCodingFirstRep().getCode()).isEqualTo("RPM");
        assertThat(rpm.getSectionFirstRep().getEntry()).hasSize(2);
    }

    @Test
    @DisplayName("Os opcionais local de atendimento e motivo geram Location e Observation, e validam")
    void opcionaisDoContatoValidam() {
        EnrichedRegistroSumarioAlta dto = sumario()
                .contato(ContatoAssistencial.builder()
                        .caraterAtendimento(rnds("BRCaraterAtendimento", "02"))
                        .dataInicio(Instant.parse("2021-01-18T11:00:00Z"))
                        .dataFim(ALTA)
                        .procedencia(rnds("BRProcedencia", "09"))
                        .motivoDesfecho(rnds("BRMotivoDesfecho", "03"))
                        .cnsProfissionalAlta("815113499149874")
                        .localAtendimento("Unidade de Terapia Intensiva")
                        .motivoAtendimento("Febre persistente e dispneia.")
                        .build())
                .build();
        Bundle b = builder().build(dto);
        assertThat(b.getEntry()).extracting(e -> e.getResource().fhirType()).contains("Location");
        imprimir(b);
        assertThat(LocalFhirValidator.erros(validador.validar(b))).isEmpty();
    }

    @Test
    @DisplayName("SEXTA DEMONSTRAÇÃO: dipirona prescrita a paciente alérgico a dipirona passa na Camada 4")
    void alergiaContraditoriaPassaNaCamadaQuatro() {
        // Alergia registrada: BR0267205U0063 (BRMedicamento), dipirona solução.
        // Prescrição de alta:  BR0267203      (BRObmCATMAT),   dipirona comprimido.
        // Mesma substância, códigos e vocabulários diferentes — nenhum dos dois informa o
        // princípio ativo, e nem comparar códigos detectaria o conflito.
        EnrichedRegistroSumarioAlta perigoso = sumario()
                .prescricaoAlta(PrescricaoAlta.builder()
                        .cnsPrescritor("898005900000003")
                        .dataPrescricao(ALTA)
                        .itens(List.of(item().medicamento(rnds("BRObmCATMAT", "BR0267203"))
                                .build()))
                        .build())
                .build();
        assertThat(LocalFhirValidator.erros(validador.validar(builder().build(perigoso))))
                .as("conformidade estrutural não é correção clínica: o validador aprova")
                .isEmpty();
    }

    @Test
    @DisplayName("Obrigatórios do perfil ausentes falham na construção")
    void obrigatoriosFalham() {
        assertThatThrownBy(() -> builder()
                        .build(sumario()
                                .prescricaoAlta(PrescricaoAlta.builder()
                                        .cnsPrescritor("898005900000003")
                                        .dataPrescricao(ALTA)
                                        .itens(List.of(item().vezesMaximo(null).build()))
                                        .build())
                                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("countMax");
        assertThatThrownBy(() -> builder()
                        .build(sumario()
                                .procedimentos(List.of(Procedimento.builder()
                                        .procedimento(rnds("BRTabelaSUS", "0206020040"))
                                        .dataRealizacao(ALTA)
                                        .build()))
                                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("financiamento");
    }
}
