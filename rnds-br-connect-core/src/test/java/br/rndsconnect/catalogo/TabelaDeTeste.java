package br.rndsconnect.catalogo;

import br.rndsconnect.model.CodedTarget;
import br.rndsconnect.model.TerminologyDomain;
import br.rndsconnect.semantic.MappingSource;
import java.util.Map;

/**
 * Tabela de mapeamento montada em memória, para os testes da conferência contra um catálogo.
 *
 * @author Igor Mendes Domingues Miras
 */
final class TabelaDeTeste {

    private TabelaDeTeste() {}

    /**
     * @param system URL canônica do CodeSystem de destino
     * @param code código de destino
     * @return o alvo, sem display nem responsável
     */
    static CodedTarget alvo(String system, String code) {
        return new CodedTarget(system, code, null);
    }

    /**
     * @param dominio domínio que a tabela alimenta
     * @param tabela código local → alvo
     * @return a fonte de mapeamento correspondente
     */
    static MappingSource de(TerminologyDomain dominio, Map<String, CodedTarget> tabela) {
        return new MappingSource() {
            @Override
            public TerminologyDomain getDomain() {
                return dominio;
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
