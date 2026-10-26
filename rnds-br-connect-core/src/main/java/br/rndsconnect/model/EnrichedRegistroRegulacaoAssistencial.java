package br.rndsconnect.model;

import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * DTO enriquecido do RIRA: o que a Camada 2 entrega à Camada 3 no serviço de regulação
 * assistencial.
 *
 * <h2>O que distingue este serviço dos anteriores</h2>
 *
 * <p>RIA e REL descrevem <strong>fato consumado</strong> — a vacina foi aplicada, o exame foi
 * realizado. O RIRA descreve uma <strong>solicitação em trânsito por uma máquina de
 * estados</strong>, e o mesmo documento carrega o histórico dos estados por que ela passou.
 *
 * <p>Por isso o {@code Composition.event}, irrelevante nos outros serviços, é aqui o núcleo do
 * documento: {@code 1..*}, com sete fatias possíveis. O exemplo oficial traz três eventos —
 * {@code pending}, {@code returned-to-requester} e {@code booked} —, o que mostra que o
 * histórico é a informação, e não apenas o estado atual.
 *
 * <h2>Topologia encadeada</h2>
 *
 * <pre>
 * Composition ──section.entry──► Appointment ──basedOn──► ServiceRequest
 *      │                              │                        │
 *      └──event[].detail──────────────┴────────────────────────┘
 *                                                       Condition (reasonReference)
 * </pre>
 *
 * <p>Difere do REL, onde o vínculo é bidirecional: aqui a cadeia é linear, e os eventos
 * apontam para os resources conforme o estado.
 *
 * @author Igor Mendes Domingues Miras
 */
@Value
@Builder
public class EnrichedRegistroRegulacaoAssistencial {

    // ── Envelope ────────────────────────────────────────────────────────
    String idLocalRegistro;

    // ── Indivíduo — exatamente um dos dois ──────────────────────────────
    String cnsPaciente;
    String cpfPaciente;

    // ── Contato assistencial ────────────────────────────────────────────
    /** CNES do estabelecimento solicitante — {@code Composition.author}. */
    String cnesSolicitante;
    /** {@code Composition.date} — criação do documento no sistema de origem. */
    Instant dataRegistroOrigem;

    // ── Classificação ───────────────────────────────────────────────────
    /**
     * {@code Composition.category} — modalidade assistencial.
     *
     * <p>Binding ao ValueSet {@code BRModalidadeAssistencialMIRA}, que compõe o CodeSystem
     * {@code BRModalidadeAssistencial} e enumera <strong>apenas dois códigos</strong>:
     * {@code "04"} e {@code "09"}. Um ValueSet que restringe o CodeSystem a um par é o tipo de
     * detalhe que só aparece lendo o {@code compose}.
     */
    CodedValue modalidadeAssistencial;

    /**
     * Caráter do atendimento — {@code Appointment.appointmentType} e
     * {@code ServiceRequest.priority}, ambos {@code 1..1}.
     *
     * <p><strong>Atenção ao vocabulário.</strong> O binding é ao ValueSet
     * {@code BRCaraterAtendimentoMIRA}, que <em>não</em> compõe o CodeSystem
     * {@code BRCaraterAtendimento} da RNDS — o de sete códigos numéricos, de {@code "01"}
     * Eletivo a {@code "99"}. Ele compõe o {@code request-priority} do HL7, e admite apenas
     * {@code routine} e {@code urgent}.
     *
     * <p>Um ValueSet de nome brasileiro apontando para vocabulário internacional é armadilha:
     * quem procurar "caráter de atendimento" nos CodeSystems da RNDS encontra o vocabulário
     * errado, e os códigos numéricos reprovam o binding.
     */
    CodedValue caraterAtendimento;

    // ── A requisição (ServiceRequest) ───────────────────────────────────
    /**
     * {@code ServiceRequest.code} — procedimento solicitado.
     *
     * <p>Binding a {@code BRProcedimentosNacionais-1.0}, que compõe <strong>dois</strong>
     * CodeSystems: {@code BRTabelaSUS} (4.932 códigos de 10 dígitos) e {@code BRCBHPMTUSS}
     * (32.891 códigos de 3 dígitos). É o terceiro domínio multi-CodeSystem do framework, e o
     * {@code system} é estrutural — ver {@code RN03ProcedimentoExigeCbo}.
     */
    CodedValue procedimento;
    /** {@code ServiceRequest.performerType} — CBO exigido pela RN03 nos grupos 03 e 04. */
    CodedValue cboExecutante;
    /** {@code ServiceRequest.authoredOn} — quando a requisição foi feita. */
    Instant dataRequisicao;

    // ── O agendamento (Appointment) ─────────────────────────────────────
    /** {@code Appointment.start} e {@code end}. */
    Instant inicioAgendamento;

    Instant fimAgendamento;
    /** CNES do estabelecimento executante. */
    String cnesExecutante;

    // ── Motivo (Condition) ──────────────────────────────────────────────
    /** Presente ⇒ o Builder emite o {@code Condition} e o {@code reasonReference}. */
    CodedValue cid10;
    /** Texto livre: não passa pelo Motor Semântico. */
    String observacaoCondicao;

    // ── A máquina de estados ────────────────────────────────────────────
    /** {@code Composition.event} é {@code 1..*}: pelo menos um estado é obrigatório. */
    List<EventoRegulacao> eventos;

    /**
     * Um estado por que a solicitação passou.
     *
     * <p>O {@code code} não é traduzido: é um dos sete valores fixos do
     * {@code BRStatusRegulacaoAssistencial}, e o {@link EstadoRegulacao} os enumera. Traduzir
     * um vocabulário fechado de sete itens seria pedir ao integrador que mapeasse o que o
     * próprio framework já conhece.
     */
    @Value
    @Builder
    public static class EventoRegulacao {
        /** O estado. */
        EstadoRegulacao estado;
        /** {@code event.period.end} — quando o estado foi atingido. */
        Instant quando;
    }

    /**
     * @return o identificador do indivíduo — CNS quando presente, senão CPF
     * @throws IllegalStateException se nenhum dos dois foi informado
     */
    public String identificadorIndividuo() {
        if (cnsPaciente != null && !cnsPaciente.isBlank()) {
            return cnsPaciente;
        }
        if (cpfPaciente != null && !cpfPaciente.isBlank()) {
            return cpfPaciente;
        }
        throw new IllegalStateException("Nenhum identificador de indivíduo informado: CNS ou CPF é obrigatório "
                + "(Composition.subject.identifier é 1..1 no perfil).");
    }
}
