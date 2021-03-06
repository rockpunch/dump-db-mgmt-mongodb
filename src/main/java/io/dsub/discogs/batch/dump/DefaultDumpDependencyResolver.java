package io.dsub.discogs.batch.dump;

import io.dsub.discogs.batch.argument.ArgType;
import io.dsub.discogs.batch.dump.service.DiscogsDumpService;
import io.dsub.discogs.batch.exception.DumpNotFoundException;
import io.dsub.discogs.batch.exception.InvalidArgumentException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultDumpDependencyResolver implements DumpDependencyResolver {

  private static final String ETAG = ArgType.ETAG.getGlobalName();
  private static final String TYPE = ArgType.TYPE.getGlobalName();
  private static final String YEAR = ArgType.YEAR.getGlobalName();
  private static final String STRICT = ArgType.STRICT.getGlobalName();
  private static final String YEAR_MONTH = ArgType.YEAR_MONTH.getGlobalName();
  private final DiscogsDumpService dumpService;

  /**
   * Resolves {@link ApplicationArguments} to obtain list of {@link DiscogsDump} items to be
   * consumed from batch process.
   *
   * @param args Argument to be resolved
   * @return List of {@link DiscogsDump} to be handled.
   */
  @Override
  public Collection<DiscogsDump> resolve(ApplicationArguments args)
      throws DumpNotFoundException, InvalidArgumentException {

    if (args.containsOption(ETAG)) {
      if (args.containsOption(STRICT)) {
        return resolveByStrictETagEntries(args);
      }
      return resolveByETagEntries(args.getOptionValues(ETAG));
    }
    List<EntityType> types = parseTypes(args);
    int year = parseYear(args);
    int month = parseMonth(args);
    boolean retry = false;

    LocalDate targetDate = LocalDate.of(year, month, 1);

    Collection<DiscogsDump> dumps = null;
    while (dumps == null) {
      try {
        if (!retry) { // prevent redundant logging.
          log.info(
              "find dump for year:{}, month:{} of types:{}.",
              targetDate.getYear(),
              targetDate.getMonthValue(),
              types);
        }
        dumps =
            dumpService.getAllByTypeYearMonth(
                types, targetDate.getYear(), targetDate.getMonthValue());
      } catch (DumpNotFoundException ignored) {
        if (targetDate.getYear() > 2010) {
          targetDate = targetDate.minusMonths(1);
          log.info(
              "retrying to find dump for year:{} month:{} of types:{}.",
              targetDate.getYear(),
              targetDate.getMonthValue(),
              types);
          retry = true;
          continue;
        }
        throw new DumpNotFoundException("failed to find dump...");
      }
    }
    return dumps;
  }

  /**
   * Returns ETag regardless of dependencies.
   *
   * @param args argument to be examined.
   * @return list of discogs dump fetched via etag.
   */
  private List<DiscogsDump> resolveByStrictETagEntries(ApplicationArguments args) {
    log.info("strict option found. skip resolve dependencies..");
    return args.getOptionValues(ETAG).stream()
        .map(
            etag -> {
              try {
                return this.getDumpByETag(etag);
              } catch (DumpNotFoundException | InvalidArgumentException e) {
                log.error(e.getMessage(), e);
                return null;
              }
            })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  /**
   * Returns entire dump required to be processed by eTags. The process includes the validation of
   * each given eTags and it dependencies' validation.
   *
   * @param eTags to be examined. it is safe to contain duplicated eTags.
   * @return dump list containing required items to be processed.
   * @throws InvalidArgumentException thrown if given list is null or empty, or eTag is not
   *                                  present.
   * @throws DumpNotFoundException    if dump cannot be found under specific type, year and month.
   */
  protected Collection<DiscogsDump> resolveByETagEntries(List<String> eTags)
      throws DumpNotFoundException, InvalidArgumentException {
    if (eTags == null || eTags.isEmpty()) {
      throw new InvalidArgumentException("eTags cannot be null or empty");
    }

    LocalDate foundDate = null;
    Set<EntityType> requiredTypes = new HashSet<>();
    Map<EntityType, DiscogsDump> foundDumps = new HashMap<>();

    // validates given eTags, then save the progress to map nad set.
    for (String eTag : eTags.stream().distinct().collect(Collectors.toList())) {

      DiscogsDump dump = getDumpByETag(eTag);
      LocalDate dumpYearMonth = dump.getLastModifiedAt().withDayOfMonth(1);

      if (foundDate == null) {
        foundDate = dumpYearMonth;
      } else if (!foundDate.equals(dumpYearMonth)) { // not the same year and month.
        throw new InvalidArgumentException("eTags must be from the same year and month");
      }
      requiredTypes.addAll(dump.getType().getDependencies());
      foundDumps.put(dump.getType(), dump);
    }

    assert foundDate != null; // ignore

    int year = foundDate.getYear();
    int month = foundDate.getMonthValue();

    for (EntityType requiredType : requiredTypes) {
      if (foundDumps.containsKey(requiredType)) { // already found, so skip.
        continue;
      }
      // we have required type, year and month. proceeding to save them into map!
      DiscogsDump dump =
          dumpService.getMostRecentDiscogsDumpByTypeYearMonth(requiredType, year, month);
      if (dump == null) {
        throw new DumpNotFoundException(
            String.format(
                "discogs dump with type %s under %d-%d not found", requiredType, year, month));
      }
      foundDumps.put(requiredType, dump);
    }
    return foundDumps.values();
  }

  /**
   * Calls dumpService to fetch dump via ETag entry.
   */
  private DiscogsDump getDumpByETag(String eTag)
      throws InvalidArgumentException, DumpNotFoundException {
    DiscogsDump dump = dumpService.getDiscogsDump(eTag);
    if (dump == null) {
      throw new DumpNotFoundException("dump of eTag " + eTag + " not found");
    }
    return dump;
  }

  /**
   * Parses target month to be processed whether the given entry is present of not. It is important
   * to note that default value will be current month based on Clock.SystemUTC().
   *
   * @param args Argument to be extracted.
   * @return Extracted or default value.
   */
  protected int parseMonth(ApplicationArguments args) {
    if (args.containsOption(YEAR_MONTH)) {
      return Integer.parseInt(args.getOptionValues(YEAR_MONTH).get(0).substring(5));
    }
    if (args.containsOption(YEAR)) {
      return 1;
    }
    return LocalDate.now(Clock.systemUTC()).getMonthValue();
  }

  /**
   * Parses target year to be processed whether the given entry is present of not. It is important
   * to note that default value will be current year based on Clock.SystemUTC().
   *
   * @param args Argument to be extracted.
   * @return Extracted or default value.
   */
  protected int parseYear(ApplicationArguments args) {
    boolean hasYear = args.containsOption(YEAR);
    boolean hasYearMonth = args.containsOption(YEAR_MONTH);
    if (!hasYear && !hasYearMonth) {
      return LocalDate.now(Clock.systemUTC()).getYear();
    }
    String target;
    if (hasYear) {
      target = args.getOptionValues(YEAR).get(0);
    } else {
      target = args.getOptionValues(YEAR_MONTH).get(0);
    }
    return Integer.parseInt(target, 0, 4, 10);
  }

  /**
   * Parses target type and resolve its dependant types. If no type has been specified, then should
   * return entire types. It is important to note that default value will be the entire types of
   * {@link EntityType}.
   *
   * @param args Argument to be extracted.
   * @return Resolved types if type entry present, or simply all types of {@link EntityType}.
   */
  protected List<EntityType> parseTypes(ApplicationArguments args) throws InvalidArgumentException {
    if (!args.containsOption(TYPE)) {
      return List.of(EntityType.values());
    }

    Set<EntityType> requiredTypes = new HashSet<>();

    if (args.containsOption(STRICT)) {
      log.info("strict option found. skip resolve dependencies..");
      for (String s : args.getOptionValues(TYPE)) {
        EntityType of = EntityType.of(s);
        requiredTypes.add(of);
      }
      return List.copyOf(requiredTypes);
    }

    for (String s : args.getOptionValues(TYPE)) {
      EntityType of = EntityType.of(s);
      List<EntityType> dependencies = of.getDependencies();
      requiredTypes.addAll(dependencies);
    }
    return List.copyOf(requiredTypes);
  }
}
