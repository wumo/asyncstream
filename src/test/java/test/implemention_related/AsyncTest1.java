/*
 * Copyright (c) 2014, Oracle America, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 *  * Neither the name of Oracle nor the names of its contributors may be used
 *    to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */
package test.implemention_related;

import lab.mars.util.async.AsyncStream;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
public class AsyncTest1 {
    AsyncStream asyncStream;
    CountDownLatch endLatch, startLatch;
    ExecutorService executor;
    static final int total;

    static {
        int n = 1;
        for (int i = 0; i < 8; i++) {
            n *= i + 1;
        }
        total = n;
    }

    @Param(value = {"1", "2", "3", "4", "5", "6", "7", "8"})
    int threadCount;

    @Setup(Level.Invocation)
    public void setup() {
        asyncStream = AsyncStream.deferredAsync();
        endLatch = new CountDownLatch(threadCount);
        startLatch = new CountDownLatch(1);
//        for (int i = 0; i < total; i++) {
//            asyncStream.<Integer>then(e -> {
//                e++;
//            });
//        }
//        asyncStream.end(() -> {
//            endLatch.countDown();
//        });

        executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    int load = total / threadCount;
                    startLatch.await();
                    for (int i1 = 0; i1 < load; i1++) {
                        if (i1 % 2 == 0)
                            asyncStream.onEvent(i1);
                        else
                            asyncStream.<Integer>then(e -> {
                                e++;
                            });
                    }
                    endLatch.countDown();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    @Benchmark
//    @Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
//    @Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
    @Warmup(iterations = 10, batchSize = 1000)
    @Measurement(iterations = 10, batchSize = 1000)
    @OperationsPerInvocation(1000)
    @BenchmarkMode(Mode.AverageTime)
    public void test() throws InterruptedException {
        startLatch.countDown();
        endLatch.await();
    }

    @TearDown(Level.Invocation)
    public void tearDown() {
        executor.shutdownNow();
    }

    /*
    Benchmark        (threadCount)  Mode  Cnt     Score     Error  Units
AsyncTest1.test              1  avgt   10  2239.138 ± 165.687  us/op
AsyncTest1.test              2  avgt   10  3173.787 ± 157.721  us/op
AsyncTest1.test              3  avgt   10  2796.642 ±  51.675  us/op
AsyncTest1.test              4  avgt   10  2239.680 ±  49.272  us/op
AsyncTest1.test              5  avgt   10  2410.128 ±  28.680  us/op
AsyncTest1.test              6  avgt   10  2408.522 ±  35.053  us/op
AsyncTest1.test              7  avgt   10  2225.983 ±  30.034  us/op
AsyncTest1.test              8  avgt   10  2299.488 ±  50.047  us/op
     */
    public static void main(String[] args) throws RunnerException, InterruptedException {
        Options opt = new OptionsBuilder()
                .include(AsyncTest1.class.getSimpleName())
                .jvmArgsPrepend("-XX:-RestrictContended")
                .threads(1)
                .forks(1)
                .timeUnit(TimeUnit.MICROSECONDS)
                .shouldDoGC(true)
                .syncIterations(true)
                .shouldFailOnError(true) // switch to "true" to fail the complete run
//                .addProfiler(StackProfiler.class)
//                .addProfiler(GCProfiler.class)
//                .addProfiler(HotspotThreadProfiler.class)
                .build();

        new Runner(opt).run();
    }

}
