package io.spring.api.web;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A media type or media range (RFC 7231, section 5.3.2) as the API reads it from the {@code Accept}
 * and {@code Content-Type} headers. The graded answers depend on the original service's exact
 * rules, which Micronaut's {@code MediaType} does not apply, so they are kept here: strict RFC 7230
 * token syntax (a header that does not parse is answered with 406 or 415), checked {@code charset}
 * and {@code q} parameters, {@code +json} structured-suffix ranges, and the preference order
 * (specificity, then quality) that picks the response type.
 */
public final class MediaRange {
  public static final MediaRange ANY = new MediaRange("*", "*", Map.of());
  public static final MediaRange ANY_APPLICATION = new MediaRange("application", "*", Map.of());
  public static final MediaRange APPLICATION_JSON = new MediaRange("application", "json", Map.of());
  public static final MediaRange ANY_JSON_SUFFIX =
      new MediaRange("application", "*+json", Map.of());

  private static final String WILDCARD = "*";

  private static final String PARAM_QUALITY = "q";
  private static final String PARAM_CHARSET = "charset";
  private static final BitSet TOKEN;

  static {
    BitSet ctl = new BitSet(128);
    for (int i = 0; i <= 31; i++) {
      ctl.set(i);
    }
    ctl.set(127);
    BitSet separators = new BitSet(128);
    for (char c : "()<>@,;:\\\"/[]?={} \t".toCharArray()) {
      separators.set(c);
    }
    TOKEN = new BitSet(128);
    TOKEN.set(0, 128);
    TOKEN.andNot(ctl);
    TOKEN.andNot(separators);
  }

  private final String type;
  private final String subtype;
  private final Map<String, String> parameters;

  private MediaRange(String type, String subtype, Map<String, String> parameters) {
    this.type = type.toLowerCase(Locale.ENGLISH);
    this.subtype = subtype.toLowerCase(Locale.ENGLISH);
    this.parameters = Collections.unmodifiableMap(parameters);
  }

  /** Parses one media range; a value that breaks the syntax is an IllegalArgumentException. */
  public static MediaRange parse(String value) {
    if (value == null || value.isEmpty()) {
      throw invalid(value, "no type");
    }
    int index = value.indexOf(';');
    String fullType = (index >= 0 ? value.substring(0, index) : value).trim();
    if (fullType.isEmpty()) {
      throw invalid(value, "no type");
    }
    if (WILDCARD.equals(fullType)) {
      fullType = "*/*";
    }
    int subIndex = fullType.indexOf('/');
    if (subIndex == -1) {
      throw invalid(value, "no '/' between type and subtype");
    }
    if (subIndex == fullType.length() - 1) {
      throw invalid(value, "no subtype after '/'");
    }
    String type = fullType.substring(0, subIndex);
    String subtype = fullType.substring(subIndex + 1);
    if (WILDCARD.equals(type) && !WILDCARD.equals(subtype)) {
      throw invalid(value, "a wildcard type needs a wildcard subtype");
    }
    Map<String, String> parameters = new LinkedHashMap<>(4);
    do {
      int nextIndex = index + 1;
      boolean quoted = false;
      while (nextIndex < value.length()) {
        char ch = value.charAt(nextIndex);
        if (ch == ';') {
          if (!quoted) {
            break;
          }
        } else if (ch == '"') {
          quoted = !quoted;
        }
        nextIndex++;
      }
      String parameter = value.substring(index + 1, nextIndex).trim();
      if (parameter.length() > 0) {
        int eqIndex = parameter.indexOf('=');
        if (eqIndex >= 0) {
          String attribute = parameter.substring(0, eqIndex).trim();
          String attributeValue = parameter.substring(eqIndex + 1).trim();
          putParameter(parameters, attribute, attributeValue);
        }
      }
      index = nextIndex;
    } while (index < value.length());

    try {
      checkToken(type);
      checkToken(subtype);
      for (Map.Entry<String, String> parameter : parameters.entrySet()) {
        checkParameter(parameter.getKey(), parameter.getValue());
      }
    } catch (IllegalArgumentException e) {
      throw invalid(value, e.getMessage());
    }
    return new MediaRange(type, subtype, parameters);
  }

  private static IllegalArgumentException invalid(String value, String reason) {
    return new IllegalArgumentException("Invalid media type \"" + value + "\": " + reason);
  }

  /** Parses a list of comma separated media ranges, skipping empty entries. */
  public static List<MediaRange> parseList(List<String> headerValues) {
    List<MediaRange> result = new ArrayList<>();
    for (String headerValue : headerValues) {
      if (headerValue == null) {
        continue;
      }
      for (String token : tokenize(headerValue)) {
        if (!token.isBlank()) {
          result.add(parse(token));
        }
      }
    }
    return result;
  }

  private static List<String> tokenize(String value) {
    List<String> tokens = new ArrayList<>();
    boolean inQuotes = false;
    int startIndex = 0;
    int i = 0;
    while (i < value.length()) {
      switch (value.charAt(i)) {
        case '"':
          inQuotes = !inQuotes;
          break;
        case ',':
          if (!inQuotes) {
            tokens.add(value.substring(startIndex, i));
            startIndex = i + 1;
          }
          break;
        case '\\':
          i++;
          break;
        default:
          break;
      }
      i++;
    }
    tokens.add(value.substring(startIndex));
    return tokens;
  }

  private static void putParameter(Map<String, String> parameters, String key, String value) {
    parameters.keySet().removeIf(existing -> existing.equalsIgnoreCase(key));
    parameters.put(key, value);
  }

  private static void checkToken(String token) {
    for (int i = 0; i < token.length(); i++) {
      char ch = token.charAt(i);
      if (ch >= 128 || !TOKEN.get(ch)) {
        throw new IllegalArgumentException(
            "character '" + ch + "' is not allowed in token \"" + token + "\"");
      }
    }
  }

  private static void checkParameter(String attribute, String value) {
    if (attribute.isEmpty() || value.isEmpty()) {
      throw new IllegalArgumentException("empty parameter name or value");
    }
    checkToken(attribute);
    if (PARAM_CHARSET.equals(attribute)) {
      Charset.forName(unquote(value));
    } else if (PARAM_QUALITY.equals(attribute)) {
      double quality = Double.parseDouble(unquote(value));
      if (quality < 0D || quality > 1D) {
        throw new IllegalArgumentException("quality \"" + value + "\" is not between 0 and 1");
      }
    } else if (!isQuotedString(value)) {
      checkToken(value);
    }
  }

  private static boolean isQuotedString(String s) {
    if (s.length() < 2) {
      return false;
    }
    return (s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"));
  }

  private static String unquote(String s) {
    return isQuotedString(s) ? s.substring(1, s.length() - 1) : s;
  }

  public String type() {
    return type;
  }

  public String subtype() {
    return subtype;
  }

  /** The value of parameter {@code name} (case-insensitive), or null. */
  public String parameterValue(String name) {
    for (Map.Entry<String, String> entry : parameters.entrySet()) {
      if (entry.getKey().equalsIgnoreCase(name)) {
        return entry.getValue();
      }
    }
    return null;
  }

  /** The {@code charset} parameter, or null when there is none. */
  public Charset charset() {
    String charset = parameterValue(PARAM_CHARSET);
    return charset == null ? null : Charset.forName(unquote(charset));
  }

  /** The {@code q} parameter, 1 by default. */
  public double quality() {
    String quality = parameterValue(PARAM_QUALITY);
    return quality == null ? 1D : Double.parseDouble(unquote(quality));
  }

  private boolean isWildcardType() {
    return WILDCARD.equals(type);
  }

  private boolean isWildcardSubtype() {
    return WILDCARD.equals(subtype) || subtype.startsWith("*+");
  }

  /** Whether this names one media type rather than a range. */
  public boolean isSpecific() {
    return !isWildcardType() && !isWildcardSubtype();
  }

  private String suffix() {
    int suffixIndex = subtype.lastIndexOf('+');
    if (suffixIndex != -1 && subtype.length() > suffixIndex) {
      return subtype.substring(suffixIndex + 1);
    }
    return null;
  }

  /** Whether this range, possibly a wildcard, covers all of {@code other}. */
  public boolean covers(MediaRange other) {
    if (other == null) {
      return false;
    }
    if (isWildcardType()) {
      return true;
    } else if (type.equals(other.type)) {
      if (subtype.equals(other.subtype)) {
        return true;
      }
      if (isWildcardSubtype()) {
        int thisPlusIdx = subtype.lastIndexOf('+');
        if (thisPlusIdx == -1) {
          return true;
        } else {
          int otherPlusIdx = other.subtype.lastIndexOf('+');
          if (otherPlusIdx != -1) {
            String thisSubtypeNoSuffix = subtype.substring(0, thisPlusIdx);
            String thisSubtypeSuffix = subtype.substring(thisPlusIdx + 1);
            String otherSubtypeSuffix = other.subtype.substring(otherPlusIdx + 1);
            if (thisSubtypeSuffix.equals(otherSubtypeSuffix)
                && WILDCARD.equals(thisSubtypeNoSuffix)) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  /** Whether either of the two ranges covers the other. */
  public boolean overlaps(MediaRange other) {
    if (other == null) {
      return false;
    }
    if (isWildcardType() || other.isWildcardType()) {
      return true;
    } else if (type.equals(other.type)) {
      if (subtype.equals(other.subtype)) {
        return true;
      }
      if (isWildcardSubtype() || other.isWildcardSubtype()) {
        String thisSuffix = suffix();
        String otherSuffix = other.suffix();
        if (WILDCARD.equals(subtype) || WILDCARD.equals(other.subtype)) {
          return true;
        } else if (isWildcardSubtype() && thisSuffix != null) {
          return thisSuffix.equals(other.subtype) || thisSuffix.equals(otherSuffix);
        } else if (other.isWildcardSubtype() && otherSuffix != null) {
          return subtype.equals(otherSuffix) || otherSuffix.equals(thisSuffix);
        }
      }
    }
    return false;
  }

  public boolean sameTypeAs(MediaRange other) {
    return other != null && type.equals(other.type) && subtype.equals(other.subtype);
  }

  /** This range with the {@code q} parameter of {@code from}, if it has one. */
  public MediaRange withQualityOf(MediaRange from) {
    String quality = from.parameterValue(PARAM_QUALITY);
    if (quality == null) {
      return this;
    }
    Map<String, String> params = new LinkedHashMap<>(parameters);
    putParameter(params, PARAM_QUALITY, quality);
    return new MediaRange(type, subtype, params);
  }

  public MediaRange withoutQuality() {
    if (parameterValue(PARAM_QUALITY) == null) {
      return this;
    }
    Map<String, String> params = new LinkedHashMap<>(parameters);
    params.keySet().removeIf(key -> key.equalsIgnoreCase(PARAM_QUALITY));
    return new MediaRange(type, subtype, params);
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder(type).append('/').append(subtype);
    parameters.forEach((key, value) -> builder.append(';').append(key).append('=').append(value));
    return builder.toString();
  }

  /**
   * Compares by specificity: a concrete type before a wildcard and a concrete subtype before a
   * wildcard subtype; for the same type and subtype, the higher quality and then the one with more
   * parameters first. Ranges of different types or subtypes are equally specific.
   */
  public static int compareSpecificity(MediaRange a, MediaRange b) {
    int wildcards = compareWildcards(a, b);
    if (wildcards != 0 || !a.sameTypeAs(b)) {
      return wildcards;
    }
    int quality = Double.compare(b.quality(), a.quality());
    return quality != 0 ? quality : Integer.compare(b.parameters.size(), a.parameters.size());
  }

  /** Compares by quality, highest first, then by specificity. */
  private static int compareQuality(MediaRange a, MediaRange b) {
    int quality = Double.compare(b.quality(), a.quality());
    if (quality != 0) {
      return quality;
    }
    int wildcards = compareWildcards(a, b);
    if (wildcards != 0 || !a.sameTypeAs(b)) {
      return wildcards;
    }
    return Integer.compare(b.parameters.size(), a.parameters.size());
  }

  /** A concrete type, then a concrete subtype of the same type, sorts first; 0 otherwise. */
  private static int compareWildcards(MediaRange a, MediaRange b) {
    if (a.isWildcardType() != b.isWildcardType()) {
      return a.isWildcardType() ? 1 : -1;
    }
    if (!a.type.equals(b.type)) {
      return 0;
    }
    if (a.isWildcardSubtype() != b.isWildcardSubtype()) {
      return a.isWildcardSubtype() ? 1 : -1;
    }
    return 0;
  }

  /** Sorts {@code ranges} most preferred first: by specificity, then by quality (stable). */
  public static void sortByPreference(List<MediaRange> ranges) {
    if (ranges.size() > 1) {
      ranges.sort(
          ((Comparator<MediaRange>) MediaRange::compareSpecificity)
              .thenComparing(MediaRange::compareQuality));
    }
  }
}
