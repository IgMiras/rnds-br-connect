package br.rndsconnect.semantic.regras;

import br.rndsconnect.semantic.RegraValidacao;
import br.rndsconnect.semantic.RndsValidationException;
import java.util.Objects;
import java.util.function.Function;

/**
 * Elo 1 (A1) — valida o Cartão Nacional de Saúde pelo Módulo 11.
 *
 * <h2>O algoritmo</h2>
 *
 * <p>O CNS tem 15 dígitos. O primeiro indica a faixa: <strong>1 e 2</strong> são definitivos,
 * derivados do PIS/PASEP nos 11 primeiros dígitos; <strong>7, 8 e 9</strong> são provisórios,
 * emitidos antes da vinculação ao CPF.
 *
 * <p>As duas faixas são descritas na literatura com algoritmos diferentes — soma ponderada
 * dos 15 dígitos para os provisórios, cálculo de dígito verificador sobre o PIS para os
 * definitivos. <strong>Os dois se reduzem à mesma verificação</strong>, e vale a pena mostrar
 * por quê.
 *
 * <p>No definitivo, o resultado é {@code pis + "000" + dv}, com
 * {@code dv = 11 − (s mod 11)} e {@code s = Σ pis[i] × (15−i)}. A soma ponderada dos 15
 * dígitos é então {@code s + 0×4 + 0×3 + 0×2 + dv×1 = s + 11 − (s mod 11)}, que é múltiplo de
 * 11 por construção. O caso {@code "001"} segue o mesmo raciocínio somando 2. Logo:
 *
 * <blockquote>a soma ponderada dos 15 dígitos, com pesos de 15 a 1, é divisível por 11 —
 * para <strong>ambas</strong> as faixas.</blockquote>
 *
 * <h2>Escopo deliberado da verificação</h2>
 *
 * <p>Esta implementação verifica <strong>tamanho, faixa do primeiro dígito e Módulo 11</strong>.
 * Não impõe a estrutura interna dos definitivos (posições 12 a 14 iguais a {@code "000"} ou
 * {@code "001"}), e a omissão é escolha: as fontes públicas divergem sobre quais faixas são
 * definitivas e quais são provisórias, e um validador estrito demais rejeita paciente
 * legítimo. <strong>Rejeitar um CNS válido é pior que aceitar um inválido</strong> — o
 * primeiro nega atendimento; o segundo é apanhado adiante pelo cadastro nacional.
 *
 * <p>Note que os CNS dos exemplos oficiais da RNDS <strong>não passam</strong> nesta
 * verificação: {@code 898005900000823} tem resto 6, e {@code 012345678912345} começa com
 * dígito fora de qualquer faixa. Isso não é defeito dos exemplos — dado de exemplo deve ser
 * fictício —, mas explica por que os artefatos de referência do projeto preservam esses
 * números enquanto as fixturas da Facade usam CNS matematicamente válidos.
 *
 * @param <T> tipo do DTO
 * @author Igor Mendes Domingues Miras
 */
public class CnsValido<T> implements RegraValidacao<T> {

    /** Identificação da regra nas mensagens de erro. */
    public static final String REGRA = "ELO-1/CNS-MOD11";

    private static final String FAIXAS_VALIDAS = "12789";

    private final Function<T, String> extrator;
    private final String campo;
    private final boolean obrigatorio;

    /**
     * @param campo nome do campo no DTO, para o diagnóstico
     * @param extrator como obter o CNS a partir do DTO
     * @param obrigatorio se {@code false}, valor ausente é aceito
     */
    public CnsValido(String campo, Function<T, String> extrator, boolean obrigatorio) {
        this.campo = Objects.requireNonNull(campo);
        this.extrator = Objects.requireNonNull(extrator);
        this.obrigatorio = obrigatorio;
    }

    @Override
    public void validar(T alvo) {
        String bruto = extrator.apply(alvo);
        if (bruto == null || bruto.isBlank()) {
            if (obrigatorio) {
                throw new RndsValidationException(campo, REGRA, "CNS obrigatório e ausente", null);
            }
            return;
        }
        String cns = bruto.replaceAll("\\D", "");

        if (cns.length() != 15) {
            throw new RndsValidationException(campo, REGRA, "CNS deve ter 15 dígitos; recebeu " + cns.length(), bruto);
        }
        if (FAIXAS_VALIDAS.indexOf(cns.charAt(0)) < 0) {
            throw new RndsValidationException(
                    campo,
                    REGRA,
                    "primeiro dígito '" + cns.charAt(0) + "' fora das faixas conhecidas "
                            + "(1 e 2 definitivos; 7, 8 e 9 provisórios)",
                    bruto);
        }
        int soma = somaPonderada(cns);
        if (soma % 11 != 0) {
            throw new RndsValidationException(
                    campo,
                    REGRA,
                    "dígito verificador inválido — soma ponderada " + soma + " não é múltipla de 11 " + "(resto "
                            + (soma % 11) + "). Verifique dígitos trocados de posição",
                    bruto);
        }
    }

    /**
     * @param cns 15 dígitos
     * @return Σ dígito[i] × (15 − i)
     */
    public static int somaPonderada(String cns) {
        int soma = 0;
        for (int i = 0; i < cns.length(); i++) {
            soma += Character.getNumericValue(cns.charAt(i)) * (15 - i);
        }
        return soma;
    }

    @Override
    public String identificacao() {
        return REGRA + "(" + campo + ")";
    }
}
