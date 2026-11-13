package br.rndsconnect.catalogo;

import br.rndsconnect.model.CodedTarget;
import br.rndsconnect.semantic.MappingSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Confere a tabela de mapeamento da instituição contra um catálogo de códigos aceitos pela
 * RNDS, <strong>na inicialização</strong>.
 *
 * <h2>O problema que resolve</h2>
 *
 * <p>Uma correspondência que aponte para código fora do catálogo produz Bundle reprovado pela
 * Camada 4 — o binding {@code required} não deixa passar. Mas a reprovação só acontece quando
 * o <strong>primeiro paciente daquele código</strong> for enviado, o que pode ser meses depois
 * de a tabela ter sido escrita. Até lá, ninguém sabe que há uma linha errada.
 *
 * <p>Esta classe antecipa a descoberta para o momento em que a aplicação sobe, e o
 * diagnóstico aponta a linha da tabela em vez de um caminho FHIR.
 *
 * <p>Serve a qualquer domínio que tenha catálogo: recebe o {@link CatalogoCodigosAceitos} do
 * elemento e a fonte de mapeamento do domínio correspondente. Hoje há dois, o dos nomes de
 * exame do REL e o das medidas observadas do RAC.
 *
 * <h2>Os dois modos de erro que ela pega</h2>
 *
 * <p><strong>Código inexistente.</strong> Um código digitado errado, ou declarado no
 * CodeSystem errado. Nos exames, por exemplo, o LOINC e o GAL usam formatos incompatíveis, e
 * um código na forma errada denuncia o CodeSystem errado.
 *
 * <p><strong>Código existente mas não aceito.</strong> Os 7 códigos que o
 * {@code compose.exclude} do {@code BRNomeExame-2.0} remove estão no {@code BRNomeExameLOINC}
 * e não são aceitos pela RNDS. Uma tabela montada a partir do CodeSystem, sem ler o ValueSet,
 * os incluiria — e esse é justamente o erro que a diferença entre 484 e 477 representa.
 *
 * <p>A conferência é <strong>relatório, não exceção</strong>: devolve todas as divergências de
 * uma vez. Aqui, ao contrário da cadeia da Frente A, as ocorrências são independentes entre
 * si — quem revisa uma tabela quer a lista inteira, não a primeira linha errada.
 *
 * <p>Ela <strong>não</strong> diz se a correspondência está clinicamente certa (ADR-05): um
 * código aceito que designa outro conceito passa aqui e em qualquer validador.
 *
 * @author Igor Mendes Domingues Miras
 */
public final class MapeamentoConformeCatalogo {

    private final CatalogoCodigosAceitos catalogo;

    /**
     * @param catalogo catálogo de referência, do mesmo elemento que a tabela a conferir alimenta
     */
    public MapeamentoConformeCatalogo(CatalogoCodigosAceitos catalogo) {
        this.catalogo = Objects.requireNonNull(catalogo, "catalogo é obrigatório");
    }

    /**
     * Uma correspondência da tabela que o catálogo não aceita.
     *
     * @param codigoLocal chave na tabela da instituição
     * @param alvo par ({@code system}, {@code code}) para o qual ela aponta
     * @param motivo por que não é aceito
     */
    public record Divergencia(String codigoLocal, CodedTarget alvo, String motivo) {
        @Override
        public String toString() {
            return String.format("\"%s\" -> %s#%s : %s", codigoLocal, alvo.system(), alvo.code(), motivo);
        }
    }

    /**
     * @param fonte tabela de mapeamento do domínio a que o catálogo se refere
     * @return divergências encontradas; lista vazia se a tabela inteira é aceita
     */
    public List<Divergencia> conferir(MappingSource fonte) {
        Objects.requireNonNull(fonte, "fonte é obrigatória");
        List<Divergencia> divergencias = new ArrayList<>();

        for (Map.Entry<String, CodedTarget> e : fonte.load().entrySet()) {
            CodedTarget alvo = e.getValue();
            // toCodedValue() é a conversão explícita entre os dois tipos: CodedTarget carrega
            // display e validatedBy, que existem para a revisão humana e não têm papel aqui.
            if (catalogo.aceita(alvo.toCodedValue())) {
                continue;
            }
            String motivo;
            if (!catalogo.systems().contains(alvo.system())) {
                motivo = "CodeSystem fora do catálogo — o elemento aceita códigos apenas de "
                        + String.join(", ", catalogo.systems());
            } else if (catalogo.display(alvo.system(), alvo.code()).isEmpty()) {
                motivo = "código não consta entre os " + catalogo.totalDe(alvo.system())
                        + " aceitos deste CodeSystem — verifique digitação, ou se o código "
                        + "está entre os removidos pelo compose.exclude do ValueSet";
            } else {
                motivo = "não aceito";
            }
            divergencias.add(new Divergencia(e.getKey(), alvo, motivo));
        }
        return Collections.unmodifiableList(divergencias);
    }

    /**
     * Confere e interrompe a inicialização se houver divergência.
     *
     * @param fonte tabela de mapeamento do domínio a que o catálogo se refere
     * @throws IllegalStateException se alguma correspondência não for aceita
     */
    public void exigirConformidade(MappingSource fonte) {
        List<Divergencia> divergencias = conferir(fonte);
        if (divergencias.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder()
                .append("A tabela de mapeamento do domínio ")
                .append(fonte.getDomain())
                .append(" aponta para ")
                .append(divergencias.size())
                .append(" código(s) que a RNDS não aceita.")
                .append(System.lineSeparator())
                .append("Fonte: ")
                .append(fonte.getVersion())
                .append(System.lineSeparator())
                .append("Catálogo: ")
                .append(catalogo.recurso())
                .append(", ")
                .append(catalogo.total())
                .append(" códigos, sha256:")
                .append(catalogo.sha256())
                .append(System.lineSeparator());
        divergencias.forEach(d -> sb.append("  ").append(d).append(System.lineSeparator()));
        throw new IllegalStateException(sb.toString());
    }
}
