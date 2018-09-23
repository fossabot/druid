/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.druid.java.util.common.guava;

import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 */
public class MergeSequenceTest
{
  public static <T extends Comparable<? super T>> Supplier<Sequence<T>> naturalMergeSupplier(
      Supplier<Stream<? extends Sequence<? extends T>>> stream
  )
  {
    return () -> naturalMerge(stream.get());
  }

  public static <T extends Comparable<? super T>> Sequence<T> naturalMerge(Stream<? extends Sequence<? extends T>> stream)
  {
    return new MergeSequence<>(
        Ordering.natural(),
        Sequences.fromStream(stream)
    );
  }

  @Test
  public void testSanity() throws Exception
  {
    final ArrayList<TestSequence<Integer>> testSeqs = Lists.newArrayList(
        TestSequence.create(1, 3, 5, 7, 9),
        TestSequence.create(2, 8),
        TestSequence.create(4, 6, 8)
    );
    final List<Integer> expected = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 8, 9);

    MergeSequence<Integer> seq = new MergeSequence<>(
        Ordering.<Integer>natural(),
        (Sequence) Sequences.simple(testSeqs)
    );
    SequenceTestHelper.testAll(seq, expected);

    for (TestSequence<Integer> sequence : testSeqs) {
      Assert.assertTrue(sequence.isClosed());
    }

    SequenceTestHelper.testAll(
        naturalMergeSupplier(testSeqs::stream),
        expected
    );

  }

  @Test
  public void testWorksWhenBeginningOutOfOrder() throws Exception
  {
    final ArrayList<TestSequence<Integer>> testSeqs = Lists.newArrayList(
        TestSequence.create(2, 8),
        TestSequence.create(1, 3, 5, 7, 9),
        TestSequence.create(4, 6, 8)
    );

    final List<Integer> expected = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 8, 9);

    MergeSequence<Integer> seq = new MergeSequence<>(Ordering.natural(), Sequences.simple(testSeqs));
    SequenceTestHelper.testAll(seq, expected);

    for (TestSequence<Integer> sequence : testSeqs) {
      Assert.assertTrue(sequence.isClosed());
    }

    SequenceTestHelper.testAll(
        naturalMergeSupplier(testSeqs::stream),
        expected
    );
  }

  @Test
  public void testMergeEmpties() throws Exception
  {
    final ArrayList<TestSequence<Integer>> testSeqs = Lists.newArrayList(
        TestSequence.create(1, 3, 5, 7, 9),
        TestSequence.create(),
        TestSequence.create(2, 8),
        TestSequence.create(4, 6, 8)
    );

    final List<Integer> expected = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 8, 9);

    MergeSequence<Integer> seq = new MergeSequence<>(
        Ordering.natural(),
        Sequences.simple(testSeqs)
    );
    SequenceTestHelper.testAll(seq, expected);

    for (TestSequence<Integer> sequence : testSeqs) {
      Assert.assertTrue(sequence.isClosed());
    }

    SequenceTestHelper.testAll(
        naturalMergeSupplier(testSeqs::stream),
        expected
    );
  }

  @Test
  public void testMergeEmpties1() throws Exception
  {
    final ArrayList<TestSequence<Integer>> testSeqs = Lists.newArrayList(
        TestSequence.create(),
        TestSequence.create(1, 3, 5, 7, 9),
        TestSequence.create(2, 8),
        TestSequence.create(4, 6, 8)
    );

    final List<Integer> expected = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 8, 9);

    MergeSequence<Integer> seq = new MergeSequence<>(
        Ordering.natural(),
        Sequences.simple(testSeqs)
    );
    SequenceTestHelper.testAll(seq, expected);

    for (TestSequence<Integer> sequence : testSeqs) {
      Assert.assertTrue(sequence.isClosed());
    }

    SequenceTestHelper.testAll(
        naturalMergeSupplier(testSeqs::stream),
        expected
    );
  }

  @Test
  public void testMergeEmpties2() throws Exception
  {
    final ArrayList<TestSequence<Integer>> testSeqs = Lists.newArrayList(
        TestSequence.create(1, 3, 5, 7, 9),
        TestSequence.create(2, 8),
        TestSequence.create(),
        TestSequence.create(4, 6, 8),
        TestSequence.create()
    );

    final List<Integer> expected = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 8, 9);

    MergeSequence<Integer> seq = new MergeSequence<>(
        Ordering.natural(),
        Sequences.simple(testSeqs)
    );
    SequenceTestHelper.testAll(seq, expected);

    for (TestSequence<Integer> sequence : testSeqs) {
      Assert.assertTrue(sequence.isClosed());
    }

    SequenceTestHelper.testAll(
        naturalMergeSupplier(testSeqs::stream),
        expected
    );
  }

  @Test
  public void testScrewsUpOnOutOfOrder() throws Exception
  {
    final ArrayList<TestSequence<Integer>> testSeqs = Lists.newArrayList(
        TestSequence.create(1, 3, 5, 4, 7, 9),
        TestSequence.create(2, 8),
        TestSequence.create(4, 6)
    );

    final List<Integer> expected = Arrays.asList(1, 2, 3, 4, 5, 4, 6, 7, 8, 9);

    MergeSequence<Integer> seq = new MergeSequence<>(Ordering.natural(), Sequences.simple(testSeqs));
    SequenceTestHelper.testAll(seq, expected);

    for (TestSequence<Integer> sequence : testSeqs) {
      Assert.assertTrue(sequence.isClosed());
    }

    SequenceTestHelper.testAll(
        naturalMergeSupplier(testSeqs::stream),
        expected
    );
  }

  @Test
  public void testHierarchicalMerge() throws Exception
  {
    final Sequence<Integer> seq1 = new MergeSequence<>(
        Ordering.natural(), Sequences.simple(
        Collections.singletonList(TestSequence.create(1))
    )
    );

    final Sequence<Integer> finalMerged = new MergeSequence<>(
        Ordering.natural(),
        Sequences.simple(
            Collections.singletonList(seq1)
        )
    );

    SequenceTestHelper.testAll(finalMerged, Collections.singletonList(1));
  }

  @Test
  public void testMergeOne() throws Exception
  {
    final Sequence<Integer> mergeOne = new MergeSequence<>(
        Ordering.natural(),
        Sequences.<Sequence<Integer>>simple(
            Collections.singletonList(
                TestSequence.create(1)
            )
        )
    );

    SequenceTestHelper.testAll(mergeOne, Collections.singletonList(1));
  }

}
