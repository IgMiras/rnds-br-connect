package br.rndsconnect.semantic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rndsconnect.builder.RiaBundleBuilder;
import br.rndsconnect.model.CodedValue;
import br.rndsconnect.model.EnrichedRegistroImunizacao;
import br.rndsconnect.model.TerminologyDomain;
import br.rndsconnect.validator.ConformancePackage;
import br.rndsconnect.validator.LocalFhirValidator;
import br.rndsconnect.validator.Servico;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.ValidationResult;
import java.time.Instant;
import java.time.LocalDate;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Camada 2, Frente B — mapeamento terminológico plugável.
 *
 * <p>O teste que carrega o argumento do trabalho é
 * {@link #codigoLocalRepassadoProduzBundleValidoEClinicamenteErrado()}: ele demonstra, em
 * execução, que a Camada 4 <strong>aprova</strong> um Bundle com a vacina errada. A
 * validação estrutural não protege contra erro de significado — e é por isso que a Camada 2
 * existe como camada separada, e não como um punhado de conversões no meio do código de
 * envio.
 *
 * @author Igor Mendes Domingues Miras
 */
class FrenteBTest {

    private static final String CS_IMUNO = "http://www.saude.gov.br/fhir/r4/CodeSystem/BRImunobiologico";
    private static final String ID_REGISTRO = "REG-2024-0001";

    private static FhirContext ctx;
    private static LocalFhirValidator validador;
    private static TerminologyRegistry registry;

    @BeforeAll
    static void preparar() {
        ctx = FhirContext.forR4();
        validador = new LocalFhirValidator(ctx, ConformancePackage.paraServico(ctx, Servico.RIA, "conformance"));

        registry = TerminologyRegistry.novo()
                .registrar(new SourceBackedMappingStrategy(
                        new YamlMappingSource(TerminologyDomain.VACINA, "mapeamentos/vacinas.yml")))
                // DOSE: Properties SEM _validatedBy — correspondência operacional.
                .registrar(new SourceBackedMappingStrategy(
                        new PropertiesMappingSource(TerminologyDomain.DOSE, "mapeamentos/doses.properties")))
                // VIA_ADMINISTRACAO: Properties COM _validatedBy — o par que prova que o
                // formato simples também registra responsabilidade clínica.
                .registrar(new SourceBackedMappingStrategy(new PropertiesMappingSource(
                        TerminologyDomain.VIA_ADMINISTRACAO, "mapeamentos/vias-administracao.properties")))
                .registrar(new SourceBackedMappingStrategy(new YamlMappingSource(
                        TerminologyDomain.NOME_EXAME, "mapeamentos/exames-multi-codesystem.yml")));
    }

    private static MappingContext contexto() {
        return new MappingContext(ID_REGISTRO, Instant.parse("2024-04-01T17:59:51.283Z"));
    }

    // ══ O argumento central ═════════════════════════════════════════════

    @Test
    @DisplayName("ARGUMENTO P2: código sem correspondência interrompe o fluxo antes de qualquer FHIR")
    void codigoSemCorrespondenciaInterrompeOFluxo() {
        RawValue naoMapeado = new RawValue(TerminologyDomain.VACINA, "999_VACINA_NOVA");

        assertThatThrownBy(() -> registry.map(naoMapeado, contexto()))
                .isInstanceOf(UnmappedTerminologyException.class)
                .hasMessageContaining("999_VACINA_NOVA")
                .hasMessageContaining("VACINA")
                .hasMessageContaining("vacinas.yml")
                .hasMessageContaining("NÃO repassa o código local");
    }

    @Test
    @DisplayName("ARGUMENTO P2, prova pelo contrário: repassar o código local produz Bundle "
            + "que a Camada 4 APROVA e que registra a vacina errada")
    void codigoLocalRepassadoProduzBundleValidoEClinicamenteErrado() {
        // No vocabulário local, "105" é a Pfizer. Simulamos o comportamento ingênuo —
        // repassar o código local quando não há correspondência registrada.
        Bundle bundleIngenuo = montarBundle(new CodedValue(CS_IMUNO, "105"));

        ValidationResult resultado = validador.validar(bundleIngenuo);

        assertThat(LocalFhirValidator.erros(resultado))
                .as("A Camada 4 aprova: \"105\" É um código válido do BRImunobiologico. "
                        + "Não há nada de errado com a ESTRUTURA — está errado o SIGNIFICADO.")
                .isEmpty();

        // O que a base nacional registraria: BRImunobiologico#105 = COVID-19 MODERNA
        // SPIKEVAX BIVALENTE. O paciente recebeu Pfizer. HTTP 200, dado clínico errado,
        // nenhum alarme em lugar nenhum.
        System.out.println("[Frente B] Bundle com código local repassado: APROVADO pela Camada 4, "
                + "e registra imunobiológico diferente do aplicado.");

        // Com a Camada 2 no caminho, o mesmo código local produz o alvo correto.
        MappedValue traduzido = registry.map(new RawValue(TerminologyDomain.VACINA, "105"), contexto());
        assertThat(traduzido.target().code())
                .as("\"105\" local (Pfizer) traduz para \"87\" na RNDS — não para \"105\"")
                .isEqualTo("87");
        assertThat(traduzido.target().display()).isEqualTo("COVID-19 PFIZER - COMIRNATY");

        Bundle bundleCorreto = montarBundle(traduzido.target().toCodedValue());
        assertThat(LocalFhirValidator.erros(validador.validar(bundleCorreto))).isEmpty();
    }

    // ══ Proveniência (ADR-04) ═══════════════════════════════════════════

    @Test
    @DisplayName("Toda tradução carrega proveniência verificável, com hash da fonte")
    void traducaoCarregaProveniencia() {
        MappedValue r = registry.map(new RawValue(TerminologyDomain.VACINA, "105"), contexto());
        MappingProvenance p = r.provenance();

        assertThat(p.getDomain()).isEqualTo(TerminologyDomain.VACINA);
        assertThat(p.getSourceValue()).isEqualTo("105");
        assertThat(p.getTargetSystem()).isEqualTo(CS_IMUNO);
        assertThat(p.getTargetValue()).isEqualTo("87");
        assertThat(p.getStrategyUsed()).isEqualTo("SourceBackedMappingStrategy");
        assertThat(p.getConfidence()).isEqualTo(1.0);
        assertThat(p.getIdLocalRegistro()).isEqualTo(ID_REGISTRO);
        assertThat(p.getValidatedBy())
                .as("a assinatura de quem revisou a correspondência chega intacta à trilha")
                .contains("COREN-SP 123456");
        assertThat(p.getSourceVersion())
                .as("nome do artefato + SHA-256 + contagem: é isso que torna a trilha auditável")
                .startsWith("vacinas.yml · sha256:")
                .endsWith("· 4 entradas");

        System.out.println("[Frente B] " + p.toLinha());
    }

    @Test
    @DisplayName("O hash muda quando o conteúdo da fonte muda")
    void versaoDaFonteDistingueConteudo() {
        String vacinas = new YamlMappingSource(TerminologyDomain.VACINA, "mapeamentos/vacinas.yml").getVersion();
        String exames = new YamlMappingSource(TerminologyDomain.NOME_EXAME, "mapeamentos/exames-multi-codesystem.yml")
                .getVersion();
        assertThat(vacinas).isNotEqualTo(exames);
    }

    @Test
    @DisplayName("A trilha acumula todas as traduções de um envio")
    void trilhaAcumulaTraducoes() {
        MappingAuditLog trilha = new MappingAuditLog();
        registry.map(new RawValue(TerminologyDomain.VACINA, "105"), contexto(), trilha);
        registry.map(new RawValue(TerminologyDomain.DOSE, "D1"), contexto(), trilha);

        assertThat(trilha.size()).isEqualTo(2);
        assertThat(trilha.toRelatorio()).contains("VACINA").contains("DOSE").contains("sha256:");
    }

    // ══ Fontes ══════════════════════════════════════════════════════════

    @Test
    @DisplayName("Cabeçalho _validatedBy: o Properties também registra responsabilidade clínica")
    void propertiesRegistraValidacaoClinica() {
        MappedValue r = registry.map(new RawValue(TerminologyDomain.VIA_ADMINISTRACAO, "IM"), contexto());
        assertThat(r.provenance().getValidatedBy())
                .as("sem o cabeçalho, o formato mais simples de usar seria o único incapaz de "
                        + "registrar quem validou — e o integrador abriria mão disso sem perceber")
                .contains("COREN-SP 123456");
    }

    @Test
    @DisplayName("Ausência de _validatedBy é registrada como ausência, não inventada")
    void ausenciaDeValidacaoNaoEInventada() {
        MappedValue r = registry.map(new RawValue(TerminologyDomain.DOSE, "D1"), contexto());
        assertThat(r.provenance().getValidatedBy())
                .as("a correspondência de dose é operacional, não clínica; nulo aqui significa "
                        + "\"a fonte não declara\", e essa distinção importa numa auditoria")
                .isNull();
    }

    @Test
    @DisplayName("Entrada sobrescreve o validatedBy de topo do YAML")
    void entradaSobrescreveValidatedByDeTopo() {
        MappedValue padrao = registry.map(new RawValue(TerminologyDomain.VACINA, "105"), contexto());
        MappedValue especifica = registry.map(new RawValue(TerminologyDomain.VACINA, "BCG"), contexto());

        assertThat(padrao.provenance().getValidatedBy()).contains("COREN-SP 123456");
        assertThat(especifica.provenance().getValidatedBy())
                .as("mesmo modelo do system: cabeçalho é o padrão, entrada sobrescreve")
                .contains("COREN-SP 654321")
                .contains("revisão específica BCG");
    }

    @Test
    @DisplayName("Chaves de cabeçalho não viram correspondência")
    void cabecalhoNaoViraCorrespondencia() {
        PropertiesMappingSource fonte =
                new PropertiesMappingSource(TerminologyDomain.DOSE, "mapeamentos/doses.properties");
        assertThat(fonte.load()).doesNotContainKeys("_system", "_validatedBy");
        assertThat(fonte.getVersion()).endsWith("· 4 entradas");
    }

    @Test
    @DisplayName("Properties atende o caso 1:1 com um único CodeSystem")
    void propertiesTraduzComSystemUnico() {
        MappedValue r = registry.map(new RawValue(TerminologyDomain.DOSE, "REF"), contexto());
        assertThat(r.target().code()).isEqualTo("5");
        assertThat(r.target().display()).isEqualTo("Reforço");
        assertThat(r.target().system()).endsWith("/BRDose");
    }

    @Test
    @DisplayName("YAML expressa alvos em CodeSystems diferentes no mesmo domínio")
    void yamlSuportaMultiCodeSystem() {
        MappedValue loinc = registry.map(new RawValue(TerminologyDomain.NOME_EXAME, "COVID_PCR"), contexto());
        MappedValue gal = registry.map(new RawValue(TerminologyDomain.NOME_EXAME, "DENGUE_NS1"), contexto());

        assertThat(loinc.target().system()).endsWith("/BRNomeExameLOINC");
        assertThat(gal.target().system()).endsWith("/BRNomeExameGAL");
        assertThat(loinc.target().system())
                .as("BRNomeExame-2.0 compõe dois CodeSystems: o alvo é o par (system, code), "
                        + "e um code sem o system correto reprova o binding required")
                .isNotEqualTo(gal.target().system());
    }

    @Test
    @DisplayName("Fonte registrada no domínio errado falha na carga, não em produção")
    void dominioDivergenteFalhaNaCarga() {
        assertThatThrownBy(() -> new YamlMappingSource(TerminologyDomain.DOSE, "mapeamentos/vacinas.yml"))
                .isInstanceOf(MappingSourceException.class)
                .hasMessageContaining("declara domain: VACINA")
                .hasMessageContaining("registrado para o domínio DOSE");
    }

    @Test
    @DisplayName("Fonte ausente falha na inicialização com mensagem acionável")
    void fonteAusenteFalhaNaInicializacao() {
        assertThatThrownBy(() -> new YamlMappingSource(TerminologyDomain.VACINA, "mapeamentos/inexistente.yml"))
                .isInstanceOf(MappingSourceException.class)
                .hasMessageContaining("ausente no classpath");
    }

    // ══ Registro ════════════════════════════════════════════════════════

    @Test
    @DisplayName("Domínio sem estratégia registrada é erro, não omissão silenciosa")
    void dominioSemEstrategiaFalha() {
        assertThatThrownBy(() -> registry.map(new RawValue(TerminologyDomain.CID10, "B15"), contexto()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Nenhuma estratégia de mapeamento registrada")
                .hasMessageContaining("CID10");
    }

    @Test
    @DisplayName("Duas estratégias para o mesmo domínio são recusadas no registro")
    void estrategiaDuplicadaFalha() {
        TerminologyRegistry r = TerminologyRegistry.novo()
                .registrar(new SourceBackedMappingStrategy(
                        new YamlMappingSource(TerminologyDomain.VACINA, "mapeamentos/vacinas.yml")));

        assertThatThrownBy(() -> r.registrar(new SourceBackedMappingStrategy(
                        new YamlMappingSource(TerminologyDomain.VACINA, "mapeamentos/vacinas.yml"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dependente da ordem de registro");
    }

    // ══ Auxiliar ════════════════════════════════════════════════════════

    /** Monta um Bundle RIA-R mínimo variando apenas o código da vacina. */
    private static Bundle montarBundle(CodedValue vacina) {
        EnrichedRegistroImunizacao dto = EnrichedRegistroImunizacao.builder()
                .idLocalRegistro(ID_REGISTRO)
                .cpfPaciente("01234567890")
                .cnesEstabelecimento("0123456")
                .cnsProfissional("012345678912345")
                .dataRegistroOrigem(Instant.parse("2024-04-01T11:18:30Z"))
                .grupoAtendimento(CodedValue.rnds("BRGrupoAtendimento", "000703"))
                .condicaoMaternal(CodedValue.rnds("BRCondicaoMaternal", "1"))
                .estrategiaVacinacao(CodedValue.rnds("BREstrategiaVacinacao", "1"))
                .vacina(vacina)
                .localAplicacao(CodedValue.rnds("BRLocalAplicacao", "0"))
                .viaAdministracao(CodedValue.rnds("BRViaAdministracao", "0"))
                .doseNumber("1")
                .dataAplicacao(LocalDate.of(2024, 4, 1))
                .build();

        return new RiaBundleBuilder(
                        "29376",
                        new java.util.ArrayDeque<>(java.util.List.of(
                                "11111111-1111-4111-8111-111111111111", "22222222-2222-4222-8222-222222222222"))
                                ::removeFirst,
                        () -> Instant.parse("2024-04-01T17:59:51.283Z"))
                .build(dto);
    }
}
