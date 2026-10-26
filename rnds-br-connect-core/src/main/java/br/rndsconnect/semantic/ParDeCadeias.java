package br.rndsconnect.semantic;

import java.util.Objects;

/**
 * O par de cadeias da Frente A de um serviço: A1 antes da tradução, A2 depois.
 *
 * <p>Existe por pressão de assinatura. Com um campo por cadeia, sete serviços dão catorze
 * parâmetros do mesmo tipo genérico lado a lado no construtor de injeção — e trocar duas
 * posições por engano <strong>compila</strong>. O par nomeia o que pertence junto.
 *
 * <p><strong>A alternativa descartada</strong> era um {@code Map<Servico, CadeiaDeValidacao<?>>},
 * de assinatura curta para sempre. Ela exige um <em>cast</em> na recuperação, e com ele um
 * registro errado deixa de ser erro de compilação e passa a ser {@code ClassCastException} no
 * meio de um envio — o oposto do fail-fast que a Camada 4 defende. Aqui {@code B} é o DTO
 * canônico do serviço e {@code E} o enriquecido, e não há <em>cast</em> em lugar nenhum.
 *
 * @param <B> DTO canônico, no vocabulário local da instituição
 * @param <E> DTO enriquecido, com os códigos já traduzidos pela Frente B
 * @param a1 regras estruturais, aplicadas antes da Frente B
 * @param a2 regras semânticas, aplicadas depois da Frente B (Seção 7.1)
 * @author Igor Mendes Domingues Miras
 */
public record ParDeCadeias<B, E>(CadeiaDeValidacao<B> a1, CadeiaDeValidacao<E> a2) {

    /** @throws NullPointerException se alguma das cadeias for nula */
    public ParDeCadeias {
        Objects.requireNonNull(a1, "a1 é obrigatória");
        Objects.requireNonNull(a2, "a2 é obrigatória");
    }

    /**
     * Par sem nenhum elo.
     *
     * <p>Serve a serviço cuja cadeia ainda não existe — e deixa isso explícito no lugar do
     * silêncio, que é o estado de que o REDFM, o SA e o RAC vinham.
     *
     * @param <B> DTO canônico
     * @param <E> DTO enriquecido
     * @return par de cadeias vazias
     */
    public static <B, E> ParDeCadeias<B, E> vazio() {
        return new ParDeCadeias<>(CadeiaDeValidacao.vazia(), CadeiaDeValidacao.vazia());
    }
}
