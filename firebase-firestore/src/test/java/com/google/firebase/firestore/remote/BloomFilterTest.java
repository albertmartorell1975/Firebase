// Copyright 2022 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.firestore.remote;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Base64;
import java.util.HashMap;
import java.util.stream.Stream;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class BloomFilterTest {

  @Test
  public void testEmptyBloomFilter() {
    BloomFilter bloomFilter = new BloomFilter(new byte[0], 0, 0);
    assertEquals(bloomFilter.getSize(), 0);
  }

  @Test
  public void testEmptyBloomFilterThrowException() {
    IllegalArgumentException paddingException =
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(new byte[0], 1, 0));
    assertThat(paddingException)
        .hasMessageThat()
        .contains("Invalid padding when bitmap length is 0: 1");
    IllegalArgumentException hashCountException =
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(new byte[0], 0, -1));
    assertThat(hashCountException).hasMessageThat().contains("Invalid hash count: -1");
  }

  @Test
  public void testNonEmptyBloomFilter() {
    BloomFilter bloomFilter1 = new BloomFilter(new byte[1], 0, 1);
    assertEquals(bloomFilter1.getSize(), 8);
    BloomFilter bloomFilter2 = new BloomFilter(new byte[1], 7, 1);
    assertEquals(bloomFilter2.getSize(), 1);
  }

  @Test
  public void testNonEmptyBloomFilterThrowException() {
    IllegalArgumentException negativePaddingException =
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(new byte[1], -1, 1));
    assertThat(negativePaddingException).hasMessageThat().contains("Invalid padding: -1");
    IllegalArgumentException overflowPaddingException =
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(new byte[1], 8, 1));
    assertThat(overflowPaddingException).hasMessageThat().contains("Invalid padding: 8");

    IllegalArgumentException negativeHashCountException =
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(new byte[1], 1, -1));
    assertThat(negativeHashCountException).hasMessageThat().contains("Invalid hash count: -1");
    IllegalArgumentException zeroHashCountException =
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(new byte[1], 1, 0));
    assertThat(zeroHashCountException).hasMessageThat().contains("Invalid hash count: 0");
  }

  @Test
  public void testBloomFilterProcessNonStandardCharacters() {
    // A non-empty BloomFilter object with 1 insertion : "ÀÒ∑"
    BloomFilter bloomFilter = new BloomFilter(new byte[] {(byte) 237, 5}, 5, 8);
    assertTrue(bloomFilter.mightContain("ÀÒ∑"));
    assertFalse(bloomFilter.mightContain("Ò∑À"));
  }

  @Test
  public void testEmptyBloomFilterMightContainAlwaysReturnFalse() {
    BloomFilter bloomFilter = new BloomFilter(new byte[0], 0, 0);
    assertFalse(bloomFilter.mightContain("abc"));
  }

  @Test
  public void testBloomFilterMightContainOnEmptyStringAlwaysReturnFalse() {
    BloomFilter emptyBloomFilter = new BloomFilter(new byte[0], 0, 0);
    BloomFilter nonEmptyBloomFilter =
        new BloomFilter(new byte[] {(byte) 255, (byte) 255, (byte) 255}, 1, 16);

    assertFalse(emptyBloomFilter.mightContain(""));
    assertFalse(nonEmptyBloomFilter.mightContain(""));
  }

  /**
   * Golden tests are generated by backend based on inserting n number of document paths into a
   * bloom filter.
   *
   * <p>Full document path is generated by concatenating documentPrefix and number n, eg,
   * projects/project-1/databases/database-1/documents/coll/doc12.
   *
   * <p>The test result is generated by checking the membership of documents from documentPrefix+0
   * to documentPrefix+2n. The membership results from 0 to n is expected to be true, and the
   * membership results from n to 2n is expected to be false with some false positive results.
   */
  @Test
  @SuppressWarnings("DefaultCharset")
  public void testBloomFilterGoldenTest() throws Exception {
    String documentPrefix = "projects/project-1/databases/database-1/documents/coll/doc";

    // Import the golden test files for bloom filter
    HashMap<String, JSONObject> parsedSpecFiles = new HashMap<>();
    File jsonDir = new File("src/test/resources/bloom_filter_golden_test_data");
    File[] jsonFiles = jsonDir.listFiles();
    for (File f : jsonFiles) {
      if (!f.toString().endsWith(".json")) {
        continue;
      }

      // Read the files into a map.
      StringBuilder builder = new StringBuilder();
      BufferedReader reader = new BufferedReader(new FileReader(f));
      Stream<String> lines = reader.lines();
      lines.forEach(builder::append);
      String json = builder.toString();
      JSONObject fileJSON = new JSONObject(json);
      parsedSpecFiles.put(f.getName(), fileJSON);
    }

    // Loop and test the files
    for (String fileName : parsedSpecFiles.keySet()) {
      if (fileName.contains("membership_test_result")) {
        continue;
      }

      // Read test data and instantiate a BloomFilter object
      JSONObject fileJSON = parsedSpecFiles.get(fileName);
      JSONObject bits = fileJSON.getJSONObject("bits");
      String bitmap = bits.getString("bitmap");
      int padding = bits.getInt("padding");
      int hashCount = fileJSON.getInt("hashCount");
      BloomFilter bloomFilter =
          new BloomFilter(Base64.getDecoder().decode(bitmap), padding, hashCount);

      // Find corresponding membership test result.
      JSONObject resultJSON =
          parsedSpecFiles.get(fileName.replace("bloom_filter_proto", "membership_test_result"));
      String membershipTestResults = resultJSON.getString("membershipTestResults");

      // Run and compare mightContain result with the expectation.
      for (int i = 0; i < membershipTestResults.length(); i++) {
        boolean expectedMembershipResult = membershipTestResults.charAt(i) == '1';
        boolean mightContain = bloomFilter.mightContain(documentPrefix + i);
        assertEquals(mightContain, expectedMembershipResult);
      }
    }
  }
}
