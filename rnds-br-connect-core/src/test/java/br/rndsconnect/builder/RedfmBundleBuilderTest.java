package br.rndsconnect.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rndsconnect.model.CodedValue;
import br.rndsconnect.model.EnrichedRegistroDispensacaoMedicamento;
import br.rndsconnect.model.EnrichedRegistroDispensacaoMedicamento.Prescricao;
import br.rndsconnect.validator.ConformancePackage;
import br.rndsconnect.validator.LocalFhirValidator;
import br.rndsconnect.validator.Servico;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.ValidationResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.MedicationDispense;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Critério de saída do {@link RedfmBundleBuilder}.
 *
 * <p>O teste que carrega o argumento arquitetônico é {@link #pacoteCombinaXmlEJson()}: o
 * REDFM valida contra um pacote que reúne 381 artefatos XML do DATASUS e 324 JSON do IG
 * BR-Core. É a Fase 0 sendo exercida no seu caso mais difícil — duas fundações, dois
 * formatos, um único {@code ConformancePackage}.
 *
 * @author Igor Mendes Domingues Miras
 */
class RedfmBundleBuilderTest {

    private static final String SOLICITANTE = "12345";
    private static final String CNS = "712176901347489";
    private static final String CNES = "5354072";
    private static final Instant QUANDO = Instant.parse("2024-12-30T14:46:12Z");
    private static final AtomicInteger SEQ = new AtomicInteger();

    private static LocalFhirValidator validador;
    private static ConformancePackage pacote;

    @BeforeAll
    static void carregar() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
        FhirContext ctx = FhirContext.forR4();
        // XML do DATASUS + JSON do IG BR-Core, no MESMO pacote.
        //
        // O pacote e carregado UMA vez e reusado por todos os testes. Instanciar um
        // segundo ConformancePackage na mesma JVM significa outro FhirContext, outro
        // conjunto de snapshots gerados e outra copia dos artefatos em memoria -- e a
        // JVM de teste morre com OutOfMemoryError mesmo com heap de 4 GB.
        pacote = ConformancePackage.paraServico(ctx, Servico.REDFM, "conformance/redfm");
        validador = new LocalFhirValidator(ctx, pacote);
    }

    private static RedfmBundleBuilder builder() {
        return new RedfmBundleBuilder(
                SOLICITANTE, () -> String.format("ff000000-0000-4000-8000-%012d", SEQ.incrementAndGet()), () -> QUANDO);
    }

    private static Prescricao.PrescricaoBuilder prescricao() {
        return Prescricao.builder()
                .idItemPrescrito("e6f89964-b218-4fb3-9603-e0897c5c2181")
                .dataPrescricao(QUANDO)
                .cnsPrescritor("704200519817090")
                .conselhoPrescritor("crm-sp")
                .inscricaoPrescritor("154735")
                .cboPrescritor(CodedValue.rnds("BRCBO", "225110"))
                .cnesLotacaoPrescritor("6156002")
                .cid10(CodedValue.rnds("BRCID10", "I10"))
                .viaAdministracao(CodedValue.rnds("BRViaAdministracao", "10907"))
                .dose(BigDecimal.ONE)
                .unidadeDose(CodedValue.rnds("BRUnidadeMedida", "13"))
                .descricaoUnidadeDose("Comprimido");
    }

    private static EnrichedRegistroDispensacaoMedicamento.EnrichedRegistroDispensacaoMedicamentoBuilder dispensacao() {
        return EnrichedRegistroDispensacaoMedicamento.builder()
                .idLocalRegistro("73549927")
                .idItemDispensado("e6f89964-b218-4fb3-9603-e0897c5c2181")
                .cnsPaciente(CNS)
                .cnesEstabelecimento(CNES)
                .dataRegistroOrigem(QUANDO)
                .dataEntrega(QUANDO)
                .medicamento(CodedValue.rnds("BRObmVMP", "@brasil12898180271"))
                .descricaoMedicamento("Amoxicilina 500 mg cápsula")
                .formaFarmaceutica(CodedValue.rnds("BRUnidadeMedida", "13"))
                .cnpjFabricante("21341776000145")
                .lote("587AT765H")
                .validade(LocalDate.of(2025, 10, 31))
                .categoriaDispensacao(new CodedValue(
                        "http://terminology.hl7.org/CodeSystem/medicationdispense-category", "community"))
                .tipoDispensacao(CodedValue.rnds("BRTipoDispensacaoRealizada", "fully-dispensing"))
                .quantidade(new BigDecimal("30"))
                .unidadeQuantidade("Cápsula")
                .cnsFarmaceutico("704200519817090")
                .cboFarmaceutico(CodedValue.rnds("BRCBO", "223405"))
                .conselhoFarmaceutico("crf-sp")
                .inscricaoFarmaceutico("154735")
                .prescricao(prescricao().build());
    }

    // ══ Critério de saída ═══════════════════════════════════════════════

    @Test
    @DisplayName("O Bundle construído passa na Camada 4, com o pacote combinado do REDFM")
    void bundleConstruidoEConforme() {
        Bundle b = builder().build(dispensacao().build());
        ValidationResult r = validador.validar(b);
        LocalFhirValidator.erros(r)
                .forEach(m ->
                        System.out.printf("  [%s] %s: %s%n", m.getSeverity(), m.getLocationString(), m.getMessage()));
        assertThat(LocalFhirValidator.erros(r)).isEmpty();
    }

    // ══ O argumento arquitetônico ═══════════════════════════════════════

    @Test
    @DisplayName("O pacote do REDFM combina XML do DATASUS com JSON do BR-Core")
    void pacoteCombinaXmlEJson() {
        ConformancePackage p = pacote;

        assertThat(p.servico()).isEqualTo(Servico.REDFM);
        assertThat(p.total())
                .as("fechamento transitivo ESTRITO a partir dos seis perfis que o Bundle "
                        + "instancia: 43 do pacote XML do REDFM mais 3 que so o IG BR-Core traz. "
                        + "O modo estrito segue apenas baseDefinition e bindings required, "
                        + "ignorando targetProfile — sem ele o pacote teria 51 StructureDefinition "
                        + "e a geracao de snapshot nao completa nem com 6 GB de heap")
                .isEqualTo(46);

        // O REDFM deriva do BR-Core: sem esses perfis, o SnapshotGeneratingValidationSupport
        // nao monta o snapshot e o servico nao e validavel.
        //
        // A PROCEDENCIA surpreende: o br-core-composition vem do pacote XML do proprio REDFM,
        // e nao do IG BR-Core em JSON. O projeto Simplifier do REDFM inclui o BR-Core inteiro,
        // e os dois pacotes se sobrepoem em 291 de 324 URLs. Por isso o --append preserva o
        // que ja esta carregado: quem chegou primeiro define a versao, e mesclar exigiria
        // arbitrar qual copia vale.
        String base = "https://br-core.saude.gov.br/fhir/StructureDefinition/br-core-composition";
        assertThat(p.origemDe(base))
                .as("o perfil do qual o documento do REDFM deriva precisa estar no pacote")
                .isNotNull();

        // E o IG BR-Core em JSON contribui com o que o pacote XML nao tem.
        assertThat(p.diretorios()).containsExactly("conformance/redfm");

        System.out.printf(
                "%n[REDFM] %d artefatos (%d SD, %d VS, %d CS) em %d ms%n",
                p.total(),
                p.structureDefinitions().size(),
                p.valueSets().size(),
                p.codeSystems().size(),
                p.tempoCargaMillis());
    }

    // ══ Resources contidos ══════════════════════════════════════════════

    @Test
    @DisplayName("Com receita embutida, o MedicationDispense contém quatro resources")
    void receitaEmbutidaGeraQuatroContidos() {
        Bundle b = builder().build(dispensacao().build());
        MedicationDispense d = (MedicationDispense) b.getEntry().get(1).getResource();

        assertThat(d.getContained())
                .extracting(org.hl7.fhir.r4.model.Resource::getId)
                .containsExactly("prescription", "prescriber-identifier", "prescriber-specialty", "pharmacist-crf");

        // Referências a contidos usam #id, não urn:uuid. Não contraria o ADR-07: aquele
        // trata de referências entre ENTRADAS do Bundle, e um contido não é entrada.
        assertThat(d.getAuthorizingPrescriptionFirstRep().getReference()).isEqualTo("#prescription");
        assertThat(d.getPerformer().get(1).getActor().getReference()).isEqualTo("#pharmacist-crf");
    }

    @Test
    @DisplayName("Com receita na RNDS, não há contained:prescription")
    void receitaNaRndsNaoEmbuteAReceita() {
        Bundle b = builder()
                .build(dispensacao()
                        .prescricao(null)
                        .idPrescricaoNaRnds("563BFD7432")
                        .build());
        MedicationDispense d = (MedicationDispense) b.getEntry().get(1).getResource();

        assertThat(d.getContained())
                .as("só o farmacêutico; o prescritor vem junto da receita")
                .extracting(org.hl7.fhir.r4.model.Resource::getId)
                .containsExactly("pharmacist-crf");

        assertThat(d.getAuthorizingPrescriptionFirstRep().hasReference()).isFalse();
        assertThat(d.getAuthorizingPrescriptionFirstRep().getIdentifier().getValue())
                .isEqualTo("563BFD7432");

        assertThat(LocalFhirValidator.erros(validador.validar(b))).isEmpty();
    }

    @Test
    @DisplayName("As duas fatias de authorizingPrescription são mutuamente exclusivas")
    void asDuasFatiasSaoExclusivas() {
        // Ambas: a mesma receita não pode estar em dois lugares.
        assertThatThrownBy(() -> builder()
                        .build(dispensacao().idPrescricaoNaRnds("563BFD7432").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mutuamente")
                .hasMessageContaining("nunca os dois");

        // Nenhuma: a dispensação precisa de uma receita que a autorize.
        assertThatThrownBy(() -> builder().build(dispensacao().prescricao(null).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nem nenhum");
    }

    // ══ Valores fixados pelo perfil ═════════════════════════════════════

    @Test
    @DisplayName("Título e código LOINC da seção são fixados pelo perfil")
    void valoresFixosDoPerfil() {
        Bundle b = builder().build(dispensacao().build());
        org.hl7.fhir.r4.model.Composition c =
                (org.hl7.fhir.r4.model.Composition) b.getEntry().get(0).getResource();

        assertThat(c.getTitle()).isEqualTo("Registro Eletrônico de Dispensação ou Fornecimento de Medicamentos");
        assertThat(c.getSectionFirstRep().getTitle()).isEqualTo("Medicamentos Dispensados");
        assertThat(c.getSectionFirstRep().getCode().getCodingFirstRep().getCode())
                .as("56445-0 é 'Medication summary' no LOINC — fixado pelo perfil")
                .isEqualTo("56445-0");
    }

    @Test
    @DisplayName("Três entradas no Bundle: Composition, MedicationDispense, Medication")
    void tresEntradas() {
        Bundle b = builder().build(dispensacao().build());
        assertThat(b.getEntry())
                .extracting(e -> e.getResource().fhirType())
                .containsExactly("Composition", "MedicationDispense", "Medication");
    }

    @Test
    @DisplayName("Campos obrigatórios ausentes falham na construção")
    void camposObrigatoriosFalham() {
        assertThatThrownBy(() ->
                        builder().build(dispensacao().cnsFarmaceutico(null).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cnsFarmaceutico");

        assertThatThrownBy(() -> builder().build(dispensacao().quantidade(null).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantidade");
    }
}
