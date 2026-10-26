package br.rndsconnect.semantic;

import br.rndsconnect.model.CodedTarget;
import br.rndsconnect.model.TerminologyDomain;
import java.util.Map;

/**
 * Fonte de uma tabela de correspondência terminológica.
 *
 * <p>Separar a <em>fonte</em> da <em>estratégia</em> é o que permite trocar o formato de
 * armazenamento sem tocar na lógica de tradução, e é o que sustenta o argumento P1 da Seção
 * 3.1: a tabela vive num artefato que um profissional de saúde consegue ler e revisar, não
 * num {@code switch} compilado.
 *
 * @author Igor Mendes Domingues Miras
 */
public interface MappingSource {

    /** @return o domínio que esta fonte alimenta */
    TerminologyDomain getDomain();

    /**
     * @return a tabela completa: código local → alvo na RNDS
     * @throws MappingSourceException se a fonte não puder ser lida ou estiver malformada
     */
    Map<String, CodedTarget> load();

    /**
     * Identificação verificável da fonte, para a proveniência.
     *
     * <p>Inclui o nome do artefato e o SHA-256 do conteúdo carregado. O hash é o que
     * transforma "esta tradução veio de vacinas.yml" em afirmação auditável: dois anos
     * depois, é possível provar qual conteúdo exato produziu um registro.
     *
     * @return algo como {@code vacinas.yml · sha256:a3f2c1d4 · 4 entradas}
     */
    String getVersion();
}
