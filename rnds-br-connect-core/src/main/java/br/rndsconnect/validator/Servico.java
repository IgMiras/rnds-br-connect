package br.rndsconnect.validator;

/**
 * Os serviços da RNDS que o framework atende.
 *
 * <p>A lista corresponde aos serviços com <strong>manual de integração publicado</strong> no
 * Portal de Serviços do DATASUS. O critério é objetivo e verificável, e corresponde ao que um
 * integrador de fato consegue implementar: sem manual não há regra de negócio, fluxo de
 * credenciamento nem catálogo de erros.
 *
 * <p><strong>Perfil de {@code Composition} não é serviço.</strong> Os pacotes de artefatos
 * contêm também rotas internas, cargas em lote via SISAB ({@code ...Carga-2.0}) e perfis de
 * contexto específico ({@code ...TesteRapido-1.0}, COVID-19). Contá-los como serviços produz
 * uma lista inflada de mais de vinte "serviços" que ninguém integra.
 *
 * <p>Note que <strong>RIA é um serviço, não dois</strong>: rotina e campanha são modalidades
 * sob um único manual, com perfis FHIR distintos. E <strong>RPM e RDM não existem
 * separadamente</strong> — foram unificados no REDFM em 2025.
 *
 * @author Igor Mendes Domingues Miras
 */
public enum Servico {

    /** Registro de Imunobiológico Administrado — rotina e campanha. */
    RIA("Registro de Imunobiológico Administrado"),

    /** Resultado de Exame Laboratorial. */
    REL("Resultado de Exame Laboratorial"),

    /** Registro de Informação de Regulação Assistencial. */
    RIRA("Registro de Informação de Regulação Assistencial"),

    /**
     * Registro Eletrônico da Prescrição, Dispensação ou Fornecimento de Medicamentos.
     *
     * <p>Primeiro serviço da geração {@code RNDS*}, publicado em 2025. <strong>Deriva do
     * BR-Core</strong> — oito de seus perfis têm {@code baseDefinition} apontando para
     * {@code br-core.saude.gov.br}, inclusive o perfil de documento. Seu pacote de
     * conformidade precisa incluir o IG BR-Core, distribuído em JSON pelo Simplifier.
     */
    REDFM("Registro Eletrônico da Prescrição, Dispensação ou Fornecimento de Medicamentos"),

    /** Sumário de Alta. */
    SA("Sumário de Alta"),

    /** Registro de Atendimento Clínico. */
    RAC("Registro de Atendimento Clínico");

    private final String descricao;

    Servico(String descricao) {
        this.descricao = descricao;
    }

    /** @return o nome do serviço por extenso, como no manual */
    public String descricao() {
        return descricao;
    }
}
