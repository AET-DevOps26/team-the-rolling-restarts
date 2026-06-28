package rolling_restarts.content.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import rolling_restarts.content.model.Source;

@ExtendWith(MockitoExtension.class)
class SourceServiceTest {

	@Mock
	private MongoTemplate mongoTemplate;

	@InjectMocks
	private SourceService sourceService;

	@Test
	void subscribe_existing_returnsUpdatedSource() {
		Source updated = new Source();
		updated.setId("1");
		updated.setSubscriberCount(2);
		when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
				any(FindAndModifyOptions.class), eq(Source.class))).thenReturn(updated);

		assertSame(updated, sourceService.subscribe("1"));
	}

	@Test
	void subscribe_missing_returnsNull() {
		when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
				any(FindAndModifyOptions.class), eq(Source.class))).thenReturn(null);

		assertNull(sourceService.subscribe("999"));
	}

	@Test
	void unsubscribe_countStillPositive_keepsSource() {
		Source updated = new Source();
		updated.setId("1");
		updated.setSubscriberCount(1);
		when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
				any(FindAndModifyOptions.class), eq(Source.class))).thenReturn(updated);

		assertTrue(sourceService.unsubscribe("1"));
		verify(mongoTemplate, never()).remove(any(Query.class), eq(Source.class));
	}

	@Test
	void unsubscribe_countReachesZero_removesSource() {
		Source updated = new Source();
		updated.setId("1");
		updated.setSubscriberCount(0);
		when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
				any(FindAndModifyOptions.class), eq(Source.class))).thenReturn(updated);

		assertTrue(sourceService.unsubscribe("1"));
		verify(mongoTemplate).remove(any(Query.class), eq(Source.class));
	}

	@Test
	void unsubscribe_missing_returnsFalse() {
		when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
				any(FindAndModifyOptions.class), eq(Source.class))).thenReturn(null);

		assertFalse(sourceService.unsubscribe("999"));
		verify(mongoTemplate, never()).remove(any(Query.class), eq(Source.class));
	}
}
