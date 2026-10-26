package br.rndsconnect.semantic;

/**
 * Fonte de mapeamento ausente, ilegível ou malformada.
 *
 * <p>Falha de inicialização, não de tradução: acontece na carga, antes de qualquer registro
 * clínico ser processado.
 *
 * @author Igor Mendes Domingues Miras
 */
public class MappingSourceException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public MappingSourceException(String mensagem) {
        super(mensagem);
    }

    public MappingSourceException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
