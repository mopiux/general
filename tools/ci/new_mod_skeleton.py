#!/usr/bin/env python3
"""Genera los archivos Gradle/metadatos comunes de cada mod (build.gradle, settings.gradle,
gradle.properties, mods.toml, pack.mcmeta). Ejecutar desde la raíz del repo:
    python3 tools/ci/new_mod_skeleton.py
Es idempotente: sobrescribe solo esos archivos de configuración."""
import os, textwrap

MODS = {
    "alquimia": dict(
        name="Alquimia Cartográfica",
        package="com.mopiux.alquimia",
        mixins=False,
        description="Alquimia al estilo Potion Craft: cada ingrediente traza un camino sobre un mapa de esencias. "
                    "Moler, remover, diluir y fijar esencias con la tria prima (sal, mercurio y azufre).",
    ),
    "lenguaperdida": dict(
        name="La Lengua Perdida",
        package="com.mopiux.lenguaperdida",
        mixins=False,
        description="Cada mundo genera una civilización antigua con su propio idioma y glifos. "
                    "Explorá ruinas, reuní piedras Rosetta y descifrá inscripciones que te llevan a bóvedas selladas.",
    ),
    "planetoides": dict(
        name="Planetoides",
        package="com.mopiux.planetoides",
        mixins=True,
        description="Una dimensión de pequeños planetas cúbicos con gravedad propia: caminá por todas sus caras, "
                    "flotá en gravedad cero y explorá el Vacío Estelar.",
    ),
}

FORGE_VERSION = "47.3.0"

def write(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)

for mod_id, m in MODS.items():
    root = os.path.join("mods", mod_id)
    mixin_plugin = "\n    id 'org.spongepowered.mixin' version '0.7.+'" if m["mixins"] else ""
    mixin_block = textwrap.dedent(f"""
        mixin {{
            add sourceSets.main, "${{mod_id}}.refmap.json"
            config "${{mod_id}}.mixins.json"
        }}
        """) if m["mixins"] else ""
    mixin_dep = "\n    annotationProcessor 'org.spongepowered:mixin:0.8.5:processor'" if m["mixins"] else ""
    mixin_run_props = textwrap.indent(textwrap.dedent("""\
        property 'mixin.env.remapRefMap', 'true'
        property 'mixin.env.refMapRemappingFile', "${projectDir}/build/createSrgToMcp/output.srg"
        """), " " * 12) if m["mixins"] else ""
    mixin_manifest = "\n                'MixinConfigs'            : \"${mod_id}.mixins.json\"," if m["mixins"] else ""

    write(os.path.join(root, "build.gradle"), f"""plugins {{
    id 'eclipse'
    id 'idea'
    id 'net.minecraftforge.gradle' version '[6.0.24,6.2)'{mixin_plugin}
}}

version = mod_version
group = mod_group_id

base {{
    archivesName = "${{mod_id}}-${{minecraft_version}}"
}}

java.toolchain.languageVersion = JavaLanguageVersion.of(17)

println "Java: ${{System.getProperty 'java.version'}}, JVM: ${{System.getProperty 'java.vm.version'}} (${{System.getProperty 'java.vendor'}}), Arch: ${{System.getProperty 'os.arch'}}"

minecraft {{
    mappings channel: mapping_channel, version: mapping_version
    copyIdeResources = true

    runs {{
        configureEach {{
            workingDirectory project.file('run')
            property 'forge.logging.markers', 'REGISTRIES'
            property 'forge.logging.console.level', 'info'
{mixin_run_props}
            mods {{
                "${{mod_id}}" {{
                    source sourceSets.main
                }}
            }}
        }}

        client {{
            property 'forge.enabledGameTestNamespaces', mod_id
        }}

        server {{
            property 'forge.enabledGameTestNamespaces', mod_id
            args '--nogui'
        }}

        gameTestServer {{
            property 'forge.enabledGameTestNamespaces', mod_id
        }}
    }}
}}
{mixin_block}
repositories {{
}}

dependencies {{
    minecraft "net.minecraftforge:forge:${{minecraft_version}}-${{forge_version}}"{mixin_dep}
}}

tasks.named('processResources', ProcessResources).configure {{
    var replaceProperties = [
            minecraft_version      : minecraft_version,
            minecraft_version_range: minecraft_version_range,
            forge_version          : forge_version,
            forge_version_range    : forge_version_range,
            loader_version_range   : loader_version_range,
            mod_id                 : mod_id,
            mod_name               : mod_name,
            mod_license            : mod_license,
            mod_version            : mod_version,
            mod_authors            : mod_authors,
            mod_description        : mod_description,
    ]
    inputs.properties replaceProperties
    filesMatching(['META-INF/mods.toml', 'pack.mcmeta']) {{
        expand replaceProperties + [project: project]
    }}
}}

tasks.named('jar', Jar).configure {{
    manifest {{
        attributes([
                'Specification-Title'     : mod_id,
                'Specification-Vendor'    : mod_authors,
                'Specification-Version'   : '1',
                'Implementation-Title'    : project.name,
                'Implementation-Version'  : project.jar.archiveVersion,
                'Implementation-Vendor'   : mod_authors,{mixin_manifest}
                'Implementation-Timestamp': new Date().format("yyyy-MM-dd'T'HH:mm:ssZ")
        ])
    }}
    finalizedBy 'reobfJar'
}}

tasks.withType(JavaCompile).configureEach {{
    options.encoding = 'UTF-8'
    options.compilerArgs += ['-Xmaxerrs', '2000', '-Xlint:-removal']
}}
""")

    write(os.path.join(root, "settings.gradle"), f"""pluginManagement {{
    repositories {{
        gradlePluginPortal()
        maven {{
            name = 'MinecraftForge'
            url = 'https://maven.minecraftforge.net/'
        }}
        maven {{
            name = 'Sponge'
            url = 'https://repo.spongepowered.org/repository/maven-public/'
        }}
    }}
}}

plugins {{
    id 'org.gradle.toolchains.foojay-resolver-convention' version '0.8.0'
}}

rootProject.name = '{mod_id}'
""")

    write(os.path.join(root, "gradle.properties"), f"""org.gradle.jvmargs=-Xmx3G
org.gradle.daemon=false

minecraft_version=1.20.1
minecraft_version_range=[1.20.1,1.20.2)
forge_version={FORGE_VERSION}
forge_version_range=[47,)
loader_version_range=[47,)
mapping_channel=official
mapping_version=1.20.1

mod_id={mod_id}
mod_name={m["name"]}
mod_license=MIT
mod_version=1.0.0
mod_group_id={m["package"]}
mod_authors=mopiux, Claude (Anthropic)
mod_description={m["description"]}
""")

    write(os.path.join(root, "src/main/resources/META-INF/mods.toml"), """modLoader="javafml"
loaderVersion="${loader_version_range}"
license="${mod_license}"
issueTrackerURL="https://github.com/mopiux/general/issues"

[[mods]]
modId="${mod_id}"
version="${mod_version}"
displayName="${mod_name}"
logoFile="logo.png"
authors="${mod_authors}"
description='''${mod_description}'''

[[dependencies.${mod_id}]]
    modId="forge"
    mandatory=true
    versionRange="${forge_version_range}"
    ordering="NONE"
    side="BOTH"

[[dependencies.${mod_id}]]
    modId="minecraft"
    mandatory=true
    versionRange="${minecraft_version_range}"
    ordering="NONE"
    side="BOTH"
""")

    write(os.path.join(root, "src/main/resources/pack.mcmeta"), """{
  "pack": {
    "description": {
      "text": "${mod_name} resources"
    },
    "pack_format": 15
  }
}
""")
print("ok")
