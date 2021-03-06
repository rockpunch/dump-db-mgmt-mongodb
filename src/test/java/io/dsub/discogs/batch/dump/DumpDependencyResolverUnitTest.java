package io.dsub.discogs.batch.dump;

import static io.dsub.discogs.batch.TestArguments.getRandomDump;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.dsub.discogs.batch.TestArguments;
import io.dsub.discogs.batch.argument.ArgType;
import io.dsub.discogs.batch.dump.service.DiscogsDumpService;
import io.dsub.discogs.batch.exception.DumpNotFoundException;
import io.dsub.discogs.batch.exception.InvalidArgumentException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.bytebuddy.utility.RandomString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;

class DumpDependencyResolverUnitTest {

  final Random rand = new Random();
  @Mock
  DiscogsDumpService dumpService;
  @InjectMocks
  DefaultDumpDependencyResolver resolver;
  @Captor
  ArgumentCaptor<List<EntityType>> dumpTypeCaptor;
  @Captor
  ArgumentCaptor<Integer> yearCaptor;
  @Captor
  ArgumentCaptor<Integer> monthCaptor;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @RepeatedTest(10)
  void whenDumpServiceFailedToFetchDumpByTypeYearMonth__ThenShouldThrowDumpNotFoundException()
      throws InvalidArgumentException, DumpNotFoundException {
    String typeName = rand.nextBoolean() ? "release" : "master";
    DiscogsDump fakeDump = TestArguments.getRandomDumpWithType(EntityType.of(typeName));
    LocalDate lastModifiedAt = fakeDump.getLastModifiedAt();
    int year = lastModifiedAt.getYear();
    int month = lastModifiedAt.getMonthValue();

    List<EntityType> dependencies = new ArrayList<>(fakeDump.getType().getDependencies());
    dependencies.remove(fakeDump.getType());
    EntityType toBeNull = dependencies.get(rand.nextInt(dependencies.size()));

    when(dumpService.getDiscogsDump(any())).thenReturn(fakeDump);
    for (EntityType type : dependencies) {
      DiscogsDump toReturn = type.equals(toBeNull) ? null : fakeDump;
      when(dumpService.getMostRecentDiscogsDumpByTypeYearMonth(type, year, month))
          .thenReturn(toReturn);
    }

    String msgFormat = "discogs dump with type %s under %d-%d not found";
    String msg = String.format(msgFormat, toBeNull, year, month);

    // when
    Throwable thrown = catchThrowable(() -> resolver.resolveByETagEntries(List.of("a")));

    // then
    assertThat(thrown).isNotNull().isInstanceOf(DumpNotFoundException.class).hasMessage(msg);
  }

  @Test
  void whenETagsContainDifferentDifferentCreatedAt__ThenShouldThrowInvalidArgumentException()
      throws InvalidArgumentException, DumpNotFoundException {
    DiscogsDump fakeDump = getRandomDump();
    when(dumpService.getDiscogsDump("a")).thenReturn(fakeDump);
    when(dumpService.getDiscogsDump("b"))
        .thenReturn(TestArguments
            .getRandomDumpWithLastModifiedAt(fakeDump.getLastModifiedAt().minusMonths(1)));
    List<String> eTags = List.of("a", "b");

    // when
    Throwable thrown = catchThrowable(() -> resolver.resolveByETagEntries(eTags));

    // then
    assertThat(thrown)
        .isNotNull()
        .hasMessage("eTags must be from the same year and month")
        .isInstanceOf(InvalidArgumentException.class);
  }

  @Test
  void whenEmptyTagEntriesGiven__ThenShouldThrowInvalidArgumentException() {
    assertThrows(
        InvalidArgumentException.class,
        () -> resolver.resolveByETagEntries(Collections.emptyList()));
    assertThatThrownBy(() -> resolver.resolveByETagEntries(Collections.emptyList()))
        .hasMessage("eTags cannot be null or empty")
        .isInstanceOf(InvalidArgumentException.class);
  }

  @Test
  void whenNullTagEntriesGiven__ThenShouldThrowInvalidArgumentException() {
    assertThrows(InvalidArgumentException.class, () -> resolver.resolveByETagEntries(null));
    assertThatThrownBy(() -> resolver.resolveByETagEntries(null))
        .hasMessage("eTags cannot be null or empty");
  }

  @Test
  void whenDumpServiceReturnsNull__ThenShouldThrowDumpNotFoundException()
      throws DumpNotFoundException {
    String eTag = RandomString.make();
    when(dumpService.getDiscogsDump(eTag)).thenReturn(null);
    // when
    assertThrows(DumpNotFoundException.class, () -> resolver.resolveByETagEntries(List.of(eTag)));
    assertThatThrownBy(() -> resolver.resolveByETagEntries(List.of(eTag)))
        .isInstanceOf(DumpNotFoundException.class)
        .hasMessage("dump of eTag " + eTag + " not found");
  }

  @Test
  void whenDumpServiceThrows__DumpNotFoundException__ThenShouldThrowAsIs()
      throws DumpNotFoundException {
    when(dumpService.getDiscogsDump(any())).thenThrow(new DumpNotFoundException("test"));
    // when
    assertThatThrownBy(() -> resolver.resolveByETagEntries(List.of(RandomString.make())))
        // then
        .isInstanceOf(DumpNotFoundException.class)
        .hasMessage("test");
  }

  @Test
  void whenDumpServiceThrows__InvalidArgumentException__ThenShouldThrowAsIs()
      throws DumpNotFoundException {
    when(dumpService.getDiscogsDump(any())).thenThrow(new DumpNotFoundException("test"));
    // when
    assertThatThrownBy(() -> resolver.resolveByETagEntries(List.of(RandomString.make())))
        // then
        .isInstanceOf(DumpNotFoundException.class)
        .hasMessage("test");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
          "artist,artist",
          "release,release",
          "master,master",
          "label,label,master",
          "artist,master,master",
          "artist,label,artist",
          "label,master,label",
          "master,release,master",
          "artist,master,release,master",
          "label,release,release,release"
      })
  void whenDuplicatedTypesFound__ShouldParseCorrectly(String type) throws InvalidArgumentException {
    ApplicationArguments args =
        new DefaultApplicationArguments(
            Arrays.stream(type.split(","))
                .map(
                    typeString ->
                        String.format("--%s=%s", ArgType.TYPE.getGlobalName(), typeString))
                .toArray(String[]::new));

    Set<EntityType> expectedValues = new HashSet<>();

    for (String s : type.split(",")) {
      EntityType of = EntityType.of(s);
      List<EntityType> dependencies = of.getDependencies();
      expectedValues.addAll(dependencies);
    }

    // when
    Collection<EntityType> parsed = resolver.parseTypes(args);

    // then
    assertThat(parsed.size()).isEqualTo(expectedValues.size());
    parsed.forEach(dumpType -> assertThat(dumpType).isIn(expectedValues));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
          "artist",
          "release",
          "master",
          "label",
          "artist,master",
          "artist,label",
          "label,master",
          "master,release",
          "artist,master,release",
          "label,release"
      })
  void whenTypeArgPresent__ThenShouldProvideAllDependencies(String type)
      throws InvalidArgumentException {
    ApplicationArguments args =
        new DefaultApplicationArguments(
            Arrays.stream(type.split(","))
                .map(
                    typeString ->
                        String.format("--%s=%s", ArgType.TYPE.getGlobalName(), typeString))
                .toArray(String[]::new));

    Set<EntityType> expectedValues = new HashSet<>();

    for (String s : type.split(",")) {
      EntityType of = EntityType.of(s);
      List<EntityType> dependencies = of.getDependencies();
      expectedValues.addAll(dependencies);
    }

    // when
    Collection<EntityType> parsed = resolver.parseTypes(args);

    // then
    assertThat(parsed.size()).isEqualTo(expectedValues.size());
    parsed.forEach(dumpType -> assertThat(dumpType).isIn(expectedValues));
  }

  @Test
  void whenOnlyYearPresent__ThenShouldReturnOne() {
    int year = 1990 + rand.nextInt(20);
    String opt = "--" + ArgType.YEAR.getGlobalName() + "=" + year;
    // when
    int parsed = resolver.parseMonth(new DefaultApplicationArguments(opt));
    // then
    assertThat(parsed).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12})
  void whenYearMonthPresent__ThenShouldParseCorrectly(int givenMonth) {
    int year = 1990 + rand.nextInt(20);
    String opt = "--" + ArgType.YEAR_MONTH.getGlobalName() + "=" + year + "-" + givenMonth;
    // when
    int parsed = resolver.parseMonth(new DefaultApplicationArguments(opt));
    // then
    assertThat(parsed).isEqualTo(givenMonth);
  }

  @ParameterizedTest
  @ValueSource(strings = {"1992", "1995", "1994-6", "2001-03", "2010-3"})
  void whenYearOrYearMonthPresent__ThenShouldParseCorrectly(String yearString) {
    int expected = Integer.parseInt(yearString, 0, 4, 10);
    String prefix =
        rand.nextBoolean() ? ArgType.YEAR_MONTH.getGlobalName() : ArgType.YEAR.getGlobalName();
    // when
    int parsed =
        resolver.parseYear(new DefaultApplicationArguments("--" + prefix + "=" + yearString));
    // then
    assertThat(parsed).isEqualTo(expected);
  }

  @Test
  void whenYearOrYearMonthNotPresent__ThenShouldReturnCurrentYear() {
    int expected = LocalDate.now().getYear();
    // when
    int parsed = resolver.parseYear(new DefaultApplicationArguments());
    // then
    assertThat(parsed).isEqualTo(expected);
  }


  @Test
  void whenResolveWithETag__ThenShouldReturnAsExpected()
      throws InvalidArgumentException, DumpNotFoundException {
    // preparing
    String arg = "--" + ArgType.ETAG.getGlobalName() + "=test";
    DiscogsDump fakeDump = getRandomDump();
    int year = fakeDump.getLastModifiedAt().getYear(), month = fakeDump.getLastModifiedAt()
        .getMonthValue();

    List<DiscogsDump> expected = new ArrayList<>(List.of(fakeDump));
    List<EntityType> typesToCheck = new ArrayList<>();

    fakeDump.getType().getDependencies().stream()
        .filter(type -> !type.equals(fakeDump.getType()))
        .peek(typesToCheck::add)
        .map(TestArguments::getRandomDumpWithType)
        .peek(expected::add)
        .forEach(
            dump ->
                when(dumpService.getMostRecentDiscogsDumpByTypeYearMonth(
                    dump.getType(), year, month))
                    .thenReturn(dump));

    when(dumpService.getDiscogsDump("test")).thenReturn(fakeDump);

    // when
    Collection<DiscogsDump> result = resolver.resolve(new DefaultApplicationArguments(arg));

    // then
    assertThat(result.size()).isEqualTo(expected.size());
    result.forEach(dump -> assertThat(dump).isIn(expected));
    verify(dumpService, times(1)).getDiscogsDump(any());
    typesToCheck.forEach(
        typeToCheck -> {
          verify(dumpService, times(1))
              .getMostRecentDiscogsDumpByTypeYearMonth(typeToCheck, year, month);
        });
    verify(dumpService, never())
        .getMostRecentDiscogsDumpByTypeYearMonth(fakeDump.getType(), year, month);
  }

  @RepeatedTest(10)
  void whenResolveWithoutETag__ThenShouldReturnAsExpected()
      throws DumpNotFoundException, InvalidArgumentException {
    // preparing
    LocalDate targetDate = LocalDate.now().minusDays(1000 + rand.nextInt(1000));
    EntityType targetType = EntityType.values()[rand.nextInt(4)];
    Collection<DiscogsDump> expected = List.of(getRandomDump());

    String typeArg = "--type=" + targetType;
    String yearMonthArg = "--yearMonth=" + targetDate.getYear() + "-" + targetDate.getMonthValue();
    when(dumpService.getAllByTypeYearMonth(
        dumpTypeCaptor.capture(), yearCaptor.capture(), monthCaptor.capture()))
        .thenReturn(expected);
    // when
    Collection<DiscogsDump> result =
        resolver.resolve(new DefaultApplicationArguments(typeArg, yearMonthArg));

    // then
    assertThat(yearCaptor.getValue()).isEqualTo(targetDate.getYear());
    assertThat(monthCaptor.getValue()).isEqualTo(targetDate.getMonthValue());
    assertThat(dumpTypeCaptor.getValue().containsAll(targetType.getDependencies())).isTrue();
    assertThat(result.size()).isEqualTo(expected.size());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
          "artist",
          "release",
          "master",
          "label",
          "artist,master",
          "artist,label",
          "label,master",
          "master,release",
          "artist,master,release",
          "label,release"
      })
  void whenTypeArgPresent__AndIsSetToStrict__ThenShouldOnlyContainGivenDumps(String type)
      throws InvalidArgumentException {
    String[] types = type.split(",");
    int typeSize = types.length;
    List<String> argList = new ArrayList<>(List.of(makeTypeArgFromTypes(types)));
    argList.add("--strict");

    DefaultApplicationArguments args =
        new DefaultApplicationArguments(argList.toArray(String[]::new));

    // when
    Collection<EntityType> parsed = resolver.parseTypes(args);

    // then
    assertThat(parsed.size()).isEqualTo(typeSize);
  }

  String[] makeTypeArgFromTypes(String[] types) {
    return Arrays.stream(types)
        .map(typeString -> String.format("--%s=%s", ArgType.TYPE.getGlobalName(), typeString))
        .toArray(String[]::new);
  }
}
