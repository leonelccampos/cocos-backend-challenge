package com.cocobank.challenge.instrumentsearch.domain;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class InstrumentSearchPolicy {

  private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
  private static final Comparator<InstrumentSearchCandidate> RESULT_ORDER =
      Comparator.comparing(
              InstrumentSearchCandidate::ticker, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparingInt(InstrumentSearchCandidate::id);

  private InstrumentSearchPolicy() {}

  public static List<InstrumentSearchResult> search(
      List<InstrumentSearchCandidate> candidates, String query, InstrumentSearchField field) {
    return filterAndSort(candidates, query, field).stream()
        .map(InstrumentSearchPolicy::selectMarketPrice)
        .toList();
  }

  public static List<InstrumentSearchCandidate> filterAndSort(
      List<InstrumentSearchCandidate> candidates, String query, InstrumentSearchField field) {
    Objects.requireNonNull(field, "field");
    String normalizedQuery = normalizeForSearch(query);
    return candidates.stream()
        .filter(candidate -> "ACCIONES".equals(candidate.type()))
        .filter(candidate -> matches(candidate, normalizedQuery, field))
        .sorted(RESULT_ORDER)
        .toList();
  }

  private static InstrumentSearchResult selectMarketPrice(InstrumentSearchCandidate candidate) {
    BigDecimal price = null;
    var closeDate = candidate.latestPositiveCloseDate();
    if (isPositive(candidate.latestPositiveClose()) && closeDate != null) {
      price = candidate.latestPositiveClose();
    } else {
      closeDate = null;
      if (isPositive(candidate.latestDatedPreviousClose())) {
        price = candidate.latestDatedPreviousClose();
      }
    }
    return new InstrumentSearchResult(
        candidate.id(), candidate.ticker(), candidate.name(), candidate.type(), price, closeDate);
  }

  private static boolean isPositive(BigDecimal value) {
    return value != null && value.signum() > 0;
  }

  private static boolean matches(
      InstrumentSearchCandidate candidate, String normalizedQuery, InstrumentSearchField field) {
    String value =
        switch (field) {
          case TICKER -> candidate.ticker();
          case NAME -> candidate.name();
        };
    return value != null && normalizeForSearch(value).contains(normalizedQuery);
  }

  private static String normalizeForSearch(String value) {
    String decomposed = Normalizer.normalize(value.strip(), Normalizer.Form.NFD);
    return DIACRITICS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
  }
}
