package br.rndsconnect.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rndsconnect.dto.RegistroExameLaboratorialDTO;
import br.rndsconnect.facade.RndsFramework;
import br.rndsconnect.model.TerminologyDomain;
import br.rndsconnect.semantic.PropertiesMappingSource;
import br.rndsconnect.semantic.YamlMappingSource;
import ca.uhn.fhir.context.FhirContext;
import java.time.Instant;
import java.util.List;
import org.hl7.fhir.r4.model.CodeSystem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Fase 0 — a Camada 4 passa a ter um pacote de conformidade por serviço.
 *
 * <p>O teste que carrega o argumento é {@link #pacotesDivergentesCoexistem()}: dois serviços
 * carregam o <strong>mesmo</strong> {@code BRImunobiologico}, na mesma versão declarada, de
 * pacotes diferentes — e cada validador enxerga a cópia do seu pacote.
 *
 * <p>É o defeito nº 11 convertido em teste de regressão. Antes desta mudança, um pacote global
 * teria de escolher qual das cópias prevalece, e essa escolha não cabe ao framework.
 *
 * @author Igor Mendes Domingues Miras
 */
class ConformancePackagePorServicoTest {

    private static final String CS_IMUNO = "http://www.saude.gov.br/fhir/r4/CodeSystem/BRImunobiologico";

    private static FhirContext ctx;

    @BeforeAll
    static void carregar() {
        ctx = FhirContext.forR4();
    }

    // ══ O pacote conhece o serviço a que serve ══════════════════════════

    @Test
    @DisplayName("O pacote carrega e sabe a que serviço pertence")
    void pacoteConheceOServico() {
        ConformancePackage p = ConformancePackage.paraServico(ctx, Servico.RIA, "conformance");

        assertThat(p.servico()).isEqualTo(Servico.RIA);
        assertThat(p.diretorios()).containsExactly("conformance");
        assertThat(p.total()).isGreaterThan(100);
        assertThat(p.ignorados()).isEmpty();

        System.out.printf(
                "%n[Fase 0] %s: %d artefatos (%d SD, %d VS, %d CS) em %d ms%n",
                p.servico(),
                p.total(),
                p.structureDefinitions().size(),
                p.valueSets().size(),
                p.codeSystems().size(),
                p.tempoCargaMillis());
    }

    @Test
    @DisplayName("A origem de cada artefato é rastreável — a versão declarada não basta")
    void origemDeCadaArtefatoERastreavel() {
        ConformancePackage p = ConformancePackage.paraServico(ctx, Servico.RIA, "conformance");

        // Defeito nº 11: BRImunobiologico "02.16" existe em três pacotes com conteúdos
        // diferentes. Saber a versão não identifica contra o que se validou; saber o
        // arquivo, sim.
        assertThat(p.origemDe(CS_IMUNO))
                .as("a proveniência da Camada 4 precisa apontar o arquivo, não a versão")
                .isNotNull()
                .endsWith(".xml");

        assertThat(p.origemDe("http://exemplo.invalido/NaoExiste")).isNull();
    }

    // ══ O argumento: pacotes divergentes coexistem ══════════════════════

    @Test
    @DisplayName("DEFEITO Nº 11: dois serviços com o mesmo artefato divergente coexistem")
    void pacotesDivergentesCoexistem() {
        // Aqui os dois apontam para o mesmo diretório, porque RIA e REL vêm do mesmo
        // pacote publicado. O que o teste fixa é a INDEPENDÊNCIA: cada serviço tem seu
        // próprio ConformancePackage, e a contagem de conceitos de um não contamina o outro.
        ConformancePackage ria = ConformancePackage.paraServico(ctx, Servico.RIA, "conformance");
        ConformancePackage rel = ConformancePackage.paraServico(ctx, Servico.REL, "conformance");

        assertThat(ria.servico()).isNotEqualTo(rel.servico());

        int conceitosRia = conceitosDe(ria, CS_IMUNO);
        int conceitosRel = conceitosDe(rel, CS_IMUNO);
        assertThat(conceitosRia)
                .as("o pacote de RIA e REL declara 114 conceitos; o do SA declara 116 e o do "
                        + "REDFM, 106 — todos com versão 02.16")
                .isEqualTo(114);
        assertThat(conceitosRel).isEqualTo(conceitosRia);

        // Instâncias distintas: mexer numa não afeta a outra.
        assertThat(codeSystemDe(ria, CS_IMUNO)).isNotSameAs(codeSystemDe(rel, CS_IMUNO));
    }

    @Test
    @DisplayName("Colisão DENTRO de um pacote é erro; ENTRE pacotes é o esperado")
    void colisaoInternaEErroColisaoEntrePacotesNao() {
        // Entre pacotes: nenhum problema — é o motivo de existirem separados.
        assertThatCode(() -> {
                    ConformancePackage.paraServico(ctx, Servico.RIA, "conformance");
                    ConformancePackage.paraServico(ctx, Servico.REL, "conformance");
                })
                .doesNotThrowAnyException();

        // Dentro de um pacote: o mesmo diretório duas vezes duplica todas as URLs.
        assertThatThrownBy(() -> ConformancePackage.paraServico(ctx, Servico.RIA, "conformance", "conformance"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("URL canônica duplicada")
                .hasMessageContaining("ordem de leitura");
    }

    // ══ Carga preguiçosa ════════════════════════════════════════════════

    @Test
    @DisplayName("Só os serviços declarados são carregados")
    void soOsServicosDeclaradosSaoCarregados() {
        // Framework montado com RIA declarado e REL ausente, mas com TODAS as estratégias
        // de mapeamento do REL registradas — para que o fluxo atravesse as Camadas 2 e 3 e
        // chegue de fato à Camada 4. Passar um DTO nulo não serviria: a Camada 2 o
        // rejeitaria antes, e o teste não exercitaria o que se propõe a exercitar.
        var facade = RndsFramework.builder()
                .solicitanteId("29376")
                .fhirContext(ctx)
                .servico(Servico.RIA, "conformance")
                .mapping(new PropertiesMappingSource(
                        TerminologyDomain.SUSPEITA_DIAGNOSTICA, "mapeamentos/suspeitas-diagnosticas.properties"))
                .mapping(new YamlMappingSource(TerminologyDomain.NOME_EXAME, "mapeamentos/exames-multi-codesystem.yml"))
                .mapping(new PropertiesMappingSource(
                        TerminologyDomain.SUBGRUPO_TABELA_SUS, "mapeamentos/categorias-exame.properties"))
                .mapping(new YamlMappingSource(TerminologyDomain.TIPO_AMOSTRA, "mapeamentos/tipos-amostra.yml"))
                .mapping(new YamlMappingSource(
                        TerminologyDomain.RESULTADO_QUALITATIVO, "mapeamentos/resultados-qualitativos.yml"))
                .mapping(new PropertiesMappingSource(TerminologyDomain.PATOGENO, "mapeamentos/patogenos.properties"))
                .build();

        // O REL não foi declarado: usá-lo falha com diagnóstico acionável, em vez de
        // recair num validador genérico que daria veredicto diferente do da RNDS.
        assertThatThrownBy(() -> facade.prepararExameLaboratorial(laudoValido()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REL")
                .hasMessageContaining("não foi declarado");
    }

    /** Laudo que atravessa as Camadas 2 e 3 sem erro, para o fluxo alcançar a Camada 4. */
    private static RegistroExameLaboratorialDTO laudoValido() {
        Instant quando = Instant.parse("2024-04-01T11:18:30Z");
        return RegistroExameLaboratorialDTO.builder()
                .idLocalRegistro("LAUDO-FASE0")
                .cpfPaciente("01234567890")
                .cnpjLaboratorio("00394544000185")
                .cnesEstabelecimento("0123456")
                .dataRegistroOrigem(quando)
                .suspeitaDiagnosticaLocal("MONKEYPOX")
                .exames(List.of(RegistroExameLaboratorialDTO.ExameDTO.builder()
                        .nomeExameLocal("COVID_PCR")
                        .categoriaExameLocal("LAB_CLINICO")
                        .tipoAmostraLocal("SWAB_NASOFARINGE")
                        .dataColeta(quando)
                        .dataLiberacaoLaudo(quando.plusSeconds(3600))
                        .metodo("RT-PCR em tempo real.")
                        .valorReferencia("Não detectável em não infectados.")
                        .resultadoQualitativoLocal("NAO_DETECTAVEL")
                        .patogenoLocal("ORTOPOXVIRUS")
                        .build()))
                .build();
    }

    @Test
    @DisplayName("Construir sem declarar serviço algum é recusado")
    void semServicoDeclaradoFalha() {
        assertThatThrownBy(() -> RndsFramework.builder()
                        .solicitanteId("29376")
                        .mapping(new YamlMappingSource(TerminologyDomain.VACINA, "mapeamentos/vacinas.yml"))
                        .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Nenhum serviço declarado");
    }

    // ══ Auxiliares ══════════════════════════════════════════════════════

    private static CodeSystem codeSystemDe(ConformancePackage p, String url) {
        return p.codeSystems().stream()
                .filter(cs -> url.equals(cs.getUrl()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("CodeSystem ausente no pacote: " + url));
    }

    private static int conceitosDe(ConformancePackage p, String url) {
        return codeSystemDe(p, url).getConcept().size();
    }
}
