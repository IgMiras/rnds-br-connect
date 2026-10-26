package br.rndsconnect.semantic;

import java.time.Instant;

/**
 * Contexto de uma operação de mapeamento — o que a proveniência precisa saber além do
 * próprio código.
 *
 * <p><strong>Por que existe o {@code indiceItem}.</strong> Nos serviços de imunização, um
 * registro produz no máximo uma tradução por domínio, e o {@code idLocalRegistro} basta para
 * situar cada linha da trilha. No REL não basta: um laudo com três exames traduz
 * {@code NOME_EXAME} três vezes, {@code TIPO_AMOSTRA} três vezes, e assim por diante. Sem
 * discriminador, a trilha registraria treze traduções todas com o mesmo identificador, e
 * seria impossível dizer qual amostra corresponde a qual exame.
 *
 * <p>Confiar na ordem da lista resolveria na prática e falharia na auditoria: ordem é
 * garantia implícita, e proveniência existe justamente para não depender do implícito.
 *
 * @param idLocalRegistro identificador do registro na base de origem
 * @param mappedAt instante da tradução
 * @param indiceItem posição do item dentro do registro, quando o serviço admite lista;
 *     {@code null} para traduções do registro como um todo
 * @author Igor Mendes Domingues Miras
 */
public record MappingContext(String idLocalRegistro, Instant mappedAt, Integer indiceItem) {

    /**
     * Contexto sem discriminador de item — o caso dos serviços que não admitem lista.
     *
     * <p>Existe para que acrescentar o {@code indiceItem} não obrigue quem não tem itens a
     * passar {@code null} explicitamente.
     *
     * @param idLocalRegistro identificador do registro na base de origem
     * @param mappedAt instante da tradução
     */
    public MappingContext(String idLocalRegistro, Instant mappedAt) {
        this(idLocalRegistro, mappedAt, null);
    }

    /**
     * @param idLocalRegistro identificador do registro na base de origem
     * @return contexto sem item, marcado no instante presente
     */
    public static MappingContext agora(String idLocalRegistro) {
        return new MappingContext(idLocalRegistro, Instant.now(), null);
    }

    /**
     * @param indice posição do item dentro do registro
     * @return cópia deste contexto apontando para o item indicado, preservando o instante
     */
    public MappingContext noItem(int indice) {
        return new MappingContext(idLocalRegistro, mappedAt, indice);
    }
}
