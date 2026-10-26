package br.rndsconnect.builder;

import br.rndsconnect.model.CodedValue;
import br.rndsconnect.model.EnrichedRegistroImunizacaoCampanha;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;

/**
 * Camada 3 — construtor do Bundle do serviço RIA-C (imunização em campanha).
 *
 * <p>Produz um Bundle {@code document} com exatamente <strong>dois</strong> resources:
 * {@code Composition} e {@code Immunization}. Não há {@code Condition} — o perfil proíbe
 * {@code Immunization.reasonReference}.
 *
 * <h2>Por que este Builder não herda do {@link RiaBundleBuilder} (ADR-06)</h2>
 *
 * <p>A tentação de herdar é forte: os dois serviços registram imunização, compartilham o
 * envelope e boa parte dos campos. A comparação dos perfis mostra por que seria um erro —
 * e não apenas um erro de estilo.
 *
 * <p>O perfil do RIA-C declara {@code max=0} para elementos que o perfil do RIA-R declara
 * {@code 1..1}:
 *
 * <table border="1">
 *   <caption>Inversões entre os dois perfis</caption>
 *   <tr><th>Elemento</th><th>RIA-R</th><th>RIA-C</th></tr>
 *   <tr><td>{@code Immunization.site}</td><td>1..1 obrigatório</td><td><strong>proibido</strong></td></tr>
 *   <tr><td>{@code Immunization.route}</td><td>1..1 obrigatório</td><td><strong>proibido</strong></td></tr>
 *   <tr><td>{@code performer.function}</td><td>0..1 permitido</td><td><strong>proibido</strong></td></tr>
 *   <tr><td>{@code reportOrigin}, {@code primarySource}</td><td>permitidos</td><td><strong>proibidos</strong></td></tr>
 *   <tr><td>{@code location}, {@code expirationDate}</td><td>permitidos</td><td><strong>proibidos</strong></td></tr>
 *   <tr><td>{@code reasonReference}</td><td>0..1 permitido</td><td><strong>proibido</strong></td></tr>
 *   <tr><td>extensão de estratégia</td><td>1..1 obrigatória</td><td><strong>não existe</strong></td></tr>
 *   <tr><td>{@code Immunization.extension}</td><td>2..3</td><td>2..2</td></tr>
 *   <tr><td>{@code lotNumber}</td><td>0..1 opcional</td><td><strong>1..1 obrigatório</strong></td></tr>
 *   <tr><td>{@code manufacturer.identifier}</td><td>0..1 opcional</td><td><strong>1..1 obrigatório</strong></td></tr>
 * </table>
 *
 * <p>Um {@code RiaCampanhaBundleBuilder extends RiaBundleBuilder} herdaria a construção de
 * {@code site} e {@code route} — dois elementos que a RNDS <strong>recusa</strong> em
 * campanha. A subclasse teria de <em>desfazer</em> o trabalho da superclasse, que é o sinal
 * clássico de herança mal aplicada. E a relação não é de especialização em nenhuma direção:
 * o RIA-C não é um RIA-R com menos campos, nem o contrário.
 *
 * <p>Este Builder é, portanto, a verificação empírica do ADR-06. A duplicação aparente entre
 * os dois Builders — envelope, {@code Composition}, extensões — é o preço de manter cada
 * perfil verificável isoladamente, e é menor que o custo de uma hierarquia que precisa ser
 * combatida a cada divergência entre os perfis.
 *
 * @author Igor Mendes Domingues Miras
 */
public class RiaCampanhaBundleBuilder implements FhirBundleBuilder<EnrichedRegistroImunizacaoCampanha> {

    private static final String SD = "http://www.saude.gov.br/fhir/r4/StructureDefinition/";
    private static final String CS = "http://www.saude.gov.br/fhir/r4/CodeSystem/";
    private static final String NAMING_SYSTEM = "http://www.saude.gov.br/fhir/r4/NamingSystem/BRRNDS-";

    private static final String PERFIL_COMPOSITION = SD + "BRRegistroImunobiologicoAdministradoCampanha-2.0";
    private static final String PERFIL_IMMUNIZATION = SD + "BRImunobiologicoAdministradoCampanha-2.0";

    private static final String SYSTEM_INDIVIDUO = SD + "BRIndividuo-1.0";
    private static final String SYSTEM_ESTABELECIMENTO = SD + "BREstabelecimentoSaude-1.0";
    private static final String SYSTEM_FABRICANTE = CS + "BRFabricantePNI";

    private static final String EXT_GRUPO_ATENDIMENTO = SD + "BRGrupoAtendimento";
    private static final String EXT_CONDICAO_MATERNAL = SD + "BRCondicaoMaternal";

    /** {@code fixedString} do perfil — difere do título do RIA-R apenas na última palavra. */
    private static final String TITULO = "Registro de Imunobiologico Administrado na Campanha";

    private static final CodedValue TIPO_DOCUMENTO = CodedValue.rnds("BRTipoDocumento", "RIA");

    private final String solicitanteId;
    private final Supplier<String> geradorUuid;
    private final Supplier<Instant> relogio;

    /**
     * @param solicitanteId Identificador Solicitante fornecido pela RNDS no credenciamento
     */
    public RiaCampanhaBundleBuilder(String solicitanteId) {
        this(solicitanteId, () -> UUID.randomUUID().toString(), Instant::now);
    }

    /**
     * @param solicitanteId Identificador Solicitante
     * @param geradorUuid fonte dos UUIDs transientes — injetável para testes determinísticos
     * @param relogio instante de montagem do Bundle — idem
     */
    public RiaCampanhaBundleBuilder(String solicitanteId, Supplier<String> geradorUuid, Supplier<Instant> relogio) {
        this.solicitanteId = Objects.requireNonNull(solicitanteId, "solicitanteId é obrigatório");
        this.geradorUuid = Objects.requireNonNull(geradorUuid, "geradorUuid é obrigatório");
        this.relogio = Objects.requireNonNull(relogio, "relogio é obrigatório");
    }

    @Override
    public Bundle build(EnrichedRegistroImunizacaoCampanha dto) {
        exigirObrigatorios(dto);

        String uuidComposition = geradorUuid.get();
        String uuidImmunization = geradorUuid.get();
        Instant montagem = relogio.get();

        Bundle bundle = new Bundle();
        bundle.setMeta(new Meta().setLastUpdated(Date.from(montagem)));
        bundle.setIdentifier(
                new Identifier().setSystem(NAMING_SYSTEM + solicitanteId).setValue(dto.getIdLocalRegistro()));
        bundle.setType(Bundle.BundleType.DOCUMENT);
        bundle.setTimestamp(Date.from(montagem));

        bundle.addEntry()
                .setFullUrl("urn:uuid:" + uuidComposition)
                .setResource(montarComposition(dto, uuidImmunization));
        bundle.addEntry().setFullUrl("urn:uuid:" + uuidImmunization).setResource(montarImmunization(dto));
        return bundle;
    }

    private Composition montarComposition(EnrichedRegistroImunizacaoCampanha dto, String uuidImmunization) {
        Composition c = new Composition();
        c.setMeta(new Meta().addProfile(PERFIL_COMPOSITION));
        c.setStatus(Composition.CompositionStatus.FINAL);
        c.setType(conceito(TIPO_DOCUMENTO));
        c.setSubject(referenciaPorIdentificador(SYSTEM_INDIVIDUO, dto.identificadorIndividuo()));
        c.setDate(Date.from(dto.getDataRegistroOrigem()));
        c.addAuthor(referenciaPorIdentificador(SYSTEM_ESTABELECIMENTO, dto.getCnesEstabelecimento()));
        c.setTitle(TITULO);
        c.addSection().addEntry(new Reference("urn:uuid:" + uuidImmunization));
        return c;
    }

    private Immunization montarImmunization(EnrichedRegistroImunizacaoCampanha dto) {
        Immunization i = new Immunization();
        i.setMeta(new Meta().addProfile(PERFIL_IMMUNIZATION));

        // extension é 2..2 — exatamente duas, sem contatoHanseniase.
        i.addExtension(extensaoConceito(EXT_GRUPO_ATENDIMENTO, dto.getGrupoAtendimento()));
        i.addExtension(extensaoConceito(EXT_CONDICAO_MATERNAL, dto.getCondicaoMaternal()));

        i.setStatus(Immunization.ImmunizationStatus.COMPLETED); // fixedCode
        i.setVaccineCode(conceito(dto.getVacina()));
        i.setPatient(referenciaPorIdentificador(SYSTEM_INDIVIDUO, dto.identificadorIndividuo()));
        i.setOccurrence(new DateTimeType(dto.getDataAplicacao().toString()));

        // Obrigatórios em campanha, opcionais na rotina.
        i.setManufacturer(referenciaPorIdentificador(SYSTEM_FABRICANTE, dto.getCodigoFabricante()));
        i.setLotNumber(dto.getLote());

        // performer é 1..1, mas function é PROIBIDO — não há CBO em campanha.
        i.addPerformer().setActor(new Reference("Practitioner/" + dto.getCnsProfissional()));

        // protocolApplied é 1..1 e não tem extensão de estratégia:
        // em campanha, a campanha é a estratégia.
        i.addProtocolApplied().setDoseNumber(new StringType(dto.getDoseNumber()));

        // Deliberadamente NÃO emitidos, por serem max=0 neste perfil:
        // site, route, performer.function, reportOrigin, primarySource,
        // location, expirationDate, reasonReference, doseQuantity, note.
        return i;
    }

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

    private static void exigirObrigatorios(EnrichedRegistroImunizacaoCampanha dto) {
        Objects.requireNonNull(dto, "dto não pode ser nulo");
        exigir(dto.getIdLocalRegistro(), "idLocalRegistro (Bundle.identifier.value)");
        exigir(dto.getCnesEstabelecimento(), "cnesEstabelecimento (Composition.author)");
        exigir(dto.getCnsProfissional(), "cnsProfissional (Immunization.performer.actor)");
        exigir(dto.getDataRegistroOrigem(), "dataRegistroOrigem (Composition.date)");
        exigir(dto.getGrupoAtendimento(), "grupoAtendimento (extensão 1..1)");
        exigir(dto.getCondicaoMaternal(), "condicaoMaternal (extensão 1..1)");
        exigir(dto.getVacina(), "vacina (Immunization.vaccineCode 1..1)");
        exigir(dto.getDoseNumber(), "doseNumber (protocolApplied.doseNumber 1..1)");
        exigir(dto.getDataAplicacao(), "dataAplicacao (Immunization.occurrence 1..1)");
        // Estes dois são o que distingue o RIA-C do RIA-R em obrigatoriedade.
        exigir(dto.getCodigoFabricante(), "codigoFabricante (manufacturer.identifier 1..1 — opcional no RIA-R)");
        exigir(dto.getLote(), "lote (Immunization.lotNumber 1..1 — opcional no RIA-R)");
        dto.identificadorIndividuo();
    }

    private static void exigir(Object valor, String campo) {
        if (valor == null || (valor instanceof String s && s.isBlank())) {
            throw new IllegalArgumentException("Campo obrigatório ausente para o perfil do RIA-C: " + campo);
        }
    }
}
