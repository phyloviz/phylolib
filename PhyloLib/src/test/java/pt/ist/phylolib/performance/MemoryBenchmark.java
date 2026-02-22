package pt.ist.phylolib.performance;

import org.testng.annotations.Test;
import pt.ist.phylolib.data.matrix.Matrix;
import pt.ist.phylolib.data.matrix.Symmetric;
import pt.ist.phylolib.data.tree.Edge;
import pt.ist.phylolib.data.tree.Newick;
import pt.ist.phylolib.data.tree.Tree;

import java.io.BufferedWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;

/**
 * Memory and performance benchmarks for phylogenetic operations.
 * Tests memory efficiency improvements from streaming writes and optimized
 * StringBuilder allocation.
 */
public class MemoryBenchmark {

    private static final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

    @Test(groups = "performance")
    public void benchmarkMatrixWrite_Small() throws Exception {
        System.out.println("\n=== Small Matrix (100x100) Benchmark ===");
        Matrix matrix = createTestMatrix(100, true);
        benchmarkMatrixWrite(matrix, "Small (100x100)");
    }

    @Test(groups = "performance")
    public void benchmarkMatrixWrite_Medium() throws Exception {
        System.out.println("\n=== Medium Matrix (500x500) Benchmark ===");
        Matrix matrix = createTestMatrix(500, true);
        benchmarkMatrixWrite(matrix, "Medium (500x500)");
    }

    @Test(groups = "performance")
    public void benchmarkMatrixWrite_Large() throws Exception {
        System.out.println("\n=== Large Matrix (1000x1000) Benchmark ===");
        Matrix matrix = createTestMatrix(1000, true);
        benchmarkMatrixWrite(matrix, "Large (1000x1000)");
    }

    @Test(groups = "performance")
    public void benchmarkTreeWrite_Small() throws Exception {
        System.out.println("\n=== Small Tree (50 nodes) Benchmark ===");
        Tree tree = createTestTree(50);
        benchmarkTreeWrite(tree, "Small (50 nodes)");
    }

    @Test(groups = "performance")
    public void benchmarkTreeWrite_Medium() throws Exception {
        System.out.println("\n=== Medium Tree (200 nodes) Benchmark ===");
        Tree tree = createTestTree(200);
        benchmarkTreeWrite(tree, "Medium (200 nodes)");
    }

    @Test(groups = "performance")
    public void benchmarkTreeWrite_Large() throws Exception {
        System.out.println("\n=== Large Tree (500 nodes) Benchmark ===");
        Tree tree = createTestTree(500);
        benchmarkTreeWrite(tree, "Large (500 nodes)");
    }

    private void benchmarkMatrixWrite(Matrix matrix, String label) throws Exception {
        Symmetric parser = new Symmetric();
        int warmups = 3;
        int iterations = 10;

        // Warmup
        for (int i = 0; i < warmups; i++) {
            parser.parse(matrix);
        }

        // Measure traditional string-based approach
        System.gc();
        Thread.sleep(100);
        long startMemory = getUsedMemory();
        long startTime = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            String result = parser.parse(matrix);
            // Simulate actual usage
            @SuppressWarnings("unused")
            int length = result.length();
        }

        long endTime = System.nanoTime();
        long endMemory = getUsedMemory();

        double avgTime = (endTime - startTime) / 1_000_000.0 / iterations;
        long memoryUsed = endMemory - startMemory;

        System.out.printf("%s Matrix - Traditional Approach:\n", label);
        System.out.printf("  Average time: %.2f ms\n", avgTime);
        System.out.printf("  Peak memory: %.2f MB\n", memoryUsed / 1_048_576.0);

        // Measure streaming approach
        System.gc();
        Thread.sleep(100);
        startMemory = getUsedMemory();
        startTime = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            StringWriter stringWriter = new StringWriter();
            try (BufferedWriter writer = new BufferedWriter(stringWriter)) {
                parser.streamParse(matrix, writer);
            }
        }

        endTime = System.nanoTime();
        endMemory = getUsedMemory();

        avgTime = (endTime - startTime) / 1_000_000.0 / iterations;
        memoryUsed = endMemory - startMemory;

        System.out.printf("%s Matrix - Streaming Approach:\n", label);
        System.out.printf("  Average time: %.2f ms\n", avgTime);
        System.out.printf("  Peak memory: %.2f MB\n", memoryUsed / 1_048_576.0);
    }

    private void benchmarkTreeWrite(Tree tree, String label) throws Exception {
        Newick parser = new Newick();
        int warmups = 3;
        int iterations = 10;

        // Warmup
        for (int i = 0; i < warmups; i++) {
            parser.parse(tree);
        }

        // Measure traditional approach
        System.gc();
        Thread.sleep(100);
        long startMemory = getUsedMemory();
        long startTime = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            String result = parser.parse(tree);
            @SuppressWarnings("unused")
            int length = result.length();
        }

        long endTime = System.nanoTime();
        long endMemory = getUsedMemory();

        double avgTime = (endTime - startTime) / 1_000_000.0 / iterations;
        long memoryUsed = endMemory - startMemory;

        System.out.printf("%s Tree:\n", label);
        System.out.printf("  Average time: %.2f ms\n", avgTime);
        System.out.printf("  Peak memory: %.2f MB\n", memoryUsed / 1_048_576.0);
    }

    private Matrix createTestMatrix(int size, boolean symmetric) {
        String[] ids = new String[size];
        for (int i = 0; i < size; i++) {
            ids[i] = "Species_" + i;
        }

        return new Matrix(symmetric, ids, (i, j) -> {
            // Simulate realistic distance calculation
            return Math.abs(Math.sin(i * 0.1) - Math.cos(j * 0.1));
        });
    }

    private Tree createTestTree(int nodes) {
        String[] ids = new String[nodes];
        List<Edge> edges = new ArrayList<>();

        for (int i = 0; i < nodes; i++) {
            ids[i] = "Node_" + i;
        }

        // Create a binary tree structure
        for (int i = 0; i < nodes - 1; i++) {
            int parent = (i - 1) / 2;
            if (i > 0) {
                double distance = Math.random();
                edges.add(new Edge(parent, i, distance));
            }
        }

        return new Tree(ids, edges);
    }

    private long getUsedMemory() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        return heapUsage.getUsed();
    }

    public static void main(String[] args) throws Exception {
        System.out.println("PhyloLib Memory & Performance Benchmark");
        System.out.println("========================================");
        System.out.println("Java Version: " + System.getProperty("java.version"));
        System.out.println("Max Heap: " + (Runtime.getRuntime().maxMemory() / 1_048_576) + " MB");
        System.out.println();

        MemoryBenchmark benchmark = new MemoryBenchmark();

        // Run matrix benchmarks
        benchmark.benchmarkMatrixWrite_Small();
        benchmark.benchmarkMatrixWrite_Medium();
        benchmark.benchmarkMatrixWrite_Large();

        // Run tree benchmarks
        benchmark.benchmarkTreeWrite_Small();
        benchmark.benchmarkTreeWrite_Medium();
        benchmark.benchmarkTreeWrite_Large();

        System.out.println("\n========================================");
        System.out.println("Benchmark Complete!");
    }
}
