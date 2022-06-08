package io.spring.api.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

public class MediaRangeTest {

  @Test
  public void should_parse_type_subtype_and_parameters() {
    MediaRange range = MediaRange.parse("Application/JSON; charset=utf-8 ; q=0.5");

    assertEquals("application", range.type());
    assertEquals("json", range.subtype());
    assertEquals("utf-8", range.parameterValue("charset"));
    assertEquals(StandardCharsets.UTF_8, range.charset());
    assertEquals(0.5, range.quality());
    assertEquals("application/json;charset=utf-8;q=0.5", range.toString());
    assertEquals("application/json;charset=utf-8", range.withoutQuality().toString());
    assertEquals("*/*", MediaRange.parse("*").toString());
    MediaRange quoted = MediaRange.parse("text/plain;title=\"a;b\";CHARSET='UTF-8'");
    assertEquals("\"a;b\"", quoted.parameterValue("TITLE"));
    assertEquals(StandardCharsets.UTF_8, quoted.charset());
    assertNull(MediaRange.APPLICATION_JSON.charset());
    assertEquals(1.0, MediaRange.APPLICATION_JSON.quality());
  }

  @Test
  public void should_reject_invalid_media_types() {
    assertInvalid("", "no type");
    assertInvalid(";q=1", "no type");
    assertInvalid("json", "no '/' between type and subtype");
    assertInvalid("text/", "no subtype after '/'");
    assertInvalid("*/json", "a wildcard type needs a wildcard subtype");
    assertInvalid("te@xt/plain", "character '@' is not allowed in token \"te@xt\"");
    assertInvalid("text/plain;q=1.5", "quality \"1.5\" is not between 0 and 1");
    assertInvalid("text/plain;charset=", "empty parameter name or value");
    assertThrows(IllegalArgumentException.class, () -> MediaRange.parse("text/plain;charset=x"));
  }

  @Test
  public void should_split_lists_outside_quotes_and_skip_blank_entries() {
    List<MediaRange> ranges =
        MediaRange.parseList(List.of("text/plain;x=\"a,b\", , application/json", "*/*"));

    assertEquals(
        List.of("text/plain;x=\"a,b\"", "application/json", "*/*"),
        ranges.stream().map(MediaRange::toString).collect(Collectors.toList()));
  }

  @Test
  public void should_match_wildcards_and_suffixes_in_covers() {
    MediaRange json = MediaRange.APPLICATION_JSON;
    MediaRange hal = MediaRange.parse("application/hal+json");

    assertTrue(MediaRange.ANY.covers(json));
    assertTrue(MediaRange.ANY_APPLICATION.covers(hal));
    assertTrue(MediaRange.ANY_JSON_SUFFIX.covers(hal));
    assertFalse(MediaRange.ANY_JSON_SUFFIX.covers(json));
    assertFalse(json.covers(MediaRange.ANY_APPLICATION));
    assertFalse(MediaRange.parse("text/*").covers(json));
    assertFalse(json.covers(null));
    assertTrue(json.overlaps(MediaRange.ANY_APPLICATION));
    assertTrue(MediaRange.ANY_JSON_SUFFIX.overlaps(json));
    assertFalse(MediaRange.parse("text/*").overlaps(json));
  }

  @Test
  public void should_sort_by_specificity_then_quality() {
    List<MediaRange> ranges =
        new ArrayList<>(
            MediaRange.parseList(
                List.of("*/*, application/*, application/json;q=0.5, application/json")));

    MediaRange.sortByPreference(ranges);

    assertEquals(
        List.of("application/json", "application/json;q=0.5", "application/*", "*/*"),
        ranges.stream().map(MediaRange::toString).collect(Collectors.toList()));
    assertTrue(
        MediaRange.compareSpecificity(
                MediaRange.parse("text/plain;a=1"), MediaRange.parse("text/plain"))
            < 0);
    assertEquals(
        0,
        MediaRange.compareSpecificity(MediaRange.parse("text/plain"), MediaRange.APPLICATION_JSON));
  }

  private static void assertInvalid(String value, String reason) {
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> MediaRange.parse(value));
    assertEquals("Invalid media type \"" + value + "\": " + reason, e.getMessage());
  }
}
