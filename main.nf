#!/usr/bin/env nextflow

nextflow.enable.dsl = 2

def commandToken(name: String, value) {
    def text = value == null ? '' : value.toString().trim()
    if (!text) {
        throw new IllegalArgumentException("Missing required parameter --${name}")
    }
    if (!(text ==~ /[A-Za-z0-9_-]+/)) {
        throw new IllegalArgumentException("Invalid value for --${name}: '${text}'")
    }
    return text.toLowerCase()
}

def requireOneOf(name: String, value: String, allowed: List) {
    if (!allowed.contains(value)) {
        throw new IllegalArgumentException("Invalid value for --${name}: '${value}'. Expected one of: ${allowed.join(', ')}")
    }
    return value
}

def flagEnabled(value) {
    if (value instanceof Boolean) {
        return value
    }
    if (value == null) {
        return false
    }
    return ['true', '1', 'yes', 'y'].contains(value.toString().trim().toLowerCase())
}

process COMPUTE_DISTANCE {
    tag "${distanceModel}"

    publishDir "${params.outdir}/matrix", mode: 'copy', overwrite: true

    input:
    tuple path(dataset), val(datasetFormat), val(distanceModel), val(matrixFormat), val(forceDenseFlag)

    output:
    path 'matrix.txt', emit: matrix

    script:
    def forceDenseOption = forceDenseFlag ? ' -f' : ''
    """
    phylolib distance ${distanceModel} -d="${datasetFormat}:${dataset}" -o="${matrixFormat}:matrix.txt"${forceDenseOption}
    """

    stub:
    """
    printf 'id\\tid\\n' > matrix.txt
    """
}

process INFER_TREE {
    tag "${algorithm}"

    publishDir "${params.outdir}/trees", mode: 'copy', overwrite: true

    input:
    tuple path(matrix), val(algorithm), val(matrixFormat), val(treeFormat), val(treeOutput), val(lvsValue), val(forceDenseFlag)

    output:
    path "${treeOutput}"

    script:
    def forceDenseOption = forceDenseFlag ? ' -f' : ''
    def lvsOption = algorithm == 'goeburst' ? " -l=${lvsValue}" : ''
    """
    phylolib algorithm ${algorithm}${lvsOption} -m="${matrixFormat}:${matrix}" -o="${treeFormat}:${treeOutput}"${forceDenseOption}
    """

    stub:
    """
    printf '(%s);\\n' '${algorithm}' > ${treeOutput}
    """
}

workflow {
    if (params.dataset == null || params.dataset.toString().trim() == '') {
        throw new IllegalArgumentException('Missing required parameter --dataset')
    }

    datasetFormat = commandToken('dataset_format', params.dataset_format)
    distanceModel = commandToken('distance', params.distance)
    matrixFormat = commandToken('matrix_format', params.matrix_format)
    treeFormat = commandToken('tree_format', params.tree_format)
    treeExtension = treeFormat == 'nexus' ? 'nexus' : 'newick'
    lvsValue = params.lvs == null ? '' : params.lvs.toString().trim()
    forceDenseFlag = flagEnabled(params.force_dense)
    algorithms = params.algorithm
        .toString()
        .split(',')
        .collect { value -> commandToken('algorithm', value) }

    if (algorithms.isEmpty()) {
        throw new IllegalArgumentException('At least one algorithm must be provided with --algorithm')
    }

    requireOneOf('dataset_format', datasetFormat, ['fasta', 'ml', 'snp'])
    requireOneOf('distance', distanceModel, ['hamming', 'grapetree', 'kimura'])
    requireOneOf('matrix_format', matrixFormat, ['asymmetric', 'symmetric'])
    requireOneOf('tree_format', treeFormat, ['newick', 'nexus'])
    if (!(lvsValue ==~ /[0-9]+/)) {
        throw new IllegalArgumentException("Invalid value for --lvs: '${lvsValue}'. Expected a non-negative integer")
    }
    algorithms.each { alg ->
        requireOneOf(
            'algorithm',
            alg,
            ['goeburst', 'edmonds', 'sl', 'cl', 'upgma', 'upgmc', 'wpgma', 'wpgmc', 'saitounei', 'studierkepler', 'unj'],
        )
    }

    dataset_ch = channel.fromPath(params.dataset, checkIfExists: true)
        .map { dataset -> tuple(dataset, datasetFormat, distanceModel, matrixFormat, forceDenseFlag) }
    algorithm_ch = channel.fromList(algorithms)

    COMPUTE_DISTANCE(dataset_ch)
    tree_input_ch = COMPUTE_DISTANCE.out.matrix
        .combine(algorithm_ch)
        .map { matrix, algorithm -> tuple(matrix, algorithm, matrixFormat, treeFormat, "${algorithm}.${treeExtension}", lvsValue, forceDenseFlag) }

    INFER_TREE(tree_input_ch)
}
