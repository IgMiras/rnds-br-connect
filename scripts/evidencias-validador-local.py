#!/usr/bin/env python3
"""Gera as evidências de conformidade: o mesmo arquivo na Camada 4 e no Validador Local da RNDS.

Quatro etapas, nesta ordem:

  1. EXPORTAÇÃO. Roda os testes dos sete construtores e o ExportarBundlesTest com a variável
     RNDS_EVIDENCIAS_DIR apontando para evidencias/validador-local/documentos. Cada documento
     sai com o nome "<sigla>--<caso>.json", e a sigla decide o pacote de cada validador.
  2. CANÁRIOS. Deriva de quatro documentos aprovados quatro documentos que violam o perfil,
     um por pacote de conformidade distinto. Um canário aprovado indica que o validador deixou
     de aplicar o pacote, e o script falha.
  3. VALIDAÇÃO. Submete cada arquivo (documentos, canários e o exemplo oficial em controle/)
     à Camada 4, com o pacote do serviço que o framework carrega, e ao Validador Local, com a
     distribuição inteira do serviço tirada do repositório de artefatos.
  4. RESULTADO. Escreve evidencias/validador-local/RESULTADOS.md e sai com código 1 se houver
     divergência que não esteja em DIVERGENCIAS_CONHECIDAS.

O Validador Local não é copiado para este repositório: é um aplicativo do DATASUS, obtido no
Portal de Serviços, e o script recebe a pasta dele. A chamada é a mesma que a janela faz
(scripts/ValidadorLocalSemJanela.java).

Uso:
  python3 scripts/evidencias-validador-local.py \\
      --validador-local <pasta rnds-fhir-validador, com libs/> \\
      --artefatos <clone de github.com/IgMiras/rnds-artefatos> [--offline]
"""

import argparse
import datetime
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from collections import defaultdict
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
SAIDA = RAIZ / "evidencias" / "validador-local"
VARIAVEL = "RNDS_EVIDENCIAS_DIR"

# sigla -> (Servico da Camada 4, pacote no classpath do core, pasta no repositório de artefatos)
PACOTES = {
    "ria-r": ("RIA", "conformance", "br-core-ril-ria-rel"),
    "ria-c": ("RIA", "conformance", "br-core-ril-ria-rel"),
    "rel": ("REL", "conformance", "br-core-ril-ria-rel"),
    "rira": ("RIRA", "conformance/rira", "Artefatos_Validador_RIRA"),
    "redfm": ("REDFM", "conformance/redfm", "modelagem-redfm"),
    "sa": ("SA", "conformance/sa", "arquivos_definicoes_SA"),
    "rac": ("RAC", "conformance/rac", "arquivos_definicoes_SA"),
}

# nome do arquivo -> (Camada 4 esperada, Validador Local esperado, explicação)
#
# Com o HAPI FHIR 7.6.0 havia uma segunda divergência: o exemplo oficial do RIA-R, com
# urn:uuid:transient-N (defeito nº 6), só a Camada 4 reprovava. Ela vinha da versão do motor e
# sumiu com a migração para a 5.1.0, a versão do Validador Local: os dois aprovam.
DIVERGENCIAS_CONHECIDAS = {
    "redfm--variacao-2-substituicao": (
        "APROVADO",
        "REPROVADO",
        "O vínculo required de Composition.relatesTo.code, herdado do br-core-composition, "
        "aponta para uma página de documentação (defeito nº 12). Com o mesmo motor, o HAPI FHIR "
        "5.1.0, e a mesma mensagem, a Camada 4 registra o ValueSet não encontrado como aviso, e o "
        "Validador Local, como erro: a diferença vem da configuração ou do pacote, e não da versão.",
    ),
}


# ── Canários ────────────────────────────────────────────────────────────────────


def _recursos(bundle, tipo):
    return [e["resource"] for e in bundle["entry"] if e["resource"]["resourceType"] == tipo]


def _sem_route(b):
    return sum(1 for r in _recursos(b, "Immunization") if r.pop("route", None) is not None)


def _sem_event(b):
    return sum(1 for r in _recursos(b, "Composition") if r.pop("event", None) is not None)


def _cmd1_violada(b):
    n = 0
    for r in _recursos(b, "Procedure"):
        for i in r.get("identifier", []):
            i["value"] = "ABC"
            n += 1
    return n


def _sem_quantity(b):
    return sum(1 for r in _recursos(b, "MedicationDispense") if r.pop("quantity", None) is not None)


# canário -> (documento de origem, alteração, o que muda)
CANARIOS = {
    "ria-r--canario-sem-route": ("ria-r--minimo", _sem_route, "sem `Immunization.route`, 1..1 no perfil"),
    "rira--canario-sem-event": ("rira--um-evento", _sem_event, "sem `Composition.event`, 1..* no perfil"),
    "rac--canario-cmd-1": ("rac--completo", _cmd1_violada, "com `Procedure.identifier` fora da máscara da invariante cmd-1"),
    "redfm--canario-dfm-2": ("redfm--receita-embutida", _sem_quantity, "sem `MedicationDispense.quantity`, que a invariante dfm-2 exige"),
}


def gerar_canarios(documentos, destino):
    destino.mkdir(parents=True)
    for nome, (origem, alterar, _) in CANARIOS.items():
        bundle = json.loads((documentos / f"{origem}.json").read_text(encoding="utf-8"))
        if alterar(bundle) == 0:
            sys.exit(f"canário {nome}: nada a alterar em {origem}.json")
        (destino / f"{nome}.json").write_text(
            json.dumps(bundle, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


# ── Execução dos validadores ────────────────────────────────────────────────────


def mvn(*argumentos, offline, env=None):
    comando = ["mvn", "-B", "-q"] + (["-o"] if offline else []) + list(argumentos)
    subprocess.run(comando, cwd=RAIZ, env=env, check=True)


def classpath_do_core(offline):
    with tempfile.TemporaryDirectory() as tmp:
        arquivo = Path(tmp) / "cp.txt"
        mvn("-pl", "rnds-br-connect-core", "dependency:build-classpath",
            f"-Dmdep.outputFile={arquivo}", offline=offline)
        dependencias = arquivo.read_text().strip()
    return f"{RAIZ / 'rnds-br-connect-core' / 'target' / 'classes'}{os.pathsep}{dependencias}"


def camada_quatro(arquivos, classpath, mensagens):
    por_pacote = defaultdict(list)
    for a in arquivos:
        servico, pacote, _ = PACOTES[sigla(a)]
        por_pacote[(servico, pacote)].append(a)

    veredictos = {}
    for (servico, pacote), lista in sorted(por_pacote.items()):
        print(f"  Camada 4 · {servico} ({len(lista)} arquivos)", flush=True)
        saida = subprocess.run(
            ["java", "-Xmx2g", "-cp", classpath, str(RAIZ / "scripts" / "CamadaQuatroEmArquivos.java"),
             servico, pacote, str(mensagens)] + [str(a) for a in lista],
            cwd=RAIZ, check=True, capture_output=True, text=True).stdout
        for linha in saida.splitlines():
            if linha.startswith("RESULTADO\t"):
                _, arquivo, veredicto, erros = linha.split("\t")
                veredictos[Path(arquivo).stem] = (veredicto, int(erros))
    return veredictos


def validador_local(arquivos, pasta_validador, artefatos, mensagens):
    libs = pasta_validador / "libs"
    veredictos = {}
    for a in arquivos:
        _, _, distribuicao = PACOTES[sigla(a)]
        mensagem = mensagens / f"{a.stem}.validador-local.txt"
        print(f"  Validador Local · {a.stem}", flush=True)
        subprocess.run(
            ["java", "-Xmx4g", "-cp", f"{libs}/*", str(RAIZ / "scripts" / "ValidadorLocalSemJanela.java"),
             str(artefatos / distribuicao), str(a), str(mensagem)],
            check=True, capture_output=True, text=True)
        texto = mensagem.read_text(encoding="utf-8")
        falhas = re.search(r"itens falha: (\d+)", texto)
        if falhas is None:
            sys.exit(f"resumo do Validador Local não encontrado em {mensagem}")
        erros = len(re.findall(r'"severity": "(?:error|fatal)"', texto))
        veredictos[a.stem] = ("APROVADO" if falhas.group(1) == "0" else "REPROVADO", erros)
    return veredictos


# ── Relatório ───────────────────────────────────────────────────────────────────


def sigla(arquivo):
    prefixo = arquivo.stem.split("--", 1)[0]
    if prefixo not in PACOTES:
        sys.exit(f"{arquivo.name}: o nome precisa começar por uma sigla de {sorted(PACOTES)} e '--'")
    return prefixo


def erros(n):
    return "1 erro" if n == 1 else f"{n} erros"


def sha256(arquivo):
    return hashlib.sha256(arquivo.read_bytes()).hexdigest()[:16]


def git(*argumentos):
    return subprocess.run(["git"] + list(argumentos), cwd=RAIZ, check=True,
                          capture_output=True, text=True).stdout.strip()


def escrever_resultados(grupos, c4, vl, pasta_validador):
    versao_c4 = re.search(r"<hapi.version>([^<]+)<", (RAIZ / "pom.xml").read_text()).group(1)
    jar_vl = next((pasta_validador / "libs").glob("hapi-fhir-validation-[0-9]*.jar")).name
    versao_vl = re.search(r"hapi-fhir-validation-(.+)\.jar", jar_vl).group(1)
    aplicativo = sha256(pasta_validador / "libs" / "tarea-fhir-validator.jar")
    alteracoes = git("status", "--porcelain", "--", ".", ":(exclude)evidencias")
    estado = "árvore limpa" if not alteracoes else "**com alterações locais não commitadas**"

    linhas = [
        "# Camada 4 × Validador Local da RNDS",
        "",
        f"Gerado por `scripts/evidencias-validador-local.py` em "
        f"{datetime.datetime.now().astimezone():%d/%m/%Y %H:%M %z}, sobre o commit "
        f"`{git('rev-parse', '--short', 'HEAD')}` ({estado}). Leia o [README](README.md) antes.",
        "",
        f"- **Camada 4:** HAPI FHIR {versao_c4}, com o pacote do serviço que o framework carrega.",
        f"- **Validador Local da RNDS:** HAPI FHIR {versao_vl} "
        f"(`tarea-fhir-validator.jar`, SHA-256 `{aplicativo}…`), com a distribuição inteira do serviço.",
        "",
    ]

    inesperadas, resumo = [], []
    for titulo, arquivos, esperado in grupos:
        linhas += [f"## {titulo}", "",
                   "| Arquivo | Camada 4 | Validador Local | Iguais | Observação | SHA-256 (16) |",
                   "|---|---|---|---|---|---|"]
        iguais = 0
        for a in arquivos:
            nome = a.stem
            (v4, e4), (vv, ev) = c4[nome], vl[nome]
            observacao = ""
            if nome in DIVERGENCIAS_CONHECIDAS:
                p4, pv, observacao = DIVERGENCIAS_CONHECIDAS[nome]
                ok = (v4, vv) == (p4, pv)
                observacao = "Divergência conhecida. " + observacao
            elif nome in CANARIOS:
                origem, _, mudanca = CANARIOS[nome]
                ok = v4 == vv == "REPROVADO"
                observacao = f"Canário: `{origem}` {mudanca}; precisa reprovar nos dois."
            else:
                ok = v4 == vv == esperado
            if not ok:
                inesperadas.append(nome)
                observacao = ("**INESPERADO.** " + observacao).strip()
            iguais += v4 == vv
            linhas.append(
                f"| `{a.relative_to(SAIDA)}` "
                f"| [{v4.lower()}](mensagens/{nome}.camada4.txt) ({erros(e4)}) "
                f"| [{vv.lower()}](mensagens/{nome}.validador-local.txt) ({erros(ev)}) "
                f"| {'sim' if v4 == vv else '**não**'} | {observacao} | `{sha256(a)}` |")
        resumo.append(f"- {titulo}: {iguais} de {len(arquivos)} com o mesmo veredicto nos dois validadores.")
        linhas.append("")

    resumo.append(f"- Divergências inesperadas: {len(inesperadas)}"
                  + (f" ({', '.join(inesperadas)})." if inesperadas else "."))
    indice = linhas.index("## " + grupos[0][0])
    linhas[indice:indice] = ["## Resumo", ""] + resumo + [""]
    linhas += ["## Pacotes", "", "| Sigla | Camada 4 (classpath do core) | Validador Local (rnds-artefatos) |",
               "|---|---|---|"]
    linhas += [f"| `{s}` | `{p}` | `{d}` |" for s, (_, p, d) in PACOTES.items()]
    (SAIDA / "RESULTADOS.md").write_text("\n".join(linhas) + "\n", encoding="utf-8")
    return inesperadas


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--validador-local", type=Path, required=True,
                        help="pasta do aplicativo do DATASUS (a que contém libs/)")
    parser.add_argument("--artefatos", type=Path, required=True,
                        help="clone de github.com/IgMiras/rnds-artefatos")
    parser.add_argument("--offline", action="store_true", help="roda o Maven com -o")
    args = parser.parse_args()

    if not (args.validador_local / "libs" / "tarea-fhir-validator.jar").is_file():
        sys.exit(f"{args.validador_local}: não encontrei libs/tarea-fhir-validator.jar")
    for _, _, distribuicao in PACOTES.values():
        if not (args.artefatos / distribuicao).is_dir():
            sys.exit(f"{args.artefatos}: falta a distribuição {distribuicao}")

    documentos, canarios, mensagens = SAIDA / "documentos", SAIDA / "canarios", SAIDA / "mensagens"
    for pasta in (documentos, canarios, mensagens):
        shutil.rmtree(pasta, ignore_errors=True)

    print("1/4 Exportando os documentos pela suíte", flush=True)
    mvn("test", "-pl", "rnds-br-connect-core", "-Dtest=*BundleBuilderTest,ExportarBundlesTest",
        "-Dsurefire.failIfNoSpecifiedTests=false", offline=args.offline,
        env={**os.environ, VARIAVEL: str(documentos)})

    print("2/4 Gerando os canários", flush=True)
    gerar_canarios(documentos, canarios)

    grupos = [
        ("Documentos produzidos pelo framework", sorted(documentos.glob("*.json")), "APROVADO"),
        ("Canários", sorted(canarios.glob("*.json")), "REPROVADO"),
        ("Controle: exemplo oficial", sorted((SAIDA / "controle").glob("*.json")), "APROVADO"),
    ]
    todos = [a for _, arquivos, _ in grupos for a in arquivos]

    print(f"3/4 Validando {len(todos)} arquivos nos dois validadores", flush=True)
    mensagens.mkdir(parents=True)
    c4 = camada_quatro(todos, classpath_do_core(args.offline), mensagens)
    vl = validador_local(todos, args.validador_local, args.artefatos, mensagens)

    print("4/4 Escrevendo RESULTADOS.md", flush=True)
    inesperadas = escrever_resultados(grupos, c4, vl, args.validador_local)
    if inesperadas:
        sys.exit(f"Divergências inesperadas: {', '.join(inesperadas)}")
    print("Nenhuma divergência inesperada.")


if __name__ == "__main__":
    main()
