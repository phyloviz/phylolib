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

## Usage

To execute a command of this command line application you should type the name of the library followed by the command
name, respective type and options. The usage of this command line application can be retrieved by running the command
```phylolib help``` and looks like the following:

```
Usage:
    phylolib help
    phylolib distance (hamming|grapetree|kimura) [options]
    phylolib correction (jukescantor) [options]
    phylolib algorithm (goeburst|edmonds|sl|cl|upgma|upgmc|wpgma|wpgmc|saitounei|studierkepler|unj) [options]
    phylolib optimization (lbr) [options]

Options:
    -o=<file>      --out=<file>       Output file as <format>:<location> with format being (asymmetric|symmetric|newick|nexus)
    -d=<file>      --dataset=<file>   Input dataset file as <format>:<location> with format being (fasta|ml|snp)
    -m=<file>      --matrix=<file>    Input distance matrix file as <format>:<location> with format being (asymmetric|symmetric)
    -t=<file>      --tree=<file>      Input phylogenetic tree file as <format>:<location> with format being (newick|nexus)
    -l=<number>    --lvs=<number>     Limit of locus variants to consider using goeBURST algorithm [default: 3]
    -f             --force-dense      Flag that allows forcing a dense matrix approach in algorithms that were using a sparse matrix automatically.
```

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
3. Run the command ```./gradlew installDist``` to build the distribution.
4. Run the command ```build/install/phylolib/bin/phylolib help``` to execute PhyloLib.

You can also run the JAR directly:

```
java -jar build/libs/PhyloLib-1.0.0.jar help
```

## Docker

To build a Docker image for this project and execute it, you should:

1. Install Docker and run ```./gradlew jar``` to compile the JAR.
2. Open the terminal in the `PhyloLib` folder.
3. Run the command ```docker build -t phylolib:1.0.0 .``` to build the Docker image.
4. Run the command
   ```docker run --rm -v $HOME/<DIRECTORY>/files:/files -v $HOME/<DIRECTORY>/logs:/logs phylolib:1.0.0 phylolib help``` to
   execute the Docker image.

Release images are published for both `linux/amd64` and `linux/arm64`. To build and publish a multi-platform image
manually, use Docker Buildx from the `PhyloLib` folder:

```
docker buildx build --platform linux/amd64,linux/arm64 -t phyloviz/phylolib:1.0.0 --push .
```

## License

PhyloLib is licensed under the [MIT License](LICENSE).
