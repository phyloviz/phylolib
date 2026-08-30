# PhyloLib

PhyloLib is an open-source command-line library of efficient algorithms for distance-based phylogenetic analysis.

The project was originally developed in the scope of a master's thesis at Instituto Superior Técnico, divided into two
phases. The first phase consisted of an initial project report and presentation. The second phase resulted in the
master's thesis, an article, supplementary material, documentation, usage examples, and the first Docker-based
distribution of the library.

Since then, PhyloLib has continued to evolve under the PHYLOViZ organization. It provides a composable phylogenetic
workflow in which distance computation, distance correction, tree inference, and local optimization can be executed
independently or combined into a complete analysis pipeline.

The library supports data formats commonly used in microbial typing studies, handles missing and ambiguous characters,
and exposes multiple distance models and phylogenetic inference algorithms through a uniform command-line interface.

PhyloLib is implemented in Java 21 and includes unit and integration tests, continuous integration, example datasets,
Docker support, and a Nextflow pipeline for reproducible and containerized execution.

A pre-built [Docker image](https://hub.docker.com/r/phyloviz/phylolib) is available on Docker Hub.

The unit tests and benchmarks developed for this library are available in
the [test folder](https://github.com/phyloviz/phylolib/tree/master/PhyloLib/src/test/java/pt/ist/phylolib) of the code.

The Javadoc documentation of the library can be found [here](https://phyloviz.github.io/phylolib/).

## Contents

- [Usage](#usage)
- [Installation](#installation)
- [Docker](#docker)
- [Reproducible Nextflow Pipeline](#reproducible-nextflow-pipeline)
- [License](#license)

## Usage

To execute a command of this command line application you should type the name of the library followed by the command
name, respective type and options. The usage of this command line application can be retrieved by running the command
`phylolib help` and looks like the following:

```
Usage:
    phylolib help
    phylolib distance (hamming|grapetree|kimura) [options]
    phylolib correction (jukescantor) [options]
    phylolib algorithm (goeburst|goeburstfullmst|edmonds|sl|cl|upgma|upgmc|wpgma|wpgmc|saitounei|studierkepler|unj) [options]
    phylolib optimization (lbr) [options]

Options:
    -o=<file>      --out=<file>       Output file as <format>:<location> with format being (asymmetric|symmetric|newick|nexus)
    -d=<file>      --dataset=<file>   Input dataset file as <format>:<location> with format being (fasta|ml|snp)
    -m=<file>      --matrix=<file>    Input distance matrix file as <format>:<location> with format being (asymmetric|symmetric)
    -t=<file>      --tree=<file>      Input phylogenetic tree file as <format>:<location> with format being (newick|nexus)
    -l=<number>    --lvs=<number>     Limit of locus variants to consider using goeBURST algorithm [default: 3]
    -f             --force-dense      Explicitly force heap-backed dense storage, overriding the automatic memory budget.
```

`algorithm goeburst` remains the threshold-limited goeBURST implementation and uses `--lvs` (default: 3). When
estimated complete dense storage exceeds the automatic 320 MiB raw-distance budget, it may use threshold-filtered
storage retaining only distances through that bound; `--force-dense` retains all distances instead without changing
goeBURST's threshold semantics.
`algorithm goeburstfullmst` builds one complete goeBURST Full MST from a symmetric, complete matrix of positive
integral LV distances. Matrix-only input has equal-frequency tie semantics because profile frequencies are not stored
in the matrix format. When estimated complete dense storage exceeds the automatic 320 MiB raw-distance budget, it
requires `--force-dense`, because the only current complete-pairwise storage is heap-backed dense storage.
`--force-dense` changes storage only; it never changes an algorithm's distance scope.

You can also run multiple commands by concatenating them with a ":" character like this:

```
phylolib algorithm upgma --out=newick:tree.txt : distance hamming --dataset=ml:dataset.txt
```

The order in which the commands are executed is dictated by the phylogenetic analysis workflow, making the order in
which the commands are provided indifferent. Except for commands of the same type, that is, that can be executed
multiple times, as is the case of the optimization command, in which case the order of execution between them will be
dictated by the order in which they are provided.
For example, in the execution above, the order in which the commands would be executed would be distance and then
algorithm and not algorithm and then distance.

## Installation

To build a local command-line distribution with the `phylolib` executable, you should:

1. Install Java JDK21 or higher.
2. Open the terminal in the `PhyloLib` folder.
3. Run the command `./gradlew installDist` to build the distribution.
4. Run the command `build/install/phylolib/bin/phylolib help` to execute PhyloLib.

You can also run the JAR directly:

```
java -jar build/libs/PhyloLib-1.0.0.jar help
```

## Docker

To build a Docker image for this project and execute it, you should:

1. Install Docker and run `./gradlew jar` to compile the JAR.
2. Open the terminal in the `PhyloLib` folder.
3. Run the command `docker build -t phylolib:1.0.0 .` to build the Docker image.
4. Run the command
   `docker run --rm -v $HOME/<DIRECTORY>/files:/files -v $HOME/<DIRECTORY>/logs:/logs phylolib:1.0.0 phylolib help` to
   execute the Docker image.

Release images are published for both `linux/amd64` and `linux/arm64`. To build and publish a multi-platform image
manually, use Docker Buildx from the `PhyloLib` folder:

```
docker buildx build --platform linux/amd64,linux/arm64 -t phyloviz/phylolib:1.0.0 --push .
```

## Reproducible Nextflow Pipeline

The repository includes a Nextflow pipeline for reproducible, containerized execution of the distance-based workflow.
The distance matrix is computed once and reused by every requested inference algorithm.

### Install requirements

Before running the pipeline, install the following tools.

1. Install Java 17 or higher. Nextflow requires Java 17 or higher.

```
java -version
```

2. Install Nextflow.

```
curl -s https://get.nextflow.io | bash
chmod +x nextflow
mkdir -p $HOME/.local/bin
mv nextflow $HOME/.local/bin/
```

Make sure `$HOME/.local/bin` is available in your `PATH`. Then confirm that Nextflow works:

```
nextflow info
```

3. Install Docker and confirm that it works.

```
docker --version
docker run hello-world
```

4. Confirm that the PhyloLib Docker image is available.

```
docker pull phyloviz/phylolib:1.0.0
docker run --rm phyloviz/phylolib:1.0.0 phylolib help
```

The Docker profile uses `phyloviz/phylolib:1.0.0` by default. If you are testing local changes before a release, build
the image as described in the Docker section and pass it with `--container`.

### Run the pipeline

The pipeline is defined in the repository root:

```
main.nf
nextflow.config
```

Run the pipeline from the repository root:

```
nextflow run main.nf -profile docker \
  --dataset profiles.tsv --dataset_format ml \
  --distance hamming \
  --algorithm goeburst,upgma,saitounei \
  --outdir results
```

### Outputs

The command creates:

```
results/
  execution_report.html
  matrix/
    matrix.txt
  trees/
    goeburst.newick
    upgma.newick
    saitounei.newick
```

The Docker profile uses `phyloviz/phylolib:1.0.0` by default. When testing a locally built image, override it with:

```
nextflow run main.nf -profile docker \
  --container <YOUR_LOCAL_IMAGE> \
  --dataset profiles.tsv --dataset_format ml \
  --distance hamming \
  --algorithm goeburst,upgma,saitounei \
  --outdir results
```

The most relevant parameters are:

```
--dataset          Input dataset path.
--dataset_format   Input dataset format: fasta, ml, or snp.
--distance         Distance model: hamming, grapetree, or kimura.
--algorithm        Comma-separated inference algorithms.
--matrix_format    Distance matrix format: symmetric or asymmetric.
--tree_format      Tree output format: newick or nexus.
--lvs              goeBURST locus variant limit. Default: 3.
--force_dense      Force dense matrix mode. Default: false.
--outdir           Output directory. Default: results.
--container        Docker image used by the docker profile.
```

## License

PhyloLib is licensed under the [MIT License](LICENSE).
