package br.rndsconnect.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rndsconnect.model.CodedValue;
import br.rndsconnect.model.EnrichedRegistroAtendimentoClinico;
import br.rndsconnect.model.EnrichedRegistroAtendimentoClinico.Alergia;
import br.rndsconnect.model.EnrichedRegistroAtendimentoClinico.Atestado;
import br.rndsconnect.model.EnrichedRegistroAtendimentoClinico.CidAvaliado;
import br.rndsconnect.model.EnrichedRegistroAtendimentoClinico.ContatoAssistencial;
import br.rndsconnect.model.EnrichedRegistroAtendimentoClinico.Diagnostico;
import br.rndsconnect.model.EnrichedRegistroAtendimentoClinico.ItemPrescricao;
import br.rndsconnect.model.EnrichedRegistroAtendimentoClinico.MedidaObservada;
import br.rndsconnect.model.EnrichedRegistroAtendimentoClinico.Prescricao;
import br.rndsconnect.model.EnrichedRegistroAtendimentoClinico.Procedimento;
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
import org.hl7.fhir.r4.model.CarePlan;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Encounter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Critério de saída do {@link RacBundleBuilder}.
 *
 * <p>O alvo é o artefato de referência {@code golden/rac-completo.json}: 24 entradas, 18 seções,
 * aprovado pela Camada 4 e pelo validador desktop do DATASUS. Os códigos abaixo são os mesmos do
 * artefato, inclusive os LOINC das medidas — trocá-los por outros quaisquer reprova, porque o
 * {@code BRTipoObservacao-1.0} enumera os códigos que aceita.
 *
 * @author Igor Mendes Domingues Miras
 */
class RacBundleBuilderTest {

    private static final String CNS = "819217217061851";
    private static final String CNES = "3191384";
    private static final String CNS_PROFISSIONAL = "815113499149874";
    private static final Instant ATENDIMENTO = Instant.parse("2022-05-09T12:00:00Z");
    private static final AtomicInteger SEQ = new AtomicInteger();
    private static LocalFhirValidator validador;

    @BeforeAll
    static void carregar() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
        FhirContext ctx = FhirContext.forR4();
        validador = new LocalFhirValidator(ctx, ConformancePackage.paraServico(ctx, Servico.RAC, "conformance/rac"));
    }

    private static RacBundleBuilder builder() {
        return new RacBundleBuilder(
                "12345",
                () -> String.format("7a000000-0000-4000-8000-%012d", SEQ.incrementAndGet()),
                () -> ATENDIMENTO);
    }

    private static CodedValue rnds(String cs, String code) {
        return CodedValue.rnds(cs, code);
    }

    /** O {@code BRTipoObservacao-1.0} exige a barra final — {@code http://loinc.org} reprova. */
    private static CodedValue loinc(String code) {
        return new CodedValue("https://loinc.org/", code);
    }

    /** As onze medidas do artefato de referência, nas três formas de valor que o perfil admite. */
    private static List<MedidaObservada> medidas() {
        return List.of(
                MedidaObservada.builder()
                        .medida(loinc("29463-7"))
                        .valor(new BigDecimal("1.83"))
                        .unidadeUcum("m")
                        .metodo(loinc("LA11870-5"))
                        .origemMedida(loinc("3137-7"))
                        .build(),
                MedidaObservada.builder()
                        .medida(loinc("8280-0"))
                        .valor(new BigDecimal("135.5"))
                        .unidadeUcum("cm")
                        .build(),
                MedidaObservada.builder()
                        .medida(loinc("8665-2"))
                        .valorData(Instant.parse("2022-05-23T00:00:00Z"))
                        .build(),
                MedidaObservada.builder()
                        .medida(loinc("56832-9"))
                        .valorCodigo(rnds("BRTipoSubstanciaUso", "alcool"))
                        .frequenciaUso(rnds("BRFrequenciaUsoSubstancia", "uma-ou-duas"))
                        .observacao("Uso ocasional.")
                        .build(),
                MedidaObservada.builder()
                        .medida(loinc("11885-1"))
                        .valor(new BigDecimal("3.5"))
                        .unidadeUcum("wk")
                        .build(),
                MedidaObservada.builder()
                        .medida(loinc("9843-4"))
                        .valor(new BigDecimal("45.5"))
                        .unidadeUcum("cm")
                        .build(),
                MedidaObservada.builder()
                        .medida(loinc("29463-7"))
                        .valor(new BigDecimal("88.15"))
                        .unidadeUcum("kg")
                        .metodo(loinc("LA11870-5"))
                        .origemMedida(loinc("3138-5"))
                        .roupasUsadas(loinc("LA11873-9"))
                        .build(),
                MedidaObservada.builder()
                        .medida(loinc("8480-6"))
                        .valor(new BigDecimal("0.15"))
                        .unidadeUcum("mm[Hg]")
                        .metodo(loinc("LA11868-9"))
                        .localAfericao(rnds("BRLocalAfericao", "6"))
                        .build(),
                // Contagens puras: valor sem unidade. O Builder não emite system nem code.
                MedidaObservada.builder()
                        .medida(loinc("11612-9"))
                        .valor(new BigDecimal("1"))
                        .build(),
                MedidaObservada.builder()
                        .medida(loinc("11996-6"))
                        .valor(new BigDecimal("2"))
                        .build(),
                MedidaObservada.builder()
                        .medida(loinc("11996-6"))
                        .valorCodigo(rnds("BRTipoAleitamentoMaterno", "predominante"))
                        .build());
    }

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

    /** Só o que o perfil obriga: contato, um diagnóstico e um procedimento. */
    private static EnrichedRegistroAtendimentoClinico.EnrichedRegistroAtendimentoClinicoBuilder minimo() {
        return EnrichedRegistroAtendimentoClinico.builder()
                .idLocalRegistro("rac-0001")
                .cnsPaciente(CNS)
                .cnesEstabelecimento(CNES)
                .dataRegistroOrigem(ATENDIMENTO)
                .modalidadeAssistencial(rnds("BRModalidadeAssistencial", "04"))
                .contato(contato().build())
                .diagnosticos(List.of(Diagnostico.builder()
                        .problemaDiagnostico(rnds("BRCID10", "I859"))
                        .ordem(1)
                        .papel(rnds("BRPapelProblemaDiagnostico", "NAD"))
                        .observacao("Varizes esofagianas sem sangramento.")
                        .build()))
                .procedimentos(List.of(Procedimento.builder()
                        .procedimento(rnds("BRTabelaSUS", "0301010048"))
                        .dataRealizacao(ATENDIMENTO)
                        .numeroAutorizacao("462921-M-13-17-026312-9159748-3")
                        .quantidade(23)
                        .financiamento(rnds("BRFinanciamento", "01"))
                        .cboExecutante(rnds("BRCBO", "223605"))
                        .cnsExecutante(CNS_PROFISSIONAL)
                        .cnesLotacaoExecutante(CNES)
                        .cnesEmNomeDe("162338254590005")
                        .equipe(2)
                        .build()));
    }

    private static ContatoAssistencial.ContatoAssistencialBuilder contato() {
        return ContatoAssistencial.builder()
                .caraterAtendimento(rnds("BRCaraterAtendimento", "01"))
                .dataInicio(Instant.parse("2022-04-09T12:00:00Z"))
                .dataFim(ATENDIMENTO)
                .procedencia(rnds("BRProcedencia", "99"))
                .motivoDesfecho(rnds("BRMotivoDesfecho", "06"))
                .responsabilidadeParticipante(rnds("BRResponsabilidadeParticipante", "solicitante"))
                .cnsProfissional(CNS_PROFISSIONAL)
                .cnesLotacaoProfissional(CNES)
                .cboProfissional(rnds("BRCBO", "223505"))
                .equipe(2);
    }

    /** O documento inteiro, como o artefato de referência. */
    private static EnrichedRegistroAtendimentoClinico.EnrichedRegistroAtendimentoClinicoBuilder completo() {
        return minimo().contato(contato()
                        .localAtendimento("Consultório")
                        .motivoAtendimento("Varizes esofagianas sem sangramento.")
                        .outrasInformacoes("Paciente se queixando da presença de sangue na saliva.")
                        .build())
                .medidas(medidas())
                .alergias(List.of(Alergia.builder()
                        .agente(rnds("BRImunobiologico", "2"))
                        .categoria(CategoriaAlergia.ALIMENTO)
                        .criticidade(CriticidadeAlergia.BAIXA)
                        .verificacao(VerificacaoAlergia.CONFIRMADA)
                        .dataInicio(LocalDate.of(2022, 6, 1))
                        .observacao("Soro antitetânico")
                        .build()))
                .prescricao(Prescricao.builder()
                        .cnsPrescritor("898005900000003")
                        .dataPrescricao(ATENDIMENTO)
                        .itens(List.of(item().build()))
                        .build())
                .planoCuidados("Repouso pós-medicação intramuscular")
                .atestados(List.of(Atestado.builder()
                        .dataInicioAfastamento(ATENDIMENTO)
                        .diasAfastamento(1)
                        .informacoesAdicionais("Nada digno de nota.")
                        .diagnosticos(List.of(CidAvaliado.builder()
                                .cid10(rnds("BRCID10", "I859"))
                                .categoriaDiagnostico(rnds("BRCategoriaDiagnostico", "01"))
                                .observacao("Varizes esofagianas sem sangramento.")
                                .build()))
                        .build()));
    }

    private static void imprimir(Bundle b) {
        LocalFhirValidator.erros(validador.validar(b))
                .forEach(m ->
                        System.out.printf("  [%s] %s: %s%n", m.getSeverity(), m.getLocationString(), m.getMessage()));
    }

    @Test
    @DisplayName("O Bundle completo do RAC passa na Camada 4")
    void bundleCompletoEConforme() {
        Bundle b = builder().build(completo().build());
        imprimir(b);
        assertThat(LocalFhirValidator.erros(validador.validar(b))).isEmpty();
    }

    @Test
    @DisplayName("Vinte e quatro entradas e dezoito seções, como no artefato de referência")
    void estruturaDoGolden() {
        Bundle b = builder().build(completo().build());
        assertThat(b.getEntry()).hasSize(24);
        assertThat(b.getEntry())
                .extracting(e -> e.getResource().fhirType())
                .startsWith("Composition", "Encounter", "Location", "Observation", "Condition", "Procedure");
        assertThat(b.getEntry())
                .extracting(e -> e.getResource().fhirType())
                .endsWith(
                        "AllergyIntolerance",
                        "Composition",
                        "MedicationRequest",
                        "Medication",
                        "CarePlan",
                        "Condition",
                        "CarePlan");

        Composition rac = (Composition) b.getEntry().get(0).getResource();
        assertThat(rac.getSection()).hasSize(18);
        // As seções são discriminadas por entry.resolve(): sem code, sem title.
        assertThat(rac.getSection()).allSatisfy(s -> assertThat(s.hasCode()).isFalse());
    }

    @Test
    @DisplayName("Só o mínimo obrigatório também passa: as seis seções opcionais são mesmo opcionais")
    void minimoObrigatorioEConforme() {
        Bundle b = builder().build(minimo().build());
        imprimir(b);
        assertThat(LocalFhirValidator.erros(validador.validar(b))).isEmpty();
        assertThat(((Composition) b.getEntry().get(0).getResource()).getSection())
                .hasSize(3);
    }

    @Test
    @DisplayName("A prescrição é um documento RPM envolvendo pedido e medicamento")
    void prescricaoEmbutidaComoDocumento() {
        Bundle b = builder().build(completo().build());
        Composition rpm = (Composition) b.getEntry().get(18).getResource();
        assertThat(rpm.getType().getCodingFirstRep().getCode()).isEqualTo("RPM");
        assertThat(rpm.getSectionFirstRep().getEntry()).hasSize(2);
    }

    @Test
    @DisplayName("A condição endereçada pelo atestado entra no Bundle, mas não é seção")
    void condicaoDoAtestadoNaoEhSecao() {
        Bundle b = builder().build(completo().build());
        String urlCondicao = b.getEntry().get(22).getFullUrl();
        CarePlan atestado = (CarePlan) b.getEntry().get(23).getResource();

        assertThat(atestado.getAddressesFirstRep().getReference()).isEqualTo(urlCondicao);
        assertThat(((Composition) b.getEntry().get(0).getResource()).getSection())
                .noneSatisfy(
                        s -> assertThat(s.getEntryFirstRep().getReference()).isEqualTo(urlCondicao));
        // CarePlan.description é max=0 no BRAtestado: o texto vai na atividade.
        assertThat(atestado.hasDescription()).isFalse();
        assertThat(atestado.getActivityFirstRep().getDetail().getDescription()).isEqualTo("Nada digno de nota.");
    }

    @Test
    @DisplayName("EXCLUSÃO DECLARADA: o Encounter não leva type — a Teleconsulta do manual v2.0 não é emitida")
    void teleconsultaNaoEhEmitida() {
        Encounter e = (Encounter)
                builder().build(completo().build()).getEntry().get(1).getResource();
        assertThat(e.hasType())
                .as("Encounter.type é max=0 em todo BRContatoAssistencial-1.0 publicado, e o "
                        + "CodeSystem BRModalidadeTelessaude não existe em nenhum pacote. Emitir "
                        + "produziria um documento que reprova na Camada 4.")
                .isFalse();
    }

    @Test
    @DisplayName("Obrigatórios do perfil ausentes falham na construção")
    void obrigatoriosFalham() {
        assertThatThrownBy(() -> builder()
                        .build(minimo().procedimentos(List.of(Procedimento.builder()
                                        .procedimento(rnds("BRTabelaSUS", "0301010048"))
                                        .dataRealizacao(ATENDIMENTO)
                                        .quantidade(1)
                                        .build()))
                                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("financiamento");

        assertThatThrownBy(() -> builder()
                        .build(minimo().atestados(List.of(Atestado.builder()
                                        .dataInicioAfastamento(ATENDIMENTO)
                                        .build()))
                                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("diasAfastamento");

        assertThatThrownBy(() -> builder()
                        .build(minimo().medidas(List.of(MedidaObservada.builder()
                                        .medida(loinc("29463-7"))
                                        .build()))
                                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valorCodigo");

        assertThatThrownBy(() -> builder()
                        .build(minimo().contato(contato()
                                        .responsabilidadeParticipante(null)
                                        .build())
                                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("participant.type");
    }
}
