package br.rndsconnect.builder;

import br.rndsconnect.model.CodedValue;
import br.rndsconnect.model.EnrichedRegistroDispensacaoMedicamento;
import br.rndsconnect.model.EnrichedRegistroDispensacaoMedicamento.Prescricao;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Dosage;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.MedicationDispense;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.SimpleQuantity;

/**
 * Camada 3 — construtor do Bundle do serviço REDFM (dispensação de medicamentos).
 *
 * <p>Produz um Bundle {@code document} com três entradas e até quatro recursos
 * <strong>contidos</strong>:
 *
 * <pre>
 * Composition ──section.entry──► MedicationDispense ──medicationReference──► Medication
 *                                      │
 *                                      ├─ contained:prescription          (#prescription)
 *                                      ├─ contained:prescriber-identifier (#prescriber-identifier)
 *                                      ├─ contained:prescriber-specialty  (#prescriber-specialty)
 *                                      └─ contained:pharmacist-crf        (#pharmacist-crf)
 * </pre>
 *
 * <h2>Duas fundações arquitetônicas</h2>
 *
 * <p>É o primeiro serviço da geração {@code RNDS*} e o primeiro que <strong>deriva do
 * BR-Core</strong>. Seu pacote de conformidade — o fecho estrito, com 46 artefatos — combina
 * XML do DATASUS com JSON do IG BR-Core, o que só é possível porque a Camada 4 aceita os dois
 * formatos no mesmo pacote (Arquitetura §9.2).
 *
 * <h2>{@code contained} e a convenção de referência</h2>
 *
 * <p>Referências a recursos contidos usam {@code #id}, e não {@code urn:uuid}. Isso
 * <strong>não contraria o ADR-07</strong>: aquele trata de referências entre <em>entradas do
 * Bundle</em>, e um recurso contido não é entrada — vive dentro do recurso que o contém, e o
 * FHIR reserva {@code #id} exatamente para esse caso.
 *
 * <p>Os quatro {@code id} são <strong>fixados pelo perfil</strong>, não escolhidos pelo
 * framework: {@code prescription}, {@code prescriber-identifier},
 * {@code prescriber-specialty} e {@code pharmacist-crf}.
 *
 * <h2>As duas fatias de {@code authorizingPrescription} são exclusivas</h2>
 *
 * <p>Ou a receita já está na RNDS — e então basta referenciá-la por {@code identifier} — ou
 * ela vem embutida como {@code contained:prescription}. Emitir as duas seria afirmar que a
 * mesma receita está em dois lugares.
 *
 * @author Igor Mendes Domingues Miras
 */
public class RedfmBundleBuilder implements FhirBundleBuilder<EnrichedRegistroDispensacaoMedicamento> {

    private static final String SD = "http://www.saude.gov.br/fhir/r4/StructureDefinition/";
    private static final String CS = "http://www.saude.gov.br/fhir/r4/CodeSystem/";
    private static final String NS = "http://www.saude.gov.br/fhir/r4/NamingSystem/";

    private static final String PERFIL_COMPOSITION = SD + "RNDSRegistroEletronicoDispensacaoFornecimentoMedicamento";
    private static final String PERFIL_DISPENSE = SD + "RNDSMedicamentoDispensadoFornecido";
    private static final String PERFIL_MEDICATION = SD + "RNDSMedicamentoDispensado";
    private static final String PERFIL_PRESCRICAO = SD + "RNDSPrescricaoMedicamentoDispensado";
    private static final String PERFIL_PROFISSIONAL = SD + "RNDSProfissional";
    private static final String PERFIL_LOTACAO = SD + "RNDSLotacaoProfissional";

    private static final String SYSTEM_INDIVIDUO = SD + "BRIndividuo-1.0";
    private static final String SYSTEM_ESTABELECIMENTO = SD + "BREstabelecimentoSaude-1.0";
    private static final String SYSTEM_PROFISSIONAL = SD + "BRProfissional-1.0";
    private static final String SYSTEM_PESSOA_JURIDICA = SD + "BRPessoaJuridicaProfissionalLiberal-1.0";
    private static final String SID = "https://saude.gov.br/sid/";

    /** Os quatro {@code id} de recurso contido, fixados pelo perfil. */
    private static final String ID_PRESCRICAO = "prescription";

    private static final String ID_PRESCRITOR = "prescriber-identifier";
    private static final String ID_ESPECIALIDADE = "prescriber-specialty";
    private static final String ID_FARMACEUTICO = "pharmacist-crf";

    /** {@code fixedString} do perfil. */
    private static final String TITULO = "Registro Eletrônico de Dispensação ou Fornecimento de Medicamentos";
    /** {@code fixedString} da seção. */
    private static final String TITULO_SECAO = "Medicamentos Dispensados";
    /** Código LOINC fixado pelo perfil para {@code section.code}. */
    private static final CodedValue CODIGO_SECAO = new CodedValue("http://loinc.org", "56445-0");

    private static final CodedValue TIPO_DOCUMENTO = CodedValue.rnds("BRTipoDocumento", "REDFM");
    private static final CodedValue TIPO_ID_MEDICO =
            new CodedValue("http://terminology.hl7.org/CodeSystem/v2-0203", "MD");
    private static final CodedValue TIPO_ID_FARMACEUTICO =
            new CodedValue("http://terminology.hl7.org/CodeSystem/v2-0203", "RPH");

    private final String solicitanteId;
    private final Supplier<String> geradorUuid;
    private final Supplier<Instant> relogio;

    /**
     * @param solicitanteId Identificador Solicitante fornecido pela RNDS no credenciamento
     */
    public RedfmBundleBuilder(String solicitanteId) {
        this(solicitanteId, () -> UUID.randomUUID().toString(), Instant::now);
    }

    /**
     * @param solicitanteId Identificador Solicitante
     * @param geradorUuid fonte dos UUIDs transientes — injetável para testes determinísticos
     * @param relogio instante de montagem do Bundle — idem
     */
    public RedfmBundleBuilder(String solicitanteId, Supplier<String> geradorUuid, Supplier<Instant> relogio) {
        this.solicitanteId = Objects.requireNonNull(solicitanteId, "solicitanteId é obrigatório");
        this.geradorUuid = Objects.requireNonNull(geradorUuid, "geradorUuid é obrigatório");
        this.relogio = Objects.requireNonNull(relogio, "relogio é obrigatório");
    }

    @Override
    public Bundle build(EnrichedRegistroDispensacaoMedicamento dto) {
        exigirObrigatorios(dto);

        String uuidComposition = geradorUuid.get();
        String uuidDispense = geradorUuid.get();
        String uuidMedication = geradorUuid.get();
        Instant montagem = relogio.get();

        Bundle bundle = new Bundle();
        bundle.setMeta(new Meta().setLastUpdated(Date.from(montagem)));
        bundle.setIdentifier(
                new Identifier().setSystem(NS + "BRRNDS-" + solicitanteId).setValue(dto.getIdLocalRegistro()));
        bundle.setType(Bundle.BundleType.DOCUMENT);
        bundle.setTimestamp(Date.from(montagem));

        adicionar(bundle, uuidComposition, montarComposition(dto, uuidDispense));
        adicionar(bundle, uuidDispense, montarDispense(dto, uuidMedication));
        adicionar(bundle, uuidMedication, montarMedication(dto));
        return bundle;
    }

    // ══ Composition ═════════════════════════════════════════════════════

    private Composition montarComposition(EnrichedRegistroDispensacaoMedicamento dto, String uuidDispense) {
        Composition c = new Composition();
        c.setMeta(new Meta().addProfile(PERFIL_COMPOSITION));
        c.setStatus(Composition.CompositionStatus.FINAL);
        c.setType(conceito(TIPO_DOCUMENTO));
        c.setSubject(referenciaPorIdentificador(SYSTEM_INDIVIDUO, dto.identificadorIndividuo()));
        c.setDate(Date.from(dto.getDataRegistroOrigem()));
        c.addAuthor(referenciaPorIdentificador(SYSTEM_ESTABELECIMENTO, dto.getCnesEstabelecimento()));
        c.setTitle(TITULO);

        // relatesTo e 0..1 e so existe em retificacao: o documento novo declara qual
        // registro anterior ele corrige.
        if (dto.getSubstituiDocumentoRnds() != null
                && !dto.getSubstituiDocumentoRnds().isBlank()) {
            c.addRelatesTo()
                    .setCode(Composition.DocumentRelationshipType.REPLACES)
                    .setTarget(new Reference(dto.getSubstituiDocumentoRnds()));
        }

        // O título da seção e o código LOINC são fixados pelo perfil.
        Composition.SectionComponent secao = c.addSection();
        secao.setTitle(TITULO_SECAO);
        secao.setCode(conceito(CODIGO_SECAO));
        secao.addEntry(new Reference("urn:uuid:" + uuidDispense).setDisplay(dto.getDescricaoMedicamento()));
        return c;
    }

    // ══ MedicationDispense, com os contidos ═════════════════════════════

    private MedicationDispense montarDispense(EnrichedRegistroDispensacaoMedicamento dto, String uuidMedication) {
        MedicationDispense d = new MedicationDispense();
        d.setMeta(new Meta().addProfile(PERFIL_DISPENSE));
        d.addIdentifier(new Identifier().setSystem(NS + "dispensed-item-id").setValue(dto.getIdItemDispensado()));
        d.setStatus(MedicationDispense.MedicationDispenseStatus.COMPLETED);
        d.setCategory(conceito(dto.getCategoriaDispensacao()));
        d.setMedication(new Reference("urn:uuid:" + uuidMedication));
        d.setSubject(referenciaPorIdentificador(SYSTEM_INDIVIDUO, dto.identificadorIndividuo()));

        // Os contidos do prescritor só existem quando a receita vem embutida.
        if (dto.prescricaoEmbutida()) {
            Prescricao p = dto.getPrescricao();
            d.addContained(montarPrescricao(dto, p));
            d.addContained(montarPrescritor(p));
            d.addContained(montarEspecialidade(p));
            d.addSupportingInformation(new Reference("#" + ID_PRESCRITOR)
                    .setDisplay("Referência para a identificação do Profissional que prescreveu o medicamento."));
            d.addSupportingInformation(new Reference("#" + ID_ESPECIALIDADE)
                    .setDisplay("Referência para a Especialidade do profissional que prescreveu o medicamento."));
        }
        d.addContained(montarFarmaceutico(dto));

        // performer é fatiado: organization e pharmacist.
        // setId(...) devolve Element no HAPI, e nao o tipo especifico -- encadear depois
        // dele quebra a tipagem. Por isso cada atribuicao vai em sua propria chamada.
        MedicationDispense.MedicationDispensePerformerComponent org = d.addPerformer();
        org.setId("organization");
        org.setActor(referenciaPorIdentificador(SYSTEM_ESTABELECIMENTO, dto.getCnesEstabelecimento()));

        MedicationDispense.MedicationDispensePerformerComponent farm = d.addPerformer();
        farm.setId("pharmacist");
        farm.setFunction(conceito(dto.getCboFarmaceutico()));
        farm.setActor(new Reference("#" + ID_FARMACEUTICO)
                .setIdentifier(new Identifier().setSystem(SYSTEM_PROFISSIONAL).setValue(dto.getCnsFarmaceutico())));

        // As duas fatias de authorizingPrescription são MUTUAMENTE EXCLUSIVAS: ou a receita
        // está na RNDS, ou vem embutida como contained. Emitir as duas afirmaria que a
        // mesma receita está em dois lugares.
        if (dto.prescricaoEmbutida()) {
            Reference fora = new Reference("#" + ID_PRESCRICAO);
            fora.setId("out-of-rnds");
            d.addAuthorizingPrescription(fora);
        } else {
            Reference dentro = new Reference();
            dentro.setId("in-rnds");
            dentro.setIdentifier(
                    new Identifier().setSystem(NS + "prescription-item-id").setValue(dto.getIdPrescricaoNaRnds()));
            d.addAuthorizingPrescription(dentro);
        }

        d.setType(conceito(dto.getTipoDispensacao()));
        d.setQuantity((SimpleQuantity)
                new SimpleQuantity().setValue(dto.getQuantidade()).setUnit(dto.getUnidadeQuantidade()));
        d.setWhenHandedOver(Date.from(dto.getDataEntrega()));
        if (dto.getObservacaoDispensacao() != null
                && !dto.getObservacaoDispensacao().isBlank()) {
            d.addNote(new org.hl7.fhir.r4.model.Annotation().setText(dto.getObservacaoDispensacao()));
        }
        d.addReceiver(referenciaPorIdentificador(SYSTEM_INDIVIDUO, dto.identificadorIndividuo()));
        return d;
    }

    /** {@code contained:prescription} — a receita, quando não está na RNDS. */
    private MedicationRequest montarPrescricao(EnrichedRegistroDispensacaoMedicamento dto, Prescricao p) {
        MedicationRequest r = new MedicationRequest();
        r.setId(ID_PRESCRICAO);
        r.setMeta(new Meta().addProfile(PERFIL_PRESCRICAO));
        r.addIdentifier(new Identifier().setSystem(NS + "prescription-item-id").setValue(p.getIdItemPrescrito()));
        r.setStatus(MedicationRequest.MedicationRequestStatus.ACTIVE);
        r.setIntent(MedicationRequest.MedicationRequestIntent.ORDER);
        r.setMedication(conceitoComDisplay(dto.getMedicamento(), dto.getDescricaoMedicamento()));
        r.setSubject(referenciaPorIdentificador(SYSTEM_INDIVIDUO, dto.identificadorIndividuo()));
        r.setAuthoredOn(Date.from(p.getDataPrescricao()));
        r.setRequester(new Reference("PractitionerRole/prescriber-info")
                .setIdentifier(new Identifier().setSystem(SYSTEM_PROFISSIONAL).setValue(p.getCnsPrescritor())));
        if (p.getCid10() != null) {
            r.addReasonCode(conceito(p.getCid10()));
        }

        if (p.getObservacao() != null && !p.getObservacao().isBlank()) {
            r.addNote(new org.hl7.fhir.r4.model.Annotation().setText(p.getObservacao()));
        }

        Dosage dose = r.addDosageInstruction();
        dose.setRoute(conceito(p.getViaAdministracao()));
        Dosage.DosageDoseAndRateComponent dr = dose.addDoseAndRate();
        Quantity q = new Quantity().setValue(p.getDose());
        if (p.getUnidadeDose() != null) {
            q.setSystem(p.getUnidadeDose().system()).setCode(p.getUnidadeDose().code());
        }
        q.setUnit(p.getDescricaoUnidadeDose());
        dr.setDose(q);
        return r;
    }

    /** {@code contained:prescriber-identifier} — registro no conselho de quem prescreveu. */
    private Practitioner montarPrescritor(Prescricao p) {
        Practitioner pr = new Practitioner();
        pr.setId(ID_PRESCRITOR);
        pr.setMeta(new Meta().addProfile(PERFIL_PROFISSIONAL));
        pr.addIdentifier(new Identifier()
                .setUse(Identifier.IdentifierUse.OFFICIAL)
                .setType(conceito(TIPO_ID_MEDICO))
                .setSystem(SID + p.getConselhoPrescritor())
                .setValue(p.getInscricaoPrescritor()));
        pr.setActive(true);
        return pr;
    }

    /** {@code contained:prescriber-specialty} — lotação e CBO do prescritor. */
    private PractitionerRole montarEspecialidade(Prescricao p) {
        PractitionerRole role = new PractitionerRole();
        role.setId(ID_ESPECIALIDADE);
        role.setMeta(new Meta().addProfile(PERFIL_LOTACAO));
        role.setActive(true);
        role.setPractitioner(referenciaPorIdentificador(SYSTEM_PROFISSIONAL, p.getCnsPrescritor()));
        role.setOrganization(referenciaPorIdentificador(SYSTEM_ESTABELECIMENTO, p.getCnesLotacaoPrescritor()));
        role.addCode(conceito(p.getCboPrescritor()));
        return role;
    }

    /** {@code contained:pharmacist-crf} — o CRF de quem dispensou. */
    private Practitioner montarFarmaceutico(EnrichedRegistroDispensacaoMedicamento dto) {
        Practitioner f = new Practitioner();
        f.setId(ID_FARMACEUTICO);
        f.setMeta(new Meta().addProfile(PERFIL_PROFISSIONAL));
        f.addIdentifier(new Identifier()
                .setUse(Identifier.IdentifierUse.OFFICIAL)
                .setType(conceito(TIPO_ID_FARMACEUTICO))
                .setSystem(SID + dto.getConselhoFarmaceutico())
                .setValue(dto.getInscricaoFarmaceutico()));
        f.setActive(true);
        return f;
    }

    // ══ Medication ══════════════════════════════════════════════════════

    private Medication montarMedication(EnrichedRegistroDispensacaoMedicamento dto) {
        Medication m = new Medication();
        m.setMeta(new Meta().addProfile(PERFIL_MEDICATION));
        m.setCode(conceitoComDisplay(dto.getMedicamento(), dto.getDescricaoMedicamento()));
        if (dto.getCnpjFabricante() != null) {
            m.setManufacturer(referenciaPorIdentificador(SYSTEM_PESSOA_JURIDICA, dto.getCnpjFabricante()));
        }
        if (dto.getFormaFarmaceutica() != null) {
            m.setForm(conceito(dto.getFormaFarmaceutica()));
        }
        if (dto.getLote() != null || dto.getValidade() != null) {
            Medication.MedicationBatchComponent b = m.getBatch();
            if (dto.getLote() != null) {
                b.setLotNumber(dto.getLote());
            }
            if (dto.getValidade() != null) {
                // batch.expirationDate e dateTime no FHIR base, nao date.
                b.setExpirationDateElement(new DateTimeType(dto.getValidade().toString()));
            }
        }
        return m;
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

    private static CodeableConcept conceitoComDisplay(CodedValue v, String display) {
        CodeableConcept cc = new CodeableConcept();
        cc.addCoding().setSystem(v.system()).setCode(v.code()).setDisplay(display);
        return cc;
    }

    private static Reference referenciaPorIdentificador(String system, String value) {
        return new Reference().setIdentifier(new Identifier().setSystem(system).setValue(value));
    }

    private static void exigirObrigatorios(EnrichedRegistroDispensacaoMedicamento dto) {
        Objects.requireNonNull(dto, "dto não pode ser nulo");
        exigir(dto.getIdLocalRegistro(), "idLocalRegistro (Bundle.identifier.value)");
        exigir(dto.getIdItemDispensado(), "idItemDispensado (MedicationDispense.identifier 1..1)");
        exigir(dto.getCnesEstabelecimento(), "cnesEstabelecimento (Composition.author)");
        exigir(dto.getDataRegistroOrigem(), "dataRegistroOrigem (Composition.date)");
        exigir(dto.getDataEntrega(), "dataEntrega (MedicationDispense.whenHandedOver 1..1)");
        exigir(dto.getMedicamento(), "medicamento (Medication.code 1..1)");
        exigir(dto.getCategoriaDispensacao(), "categoriaDispensacao (MedicationDispense.category 1..1)");
        exigir(dto.getTipoDispensacao(), "tipoDispensacao (MedicationDispense.type 1..1)");
        exigir(dto.getQuantidade(), "quantidade (MedicationDispense.quantity 1..1)");
        exigir(dto.getCnsFarmaceutico(), "cnsFarmaceutico (performer:pharmacist)");
        exigir(dto.getCboFarmaceutico(), "cboFarmaceutico (performer:pharmacist.function)");
        exigir(dto.getConselhoFarmaceutico(), "conselhoFarmaceutico (contained:pharmacist-crf)");
        exigir(dto.getInscricaoFarmaceutico(), "inscricaoFarmaceutico (contained:pharmacist-crf)");
        dto.identificadorIndividuo();

        boolean naRnds = dto.getIdPrescricaoNaRnds() != null
                && !dto.getIdPrescricaoNaRnds().isBlank();
        if (naRnds == dto.prescricaoEmbutida()) {
            throw new IllegalArgumentException(
                    "Campo obrigatório ausente para o perfil do REDFM: informe o identificador da "
                            + "prescrição na RNDS OU os dados da receita, nunca os dois nem nenhum. "
                            + "As fatias authorizingPrescription:inRNDS e :outOfRNDS são mutuamente "
                            + "exclusivas — ou a receita está na base nacional, ou vem embutida.");
        }
        if (dto.prescricaoEmbutida()) {
            Prescricao p = dto.getPrescricao();
            exigir(p.getIdItemPrescrito(), "prescricao.idItemPrescrito");
            exigir(p.getDataPrescricao(), "prescricao.dataPrescricao");
            exigir(p.getCnsPrescritor(), "prescricao.cnsPrescritor");
            exigir(p.getConselhoPrescritor(), "prescricao.conselhoPrescritor");
            exigir(p.getInscricaoPrescritor(), "prescricao.inscricaoPrescritor");
            exigir(p.getCboPrescritor(), "prescricao.cboPrescritor");
            exigir(p.getCnesLotacaoPrescritor(), "prescricao.cnesLotacaoPrescritor");
            exigir(p.getViaAdministracao(), "prescricao.viaAdministracao");
            exigir(p.getDose(), "prescricao.dose");
        }
    }

    private static void exigir(Object valor, String campo) {
        if (valor == null || (valor instanceof String s && s.isBlank())) {
            throw new IllegalArgumentException("Campo obrigatório ausente para o perfil do REDFM: " + campo);
        }
    }
}
