#!/usr/bin/env python3
"""
Gera o catalogo dos codigos de exame que a RNDS aceita, a partir dos artefatos
de conformidade oficiais.

    uso: python3 scripts/gerar-catalogo-exames.py <diretorio-com-os-XMLs-da-RNDS>

O catalogo NAO e uma tabela de equivalencia clinica (ADR-05). Ele responde
"este codigo e aceito pela RNDS?", nao "qual codigo corresponde ao meu exame".

Fonte: ValueSet BRNomeExame-2.0, que compoe BRNomeExameLOINC e BRNomeExameGAL
e declara um compose.exclude com 7 codigos LOINC.

A conta exige DUAS correcoes sobre a soma ingenua dos CodeSystems:

    451 conceitos LOINC
  -   2 codigos DUPLICADOS no proprio CodeSystem (80205-8 e 80206-6,
      cada um com dois displays que traduzem o mesmo termo LOINC)
  =  449 codigos LOINC distintos
  -   7 removidos pelo compose.exclude do ValueSet
  = 442 LOINC aceitos
  +  33 GAL
  = 475

Somar os conceitos dos dois CodeSystems da 484; parar apos o exclude da 477.
Nenhum dos dois e o numero de codigos que a RNDS aceita.
"""
import sys, os, glob, hashlib, datetime
import xml.etree.ElementTree as ET

NS = {'f': 'http://hl7.org/fhir'}
CS = "http://www.saude.gov.br/fhir/r4/CodeSystem/"


def achar(origem, nome):
    for f in glob.glob(os.path.join(origem, "**", nome), recursive=True):
        return f
    raise SystemExit(f"ERRO: artefato nao encontrado na origem: {nome}")


def raiz(f):
    return ET.parse(f).getroot()


def conceitos(f):
    """Conceitos do CodeSystem, na ordem de declaracao."""
    out = []
    for c in raiz(f).iter('{http://hl7.org/fhir}concept'):
        k = c.find('f:code', NS)
        d = c.find('f:display', NS)
        if k is not None:
            out.append((k.get('value'), (d.get('value') if d is not None else '').strip()))
    return out


def deduplicar(conceitos_, nome):
    """Colapsa codigos repetidos, preservando a ordem e a PRIMEIRA ocorrencia.

    O BRNomeExameLOINC declara 80205-8 e 80206-6 duas vezes cada, com displays
    que traduzem o mesmo termo LOINC de formas diferentes. O CodeSystem nao
    declara `count`, entao nada no artefato denuncia a discrepancia: quem soma
    conceitos obtem 451 e quem conta codigos obtem 449.
    """
    vistos = {}
    duplicados = []
    for code, display in conceitos_:
        if code in vistos:
            duplicados.append(code)
            continue
        vistos[code] = display
    if duplicados:
        print(f"   AVISO: {nome} declara {len(duplicados)} codigo(s) duplicado(s): "
              f"{', '.join(sorted(set(duplicados)))}")
        print("          mantida a primeira ocorrencia de cada.")
    return list(vistos.items())


def versao(f):
    v = raiz(f).find('f:version', NS)
    return v.get('value') if v is not None else '?'


def main(origem):
    f_loinc = achar(origem, "CodeSystem-BRNomeExameLOINC.xml")
    f_gal = achar(origem, "CodeSystem-BRNomeExameGAL.xml")
    f_vs = achar(origem, "ValueSet-BRNomeExame-2_0.xml")

    excluidos = set()
    for e in raiz(f_vs).iter('{http://hl7.org/fhir}exclude'):
        s = e.find('f:system', NS)
        for c in e.findall('f:concept', NS):
            excluidos.add((s.get('value'), c.find('f:code', NS).get('value')))

    linhas = []
    total = {}
    bruto = {}
    for f, nome in ((f_loinc, "BRNomeExameLOINC"), (f_gal, "BRNomeExameGAL")):
        todos = conceitos(f)
        bruto[nome] = len(todos)
        distintos = deduplicar(todos, nome)
        aceitos = [(c, d) for c, d in distintos if (CS + nome, c) not in excluidos]
        total[nome] = len(aceitos)
        linhas.append(f"  {CS}{nome}:")
        for c, d in aceitos:
            linhas.append(f'    "{c}": "{d.replace(chr(34), chr(39))}"')

    sha = hashlib.sha256()
    for f in (f_loinc, f_gal, f_vs):
        sha.update(open(f, 'rb').read())

    cab = [
        "# Catalogo dos codigos de exame aceitos pela RNDS.",
        "#",
        "# GERADO por scripts/gerar-catalogo-exames.py. NAO EDITAR A MAO.",
        f"# Gerado em: {datetime.datetime.now().isoformat(timespec='seconds')}",
        f"# SHA-256 das fontes: {sha.hexdigest()[:16]}",
        "#",
        "# NAO e tabela de equivalencia clinica (ADR-05): responde 'este codigo e",
        "# aceito?', nao 'qual codigo corresponde ao meu exame'. A segunda pergunta",
        "# e decisao clinica e permanece com a equipe de saude.",
        "#",
        f"# Fonte: ValueSet BRNomeExame v{versao(f_vs)}",
        f"#        CodeSystem BRNomeExameLOINC v{versao(f_loinc)}: "
        f"{bruto['BRNomeExameLOINC']} conceitos -> {total['BRNomeExameLOINC']} codigos aceitos",
        f"#        CodeSystem BRNomeExameGAL   v{versao(f_gal)}: "
        f"{bruto['BRNomeExameGAL']} conceitos -> {total['BRNomeExameGAL']} codigos aceitos",
        f"#        (descontados {len(excluidos)} codigos do compose.exclude do ValueSet",
        f"#         e os codigos declarados em duplicidade nos CodeSystems)",
        f"# TOTAL: {sum(total.values())} codigos aceitos",
        "",
        f"totalDeclarado: {sum(total.values())}",
        "codigos:",
    ]

    destino = "rnds-br-connect-exames-starter/src/main/resources/catalogo/exames-rnds.yml"
    os.makedirs(os.path.dirname(destino), exist_ok=True)
    with open(destino, "w", encoding="utf-8") as fh:
        fh.write("\n".join(cab + linhas) + "\n")

    print(f"\n{sum(total.values())} codigos gravados em {destino}")
    for k, v in total.items():
        print(f"   {k}: {bruto[k]} conceitos -> {v} codigos aceitos")
    print(f"   excluidos pelo compose.exclude: {len(excluidos)}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    main(sys.argv[1])
