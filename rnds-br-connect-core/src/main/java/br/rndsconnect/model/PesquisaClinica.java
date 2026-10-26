package br.rndsconnect.model;

import lombok.Builder;
import lombok.Value;

/**
 * Sub-estrutura da extensão complexa {@code BREstrategiaVacinacaoPesquisa-1.0}.
 *
 * <p>As três sub-extensões são {@code string} e suas URLs são relativas — {@code
 * numeroProtocoloEstudoANVISA}, {@code numeroVersaoProtocoloEstudo} e {@code
 * numeroRegistroVacinaAnvisa}. Note a inconsistência de caixa entre a primeira (ANVISA
 * maiúsculo) e a terceira (Anvisa): é assim no artefato oficial, e o Builder emite as URLs
 * literais, sem normalizar.
 *
 * @author Igor Mendes Domingues Miras
 */
@Value
@Builder
public class PesquisaClinica {
    String numeroProtocoloEstudoAnvisa;
    String numeroVersaoProtocoloEstudo;
    String numeroRegistroVacinaAnvisa;
}
