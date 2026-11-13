package br.rndsconnect.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rndsconnect.model.CodedTarget;
import br.rndsconnect.model.TerminologyDomain;
import br.rndsconnect.semantic.MappingSource;
import br.rndsconnect.semantic.YamlMappingSource;
import ca.uhn.fhir.context.FhirContext;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O catálogo dos 475 códigos de exame aceitos pela RNDS.
 *
 * <p>O teste que carrega o argumento é {@link #osSeteExcluidosNaoSaoAceitos()}: os sete
 * códigos que o {@code compose.exclude} do ValueSet remove <strong>existem</strong> no
 * CodeSystem e <strong>não são aceitos</strong>. É a diferença entre 484 e 477, e é o erro que
 * alguém comete ao montar o catálogo somando os CodeSystems sem ler o ValueSet.
 *
 * <p>O portão, {@link #catalogoNaoDivergeDosArtefatos()}, recalcula o catálogo do pacote do
 * RIA e do REL e exige igualdade com o arquivo distribuído, como já fazia o do catálogo de
 * medidas. Sem ele, um catálogo desatualizado em relação ao pacote passava pela conferência
 * do total declarado e pelo teste da contagem, porque nenhum dos dois lê os artefatos.
 *
 * @author Igor Mendes Domingues Miras
 */
class CatalogoExamesRndsTest {

    /** O pacote do RIA e do REL, de onde o catálogo foi gerado. */
    private static final String PACOTE = "conformance";

    private static final String VS = "http://www.saude.gov.br/fhir/r4/ValueSet/BRNomeExame-2.0";
    private static final String CS = "http://www.saude.gov.br/fhir/r4/CodeSystem/";
    private static final String SYSTEM_LOINC = CS + "BRNomeExameLOINC";
    private static final String SYSTEM_GAL = CS + "BRNomeExameGAL";

    private static CatalogoCodigosAceitos catalogo;

    @BeforeAll
    static void carregar() {
        catalogo = CatalogoCodigosAceitos.exames();
        System.out.printf(
                "%n[Catálogo] %d códigos (%d LOINC + %d GAL) · sha256:%s%n",
                catalogo.total(), catalogo.totalDe(SYSTEM_LOINC), catalogo.totalDe(SYSTEM_GAL), catalogo.sha256());
    }

    @Test
    @DisplayName("São 475: 442 LOINC + 33 GAL — nem 484, nem 477")
    void totalCorreto() {
        // 451 conceitos LOINC - 2 duplicados - 7 excluídos = 442; + 33 GAL = 475.
        // Somar os conceitos dos dois CodeSystems dá 484; parar após o exclude dá 477.
        // Nenhum dos dois é o número de códigos que a RNDS aceita.
        assertThat(catalogo.total()).isEqualTo(475);
        assertThat(catalogo.totalDe(SYSTEM_LOINC)).isEqualTo(442);
        assertThat(catalogo.totalDe(SYSTEM_GAL)).isEqualTo(33);
        assertThat(catalogo.systems()).hasSize(2);
    }

    @Test
    @DisplayName("Os 2 códigos declarados em duplicidade no CodeSystem entram uma vez só")
    void duplicadosDoCodeSystemEntramUmaVez() {
        // O BRNomeExameLOINC declara 80205-8 e 80206-6 DUAS vezes cada, com displays
        // que traduzem o mesmo termo LOINC de formas diferentes. O CodeSystem não declara
        // `count`, então nada no artefato denuncia a discrepância: quem soma conceitos
        // obtém 451, quem conta códigos obtém 449.
        for (String c : new String[] {"80205-8", "80206-6"}) {
            assertThat(catalogo.aceita(SYSTEM_LOINC, c))
                    .as("o código %s é aceito — a duplicidade é do artefato, não do código", c)
                    .isTrue();
        }
        // A verificação de consistência do próprio catálogo é o que pegou isto:
        // o gerador contava linhas e o parser carregava chaves distintas.
        assertThat(catalogo.total()).as("475 chaves distintas, não 477 linhas").isEqualTo(475);
    }

    @Test
    @DisplayName("Os 7 códigos do compose.exclude existem no CodeSystem e NÃO são aceitos")
    void osSeteExcluidosNaoSaoAceitos() {
        String[] excluidos = {"94764-8", "94822-4", "95424-8", "95609-4", "95970-0", "96741-4", "98062-3"};
        for (String c : excluidos) {
            assertThat(catalogo.aceita(SYSTEM_LOINC, c))
                    .as(
                            "o código %s consta do BRNomeExameLOINC mas o ValueSet o remove — "
                                    + "um catálogo com 484 entradas o incluiria, e a RNDS o recusaria",
                            c)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("Aceita códigos dos dois CodeSystems, com display oficial")
    void aceitaOsDoisCodeSystems() {
        assertThat(catalogo.aceita(SYSTEM_LOINC, "94309-2")).isTrue();
        assertThat(catalogo.aceita(SYSTEM_GAL, "DENAN")).isTrue();
        assertThat(catalogo.display(SYSTEM_GAL, "DENAN"))
                .hasValueSatisfying(d -> assertThat(d).containsIgnoringCase("dengue"));
    }

    @Test
    @DisplayName("Formato errado denuncia CodeSystem errado")
    void formatoErradoDenunciaCodeSystemErrado() {
        // LOINC é numérico com dígito verificador; GAL é mnemônico alfanumérico.
        assertThat(catalogo.aceita(SYSTEM_GAL, "94309-2"))
                .as("código LOINC declarado como GAL")
                .isFalse();
        assertThat(catalogo.aceita(SYSTEM_LOINC, "DENAN"))
                .as("código GAL declarado como LOINC")
                .isFalse();
    }

    // ══ O portão ════════════════════════════════════════════════════════

    @Test
    @DisplayName("GATE: o catálogo de exames é exatamente o que os artefatos declaram")
    void catalogoNaoDivergeDosArtefatos() {
        RecalculoDeCatalogo artefatos = RecalculoDeCatalogo.doPacote(FhirContext.forR4(), PACOTE, VS, "BRNomeExame");

        assertThat(artefatos.valueSet().getCompose().getInclude())
                .as("os dois CodeSystems que o ValueSet compõe")
                .hasSize(2);
        assertThat(catalogo.codigos())
                .as(
                        "o YAML gerado contra o pacote %s — regerar com "
                                + "scripts/gerar-catalogo-exames.py se os artefatos mudaram",
                        PACOTE)
                .isEqualTo(artefatos.codigosAceitos());
    }

    // ══ Conferência da tabela do integrador ═════════════════════════════

    @Test
    @DisplayName("Tabela conforme passa sem divergência")
    void tabelaConformePassa() {
        MapeamentoConformeCatalogo conferencia = new MapeamentoConformeCatalogo(catalogo);
        assertThat(conferencia.conferir(fonteFake(Map.of(
                        "COVID_PCR", alvo(SYSTEM_LOINC, "94309-2"),
                        "DENGUE_NS1", alvo(SYSTEM_GAL, "DENAN")))))
                .isEmpty();
    }

    @Test
    @DisplayName("A tabela de exemplo de exames, lida do YAML, passa na conferência")
    void tabelaDeExemploPassaNaConferencia() {
        // Com o catálogo no mesmo módulo das fontes de mapeamento, a conferência é exercida
        // com a fonte real, e não só com uma tabela montada em memória.
        MappingSource fonte =
                new YamlMappingSource(TerminologyDomain.NOME_EXAME, "mapeamentos/exames-multi-codesystem.yml");
        assertThat(new MapeamentoConformeCatalogo(catalogo).conferir(fonte)).isEmpty();
    }

    @Test
    @DisplayName("Código excluído pelo ValueSet é apanhado na inicialização, não no primeiro paciente")
    void codigoExcluidoEApanhadoNaInicializacao() {
        MapeamentoConformeCatalogo conferencia = new MapeamentoConformeCatalogo(catalogo);

        assertThatThrownBy(() ->
                        conferencia.exigirConformidade(fonteFake(Map.of("EXAME_X", alvo(SYSTEM_LOINC, "94764-8")))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EXAME_X")
                .hasMessageContaining("compose.exclude");
    }

    @Test
    @DisplayName("A conferência devolve TODAS as divergências, ao contrário da cadeia fail-fast")
    void conferenciaRelataTodasAsDivergencias() {
        Map<String, CodedTarget> tabela = new LinkedHashMap<>();
        tabela.put("BOM", alvo(SYSTEM_LOINC, "94309-2"));
        tabela.put("DIGITADO_ERRADO", alvo(SYSTEM_LOINC, "99999-9"));
        tabela.put("EXCLUIDO", alvo(SYSTEM_LOINC, "95424-8"));
        tabela.put("SYSTEM_ERRADO", alvo("http://loinc.org", "94309-2"));

        assertThat(new MapeamentoConformeCatalogo(catalogo).conferir(fonteFake(tabela)))
                .as("quem revisa uma tabela quer a lista inteira, não a primeira linha errada")
                .hasSize(3)
                .extracting(MapeamentoConformeCatalogo.Divergencia::codigoLocal)
                .containsExactly("DIGITADO_ERRADO", "EXCLUIDO", "SYSTEM_ERRADO");
    }

    @Test
    @DisplayName("CodeSystem fora do catálogo tem diagnóstico próprio")
    void codeSystemForaDoCatalogo() {
        // http://loinc.org é o LOINC internacional — válido no mundo, não na RNDS,
        // que exige a URL do seu próprio CodeSystem.
        assertThat(new MapeamentoConformeCatalogo(catalogo)
                        .conferir(fonteFake(Map.of("X", alvo("http://loinc.org", "94309-2"))))
                        .get(0)
                        .motivo())
                .contains("CodeSystem fora do catálogo");
    }

    // ══ Auxiliares ══════════════════════════════════════════════════════

    private static CodedTarget alvo(String system, String code) {
        return TabelaDeTeste.alvo(system, code);
    }

    private static MappingSource fonteFake(Map<String, CodedTarget> tabela) {
        return TabelaDeTeste.de(TerminologyDomain.NOME_EXAME, tabela);
    }
}
