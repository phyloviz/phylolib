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

A pre-built [Docker image](https://hub.docker.com/r/gonfrutuoso/phylolib) is available on Docker Hub.

The unit tests and benchmarks developed for this library are available in
the [test folder](https://github.com/phyloviz/phylolib/tree/master/PhyloLib/src/test/java/pt/ist/phylolib) of the code.

## Project History

The original academic work and associated material are available through the following references:

- [Initial project report](https://www.overleaf.com/read/dxpfjfwtfdcs)
- [Initial project presentation](https://docs.google.com/presentation/d/1x_T11wbP_nEoqif2Tt05Od9tPjfYgre55OPe69C3I7k/edit?usp=sharing)
- [Master's thesis](http://arxiv.org/abs/2012.12697)
- [Original article](https://www.overleaf.com/read/kmjyztpsknbp)
- [Supplementary material](https://www.overleaf.com/read/tqpsxpcynrwh)
- [Dissertation presentation](https://docs.google.com/presentation/d/1qPudTnvzP8hGGGDKaR8n9iOIMUOoeEIu2nGxD7D9tUE/edit?usp=sharing)
- [Original documentation](https://luanab.github.io/phylolib/index.html)
- [Usage video](https://youtu.be/v_pCZMlCyRY)
- [Docker deployment video](https://youtu.be/hr0iBjTeV1U)
- [Original repository](https://github.com/luanab/phylolib)

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

## JAR

To compile this project into a JAR and execute it, you should:

1. Install Gradle and Java JDK21 or higher.
2. Open the terminal in the project's folder.
3. Run the command ```gradle clean``` to clean the project.
4. Run the command ```gradle jar``` to build the JAR.
5. Open the terminal in the folder *build/libs* of the project.
6. Run the command ```java -jar PhyloLib-1.0-SNAPSHOT.jar help``` to execute the JAR.

## Docker

To build a Docker image for this project and execute it, you should:

1. Install Docker and compile the JAR of this project.
2. Open the terminal in the project's folder.
3. Run the command ```docker build -t phylolib .``` to build the Docker image.
4. Run the command
   ```docker run --rm -v $HOME/<DIRECTORY>/files:/files -v $HOME/<DIRECTORY>/logs:/logs phylolib:latest help``` to
   execute the Docker image.

## License

PhyloLib is licensed under the [MIT License](LICENSE).
