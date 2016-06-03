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
public class AsyncTest2 {
    AsyncStream asyncStream;
    CountDownLatch endLatch, startLatch;
    ExecutorService executor;
    static final int total = 40320;

    @Param(value = {"1", "2", "3", "4", "5", "6", "7", "8"})
    int threadCount;

    @Setup(Level.Invocation)
    public void setup() {
        asyncStream = AsyncStream.deferredAsync();
        endLatch = new CountDownLatch(1);
        startLatch = new CountDownLatch(1);
        for (int i = 0; i < total; i++) {
            asyncStream.<Integer>then(e -> {
                e++;
            });
        }
        asyncStream.end(() -> {
            endLatch.countDown();
        });

        executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    int load = total / threadCount;
                    startLatch.await();
                    for (int i1 = 0; i1 < load; i1++) {
                        asyncStream.onEvent(i1);
//                        if (i1 % 2 == 0)
//                            asyncStream.onEvent(i1);
//                        else
//                            asyncStream.<Integer>then(e -> {
//                                e++;
//                            });
                    }
//                    endLatch.countDown();
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
    @OperationsPerInvocation(1000 )
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
AsyncTest2.test              1  avgt   10  3117.273 ± 317.487  us/op
AsyncTest2.test              2  avgt   10  3507.157 ± 114.938  us/op
AsyncTest2.test              3  avgt   10  3084.216 ±  80.021  us/op
AsyncTest2.test              4  avgt   10  2797.186 ± 101.830  us/op
AsyncTest2.test              5  avgt   10  2868.515 ±  46.786  us/op
AsyncTest2.test              6  avgt   10  2816.878 ±  37.290  us/op
AsyncTest2.test              7  avgt   10  2960.530 ± 409.869  us/op
AsyncTest2.test              8  avgt   10  2759.667 ±  30.220  us/op


Benchmark        (threadCount)  Mode  Cnt     Score     Error  Units
AsyncTest2.test              1  avgt   10  3096.851 ± 174.783  us/op
AsyncTest2.test              2  avgt   10  3194.395 ±  72.536  us/op
AsyncTest2.test              3  avgt   10  3152.409 ±  45.526  us/op
AsyncTest2.test              4  avgt   10  2728.716 ±  85.472  us/op
AsyncTest2.test              5  avgt   10  2854.630 ±  75.907  us/op
AsyncTest2.test              6  avgt   10  2873.981 ±  34.585  us/op
AsyncTest2.test              7  avgt   10  3051.169 ± 201.987  us/op
AsyncTest2.test              8  avgt   10  2855.872 ±  62.533  us/op

lazySet version
Benchmark        (threadCount)  Mode  Cnt     Score     Error  Units
AsyncTest2.test              1  avgt   10  2788.985 ± 114.927  us/op
AsyncTest2.test              2  avgt   10  3097.012 ± 138.015  us/op
AsyncTest2.test              3  avgt   10  2968.466 ±  44.522  us/op
AsyncTest2.test              4  avgt   10  2699.264 ±  44.041  us/op
AsyncTest2.test              5  avgt   10  2704.832 ±  43.998  us/op
AsyncTest2.test              6  avgt   10  2815.367 ±  24.906  us/op
AsyncTest2.test              7  avgt   10  2696.310 ±  26.508  us/op
AsyncTest2.test              8  avgt   10  2799.674 ±  27.635  us/op
     */



    public static void main(String[] args) throws RunnerException, InterruptedException {
        Options opt = new OptionsBuilder()
                .include(AsyncTest2.class.getSimpleName())
                .jvmArgsPrepend("-XX:-RestrictContended -XX:+PrintCompilation")
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
