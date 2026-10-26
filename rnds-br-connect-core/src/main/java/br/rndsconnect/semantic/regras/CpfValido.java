package br.rndsconnect.semantic.regras;

import br.rndsconnect.semantic.RegraValidacao;
import br.rndsconnect.semantic.RndsValidationException;
import java.util.Objects;
import java.util.function.Function;

/**
 * Elo 2 (A1) — valida o CPF pelos dois dígitos verificadores.
 *
 * <p>Módulo 11 com pesos decrescentes, como o CNS, mas em duas passagens: o primeiro dígito
 * é calculado sobre os nove primeiros com pesos de 10 a 2; o segundo, sobre os dez primeiros
 * com pesos de 11 a 2. Resto 0 ou 1 produz dígito 0.
 *
 * <p>Sequências de dígito repetido — {@code 000...}, {@code 111...} — satisfazem o Módulo 11
 * por acidente aritmético e são rejeitadas explicitamente. É o caso que mais aparece em base
 * de produção, tipicamente como preenchimento de campo obrigatório sem dado real.
 *
 * @param <T> tipo do DTO
 * @author Igor Mendes Domingues Miras
 */
public class CpfValido<T> implements RegraValidacao<T> {

    /** Identificação da regra nas mensagens de erro. */
    public static final String REGRA = "ELO-2/CPF-MOD11";

    private final Function<T, String> extrator;
    private final String campo;
    private final boolean obrigatorio;

    /**
     * @param campo nome do campo no DTO
     * @param extrator como obter o CPF
     * @param obrigatorio se {@code false}, valor ausente é aceito
     */
    public CpfValido(String campo, Function<T, String> extrator, boolean obrigatorio) {
        this.campo = Objects.requireNonNull(campo);
        this.extrator = Objects.requireNonNull(extrator);
        this.obrigatorio = obrigatorio;
    }

    @Override
    public void validar(T alvo) {
        String bruto = extrator.apply(alvo);
        if (bruto == null || bruto.isBlank()) {
            if (obrigatorio) {
                throw new RndsValidationException(campo, REGRA, "CPF obrigatório e ausente", null);
            }
            return;
        }
        String cpf = bruto.replaceAll("\\D", "");

        if (cpf.length() != 11) {
            throw new RndsValidationException(campo, REGRA, "CPF deve ter 11 dígitos; recebeu " + cpf.length(), bruto);
        }
        if (cpf.chars().distinct().count() == 1) {
            throw new RndsValidationException(
                    campo,
                    REGRA,
                    "sequência de dígito repetido satisfaz o Módulo 11 por acidente e não é CPF válido",
                    bruto);
        }
        if (digito(cpf, 9, 10) != Character.getNumericValue(cpf.charAt(9))
                || digito(cpf, 10, 11) != Character.getNumericValue(cpf.charAt(10))) {
            throw new RndsValidationException(campo, REGRA, "dígitos verificadores não conferem", bruto);
        }
    }

    private static int digito(String cpf, int ate, int pesoInicial) {
        int soma = 0;
        for (int i = 0; i < ate; i++) {
            soma += Character.getNumericValue(cpf.charAt(i)) * (pesoInicial - i);
        }
        int d = (soma * 10) % 11;
        return d == 10 ? 0 : d;
    }

    @Override
    public String identificacao() {
        return REGRA + "(" + campo + ")";
    }
}
