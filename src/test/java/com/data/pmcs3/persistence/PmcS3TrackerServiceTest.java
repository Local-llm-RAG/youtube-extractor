package com.data.pmcs3.persistence;

import com.data.pmcs3.persistence.repository.PmcS3TrackerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Unit tests for {@link PmcS3TrackerService#incrementSkipped(Long, SkipReason)}.
 *
 * <p>The service's job here is purely dispatch: translate a {@link SkipReason}
 * enum value into a call to the matching per-column atomic counter on
 * {@link PmcS3TrackerRepository}. Because the repository queries hit real SQL
 * paths that aren't exercised by unit tests, the contract we guard here is
 * simply that the correct repository method is invoked — exactly once, with
 * the correct tracker id, and no other repository method is called as a
 * side effect.
 */
class PmcS3TrackerServiceTest {

    private static final Long TRACKER_ID = 1L;

    @ParameterizedTest
    @EnumSource(SkipReason.class)
    void dispatchesToCorrectRepositoryMethod_forEverySkipReason(SkipReason reason) {
        PmcS3TrackerRepository repository = mock(PmcS3TrackerRepository.class);
        PmcS3TrackerService service = new PmcS3TrackerService(repository);

        service.incrementSkipped(TRACKER_ID, reason);

        switch (reason) {
            case LICENSE -> {
                verify(repository).incrementSkippedLicense(TRACKER_ID);
                verify(repository, never()).incrementSkippedMissingMetadata(TRACKER_ID);
                verify(repository, never()).incrementSkippedMissingJats(TRACKER_ID);
                verify(repository, never()).incrementSkippedDuplicate(TRACKER_ID);
                verify(repository, never()).incrementSkippedIo(TRACKER_ID);
                verify(repository, never()).incrementSkippedInterrupted(TRACKER_ID);
            }
            case MISSING_METADATA -> {
                verify(repository).incrementSkippedMissingMetadata(TRACKER_ID);
                verify(repository, never()).incrementSkippedLicense(TRACKER_ID);
                verify(repository, never()).incrementSkippedMissingJats(TRACKER_ID);
                verify(repository, never()).incrementSkippedDuplicate(TRACKER_ID);
                verify(repository, never()).incrementSkippedIo(TRACKER_ID);
                verify(repository, never()).incrementSkippedInterrupted(TRACKER_ID);
            }
            case MISSING_JATS -> {
                verify(repository).incrementSkippedMissingJats(TRACKER_ID);
                verify(repository, never()).incrementSkippedLicense(TRACKER_ID);
                verify(repository, never()).incrementSkippedMissingMetadata(TRACKER_ID);
                verify(repository, never()).incrementSkippedDuplicate(TRACKER_ID);
                verify(repository, never()).incrementSkippedIo(TRACKER_ID);
                verify(repository, never()).incrementSkippedInterrupted(TRACKER_ID);
            }
            case DUPLICATE -> {
                verify(repository).incrementSkippedDuplicate(TRACKER_ID);
                verify(repository, never()).incrementSkippedLicense(TRACKER_ID);
                verify(repository, never()).incrementSkippedMissingMetadata(TRACKER_ID);
                verify(repository, never()).incrementSkippedMissingJats(TRACKER_ID);
                verify(repository, never()).incrementSkippedIo(TRACKER_ID);
                verify(repository, never()).incrementSkippedInterrupted(TRACKER_ID);
            }
            case IO -> {
                verify(repository).incrementSkippedIo(TRACKER_ID);
                verify(repository, never()).incrementSkippedLicense(TRACKER_ID);
                verify(repository, never()).incrementSkippedMissingMetadata(TRACKER_ID);
                verify(repository, never()).incrementSkippedMissingJats(TRACKER_ID);
                verify(repository, never()).incrementSkippedDuplicate(TRACKER_ID);
                verify(repository, never()).incrementSkippedInterrupted(TRACKER_ID);
            }
            case INTERRUPTED -> {
                verify(repository).incrementSkippedInterrupted(TRACKER_ID);
                verify(repository, never()).incrementSkippedLicense(TRACKER_ID);
                verify(repository, never()).incrementSkippedMissingMetadata(TRACKER_ID);
                verify(repository, never()).incrementSkippedMissingJats(TRACKER_ID);
                verify(repository, never()).incrementSkippedDuplicate(TRACKER_ID);
                verify(repository, never()).incrementSkippedIo(TRACKER_ID);
            }
        }
        verifyNoMoreInteractions(repository);
    }

    @Test
    void everySkipReasonIsHandled_noEnumValueFallsThrough() {
        // Guardrail: if a new SkipReason is added, the service's switch must be
        // exhaustive. A missing case would compile-fail on the production
        // switch statement, but this test also locks the behavior explicitly
        // by invoking every enum value without exception.
        PmcS3TrackerRepository repository = mock(PmcS3TrackerRepository.class);
        PmcS3TrackerService service = new PmcS3TrackerService(repository);

        for (SkipReason reason : SkipReason.values()) {
            service.incrementSkipped(TRACKER_ID, reason);
        }

        // Exactly one call per reason — total six verified repo interactions.
        verify(repository).incrementSkippedLicense(TRACKER_ID);
        verify(repository).incrementSkippedMissingMetadata(TRACKER_ID);
        verify(repository).incrementSkippedMissingJats(TRACKER_ID);
        verify(repository).incrementSkippedDuplicate(TRACKER_ID);
        verify(repository).incrementSkippedIo(TRACKER_ID);
        verify(repository).incrementSkippedInterrupted(TRACKER_ID);
        verifyNoMoreInteractions(repository);
    }
}
