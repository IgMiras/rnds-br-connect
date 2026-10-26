package br.rndsconnect.semantic;

/**
 * Uma regra de negócio do SUS foi violada (Camada 2, Frente A).
 *
 * <p>Distingue-se da {@link UnmappedTerminologyException} pelo tipo de problema: aquela diz
 * que um código não tem tradução; esta diz que o dado, mesmo traduzível, viola uma regra que
 * <strong>nenhum perfil FHIR expressa</strong>.
 *
 * <p>É a diferença que justifica a Frente A existir. Um laudo com resultado quantitativo e sem
 * interpretação passa em qualquer validador FHIR — {@code Observation.interpretation} é
 * {@code 0..1} no perfil — e viola a RN3 do manual REL. A Camada 4 nunca o pegaria, porque a
 * regra não está no perfil: está na prosa do manual.
 *
 * @author Igor Mendes Domingues Miras
 */
public class RndsValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String campo;
    private final String valorRecebido;
    private final String regra;

    /**
     * @param campo campo do DTO que violou a regra
     * @param regra identificação da regra, para correlacionar com o manual
     * @param motivo explicação acionável
     * @param valorRecebido valor que chegou, para o diagnóstico
     */
    public RndsValidationException(String campo, String regra, String motivo, String valorRecebido) {
        super(String.format(
                "[%s] %s: %s%s",
                regra, campo, motivo, valorRecebido != null ? " (recebido: \"" + valorRecebido + "\")" : ""));
        this.campo = campo;
        this.regra = regra;
        this.valorRecebido = valorRecebido;
    }

    /** @return campo do DTO que violou a regra */
    public String getCampo() {
        return campo;
    }

    /** @return identificação da regra violada */
    public String getRegra() {
        return regra;
    }

    /** @return valor que chegou */
    public String getValorRecebido() {
        return valorRecebido;
    }
}
