package br.rndsconnect.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rndsconnect.model.CodedValue;
import br.rndsconnect.model.EnrichedRegistroRegulacaoAssistencial;
import br.rndsconnect.model.EnrichedRegistroRegulacaoAssistencial.EventoRegulacao;
import br.rndsconnect.model.EstadoRegulacao;
import br.rndsconnect.validator.ConformancePackage;
import br.rndsconnect.validator.LocalFhirValidator;
import br.rndsconnect.validator.Servico;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.ValidationResult;
import java.time.Instant;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;
import org.hl7.fhir.r4.model.Appointment;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Critério de saída do {@link RiraBundleBuilder}.
 *
 * <p>É o primeiro serviço cujo documento descreve uma <strong>solicitação em trânsito</strong>,
 * e não um fato consumado. O teste que carrega essa diferença é
 * {@link #historicoDeEstadosEONucleoDoDocumento()}: o mesmo Bundle carrega três eventos, e
 * cada um aponta para o resource pertinente ao seu estado.
 *
 * @author Igor Mendes Domingues Miras
 */
class RiraBundleBuilderTest {

    private static final String SOLICITANTE = "29376";
    private static final String CNS = "819217217061851";
    private static final String CNES_SOLICITANTE = "162338254590005";
    private static final Instant QUANDO = Instant.parse("2023-04-09T00:00:00Z");
    private static final AtomicInteger SEQ = new AtomicInteger();

    private static LocalFhirValidator validador;

    @BeforeAll
    static void carregar() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
        FhirContext ctx = FhirContext.forR4();
        // Pacote PRÓPRIO do RIRA: 318 artefatos, distintos dos 113 do RIA e REL.
        validador = new LocalFhirValidator(ctx, ConformancePackage.paraServico(ctx, Servico.RIRA, "conformance/rira"));
    }

    private static RiraBundleBuilder builder() {
        return new RiraBundleBuilder(
                SOLICITANTE, () -> String.format("dd000000-0000-4000-8000-%012d", SEQ.incrementAndGet()), () -> QUANDO);
    }

    private static EnrichedRegistroRegulacaoAssistencial.EnrichedRegistroRegulacaoAssistencialBuilder solicitacao() {
        return EnrichedRegistroRegulacaoAssistencial.builder()
                .idLocalRegistro("REG-RIRA-0001")
                .cnsPaciente(CNS)
                .cnesSolicitante(CNES_SOLICITANTE)
                .cnesExecutante(CNES_SOLICITANTE)
                .dataRegistroOrigem(QUANDO)
                .modalidadeAssistencial(CodedValue.rnds("BRModalidadeAssistencial", "09"))
                // O ValueSet BRCaraterAtendimentoMIRA compoe o request-priority do HL7,
                // NAO o BRCaraterAtendimento da RNDS: so aceita routine e urgent.
                .caraterAtendimento(new CodedValue("http://hl7.org/fhir/request-priority", "routine"))
                .procedimento(CodedValue.rnds("BRTabelaSUS", "0406010013"))
                .cboExecutante(CodedValue.rnds("BRCBO", "225265"))
                .dataRequisicao(QUANDO)
                .inicioAgendamento(QUANDO)
                .fimAgendamento(QUANDO)
                .cid10(CodedValue.rnds("BRCID10", "I85"))
                .eventos(List.of(EventoRegulacao.builder()
                        .estado(EstadoRegulacao.PENDENTE)
                        .quando(QUANDO)
                        .build()));
    }

    // ══ Critério de saída ═══════════════════════════════════════════════

    @Test
    @DisplayName("O Bundle construído passa na Camada 4, com o pacote do RIRA")
    void bundleConstruidoEConforme() {
        Bundle b = builder().build(solicitacao().build());
        ValidationResult r = validador.validar(b);
        LocalFhirValidator.erros(r)
                .forEach(m ->
                        System.out.printf("  [%s] %s: %s%n", m.getSeverity(), m.getLocationString(), m.getMessage()));
        assertThat(LocalFhirValidator.erros(r)).isEmpty();
    }

    // ══ A máquina de estados ════════════════════════════════════════════

    @Test
    @DisplayName("O histórico de estados é o núcleo do documento")
    void historicoDeEstadosEONucleoDoDocumento() {
        Bundle b = builder()
                .build(solicitacao()
                        .eventos(List.of(
                                EventoRegulacao.builder()
                                        .estado(EstadoRegulacao.PENDENTE)
                                        .quando(QUANDO)
                                        .build(),
                                EventoRegulacao.builder()
                                        .estado(EstadoRegulacao.DEVOLVIDA)
                                        .quando(QUANDO)
                                        .build(),
                                EventoRegulacao.builder()
                                        .estado(EstadoRegulacao.AGENDADA)
                                        .quando(QUANDO)
                                        .build()))
                        .build());

        Composition c = (Composition) b.getEntry().get(0).getResource();
        assertThat(c.getEvent())
                .as("RIA e REL registram fato consumado; o RIRA registra a trajetória")
                .hasSize(3);
        assertThat(c.getEvent())
                .extracting(e -> e.getCodeFirstRep().getCodingFirstRep().getCode())
                .containsExactly("pending", "returned-to-requester", "booked");

        assertThat(LocalFhirValidator.erros(validador.validar(b))).isEmpty();
    }

    @Test
    @DisplayName("Estados antes do agendamento apontam para a requisição; depois, para o agendamento")
    void detalheDoEventoSegueOEstado() {
        Bundle b = builder()
                .build(solicitacao()
                        .eventos(List.of(
                                EventoRegulacao.builder()
                                        .estado(EstadoRegulacao.PENDENTE)
                                        .quando(QUANDO)
                                        .build(),
                                EventoRegulacao.builder()
                                        .estado(EstadoRegulacao.ATENDIDA)
                                        .quando(QUANDO)
                                        .build()))
                        .build());

        String uuidAppointment = b.getEntry().get(1).getFullUrl();
        String uuidServiceRequest = b.getEntry().get(2).getFullUrl();
        Composition c = (Composition) b.getEntry().get(0).getResource();

        // detail[0] e sempre o estabelecimento; o resource, quando admitido, vem em [1].
        assertThat(c.getEvent().get(0).getDetail().get(1).getReference())
                .as("antes de booked não há agendamento a que se referir")
                .isEqualTo(uuidServiceRequest);
        assertThat(c.getEvent().get(1).getDetail().get(1).getReference()).isEqualTo(uuidAppointment);
    }

    @Test
    @DisplayName("Identificador local é PROIBIDO nos resources internos")
    void identificadorLocalEProibidoNosResourcesInternos() {
        Bundle b = builder().build(solicitacao().build());

        // Appointment.identifier e ServiceRequest.identifier sao max=0. O Bundle.identifier
        // ja carrega o idLocalRegistro, e repeti-lo nos resources internos e erro.
        assertThat(((Appointment) b.getEntry().get(1).getResource()).hasIdentifier())
                .isFalse();
        assertThat(((ServiceRequest) b.getEntry().get(2).getResource()).hasIdentifier())
                .isFalse();
        assertThat(b.getIdentifier().getValue()).isEqualTo("REG-RIRA-0001");
    }

    @Test
    @DisplayName("O caráter do atendimento vem do vocabulário do HL7, não do BRCaraterAtendimento")
    void caraterAtendimentoUsaVocabularioDoHl7() {
        Bundle b = builder().build(solicitacao().build());

        Appointment a = (Appointment) b.getEntry().get(1).getResource();
        assertThat(a.getAppointmentType().getCodingFirstRep().getSystem())
                .as("BRCaraterAtendimentoMIRA compõe request-priority do HL7; usar o "
                        + "BRCaraterAtendimento da RNDS, com códigos 01 a 99, reprova o binding")
                .isEqualTo("http://hl7.org/fhir/request-priority");

        ServiceRequest s = (ServiceRequest) b.getEntry().get(2).getResource();
        assertThat(s.getPriority()).isEqualTo(ServiceRequest.ServiceRequestPriority.ROUTINE);
    }

    @Test
    @DisplayName("Documento sem evento algum é recusado — event é 1..*")
    void semEventoFalha() {
        assertThatThrownBy(
                        () -> builder().build(solicitacao().eventos(List.of()).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pelo menos um evento")
                .hasMessageContaining("não descreve nada");
    }

    @Test
    @DisplayName("Os sete estados do enum correspondem às fatias do perfil")
    void seteEstadosCorrespondemAsFatias() {
        assertThat(EstadoRegulacao.values()).hasSize(7);
        assertThat(EstadoRegulacao.values())
                .extracting(EstadoRegulacao::codigo)
                .containsExactly(
                        "pending", "returned-to-requester", "booked", "attended", "cancelled", "absence", "excluded");
    }

    // ══ Topologia encadeada ═════════════════════════════════════════════

    @Test
    @DisplayName("A cadeia é linear: Composition → Appointment → ServiceRequest")
    void cadeiaELinear() {
        Bundle b = builder().build(solicitacao().build());
        assertThat(b.getEntry()).hasSize(4);

        String uuidAppointment = b.getEntry().get(1).getFullUrl();
        String uuidServiceRequest = b.getEntry().get(2).getFullUrl();
        String uuidCondition = b.getEntry().get(3).getFullUrl();

        Composition c = (Composition) b.getEntry().get(0).getResource();
        assertThat(c.getSectionFirstRep().getEntryFirstRep().getReference()).isEqualTo(uuidAppointment);

        Appointment a = (Appointment) b.getEntry().get(1).getResource();
        assertThat(a.getBasedOnFirstRep().getReference())
                .as("no REL o vínculo é bidirecional; aqui é encadeado")
                .isEqualTo(uuidServiceRequest);
        assertThat(a.getReasonReferenceFirstRep().getReference()).isEqualTo(uuidCondition);

        ServiceRequest s = (ServiceRequest) b.getEntry().get(2).getResource();
        assertThat(s.getReasonReferenceFirstRep().getReference()).isEqualTo(uuidCondition);
    }

    @Test
    @DisplayName("O CID é OBRIGATÓRIO — reasonReference é 1..1 nos dois resources")
    void cidEObrigatorio() {
        // No RIA-R e no REL o Condition e condicional. Aqui nao: uma solicitacao de
        // regulacao sem motivo clinico nao existe.
        assertThatThrownBy(() -> builder().build(solicitacao().cid10(null).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cid10")
                .hasMessageContaining("nao e opcional");

        Bundle b = builder().build(solicitacao().build());
        assertThat(b.getEntry()).hasSize(4);
        assertThat(((Appointment) b.getEntry().get(1).getResource()).hasReasonReference())
                .isTrue();
        assertThat(((ServiceRequest) b.getEntry().get(2).getResource()).hasReasonReference())
                .isTrue();
    }

    @Test
    @DisplayName("A estrutura de detail varia por fatia de evento")
    void detailVariaPorFatia() {
        Bundle b = builder()
                .build(solicitacao()
                        .eventos(List.of(
                                EventoRegulacao.builder()
                                        .estado(EstadoRegulacao.PENDENTE)
                                        .quando(QUANDO)
                                        .build(),
                                EventoRegulacao.builder()
                                        .estado(EstadoRegulacao.DEVOLVIDA)
                                        .quando(QUANDO)
                                        .build(),
                                EventoRegulacao.builder()
                                        .estado(EstadoRegulacao.CANCELADA)
                                        .quando(QUANDO)
                                        .build()))
                        .build());

        Composition c = (Composition) b.getEntry().get(0).getResource();
        assertThat(c.getEvent().get(0).getDetail())
                .as("pending: estabelecimento + requisicao")
                .hasSize(2);
        assertThat(c.getEvent().get(1).getDetail())
                .as("returned-to-requester: detail e 1..1 e detail.reference e max=0 — "
                        + "devolver ao solicitante e ato do estabelecimento, nao do agendamento")
                .hasSize(1);
        assertThat(c.getEvent().get(1).getDetailFirstRep().hasReference()).isFalse();
        assertThat(c.getEvent().get(2).getDetail())
                .as("cancelled: so o estabelecimento")
                .hasSize(1);

        assertThat(LocalFhirValidator.erros(validador.validar(b))).isEmpty();
    }

    // ══ Pacote próprio ══════════════════════════════════════════════════

    @Test
    @DisplayName("O RIRA usa seu próprio pacote de conformidade, com 318 artefatos")
    void usaPacoteProprio() {
        FhirContext ctx = FhirContext.forR4();
        ConformancePackage p = ConformancePackage.paraServico(ctx, Servico.RIRA, "conformance/rira");

        assertThat(p.servico()).isEqualTo(Servico.RIRA);
        assertThat(p.total())
                .as("os 113 artefatos do RIA e REL não bastam: o RIRA traz Appointment, "
                        + "ServiceRequest e vocabulários próprios")
                .isEqualTo(318);

        System.out.printf(
                "%n[RIRA] %d artefatos (%d SD, %d VS, %d CS) em %d ms%n",
                p.total(),
                p.structureDefinitions().size(),
                p.valueSets().size(),
                p.codeSystems().size(),
                p.tempoCargaMillis());
    }
}
