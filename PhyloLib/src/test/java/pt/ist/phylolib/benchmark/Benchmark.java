package pt.ist.phylolib.benchmark;

import pt.ist.phylolib.cli.Data;
import pt.ist.phylolib.cli.Options;
import pt.ist.phylolib.command.algorithm.Algorithm;
import pt.ist.phylolib.command.distance.Hamming;
import pt.ist.phylolib.data.Context;
import pt.ist.phylolib.data.IReader;
import pt.ist.phylolib.data.dataset.Dataset;
import pt.ist.phylolib.data.matrix.Matrix;
import pt.ist.phylolib.reflection.Types;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class Benchmark {

	private static final List<MemoryPoolMXBean> MEMORY = ManagementFactory.getMemoryPoolMXBeans() ;
	private static final int WARMUPS = 10;
	private static final int ITERATIONS = 20;
	private static final File[] DATASETS = resources("datasets");
	private static final File[] MATRICES = resources("matrices");

	public static void main(String[] args) throws Exception {
		for (Map.Entry<String, Constructor<?>> command : Types.get(Algorithm.class).entrySet()) {
			System.out.println(command.getKey() + ':');
			System.out.println("\teager:");
			for (File file : MATRICES) {
				Matrix matrix = (Matrix) read(file, "symmetric", Data.MATRIX);
				run(file.getName(), () -> matrix, command.getValue());
			}
			System.out.println("\tlazy:");
			for (File file : DATASETS) {
				Dataset dataset = (Dataset) read(file, "ml", Data.DATASET);
				run(file.getName(), () -> new Hamming().process(dataset), command.getValue());
			}
		}
	}

	private static File[] resources(String folder) {
		return Arrays.stream(new File(Benchmark.class.getClassLoader().getResource(folder).getPath()).listFiles())
				.sorted((one, two) -> one.length() == two.length() ? one.getName().compareTo(two.getName()) : (int) (one.length() - two.length()))
				.toArray(File[]::new);
	}

	private static Object read(File file, String type, Data data) throws Exception {
		try (Stream<String> lines = Files.lines(Paths.get(file.toURI()))) {
			return ((IReader<?>) data.type(type).newInstance()).parse(lines, new Options());
		}
	}

	private static void run(String file, Supplier<Matrix> input, Constructor<?> constructor) throws Exception {
		for (int i = 1; i <= WARMUPS; i++) {
			Algorithm algorithm = (Algorithm) constructor.newInstance();
			algorithm.init(new Context(), new Options());
			algorithm.process(input.get());
		}
		double totalTime = 0;
		double totalMemory = 0;
		for (int i = 1; i <= ITERATIONS; i++) {
			Algorithm algorithm = (Algorithm) constructor.newInstance();
			algorithm.init(new Context(), new Options());
			Matrix matrix = input.get();
			System.gc();
			MEMORY.forEach(MemoryPoolMXBean::resetPeakUsage);
			double start = System.nanoTime();
			algorithm.process(matrix);
			totalTime += (System.nanoTime() - start) / 1000000;
			totalMemory += MEMORY.stream().mapToDouble(pool -> pool.getPeakUsage().getUsed() / 1000000.0).sum();
		}
		System.out.printf("\t\t%s: %f ms, %f MB\n", file.substring(0, file.lastIndexOf('.')), totalTime / ITERATIONS, totalMemory / ITERATIONS);
	}

	private interface Supplier<T> {

		T get() throws Exception;

	}

}
