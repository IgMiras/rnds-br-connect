package br.rndsconnect.builder;

import br.rndsconnect.model.CodedValue;
import br.rndsconnect.model.EnrichedRegistroRegulacaoAssistencial;
import br.rndsconnect.model.EnrichedRegistroRegulacaoAssistencial.EventoRegulacao;
import br.rndsconnect.model.EstadoRegulacao;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.hl7.fhir.r4.model.Appointment;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ServiceRequest;

/**
 * Camada 3 — construtor do Bundle do serviço RIRA (regulação assistencial).
 *
 * <p>Produz um Bundle {@code document} com <strong>quatro</strong> resources. Diferente do
 * RIA-R e do REL, onde o {@code Condition} é condicional, aqui ele é obrigatório:
 * {@code reasonReference} é {@code 1..1} tanto no {@code Appointment} quanto no
 * {@code ServiceRequest} — uma solicitação de regulação sem motivo clínico não existe.
 *
 * <pre>
 * Composition ──section.entry──► Appointment ──basedOn──► ServiceRequest
 *      │                                                        │
 *      └── event[] ──► detalhes por estado          reasonReference ──► Condition
 * </pre>
 *
 * <h2>O que distingue este Builder dos anteriores</h2>
 *
 * <p>RIA e REL constroem o registro de um <strong>fato consumado</strong>. O RIRA constrói o
 * registro de uma <strong>solicitação em trânsito</strong>, e o núcleo do documento é
 * {@code Composition.event} — {@code 1..*}, com sete fatias possíveis, uma por estado da
 * máquina de regulação. O exemplo oficial traz três eventos, o que mostra que o
 * <em>histórico</em> é a informação, e não apenas o estado corrente.
 *
 * <p>A topologia também difere: no REL o vínculo é bidirecional — a {@code Composition}
 * aponta para o {@code Observation} e o {@code Condition} aponta de volta. Aqui a cadeia é
 * <strong>linear</strong>: {@code Composition → Appointment → ServiceRequest}.
 *
 * <h2>Estados não passam pela Frente B</h2>
 *
 * <p>Os sete estados vêm de {@link EstadoRegulacao}, um enum, e não de um
 * {@code TerminologyDomain}. O critério é o mesmo que rege {@code Composition.type} e
 * {@code Composition.status} nos outros Builders: vocabulário <strong>fechado e definido pelo
 * próprio perfil</strong> é constante do framework, não decisão do integrador. Mapear sete
 * valores que o perfil enumera seria transferir trabalho sem transferir escolha.
 *
 * @author Igor Mendes Domingues Miras
 */
public class RiraBundleBuilder implements FhirBundleBuilder<EnrichedRegistroRegulacaoAssistencial> {

    private static final String SD = "http://www.saude.gov.br/fhir/r4/StructureDefinition/";
    private static final String NAMING_SYSTEM = "http://www.saude.gov.br/fhir/r4/NamingSystem/BRRNDS-";

    private static final String PERFIL_COMPOSITION = SD + "BRRegulacaoAssistencial";
    private static final String PERFIL_APPOINTMENT = SD + "BRAgendamentoRegulacaoAssistencial";
    private static final String PERFIL_SERVICE_REQUEST = SD + "BRRequisicaoRegulacaoAssistencial";
    private static final String PERFIL_CONDITION = SD + "BRCID10Avaliado-1.0";

    private static final String SYSTEM_INDIVIDUO = SD + "BRIndividuo-1.0";
    private static final String SYSTEM_ESTABELECIMENTO = SD + "BREstabelecimentoSaude-1.0";

    /** {@code fixedString} do perfil. */
    private static final String TITULO = "Regulação Assistencial";

    private static final CodedValue TIPO_DOCUMENTO = CodedValue.rnds("BRTipoDocumento", "RA");
    private static final CodedValue TIPO_PARTICIPANTE_PACIENTE = CodedValue.rnds("BRTipoParticipante", "PCT");
    private static final CodedValue CATEGORIA_DIAGNOSTICO = CodedValue.rnds("BRCategoriaDiagnostico", "01");
    private static final CodedValue CONDITION_ATIVA =
            new CodedValue("http://terminology.hl7.org/CodeSystem/condition-clinical", "active");

    /**
     * Caráter do atendimento.
     *
     * <p>O ValueSet {@code BRCaraterAtendimentoMIRA} não compõe o CodeSystem
     * {@code BRCaraterAtendimento} da RNDS — que tem sete códigos numéricos —, e sim o
     * {@code request-priority} do HL7, com apenas {@code routine} e {@code urgent}. Um
     * ValueSet de nome brasileiro apontando para vocabulário internacional é o tipo de
     * detalhe que só aparece lendo o {@code compose}.
     */
    private static final String SYSTEM_PRIORIDADE = "http://hl7.org/fhir/request-priority";

    private final String solicitanteId;
    private final Supplier<String> geradorUuid;
    private final Supplier<Instant> relogio;

    /**
     * @param solicitanteId Identificador Solicitante fornecido pela RNDS no credenciamento
     */
    public RiraBundleBuilder(String solicitanteId) {
        this(solicitanteId, () -> UUID.randomUUID().toString(), Instant::now);
    }

    /**
     * @param solicitanteId Identificador Solicitante
     * @param geradorUuid fonte dos UUIDs transientes — injetável para testes determinísticos
     * @param relogio instante de montagem do Bundle — idem
     */
    public RiraBundleBuilder(String solicitanteId, Supplier<String> geradorUuid, Supplier<Instant> relogio) {
        this.solicitanteId = Objects.requireNonNull(solicitanteId, "solicitanteId é obrigatório");
        this.geradorUuid = Objects.requireNonNull(geradorUuid, "geradorUuid é obrigatório");
        this.relogio = Objects.requireNonNull(relogio, "relogio é obrigatório");
    }

    @Override
    public Bundle build(EnrichedRegistroRegulacaoAssistencial dto) {
        exigirObrigatorios(dto);

        // Sorteados antes da montagem: a Composition depende do UUID do Appointment, que
        // depende do ServiceRequest, e os eventos referenciam ambos.
        String uuidComposition = geradorUuid.get();
        String uuidAppointment = geradorUuid.get();
        String uuidServiceRequest = geradorUuid.get();
        // reasonReference e 1..1 no Appointment E no ServiceRequest: o CID nao e opcional.
        String uuidCondition = geradorUuid.get();

        Instant montagem = relogio.get();

        Bundle bundle = new Bundle();
        bundle.setMeta(new Meta().setLastUpdated(Date.from(montagem)));
        bundle.setIdentifier(
                new Identifier().setSystem(NAMING_SYSTEM + solicitanteId).setValue(dto.getIdLocalRegistro()));
        bundle.setType(Bundle.BundleType.DOCUMENT);
        bundle.setTimestamp(Date.from(montagem));

        adicionar(bundle, uuidComposition, montarComposition(dto, uuidAppointment, uuidServiceRequest));
        adicionar(bundle, uuidAppointment, montarAppointment(dto, uuidServiceRequest, uuidCondition));
        adicionar(bundle, uuidServiceRequest, montarServiceRequest(dto, uuidCondition));
        adicionar(bundle, uuidCondition, montarCondition(dto));
        return bundle;
    }

    // ══ Composition ═════════════════════════════════════════════════════

    private Composition montarComposition(
            EnrichedRegistroRegulacaoAssistencial dto, String uuidAppointment, String uuidServiceRequest) {
        Composition c = new Composition();
        c.setMeta(new Meta().addProfile(PERFIL_COMPOSITION));
        c.setStatus(Composition.CompositionStatus.FINAL);
        c.setType(conceito(TIPO_DOCUMENTO));
        c.addCategory(conceito(dto.getModalidadeAssistencial()));
        c.setSubject(referenciaPorIdentificador(SYSTEM_INDIVIDUO, dto.identificadorIndividuo()));
        c.setDate(Date.from(dto.getDataRegistroOrigem()));
        c.addAuthor(referenciaPorIdentificador(SYSTEM_ESTABELECIMENTO, dto.getCnesSolicitante()));
        c.setTitle(TITULO);

        // event é 1..* — o núcleo do documento. Cada estado aponta para o estabelecimento
        // responsável e, conforme o caso, para a requisição ou o agendamento.
        for (EventoRegulacao ev : dto.getEventos()) {
            Composition.CompositionEventComponent evento = c.addEvent();
            evento.addCode(conceito(ev.getEstado().toCodedValue()));
            Date quando = Date.from(ev.getQuando());
            evento.setPeriod(new Period().setStart(quando).setEnd(quando));
            // A estrutura de detail VARIA POR FATIA. Todas exigem o estabelecimento; algumas
            // admitem tambem o resource pertinente ao estado; e returned-to-requester aceita
            // UM UNICO detail, por identifier -- devolver ao solicitante e ato do
            // estabelecimento, nao do agendamento.
            evento.addDetail(
                    referenciaPorIdentificador(SYSTEM_ESTABELECIMENTO, cnesResponsavelPor(dto, ev.getEstado())));
            if (admiteResourceNoDetail(ev.getEstado())) {
                evento.addDetail(detalheDoEstado(ev.getEstado(), uuidAppointment, uuidServiceRequest));
            }
        }

        c.addSection().addEntry(new Reference("urn:uuid:" + uuidAppointment));
        return c;
    }

    /**
     * A qual resource o estado se refere.
     *
     * <p>Estados anteriores ao agendamento apontam para a requisição; do agendamento em
     * diante, para o próprio agendamento. É a leitura direta da máquina de estados: antes de
     * {@code booked} não há agendamento a que se referir.
     */
    private static Reference detalheDoEstado(
            EstadoRegulacao estado, String uuidAppointment, String uuidServiceRequest) {
        return switch (estado) {
            case PENDENTE -> new Reference("urn:uuid:" + uuidServiceRequest);
            case AGENDADA, ATENDIDA, FALTA -> new Reference("urn:uuid:" + uuidAppointment);
            default -> throw new IllegalStateException("estado sem resource no detail: " + estado);
        };
    }

    /**
     * Quais estados admitem, além do estabelecimento, o resource a que se referem.
     *
     * <p>{@code returned-to-requester} tem {@code detail} {@code 1..1} e
     * {@code detail.reference} com {@code max=0}: aceita um único detalhe, e por
     * {@code identifier}. {@code cancelled} e {@code excluded} referenciam apenas o
     * estabelecimento.
     */
    private static boolean admiteResourceNoDetail(EstadoRegulacao estado) {
        return switch (estado) {
            case PENDENTE, AGENDADA, ATENDIDA, FALTA -> true;
            case DEVOLVIDA, CANCELADA, EXCLUIDA -> false;
        };
    }

    /** Antes do agendamento o responsável é o solicitante; depois, o executante. */
    private static String cnesResponsavelPor(EnrichedRegistroRegulacaoAssistencial dto, EstadoRegulacao estado) {
        return switch (estado) {
            case PENDENTE, DEVOLVIDA, EXCLUIDA -> dto.getCnesSolicitante();
            case AGENDADA, ATENDIDA, CANCELADA, FALTA -> dto.getCnesExecutante() != null
                    ? dto.getCnesExecutante()
                    : dto.getCnesSolicitante();
        };
    }

    // ══ Appointment ═════════════════════════════════════════════════════

    private Appointment montarAppointment(
            EnrichedRegistroRegulacaoAssistencial dto, String uuidServiceRequest, String uuidCondition) {
        Appointment a = new Appointment();
        a.setMeta(new Meta().addProfile(PERFIL_APPOINTMENT));
        // Appointment.identifier e ServiceRequest.identifier sao max=0: o perfil recusa
        // identificador local nos resources internos. O Bundle.identifier ja o carrega, e
        // repeti-lo seria redundancia que o perfil trata como erro.
        a.setStatus(Appointment.AppointmentStatus.PROPOSED);
        a.setAppointmentType(conceito(dto.getCaraterAtendimento()));
        a.addServiceCategory(conceito(dto.getModalidadeAssistencial()));
        a.addServiceType(conceito(dto.getProcedimento()));
        if (dto.getCboExecutante() != null) {
            a.addSpecialty(conceito(dto.getCboExecutante()));
        }
        a.addReasonReference(new Reference("urn:uuid:" + uuidCondition));
        // Invariante app-2 do FHIR base: "Either start and end are specified, or neither".
        // Nao e regra do perfil da RNDS, e sim do resource Appointment -- e o Builder a
        // impoe estruturalmente, porque emitir so um dos dois reprova na Camada 4.
        if (dto.getInicioAgendamento() != null && dto.getFimAgendamento() != null) {
            a.setStart(Date.from(dto.getInicioAgendamento()));
            a.setEnd(Date.from(dto.getFimAgendamento()));
        }
        a.addBasedOn(new Reference("urn:uuid:" + uuidServiceRequest));

        // participant é 1..*: o paciente, tipado como PCT.
        Appointment.AppointmentParticipantComponent p = a.addParticipant();
        p.addType(conceito(TIPO_PARTICIPANTE_PACIENTE));
        p.setActor(referenciaPorIdentificador(SYSTEM_INDIVIDUO, dto.identificadorIndividuo()));
        p.setStatus(Appointment.ParticipationStatus.ACCEPTED);
        return a;
    }

    // ══ ServiceRequest ══════════════════════════════════════════════════

    private ServiceRequest montarServiceRequest(EnrichedRegistroRegulacaoAssistencial dto, String uuidCondition) {
        ServiceRequest s = new ServiceRequest();
        s.setMeta(new Meta().addProfile(PERFIL_SERVICE_REQUEST));
        s.setStatus(ServiceRequest.ServiceRequestStatus.ACTIVE);
        s.setIntent(ServiceRequest.ServiceRequestIntent.ORDER);
        // priority e 1..1, com binding required a request-priority do HL7.
        s.setPriority(prioridadeDe(dto.getCaraterAtendimento()));
        s.addCategory(conceito(dto.getModalidadeAssistencial()));
        s.setCode(conceito(dto.getProcedimento()));
        s.setSubject(referenciaPorIdentificador(SYSTEM_INDIVIDUO, dto.identificadorIndividuo()));
        if (dto.getDataRequisicao() != null) {
            s.setAuthoredOn(Date.from(dto.getDataRequisicao()));
        }
        s.setRequester(referenciaPorIdentificador(SYSTEM_ESTABELECIMENTO, dto.getCnesSolicitante()));
        if (dto.getCboExecutante() != null) {
            s.setPerformerType(conceito(dto.getCboExecutante()));
        }
        s.addReasonReference(new Reference("urn:uuid:" + uuidCondition));
        return s;
    }

    // ══ Condition ═══════════════════════════════════════════════════════

    private Condition montarCondition(EnrichedRegistroRegulacaoAssistencial dto) {
        Condition c = new Condition();
        c.setMeta(new Meta().addProfile(PERFIL_CONDITION));
        c.setClinicalStatus(conceito(CONDITION_ATIVA));
        c.addCategory(conceito(CATEGORIA_DIAGNOSTICO));
        c.setCode(conceito(dto.getCid10()));
        c.setSubject(referenciaPorIdentificador(SYSTEM_INDIVIDUO, dto.identificadorIndividuo()));
        if (dto.getObservacaoCondicao() != null && !dto.getObservacaoCondicao().isBlank()) {
            c.addNote(new org.hl7.fhir.r4.model.Annotation().setText(dto.getObservacaoCondicao()));
        }
        return c;
    }

    // ══ Auxiliares ══════════════════════════════════════════════════════

    private static void adicionar(Bundle bundle, String uuid, org.hl7.fhir.r4.model.Resource resource) {
        bundle.addEntry().setFullUrl("urn:uuid:" + uuid).setResource(resource);
    }

    private static CodeableConcept conceito(CodedValue v) {
        CodeableConcept cc = new CodeableConcept();
        cc.addCoding().setSystem(v.system()).setCode(v.code());
        return cc;
    }

    /**
     * Converte o caráter do atendimento no enum de prioridade do HAPI.
     *
     * <p>O binding aceita apenas {@code routine} e {@code urgent}; qualquer outro valor
     * reprova na Camada 4.
     */
    private static ServiceRequest.ServiceRequestPriority prioridadeDe(CodedValue carater) {
        return "urgent".equals(carater.code())
                ? ServiceRequest.ServiceRequestPriority.URGENT
                : ServiceRequest.ServiceRequestPriority.ROUTINE;
    }

    private static Reference referenciaPorIdentificador(String system, String value) {
        return new Reference().setIdentifier(new Identifier().setSystem(system).setValue(value));
    }

    private static void exigirObrigatorios(EnrichedRegistroRegulacaoAssistencial dto) {
        Objects.requireNonNull(dto, "dto não pode ser nulo");
        exigir(dto.getIdLocalRegistro(), "idLocalRegistro (Bundle.identifier.value)");
        exigir(dto.getCnesSolicitante(), "cnesSolicitante (Composition.author)");
        exigir(dto.getDataRegistroOrigem(), "dataRegistroOrigem (Composition.date)");
        exigir(dto.getModalidadeAssistencial(), "modalidadeAssistencial (Composition.category 1..1)");
        exigir(dto.getProcedimento(), "procedimento (ServiceRequest.code 1..1)");
        exigir(
                dto.getCaraterAtendimento(),
                "caraterAtendimento (Appointment.appointmentType 1..1 e ServiceRequest.priority 1..1)");
        if ((dto.getInicioAgendamento() == null) != (dto.getFimAgendamento() == null)) {
            throw new IllegalArgumentException("Campo obrigatório ausente para o perfil do RIRA: inicioAgendamento e "
                    + "fimAgendamento precisam vir juntos ou ausentes — invariante app-2 do "
                    + "resource Appointment do FHIR base, não do perfil da RNDS.");
        }
        exigir(
                dto.getCid10(),
                "cid10 (reasonReference e 1..1 no Appointment E no ServiceRequest — o motivo "
                        + "da solicitacao nao e opcional na regulacao assistencial)");
        dto.identificadorIndividuo();

        if (dto.getEventos() == null || dto.getEventos().isEmpty()) {
            throw new IllegalArgumentException("Campo obrigatório ausente para o perfil do RIRA: pelo menos um evento "
                    + "(Composition.event é 1..*). O documento registra o histórico de estados "
                    + "da solicitação, e um documento sem estado algum não descreve nada.");
        }
        for (int i = 0; i < dto.getEventos().size(); i++) {
            EventoRegulacao e = dto.getEventos().get(i);
            exigir(e.getEstado(), "eventos[" + i + "].estado");
            exigir(e.getQuando(), "eventos[" + i + "].quando (event.period 1..1)");
        }
    }

    private static void exigir(Object valor, String campo) {
        if (valor == null || (valor instanceof String s && s.isBlank())) {
            throw new IllegalArgumentException("Campo obrigatório ausente para o perfil do RIRA: " + campo);
        }
    }
}
