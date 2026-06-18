package rolling_restarts.content.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import rolling_restarts.content.service.RssFetcherService;

@Component
public class RssFetchScheduler {

	private static final Logger log = LoggerFactory.getLogger(RssFetchScheduler.class);

	private final RssFetcherService rssFetcherService;

	public RssFetchScheduler(RssFetcherService rssFetcherService) {
		this.rssFetcherService = rssFetcherService;
	}

	@Scheduled(fixedDelayString = "${rss.fetch.interval-ms:900000}")
	public void fetchFeeds() {
		log.info("Starting scheduled RSS feed fetch");
		rssFetcherService.fetchAllActiveSources();
		log.info("Completed scheduled RSS feed fetch");
	}
}
