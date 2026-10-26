package br.rndsconnect.builder;

import br.rndsconnect.model.CodedValue;
import br.rndsconnect.model.EnrichedRegistroExameLaboratorial;
import br.rndsconnect.model.EnrichedRegistroExameLaboratorial.ExameLaboratorial;
import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Specimen;

/**
 * Camada 3 — construtor do Bundle do serviço REL (resultado de exame laboratorial).
 *
 * <p>É o mais complexo dos três Builders, e o único com <strong>cardinalidade variável</strong>:
 * produz {@code 2 + 2 × N} resources para {@code N} exames.
 *
 * <pre>
 * Composition ──section.entry:condicaoSaude──────────► Condition
 *      │                                                   │
 *      └──section.entry:diagnosticoLaboratorioClinico──┐   │ stage.assessment
 *                                                       ▼   ▼
 *                                                   Observation ──specimen──► Specimen
 * </pre>
 *
 * <p>O vínculo é <strong>bidirecional</strong>: a {@code Composition} referencia
 * {@code Condition} e {@code Observation}, e a {@code Condition} referencia de volta todos os
 * {@code Observation} por {@code stage.assessment}. Nenhum dos outros dois serviços tem essa
 * topologia.
 *
 * <h2>Três particularidades do perfil</h2>
 *
 * <p><strong>1. Campos obrigatórios que só aceitam texto livre.</strong>
 * {@code Observation.method} e {@code Observation.referenceRange} são {@code 1..1}, e o perfil
 * proíbe todos os seus sub-elementos estruturados. Restam {@code method.text} e
 * {@code referenceRange.text}. São exigidos e não podem ser codificados.
 *
 * <p><strong>2. Dois ValueSets parecidos e distintos.</strong> {@code Observation.value[x]}
 * usa {@code BRResultadoQualitativoExame-2.0}; {@code Observation.interpretation} usa o
 * <strong>1.0</strong>, com apenas três códigos. Trocá-los produz erro de binding difícil de
 * diagnosticar.
 *
 * <p><strong>3. A unidade de medida não pode ser codificada.</strong>
 * {@code Observation.value[x]} admite {@code Quantity} ou {@code CodeableConcept}, com binding
 * {@code required} ao {@code BRResultadoQualitativoExame-2.0}. No FHIR, binding sobre
 * {@code Quantity} recai sobre {@code Quantity.code} — a unidade. O perfil, portanto, exige que
 * a <em>unidade de medida</em> seja um código de <em>resultado qualitativo</em>, o que expulsa o
 * UCUM do único campo em que ele caberia.
 *
 * <p>Verificado por experimento (ver {@code SondaValueQuantityTest}): {@code Quantity} com
 * {@code system} e {@code code} UCUM é <strong>reprovado</strong>; com {@code unit} textual e
 * sem os dois, é <strong>aprovado</strong>. O Builder emite a segunda forma. A consequência é
 * que a unidade chega legível para humano e não processável por máquina — e que o
 * {@code LoincUcumCrossValidator} previsto para a Frente A não tem objeto neste perfil, porque
 * não há unidade UCUM a cruzar.
 *
 * <p><strong>4. O {@code Specimen} é quase vazio.</strong> O perfil
 * {@code BRAmostraBiologica-1.0} proíbe tudo exceto {@code type} — sem {@code subject}, sem
 * {@code collection}, sem {@code status}. Isso torna trivial construir o resource que o
 * exemplo oficial omite (defeito nº 5).
 *
 * <p><strong>Referências internas em {@code urn:uuid}</strong> (ADR-07). O exemplo oficial usa
 * a URL do {@code StructureDefinition} em {@code Observation.specimen} e em
 * {@code Condition.stage.assessment} — referenciando o <em>tipo</em>, não a <em>instância</em>
 * — enquanto usa {@code urn:uuid} em {@code Composition.section.entry}. Duas convenções no
 * mesmo documento; o framework adota apenas a segunda.
 *
 * @author Igor Mendes Domingues Miras
 */
public class RelBundleBuilder implements FhirBundleBuilder<EnrichedRegistroExameLaboratorial> {

    private static final String SD = "http://www.saude.gov.br/fhir/r4/StructureDefinition/";
    private static final String NAMING_SYSTEM = "http://www.saude.gov.br/fhir/r4/NamingSystem/BRRNDS-";

    private static final String PERFIL_COMPOSITION = SD + "BRResultadoExameLaboratorial-3.2.1";
    private static final String PERFIL_CONDITION = SD + "BRCondicaoSaude";
    private static final String PERFIL_OBSERVATION = SD + "BRDiagnosticoLaboratorioClinico-3.2.1";
    private static final String PERFIL_SPECIMEN = SD + "BRAmostraBiologica-1.0";

    private static final String SYSTEM_INDIVIDUO = SD + "BRIndividuo-1.0";
    private static final String SYSTEM_ESTABELECIMENTO = SD + "BREstabelecimentoSaude-1.0";
    private static final String SYSTEM_PESSOA_JURIDICA = SD + "BRPessoaJuridicaProfissionalLiberal-1.0";

    private static final String EXT_PATOGENO = SD + "BRPatogeno";

    /** {@code fixedString} do perfil. */
    private static final String TITULO = "Resultado de Exame Laboratorial";

    private static final CodedValue TIPO_DOCUMENTO = CodedValue.rnds("BRTipoDocumento", "REL");

    private final String solicitanteId;
    private final Supplier<String> geradorUuid;
    private final Supplier<Instant> relogio;

    /**
     * @param solicitanteId Identificador Solicitante fornecido pela RNDS no credenciamento
     */
    public RelBundleBuilder(String solicitanteId) {
        this(solicitanteId, () -> UUID.randomUUID().toString(), Instant::now);
    }

    /**
     * @param solicitanteId Identificador Solicitante
     * @param geradorUuid fonte dos UUIDs transientes — injetável para testes determinísticos
     * @param relogio instante de montagem do Bundle — idem
     */
    public RelBundleBuilder(String solicitanteId, Supplier<String> geradorUuid, Supplier<Instant> relogio) {
        this.solicitanteId = Objects.requireNonNull(solicitanteId, "solicitanteId é obrigatório");
        this.geradorUuid = Objects.requireNonNull(geradorUuid, "geradorUuid é obrigatório");
        this.relogio = Objects.requireNonNull(relogio, "relogio é obrigatório");
    }

    @Override
    public Bundle build(EnrichedRegistroExameLaboratorial dto) {
        exigirObrigatorios(dto);

        // Todos os UUIDs são sorteados antes da montagem: a Composition precisa dos UUIDs
        // do Condition e dos Observation, e o Condition precisa dos dos Observation.
        String uuidComposition = geradorUuid.get();
        String uuidCondition = geradorUuid.get();
        List<String> uuidObservacoes = new ArrayList<>();
        List<String> uuidAmostras = new ArrayList<>();
        for (int i = 0; i < dto.getExames().size(); i++) {
            uuidObservacoes.add(geradorUuid.get());
            uuidAmostras.add(geradorUuid.get());
        }

        Instant montagem = relogio.get();

        Bundle bundle = new Bundle();
        bundle.setMeta(new Meta().setLastUpdated(Date.from(montagem)));
        bundle.setIdentifier(
                new Identifier().setSystem(NAMING_SYSTEM + solicitanteId).setValue(dto.getIdLocalRegistro()));
        bundle.setType(Bundle.BundleType.DOCUMENT);
        bundle.setTimestamp(Date.from(montagem));

        adicionar(bundle, uuidComposition, montarComposition(dto, uuidCondition, uuidObservacoes));
        adicionar(bundle, uuidCondition, montarCondition(dto, uuidObservacoes));
        for (int i = 0; i < dto.getExames().size(); i++) {
            ExameLaboratorial exame = dto.getExames().get(i);
            adicionar(bundle, uuidObservacoes.get(i), montarObservation(dto, exame, uuidAmostras.get(i)));
            adicionar(bundle, uuidAmostras.get(i), montarSpecimen(exame));
        }
        return bundle;
    }

    // ══ Composition ═════════════════════════════════════════════════════

    private Composition montarComposition(
            EnrichedRegistroExameLaboratorial dto, String uuidCondition, List<String> uuidObservacoes) {
        Composition c = new Composition();
        c.setMeta(new Meta().addProfile(PERFIL_COMPOSITION));
        c.setStatus(Composition.CompositionStatus.FINAL);
        c.setType(conceito(TIPO_DOCUMENTO));
        c.setSubject(referenciaPorIdentificador(SYSTEM_INDIVIDUO, dto.identificadorIndividuo()));
        c.setDate(Date.from(dto.getDataRegistroOrigem()));
        // Diferente do RIA: o autor do laudo é a pessoa jurídica, não o estabelecimento.
        c.addAuthor(referenciaPorIdentificador(SYSTEM_PESSOA_JURIDICA, dto.getCnpjLaboratorio()));
        c.setTitle(TITULO);

        // section é 1..1; suas entries são fatiadas em condicaoSaude (1..1) e
        // diagnosticoLaboratorioClinico (1..*). A ordem segue o exemplo oficial.
        Composition.SectionComponent secao = c.addSection();
        secao.addEntry(new Reference("urn:uuid:" + uuidCondition));
        uuidObservacoes.forEach(u -> secao.addEntry(new Reference("urn:uuid:" + u)));
        return c;
    }

    // ══ Condition ═══════════════════════════════════════════════════════

    private Condition montarCondition(EnrichedRegistroExameLaboratorial dto, List<String> uuidObservacoes) {
        Condition c = new Condition();
        c.setMeta(new Meta().addProfile(PERFIL_CONDITION));
        // clinicalStatus é PROIBIDO aqui — e obrigatório no BRCID10Avaliado-1.0 do RIA-R.
        c.setCode(conceito(dto.getSuspeitaDiagnostica()));
        c.setSubject(referenciaPorIdentificador(SYSTEM_INDIVIDUO, dto.identificadorIndividuo()));

        // stage é 1..1 e stage.assessment é 1..*: aponta de volta para TODOS os exames.
        Condition.ConditionStageComponent estagio = c.addStage();
        uuidObservacoes.forEach(u -> estagio.addAssessment(new Reference("urn:uuid:" + u)));
        return c;
    }

    // ══ Observation ═════════════════════════════════════════════════════

    private Observation montarObservation(
            EnrichedRegistroExameLaboratorial dto, ExameLaboratorial exame, String uuidAmostra) {
        Observation o = new Observation();
        o.setMeta(new Meta().addProfile(PERFIL_OBSERVATION));

        if (exame.getPatogeno() != null) {
            o.addExtension(new Extension(EXT_PATOGENO, conceito(exame.getPatogeno())));
        }

        o.setStatus(Observation.ObservationStatus.FINAL);
        o.addCategory(conceito(exame.getCategoriaExame()));
        o.setCode(conceito(exame.getNomeExame()));
        o.setSubject(referenciaPorIdentificador(SYSTEM_INDIVIDUO, dto.identificadorIndividuo()));
        o.setEffective(new DateTimeType(Date.from(exame.getDataColeta()), TemporalPrecisionEnum.SECOND));
        o.setIssuedElement(new InstantType(Date.from(exame.getDataLiberacaoLaudo()), TemporalPrecisionEnum.SECOND));

        // performer é fatiado: estabelecimentoSaude e pessoaJuridicaProfissionalLiberal.
        o.addPerformer(referenciaPorIdentificador(SYSTEM_ESTABELECIMENTO, dto.getCnesEstabelecimento()));
        o.addPerformer(referenciaPorIdentificador(SYSTEM_PESSOA_JURIDICA, dto.getCnpjLaboratorio()));

        // value[x] é 1..1 e aceita Quantity OU CodeableConcept — nunca os dois.
        if (exame.isQuantitativo()) {
            // A unidade vai APENAS em Quantity.unit, como texto. Emitir system e code
            // reprova: o binding required de value[x] recai sobre Quantity.code, e ali o
            // perfil exige um código de RESULTADO QUALITATIVO. Ver Javadoc da classe.
            o.setValue(new Quantity().setValue(exame.getResultadoQuantitativo()).setUnit(exame.getUnidadeMedida()));
        } else {
            o.setValue(conceito(exame.getResultadoQualitativo()));
        }

        if (exame.getInterpretacao() != null) {
            // ValueSet DISTINTO do resultado: BRResultadoQualitativoExame-1.0.
            o.addInterpretation(conceito(exame.getInterpretacao()));
        }

        // method e referenceRange: obrigatórios e só aceitam texto (ver Javadoc da classe).
        o.setMethod(new CodeableConcept().setText(exame.getMetodo()));
        o.addReferenceRange().setText(exame.getValorReferencia());
        if (exame.getObservacao() != null && !exame.getObservacao().isBlank()) {
            // note.author[x] e note.time sao max=0 no perfil: so o texto passa.
            o.addNote(new org.hl7.fhir.r4.model.Annotation().setText(exame.getObservacao()));
        }

        o.setSpecimen(new Reference("urn:uuid:" + uuidAmostra));
        return o;
    }

    // ══ Specimen ════════════════════════════════════════════════════════

    /**
     * O resource que o exemplo oficial não fornece (defeito nº 5).
     *
     * <p>O perfil proíbe {@code identifier}, {@code accessionIdentifier}, {@code status},
     * {@code subject}, {@code receivedTime}, {@code parent}, {@code request},
     * {@code collection}, {@code processing}, {@code container}, {@code condition} e
     * {@code note}. Sobra {@code type}, e só.
     */
    private Specimen montarSpecimen(ExameLaboratorial exame) {
        Specimen s = new Specimen();
        s.setMeta(new Meta().addProfile(PERFIL_SPECIMEN));
        s.setType(conceito(exame.getTipoAmostra()));
        return s;
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

    private static Reference referenciaPorIdentificador(String system, String value) {
        return new Reference().setIdentifier(new Identifier().setSystem(system).setValue(value));
    }

    private static void exigirObrigatorios(EnrichedRegistroExameLaboratorial dto) {
        Objects.requireNonNull(dto, "dto não pode ser nulo");
        exigir(dto.getIdLocalRegistro(), "idLocalRegistro (Bundle.identifier.value)");
        exigir(dto.getCnpjLaboratorio(), "cnpjLaboratorio (Composition.author)");
        exigir(dto.getCnesEstabelecimento(), "cnesEstabelecimento (Observation.performer:estabelecimentoSaude)");
        exigir(dto.getDataRegistroOrigem(), "dataRegistroOrigem (Composition.date)");
        exigir(dto.getSuspeitaDiagnostica(), "suspeitaDiagnostica (Condition.code 1..1)");
        dto.identificadorIndividuo();

        if (dto.getExames() == null || dto.getExames().isEmpty()) {
            throw new IllegalArgumentException("Campo obrigatório ausente para o perfil do REL: pelo menos um exame "
                    + "(Composition.section.entry:diagnosticoLaboratorioClinico é 1..*).");
        }
        for (int i = 0; i < dto.getExames().size(); i++) {
            exigirExame(dto.getExames().get(i), i);
        }
    }

    private static void exigirExame(ExameLaboratorial e, int indice) {
        String onde = "exame[" + indice + "].";
        exigir(e.getNomeExame(), onde + "nomeExame (Observation.code 1..1)");
        exigir(e.getCategoriaExame(), onde + "categoriaExame (Observation.category 1..1)");
        exigir(e.getTipoAmostra(), onde + "tipoAmostra (Specimen.type 1..1)");
        exigir(e.getDataColeta(), onde + "dataColeta (Observation.effective 1..1)");
        exigir(e.getDataLiberacaoLaudo(), onde + "dataLiberacaoLaudo (Observation.issued 1..1)");
        exigir(e.getMetodo(), onde + "metodo (Observation.method 1..1 — texto livre)");
        exigir(e.getValorReferencia(), onde + "valorReferencia (Observation.referenceRange 1..1 — texto livre)");

        // value[x] é 1..1: exatamente um dos dois. A exclusividade é o elo 4 da Frente A,
        // que ainda não existe; enquanto isso, o Builder a impõe estruturalmente.
        boolean temQualitativo = e.getResultadoQualitativo() != null;
        boolean temQuantitativo = e.getResultadoQuantitativo() != null;
        if (temQualitativo == temQuantitativo) {
            throw new IllegalArgumentException("Campo obrigatório ausente para o perfil do REL: " + onde
                    + "resultado — Observation.value[x] é 1..1 e aceita Quantity OU CodeableConcept. "
                    + (temQualitativo ? "Foram informados os dois; informe apenas um." : "Nenhum foi informado."));
        }
        if (temQuantitativo) {
            exigir(
                    e.getUnidadeMedida(),
                    onde + "unidadeMedida — obrigatória para resultado quantitativo; um número "
                            + "sem unidade não tem significado clínico");
        }
    }

    private static void exigir(Object valor, String campo) {
        if (valor == null || (valor instanceof String s && s.isBlank())) {
            throw new IllegalArgumentException("Campo obrigatório ausente para o perfil do REL: " + campo);
        }
    }
}
