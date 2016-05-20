package test.implemention_related;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

/**
 * Created by wumo on 2016/5/18.
 * email: wumo@outlook.com
 */
@State(Scope.Benchmark)
//@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
//@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class BaseTest {
    @Param(value = {"1"})
    int threadCount;
    int i = 0;

    @Setup(Level.Invocation)
    public void setup() {
        i=0;
        System.out.print("\tsetup:" + (i++) + "->" + threadCount + ": " + Thread.currentThread().getId());
    }

    @Benchmark
    @Warmup(iterations = 1, batchSize = 1)
    @Measurement(iterations = 2, batchSize = 2)
    @BenchmarkMode(Mode.SingleShotTime)
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void test() throws InterruptedException {
        System.out.print("\ttest:" + (i++) + "->" + threadCount + ": " + Thread.currentThread().getId());
    }

    @TearDown(Level.Invocation)
    public void tearDown() {
        System.out.print("\tteardown:" + (i++) + "->" + threadCount + ": " + Thread.currentThread().getId());
        i=0;
    }

    public static void main(String[] args) throws RunnerException, InterruptedException {

//        AsyncTest1 instance = new AsyncTest1();
//        instance.setup();
//        instance.test();
//        instance.tearDown();
        Options opt = new OptionsBuilder()
                .include(BaseTest.class.getSimpleName())
                .threads(1)
                .forks(1)
                .timeUnit(TimeUnit.MILLISECONDS)
                .shouldDoGC(true)
                .syncIterations(true)
                .shouldFailOnError(true) // switch to "true" to fail the complete run
                .build();

        new Runner(opt).run();
    }
}
