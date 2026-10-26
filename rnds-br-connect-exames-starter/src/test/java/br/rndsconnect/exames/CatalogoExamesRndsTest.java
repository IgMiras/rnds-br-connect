package br.rndsconnect.exames;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rndsconnect.model.CodedTarget;
import br.rndsconnect.model.TerminologyDomain;
import br.rndsconnect.semantic.MappingSource;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O catálogo dos 477 códigos de exame aceitos pela RNDS.
 *
 * <p>O teste que carrega o argumento é {@link #osSeteExcluidosNaoSaoAceitos()}: os sete
 * códigos que o {@code compose.exclude} do ValueSet remove <strong>existem</strong> no
 * CodeSystem e <strong>não são aceitos</strong>. É a diferença entre 484 e 477, e é o erro que
 * alguém comete ao montar o catálogo somando os CodeSystems sem ler o ValueSet.
 *
 * @author Igor Mendes Domingues Miras
 */
class CatalogoExamesRndsTest {

    private static CatalogoExamesRnds catalogo;

    @BeforeAll
    static void carregar() {
        catalogo = CatalogoExamesRnds.carregar();
        System.out.printf(
                "%n[Catálogo] %d códigos (%d LOINC + %d GAL) · sha256:%s%n",
                catalogo.total(),
                catalogo.totalDe(CatalogoExamesRnds.SYSTEM_LOINC),
                catalogo.totalDe(CatalogoExamesRnds.SYSTEM_GAL),
                catalogo.sha256());
    }

    @Test
    @DisplayName("São 475: 442 LOINC + 33 GAL — nem 484, nem 477")
    void totalCorreto() {
        // 451 conceitos LOINC - 2 duplicados - 7 excluídos = 442; + 33 GAL = 475.
        // Somar os conceitos dos dois CodeSystems dá 484; parar após o exclude dá 477.
        // Nenhum dos dois é o número de códigos que a RNDS aceita.
        assertThat(catalogo.total()).isEqualTo(475);
        assertThat(catalogo.totalDe(CatalogoExamesRnds.SYSTEM_LOINC)).isEqualTo(442);
        assertThat(catalogo.totalDe(CatalogoExamesRnds.SYSTEM_GAL)).isEqualTo(33);
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
            assertThat(catalogo.aceita(CatalogoExamesRnds.SYSTEM_LOINC, c))
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
            assertThat(catalogo.aceita(CatalogoExamesRnds.SYSTEM_LOINC, c))
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
        assertThat(catalogo.aceita(CatalogoExamesRnds.SYSTEM_LOINC, "94309-2")).isTrue();
        assertThat(catalogo.aceita(CatalogoExamesRnds.SYSTEM_GAL, "DENAN")).isTrue();
        assertThat(catalogo.display(CatalogoExamesRnds.SYSTEM_GAL, "DENAN"))
                .hasValueSatisfying(d -> assertThat(d).containsIgnoringCase("dengue"));
    }

    @Test
    @DisplayName("Formato errado denuncia CodeSystem errado")
    void formatoErradoDenunciaCodeSystemErrado() {
        // LOINC é numérico com dígito verificador; GAL é mnemônico alfanumérico.
        assertThat(catalogo.aceita(CatalogoExamesRnds.SYSTEM_GAL, "94309-2"))
                .as("código LOINC declarado como GAL")
                .isFalse();
        assertThat(catalogo.aceita(CatalogoExamesRnds.SYSTEM_LOINC, "DENAN"))
                .as("código GAL declarado como LOINC")
                .isFalse();
    }

    // ══ Conferência da tabela do integrador ═════════════════════════════

    @Test
    @DisplayName("Tabela conforme passa sem divergência")
    void tabelaConformePassa() {
        MapeamentoConformeCatalogo conferencia = new MapeamentoConformeCatalogo(catalogo);
        assertThat(conferencia.conferir(fonteFake(Map.of(
                        "COVID_PCR", alvo(CatalogoExamesRnds.SYSTEM_LOINC, "94309-2"),
                        "DENGUE_NS1", alvo(CatalogoExamesRnds.SYSTEM_GAL, "DENAN")))))
                .isEmpty();
    }

    @Test
    @DisplayName("Código excluído pelo ValueSet é apanhado na inicialização, não no primeiro paciente")
    void codigoExcluidoEApanhadoNaInicializacao() {
        MapeamentoConformeCatalogo conferencia = new MapeamentoConformeCatalogo(catalogo);

        assertThatThrownBy(() -> conferencia.exigirConformidade(
                        fonteFake(Map.of("EXAME_X", alvo(CatalogoExamesRnds.SYSTEM_LOINC, "94764-8")))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EXAME_X")
                .hasMessageContaining("compose.exclude");
    }

    @Test
    @DisplayName("A conferência devolve TODAS as divergências, ao contrário da cadeia fail-fast")
    void conferenciaRelataTodasAsDivergencias() {
        Map<String, CodedTarget> tabela = new LinkedHashMap<>();
        tabela.put("BOM", alvo(CatalogoExamesRnds.SYSTEM_LOINC, "94309-2"));
        tabela.put("DIGITADO_ERRADO", alvo(CatalogoExamesRnds.SYSTEM_LOINC, "99999-9"));
        tabela.put("EXCLUIDO", alvo(CatalogoExamesRnds.SYSTEM_LOINC, "95424-8"));
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
        return new CodedTarget(system, code, null);
    }

    private static MappingSource fonteFake(Map<String, CodedTarget> tabela) {
        return new MappingSource() {
            @Override
            public TerminologyDomain getDomain() {
                return TerminologyDomain.NOME_EXAME;
            }

            @Override
            public Map<String, CodedTarget> load() {
                return tabela;
            }

            @Override
            public String getVersion() {
                return "tabela-de-teste · " + tabela.size() + " entradas";
            }
        };
    }
}
