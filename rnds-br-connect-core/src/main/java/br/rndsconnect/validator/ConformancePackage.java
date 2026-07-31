package br.rndsconnect.validator;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.parser.LenientErrorHandler;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.hl7.fhir.r4.model.ValueSet;

/**
 * Pacote de conformidade da RNDS: os artefatos de definição FHIR publicados pelo
 * Ministério da Saúde no Portal de Serviços do DATASUS.
 *
 * <p>O DATASUS <strong>não</strong> distribui um pacote NPM: distribui recursos de
 * conformidade avulsos em XML — StructureDefinition, ValueSet e CodeSystem — acompanhados
 * de um validador desktop. Esta classe faz a carga desses arquivos exatamente na forma em
 * que são publicados, que é o que sustenta o ADR-03.
 *
 * <p>Como o classpath não é enumerável de forma portátil quando empacotado em JAR, a lista
 * de arquivos é lida de um índice textual (<code>index.txt</code>) gerado pelo script
 * <code>scripts/copiar-conformance.sh</code>. Uma entrada por linha; linhas iniciadas por
 * <code>#</code> são comentário.
 *
 * <p><strong>Atenção — os perfis da RNDS não trazem snapshot.</strong> Todos os
 * StructureDefinition publicados contêm apenas <code>differential</code>. A cadeia de
 * validação precisa, por isso, do elo <code>SnapshotGeneratingValidationSupport</code>
 * (ver {@link LocalFhirValidator}); sem ele o HAPI não consegue resolver o perfil e a
 * validação passa a ser silenciosamente inócua.
 *
 * @author Igor Mendes Domingues Miras
 */
public final class ConformancePackage {

    /** Diretório padrão dos artefatos dentro do classpath. */
    public static final String DIRETORIO_PADRAO = "conformance";

    private final List<StructureDefinition> structureDefinitions = new ArrayList<>();
    private final List<ValueSet> valueSets = new ArrayList<>();
    private final List<CodeSystem> codeSystems = new ArrayList<>();
    private final List<String> ignorados = new ArrayList<>();
    private final long tempoCargaMillis;

    private ConformancePackage(FhirContext ctx, String diretorio) {
        long inicio = System.currentTimeMillis();
        IParser parser = ctx.newXmlParser();
        parser.setParserErrorHandler(new LenientErrorHandler());

        for (String arquivo : lerIndice(diretorio)) {
            String recurso = diretorio + "/" + arquivo;
            try (InputStream in = abrir(recurso)) {
                Object parsed = parser.parseResource(new InputStreamReader(in, StandardCharsets.UTF_8));
                if (parsed instanceof StructureDefinition sd) {
                    structureDefinitions.add(sd);
                } else if (parsed instanceof ValueSet vs) {
                    valueSets.add(vs);
                } else if (parsed instanceof CodeSystem cs) {
                    codeSystems.add(cs);
                } else {
                    ignorados.add(arquivo);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Falha ao ler o artefato de conformidade: " + recurso, e);
            }
        }
        this.tempoCargaMillis = System.currentTimeMillis() - inicio;
    }

    /**
     * Carrega o pacote a partir do diretório padrão no classpath.
     *
     * @param ctx contexto FHIR R4 já inicializado
     * @return pacote pronto para alimentar o {@link LocalFhirValidator}
     */
    public static ConformancePackage fromClasspath(FhirContext ctx) {
        return fromClasspath(ctx, DIRETORIO_PADRAO);
    }

    /**
     * Carrega o pacote a partir de um diretório arbitrário do classpath.
     *
     * @param ctx contexto FHIR R4 já inicializado
     * @param diretorio caminho no classpath, sem barra inicial nem final
     * @return pacote pronto para alimentar o {@link LocalFhirValidator}
     */
    public static ConformancePackage fromClasspath(FhirContext ctx, String diretorio) {
        return new ConformancePackage(ctx, diretorio);
    }

    private static List<String> lerIndice(String diretorio) {
        String indice = diretorio + "/index.txt";
        List<String> arquivos = new ArrayList<>();
        try (InputStream in = abrir(indice);
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String linha;
            while ((linha = r.readLine()) != null) {
                String limpa = linha.trim();
                if (!limpa.isEmpty() && !limpa.startsWith("#")) {
                    arquivos.add(limpa);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler o índice de conformidade: " + indice, e);
        }
        if (arquivos.isEmpty()) {
            throw new IllegalStateException(
                    "Índice de conformidade vazio: " + indice + ". Execute scripts/copiar-conformance.sh.");
        }
        return arquivos;
    }

    private static InputStream abrir(String recurso) {
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(recurso);
        if (in == null) {
            in = ConformancePackage.class.getClassLoader().getResourceAsStream(recurso);
        }
        if (in == null) {
            throw new IllegalStateException("Artefato de conformidade ausente no classpath: " + recurso);
        }
        return in;
    }

    /** @return os StructureDefinition carregados (perfis e extensões da RNDS) */
    public List<StructureDefinition> structureDefinitions() {
        return Collections.unmodifiableList(structureDefinitions);
    }

    /** @return os ValueSet carregados */
    public List<ValueSet> valueSets() {
        return Collections.unmodifiableList(valueSets);
    }

    /** @return os CodeSystem carregados */
    public List<CodeSystem> codeSystems() {
        return Collections.unmodifiableList(codeSystems);
    }

    /** @return arquivos do índice cujo tipo de recurso não é de conformidade */
    public List<String> ignorados() {
        return Collections.unmodifiableList(ignorados);
    }

    /**
     * Tempo de carga dos artefatos, em milissegundos.
     *
     * <p>Este número é o custo de inicialização que a Arquitetura §9 e o ADR-03 declaram
     * como pendente de medição. Registrá-lo aqui é o que permite citá-lo no TCC como
     * medição própria, e não como estimativa.
     *
     * @return duração da carga em milissegundos
     */
    public long tempoCargaMillis() {
        return tempoCargaMillis;
    }

    /** @return total de artefatos efetivamente carregados */
    public int total() {
        return structureDefinitions.size() + valueSets.size() + codeSystems.size();
    }
}
