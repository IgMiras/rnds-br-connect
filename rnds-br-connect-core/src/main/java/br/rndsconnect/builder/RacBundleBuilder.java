package br.rndsconnect.builder;

import br.rndsconnect.model.CodedValue;
import br.rndsconnect.model.EnrichedRegistroAtendimentoClinico;
import br.rndsconnect.model.EnrichedRegistroAtendimentoClinico.Alergia;
import br.rndsconnect.model.EnrichedRegistroAtendimentoClinico.Atestado;
import br.rndsconnect.model.EnrichedRegistroAtendimentoClinico.CidAvaliado;
import br.rndsconnect.model.EnrichedRegistroAtendimentoClinico.ContatoAssistencial;
import br.rndsconnect.model.EnrichedRegistroAtendimentoClinico.Diagnostico;
import br.rndsconnect.model.EnrichedRegistroAtendimentoClinico.IndividuoNaoIdentificado;
import br.rndsconnect.model.EnrichedRegistroAtendimentoClinico.ItemPrescricao;
import br.rndsconnect.model.EnrichedRegistroAtendimentoClinico.MedidaObservada;
import br.rndsconnect.model.EnrichedRegistroAtendimentoClinico.Procedimento;
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
import org.hl7.fhir.r4.model.IntegerType;
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
 * Camada 3 — construtor do Bundle do Registro de Atendimento Clínico (RAC).
 *
 * <pre>
 * Composition (RAC) ── section ──► Encounter ─ diagnosis ─► Condition(s), Procedure(s)
 *                                  ├─► Condition(s)            (problemas/diagnósticos, 1..*)
 *                                  ├─► Procedure(s)            (procedimentos, 1..*)
 *                                  ├─► Observation(s)          (medidas observadas)
 *                                  ├─► AllergyIntolerance(s)
 *                                  ├─► Composition (RPM) ─ section ─► MedicationRequest(s), Medication(s)
 *                                  ├─► CarePlan                (plano de cuidados)
 *                                  └─► CarePlan(s)             (atestados) ─ addresses ─► Condition(s)
 * </pre>
 *
 * <p>Independente dos demais Builders, sem herança (ADR-06), ainda que compartilhe nove perfis
 * com o SA. A independência não é cosmética: os pacotes de conformidade são por serviço, e a
 * mesma URL canônica pode trazer regras diferentes em cada um. O
 * {@code BRProcedimentoRealizado-1.0} do RAC e o do SA são a prova — mesma URL, conteúdos
 * distintos entre as distribuições do DATASUS.
 *
 * <h2>O que não é seção</h2>
 *
 * <p>O {@code Location}, a {@code Observation} descritiva do motivo, as linhas da prescrição e
 * as condições que o atestado endereça entram no Bundle mas <strong>não</strong> viram seção:
 * são alcançados por referência a partir de quem os usa. Das 24 entradas do artefato de
 * referência, 18 são seções.
 *
 * <h2>Teleconsulta</h2>
 *
 * <p>Não emitida. O manual v2.0 acrescenta {@code Encounter.type} com o CodeSystem
 * {@code BRModalidadeTelessaude}; em todos os pacotes publicados o elemento é {@code max=0} e o
 * CodeSystem não existe. Emitir seria produzir um documento que reprova na Camada 4.
 *
 * @author Igor Mendes Domingues Miras
 */
public class RacBundleBuilder implements FhirBundleBuilder<EnrichedRegistroAtendimentoClinico> {

    private static final String SD = "http://www.saude.gov.br/fhir/r4/StructureDefinition/";
    private static final String CS = "http://www.saude.gov.br/fhir/r4/CodeSystem/";
    private static final String NS = "http://www.saude.gov.br/fhir/r4/NamingSystem/";
    private static final String HL7 = "http://terminology.hl7.org/CodeSystem/";

    private static final String SYSTEM_INDIVIDUO = SD + "BRIndividuo-1.0";
    private static final String SYSTEM_ESTABELECIMENTO = SD + "BREstabelecimentoSaude-1.0";
    private static final String SYSTEM_PROFISSIONAL = SD + "BRProfissional-1.0";
    private static final String SYSTEM_LOTACAO = SD + "BRLotacaoProfissional-1.0";

    /**
     * {@code https://loinc.org/}, com a barra final — a forma que o {@code BRTipoObservacao-1.0}
     * exige, diferente da URI canônica {@code http://loinc.org}.
     */
    private static final CodedValue CODIGO_OBSERVACAO = new CodedValue("https://loinc.org/", "48767-8");

    /**
     * O UCUM canônico. O exemplo oficial declara aqui uma página HTML do HL7, que o validador do
     * DATASUS aceita e o FHIR proíbe.
     */
    private static final String UCUM = "http://unitsofmeasure.org";

    /** O ValueSet {@code BRTipoAtestado} tem um único código: não é domínio, é constante. */
    private static final CodedValue TIPO_ATESTADO = new CodedValue(CS + "BRTipoDocumento", "ATM");

    private final String solicitanteId;
    private final Supplier<String> geradorUuid;
    private final Supplier<Instant> relogio;

    /** @param solicitanteId Identificador Solicitante fornecido pela RNDS no credenciamento */
    public RacBundleBuilder(String solicitanteId) {
        this(solicitanteId, () -> UUID.randomUUID().toString(), Instant::now);
    }

    /**
     * @param solicitanteId Identificador Solicitante
     * @param geradorUuid fonte dos UUIDs transientes — injetável para testes determinísticos
     * @param relogio instante de montagem do Bundle — idem
     */
    public RacBundleBuilder(String solicitanteId, Supplier<String> geradorUuid, Supplier<Instant> relogio) {
        this.solicitanteId = Objects.requireNonNull(solicitanteId, "solicitanteId é obrigatório");
        this.geradorUuid = Objects.requireNonNull(geradorUuid, "geradorUuid é obrigatório");
        this.relogio = Objects.requireNonNull(relogio, "relogio é obrigatório");
    }

    @Override
    public Bundle build(EnrichedRegistroAtendimentoClinico dto) {
        exigirObrigatorios(dto);
        Instant montagem = relogio.get();

        Bundle bundle = new Bundle();
        bundle.setMeta(new Meta().setLastUpdated(Date.from(montagem)));
        bundle.setIdentifier(
                new Identifier().setSystem(NS + "BRRNDS-" + solicitanteId).setValue(dto.getIdLocalRegistro()));
        bundle.setType(Bundle.BundleType.DOCUMENT);
        bundle.setTimestamp(Date.from(montagem));

        // A primeira entrada é reservada para o Composition, que só pode ser montado depois que
        // todas as seções tiverem UUID. O documento FHIR exige o Composition em primeiro.
        Bundle.BundleEntryComponent entradaRac = bundle.addEntry();
        String uuidRac = geradorUuid.get();
        List<String> secoes = new ArrayList<>();

        // ── Contato assistencial, e o que ele referencia ────────────────
        String uuidEncounter = geradorUuid.get();
        Bundle.BundleEntryComponent entradaEncounter = bundle.addEntry();
        secoes.add(uuidEncounter);

        ContatoAssistencial ca = dto.getContato();
        String uuidLocal = texto(ca.getLocalAtendimento()) ? adicionar(bundle, local(ca.getLocalAtendimento())) : null;
        String uuidMotivo = texto(ca.getMotivoAtendimento())
                ? adicionar(bundle, observacaoDescritiva(dto, ca.getMotivoAtendimento()))
                : null;

        List<String> uuidsDiagnosticos = new ArrayList<>();
        for (Diagnostico d : dto.getDiagnosticos()) {
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

        // ── Medidas observadas, alergias ────────────────────────────────
        if (dto.getMedidas() != null) {
            for (MedidaObservada m : dto.getMedidas()) {
                secoes.add(adicionar(bundle, medida(m)));
            }
        }
        if (dto.getAlergias() != null) {
            for (Alergia a : dto.getAlergias()) {
                secoes.add(adicionar(bundle, alergia(dto, a)));
            }
        }

        // ── A prescrição: envelope RPM + linhas ─────────────────────────
        if (dto.getPrescricao() != null) {
            Bundle.BundleEntryComponent entradaRpm = bundle.addEntry();
            String uuidRpm = geradorUuid.get();
            secoes.add(uuidRpm);
            List<String> linhas = new ArrayList<>();
            for (ItemPrescricao item : dto.getPrescricao().getItens()) {
                String uuidMedicamento = geradorUuid.get();
                linhas.add(adicionar(bundle, pedido(dto, item, uuidMedicamento)));
                preencher(bundle.addEntry(), uuidMedicamento, medicamento(item));
                linhas.add(uuidMedicamento);
            }
            preencher(entradaRpm, uuidRpm, prescricao(dto, linhas));
        }

        // ── Plano de cuidados e atestados ───────────────────────────────
        if (texto(dto.getPlanoCuidados())) {
            secoes.add(adicionar(bundle, planoCuidados(dto)));
        }
        if (dto.getAtestados() != null) {
            for (Atestado a : dto.getAtestados()) {
                List<String> uuidsCondicoes = new ArrayList<>();
                if (a.getDiagnosticos() != null) {
                    for (CidAvaliado c : a.getDiagnosticos()) {
                        // As condições endereçadas pelo atestado NÃO são seções: só o atestado as alcança.
                        uuidsCondicoes.add(adicionar(bundle, cidAvaliado(dto, c)));
                    }
                }
                secoes.add(adicionar(bundle, atestado(dto, a, uuidsCondicoes)));
            }
        }

        preencher(entradaRac, uuidRac, registro(dto, secoes));
        return bundle;
    }

    // ══ Os dois Composition ═════════════════════════════════════════════

    private Composition registro(EnrichedRegistroAtendimentoClinico dto, List<String> secoes) {
        Composition c = documento(dto, "BRRegistroAtendimentoClinico", "RAC", "Registro de Atendimento Clínico");
        c.addCategory(conceito(dto.getModalidadeAssistencial()));
        // Uma seção por recurso, sem code nem title: o discriminador é entry.resolve().
        secoes.forEach(u -> c.addSection().addEntry(ref(u)));
        return c;
    }

    private Composition prescricao(EnrichedRegistroAtendimentoClinico dto, List<String> linhas) {
        Composition c = documento(dto, "BRRegistroPrescricaoMedicamento", "RPM", "Prescrição de Medicamento");
        Composition.SectionComponent s = c.addSection();
        linhas.forEach(u -> s.addEntry(ref(u)));
        return c;
    }

    private Composition documento(EnrichedRegistroAtendimentoClinico dto, String perfil, String tipo, String titulo) {
        Composition c = new Composition();
        c.setMeta(new Meta().addProfile(SD + perfil));
        c.setStatus(Composition.CompositionStatus.FINAL);
        c.setType(conceito(new CodedValue(CS + "BRTipoDocumento", tipo)));
        c.setSubject(individuo(dto));
        c.setDate(Date.from(dto.getDataRegistroOrigem()));
        c.addAuthor(porIdentificador(SYSTEM_ESTABELECIMENTO, dto.getCnesEstabelecimento()));
        c.setTitle(titulo);
        return c;
    }

    // ══ Contato assistencial ════════════════════════════════════════════

    private Encounter encounter(
            EnrichedRegistroAtendimentoClinico dto,
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
        e.setSubject(individuo(dto));
        e.getPeriod().setStart(Date.from(ca.getDataInicio()));
        if (ca.getDataFim() != null) {
            e.getPeriod().setEnd(Date.from(ca.getDataFim()));
        }

        // diagnosis é fatiado por condition.resolve(): Condition numa fatia, Procedure na outra.
        List<Diagnostico> ds = dto.getDiagnosticos();
        for (int i = 0; i < ds.size(); i++) {
            Encounter.DiagnosisComponent dc = e.addDiagnosis().setCondition(ref(diagnosticos.get(i)));
            if (ds.get(i).getPapel() != null) {
                dc.setUse(conceito(ds.get(i).getPapel()));
            }
            dc.setRank(ds.get(i).getOrdem() != null ? ds.get(i).getOrdem() : i + 1);
        }
        // Na fatia de procedimento, a extensão de financiamento é 1..1.
        List<Procedimento> ps = dto.getProcedimentos();
        for (int i = 0; i < ps.size(); i++) {
            Reference r = ref(procedimentos.get(i));
            r.addExtension(
                    new Extension(SD + "BRFinanciamento-1.0", conceito(ps.get(i).getFinanciamento())));
            e.addDiagnosis().setCondition(r);
        }

        Encounter.EncounterParticipantComponent part = e.addParticipant();
        part.addType(conceito(ca.getResponsabilidadeParticipante()));
        part.setIndividual(
                texto(ca.getCnesLotacaoProfissional())
                        ? porIdentificador(
                                SYSTEM_LOTACAO, ca.getCnsProfissional() + "-" + ca.getCnesLotacaoProfissional())
                        : porIdentificador(SYSTEM_PROFISSIONAL, ca.getCnsProfissional()));
        if (ca.getCboProfissional() != null) {
            part.addExtension(new Extension(SD + "BROcupacao-1.0", conceito(ca.getCboProfissional())));
        }
        if (ca.getEquipe() != null) {
            part.addExtension(new Extension(SD + "BRIdentificacaoEquipe-1.0", new IntegerType(ca.getEquipe())));
        }

        // hospitalization: admitSource e dischargeDisposition são 1..1 no perfil.
        e.getHospitalization().setAdmitSource(conceito(ca.getProcedencia()));
        e.getHospitalization().setDischargeDisposition(conceito(ca.getMotivoDesfecho()));
        if (texto(ca.getOutrasInformacoes())) {
            Annotation nota = new Annotation().setText(ca.getOutrasInformacoes());
            nota.setTime(Date.from(dto.getDataRegistroOrigem()));
            e.getHospitalization().addExtension(new Extension(SD + "BROutrasInformacoes", nota));
        }
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
        // O perfil fixa mode = kind: é um TIPO de local, não uma instância.
        l.setMode(Location.LocationMode.KIND);
        return l;
    }

    // ══ Condições e procedimentos ═══════════════════════════════════════

    private Condition diagnostico(EnrichedRegistroAtendimentoClinico dto, Diagnostico d) {
        Condition c = new Condition();
        c.setMeta(new Meta().addProfile(SD + "BRProblemaDiagnostico"));
        c.setClinicalStatus(situacao(d.getSituacaoClinica()));
        c.addCategory(new CodeableConcept()
                .addCoding(new Coding().setSystem(HL7 + "condition-category").setCode("encounter-diagnosis")));
        // display é max=0 no code: só system e code. O ValueSet admite CID-10 e CIAP-2.
        c.setCode(conceito(d.getProblemaDiagnostico()));
        c.setSubject(individuo(dto));
        nota(c.getNote(), d.getObservacao());
        return c;
    }

    private Condition cidAvaliado(EnrichedRegistroAtendimentoClinico dto, CidAvaliado c) {
        Condition cond = new Condition();
        cond.setMeta(new Meta().addProfile(SD + "BRCID10Avaliado-1.0"));
        cond.setClinicalStatus(situacao(c.getSituacaoClinica()));
        if (c.getCategoriaDiagnostico() != null) {
            cond.addCategory(conceito(c.getCategoriaDiagnostico()));
        }
        cond.setCode(conceito(c.getCid10()));
        cond.setSubject(individuo(dto));
        nota(cond.getNote(), c.getObservacao());
        return cond;
    }

    private Procedure procedimento(EnrichedRegistroAtendimentoClinico dto, Procedimento p) {
        Procedure pr = new Procedure();
        pr.setMeta(new Meta().addProfile(SD + "BRProcedimentoRealizado-1.0"));
        if (texto(p.getNumeroAutorizacao())) {
            // A constraint cmd-1 exige o formato da EXPRESSÃO, não o da descrição (defeito nº 16).
            pr.addIdentifier(new Identifier()
                    .setType(conceito(new CodedValue(CS + "BRTipoIdentificador", "AUTH")))
                    .setValue(p.getNumeroAutorizacao()));
        }
        pr.setStatus(Procedure.ProcedureStatus.COMPLETED);
        pr.setCode(conceito(p.getProcedimento()));
        pr.setSubject(individuo(dto));
        pr.setPerformed(new DateTimeType(Date.from(p.getDataRealizacao())));

        Procedure.ProcedurePerformerComponent exec = pr.addPerformer();
        if (p.getCboExecutante() != null) {
            exec.setFunction(conceito(p.getCboExecutante()));
        }
        if (texto(p.getCnsExecutante())) {
            exec.setActor(
                    texto(p.getCnesLotacaoExecutante())
                            ? porIdentificador(
                                    SYSTEM_LOTACAO, p.getCnsExecutante() + "-" + p.getCnesLotacaoExecutante())
                            : porIdentificador(SYSTEM_PROFISSIONAL, p.getCnsExecutante()));
        } else {
            exec.setActor(porIdentificador(SYSTEM_ESTABELECIMENTO, dto.getCnesEstabelecimento()));
        }
        if (texto(p.getCnesEmNomeDe())) {
            exec.setOnBehalfOf(porIdentificador(SYSTEM_ESTABELECIMENTO, p.getCnesEmNomeDe()));
        }
        if (p.getEquipe() != null) {
            exec.addExtension(new Extension(SD + "BRIdentificacaoEquipe-1.0", new IntegerType(p.getEquipe())));
        }
        // extension:quantity é 1..1. Sem ela o Procedure não conforma, e a falha desce em cascata:
        // a fatia diagnosis:procedure não casa, o Encounter não conforma, e as duas seções
        // obrigatórias do CMD deixam de ser reconhecidas — oito erros, nenhum apontando a causa.
        pr.addExtension(new Extension(SD + "BRQuantidade-1.0", new PositiveIntType(p.getQuantidade())));
        nota(pr.getNote(), p.getObservacao());
        return pr;
    }

    // ══ Medidas observadas ══════════════════════════════════════════════

    /**
     * Uma {@code Observation} por medida. O artefato de referência não informa
     * {@code Observation.subject} nessas entradas — o sujeito do documento já é o do
     * {@code Composition} —, e o Builder segue a mesma forma.
     */
    private Observation medida(MedidaObservada m) {
        Observation o = new Observation();
        o.setMeta(new Meta().addProfile(SD + "BRMedidaObservada"));
        o.setStatus(Observation.ObservationStatus.FINAL);
        o.setCode(conceito(m.getMedida()));

        if (m.getValor() != null) {
            Quantity q = new Quantity().setValue(m.getValor());
            if (texto(m.getUnidadeUcum())) {
                q.setSystem(UCUM).setCode(m.getUnidadeUcum());
            }
            o.setValue(q);
        } else if (m.getValorData() != null) {
            o.setValue(new DateTimeType(Date.from(m.getValorData())));
        } else if (m.getValorCodigo() != null) {
            // Sem binding no perfil: o par (system, code) é do integrador, e é ele quem responde.
            o.setValue(conceito(m.getValorCodigo()));
        }

        if (m.getMetodo() != null) {
            o.setMethod(conceito(m.getMetodo()));
        }
        if (m.getLocalAfericao() != null) {
            o.setBodySite(conceito(m.getLocalAfericao()));
        }
        if (m.getOrigemMedida() != null) {
            o.addExtension(new Extension(SD + "BROrigemMedida", conceito(m.getOrigemMedida())));
        }
        if (m.getRoupasUsadas() != null) {
            o.addExtension(new Extension(SD + "BRRoupasUsadasMedicao", conceito(m.getRoupasUsadas())));
        }
        if (m.getFrequenciaUso() != null) {
            o.setEffective(new Timing().setCode(conceito(m.getFrequenciaUso())));
        }
        nota(o.getNote(), m.getObservacao());
        return o;
    }

    private Observation observacaoDescritiva(EnrichedRegistroAtendimentoClinico dto, String texto) {
        Observation o = new Observation();
        o.setMeta(new Meta().addProfile(SD + "BRObservacaoDescritiva-1.0"));
        o.setStatus(Observation.ObservationStatus.FINAL);
        o.setCode(conceito(CODIGO_OBSERVACAO));
        o.setSubject(individuo(dto));
        o.setValue(new StringType(texto));
        o.setIssued(Date.from(dto.getDataRegistroOrigem()));
        return o;
    }

    // ══ Alergia ═════════════════════════════════════════════════════════

    private AllergyIntolerance alergia(EnrichedRegistroAtendimentoClinico dto, Alergia a) {
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
        // Invariante ait-1 do FHIR base: clinicalStatus é obrigatório salvo registro por engano.
        if (v != VerificacaoAlergia.REGISTRADA_POR_ENGANO) {
            ai.setClinicalStatus(conceito(new CodedValue(HL7 + "allergyintolerance-clinical", "active")));
        }
        return ai;
    }

    // ══ Prescrição ══════════════════════════════════════════════════════

    private MedicationRequest pedido(
            EnrichedRegistroAtendimentoClinico dto, ItemPrescricao item, String uuidMedicamento) {
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
            d.setAsNeeded(new BooleanType(item.getSeNecessario()));
        }
        mr.setAuthoredOn(Date.from(dto.getPrescricao().getDataPrescricao()));
        mr.setRecorder(porIdentificador(SYSTEM_PROFISSIONAL, dto.getPrescricao().getCnsPrescritor()));
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

    // ══ Plano de cuidados e atestado ════════════════════════════════════

    private CarePlan planoCuidados(EnrichedRegistroAtendimentoClinico dto) {
        CarePlan cp = new CarePlan();
        cp.setMeta(new Meta().addProfile(SD + "BRPlanoCuidados-1.0"));
        cp.setStatus(CarePlan.CarePlanStatus.ACTIVE);
        cp.setIntent(CarePlan.CarePlanIntent.PLAN);
        cp.setSubject(individuo(dto));
        cp.setDescription(dto.getPlanoCuidados());
        return cp;
    }

    /**
     * O atestado é um {@code CarePlan} com uma atividade. O {@code CarePlan.description} é
     * {@code max=0} no perfil: o texto vai em {@code activity.detail.description}. O estado da
     * atividade e a categoria vêm de ValueSets de um código só — constantes, não domínios.
     */
    private CarePlan atestado(EnrichedRegistroAtendimentoClinico dto, Atestado a, List<String> uuidsCondicoes) {
        CarePlan cp = new CarePlan();
        cp.setMeta(new Meta().addProfile(SD + "BRAtestado"));
        cp.setStatus(CarePlan.CarePlanStatus.ACTIVE);
        cp.setIntent(CarePlan.CarePlanIntent.PLAN);
        cp.addCategory(conceito(TIPO_ATESTADO));
        cp.setSubject(individuo(dto));
        uuidsCondicoes.forEach(u -> cp.addAddresses(ref(u)));

        Timing t = new Timing();
        t.addEvent(Date.from(a.getDataInicioAfastamento()));
        t.getRepeat().setCount(a.getDiasAfastamento());

        CarePlan.CarePlanActivityDetailComponent det = cp.addActivity().getDetail();
        det.setStatus(CarePlan.CarePlanActivityStatus.UNKNOWN);
        det.setScheduled(t);
        if (texto(a.getInformacoesAdicionais())) {
            det.setDescription(a.getInformacoesAdicionais());
        }
        return cp;
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

    /**
     * A referência ao indivíduo: o identificador nacional OU os dados observados de quem não
     * pôde ser identificado, nunca os dois (manual, 5.5.2.4.2).
     *
     * <p>Um helper só, ao contrário do Sumário de Alta, que precisou de dois. O motivo é medido:
     * dos quatorze contextos de {@code subject} do pacote, só quatro aceitam a extensão, e
     * {@code exigirObrigatorios} recusa os blocos que carregam os outros dez quando o indivíduo
     * não é identificado. Logo, todo recurso construído nessa forma está num contexto permitido
     * — a {@code Observation} de medida observada inclusive, que não emite {@code subject}.
     */
    private static Reference individuo(EnrichedRegistroAtendimentoClinico dto) {
        IndividuoNaoIdentificado ni = dto.getIndividuoNaoIdentificado();
        if (ni == null) {
            return porIdentificador(
                    SYSTEM_INDIVIDUO, texto(dto.getCnsPaciente()) ? dto.getCnsPaciente() : dto.getCpfPaciente());
        }
        // Sem CNS nem CPF não há identifier, e display e reference são max=0 nos quatro perfis:
        // a extensão sozinha preenche a referência e satisfaz a invariante ele-1, por ter filhos.
        Reference r = new Reference();
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

    private static boolean texto(String s) {
        return s != null && !s.isBlank();
    }

    private static void exigirObrigatorios(EnrichedRegistroAtendimentoClinico dto) {
        Objects.requireNonNull(dto, "dto não pode ser nulo");
        exigir(dto.getIdLocalRegistro(), "idLocalRegistro");
        exigir(dto.getCnesEstabelecimento(), "cnesEstabelecimento");
        exigir(dto.getDataRegistroOrigem(), "dataRegistroOrigem");
        exigir(dto.getModalidadeAssistencial(), "modalidadeAssistencial (Composition.category e Encounter.class)");
        boolean identificado = texto(dto.getCnsPaciente()) || texto(dto.getCpfPaciente());
        boolean semIdentificacao = dto.getIndividuoNaoIdentificado() != null;
        if (identificado == semIdentificacao) {
            // As duas formas são excludentes (manual, 5.5.2.4.2): ou o identificador nacional, ou
            // os dados observados com a justificativa. Nunca as duas, nunca nenhuma.
            throw new IllegalArgumentException("Campo obrigatório ausente para o perfil do RAC: informe cnsPaciente ou "
                    + "cpfPaciente, OU individuoNaoIdentificado — nunca os dois juntos");
        }
        if (semIdentificacao) {
            IndividuoNaoIdentificado ni = dto.getIndividuoNaoIdentificado();
            exigir(ni.getSexo(), "individuoNaoIdentificado.sexo (extensão gender 1..1)");
            exigir(ni.getAnoNascimento(), "individuoNaoIdentificado.anoNascimento (extensão birthYear 1..1)");
            exigir(ni.getJustificativa(), "individuoNaoIdentificado.justificativa (extensão reason 1..1)");
            // Medido em 10/10 (PARTE 3 da sonda, pacote rac): o BRAlergiaReacaoAdversa-1.0, o
            // BRPrescricaoMedicamento, o BRPlanoCuidados-1.0, o BRAtestado e o BRCID10Avaliado-1.0
            // exigem subject.identifier 1..1; o BRObservacaoDescritiva-1.0 (motivo de atendimento)
            // e o BRRegistroPrescricaoMedicamento não permitem a extensão. Nenhum desses blocos
            // sobrevive sem identificador, e a Camada 4 reprovaria o documento inteiro — melhor
            // recusar na construção, nomeando o que sai. As três seções obrigatórias e as medidas
            // observadas ficam.
            boolean temAlergia = dto.getAlergias() != null && !dto.getAlergias().isEmpty();
            boolean temAtestado =
                    dto.getAtestados() != null && !dto.getAtestados().isEmpty();
            boolean temMotivo =
                    dto.getContato() != null && texto(dto.getContato().getMotivoAtendimento());
            if (temAlergia
                    || dto.getPrescricao() != null
                    || texto(dto.getPlanoCuidados())
                    || temAtestado
                    || temMotivo) {
                throw new IllegalArgumentException(
                        "Indivíduo não identificado é incompatível com alergia, prescrição, plano de "
                                + "cuidados, atestado e motivo de atendimento: os perfis desses recursos "
                                + "exigem subject.identifier 1..1 ou não permitem a extensão "
                                + "BRIndividuoNaoIdentificado-1.0. O documento não identificado leva o "
                                + "contato assistencial, os problemas/diagnósticos, os procedimentos e as "
                                + "medidas observadas");
            }
        }

        ContatoAssistencial ca = dto.getContato();
        exigir(ca, "contato (seção informacoesContatoAssistencial, 1..1)");
        exigir(ca.getCaraterAtendimento(), "contato.caraterAtendimento");
        exigir(ca.getDataInicio(), "contato.dataInicio");
        exigir(ca.getCnsProfissional(), "contato.cnsProfissional (participant.individual 1..1)");
        exigir(ca.getResponsabilidadeParticipante(), "contato.responsabilidadeParticipante (participant.type 1..1)");
        exigir(ca.getProcedencia(), "contato.procedencia (hospitalization.admitSource 1..1)");
        exigir(ca.getMotivoDesfecho(), "contato.motivoDesfecho (hospitalization.dischargeDisposition 1..1)");

        exigirLista(dto.getDiagnosticos(), "diagnosticos (seção problemasDiagnosticosAvaliados, 1..*)");
        for (Diagnostico d : dto.getDiagnosticos()) {
            exigir(d.getProblemaDiagnostico(), "diagnostico.problemaDiagnostico (Condition.code 1..1)");
        }
        exigirLista(dto.getProcedimentos(), "procedimentos (seção procedimentosRealizados, 1..*)");
        for (Procedimento p : dto.getProcedimentos()) {
            exigir(p.getProcedimento(), "procedimento.procedimento");
            exigir(p.getDataRealizacao(), "procedimento.dataRealizacao");
            exigir(p.getFinanciamento(), "procedimento.financiamento (extensão 1..1 em diagnosis:procedure)");
            exigir(p.getQuantidade(), "procedimento.quantidade (extensão BRQuantidade 1..1)");
        }
        if (dto.getMedidas() != null) {
            for (MedidaObservada m : dto.getMedidas()) {
                exigir(m.getMedida(), "medida.medida (Observation.code 1..1)");
                if (m.getValor() == null && m.getValorData() == null && m.getValorCodigo() == null) {
                    throw new IllegalArgumentException(
                            "Campo obrigatório ausente para o perfil do RAC: medida.valor, valorData ou valorCodigo");
                }
            }
        }
        if (dto.getAlergias() != null) {
            for (Alergia a : dto.getAlergias()) {
                exigir(a.getAgente(), "alergia.agente (AllergyIntolerance.code 1..1)");
            }
        }
        if (dto.getPrescricao() != null) {
            exigir(dto.getPrescricao().getCnsPrescritor(), "prescricao.cnsPrescritor (recorder 1..1)");
            exigir(dto.getPrescricao().getDataPrescricao(), "prescricao.dataPrescricao (authoredOn 1..1)");
            exigirLista(dto.getPrescricao().getItens(), "prescricao.itens (1..*)");
            for (ItemPrescricao i : dto.getPrescricao().getItens()) {
                exigir(i.getMedicamento(), "item.medicamento");
                exigir(i.getVezes(), "item.vezes (timing.repeat.count 1..1)");
                exigir(i.getVezesMaximo(), "item.vezesMaximo (timing.repeat.countMax 1..1)");
                exigir(i.getViaAdministracao(), "item.viaAdministracao (route 1..1)");
                exigir(i.getDose(), "item.dose (doseAndRate.dose 1..1)");
                exigir(i.getUnidadeDose(), "item.unidadeDose (doseAndRate.type 1..1)");
                exigir(
                        i.getDoseMaximaPorAdministracao(),
                        "item.doseMaximaPorAdministracao (maxDosePerAdministration 1..1)");
            }
        }
        if (dto.getAtestados() != null) {
            for (Atestado a : dto.getAtestados()) {
                exigir(a.getDataInicioAfastamento(), "atestado.dataInicioAfastamento (scheduledTiming.event 1..1)");
                exigir(a.getDiasAfastamento(), "atestado.diasAfastamento (scheduledTiming.repeat.count 1..*)");
                if (a.getDiagnosticos() != null) {
                    for (CidAvaliado c : a.getDiagnosticos()) {
                        exigir(c.getCid10(), "atestado.diagnostico.cid10 (Condition.code 1..1)");
                    }
                }
            }
        }
    }

    private static void exigirLista(List<?> l, String campo) {
        if (l == null || l.isEmpty()) {
            throw new IllegalArgumentException("Campo obrigatório ausente para o perfil do RAC: " + campo);
        }
    }

    private static void exigir(Object v, String campo) {
        if (v == null || (v instanceof String s && s.isBlank())) {
            throw new IllegalArgumentException("Campo obrigatório ausente para o perfil do RAC: " + campo);
        }
    }
}
