package br.rndsconnect.builder;

import br.rndsconnect.model.CodedValue;
import br.rndsconnect.model.EnrichedRegistroSumarioAlta;
import br.rndsconnect.model.EnrichedRegistroSumarioAlta.Alergia;
import br.rndsconnect.model.EnrichedRegistroSumarioAlta.ContatoAssistencial;
import br.rndsconnect.model.EnrichedRegistroSumarioAlta.Diagnostico;
import br.rndsconnect.model.EnrichedRegistroSumarioAlta.ItemPrescricao;
import br.rndsconnect.model.EnrichedRegistroSumarioAlta.Procedimento;
import br.rndsconnect.model.VocabularioClinicoFhir.SituacaoClinica;
import br.rndsconnect.model.VocabularioClinicoFhir.VerificacaoAlergia;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.Annotation;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CarePlan;
import org.hl7.fhir.r4.model.ClinicalImpression;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Dosage;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.PositiveIntType;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Timing;

/**
 * Camada 3 — construtor do Bundle do Sumário de Alta (SA).
 *
 * <p>Até quinze entradas, dois {@code Composition} e dez seções.
 *
 * <pre>
 * Composition (SA) ── section ──► Encounter ─ diagnosis ─► Condition(s), Procedure(s)
 *                                 ├─► Condition(s)            (diagnósticos)
 *                                 ├─► Procedure(s)            (procedimentos)
 *                                 ├─► Condition               (restrição funcional, opcional)
 *                                 ├─► AllergyIntolerance
 *                                 ├─► Composition (RPM) ─ section ─► MedicationRequest(s), Medication(s)
 *                                 ├─► ClinicalImpression, CarePlan, Observation
 * </pre>
 *
 * <h2>A prescrição de alta é um documento dentro do documento</h2>
 *
 * <p>A fatia {@code prescricaoAlta} exige um {@code Composition} do tipo {@code RPM} — o
 * Registro de Prescrição de Medicamento, serviço que o Portal não lista mais, unificado no
 * REDFM, e que sobrevive apenas embutido aqui. O exemplo oficial descartou esse envelope e pôs
 * as linhas de medicamento direto como seções do SA (defeito nº 15); este Builder o monta.
 *
 * <h2>As seções são discriminadas pelo recurso, não pelo código</h2>
 *
 * <p>O CMD, do qual o SA deriva, fatia {@code Composition.section} por
 * {@code entry.resolve()} — pelo perfil do recurso referenciado. Por isso as seções não têm
 * {@code code} nem {@code title}, e por isso <strong>cada recurso ganha sua própria
 * seção</strong>: é o recurso que diz a que fatia a seção pertence.
 *
 * @author Igor Mendes Domingues Miras
 */
public class SaBundleBuilder implements FhirBundleBuilder<EnrichedRegistroSumarioAlta> {

    private static final String SD = "http://www.saude.gov.br/fhir/r4/StructureDefinition/";
    private static final String CS = "http://www.saude.gov.br/fhir/r4/CodeSystem/";
    private static final String NS = "http://www.saude.gov.br/fhir/r4/NamingSystem/";
    private static final String HL7 = "http://terminology.hl7.org/CodeSystem/";

    private static final String SYSTEM_INDIVIDUO = SD + "BRIndividuo-1.0";
    private static final String SYSTEM_ESTABELECIMENTO = SD + "BREstabelecimentoSaude-1.0";
    private static final String SYSTEM_PROFISSIONAL = SD + "BRProfissional-1.0";
    private static final String SYSTEM_LOTACAO = SD + "BRLotacaoProfissional-1.0";

    /**
     * {@code https://loinc.org/}, com a barra final: é a forma que o {@code BRTipoObservacao-1.0}
     * exige, diferente da URI canônica {@code http://loinc.org}. Um {@code system} "correto"
     * pelo padrão internacional reprova aqui.
     */
    private static final CodedValue CODIGO_OBSERVACAO = new CodedValue("https://loinc.org/", "48767-8");

    private final String solicitanteId;
    private final Supplier<String> geradorUuid;
    private final Supplier<Instant> relogio;

    /** @param solicitanteId Identificador Solicitante fornecido pela RNDS no credenciamento */
    public SaBundleBuilder(String solicitanteId) {
        this(solicitanteId, () -> UUID.randomUUID().toString(), Instant::now);
    }

    /**
     * @param solicitanteId Identificador Solicitante
     * @param geradorUuid fonte dos UUIDs transientes — injetável para testes determinísticos
     * @param relogio instante de montagem do Bundle — idem
     */
    public SaBundleBuilder(String solicitanteId, Supplier<String> geradorUuid, Supplier<Instant> relogio) {
        this.solicitanteId = Objects.requireNonNull(solicitanteId, "solicitanteId é obrigatório");
        this.geradorUuid = Objects.requireNonNull(geradorUuid, "geradorUuid é obrigatório");
        this.relogio = Objects.requireNonNull(relogio, "relogio é obrigatório");
    }

    @Override
    public Bundle build(EnrichedRegistroSumarioAlta dto) {
        exigirObrigatorios(dto);
        Instant montagem = relogio.get();

        Bundle bundle = new Bundle();
        bundle.setMeta(new Meta().setLastUpdated(Date.from(montagem)));
        bundle.setIdentifier(
                new Identifier().setSystem(NS + "BRRNDS-" + solicitanteId).setValue(dto.getIdLocalRegistro()));
        bundle.setType(Bundle.BundleType.DOCUMENT);
        bundle.setTimestamp(Date.from(montagem));

        // Reserva a primeira entrada para o Composition, que só pode ser montado depois que
        // todas as seções tiverem UUID. O documento FHIR exige o Composition em primeiro.
        Bundle.BundleEntryComponent entradaSa = bundle.addEntry();
        String uuidSa = geradorUuid.get();
        List<String> secoes = new ArrayList<>();

        // ── Contato assistencial, e o que ele referencia ────────────────
        String uuidEncounter = geradorUuid.get();
        Bundle.BundleEntryComponent entradaEncounter = bundle.addEntry();
        secoes.add(uuidEncounter);

        String uuidLocal = null;
        if (texto(dto.getContato().getLocalAtendimento())) {
            uuidLocal = adicionar(bundle, local(dto.getContato().getLocalAtendimento()));
        }
        String uuidMotivo = null;
        if (texto(dto.getContato().getMotivoAtendimento())) {
            uuidMotivo = adicionar(bundle, observacao(dto, dto.getContato().getMotivoAtendimento()));
        }

        List<String> uuidsDiagnosticos = new ArrayList<>();
        for (Diagnostico d : lista(dto.getDiagnosticos())) {
            String u = adicionar(bundle, diagnostico(dto, d));
            uuidsDiagnosticos.add(u);
            secoes.add(u);
        }
        List<String> uuidsProcedimentos = new ArrayList<>();
        for (Procedimento p : dto.getProcedimentos()) {
            String u = adicionar(bundle, procedimento(dto, p));
            uuidsProcedimentos.add(u);
            secoes.add(u);
        }
        preencher(
                entradaEncounter,
                uuidEncounter,
                encounter(dto, uuidsDiagnosticos, uuidsProcedimentos, uuidLocal, uuidMotivo));

        if (texto(dto.getRestricaoFuncional())) {
            secoes.add(adicionar(bundle, restricaoFuncional(dto)));
        }
        if (dto.getAlergia() != null) {
            secoes.add(adicionar(bundle, alergia(dto)));
        }

        // ── A prescrição de alta: envelope RPM + linhas ─────────────────
        if (dto.getPrescricaoAlta() != null) {
            Bundle.BundleEntryComponent entradaRpm = bundle.addEntry();
            String uuidRpm = geradorUuid.get();
            secoes.add(uuidRpm);
            List<String> linhas = new ArrayList<>();
            for (ItemPrescricao item : dto.getPrescricaoAlta().getItens()) {
                String uuidMedicamento = geradorUuid.get();
                String uuidPedido = adicionar(bundle, pedido(dto, item, uuidMedicamento));
                preencher(bundle.addEntry(), uuidMedicamento, medicamento(item));
                linhas.add(uuidPedido);
                linhas.add(uuidMedicamento);
            }
            preencher(entradaRpm, uuidRpm, prescricao(dto, linhas));
        }

        // Só o resumo de evolução clínica é 1..1 no perfil. Os outros três são opcionais, e
        // exigi-los era recusar documento que a RNDS aceita.
        secoes.add(adicionar(bundle, resumo(dto)));
        if (texto(dto.getPlanoCuidados())) {
            secoes.add(adicionar(bundle, planoCuidados(dto)));
        }
        if (texto(dto.getInformacoesAdicionais())) {
            secoes.add(adicionar(bundle, observacao(dto, dto.getInformacoesAdicionais())));
        }

        preencher(entradaSa, uuidSa, sumario(dto, secoes));
        return bundle;
    }

    // ══ Os dois Composition ═════════════════════════════════════════════

    private Composition sumario(EnrichedRegistroSumarioAlta dto, List<String> secoes) {
        Composition c = documento(dto, "BRSumarioAlta", "SA", "Sumário de Alta");
        c.addCategory(conceito(dto.getModalidadeAssistencial()));
        // Uma seção por recurso, sem code nem title: o discriminador e entry.resolve().
        secoes.forEach(u -> c.addSection().addEntry(ref(u)));
        return c;
    }

    private Composition prescricao(EnrichedRegistroSumarioAlta dto, List<String> linhas) {
        Composition c = documento(dto, "BRRegistroPrescricaoMedicamento", "RPM", "Prescrição de Alta");
        Composition.SectionComponent s = c.addSection();
        linhas.forEach(u -> s.addEntry(ref(u)));
        return c;
    }

    private Composition documento(EnrichedRegistroSumarioAlta dto, String perfil, String tipo, String titulo) {
        Composition c = new Composition();
        c.setMeta(new Meta().addProfile(SD + perfil));
        c.setStatus(Composition.CompositionStatus.FINAL);
        c.setType(conceito(new CodedValue(CS + "BRTipoDocumento", tipo)));
        c.setSubject(individuoQualificado(dto));
        c.setDate(Date.from(dto.getDataRegistroOrigem()));
        c.addAuthor(porIdentificador(SYSTEM_ESTABELECIMENTO, dto.getCnesEstabelecimento()));
        c.setTitle(titulo);
        return c;
    }

    // ══ Contato assistencial ════════════════════════════════════════════

    private Encounter encounter(
            EnrichedRegistroSumarioAlta dto,
            List<String> diagnosticos,
            List<String> procedimentos,
            String uuidLocal,
            String uuidMotivo) {
        ContatoAssistencial ca = dto.getContato();
        Encounter e = new Encounter();
        e.setMeta(new Meta().addProfile(SD + "BRContatoAssistencial-1.0"));
        e.setStatus(Encounter.EncounterStatus.FINISHED);
        CodedValue modalidade = dto.getModalidadeAssistencial();
        e.setClass_(new Coding().setSystem(modalidade.system()).setCode(modalidade.code()));
        e.setPriority(conceito(ca.getCaraterAtendimento()));
        e.setSubject(individuoQualificado(dto));
        e.getPeriod().setStart(Date.from(ca.getDataInicio()));
        if (ca.getDataFim() != null) {
            e.getPeriod().setEnd(Date.from(ca.getDataFim()));
        }

        // diagnosis e fatiado por condition.resolve(): Condition ou Procedure.
        List<Diagnostico> ds = lista(dto.getDiagnosticos());
        for (int i = 0; i < ds.size(); i++) {
            Encounter.DiagnosisComponent dc = e.addDiagnosis().setCondition(ref(diagnosticos.get(i)));
            if (Boolean.TRUE.equals(ds.get(i).getPresenteNaAdmissao())) {
                dc.setUse(new CodeableConcept()
                        .addCoding(new Coding()
                                .setSystem(HL7 + "diagnosis-role")
                                .setCode("AD")
                                .setDisplay("Diagnóstico presente na admissão")));
            }
            dc.setRank(ds.get(i).getOrdem() != null ? ds.get(i).getOrdem() : i + 1);
        }
        // Na fatia de procedimento, a extensao de financiamento e 1..1.
        List<Procedimento> ps = dto.getProcedimentos();
        for (int i = 0; i < ps.size(); i++) {
            Reference r = ref(procedimentos.get(i));
            r.addExtension(
                    new Extension(SD + "BRFinanciamento-1.0", conceito(ps.get(i).getFinanciamento())));
            e.addDiagnosis().setCondition(r);
        }

        Encounter.EncounterParticipantComponent part = e.addParticipant();
        part.addType(conceito(new CodedValue(CS + "BRResponsabilidadeParticipante", "alta")));
        part.setIndividual(
                texto(ca.getCnesLotacaoProfissionalAlta())
                        ? porIdentificador(
                                SYSTEM_LOTACAO, ca.getCnsProfissionalAlta() + "-" + ca.getCnesLotacaoProfissionalAlta())
                        : porIdentificador(SYSTEM_PROFISSIONAL, ca.getCnsProfissionalAlta()));
        if (ca.getEquipe() != null) {
            part.addExtension(new Extension(
                    SD + "BRIdentificacaoEquipe-1.0", new org.hl7.fhir.r4.model.IntegerType(ca.getEquipe())));
        }
        if (ca.getCboProfissionalAlta() != null) {
            part.addExtension(new Extension(SD + "BROcupacao-1.0", conceito(ca.getCboProfissionalAlta())));
        }

        // hospitalization, admitSource e dischargeDisposition sao 1..1 no perfil.
        e.getHospitalization().setAdmitSource(conceito(ca.getProcedencia()));
        e.getHospitalization().setDischargeDisposition(conceito(ca.getMotivoDesfecho()));
        if (uuidLocal != null) {
            e.addLocation().setLocation(ref(uuidLocal));
        }
        if (uuidMotivo != null) {
            e.addReasonReference(ref(uuidMotivo));
        }
        e.setServiceProvider(porIdentificador(SYSTEM_ESTABELECIMENTO, dto.getCnesEstabelecimento()));
        return e;
    }

    private Location local(String nome) {
        Location l = new Location();
        l.setMeta(new Meta().addProfile(SD + "BRLocalAtendimento-1.0"));
        l.setStatus(Location.LocationStatus.ACTIVE);
        l.setName(nome);
        // O perfil fixa mode = kind: o local e um TIPO de lugar, nao uma instancia (um leito).
        l.setMode(Location.LocationMode.KIND);
        return l;
    }

    // ══ Condições e procedimentos ═══════════════════════════════════════

    private Condition diagnostico(EnrichedRegistroSumarioAlta dto, Diagnostico d) {
        Condition c = new Condition();
        c.setMeta(new Meta().addProfile(SD + "BRProblemaDiagnostico"));
        c.setClinicalStatus(situacao(d.getSituacaoClinica()));
        c.addCategory(new CodeableConcept()
                .addCoding(new Coding()
                        .setSystem(HL7 + "condition-category")
                        .setCode("encounter-diagnosis")
                        .setDisplay("Diagnóstico")));
        // display e max=0 no code: so system e code.
        c.setCode(conceito(d.getCid10()));
        c.setSubject(individuoQualificado(dto));
        nota(c.getNote(), d.getObservacao());
        return c;
    }

    private Condition restricaoFuncional(EnrichedRegistroSumarioAlta dto) {
        Condition c = new Condition();
        c.setMeta(new Meta().addProfile(SD + "BRRestricaoFuncionalIncapacidadeSaude-1.0"));
        c.setClinicalStatus(situacao(SituacaoClinica.ATIVA));
        c.setSubject(individuoQualificado(dto));
        c.setCode(new CodeableConcept().setText(dto.getRestricaoFuncional()));
        return c;
    }

    private Procedure procedimento(EnrichedRegistroSumarioAlta dto, Procedimento p) {
        Procedure pr = new Procedure();
        pr.setMeta(new Meta().addProfile(SD + "BRProcedimentoRealizado-1.0"));
        if (texto(p.getNumeroAutorizacao())) {
            pr.addIdentifier(new Identifier()
                    .setType(conceito(new CodedValue(CS + "BRTipoIdentificador", "AUTH")))
                    .setValue(p.getNumeroAutorizacao()));
        }
        pr.setStatus(Procedure.ProcedureStatus.COMPLETED);
        pr.setCode(conceito(p.getProcedimento()));
        pr.setSubject(individuoQualificado(dto));
        pr.setPerformed(new DateTimeType(Date.from(p.getDataRealizacao())));
        Procedure.ProcedurePerformerComponent exec = pr.addPerformer();
        if (p.getCboExecutante() != null) {
            exec.setFunction(conceito(p.getCboExecutante()));
        }
        exec.setActor(porIdentificador(
                SYSTEM_ESTABELECIMENTO,
                texto(p.getCnesExecutante()) ? p.getCnesExecutante() : dto.getCnesEstabelecimento()));
        if (texto(p.getCnesEmNomeDe())) {
            exec.setOnBehalfOf(porIdentificador(SYSTEM_ESTABELECIMENTO, p.getCnesEmNomeDe()));
        }
        if (p.getEquipe() != null) {
            exec.addExtension(new Extension(
                    SD + "BRIdentificacaoEquipe-1.0", new org.hl7.fhir.r4.model.IntegerType(p.getEquipe())));
        }
        // extension:quantity e 1..1 no perfil. Sem ela o Procedure nao conforma, e a falha desce
        // em cascata: a fatia diagnosis:procedure do Encounter nao casa, o Encounter nao conforma,
        // e as duas secoes obrigatorias do CMD deixam de ser reconhecidas.
        pr.addExtension(new Extension(SD + "BRQuantidade-1.0", new PositiveIntType(p.getQuantidade())));
        nota(pr.getNote(), p.getObservacao());
        return pr;
    }

    // ══ Alergia ═════════════════════════════════════════════════════════

    private AllergyIntolerance alergia(EnrichedRegistroSumarioAlta dto) {
        Alergia a = dto.getAlergia();
        AllergyIntolerance ai = new AllergyIntolerance();
        ai.setMeta(new Meta().addProfile(SD + "BRAlergiaReacaoAdversa-1.0"));
        ai.setPatient(individuo(dto));
        if (a.getCategoria() != null) {
            ai.addCategory(
                    switch (a.getCategoria()) {
                        case ALIMENTO -> AllergyIntolerance.AllergyIntoleranceCategory.FOOD;
                        case MEDICAMENTO -> AllergyIntolerance.AllergyIntoleranceCategory.MEDICATION;
                        case AMBIENTE -> AllergyIntolerance.AllergyIntoleranceCategory.ENVIRONMENT;
                        case BIOLOGICO -> AllergyIntolerance.AllergyIntoleranceCategory.BIOLOGIC;
                    });
        }
        if (a.getCriticidade() != null) {
            ai.setCriticality(
                    switch (a.getCriticidade()) {
                        case BAIXA -> AllergyIntolerance.AllergyIntoleranceCriticality.LOW;
                        case ALTA -> AllergyIntolerance.AllergyIntoleranceCriticality.HIGH;
                        case INDETERMINAVEL -> AllergyIntolerance.AllergyIntoleranceCriticality.UNABLETOASSESS;
                    });
        }
        if (a.getTipo() != null) {
            ai.setType(
                    switch (a.getTipo()) {
                        case ALERGIA -> AllergyIntolerance.AllergyIntoleranceType.ALLERGY;
                        case INTOLERANCIA -> AllergyIntolerance.AllergyIntoleranceType.INTOLERANCE;
                    });
        }
        CodeableConcept agente = conceito(a.getAgente());
        if (texto(a.getDescricaoAgente())) {
            agente.setText(a.getDescricaoAgente());
        }
        ai.setCode(agente);
        if (a.getDataInicio() != null) {
            ai.setOnset(new DateTimeType(a.getDataInicio().toString()));
        }
        if (a.getManifestacao() != null) {
            ai.addReaction().addManifestation(conceito(a.getManifestacao()));
        }
        nota(ai.getNote(), a.getObservacao());
        VerificacaoAlergia v = a.getVerificacao() != null ? a.getVerificacao() : VerificacaoAlergia.CONFIRMADA;
        ai.setVerificationStatus(conceito(new CodedValue(VerificacaoAlergia.system(), v.codigo())));
        // Invariante ait-1 do FHIR base: clinicalStatus e obrigatorio salvo registro por engano.
        if (v != VerificacaoAlergia.REGISTRADA_POR_ENGANO) {
            ai.setClinicalStatus(conceito(new CodedValue(HL7 + "allergyintolerance-clinical", "active")));
        }
        return ai;
    }

    // ══ Prescrição de alta ══════════════════════════════════════════════

    private MedicationRequest pedido(EnrichedRegistroSumarioAlta dto, ItemPrescricao item, String uuidMedicamento) {
        MedicationRequest mr = new MedicationRequest();
        mr.setMeta(new Meta().addProfile(SD + "BRPrescricaoMedicamento"));
        mr.setStatus(
                item.getSituacao() == null
                        ? MedicationRequest.MedicationRequestStatus.ACTIVE
                        : switch (item.getSituacao()) {
                            case ATIVA -> MedicationRequest.MedicationRequestStatus.ACTIVE;
                            case SUSPENSA -> MedicationRequest.MedicationRequestStatus.ONHOLD;
                            case CANCELADA -> MedicationRequest.MedicationRequestStatus.CANCELLED;
                            case CONCLUIDA -> MedicationRequest.MedicationRequestStatus.COMPLETED;
                            case INTERROMPIDA -> MedicationRequest.MedicationRequestStatus.STOPPED;
                        });
        mr.setIntent(MedicationRequest.MedicationRequestIntent.ORDER);
        mr.setSubject(individuo(dto));
        mr.setMedication(ref(uuidMedicamento));
        nota(mr.getNote(), item.getObservacao());

        Dosage d = mr.addDosageInstruction();
        if (texto(item.getInstrucaoPaciente())) {
            d.setPatientInstruction(item.getInstrucaoPaciente());
        }
        Timing.TimingRepeatComponent rep = d.getTiming().getRepeat();
        if (item.getVezes() != null) {
            rep.setCount(item.getVezes());
        }
        if (item.getFrequencia() != null) {
            rep.setFrequency(item.getFrequencia());
        }
        // countMax e 1..1 no perfil — o exemplo oficial pos 'true', booleano onde se espera inteiro.
        rep.setCountMax(item.getVezesMaximo());
        if (item.getTurno() != null) {
            rep.addExtension(
                    new Extension(SD + "BRTurno", new CodeType(item.getTurno().code())));
        }
        if (item.getPeriodo() != null && item.getUnidadePeriodo() != null) {
            Extension intervalo = new Extension(SD + "BRIntervaloDoses");
            intervalo.addExtension(new Extension(
                    "periodUnit", new CodeType(item.getUnidadePeriodo().codigo())));
            intervalo.addExtension(new Extension("period", new PositiveIntType(item.getPeriodo())));
            rep.addExtension(intervalo);
        }
        if (item.getViaAdministracao() != null) {
            d.setRoute(conceito(item.getViaAdministracao()));
        }
        if (item.getDose() != null) {
            Dosage.DosageDoseAndRateComponent dr = d.addDoseAndRate();
            if (item.getUnidadeDose() != null) {
                dr.setType(conceito(item.getUnidadeDose()));
            }
            dr.setDose(new Quantity().setValue(item.getDose()));
        }
        d.setMaxDosePerAdministration(new Quantity().setValue(item.getDoseMaximaPorAdministracao()));
        if (item.getSeNecessario() != null) {
            // asNeededBoolean — o exemplo oficial grafou asNeedBoolean, que o validador recusa.
            d.setAsNeeded(new BooleanType(item.getSeNecessario()));
        }
        mr.setAuthoredOn(Date.from(dto.getPrescricaoAlta().getDataPrescricao()));
        mr.setRecorder(
                porIdentificador(SYSTEM_PROFISSIONAL, dto.getPrescricaoAlta().getCnsPrescritor()));
        return mr;
    }

    private Medication medicamento(ItemPrescricao item) {
        Medication m = new Medication();
        m.setMeta(new Meta().addProfile(SD + "BRMedicamento"));
        m.setCode(conceito(item.getMedicamento()));
        if (item.getFormaFarmaceutica() != null) {
            m.setForm(conceito(item.getFormaFarmaceutica()));
        }
        return m;
    }

    // ══ Resumo, plano e informações adicionais ══════════════════════════

    private ClinicalImpression resumo(EnrichedRegistroSumarioAlta dto) {
        ClinicalImpression ci = new ClinicalImpression();
        ci.setMeta(new Meta().addProfile(SD + "BRResumoEvolucaoClinica"));
        ci.setStatus(ClinicalImpression.ClinicalImpressionStatus.COMPLETED);
        ci.setSubject(individuo(dto));
        ci.setSummary(dto.getResumoEvolucaoClinica());
        return ci;
    }

    private CarePlan planoCuidados(EnrichedRegistroSumarioAlta dto) {
        CarePlan cp = new CarePlan();
        cp.setMeta(new Meta().addProfile(SD + "BRPlanoCuidados-1.0"));
        cp.setStatus(CarePlan.CarePlanStatus.ACTIVE);
        cp.setIntent(CarePlan.CarePlanIntent.PLAN);
        cp.setSubject(individuo(dto));
        cp.setDescription(dto.getPlanoCuidados());
        return cp;
    }

    private Observation observacao(EnrichedRegistroSumarioAlta dto, String texto) {
        Observation o = new Observation();
        o.setMeta(new Meta().addProfile(SD + "BRObservacaoDescritiva-1.0"));
        o.setStatus(Observation.ObservationStatus.FINAL);
        o.setCode(conceito(CODIGO_OBSERVACAO));
        o.setSubject(individuo(dto));
        o.setValue(new StringType(texto));
        o.setIssued(Date.from(dto.getDataRegistroOrigem()));
        return o;
    }

    // ══ Auxiliares ══════════════════════════════════════════════════════

    private String adicionar(Bundle bundle, Resource r) {
        String uuid = geradorUuid.get();
        preencher(bundle.addEntry(), uuid, r);
        return uuid;
    }

    private static void preencher(Bundle.BundleEntryComponent e, String uuid, Resource r) {
        e.setFullUrl("urn:uuid:" + uuid).setResource(r);
    }

    private static Reference ref(String uuid) {
        return new Reference("urn:uuid:" + uuid);
    }

    private static Reference individuo(EnrichedRegistroSumarioAlta dto) {
        if (dto.getIndividuoNaoIdentificado() != null) {
            // Sem CNS nem CPF não há identifier: o manual (5.5.2.4.2) trata as duas formas
            // como excludentes. Medido em 10/10: subject.display é max=0 em TODOS os perfis do
            // SA, então não há terceira forma de preencher a referência. Nos contextos em que a
            // extensão é permitida, ela sozinha basta; nos demais, o documento é irrepresentável
            // — ver o defeito nº 20 e a exclusão declarada no CoberturaMustSupportTest.
            return new Reference();
        }
        return porIdentificador(
                SYSTEM_INDIVIDUO, texto(dto.getCnsPaciente()) ? dto.getCnsPaciente() : dto.getCpfPaciente());
    }

    /**
     * A referência ao indivíduo nos contextos em que a extensão de não identificado é
     * permitida: {@code Composition.subject}, {@code Encounter.subject},
     * {@code Condition.subject}, {@code Procedure.subject} e {@code Immunization.patient}.
     *
     * <p>O modelo de informação do manual mapeia o mesmo dado para oito caminhos, incluindo
     * {@code AllergyIntolerance.patient} e {@code CarePlan.subject}. O contexto declarado na
     * extensão lista cinco, e é o contexto que a Camada 4 valida.
     */
    private static Reference individuoQualificado(EnrichedRegistroSumarioAlta dto) {
        Reference r = individuo(dto);
        EnrichedRegistroSumarioAlta.IndividuoNaoIdentificado ni = dto.getIndividuoNaoIdentificado();
        if (ni == null) {
            return r;
        }
        Extension e = new Extension(SD + "BRIndividuoNaoIdentificado-1.0");
        e.addExtension(new Extension("gender", new CodeType(ni.getSexo().codigo())));
        e.addExtension(new Extension("birthYear", new DateType(String.valueOf(ni.getAnoNascimento()))));
        e.addExtension(new Extension("reason", conceito(ni.getJustificativa())));
        r.addExtension(e);
        return r;
    }

    private static Reference porIdentificador(String system, String value) {
        return new Reference().setIdentifier(new Identifier().setSystem(system).setValue(value));
    }

    private static CodeableConcept conceito(CodedValue v) {
        return new CodeableConcept()
                .addCoding(new Coding().setSystem(v.system()).setCode(v.code()));
    }

    private static CodeableConcept situacao(SituacaoClinica s) {
        SituacaoClinica efetiva = s != null ? s : SituacaoClinica.ATIVA;
        return conceito(new CodedValue(SituacaoClinica.system(), efetiva.codigo()));
    }

    private static void nota(List<Annotation> notas, String texto) {
        if (texto(texto)) {
            notas.add(new Annotation().setText(texto));
        }
    }

    /**
     * Troca lista nula por vazia, para o que o perfil permite ausente.
     *
     * <p>Evita a guarda de nulo repetida nos dois sítios que iteram os diagnósticos. A
     * obrigatoriedade continua sendo decidida em {@code exigirObrigatorios}, num lugar só.
     *
     * @param l lista, possivelmente nula
     * @param <T> tipo do elemento
     * @return a própria lista, ou vazia
     */
    private static <T> List<T> lista(List<T> l) {
        return l == null ? List.of() : l;
    }

    private static boolean texto(String s) {
        return s != null && !s.isBlank();
    }

    private static void exigirObrigatorios(EnrichedRegistroSumarioAlta dto) {
        Objects.requireNonNull(dto, "dto não pode ser nulo");
        exigir(dto.getIdLocalRegistro(), "idLocalRegistro");
        exigir(dto.getCnesEstabelecimento(), "cnesEstabelecimento");
        exigir(dto.getDataRegistroOrigem(), "dataRegistroOrigem");
        exigir(dto.getModalidadeAssistencial(), "modalidadeAssistencial");
        boolean identificado = texto(dto.getCnsPaciente()) || texto(dto.getCpfPaciente());
        boolean semIdentificacao = dto.getIndividuoNaoIdentificado() != null;
        if (identificado == semIdentificacao) {
            // As duas formas são excludentes (manual do SA, 5.5.2.4.2): ou o identificador
            // nacional, ou os dados demográficos com a justificativa. Nunca as duas, nunca
            // nenhuma.
            throw new IllegalArgumentException("Campo obrigatório ausente para o perfil do SA: informe cnsPaciente ou "
                    + "cpfPaciente, OU individuoNaoIdentificado — nunca os dois juntos");
        }
        if (semIdentificacao
                && (dto.getAlergia() != null
                        || dto.getPrescricaoAlta() != null
                        || texto(dto.getPlanoCuidados())
                        || (dto.getContato() != null && texto(dto.getContato().getMotivoAtendimento())))) {
            // O BRAlergiaReacaoAdversa, o BRPrescricaoMedicamento, o BRPlanoCuidados, o
            // BRResumoEvolucaoClinica e o BRObservacaoDescritiva exigem subject.identifier 1..1
            // -- e a extensão BRIndividuoNaoIdentificado-1.0 NÃO é permitida no contexto de
            // nenhum deles. Com indivíduo não identificado, esses blocos tornam o documento
            // irrepresentável: a Camada 4 o reprovaria. Melhor recusar na construção.
            throw new IllegalArgumentException(
                    "Indivíduo não identificado é incompatível com alergia, prescrição, plano de "
                            + "cuidados, resumo de evolução clínica e motivo de atendimento: esses "
                            + "perfis exigem subject.identifier (1..1) e não admitem a extensão");
        }
        if (semIdentificacao) {
            exigir(dto.getIndividuoNaoIdentificado().getSexo(), "individuoNaoIdentificado.sexo (gender 1..1)");
            exigir(
                    dto.getIndividuoNaoIdentificado().getAnoNascimento(),
                    "individuoNaoIdentificado.anoNascimento (birthYear 1..1)");
            exigir(
                    dto.getIndividuoNaoIdentificado().getJustificativa(),
                    "individuoNaoIdentificado.justificativa (reason 1..1)");
        }
        ContatoAssistencial ca = dto.getContato();
        exigir(ca, "contato (seção informacoesContatoAssistencial, 1..1)");
        exigir(ca.getCaraterAtendimento(), "contato.caraterAtendimento");
        exigir(ca.getDataInicio(), "contato.dataInicio");
        exigir(ca.getCnsProfissionalAlta(), "contato.cnsProfissionalAlta");
        exigir(ca.getProcedencia(), "contato.procedencia (hospitalization.admitSource 1..1)");
        exigir(ca.getMotivoDesfecho(), "contato.motivoDesfecho (hospitalization.dischargeDisposition 1..1)");
        // O diagnóstico é OPCIONAL no SA, e a prova está em dois lugares, não num:
        //   BRSumarioAlta: Composition.section:problemasDiagnosticosAvaliados ... 0..*
        //   BRContatoAssistencial-1.0 v2.1.0: Encounter.diagnosis .............. 1..*
        //       fatia problemAndDiagnosis ......................................  0..*
        //       fatia procedure ................................................  1..*
        // O mínimo 1..* do diagnosis é satisfeito pela fatia de PROCEDIMENTO, que continua
        // obrigatória abaixo. Olhar só a seção não bastaria para concluir — o Encounter é 1..1
        // e o seu diagnosis é 1..* —, e olhar só o elemento pai levaria ao erro oposto.
        // Medido em 13/10 (Arquitetura §8.8). Quando houver diagnóstico, o CID-10 é exigido: o
        // Condition.code é 1..1 no BRProblemaDiagnostico.
        if (dto.getDiagnosticos() != null) {
            for (Diagnostico d : dto.getDiagnosticos()) {
                exigir(d.getCid10(), "diagnostico.cid10 (Condition.code 1..1)");
            }
        }
        exigirLista(dto.getProcedimentos(), "procedimentos (seção procedimentosRealizados, 1..*)");
        for (Procedimento p : dto.getProcedimentos()) {
            exigir(p.getProcedimento(), "procedimento.procedimento");
            exigir(p.getDataRealizacao(), "procedimento.dataRealizacao");
            exigir(p.getFinanciamento(), "procedimento.financiamento (extensão 1..1 em diagnosis:procedure)");
            exigir(p.getQuantidade(), "procedimento.quantidade (extensão BRQuantidade 1..1)");
        }
        // Cardinalidade lida do SNAPSHOT do BRSumarioAlta: alergiaReacaoAdversa 0..*,
        // prescricaoAlta 0..1, planoCuidados 0..1, informacoesAdicionais 0..1. Apenas
        // resumoEvolucaoClinica é 1..1. O differential não mostra isso — mostra só o .entry
        // 1..1 de cada fatia, que vale quando a seção existe.
        if (dto.getAlergia() != null) {
            exigir(dto.getAlergia().getAgente(), "alergia.agente");
        }
        if (dto.getPrescricaoAlta() != null) {
            exigir(dto.getPrescricaoAlta().getCnsPrescritor(), "prescricaoAlta.cnsPrescritor (recorder 1..1)");
            exigir(dto.getPrescricaoAlta().getDataPrescricao(), "prescricaoAlta.dataPrescricao (authoredOn 1..1)");
            exigirLista(dto.getPrescricaoAlta().getItens(), "prescricaoAlta.itens (1..*)");
            for (ItemPrescricao i : dto.getPrescricaoAlta().getItens()) {
                exigir(i.getMedicamento(), "item.medicamento");
                exigir(i.getVezesMaximo(), "item.vezesMaximo (timing.repeat.countMax 1..1)");
                exigir(i.getVezes(), "item.vezes (timing.repeat.count 1..1)");
                exigir(i.getViaAdministracao(), "item.viaAdministracao (route 1..1)");
                exigir(i.getDose(), "item.dose (doseAndRate.dose 1..1)");
                exigir(i.getUnidadeDose(), "item.unidadeDose (doseAndRate.type 1..1)");
                exigir(
                        i.getDoseMaximaPorAdministracao(),
                        "item.doseMaximaPorAdministracao (maxDosePerAdministration 1..1)");
            }
        }
        // planoCuidados é 0..1 no snapshot: não se exige.
        exigir(dto.getResumoEvolucaoClinica(), "resumoEvolucaoClinica (1..1)");
    }

    private static void exigirLista(List<?> l, String campo) {
        if (l == null || l.isEmpty()) {
            throw new IllegalArgumentException("Campo obrigatório ausente para o perfil do SA: " + campo);
        }
    }

    private static void exigir(Object v, String campo) {
        if (v == null || (v instanceof String s && s.isBlank())) {
            throw new IllegalArgumentException("Campo obrigatório ausente para o perfil do SA: " + campo);
        }
    }
}
