package br.rndsconnect.builder;

import br.rndsconnect.model.CodedValue;
import br.rndsconnect.model.EnrichedRegistroImunizacao;
import br.rndsconnect.model.PesquisaClinica;
import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;

/**
 * Camada 3 — construtor do Bundle do serviço RIA-R (imunização em rotina).
 *
 * <p>Produz um Bundle {@code document} com dois ou três resources, conforme o perfil
 * {@code BRRegistroImunobiologicoAdministradoRotina-2.0}:
 *
 * <ul>
 *   <li>{@code Composition} — sempre;
 *   <li>{@code Immunization} ({@code BRImunobiologicoAdministrado-3.0}) — sempre;
 *   <li>{@code Condition} ({@code BRCID10Avaliado-1.0}) — quando há CID informado.
 * </ul>
 *
 * <p>O {@code Condition} <strong>não</strong> é referenciado pela {@code Composition}:
 * {@code Composition.section} é {@code 1..1} e sua única {@code entry} aponta para o
 * {@code Immunization}. O vínculo com o {@code Condition} é
 * {@code Immunization.reasonReference}.
 *
 * <p><strong>Toda referência interna usa {@code urn:uuid} com UUID da RFC 4122</strong>
 * (ADR-07). Os exemplos oficiais usam {@code urn:uuid:transient-N}, que não é UUID válido e
 * é reprovado pelo próprio validador do Ministério da Saúde — ver Arquitetura §8.1.2, defeito nº 6.
 *
 * <p>O gerador de UUID é injetável para permitir construção determinística nos testes. Em
 * produção usa-se o construtor de um argumento, que sorteia UUID v4.
 *
 * @author Igor Mendes Domingues Miras
 */
public class RiaBundleBuilder implements FhirBundleBuilder<EnrichedRegistroImunizacao> {

    private static final String SD = "http://www.saude.gov.br/fhir/r4/StructureDefinition/";
    private static final String CS = "http://www.saude.gov.br/fhir/r4/CodeSystem/";
    private static final String NAMING_SYSTEM = "http://www.saude.gov.br/fhir/r4/NamingSystem/BRRNDS-";

    // ── Perfis ──────────────────────────────────────────────────────────
    private static final String PERFIL_COMPOSITION = SD + "BRRegistroImunobiologicoAdministradoRotina-2.0";
    private static final String PERFIL_IMMUNIZATION = SD + "BRImunobiologicoAdministrado-3.0";
    private static final String PERFIL_CONDITION = SD + "BRCID10Avaliado-1.0";

    // ── Famílias de URL em identifier.system (§8.1.3) ────────────────────
    private static final String SYSTEM_INDIVIDUO = SD + "BRIndividuo-1.0";
    private static final String SYSTEM_ESTABELECIMENTO = SD + "BREstabelecimentoSaude-1.0";
    /** Exceção do perfil: {@code fixedUri} para um CodeSystem, não para um StructureDefinition. */
    private static final String SYSTEM_FABRICANTE = CS + "BRFabricantePNI";
    /** Idem — {@code Immunization.location.identifier.system} é {@code fixedUri}. */
    private static final String SYSTEM_PAIS = CS + "BRPais";

    // ── Extensões. Três SEM sufixo de versão, duas COM (§8.2) ────────────
    private static final String EXT_GRUPO_ATENDIMENTO = SD + "BRGrupoAtendimento";
    private static final String EXT_CONDICAO_MATERNAL = SD + "BRCondicaoMaternal";
    private static final String EXT_CONTATO_HANSENIASE = SD + "BRContatoHanseniase";
    private static final String EXT_ESTRATEGIA = SD + "BREstrategiaVacinacao-1.0";
    private static final String EXT_ESTRATEGIA_PESQUISA = SD + "BREstrategiaVacinacaoPesquisa-1.0";

    // ── Constantes que o Builder fixa em vez de mapear (§7.3.2) ──────────
    /** {@code fixedString} do perfil. Grafia sem acento, exatamente como publicada. */
    private static final String TITULO = "Registro de Imunobiologico Administrado na Rotina";

    private static final CodedValue TIPO_DOCUMENTO = CodedValue.rnds("BRTipoDocumento", "RIA");
    private static final CodedValue CATEGORIA_DIAGNOSTICO = CodedValue.rnds("BRCategoriaDiagnostico", "01");
    private static final CodedValue CONDITION_ATIVA =
            new CodedValue("http://terminology.hl7.org/CodeSystem/condition-clinical", "active");

    private final String solicitanteId;
    private final Supplier<String> geradorUuid;
    private final Supplier<Instant> relogio;

    /**
     * @param solicitanteId Identificador Solicitante fornecido pela RNDS no credenciamento;
     *     compõe o {@code Bundle.identifier.system} de todo envio (§8.1.1)
     */
    public RiaBundleBuilder(String solicitanteId) {
        this(solicitanteId, () -> UUID.randomUUID().toString(), Instant::now);
    }

    /**
     * @param solicitanteId Identificador Solicitante
     * @param geradorUuid fonte dos UUIDs transientes — injetável para testes determinísticos
     * @param relogio instante de montagem do Bundle — idem
     */
    public RiaBundleBuilder(String solicitanteId, Supplier<String> geradorUuid, Supplier<Instant> relogio) {
        this.solicitanteId = Objects.requireNonNull(solicitanteId, "solicitanteId é obrigatório");
        this.geradorUuid = Objects.requireNonNull(geradorUuid, "geradorUuid é obrigatório");
        this.relogio = Objects.requireNonNull(relogio, "relogio é obrigatório");
    }

    @Override
    public Bundle build(EnrichedRegistroImunizacao dto) {
        exigirObrigatorios(dto);

        String uuidComposition = geradorUuid.get();
        String uuidImmunization = geradorUuid.get();
        String uuidCondition = dto.getCid10() != null ? geradorUuid.get() : null;

        // Bundle.timestamp e meta.lastUpdated são o instante em que o DOCUMENTO foi montado —
        // não o instante do registro clínico. O exemplo oficial os distingue: timestamp
        // 14:59:51 e Composition.date 08:18:30. Confundi-los apaga informação real.
        Instant montagem = relogio.get();

        Bundle bundle = new Bundle();
        bundle.setMeta(new Meta().setLastUpdated(Date.from(montagem)));
        bundle.setIdentifier(
                new Identifier().setSystem(NAMING_SYSTEM + solicitanteId).setValue(dto.getIdLocalRegistro()));
        bundle.setType(Bundle.BundleType.DOCUMENT);
        bundle.setTimestamp(Date.from(montagem));

        adicionar(bundle, uuidComposition, montarComposition(dto, uuidComposition, uuidImmunization));
        adicionar(bundle, uuidImmunization, montarImmunization(dto, uuidImmunization, uuidCondition));
        if (uuidCondition != null) {
            adicionar(bundle, uuidCondition, montarCondition(dto, uuidCondition));
        }
        return bundle;
    }

    // ══ Composition ═════════════════════════════════════════════════════

    private Composition montarComposition(EnrichedRegistroImunizacao dto, String uuidProprio, String uuidImmunization) {
        Composition c = new Composition();
        c.setMeta(new Meta().addProfile(PERFIL_COMPOSITION));
        c.setStatus(Composition.CompositionStatus.FINAL);
        c.setType(conceito(TIPO_DOCUMENTO));
        c.setSubject(referenciaPorIdentificador(SYSTEM_INDIVIDUO, dto.identificadorIndividuo()));
        c.setDate(Date.from(dto.getDataRegistroOrigem()));
        c.addAuthor(referenciaPorIdentificador(SYSTEM_ESTABELECIMENTO, dto.getCnesEstabelecimento()));
        c.setTitle(TITULO);
        // section é 1..1 e section.entry é 1..1: aponta para o Immunization, nunca para o Condition.
        c.addSection().addEntry(new Reference("urn:uuid:" + uuidImmunization));
        return c;
    }

    // ══ Immunization ════════════════════════════════════════════════════

    private Immunization montarImmunization(EnrichedRegistroImunizacao dto, String uuidProprio, String uuidCondition) {
        Immunization i = new Immunization();
        i.setMeta(new Meta().addProfile(PERFIL_IMMUNIZATION));

        // Immunization.extension é 2..3: as duas primeiras são 1..1.
        i.addExtension(extensaoConceito(EXT_GRUPO_ATENDIMENTO, dto.getGrupoAtendimento()));
        i.addExtension(extensaoConceito(EXT_CONDICAO_MATERNAL, dto.getCondicaoMaternal()));
        if (dto.getContatoHanseniase() != null) {
            i.addExtension(new Extension(EXT_CONTATO_HANSENIASE, new BooleanType(dto.getContatoHanseniase())));
        }

        i.setStatus(Immunization.ImmunizationStatus.COMPLETED); // fixedCode no perfil
        i.setVaccineCode(conceito(dto.getVacina()));
        i.setPatient(referenciaPorIdentificador(SYSTEM_INDIVIDUO, dto.identificadorIndividuo()));
        i.setOccurrence(new DateTimeType(dto.getDataAplicacao().toString()));

        if (dto.getPrimarySource() != null) {
            i.setPrimarySource(dto.getPrimarySource());
        }
        if (dto.getRegistroOrigem() != null) {
            i.setReportOrigin(conceito(dto.getRegistroOrigem()));
        }
        if (dto.getCodigoPaisOrigem() != null) {
            i.setLocation(referenciaPorIdentificador(SYSTEM_PAIS, dto.getCodigoPaisOrigem()));
        }
        if (dto.getCodigoFabricante() != null) {
            i.setManufacturer(referenciaPorIdentificador(SYSTEM_FABRICANTE, dto.getCodigoFabricante()));
        }
        if (dto.getLote() != null) {
            i.setLotNumber(dto.getLote());
        }
        if (dto.getValidadeLote() != null) {
            i.setExpirationDateElement(new DateType(dto.getValidadeLote().toString()));
        }

        i.setSite(conceito(dto.getLocalAplicacao()));
        i.setRoute(conceito(dto.getViaAdministracao()));

        // performer é 1..1; function é 0..1 mas, se presente, function.coding é 1..1.
        Immunization.ImmunizationPerformerComponent performer = i.addPerformer();
        if (dto.getCboPrescritor() != null) {
            performer.setFunction(conceito(dto.getCboPrescritor()));
        }
        performer.setActor(new Reference("Practitioner/" + dto.getCnsProfissional()));

        if (uuidCondition != null) {
            i.addReasonReference(new Reference("urn:uuid:" + uuidCondition));
        }

        // protocolApplied é 1..1; sua extension é 1..2, com strategy 1..1.
        Immunization.ImmunizationProtocolAppliedComponent protocolo = i.addProtocolApplied();
        protocolo.addExtension(extensaoConceito(EXT_ESTRATEGIA, dto.getEstrategiaVacinacao()));
        if (dto.getPesquisaClinica() != null) {
            protocolo.addExtension(extensaoPesquisa(dto.getPesquisaClinica()));
        }
        protocolo.setDoseNumber(new StringType(dto.getDoseNumber()));
        return i;
    }

    /**
     * Extensão complexa da pesquisa clínica.
     *
     * <p>As URLs das sub-extensões são relativas e a caixa é inconsistente no artefato
     * oficial — {@code ANVISA} na primeira, {@code Anvisa} na terceira. O Builder emite as
     * URLs literais.
     */
    private Extension extensaoPesquisa(PesquisaClinica p) {
        Extension e = new Extension(EXT_ESTRATEGIA_PESQUISA);
        if (p.getNumeroProtocoloEstudoAnvisa() != null) {
            e.addExtension("numeroProtocoloEstudoANVISA", new StringType(p.getNumeroProtocoloEstudoAnvisa()));
        }
        if (p.getNumeroVersaoProtocoloEstudo() != null) {
            e.addExtension("numeroVersaoProtocoloEstudo", new StringType(p.getNumeroVersaoProtocoloEstudo()));
        }
        if (p.getNumeroRegistroVacinaAnvisa() != null) {
            e.addExtension("numeroRegistroVacinaAnvisa", new StringType(p.getNumeroRegistroVacinaAnvisa()));
        }
        return e;
    }

    // ══ Condition ═══════════════════════════════════════════════════════

    private Condition montarCondition(EnrichedRegistroImunizacao dto, String uuidProprio) {
        Condition c = new Condition();
        c.setMeta(new Meta().addProfile(PERFIL_CONDITION));
        // Constantes do Builder, não domínios terminológicos (§7.3.2).
        c.setClinicalStatus(conceito(CONDITION_ATIVA));
        c.addCategory(conceito(CATEGORIA_DIAGNOSTICO));
        c.setCode(conceito(dto.getCid10()));
        c.setSubject(referenciaPorIdentificador(SYSTEM_INDIVIDUO, dto.identificadorIndividuo()));
        if (dto.getDataInicioCondicao() != null) {
            // Precisão de milissegundos: é a que o exemplo oficial usa em onsetDateTime.
            c.setOnset(new DateTimeType(Date.from(dto.getDataInicioCondicao()), TemporalPrecisionEnum.MILLI));
        }
        if (dto.getObservacaoCondicao() != null && !dto.getObservacaoCondicao().isBlank()) {
            c.addNote(new org.hl7.fhir.r4.model.Annotation().setText(dto.getObservacaoCondicao()));
        }
        return c;
    }

    // ══ Auxiliares ══════════════════════════════════════════════════════

    private static void adicionar(Bundle bundle, String uuid, org.hl7.fhir.r4.model.Resource resource) {
        bundle.addEntry().setFullUrl("urn:uuid:" + uuid).setResource(resource);
    }

    /**
     * O perfil proíbe {@code coding.display} e {@code CodeableConcept.text} em praticamente
     * todos os elementos codificados do RIA-R. Emitir apenas {@code system} e {@code code}
     * não é economia — é conformidade.
     */
    private static CodeableConcept conceito(CodedValue v) {
        CodeableConcept cc = new CodeableConcept();
        cc.addCoding().setSystem(v.system()).setCode(v.code());
        return cc;
    }

    private static Extension extensaoConceito(String url, CodedValue v) {
        return new Extension(url, conceito(v));
    }

    private static Reference referenciaPorIdentificador(String system, String value) {
        return new Reference().setIdentifier(new Identifier().setSystem(system).setValue(value));
    }

    /**
     * Confere apenas o que o perfil exige <strong>estruturalmente</strong>. Regras
     * condicionais do Modelo de Informação pertencem à Frente A da Camada 2.
     */
    private static void exigirObrigatorios(EnrichedRegistroImunizacao dto) {
        Objects.requireNonNull(dto, "dto não pode ser nulo");
        exigir(dto.getIdLocalRegistro(), "idLocalRegistro (Bundle.identifier.value)");
        exigir(dto.getCnesEstabelecimento(), "cnesEstabelecimento (Composition.author)");
        exigir(dto.getCnsProfissional(), "cnsProfissional (Immunization.performer.actor)");
        exigir(dto.getDataRegistroOrigem(), "dataRegistroOrigem (Composition.date)");
        exigir(dto.getGrupoAtendimento(), "grupoAtendimento (extensão 1..1)");
        exigir(dto.getCondicaoMaternal(), "condicaoMaternal (extensão 1..1)");
        exigir(dto.getEstrategiaVacinacao(), "estrategiaVacinacao (protocolApplied.extension:strategy 1..1)");
        exigir(dto.getVacina(), "vacina (Immunization.vaccineCode 1..1)");
        exigir(dto.getLocalAplicacao(), "localAplicacao (Immunization.site 1..1)");
        exigir(dto.getViaAdministracao(), "viaAdministracao (Immunization.route 1..1)");
        exigir(dto.getDoseNumber(), "doseNumber (protocolApplied.doseNumber 1..1)");
        exigir(dto.getDataAplicacao(), "dataAplicacao (Immunization.occurrence 1..1)");
        dto.identificadorIndividuo(); // lança se CNS e CPF estiverem ausentes
    }

    private static void exigir(Object valor, String campo) {
        if (valor == null || (valor instanceof String s && s.isBlank())) {
            throw new IllegalArgumentException("Campo obrigatório ausente para o perfil do RIA-R: " + campo);
        }
    }
}
